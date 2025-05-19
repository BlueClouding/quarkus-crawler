package org.acme.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.entity.MovieInfo;
import org.acme.extractor.MovieInfoExtractor;
import org.acme.util.FailedUrlLogger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class MovieInfoExtractionService {

    // Create language mapping with a Map.ofEntries to handle more than 10 entries
    private static final Map<String, String> LANGUAGE_CODES = Map.ofEntries(
            Map.entry("", "zh-tw"), // Traditional Chinese (default)
            Map.entry("cn", "zh-cn"), // Simplified Chinese
            Map.entry("en", "en"), // English
            Map.entry("ja", "ja"), // Japanese
            Map.entry("ko", "ko"), // Korean
            Map.entry("ms", "ms"), // Malay
            Map.entry("th", "th"), // Thai
            Map.entry("de", "de"), // German
            Map.entry("fr", "fr"), // French
            Map.entry("vi", "vi"), // Vietnamese
            Map.entry("id", "id"), // Indonesian
            Map.entry("fil", "fil"), // Filipino
            Map.entry("pt", "pt") // Portuguese
    );

    // Base URL
    private static final String BASE_URL = "https://missav.ai/";
    /**
     * Gets the URL for a specific DVD code and language
     * 
     * @param dvdCode The DVD code
     * @param languageCode The language code (empty for default)
     * @return The full URL
     */
    private static String getLanguageUrl(String dvdCode, String languageCode) {
        if (languageCode.isEmpty()) {
            return BASE_URL + dvdCode;
        } else {
            return BASE_URL + languageCode + "/" + dvdCode;
        }
    }

    /**
     * Extracts movie information for all 13 supported languages and saves it to the database.
     * 
     * @param movieCode The DVD code of the movie to extract information for
     * @return A list of the created MovieInfo entities
     */
    @Transactional
    public List<MovieInfo> extractAndSaveAllLanguages(String movieCode) {
        movieCode = movieCode.toLowerCase();
        List<MovieInfo> savedEntities = new ArrayList<>();
        UUID movieUuid = UUID.randomUUID(); // Generate a common UUID for all language versions

        for (Map.Entry<String, String> languageEntry : LANGUAGE_CODES.entrySet()) {
            // 添加延迟以避免请求过于频繁
            try {
                // 在请求之间添加500毫秒的延迟
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("Sleep interrupted between language requests");
            }
           
            String languagePathCode = languageEntry.getKey();
            String languageDbCode = languageEntry.getValue();
            
            // Extract movie information for this language
            Map<String, Object> extractedInfo;
            String url = getLanguageUrl(movieCode, languagePathCode);
            try {
                extractedInfo = MovieInfoExtractor.extractMovieInfoForLanguage(url);
            } catch (Exception e) {
                // 记录错误日志
                Log.error("Failed to extract movie info for " + url, e);
                // 记录失败的URL到文件
                FailedUrlLogger.logFailedUrl(url, e.getMessage());
                // 继续处理其他语言版本，不中断整个流程
                continue;
            }
            
            // Create and save MovieInfo entity
            MovieInfo movieInfo = createMovieInfoFromExtracted(extractedInfo, movieCode, movieUuid, languageDbCode);
            movieInfo.persist();
            savedEntities.add(movieInfo);
            
            Log.info("Saved movie info for " + movieCode + " in language: " + languageDbCode);
            
        }
        
        return savedEntities;
    }
    
    /**
     * Creates a MovieInfo entity from extracted data.
     */
    private MovieInfo createMovieInfoFromExtracted(Map<String, Object> extractedInfo, 
                                                  String movieCode, 
                                                  UUID movieUuid, 
                                                  String language) {
        MovieInfo movieInfo = new MovieInfo();
        
        // Set basic fields
        movieInfo.code = movieCode;
        movieInfo.movieUuid = movieUuid;
        movieInfo.language = language;
        
        // Set content fields
        movieInfo.title = (String) extractedInfo.get("title");
        movieInfo.description = (String) extractedInfo.get("description");
        
        // Handle m3u8 info
        @SuppressWarnings("unchecked")
        List<String> m3u8Info = (List<String>) extractedInfo.get("m3u8_info");
        movieInfo.m3u8Info = m3u8Info != null ? m3u8Info : Collections.emptyList();
        
        // Handle dates
        movieInfo.websiteDate = parseDate((String) extractedInfo.get("website_date"));
        movieInfo.releaseDate = parseDate((String) extractedInfo.get("release_date"));
        
        // Handle duration
        String durationStr = (String) extractedInfo.get("duration");
        if (durationStr != null && !durationStr.isEmpty()) {
            try {
                movieInfo.duration = Integer.parseInt(durationStr);
            } catch (NumberFormatException e) {
                Log.warn("Could not parse duration: " + durationStr);
            }
        }
        
        // Handle cover URL
        movieInfo.coverUrl = (String) extractedInfo.get("cover_url");
        
        // Handle metadata fields
        movieInfo.series = (String) extractedInfo.get("series");
        movieInfo.label = (String) extractedInfo.get("label");
        movieInfo.maker = (String) extractedInfo.get("maker");
        movieInfo.director = (String) extractedInfo.get("director"); // Now using the proper director field
        
        // Handle lists
        // Actress field
        String actressStr = (String) extractedInfo.get("actor");
        if (actressStr != null && !actressStr.isEmpty()) {
            movieInfo.actresses = Collections.singletonList(actressStr);
        } else {
            movieInfo.actresses = Collections.emptyList();
        }
        
        // Genres
        @SuppressWarnings("unchecked")
        List<String> genres = (List<String>) extractedInfo.get("genres");
        if (genres != null) {
            movieInfo.genres = genres;
        } else {
            movieInfo.genres = Collections.emptyList();
        }
        
        // Tags (can be same as genres initially)
        movieInfo.tags = movieInfo.genres;
        
        return movieInfo;
    }
    
    /**
     * Parses a date string into a LocalDate.
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        // Try different date formats
        String[] dateFormats = {
                "yyyy-MM-dd",
                "yyyy/MM/dd",
                "dd-MM-yyyy",
                "MM/dd/yyyy"
        };
        
        for (String format : dateFormats) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format));
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        
        Log.warn("Could not parse date: " + dateStr);
        return null;
    }
}
