package org.acme.scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.entity.MovieInfo;
import org.acme.enums.CrawlerStatus;
import org.acme.enums.MovieStatus;
import org.acme.service.MovieDetailCrawlerService;
import org.acme.service.MovieInfoExtractionService;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.netty.util.internal.StringUtil;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;

@ApplicationScoped
public class MovieDetailCrawlerWorker {

    private static final Logger logger = Logger.getLogger(MovieDetailCrawlerWorker.class);

    @Inject
    MovieDetailCrawlerService movieDetailCrawlerService;
    
    @Inject
    MovieInfoExtractionService movieInfoExtractionService;

    private static final int BATCH_SIZE = 10; // 每次批量处理数
    private static final int THREAD_POOL_SIZE = 5; // 并发线程数
    
    // 每5秒执行一次，不允许并发执行
    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledBatchProcess(ScheduledExecution execution) {
        logger.info("SCHEDULED TASK TRIGGERED at " + execution.getScheduledFireTime() + ", batch size: " + BATCH_SIZE);
        try {
            boolean processed = processMoviesBatch(BATCH_SIZE);
            if (!processed) {
                logger.info("No movies to process in this batch");
            }
        } catch (Exception e) {
            logger.error("Error in scheduled movie processing: " + e.getMessage(), e);
        }
    }

    public boolean processMoviesBatch(int batchSize) {
        // 查询电影ID列表，不过滤状态，只限制数量
        List<Movie> movies = Movie.find("status = ?1 order by id desc", CrawlerStatus.PROCESSING.getValue())
                                .page(0, batchSize)
                                .list();
        
        if (movies == null || movies.isEmpty()) {
            return false;
        }
        
        // 提取ID列表，从链接中提取电影代码
        List<String> movieCodes = movies.stream()
                                    .map(movie -> {
                                        if (movie.link != null && !movie.link.isEmpty()) {
                                            // 从链接中提取最后一部分作为代码
                                            // 例如：https://123av.com/zh/dm2/v/rbd-301-uncensored-leaked
                                            // 提取为：rbd-301-uncensored-leaked
                                            String[] parts = movie.link.split("/");
                                            if (parts.length > 0) {
                                                // 获取最后一个部分作为代码
                                                String extractedCode = parts[parts.length - 1];
                                                logger.infof("Extracted code '%s' from link: %s", extractedCode, movie.link);
                                                return extractedCode;
                                            }
                                        }
                                        // 如果链接为空或无法提取，则返回存储的代码
                                        return movie.code;
                                    })
                                    .collect(Collectors.toList());
        
        logger.infof("Processing batch of %d movies", movieCodes.size());
        // 使用固定大小的线程池并发处理
        var executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<Void>> futures = movieCodes.stream()
                .map(code -> CompletableFuture.runAsync(() -> processOneMovieByCode(code), executor))
                .collect(Collectors.toList());
            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.infof("Batch of %d movies finished successfully", movieCodes.size());
        } finally {
            // 关闭线程池
            executor.shutdown();
        }
        return true;
    }

    /**
     * 处理单个电影 - 将HTTP请求与数据库事务分离
     */
    @Transactional
    public void processOneMovieByCode(String code) {
        if (StringUtil.isNullOrEmpty(code)) {
            logger.warnf("Movie code is null");
            return;
        }
        
        logger.infof("Processing movie code:{}", code);
        
        try {
            // 步骤1: 提取电影信息到movie_info表中
            List<MovieInfo> extractedInfos = movieInfoExtractionService.extractAndSaveAllLanguages(code);
            logger.infof("Extracted %d language versions for movie: %s", extractedInfos.size(), code);
            
            // 步骤2: 更新Movie表中的状态为已处理
            updateMovieStatusToProcessed(code);
            
            logger.infof("Successfully processed movie: %s", code);
        } catch (Exception e) {
            logger.errorf("Failed to process movie %s: %s", code, e.getMessage());
            updateMovieStatusToFailed(code);
        }
    }
    
    /**
     * 在新事务中更新电影状态为已处理
     */
    @Transactional
    void updateMovieStatusToProcessed(String code) {
        try {
            Movie.update("status = ?1 where code = ?2", MovieStatus.SUCCEED.getValue(), code);
            logger.infof("Updated movie with code %s status to ONLINE", code);
        } catch (Exception ex) {
            logger.errorf("Failed to update movie status to ONLINE: %s", ex.getMessage());
        }
    }
    
    /**
     * 在新事务中更新电影状态为失败
     */
    @Transactional
    void updateMovieStatusToFailed(String code) {
        try {
            Movie.update("status = ?1 where code = ?2", MovieStatus.FAILED.getValue(), code);
            logger.infof("Updated movie with code %s status to FAILED", code);
        } catch (Exception ex) {
            logger.errorf("Failed to update movie status to FAILED: %s", ex.getMessage());
        }
    }
}
