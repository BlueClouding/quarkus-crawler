package org.acme.service;

import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

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
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                    .addHeader("accept", "application/json, text/javascript, */*; q=0.01")
                    .addHeader("accept-language", "zh-CN,zh;q=0.9")
                    .addHeader("cookie", "_ga=GA1.1.1363112149.1736345509; locale=en; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=358075%7CfgZfypH0KzGXrqu7GkftJUbkPJiTHzi5vXD6rqOtFxR84AvlPyI4okC2xBGr%7C%242y%2412%24a4vCnvNHaaRhs27lVnsQiewF%2FZ660tcdkKBfTGwD3SwHmNa.5qlH.; session=Z97mK3D7kkNfSMKYI56wumMypDQRGnTo624sG7T6; x-token=685e5019136fc8e1b78321554f17983a; _ga_VZGC2QQBZ8=GS2.1.s1747148723$o53$g1$t1747148778$j0$l0$h0")
                    .addHeader("x-requested-with", "XMLHttpRequest")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.severe(String.format("Failed to process page %d: HTTP %d", page, response.code()));
                    return movies;
                }
                
                String responseBody = response.body().string();
                movies = movieParser.extractMovieLinks(responseBody, "https://123av.com/ja");
            }
            logger.info(String.format("Successfully extracted %d movies from page %d", movies.size(), page));

        } catch (IOException e) {
            logger.severe(String.format("Error processing page %d: %s", page, e.getMessage()));
        } catch (Exception e) {
            logger.severe(String.format("Unexpected error processing page %d: %s", page, e.getMessage()));
        }
        return movies;
    }
}
