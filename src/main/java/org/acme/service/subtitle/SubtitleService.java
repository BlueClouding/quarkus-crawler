package org.acme.service.subtitle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.acme.entity.Movie;
import org.acme.util.SubtitleCatAPI;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Service for managing subtitle-related operations
 */
@ApplicationScoped
public class SubtitleService {

    private static final Logger logger = Logger.getLogger(SubtitleService.class);
    
    /**
     * Fetches subtitle links for movies from the database
     * 
     * @param limit Maximum number of movies to process (default 50)
     * @param onlyWithCode Only process movies that have a non-null code (default true)
     * @return Map of movie codes to their subtitle links
     */
    @Transactional
    public Map<String, String> fetchSubtitleLinksForMovies(int limit, boolean onlyWithCode) {
        logger.info("Fetching subtitle links for movies");
        
        // Prepare query to get movies
        String query = onlyWithCode ? "code is not null" : "1=1";
        List<Movie> movies = Movie.find(query)
                .page(0, limit)
                .list();
        
        logger.infof("Found %d movies to process for subtitles", movies.size());
        Map<String, String> results = new HashMap<>();
        List<String> failedCodes = new ArrayList<>();
        
        // Process each movie
        for (Movie movie : movies) {
            if (movie.code == null || movie.code.trim().isEmpty()) {
                continue; // Skip movies without codes if we're still getting some
            }
            
            String code = movie.code.trim();
            try {
                // Call the SubtitleCatAPI to get subtitle links
                String subtitleLinks = SubtitleCatAPI.searchSubtitles(code);
                
                if (subtitleLinks != null && !subtitleLinks.isEmpty()) {
                    results.put(code, subtitleLinks);
                    logger.infof("Successfully found subtitles for movie code: %s", code);
                } else {
                    logger.infof("No subtitles found for movie code: %s", code);
                }
                
                // Add a small delay to avoid overwhelming the API
                Thread.sleep(1000);
                
            } catch (IOException e) {
                failedCodes.add(code);
                logger.warnf("Error fetching subtitles for movie code %s: %s", code, e.getMessage());
            } catch (InterruptedException e) {
                logger.warn("Thread interrupted while processing subtitles");
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        logger.infof("Subtitle processing complete. Success: %d, Failed: %d", 
                results.size(), failedCodes.size());
        
        if (!failedCodes.isEmpty()) {
            logger.infof("Failed codes: %s", String.join(", ", failedCodes));
        }
        
        return results;
    }
    
    /**
     * Overloaded method with default parameters
     * @return Map of movie codes to their subtitle links
     */
    @Transactional
    public Map<String, String> fetchSubtitleLinksForMovies() {
        // Default: 50 movies, only those with codes
        return fetchSubtitleLinksForMovies(50, true);
    }
}
