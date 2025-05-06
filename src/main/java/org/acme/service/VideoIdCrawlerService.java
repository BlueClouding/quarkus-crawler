package org.acme.service;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.WatchUrl;
import org.acme.model.VScopeParseResult;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Service to crawl videos by ID from 1 to 200000
 */
@ApplicationScoped
public class VideoIdCrawlerService {

    private static final Logger logger = Logger.getLogger(VideoIdCrawlerService.class);

    @Inject
    MovieParser movieParser;

    // This method is no longer needed as the controller now handles batch processing

    /**
     * Process a video by its ID
     *
     * @param videoId the ID to process
     */
    @Transactional
    public void processVideoById(long videoId) {
        try {
            logger.infof("Processing video ID: %d", videoId);

            Optional<MovieParser.VideoUrlsResult> result = movieParser.getVideoUrls(videoId);

            if (result.isPresent()) {
                // Get the result and check if it has any watch or download URLs
                MovieParser.VideoUrlsResult urlsResult = result.get();
                boolean hasWatchUrls = urlsResult.watch() != null && !urlsResult.watch().isEmpty();
                boolean hasDownloadUrls = urlsResult.download() != null && !urlsResult.download().isEmpty();

                // Only proceed if we have at least some data
                if (!hasWatchUrls && !hasDownloadUrls) {
                    logger.infof("Video ID %d has no watch or download URLs", videoId);
                    return;
                }

                // Save watch and download URLs
                try {
                    processVideoUrls(urlsResult, (int) videoId);
                    logger.infof("Successfully saved video ID: %d", videoId);
                } catch (Exception e) {
                    logger.errorf("Error saving video ID %d: %s", videoId, e.getMessage());
                    throw e; // Re-throw to be handled by the controller
                }
            } else {
                logger.infof("No data found for video ID: %d", videoId);
            }
        } catch (Exception e) {
            logger.errorf("Unexpected error processing video ID %d: %s", videoId, e.getMessage());
            throw e; // Re-throw to be handled by the controller
        }
    }

    private void processVideoUrls(MovieParser.VideoUrlsResult urlsResult, int index) {
        // Check if there are watch URLs to process
        if (urlsResult.watch() != null && !urlsResult.watch().isEmpty()) {
            // Iterate through the list of WatchInfo objects
            for (MovieParser.WatchInfo watchInfo : urlsResult.watch()) {
                VScopeParseResult m3u8Result = movieParser.extractM3U8FromPlayer(watchInfo.url());
                // Find existing WatchUrl or create a new one if it doesn't exist
                WatchUrl watchUrl = WatchUrl.find("movieId = ?1", m3u8Result.getVideoId()).firstResult();
                if (watchUrl == null) {
                    // Create a new WatchUrl instance
                    watchUrl = new WatchUrl();
                    watchUrl.setMovieId(index);
                }
                watchUrl.setUrl(m3u8Result.getStream());
                watchUrl.setIndex(watchInfo.index());
                watchUrl.setName(watchInfo.name());
                watchUrl.setOriginalUrl(watchInfo.url());
                watchUrl.persist();
            }
        }
    }
}
