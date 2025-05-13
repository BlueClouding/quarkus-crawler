package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.acme.entity.Genre;
import org.acme.model.GenreInfo;

@ApplicationScoped
public class GenreService {

    private static final Logger logger = Logger.getLogger(GenreService.class.getName());

    @Inject
    GenreParser genreParser;

    @Inject
    MovieParser movieParser;

    private Optional<Integer> maxGenres = Optional.empty(); // Default no limit

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .followRedirects(true)
        .build();

    public void processGenres(String baseUrl, String language) {
        logger.info("Processing genres...");
        List<GenreInfo> genres = fetchGenres(baseUrl, language);
        if (genres.isEmpty()) {
            logger.severe("Failed to fetch genres");
            return;
        }

        logger.info(String.format("Found %d genres", genres.size()));
        logger.info("Processing genre pages...");

        int limit = maxGenres.orElse(genres.size());
        logger.info(String.format("Processing up to %d genres", limit));

        saveGenresToDb(genres, language);

        logger.info("Successfully processed all genres");
    }

    protected List<GenreInfo> fetchGenres(String baseUrl, String language) {
        List<GenreInfo> genres = new ArrayList<>();
        try {
            String url = String.format("%s/%s/genres", baseUrl, language);
            logger.info(String.format("Fetching genres from: %s", url));
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
                    logger.severe(String.format("Failed to fetch genres: HTTP %d", response.code()));
                    return genres;
                }
                
                String responseBody = response.body().string();
                genres = genreParser.parseGenresPage(responseBody, baseUrl);
                logger.info(String.format("Found %d genres", genres.size()));
            }

        } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE, String.format("Error fetching genres: %s", e.getMessage()), e);
        }
        return genres;
    }

    // Placeholder for getGenreName - needs more context about GenreName entity
    protected String getGenreName(Long genreId) {
        try {
            Optional<Genre> genreOptional = Genre.findByIdOptional(genreId);
            if (genreOptional.isPresent()) {
                Genre genre = genreOptional.get();
                return String.format("Genre %s", Optional.ofNullable(genre.code).orElse(genreId.toString()));
            }
            return String.format("Genre %d", genreId);
        } catch (Exception e) {
            logger.severe(String.format("Error getting genre name: %s", e.getMessage()));
            return String.format("Genre %d", genreId);
        }
    }

    @Transactional
    protected void saveGenresToDb(List<GenreInfo> genreInfos, String language) {
        for (GenreInfo genreInfo : genreInfos) {
            try {
                insertOrUpdateGenre(genreInfo);
                logger.info(String.format("Created new genre: %s with code %s", genreInfo.getName(), genreInfo.getCode()));

            } catch (Exception genreError) {
                logger.log(java.util.logging.Level.SEVERE, String.format("Error processing genre %s: %s", genreInfo.getName(), genreError.getMessage()), genreError);
            }
        }
    }

    @Transactional
    protected void insertOrUpdateGenre(GenreInfo genreInfo) {
        Genre existingGenre = Genre.find("code", genreInfo.getCode()).firstResult();

        if (existingGenre != null) {
            // Update existing genre
            existingGenre.urls = List.of(genreInfo.getUrl());
            existingGenre.total = genreInfo.getTotal();
            existingGenre.persist(); // Panache 会自动识别这是一个更新操作，因为 existingGenre 已经有 ID
        } else {
            // Insert new genre
            Genre newGenre = new Genre();
            newGenre.urls = List.of(genreInfo.getUrl());
            newGenre.code = genreInfo.getCode();
            newGenre.createdAt = Instant.now();
            newGenre.total = genreInfo.getTotal();
            newGenre.persist(); // Panache 会识别这是一个插入操作，因为 newGenre 还没有 ID
        }
    }


    public List<Genre> getAllGenres() {
        try {
            return Genre.listAll();
        } catch (Exception e) {
            logger.severe(String.format("Error getting all genres: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }
}
