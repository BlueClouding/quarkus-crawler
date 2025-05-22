package org.acme.resource;

import com.cronutils.utils.StringUtils;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.acme.entity.Movie;
import org.acme.entity.MovieInfo;
import org.acme.service.MovieInfoExtractionService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint for movie information extraction operations
 */
@Path("/api/movies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MovieInfoResource {

    @Inject
    MovieInfoExtractionService extractionService;

    /**
     * Extract movie information in all 13 supported languages and save it to the database
     *
     * @param movieCode The DVD code to extract information for
     * @return A Response containing the extracted movie information entities
     */
    @POST
    @Path("/extract/{movieCode}")
    public Response extractMovieInfoAllLanguages(@PathParam("movieCode") String movieCode) {
        try {
            // movies 表中查询 movie_code = movieCode 的记录
            Movie movie = Movie.find("code = ?1", movieCode).firstResult();
            if (movie == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Movie not found for the specified code"))
                        .build();
            }
            List<MovieInfo> extractedInfo = extractionService.extractAndSaveAllLanguages(movie);
            return Response.ok(extractedInfo).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Extract movie information from 123av.com in all supported languages and save it to the database
     *
     * @param movieCode The DVD code to extract information for
     * @return A Response containing the extracted movie information entities
     */
    @POST
    @Path("/extract/123/{movieCode}")
    public Response extract123AvMovieInfo(@PathParam("movieCode") String movieCode) {
        try {
            Movie movie = Movie.find("code = ?1", movieCode).firstResult();
            if (movie == null || StringUtils.isEmpty(movie.link)) {
                return Response.ok().build();
            }
            List<MovieInfo> extractedInfo = extractionService.extract123AndSaveAllLanguages(movie.link, movieCode);
            if (extractedInfo.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No movie info could be extracted from 123av.com for the specified code"))
                        .build();
            }
            return Response.ok(extractedInfo).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all movie information for a specific movie code
     *
     * @param movieCode The DVD code to get information for
     * @return A Response containing the movie information entities
     */
    @GET
    @Path("/info/{movieCode}")
    public Response getMovieInfo(@PathParam("movieCode") String movieCode) {
        List<MovieInfo> movieInfos = MovieInfo.list("code", movieCode);
        return Response.ok(movieInfos).build();
    }

    /**
     * Get movie information for a specific movie code and language
     *
     * @param movieCode The DVD code to get information for
     * @param language The language code (zh-tw, zh-cn, en, ja, etc.)
     * @return A Response containing the movie information entity
     */
    @GET
    @Path("/info/{movieCode}/{language}")
    public Response getMovieInfoForLanguage(
            @PathParam("movieCode") String movieCode,
            @PathParam("language") String language) {

        MovieInfo movieInfo = MovieInfo.find("code = ?1 and language = ?2",
                movieCode, language).firstResult();

        if (movieInfo == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Movie info not found for the specified code and language"))
                    .build();
        }

        return Response.ok(movieInfo).build();
    }
}
