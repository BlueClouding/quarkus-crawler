package org.acme.controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.acme.entity.WatchUrl;
import org.acme.service.VideoIdCrawlerService;
import org.jboss.logging.Logger;

/**
 * Controller for managing the video ID crawler
 */
@Path("/crawler/video-id")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VideoIdCrawlerController {

    private static final Logger logger = Logger.getLogger(VideoIdCrawlerController.class);
    private ExecutorService executor;
    private boolean isRunning = false;
    private static final int BATCH_SIZE = 10;
    private static final int THREAD_POOL_SIZE = 5; // Adjust based on your server capacity
    private static final int DB_FETCH_BATCH_SIZE = 3000; // Batch size for fetching existing IDs

    @Inject
    VideoIdCrawlerService videoIdCrawlerService;

    /**
     * Start the video ID crawler
     * @param startId The ID to start crawling from (optional)
     * @param endId The ID to end crawling at (optional)
     * @return Response indicating if the crawler was started
     */
    @POST
    @Path("/start")
    public Response startCrawler(@QueryParam("startId") @DefaultValue("1") int startId,
                                @QueryParam("endId") @DefaultValue("200000") int endId) {
        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new CrawlerResponse("Crawler is already running"))
                    .build();
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        // Start the crawler in a separate thread to not block the response
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting crawler in background thread...");
                // First, load all existing movie IDs into a set
                Set<Long> existingIds;
                try {
                    existingIds = loadExistingMovieIds(1, startId);
                    logger.infof("Found %d existing movie IDs in the database", existingIds.size());
                } catch (Exception e) {
                    logger.errorf("Error loading existing movie IDs: %s", e.getMessage());
                    logger.error("Stack trace:", e);
                    existingIds = new HashSet<>(); // Continue with empty set if there's an error
                }

                // Process batches, skipping IDs that already exist
                try {
                    logger.info("Starting batch processing...");
                    processBatches(startId, endId, existingIds);
                    logger.info("Batch processing completed successfully.");
                } catch (Exception e) {
                    logger.errorf("Error during batch processing: %s", e.getMessage());
                    logger.error("Stack trace:", e);
                }
            } finally {
                isRunning = false;
                logger.info("Video ID crawler completed.");
            }
        });

        return Response.ok(new CrawlerResponse("Video ID crawler started with batch size " + BATCH_SIZE))
                .build();
    }

    /**
     * Load all existing movie IDs from the database in batches
     * @param startId starting ID range
     * @param endId ending ID range
     * @return Set of existing movie IDs
     */
    private Set<Long> loadExistingMovieIds(int startId, int endId) {
        logger.infof("Loading existing movie IDs from %d to %d", startId, endId);
        Set<Long> existingIds = new HashSet<>();

        try {
            // Query for all movie IDs in the WatchUrl table
            logger.info("Querying for all existing movie IDs in the database...");
            List<WatchUrl> watchUrls = WatchUrl.listAll();
            logger.infof("Found %d total watch URLs in database", watchUrls.size());

            // Extract the movie IDs
            for (WatchUrl watchUrl : watchUrls) {
                if (watchUrl.getMovieId() != null) {
                    existingIds.add(watchUrl.getMovieId().longValue());
                }
            }

            logger.infof("Extracted %d unique movie IDs from database", existingIds.size());
        } catch (Exception e) {
            logger.errorf("Error querying database for existing IDs: %s", e.getMessage());
            logger.error("Stack trace:", e);
        }

        return existingIds;
    }

    /**
     * Process video IDs in batches, skipping those that already exist
     * @param startId starting ID
     * @param endId ending ID
     * @param existingIds Set of IDs that already exist in the database
     */
    private void processBatches(int startId, int endId, Set<Long> existingIds) {
        logger.infof("Starting batch processing from ID %d to %d with batch size %d", startId, endId, BATCH_SIZE);
        int totalProcessed = 0;
        int totalSkipped = 0;

        try {
            for (int batchStart = startId; batchStart <= endId; batchStart += BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + BATCH_SIZE - 1, endId);
                logger.infof("Processing batch from %d to %d", batchStart, batchEnd);

                List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
                int batchSkipped = 0;

                // Submit each ID in the batch as a separate task, skipping existing IDs
                for (int id = batchStart; id <= batchEnd; id++) {
                    final long videoId = id;

                    // Skip if this ID already exists in the database
                    if (existingIds.contains(videoId)) {
                        batchSkipped++;
                        totalSkipped++;
                        continue;
                    }

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            logger.infof("Processing video ID %d", videoId);
                            videoIdCrawlerService.processVideoById(videoId);
                            logger.infof("Successfully processed video ID %d", videoId);
                            // Add to existing IDs set to prevent duplicates if another batch tries to process this ID
                            synchronized (existingIds) {
                                existingIds.add(videoId);
                            }
                        } catch (Exception e) {
                            logger.errorf("Error processing video ID %d: %s", videoId, e.getMessage());
                            logger.error("Stack trace:", e);
                        }
                    }, executor).exceptionally(ex -> {
                        logger.errorf("Exception in CompletableFuture for video ID %d: %s", videoId, ex.getMessage());
                        logger.error("Stack trace:", ex);
                        return null;
                    });
                    batchFutures.add(future);
                }

                // If all IDs in this batch were skipped, continue to next batch
                if (batchFutures.isEmpty()) {
                    logger.infof("All %d IDs in batch %d to %d already exist, skipping to next batch",
                            (batchEnd - batchStart + 1), batchStart, batchEnd);
                    continue;
                }

                if (batchSkipped > 0) {
                    logger.infof("Skipped %d/%d IDs in batch %d to %d",
                            batchSkipped, (batchEnd - batchStart + 1), batchStart, batchEnd);
                }

                // Wait for all tasks in this batch to complete before starting the next batch
                try {
                    // Convert list to array for allOf
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Void>[] futuresArray = batchFutures.toArray(new CompletableFuture[0]);
                    // Wait for all CompletableFutures to complete
                    CompletableFuture.allOf(futuresArray).join();
                } catch (Exception e) {
                    logger.error("Error waiting for batch completion", e);
                    logger.error("Stack trace:", e);
                }

                totalProcessed += batchFutures.size();
                logger.infof("Completed batch from %d to %d, processed %d IDs", batchStart, batchEnd, batchFutures.size());
            }

            logger.infof("Batch processing complete. Total processed: %d, Total skipped: %d", totalProcessed, totalSkipped);
        } catch (Exception e) {
            logger.errorf("Unexpected error during batch processing: %s", e.getMessage());
            logger.error("Stack trace:", e);
        }
    }


    /**
     * Get the current status of the crawler
     * @return Response with crawler status
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(new CrawlerResponse(isRunning ? "Crawler is running" : "Crawler is not running"))
                .build();
    }

    /**
     * Stop the crawler if it's running
     * @return Response indicating if the crawler was stopped
     */
    @POST
    @Path("/stop")
    public Response stopCrawler() {
        if (!isRunning) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new CrawlerResponse("Crawler is not running"))
                    .build();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            isRunning = false;
        }

        return Response.ok(new CrawlerResponse("Video ID crawler stopped"))
                .build();
    }

    /**
     * Simple response object
     */
    public static class CrawlerResponse {
        public String message;

        public CrawlerResponse(String message) {
            this.message = message;
        }
    }
}
