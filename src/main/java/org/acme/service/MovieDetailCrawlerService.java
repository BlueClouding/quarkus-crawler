package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.acme.entity.Movie;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

@ApplicationScoped
public class MovieDetailCrawlerService {

    private static final Logger logger = Logger.getLogger(MovieDetailCrawlerService.class);

    @Inject
    MovieParser movieParser;

    private final HttpClient httpClient;

    @Inject
    public MovieDetailCrawlerService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // Assuming you have a method to modify the URL if needed
    private String modifyUrl(String url) {
        // Implement your URL modification logic here
        return url;
    }

    public Movie processMovie(Movie movie) {
        String url = movie.getLink();
        url = modifyUrl(url);
        movie.setLink(url);

        try {
            logger.infof("Fetching details for movie: %s", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.errorf("Failed to fetch movie details for %s: HTTP %d", url, response.statusCode());
                return null;
            }

            // Parse movie details
            Optional<Movie> movieDetail = movieParser.parseMoviePage(movie, response.body(), url);
            return movieDetail.orElse(null);

        } catch (IOException | InterruptedException e) {
            logger.errorf("Error processing movie %s: %s", url, e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.errorf("Unexpected error processing movie %s: %s", url, e.getMessage());
            return null;
        }
    }
}
