package org.acme.service;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.entity.WatchUrl;
import org.acme.enums.MovieStatus;
import org.acme.model.VScopeParseResult;
import org.jboss.logging.Logger;

import java.util.Date;
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
     * First creates or updates a Movie record with appropriate status:
     * - NEW: initially when processing starts
     * - ONLINE: when processing succeeds and watch URLs are available
     * - OFFLINE: when no watch URLs are available
     * - FAILED: when an error occurs during processing
     *
     * @param videoId the ID to process
     */
    @Transactional
    public void processVideoById(long videoId) {
        // Create or get movie record first
        WatchUrl watchUrl = null;
        try {
            // Check if the movie already exists
            watchUrl = WatchUrl.find("movieId = ?1", (int)videoId).firstResult();

            // If it doesn't exist yet, create a new one
            if (watchUrl == null) {
                watchUrl = new WatchUrl();
                watchUrl.setMovieId((int) videoId);
                watchUrl.setStatus(MovieStatus.NEW.getValue());
                watchUrl.persist();
                logger.infof("Created new movie record for ID %d with status %s", videoId, MovieStatus.NEW.getValue());
            }

            logger.infof("Processing video ID: %d", videoId);

            Optional<MovieParser.VideoUrlsResult> result = movieParser.getVideoUrls(videoId);

            if (result.isPresent()) {
                // Get the result and check if it has any watch or download URLs
                MovieParser.VideoUrlsResult urlsResult = result.get();
                boolean hasWatchUrls = urlsResult.watch() != null && !urlsResult.watch().isEmpty();
                boolean hasDownloadUrls = urlsResult.download() != null && !urlsResult.download().isEmpty();

                // Process based on availability of URLs
                if (!hasWatchUrls && !hasDownloadUrls) {
                    // Mark as offline if no URLs available
                    watchUrl.setStatus(MovieStatus.NO_RESULT.getValue());
                    watchUrl.persist();
                    logger.infof("Video ID %d has no watch or download URLs, status set to %s", videoId, MovieStatus.OFFLINE.getValue());
                    return;
                }

                // Try to save watch and download URLs
                try {
                    processVideoUrls(urlsResult, watchUrl, (int)videoId);
                    // Update movie with successful status
                    watchUrl.setStatus(MovieStatus.ONLINE.getValue());
                    // Could add more movie data here if available from urlsResult
                    watchUrl.persist();
                    logger.infof("Successfully processed video ID: %d, status set to %s", videoId, MovieStatus.ONLINE.getValue());
                } catch (Exception e) {
                    // Update movie with failure status but don't throw exception yet
                    watchUrl.setStatus(MovieStatus.FAILED.getValue());
                    watchUrl.persist();
                    logger.errorf("Error processing video ID %d: %s", videoId, e.getMessage());
                    throw e; // Re-throw to be handled by the controller
                }
            } else {
                // No data found, mark as offline
                watchUrl.setStatus(MovieStatus.OFFLINE.getValue());
                watchUrl.persist();
                logger.infof("No data found for video ID: %d, status set to %s", videoId, MovieStatus.OFFLINE.getValue());
            }
        } catch (Exception e) {
            // Make sure to mark the movie as failed if we have a reference to it
            if (watchUrl != null) {
                watchUrl.setStatus(MovieStatus.FAILED.getValue());
                try {
                    watchUrl.persist();
                } catch (Exception persistException) {
                    logger.errorf("Failed to update movie status: %s", persistException.getMessage());
                }
            }
            logger.errorf("Unexpected error processing video ID %d: %s", videoId, e.getMessage());
        }
    }

    private void processVideoUrls(MovieParser.VideoUrlsResult urlsResult, WatchUrl watchUrl, int movieId) {
        // Check if there are watch URLs to process
        if (urlsResult.watch() != null && !urlsResult.watch().isEmpty()) {
            // Iterate through the list of WatchInfo objects
            for (MovieParser.WatchInfo watchInfo : urlsResult.watch()) {
                VScopeParseResult m3u8Result = movieParser.extractM3U8FromPlayer(watchInfo.url());
                // Find existing WatchUrl or create a new one if it doesn't exist
                if (watchUrl == null) {
                    // Create a new WatchUrl instance
                    watchUrl = new WatchUrl();
                    watchUrl.setMovieId(movieId);
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
