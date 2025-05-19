package org.acme.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.acme.service.favourite.CollectionProcessService;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for managing the collection processing
 */
@Path("/collection-process")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectionProcessController {

    private static final Logger logger = Logger.getLogger(CollectionProcessController.class);
    private ExecutorService executor;
    private boolean isRunning = false;
    private static final int THREAD_POOL_SIZE = 5; // Using 5 threads for parallel processing
    private static final int TASKS_PER_THREAD = 12; // Each thread processes 12 tasks

    @Inject
    CollectionProcessService collectionProcessService;

    /**
     * Process a single batch of movies
     *
     * @param batchSize The number of movies to process in this batch
     * @return Response with the results of the batch processing
     */
    @POST
    @Path("/batch")
    public Response processBatch(
            @QueryParam("batchSize") @DefaultValue("120") int batchSize) {

        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ProcessResponse("Collection processing is already running"))
                    .build();
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        logger.infof("Processing batch: batchSize=%d", batchSize);

        try {
            CompletableFuture.runAsync(() -> {
                collectionProcessService.processMovieBatch(batchSize);
            });
            return Response.ok(new ProcessResponse("Collection processing started with batch size " + batchSize)).build();
        } catch (Exception e) {
            logger.errorf("Error processing batch: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("success", false, "error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Add a range of movie IDs to favourites
     *
     * @param startId The ID to start from
     * @param endId The ID to end at
     * @return Response with the results of adding to favourites
     */
    @POST
    @Path("/add-favourites")
    public Response addToFavourites(
            @QueryParam("startId") @DefaultValue("1") Long startId,
            @QueryParam("endId") Long endId,
            @QueryParam("batchSize") @DefaultValue("120") int batchSize) {

        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ProcessResponse("Collection processing is already running"))
                    .build();
        }

        // Calculate endId if not provided
        if (endId == null) {
            endId = startId + batchSize - 1;
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        logger.infof("Adding favourites: startId=%d, endId=%d", startId, endId);

        final Long finalEndId = endId; // Need final variable for lambda

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    collectionProcessService.addToFavourites(startId, finalEndId);
                } finally {
                    isRunning = false;
                }
            });
            return Response.ok(new ProcessResponse("Started adding movies to favourites from ID " + startId + " to " + endId))
                    .build();
        } catch (Exception e) {
            isRunning = false;
            logger.errorf("Error adding to favourites: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ProcessResponse("Error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Process collection pages and save movies to database
     *
     * @return Response with the results of processing
     */
    @POST
    @Path("/process-collection")
    public Response processCollection() {

        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ProcessResponse("Collection processing is already running"))
                    .build();
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        logger.info("Processing all collection pages");

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    collectionProcessService.processCollectionAndSave();
                } finally {
                    isRunning = false;
                }
            });
            return Response.ok(new ProcessResponse("Started processing all collection pages"))
                    .build();
        } catch (Exception e) {
            isRunning = false;
            logger.errorf("Error processing collection: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ProcessResponse("Error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Remove a range of movie IDs from favourites
     *
     * @param startId The ID to start from
     * @param endId The ID to end at
     * @return Response with the results of removing from favourites
     */
    @POST
    @Path("/remove-favourites")
    public Response removeFromFavourites(
            @QueryParam("startId") @DefaultValue("1") Long startId,
            @QueryParam("endId") Long endId,
            @QueryParam("batchSize") @DefaultValue("120") int batchSize) {

        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ProcessResponse("Collection processing is already running"))
                    .build();
        }

        // Calculate endId if not provided
        if (endId == null) {
            endId = startId + batchSize - 1;
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        logger.infof("Removing favourites: startId=%d, endId=%d", startId, endId);

        final Long finalEndId = endId; // Need final variable for lambda

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    collectionProcessService.removeFromFavourites(startId, finalEndId);
                } finally {
                    isRunning = false;
                }
            });
            return Response.ok(new ProcessResponse("Started removing movies from favourites from ID " + startId + " to " + endId))
                    .build();
        } catch (Exception e) {
            isRunning = false;
            logger.errorf("Error removing from favourites: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ProcessResponse("Error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Automatically remove all processed movie IDs from favourites using multiple threads
     *
     * @param batchSize The size of each batch to process
     * @param threadCount The number of threads to use
     * @return Response with the results of the auto-removal process
     */
    @POST
    @Path("/auto-remove")
    public Response autoRemoveProcessedIds(
            @QueryParam("batchSize") @DefaultValue("50") int batchSize,
            @QueryParam("threadCount") @DefaultValue("5") int threadCount) {

        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ProcessResponse("Collection processing is already running"))
                    .build();
        }

        // Set running flag
        isRunning = true;

        logger.infof("Starting auto-removal with batchSize=%d, threadCount=%d", batchSize, threadCount);

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> result = collectionProcessService.autoRemoveProcessedIds(batchSize, threadCount);
                    logger.infof("Auto-removal completed: %s", result);
                } finally {
                    isRunning = false;
                }
            });
            return Response.ok(new ProcessResponse("Started auto-removal of processed movie IDs"))
                    .build();
        } catch (Exception e) {
            isRunning = false;
            logger.errorf("Error during auto-removal: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ProcessResponse("Error: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Process multiple batches asynchronously
     *
     * @param startId The ID to start processing from
     * @param batchSize The number of movies to process in each batch
     * @param batchCount The number of batches to process
     * @return Response indicating if the process was started
     */
    @POST
    @Path("/multi-batch")
    public Response processMultipleBatches(
            @QueryParam("startId") @DefaultValue("1") Long startId,
            @QueryParam("batchSize") @DefaultValue("120") int batchSize,
            @QueryParam("batchCount") @DefaultValue("1") int batchCount) {

        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ProcessResponse("Collection processing is already running"))
                    .build();
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        logger.infof("Starting multiple batch processing: startId=%d, batchSize=%d, batchCount=%d",
                startId, batchSize, batchCount);

        // Start the processing in a separate thread to not block the response
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting batch processing in background thread with parallel execution...");

                // Calculate total number of tasks
                int totalTasks = batchCount;
                logger.infof("Total tasks to process: %d", totalTasks);

                // Process in batches of TASKS_PER_THREAD
                for (int batchOffset = 0; batchOffset < totalTasks; batchOffset += TASKS_PER_THREAD) {
                    // Calculate how many tasks to process in this batch (handle the last batch which might be smaller)
                    int tasksInThisBatch = Math.min(TASKS_PER_THREAD, totalTasks - batchOffset);
                    logger.infof("Processing batch of %d tasks starting at offset %d", tasksInThisBatch, batchOffset);

                    // Create a list to hold all the futures for this batch
                    List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();

                    // Create a CompletableFuture for each task in this batch
                    for (int i = 0; i < tasksInThisBatch; i++) {
                        final int taskIndex = batchOffset + i;
                        final Long currentStartId = startId + (taskIndex * batchSize);

                        // Create a CompletableFuture for this task
                        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                            logger.infof("Processing task %d with startId %d", taskIndex, currentStartId);
                            return collectionProcessService.processBatch(currentStartId, batchSize);
                        }, executor);

                        futures.add(future);
                    }

                    // Wait for all tasks in this batch to complete
                    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0])
                    );

                    // Block until all tasks in this batch are complete
                    allFutures.join();

                    logger.infof("Completed batch of %d tasks", tasksInThisBatch);
                }

                logger.infof("Completed processing all %d batches in parallel", totalTasks);
            } catch (Exception e) {
                logger.errorf("Error during batch processing: %s", e.getMessage());
                logger.error("Stack trace:", e);
            } finally {
                isRunning = false;
                logger.info("Collection processing completed.");
            }
        });

        return Response.ok(new ProcessResponse("Collection processing started with batch size " + batchSize + " and batch count " + batchCount))
                .build();
    }

    /**
     * Get the current status of the collection processing
     * @return Response with processing status
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(new ProcessResponse(isRunning ? "Collection processing is running" : "Collection processing is not running"))
                .build();
    }

    /**
     * Stop the collection processing if it's running
     * @return Response indicating if the processing was stopped
     */
    @POST
    @Path("/stop")
    public Response stopProcessing() {
        if (!isRunning) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ProcessResponse("Collection processing is not running"))
                    .build();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            isRunning = false;
        }

        return Response.ok(new ProcessResponse("Collection processing stopped"))
                .build();
    }

    /**
     * Simple response object
     */
    public static class ProcessResponse {
        public String message;

        public ProcessResponse(String message) {
            this.message = message;
        }
    }
}
