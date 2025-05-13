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
import java.util.List;
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
    
    /**
     * Count how many records have the specified URL prefix
     * 
     * @param oldPrefix the prefix to search for
     * @return the number of records with the specified prefix
     */
    @Transactional
    public long countUrlsWithPrefix(String oldPrefix) {
        if (oldPrefix == null || oldPrefix.isEmpty()) {
            return 0;
        }
        return WatchUrl.count("originalUrl LIKE ?1", oldPrefix + "%");
    }
    
    /**
     * Get a batch of WatchUrl records with the specified URL prefix
     * 
     * @param oldPrefix the prefix to search for
     * @param batchSize the number of records to retrieve
     * @param page the page number (0-based) for pagination
     * @return a list of WatchUrl records
     */
    @Transactional
    public List<WatchUrl> getUrlBatch(String oldPrefix, int batchSize, int page) {
        if (oldPrefix == null || oldPrefix.isEmpty()) {
            return List.of();
        }
        return WatchUrl.find("originalUrl LIKE ?1", oldPrefix + "%")
                .page(page, batchSize)
                .list();
    }
    
    /**
     * Update a single WatchUrl record with a new prefix
     * 
     * @param watchUrl the WatchUrl record which might be detached
     * @param oldPrefix the current prefix to replace
     * @param newPrefix the new prefix to use
     * @return true if the update was successful, false otherwise
     */
    @Transactional
    public boolean updateSingleUrl(WatchUrl watchUrl, String oldPrefix, String newPrefix) {
        if (watchUrl == null || oldPrefix == null || oldPrefix.isEmpty() || newPrefix == null) {
            return false;
        }
        
        Long id = watchUrl.id;
        int movieId = watchUrl.getMovieId();
        String originalUrl = watchUrl.getOriginalUrl();
        
        if (originalUrl == null || !originalUrl.startsWith(oldPrefix)) {
            return false;
        }
        
        try {
            // Get a fresh attached entity instead of using the potentially detached one
            WatchUrl freshWatchUrl;
            if (id != null) {
                // If we have an ID, find by ID
                freshWatchUrl = WatchUrl.findById(id);
            } else {
                // Otherwise find by movieId (unique constraint)
                freshWatchUrl = WatchUrl.find("movieId = ?1", movieId).firstResult();
            }
            
            if (freshWatchUrl == null) {
                logger.warnf("Could not find WatchUrl with ID %d or movieId %d", id, movieId);
                return false;
            }
            
            // Replace the prefix
            String newUrl = newPrefix + originalUrl.substring(oldPrefix.length());
            freshWatchUrl.setOriginalUrl(newUrl);
            
            // Update m3u8 URL if needed by re-processing
            try {
                VScopeParseResult m3u8Result = movieParser.extractM3U8FromPlayer(newUrl);
                if (m3u8Result != null && m3u8Result.getStream() != null) {
                    freshWatchUrl.setUrl(m3u8Result.getStream());
                }
            } catch (Exception e) {
                logger.warnf("Failed to update m3u8 URL for movie ID %d: %s", 
                            movieId, e.getMessage());
            }
            
            // Persist changes - now with a managed entity
            freshWatchUrl.persist();
            logger.infof("Successfully updated URL for movie ID %d", movieId);
            return true;
        } catch (Exception e) {
            logger.errorf("Error updating URL for movie ID %d: %s", 
                        movieId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Legacy method for updating all URL prefixes at once (not recommended for large datasets)
     * 
     * @param oldPrefix the current prefix to replace
     * @param newPrefix the new prefix to use
     * @return the number of records updated
     * @deprecated Use batch processing instead
     */
    @Transactional
    public int updateOriginalUrlPrefix(String oldPrefix, String newPrefix) {
        if (oldPrefix == null || oldPrefix.isEmpty() || newPrefix == null) {
            logger.warn("Cannot update URLs with empty prefix");
            return 0;
        }

        logger.infof("Updating original URLs from prefix '%s' to '%s'", oldPrefix, newPrefix);
        
        // Find all WatchUrl records that have originalUrl starting with oldPrefix
        List<WatchUrl> watchUrls = WatchUrl.find("originalUrl LIKE ?1", oldPrefix + "%").list();
        
        // If no matching records, return 0
        if (watchUrls.isEmpty()) {
            logger.info("No watch URLs found with the specified prefix");
            return 0;
        }
        
        // Count of successfully updated records
        int updatedCount = 0;
        
        // Process each record
        for (WatchUrl watchUrl : watchUrls) {
            if (updateSingleUrl(watchUrl, oldPrefix, newPrefix)) {
                updatedCount++;
            }
        }
        
        logger.infof("Successfully updated %d/%d watch URLs", updatedCount, watchUrls.size());
        return updatedCount;
    }
}
