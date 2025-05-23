package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.acme.enums.Av123LanguageCode;
import org.acme.enums.MissAvLanguageCode;
import org.acme.service.MovieDetailCrawlerService;
import org.acme.service.MovieInfoExtractionService;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@QuarkusTest
public class MovieDetailCrawlerServiceTest {

    @Inject
    MovieDetailCrawlerService movieDetailCrawlerService;
    
    @Inject
    MovieInfoExtractionService movieInfoExtractionService;

    private final OkHttpClient client = new OkHttpClient();

    /**
     * Test method to save HTML responses for different languages to the data directory.
     * Each response is saved with a timestamp and language code in the filename.
     *
     * @throws IOException If an I/O error occurs
     */
    @Test
    public void testMissavSaveHtmlResponses() throws IOException {
        // Create data directory if it doesn't exist
        Path dataDir = Paths.get("data1");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        for (MissAvLanguageCode languageCode : MissAvLanguageCode.values()) {
            String languagePathCode = languageCode.getPathCode();
            String languageDbCode = languageCode.getDbCode();

         
            // 构建特定语言的URL
            String prefix = "https://missav.ai/";
            String movieCode = "midv-936";
            String movieLink = "midv-936";
            if (!languagePathCode.isEmpty()) {
                prefix += languagePathCode + "/";
            }
            String url = prefix + movieLink;
            
        
            // Build request with appropriate headers
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept-Language", languageDbCode)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Request failed with code: " + response.code() + " for language: " + languageDbCode);
                    continue;
                }

                // Get response body
                String responseBody = response.body().string();
                
                // Create filename with timestamp and language
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = String.format("movie_%s_lang_%s_%s.html", movieCode, languageDbCode, timestamp);
                Path filePath = dataDir.resolve(filename);
                
                // Save response to file
                Files.write(filePath, responseBody.getBytes(StandardCharsets.UTF_8));
                
