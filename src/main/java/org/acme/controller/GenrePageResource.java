package org.acme.controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import org.acme.service.GenrePageService;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

@Path("/crawler/genrePage")
public class GenrePageResource {

    private static final Logger logger = Logger.getLogger(GenrePageResource.class.getName());

    @Inject
    GenrePageService genrePageService;

    @POST
    @Path("/start/{taskId}")
    public Response processGenresPages(int taskId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            // 激活请求上下文
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();

            logger.info("Starting processGenresPages task for taskId: " + taskId);
            try {
                genrePageService.processGenresPages(taskId);
                logger.info("Finished processGenresPages task for taskId: " + taskId);
            } catch (Exception e) {
                logger.severe("Error in processGenresPages task for taskId " + taskId + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                // 确保请求上下文被终止
                if (requestContext.isActive()) {
                    requestContext.terminate();
                }
            }
        });
        executor.shutdown();
        return Response.ok().entity("Task started").build();
    }
}
