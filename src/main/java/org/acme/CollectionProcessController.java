package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.acme.service.CollectionProcessService;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing the collection processing
 */
@Path("/collection-process")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectionProcessController {

    private static final Logger logger = Logger.getLogger(CollectionProcessController.class);

    @Inject
    CollectionProcessService collectionProcessService;

    /**
     * Process a single batch of movies
     * 
     * @param startId The ID to start processing from
     * @param batchSize The number of movies to process in this batch
     * @return Response with the results of the batch processing
     */
    @POST
    @Path("/batch")
    public Response processBatch(
            @QueryParam("startId") @DefaultValue("1") Long startId,
            @QueryParam("batchSize") @DefaultValue("120") int batchSize) {
        
        logger.infof("Processing batch: startId=%d, batchSize=%d", startId, batchSize);
        
        try {
            Map<String, Object> result = collectionProcessService.processBatch(startId, batchSize);
            return Response.ok(result).build();
        } catch (Exception e) {
            logger.errorf("Error processing batch: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("success", false, "error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Process multiple batches sequentially
     * 
     * @param startId The ID to start processing from
     * @param batchSize The number of movies to process in each batch
     * @param batchCount The number of batches to process
     * @return Response with the results of all batch processing
     */
    @POST
    @Path("/multi-batch")
    public Response processMultipleBatches(
            @QueryParam("startId") @DefaultValue("1") Long startId,
            @QueryParam("batchSize") @DefaultValue("120") int batchSize,
            @QueryParam("batchCount") @DefaultValue("1") int batchCount) {
        
        logger.infof("Processing multiple batches: startId=%d, batchSize=%d, batchCount=%d", 
                startId, batchSize, batchCount);
        
        try {
            List<Map<String, Object>> results = collectionProcessService.processMultipleBatches(startId, batchSize, batchCount);
            return Response.ok(results).build();
        } catch (Exception e) {
            logger.errorf("Error processing multiple batches: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("success", false, "error", e.getMessage()))
                    .build();
        }
    }
}