                System.out.println("Saved response for language " + languageDbCode + " to " + filePath);
            }
        }
    }

    @Test
    public void testMissavPatternExtraction() throws IOException {
        // Path to the data directory
        Path dataDir = Paths.get("data");
        
        // Check if directory exists
        if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) {
            System.err.println("Data directory not found: " + dataDir);
            return;
        }
        
        // Get list of HTML files
        Files.list(dataDir)
            .filter(path -> path.toString().endsWith(".html"))
            .filter(path -> path.getFileName().toString().contains("movie_"))
            .forEach(htmlFilePath -> {
                try {
                    System.out.println("\n===== Processing file: " + htmlFilePath.getFileName() + " =====");
                    
                    // Read HTML content
                    String htmlContent = Files.readString(htmlFilePath, StandardCharsets.UTF_8);
                    
                    // Parse the HTML using Jsoup
                    Document doc = Jsoup.parse(htmlContent);
                    
                    // Extract information using the patterns
                    String title = extractTitle(doc);
                    Map<String, String> details = extractDetails(doc);
                    String movieId = extractMovieId(doc);
                    String coverUrl = extractCoverUrl(doc);
                    
                    // Print the extracted information
                    System.out.println("Extracted Title: " + title);
                    System.out.println("Extracted Movie ID: " + movieId);
                    System.out.println("Extracted Cover URL: " + coverUrl);
                    System.out.println("Extracted Details:");
                    details.forEach((key, value) -> System.out.println("  " + key + ": " + value));
                    
                    // Validate the extracted information
                    boolean isValid = true;
                    if (title == null || title.isEmpty()) {
                        System.err.println("ERROR: Title is empty");
                        isValid = false;
                    }
                    if (movieId == null || movieId.isEmpty()) {
                        System.err.println("ERROR: Movie ID is empty");
                        isValid = false;
                    }
                    if (coverUrl == null || coverUrl.isEmpty()) {
                        System.err.println("ERROR: Cover URL is empty");
                        isValid = false;
                    }
                    if (details.isEmpty()) {
                        System.err.println("ERROR: Details are empty");
                        isValid = false;
                    }
                    
                    // Check if the file is for Chinese language (contains zh-tw or zh-cn)
                    String filename = htmlFilePath.getFileName().toString();
                    if (filename.contains("zh-tw") && !details.containsKey("代码")) {
                        System.err.println("ERROR: Traditional Chinese file missing code field (代码)");
                        isValid = false;
                    }
                    
                    // Special case for zh-cn file which appears to contain Japanese content
                    if (filename.contains("zh-cn")) {
                        // For zh-cn, check if it has Japanese code field コード instead
                        if (!details.containsKey("コード")) {
                            System.err.println("ERROR: Simplified Chinese file missing expected Japanese code field (コード)");
                            isValid = false;
                        }
                    }
                    
                    System.out.println("Extraction " + (isValid ? "SUCCESSFUL" : "FAILED"));
                    
                } catch (IOException e) {
                    System.err.println("Error processing file " + htmlFilePath + ": " + e.getMessage());
                }
            });
    }
    
    /**
     * Extracts the title from the HTML document
     * @param doc The Jsoup Document
     * @return The extracted title
     */
    private String extractTitle(Document doc) {
        Element titleElement = doc.selectFirst("h1");
        return titleElement != null ? titleElement.text() : null;
    }
    
    /**
     * Extracts the movie details from the HTML document
     * @param doc The Jsoup Document
     * @return Map of detail keys and values
     */
    private Map<String, String> extractDetails(Document doc) {
        Map<String, String> details = new HashMap<>();
        Element detailsDiv = doc.selectFirst("div.detail-item");
        
        if (detailsDiv != null) {
            Elements detailItems = detailsDiv.select("div");
            for (Element item : detailItems) {
                Elements spans = item.select("span");
                if (spans.size() >= 2) {
                    String key = spans.get(0).text().replace(":", "").trim().toLowerCase();
                    String value = spans.get(1).text().trim();
                    details.put(key, value);
                }
            }
        }
        
        return details;
    }
    
    /**
     * Extracts the movie ID from the HTML document
     * @param doc The Jsoup Document
     * @return The extracted movie ID
     */
    private String extractMovieId(Document doc) {
        // Look for the v-scope attribute containing Favourite('movie', ID, 0)
        Elements elements = doc.select("[v-scope*=Favourite('movie']");
        
        for (Element element : elements) {
            String vScope = element.attr("v-scope");
            Pattern pattern = Pattern.compile("Favourite\\('movie', (\\d+),");
            Matcher matcher = pattern.matcher(vScope);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
    
    /**
     * Extracts the cover URL from the HTML document
     * @param doc The Jsoup Document
     * @return The extracted cover URL
     */
    private String extractCoverUrl(Document doc) {
        // Method 1: Look for the player div with data-poster attribute
        Element playerDiv = doc.selectFirst("div#player[data-poster]");
        if (playerDiv != null) {
            String dataPoster = playerDiv.attr("data-poster");
            if (dataPoster != null && !dataPoster.isEmpty()) {
                return dataPoster;
            }
        }
        
        // Method 2: Look for meta og:image tag
        Element metaOgImage = doc.selectFirst("meta[property=og:image]");
        if (metaOgImage != null) {
            String content = metaOgImage.attr("content");
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }
        
        return null;
    }
    
    @Test
    public void testSaveHtmlResponses() throws IOException {
        // Create data directory if it doesn't exist
        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        for (Av123LanguageCode languageCode : Av123LanguageCode.values()) {
            String languagePathCode = languageCode.getPathCode();
            String languageDbCode = languageCode.getDbCode();

         
            // 构建特定语言的URL
            String prefix = "https://123av.com/";
            String movieCode = "midv-936";
            String movieLink = "dm4/v/midv-936";
            if (!languagePathCode.isEmpty()) {
                prefix += languagePathCode + "/";
            }
            String url = prefix + movieLink;
            
        
            // Build request with appropriate headers
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Accept-Language", languageDbCode)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Request failed with code: " + response.code() + " for language: " + languageDbCode);
                    continue;
                }

                // Get response body
                String responseBody = response.body().string();
                
                // Create filename with timestamp and language
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = String.format("movie_%s_lang_%s_%s.html", movieCode, languageDbCode, timestamp);
                Path filePath = dataDir.resolve(filename);
                
                // Save response to file
                Files.write(filePath, responseBody.getBytes(StandardCharsets.UTF_8));
                
                System.out.println("Saved response for language " + languageDbCode + " to " + filePath);
            }
        }
    }
}