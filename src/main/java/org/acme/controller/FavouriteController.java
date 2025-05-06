package org.acme.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.acme.model.FavouriteRequest;
import org.acme.model.FavouriteResponse;
import org.acme.service.favourite.FavouriteService;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Controller for managing movie favourites
 */
@Path("/favourites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FavouriteController {

    private static final Logger logger = Logger.getLogger(FavouriteController.class);

    @Inject
    FavouriteService favouriteService;

    /**
     * Add or remove a single movie to/from favourites
     *
     * @param request The favourite request containing action, type, and id
     * @return Response with the result of the operation
     */
    @POST
    public Response processFavourite(FavouriteRequest request) {
        logger.infof("Processing favourite request: action=%s, type=%s, id=%d",
                request.getAction(), request.getType(), request.getId());

        try {
            FavouriteResponse response = favouriteService.processFavourite(request);

            if (response.isSuccess()) {
                return Response.ok(response).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
            }
        } catch (Exception e) {
            logger.errorf("Error processing favourite: %s", e.getMessage());
            FavouriteResponse errorResponse = new FavouriteResponse(false, "Error processing favourite: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
        }
    }

    /**
     * Process favourites for a range of movie IDs
     *
     * @param startId The ID to start from
     * @param endId The ID to end at
     * @param action The action to perform (add or remove)
     * @return Response with the results of the operations
     */
    @POST
    @Path("/range")
    public Response processFavouritesForRange(
            @QueryParam("startId") Long startId,
            @QueryParam("endId") Long endId,
            @QueryParam("action") @DefaultValue("add") String action) {

        logger.infof("Processing favourites for range: startId=%d, endId=%d, action=%s",
                startId, endId, action);

        try {
            Map<Long, FavouriteResponse> results = favouriteService.processFavouritesForRange(startId, endId, action);

            return Response.ok(results).build();
        } catch (Exception e) {
            logger.errorf("Error processing favourites for range: %s", e.getMessage());
            FavouriteResponse errorResponse = new FavouriteResponse(false, "Error processing favourites for range: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorResponse).build();
        }
    }
}
