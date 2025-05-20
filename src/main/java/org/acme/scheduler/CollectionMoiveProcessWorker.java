package org.acme.scheduler;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.acme.service.MovieDetailCrawlerService;
import org.acme.service.MovieInfoExtractionService;
import org.acme.service.favourite.CollectionProcessService;
import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CollectionMoiveProcessWorker {
    private static final Logger logger = Logger.getLogger(CollectionMoiveProcessWorker.class);

    @Inject
    CollectionProcessService collectionProcessService;


    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledBatchProcess(ScheduledExecution execution) {
        logger.info("SCHEDULED scheduledBatchProcess at " + execution.getScheduledFireTime());
        try {
            boolean processed = startWorker();
            if (!processed) {
                logger.info("No movies to process in this batch");
            }
        } catch (Exception e) {
            logger.error("Error in scheduled movie processing: " + e.getMessage(), e);
        }
    }

    private boolean startWorker() {
        logger.infof("Processing batch of collection movies");
        // 使用固定大小的线程池并发处理
        var executor = Executors.newFixedThreadPool(1);
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    collectionProcessService.processMovieBatch(120);
                } catch (Exception e) {
                    logger.errorf("Error processMovieBatch" , e.getMessage());
                    logger.error("Stack trace:", e);
                }
            }, executor).exceptionally(ex -> {
                logger.error("Stack trace:", ex);
                return null;
            });
        } finally {
            // 关闭线程池
            executor.shutdown();
        }
        return true;
    }
}
