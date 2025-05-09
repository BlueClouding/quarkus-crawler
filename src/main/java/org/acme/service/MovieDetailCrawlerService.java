package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.acme.entity.Movie;
import org.jboss.logging.Logger;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.Optional;

@ApplicationScoped
public class MovieDetailCrawlerService {

    private static final Logger logger = Logger.getLogger(MovieDetailCrawlerService.class);

    @Inject
    MovieParser movieParser;

    private final OkHttpClient httpClient;

    @Inject
    public MovieDetailCrawlerService() {
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(true)
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
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                    .addHeader("accept", "application/json, text/javascript, */*; q=0.01")
                    .addHeader("accept-language", "zh-CN,zh;q=0.9")
                    .addHeader("cookie", "_ga=GA1.1.1641394730.1737617680; _ga_VZGC2QQBZ8=GS1.1.1744253403.22.1.1744254946.0.0.0")
                    .addHeader("x-requested-with", "XMLHttpRequest")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.errorf("Failed to fetch movie details for %s: HTTP %d", url, response.code());
                    return null;
                }

                // Parse movie details
                String responseBody = response.body().string();
                Optional<Movie> movieDetail = movieParser.parseMoviePage(movie, responseBody, url);
            return movieDetail.orElse(null);

            }
        } catch (IOException e) {
            logger.errorf("Error processing movie %s: %s", url, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.errorf("Unexpected error processing movie %s: %s", url, e.getMessage());
            return null;
        }
    }
}
