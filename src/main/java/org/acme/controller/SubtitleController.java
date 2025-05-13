package org.acme.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.acme.entity.Movie;
import org.acme.service.subtitle.SubtitleService;
import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ManagedContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Controller for managing subtitle-related operations
 */
@Path("/subtitle")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SubtitleController {
    
    private static final Logger logger = Logger.getLogger(SubtitleController.class);
    private ExecutorService executor;
    private boolean isRunning = false;
    private static final int THREAD_POOL_SIZE = 5; // Default thread pool size
    private static final int BATCH_SIZE = 20; // Default batch size

    @Inject
    SubtitleService subtitleService;

    /**
     * Start the subtitle processing job
     * 
     * @param batchSize Number of movies to process in each batch (default: 20)
     * @param maxMovies Maximum number of movies to process in total (default: 1000)
     * @return Response indicating if the job was started
     */
    @POST
    @Path("/start")
    public Response startSubtitleProcessing(
            @QueryParam("batchSize") @DefaultValue("20") int batchSize,
            @QueryParam("maxMovies") @DefaultValue("1000") int maxMovies) {
        
        if (isRunning) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new JobResponse("Subtitle processing job is already running"))
                    .build();
        }

        // Initialize the executor with a fixed thread pool
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        }

        // Set running flag
        isRunning = true;

        // Get the Arc container and request context for proper CDI in background threads
        final ArcContainer container = Arc.container();
        final ManagedContext requestContext = container.requestContext();
        
        // Start the processing in a separate thread
        CompletableFuture.runAsync(() -> {
            // Activate request context for this thread
            requestContext.activate();
            
            try {
                logger.info("Starting subtitle processing in background thread...");
                processSubtitlesInBatches(batchSize, maxMovies);
                logger.info("Subtitle processing job completed.");
            } catch (Exception e) {
                logger.errorf("Error during subtitle processing: %s", e.getMessage());
                logger.error("Stack trace:", e);
            } finally {
                isRunning = false;
                // Clean up the request context when done
                requestContext.terminate();
            }
        });

        return Response.ok(new JobResponse(String.format(
                "Subtitle processing job started with batch size %d and max movies %d",
                batchSize, maxMovies)))
                .build();
    }
    
    /**
     * Process subtitles in batches
     * 
     * @param batchSize Number of movies to process in each batch
     * @param maxMovies Maximum number of movies to process
     */
    private void processSubtitlesInBatches(int batchSize, int maxMovies) {
        // Calculate number of batches needed
        int totalBatches = (int) Math.ceil(maxMovies / (double) batchSize);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        
        logger.infof("Starting processing with %d batches of size %d", totalBatches, batchSize);
        
        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            if (executor.isShutdown()) {
                logger.info("Subtitle processing was stopped");
                break;
            }
            
            int offset = batchNum * batchSize;
            int limit = Math.min(batchSize, maxMovies - offset);
            
            try {
                // Get movies for this batch
                List<Movie> movies = Movie.find("code is not null")
                        .page(batchNum, batchSize)
                        .list();
                
                if (movies.isEmpty()) {
                    logger.infof("No more movies found, ending processing");
                    break;
                }
                
                logger.infof("Processing batch %d/%d with %d movies", 
                        batchNum + 1, totalBatches, movies.size());
                
                // Process each movie in the batch with CompletableFuture
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                
                for (Movie movie : movies) {
                    if (movie.code == null || movie.code.trim().isEmpty()) {
                        continue;
                    }
                    
                    CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                        String code = movie.code.trim();
                        try {
                            logger.infof("Processing subtitles for movie: %s", code);
                            
                            // This is where we'll call the subtitle service
                            Map<String, String> result = subtitleService.fetchSubtitleLinksForMovies(1, true);
                            
                            if (result != null && !result.isEmpty()) {
                                // In a real implementation, you might want to save the results to the movie
                                successCount.incrementAndGet();
                                return true;
                            } else {
                                logger.infof("No subtitles found for movie: %s", code);
                                return false;
                            }
                        } catch (Exception e) {
                            logger.errorf("Error processing subtitles for movie %s: %s", 
                                    code, e.getMessage());
                            failedCount.incrementAndGet();
                            return false;
                        }
                    }, executor);
                    
                    futures.add(future);
                    processedCount.incrementAndGet();
                }
                
                // Wait for all movies in this batch to complete
                CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));
                
                try {
                    allFutures.join(); // Wait for all to complete
                    logger.infof("Completed batch %d/%d. Progress: %d/%d movies processed", 
                            batchNum + 1, totalBatches, processedCount.get(), maxMovies);
                } catch (Exception e) {
                    logger.errorf("Error waiting for batch %d completion: %s", 
                            batchNum + 1, e.getMessage());
                }
                
            } catch (Exception e) {
                logger.errorf("Error processing batch %d: %s", batchNum + 1, e.getMessage());
            }
        }
        
        logger.infof("Subtitle processing complete. Total processed: %d, Success: %d, Failed: %d", 
                processedCount.get(), successCount.get(), failedCount.get());
    }
    
    /**
     * Get the current status of the subtitle processing job
     * 
     * @return Response with job status
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.ok(new JobResponse(isRunning ? 
                "Subtitle processing job is running" : 
                "Subtitle processing job is not running"))
                .build();
    }
    
    /**
     * Stop the subtitle processing job if it's running
     * 
     * @return Response indicating if the job was stopped
     */
    @POST
    @Path("/stop")
    public Response stopSubtitleProcessing() {
        if (!isRunning) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new JobResponse("Subtitle processing job is not running"))
                    .build();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            isRunning = false;
        }

        return Response.ok(new JobResponse("Subtitle processing job stopped"))
                .build();
    }
    
    /**
     * Simple response object for job operations
     */
    public static class JobResponse {
        public String message;

        public JobResponse(String message) {
            this.message = message;
        }
    }
}
