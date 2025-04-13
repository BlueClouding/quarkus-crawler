package org.acme.util;

import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for file operations
 */
public class FileUtils {
    
    private static final Logger logger = Logger.getLogger(FileUtils.class);
    private static final String DATA_DIR = "data";
    private static final String PROCESSED_IDS_FILE = "processed_ids.txt";
    private static final String REMOVE_FAILED_IDS_FILE = "remove_failed_ids.txt";
    
    // Thread-safe set to track processed IDs in memory
    private static final Set<Long> processedIdsCache = ConcurrentHashMap.newKeySet();
    
    /**
     * Initialize the data directory
     */
    public static void initDataDirectory() {
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdir();
            if (created) {
                logger.info("Created data directory: " + dataDir.getAbsolutePath());
            } else {
                logger.error("Failed to create data directory: " + dataDir.getAbsolutePath());
            }
        }
        
        // Load processed IDs into memory cache
        loadProcessedIds();
    }
    
    /**
     * Save a processed movie ID to file
     * 
     * @param movieId The movie ID to save
     */
    public static void saveProcessedId(Long movieId) {
        if (movieId == null) {
            return;
        }
        
        // Add to memory cache
        if (processedIdsCache.add(movieId)) {
            // Only write to file if it's a new ID
            try {
                Path filePath = Paths.get(DATA_DIR, PROCESSED_IDS_FILE);
                Files.createDirectories(filePath.getParent());
                
                // Append the ID to the file
                Files.write(
                    filePath, 
                    (movieId + System.lineSeparator()).getBytes(),
                    Files.exists(filePath) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE
                );
            } catch (IOException e) {
                logger.errorf("Error saving processed ID %d to file: %s", movieId, e.getMessage());
            }
        }
    }
    
    /**
     * Save a batch of processed movie IDs to file
     * 
     * @param movieIds The movie IDs to save
     */
    public static void saveProcessedIds(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return;
        }
        
        try {
            Path filePath = Paths.get(DATA_DIR, PROCESSED_IDS_FILE);
            Files.createDirectories(filePath.getParent());
            
            // Filter out IDs that are already in the cache
            List<Long> newIds = new ArrayList<>();
            for (Long id : movieIds) {
                if (processedIdsCache.add(id)) {
                    newIds.add(id);
                }
            }
            
            if (!newIds.isEmpty()) {
                // Build the content to append
                StringBuilder content = new StringBuilder();
                for (Long id : newIds) {
                    content.append(id).append(System.lineSeparator());
                }
                
                // Append the IDs to the file
                Files.write(
                    filePath, 
                    content.toString().getBytes(),
                    Files.exists(filePath) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE
                );
                
                logger.infof("Saved %d new processed IDs to file", newIds.size());
            }
        } catch (IOException e) {
            logger.errorf("Error saving processed IDs to file: %s", e.getMessage());
        }
    }
    
    /**
     * Load all processed IDs from file into memory
     * 
     * @return Set of processed movie IDs
     */
    public static Set<Long> loadProcessedIds() {
        processedIdsCache.clear();
        
        Path filePath = Paths.get(DATA_DIR, PROCESSED_IDS_FILE);
        if (!Files.exists(filePath)) {
            logger.info("Processed IDs file does not exist yet");
            return processedIdsCache;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Long id = Long.parseLong(line.trim());
                    processedIdsCache.add(id);
                } catch (NumberFormatException e) {
                    logger.warnf("Invalid ID in processed IDs file: %s", line);
                }
            }
            
            logger.infof("Loaded %d processed IDs from file", processedIdsCache.size());
        } catch (IOException e) {
            logger.errorf("Error loading processed IDs from file: %s", e.getMessage());
        }
        
        return processedIdsCache;
    }
    
    /**
     * Get all processed IDs from memory cache
     * 
     * @return Set of processed movie IDs
     */
    public static Set<Long> getProcessedIds() {
        return new HashSet<>(processedIdsCache);
    }
    
    /**
     * Save a failed remove operation ID to file
     * 
     * @param movieId The movie ID that failed to be removed
     */
    public static void saveRemoveFailedId(Long movieId) {
        if (movieId == null) {
            return;
        }
        
        try {
            Path filePath = Paths.get(DATA_DIR, REMOVE_FAILED_IDS_FILE);
            Files.createDirectories(filePath.getParent());
            
            // Append the ID to the file
            Files.write(
                filePath, 
                (movieId + System.lineSeparator()).getBytes(),
                Files.exists(filePath) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE
            );
        } catch (IOException e) {
            logger.errorf("Error saving remove failed ID %d to file: %s", movieId, e.getMessage());
        }
    }
    
    /**
     * Save a batch of failed remove operation IDs to file
     * 
     * @param movieIds The movie IDs that failed to be removed
     */
    public static void saveRemoveFailedIds(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return;
        }
        
        try {
            Path filePath = Paths.get(DATA_DIR, REMOVE_FAILED_IDS_FILE);
            Files.createDirectories(filePath.getParent());
            
            // Build the content to append
            StringBuilder content = new StringBuilder();
            for (Long id : movieIds) {
                content.append(id).append(System.lineSeparator());
            }
            
            // Append the IDs to the file
            Files.write(
                filePath, 
                content.toString().getBytes(),
                Files.exists(filePath) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE
            );
            
            logger.infof("Saved %d remove failed IDs to file", movieIds.size());
        } catch (IOException e) {
            logger.errorf("Error saving remove failed IDs to file: %s", e.getMessage());
        }
    }
    
    /**
     * Load all remove failed IDs from file
     * 
     * @return List of movie IDs that failed to be removed
     */
    public static List<Long> loadRemoveFailedIds() {
        List<Long> failedIds = new ArrayList<>();
        
        Path filePath = Paths.get(DATA_DIR, REMOVE_FAILED_IDS_FILE);
        if (!Files.exists(filePath)) {
            logger.info("Remove failed IDs file does not exist yet");
            return failedIds;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Long id = Long.parseLong(line.trim());
                    failedIds.add(id);
                } catch (NumberFormatException e) {
                    logger.warnf("Invalid ID in remove failed IDs file: %s", line);
                }
            }
            
            logger.infof("Loaded %d remove failed IDs from file", failedIds.size());
        } catch (IOException e) {
            logger.errorf("Error loading remove failed IDs from file: %s", e.getMessage());
        }
        
        return failedIds;
    }
}
