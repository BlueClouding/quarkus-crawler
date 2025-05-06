package org.acme.scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.enums.MovieStatus;
import org.acme.service.MovieDetailCrawlerService;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;

@ApplicationScoped
public class MovieDetailCrawlerWorker {

    private static final Logger logger = Logger.getLogger(MovieDetailCrawlerWorker.class);

    @Inject
    MovieDetailCrawlerService movieDetailCrawlerService;

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
        // 查询电影ID列表，而不是实体对象
        List<Movie> movies = Movie.find("status = ?1 order by id", MovieStatus.NEW.getValue())
                                .page(0, batchSize)
                                .list();
        
        if (movies == null || movies.isEmpty()) {
            return false;
        }
        
        // 提取ID列表
        List<Long> movieIds = movies.stream()
                                    .map(movie -> movie.id)
                                    .collect(Collectors.toList());
        
        logger.infof("Processing batch of %d movies", movieIds.size());
        // 使用固定大小的线程池并发处理
        var executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<CompletableFuture<Void>> futures = movieIds.stream()
                .map(movieId -> CompletableFuture.runAsync(() -> processOneMovieById(movieId), executor))
                .collect(Collectors.toList());
            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.infof("Batch of %d movies finished successfully", movieIds.size());
        } finally {
            // 关闭线程池
            executor.shutdown();
        }
        return true;
    }

    /**
     * 处理单个电影，每个线程内都有自己的事务
     */
    @Transactional
    /**
     * 处理单个电影 - 将HTTP请求与数据库事务分离
     */
    public void processOneMovieById(Long movieId) {
        if (movieId == null) return;
        
        // 先获取待处理的电影信息
        Movie movie = Movie.findById(movieId);
        if (movie == null) {
            logger.warnf("Movie with id %d not found", movieId);
            return;
        }
        
        String movieCode = movie.getCode();
        logger.infof("Processing movie: %s (ID: %d)", movieCode, movieId);
        
        try {
            // 步骤1: 在事务外执行HTTP请求，避免事务超时
            Movie newMovieData = movieDetailCrawlerService.processMovie(movie);
            
            // 步骤2: 在新事务中更新数据库
            updateMovieInTransaction(movieId, newMovieData);
            
            logger.infof("Successfully processed movie: %s", movieCode);
        } catch (Exception e) {
            logger.errorf("Failed to process movie %s: %s", movieCode, e.getMessage());
            updateMovieStatusToFailed(movieId);
        }
    }
    
    /**
     * 在新事务中更新电影数据
     */
    @Transactional
    void updateMovieInTransaction(Long movieId, Movie newMovieData) {
        Movie movie = Movie.findById(movieId);
        if (movie != null) {
            // 先用新数据补全当前实体的空字段
            movie.updateIfNullFields(newMovieData);
            movie.setStatus(MovieStatus.PROCESSING.getValue());
            
            // 使用updateFieldsById更新所有非空字段
            // 这样可以更新多个字段，而不仅仅是状态字段
            movie.updateFieldsById(movieId, newMovieData);
        }
    }
    
    /**
     * 在新事务中更新电影状态为失败
     */
    @Transactional
    void updateMovieStatusToFailed(Long movieId) {
        try {
            Movie.update("status = ?1 where id = ?2", MovieStatus.FAILED.getValue(), movieId);
        } catch (Exception ex) {
            logger.errorf("Failed to update movie status: %s", ex.getMessage());
        }
    }
}
