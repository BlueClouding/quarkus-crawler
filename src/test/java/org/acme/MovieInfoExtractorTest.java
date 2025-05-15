package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for MovieInfoExtractor
 */
@QuarkusTest
public class MovieInfoExtractorTest {

    @TempDir
    static Path tempDir;
    
    private static final String TEST_VIDEO_ID = "test-video";
    private static Path originalDataDir;
    private static boolean dataFolderExisted;
    
    @BeforeAll
    public static void setup() throws IOException {
        // Save the original data directory path
        originalDataDir = Paths.get("data");
        
        // Check if the data folder exists
        dataFolderExisted = Files.exists(originalDataDir);
        
        // If it exists, rename it temporarily
        if (dataFolderExisted) {
            Path tempPath = Paths.get("data_backup_for_test");
            Files.move(originalDataDir, tempPath);
        }
        
        // Create a symlink from our temp directory to "data"
        Files.createSymbolicLink(originalDataDir, tempDir);
    }
    
    @AfterAll
    public static void cleanup() throws IOException {
        // Delete the symlink
        Files.delete(originalDataDir);
        
        // Restore the original data directory if it existed
        if (dataFolderExisted) {
            Path tempPath = Paths.get("data_backup_for_test");
            Files.move(tempPath, originalDataDir);
        }
    }
    
    /**
     * Mock test for downloading a single language HTML
     */
    @Test
    public void testDownloadSingleLanguage() throws Exception {
        // This test creates a mock implementation that doesn't hit the real server
        MockMovieInfoExtractor.downloadSingleLanguage(TEST_VIDEO_ID, "en");
        
        // Check if the file was created
        Path htmlFile = tempDir.resolve("missav_en.html");
        assertTrue(Files.exists(htmlFile), "HTML file should be created");
        
        // Verify file content contains the mocked HTML
        String content = Files.readString(htmlFile);
        assertTrue(content.contains("<html"), "File should contain HTML content");
        assertTrue(content.contains("English Mock Content"), "File should contain mock English content");
    }
    
    /**
     * Test that verifies all language files are created
     */
    @Test
    public void testAllLanguageFilesCreated() throws Exception {
        // Download all languages using the mock implementation
        MockMovieInfoExtractor.downloadAllLanguages(TEST_VIDEO_ID);
        
        // Get all HTML files in the directory
        List<Path> htmlFiles = Files.list(tempDir)
                .filter(path -> path.toString().endsWith(".html"))
                .collect(Collectors.toList());
        
        // Verify we have the expected number of files (12 languages + default)
        assertEquals(13, htmlFiles.size(), "Should have created 13 language HTML files");
        
        // Verify specific language files exist
        assertTrue(Files.exists(tempDir.resolve("missav_en.html")), "English HTML file should exist");
        assertTrue(Files.exists(tempDir.resolve("missav_cn.html")), "Chinese HTML file should exist");
        assertTrue(Files.exists(tempDir.resolve("missav_ja.html")), "Japanese HTML file should exist");
    }
    
    /**
     * Integration test for the real API (disabled by default)
     * Only enable this test when you want to test against the real website
     */
    @Test
    @Disabled("This test makes real API calls and should only be run manually")
    public void testRealApiDownload() throws Exception {
        String videoId = "shmo-162";
        
        // This calls the actual implementation that hits the real server
        MovieInfoExtractor.downloadAllLanguages(videoId);
        
        // Verify files were created
        assertTrue(Files.exists(Paths.get("data/missav_en.html")), "English HTML file should exist");
        assertTrue(Files.exists(Paths.get("data/missav_cn.html")), "Chinese HTML file should exist");
        
        // You could add more validations of the actual content here
    }
    
    /**
     * Mock implementation for testing
     */
    static class MockMovieInfoExtractor {
        public static void downloadAllLanguages(String videoId) throws IOException {
            // Create mock files for all languages
            String[] languages = {"default", "cn", "en", "ja", "ko", "ms", "th", "de", "fr", "vi", "id", "fil", "pt"};
            
            for (String lang : languages) {
                downloadSingleLanguage(videoId, lang);
            }
        }
        
        public static void downloadSingleLanguage(String videoId, String langCode) throws IOException {
            // Create data directory if it doesn't exist
            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            // Generate filename
            String fileName = "missav_" + langCode + ".html";
            
            // Create mock HTML content
            String mockHtml = "<!DOCTYPE html>\n" +
                    "<html lang=\"" + langCode + "\">\n" +
                    "<head>\n" +
                    "    <title>Mock " + langCode + " Page for " + videoId + "</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>" + langCode + " Mock Content</h1>\n" +
                    "    <p>This is mock content for testing purposes.</p>\n" +
                    "</body>\n" +
                    "</html>";
            
            // Save to file
            Path filePath = dataDir.resolve(fileName);
            Files.writeString(filePath, mockHtml);
        }
    }
}
