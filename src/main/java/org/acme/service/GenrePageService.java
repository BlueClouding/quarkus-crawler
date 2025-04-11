package org.acme.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import org.acme.entity.Genre;
import org.acme.entity.PagesProgress;
import org.acme.enums.CrawlerStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GenrePageService {
    private static final Logger logger = Logger.getLogger(GenrePageService.class.getName());

    @Inject
    private GenrePagesProcessService genrePagesProcessService;

    public boolean processGenresPages(int taskId) {
        List<Genre> allGenres = Genre.listAll();
        logger.info(String.format("Processing up to %d genres", allGenres.size()));

        // 单线程批处理，简化事务管理
        int processedCount = 0;
        for (int i = 0; i < allGenres.size(); i++) {
            Genre genre = allGenres.get(i);
            try {
                Long genreId = genre.id;
                String genreCode = genre.code;
                List<String> genreUrls = Optional.ofNullable(genre.urls).orElse(new ArrayList<>());

                if (genreUrls.isEmpty()) {
                    logger.warning(String.format("No URLs found for genre ID %d, skipping", genreId));
                    continue;
                }

                logger.info(String.format("Processing genre %d/%d: %s", i + 1, allGenres.size(), genreCode));

                // 获取当前进度
                Optional<PagesProgress> progressOpt = PagesProgress.find(
                        "crawlerProgressId = ?1 and relationId = ?2 and pageType = ?3 order by pageNumber desc limit 1",
                        taskId,
                        genreId,
                        "genre"
                ).firstResultOptional();

                int currentPage = progressOpt.map(PagesProgress::getPageNumber).orElse(0);
                String status = progressOpt.map(PagesProgress::getStatus).orElse("");
                if (currentPage > 0 && status.equals(CrawlerStatus.PROCESSING.getValue())) {
                    currentPage--;
                }

                int totalPages = Math.min(genre.total / 12, 5000);
                logger.info(String.format("Genre %s has %d pages, current progress: %d", genreCode, totalPages, currentPage));

                try {
                    genrePagesProcessService.processGenrePages(genreId, genreCode, genreUrls, totalPages, currentPage, taskId);
                } catch (Exception e) {
                    logger.severe(String.format("Error processing genre %s: %s", genreCode, e.getMessage()));
                }
            } catch (Exception genreError) {
                logger.severe(String.format("Error setting up genre %s: %s", genre.code, genreError.getMessage()));
            }
        }

        return true;
    }


}
