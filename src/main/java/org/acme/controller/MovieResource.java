package org.acme.controller;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import org.acme.entity.Movie;

@Path("/movies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MovieResource {

    @GET
    public List<Movie> listAll() {
        return Movie.listAll();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        Movie movie = Movie.findById(id);
        if (movie == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(movie).build();
    }

    @POST
    @Transactional
    public Response create(Movie movie) {
        Movie.persist(movie);
        return Response.status(Response.Status.CREATED).entity(movie).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Movie movie) {
        Movie existingMovie = Movie.findById(id);
        if (existingMovie == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        existingMovie.code = movie.code;
        existingMovie.duration = movie.duration;
        existingMovie.releaseDate = movie.releaseDate;
        existingMovie.coverImageUrl = movie.coverImageUrl;
        existingMovie.previewVideoUrl = movie.previewVideoUrl;
        existingMovie.likes = movie.likes;
        existingMovie.link = movie.link;
        existingMovie.originalId = movie.originalId;
        existingMovie.thumbnail = movie.thumbnail;
        existingMovie.title = movie.title;
        existingMovie.status = movie.status;
        existingMovie.description = movie.description;
        existingMovie.tags = movie.tags;
        existingMovie.genres = movie.genres;
        existingMovie.director = movie.director;
        existingMovie.maker = movie.maker;
        existingMovie.actresses = movie.actresses;
        existingMovie.watchUrlsInfo = movie.watchUrlsInfo;
        existingMovie.downloadUrlsInfo = movie.downloadUrlsInfo;
        existingMovie.magnets = movie.magnets;
        existingMovie.series = movie.series;
        return Response.ok(existingMovie).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Movie.deleteById(id);
        if (deleted) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }
}
