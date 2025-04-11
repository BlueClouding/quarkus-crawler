package org.acme;

import java.util.List;

import org.acme.entity.Genre;
import org.acme.enums.SupportedLanguage;
import org.acme.service.GenreService;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/crawler/genres")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GenresResource {

    @Inject
    GenreService genreService;

    @GET
    public Response getAllGenres() {
        List<Genre> allGenres = genreService.getAllGenres();
        return Response.ok(allGenres).build();
    }

    @POST
    @Path("/start")
    @Transactional
    public Response startGenresCrawler() {
        genreService.processGenres("https://123av.com", SupportedLanguage.JAPANESE.getValue());
        return Response.ok().build();
    }


}
