//package org.acme.controller;
//
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.Executors;
//
//import org.acme.service.favourite.CollectionProcessService;
//import org.jboss.logging.Logger;
//
//import jakarta.inject.Inject;
//import jakarta.ws.rs.Consumes;
//import jakarta.ws.rs.DefaultValue;
//import jakarta.ws.rs.POST;
//import jakarta.ws.rs.Path;
//import jakarta.ws.rs.Produces;
//import jakarta.ws.rs.QueryParam;
//import jakarta.ws.rs.core.MediaType;
//import jakarta.ws.rs.core.Response;
//
//@Path("/compensation")
//@Produces(MediaType.APPLICATION_JSON)
//@Consumes(MediaType.APPLICATION_JSON)
//public class CompensationController {
//    private static final Logger logger = Logger.getLogger(CollectionProcessController.class);
//
//    @Inject
//    CollectionProcessService collectionProcessService;
//
//    @POST
//    @Path("/batch1")
//    public Response processBatch(
//            @QueryParam("startId") @DefaultValue("1") Long startId,
//            @QueryParam("batchSize") @DefaultValue("120") int batchSize) {
//
//        try {
//            CompletableFuture.runAsync(() -> {
//                collectionProcessService.processBatch(startId, batchSize);
//            });
//            return Response.ok(new CollectionProcessController.ProcessResponse("Collection processing started with batch size " + batchSize)).build();
//        } catch (Exception e) {
//            logger.errorf("Error processing batch: %s", e.getMessage());
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
//                    .entity(Map.of("success", false, "error", e.getMessage()))
//                    .build();
//        }
//    }
//
//}
