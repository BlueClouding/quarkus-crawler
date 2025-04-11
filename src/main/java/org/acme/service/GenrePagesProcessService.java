package org.acme.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.acme.entity.Movie;
import org.acme.entity.PagesProgress;
import org.acme.enums.CrawlerStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.inject.Inject;

@ApplicationScoped
public class GenrePagesProcessService {

    private static final Logger logger = Logger.getLogger(GenrePagesProcessService.class.getName());
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject
    private MovieParser movieParser;

    @Inject
    private MovieService movieService;

    protected boolean processGenrePages(Long genreId, String genreCode, List<String> genreUrls, int totalPages, int currentPage, int taskId) {
        for (int page = currentPage + 1; page <= totalPages; page++) {
            try {
                logger.info(String.format("Processing page %d/%d for genre %s", page, totalPages, genreCode));
                List<Movie> movies = processPageGetMovies(genreUrls.getFirst(), page);
                if (movies.isEmpty()) {
                    break;
                }

                int successCount = 0;
                // Process each movie in a separate transaction
                for (Movie movie : movies) {
                    try {
                        movieService.saveOrUpdateMovie(movie);
                        successCount++;
                    } catch (Exception e) {
                        logger.severe(String.format("Error persisting movie %s: %s", movie.title, e));
                    }
                }

                try {
                    // 保存进度信息 - in a separate transaction
                    saveOrUpdateGenrePageProgress(
                            genreId.intValue(),
                            page,
                            totalPages,
                            totalPages == 12 ? CrawlerStatus.COMPLETED.getValue() : CrawlerStatus.PENDING.getValue(),
                            successCount,
                            taskId
                    );
                } catch (Exception e) {
                    logger.severe(String.format("Error saving progress for genre %s page %d: %s", genreCode, page, e.getMessage()));
                }
            } catch (Exception pageError) {
                logger.log(java.util.logging.Level.SEVERE, String.format("Error processing page %d for genre %s: %s", page, genreCode, pageError.getMessage()), pageError);
            }
        }
        return true;
    }

    @Transactional
    public Long saveOrUpdateGenrePageProgress(
            Integer genreId,
            Integer page,
            Integer totalPages,
            String status,
            Integer totalItems,
            Integer taskId
    ) {
        try {
            PagesProgress progress = PagesProgress.find(
                    "relationId = ?1 and pageNumber = ?2 and pageType = ?3",
                    genreId,
                    page,
                    "genre"
            ).firstResult();

            if (progress == null) {
                // 插入新记录
                progress = new PagesProgress();
                progress.setRelationId(genreId);
                progress.setPageNumber(page);
                progress.setPageType("genre");
                progress.setCrawlerProgressId(taskId);
            }

            // 无论是新建还是已有，统一更新字段
            progress.setTotalPages(totalPages);
            progress.setStatus(status != null ? status : "pending");
            progress.setTotalItems(totalItems);

            progress.persist(); // 会自动 insert 或 update

            return progress.id;
        } catch (Exception e) {
            logger.severe("Error in saveOrUpdateGenrePageProgress: " + e.getMessage());
            throw e; // Re-throw to trigger transaction rollback
        }
    }

    protected List<Movie> processPageGetMovies(String baseUrl, int page) {
        baseUrl = baseUrl.replace("www.", "");
        List<Movie> movies = new ArrayList<>();
        try {
            String url = String.format("%s?page=%d", baseUrl, page);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.severe(String.format("Failed to process page %d: HTTP %d", page, response.statusCode()));
                return movies;
            }

            movies = movieParser.extractMovieLinks(response.body(), "https://123av.com/ja");
            logger.info(String.format("Successfully extracted %d movies from page %d", movies.size(), page));

        } catch (IOException | InterruptedException e) {
            logger.severe(String.format("Error processing page %d: %s", page, e.getMessage()));
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.severe(String.format("Unexpected error processing page %d: %s", page, e.getMessage()));
        }
        return movies;
    }
}
