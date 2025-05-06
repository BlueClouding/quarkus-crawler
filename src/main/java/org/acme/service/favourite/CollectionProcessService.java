package org.acme.service.favourite;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.enums.MovieStatus;
import org.acme.model.FavouriteRequest;
import org.acme.model.FavouriteResponse;
import org.acme.service.MovieParser;
import org.acme.util.FileUtils;
import org.acme.util.JacksonUtils;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Service for processing user collections
 * This service handles:
 * 1. Adding movies to favourites using the favourite API
 * 2. Fetching collection pages to extract movie information
 * 3. Saving movie information to the database
 * 4. Removing movies from favourites
 */
@ApplicationScoped
public class CollectionProcessService {

    private static final Logger logger = Logger.getLogger(CollectionProcessService.class);
    private static final String COLLECTION_BASE_URL = "https://123av.com/zh/user/collection";
    private static final int MAX_RETRIES = 3;
    private static final int COLLECTION_THREAD_POOL_SIZE = 5; // Number of threads for processing collection pages
    private static final int PAGES_PER_THREAD = 20; // Number of pages each thread should process
    
    // Cookie string for authentication - in a real application, this would be managed properly
    private static final String COOKIE_STRING = "_ga=GA1.1.102313145.1744379567; dom3ic8zudi28v8lr6fgphwffqoz0j6c=074613d3-a62f-4b54-9e34-57d7d7215ec0%3A1%3A1; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=336255%7CuICKPKngTFAtH1xtQ8sAUD5vcWj1DfyXvIqd58W0kWmsQjPudM4ZOTkVK0Th%7C%242y%2412%24MrWKIiD0dnB.ALudK1g7Se.CGkahhbqcVmnhLAib6x8eu8mvsPWsu; locale=zh; session=q2cQvi0CshyKvHkZTVQYToCwpHahZ2mMpVZAhTvX; x-token=366cdf8a7bb8bbb1b3befa65853a06cf; _ga_VZGC2QQBZ8=GS1.1.1744454119.4.1.1744454697.0.0.0";

    private final HttpClient httpClient;
    private final ExecutorService collectionThreadPool;
    
    @Inject
    FavouriteService favouriteService;
    
    @Inject
    MovieParser movieParser;

    public CollectionProcessService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        this.collectionThreadPool = Executors.newFixedThreadPool(COLLECTION_THREAD_POOL_SIZE);
        
        // Initialize data directory
        FileUtils.initDataDirectory();
    }

    /**
     * Process a batch of movies by:
     * 1. Adding them to favourites
     * 2. Fetching collection pages to extract movie info
     * 3. Saving movie info to database
     * 4. Removing them from favourites
     * 
     * @param startId The starting movie ID
     * @param batchSize The number of movies to process (should be a multiple of MOVIES_PER_PAGE for efficiency)
     * @return A summary of the processing results
     */
    public Map<String, Object> processBatch(Long startId, int batchSize) {
        Map<String, Object> result = new HashMap<>();
        AtomicInteger addedCount = new AtomicInteger(0);
        AtomicInteger fetchedCount = new AtomicInteger(0);
        AtomicInteger savedCount = new AtomicInteger(0);
        AtomicInteger removedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        try {
            // Calculate endId based on startId and batchSize
            Long endId = startId + batchSize - 1;
            
            logger.infof("Starting batch processing from ID %d to %d", startId, endId);
            
            // Step 1: Add movies to favourites
            Map<Long, FavouriteResponse> addResults = addToFavourites(startId, endId);
            
            // Count successful additions
            addResults.forEach((id, response) -> {
                if (response.isSuccess()) {
                    addedCount.incrementAndGet();
                } else {
                    logger.warnf("Failed to add movie ID %d to favourites: %s", id, response.getMessage());
                    errorCount.incrementAndGet();
                }
            });
            
            // Step 2 & 3: Process collection pages and save movies
            Map<String, Integer> processResult = processCollectionAndSave();
            fetchedCount.set(processResult.get("fetchedCount"));
            savedCount.set(processResult.get("savedCount"));
            errorCount.addAndGet(processResult.get("errorCount"));
            
            // Step 4: Remove movies from favourites
            Map<Long, FavouriteResponse> removeResults = removeFromFavourites(startId, endId);
            
            // Count successful removals
            removeResults.forEach((id, response) -> {
                if (response.isSuccess()) {
                    removedCount.incrementAndGet();
                } else {
                    removedCount.incrementAndGet();
                    logger.warnf("Failed to remove movie ID %d from favourites: %s", id, response.getMessage());
                }
            });
            
            // Prepare result summary
            result.put("success", true);
            result.put("startId", startId);
            result.put("endId", endId);
            result.put("batchSize", batchSize);
            result.put("addedCount", addedCount.get());
            result.put("fetchedCount", fetchedCount.get());
            result.put("savedCount", savedCount.get());
            result.put("removedCount", removedCount.get());
            result.put("errorCount", errorCount.get());
            
        } catch (Exception e) {
            logger.errorf("Error processing batch: %s", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Add a range of movie IDs to favourites
     * 
     * @param startId The starting movie ID
     * @param endId The ending movie ID
     * @return Map of movie IDs to favourite response objects
     */
    public Map<Long, FavouriteResponse> addToFavourites(Long startId, Long endId) {
        logger.info("Step 1: Adding movies to favourites");
        Map<Long, FavouriteResponse> addResults = favouriteService.processFavouritesForRange(startId, endId, "add");
        int successCount = (int) addResults.values().stream().filter(FavouriteResponse::isSuccess).count();
        logger.infof("Added %d movies to favourites", successCount);
        return addResults;
    }
    
    /**
     * Process collection pages to extract movie information and save to database
     * using multiple threads
     * 
     * @return Map containing counts of fetched and saved movies
     */
    public Map<String, Integer> processCollectionAndSave() {
        Map<String, Integer> result = new HashMap<>();
        AtomicInteger fetchedCount = new AtomicInteger(0);
        AtomicInteger savedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        try {
            // First, fetch the first page to get the total number of pages
            int totalPages = getTotalCollectionPages();
            logger.infof("Found %d total collection pages", totalPages);
            
            if (totalPages <= 0) {
                logger.warn("No collection pages found, possibly empty collection or login issue");
                result.put("fetchedCount", 0);
                result.put("savedCount", 0);
                result.put("errorCount", 1);
                return result;
            }
            
            // Step 2: Fetch collection pages and extract movie information using multiple threads
            logger.infof("Step 2: Fetching %d collection pages using %d threads", 
                    totalPages, COLLECTION_THREAD_POOL_SIZE);
            
            // Create batches of pages for each thread
            List<List<Integer>> pageBatches = createPageBatches(totalPages, PAGES_PER_THREAD);
            logger.infof("Created %d page batches", pageBatches.size());
            
            // Process each batch in a separate thread and save immediately after each batch
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (List<Integer> batch : pageBatches) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    List<Movie> batchMovies = new ArrayList<>();
                    List<Long> batchProcessedIds = new ArrayList<>();
                    int batchFetchCount = 0;
                    int batchSaveCount = 0;
                    int batchErrorCount = 0;
                    
                    logger.infof("Processing page batch: %d to %d", batch.get(0), batch.get(batch.size() - 1));
                    
                    // Step 1: Fetch movies from all pages in this batch
                    for (Integer page : batch) {
                        try {
                            List<Movie> pageMovies = processCollectionPage(page);
                            if (pageMovies != null && !pageMovies.isEmpty()) {
                                batchMovies.addAll(pageMovies);
                                batchFetchCount += pageMovies.size();
                                logger.infof("Fetched %d movies from page %d/%d", 
                                        pageMovies.size(), page, totalPages);
                            } else {
                                logger.warnf("No movies fetched from page %d/%d", page, totalPages);
                            }
                            
                            // Add a small delay to avoid overwhelming the server
                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                logger.warn("Thread interrupted during delay between page requests");
                            }
                        } catch (Exception e) {
                            logger.errorf("Error processing page %d: %s", page, e.getMessage());
                            batchErrorCount++;
                        }
                    }
                    
                    // Step 2: Save this batch's movies to database immediately
                    if (!batchMovies.isEmpty()) {
                        logger.infof("Saving %d movies from batch %d to %d to database", 
                                batchMovies.size(), batch.get(0), batch.get(batch.size() - 1));
                        
                        for (Movie movie : batchMovies) {
                            try {
                                saveMovie(movie);
                                batchSaveCount++;
                                
                                // Add the original ID to the processed IDs list
                                if (movie.getOriginalId() != null) {
                                    batchProcessedIds.add(movie.getOriginalId().longValue());
                                }
                            } catch (Exception e) {
                                logger.errorf("Error saving movie %s: %s", movie.getCode(), e.getMessage());
                                batchErrorCount++;
                            }
                        }
                        
                        // Save processed IDs to file immediately
                        if (!batchProcessedIds.isEmpty()) {
                            FileUtils.saveProcessedIds(batchProcessedIds);
                            logger.infof("Saved %d processed IDs from batch to file", batchProcessedIds.size());
                        }
                        
                        // Update counters
                        fetchedCount.addAndGet(batchFetchCount);
                        savedCount.addAndGet(batchSaveCount);
                        errorCount.addAndGet(batchErrorCount);
                        
                        logger.infof("Completed batch %d to %d: fetched=%d, saved=%d, errors=%d", 
                                batch.get(0), batch.get(batch.size() - 1), 
                                batchFetchCount, batchSaveCount, batchErrorCount);
                    }
                }, collectionThreadPool);
                
                futures.add(future);
            }
            
            // Wait for all futures to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            // Wait for all futures to complete
            allFutures.join();
            
            logger.infof("All page batches completed, fetched and saved %d movies total", fetchedCount.get());
            
            logger.infof("Processed %d movies: fetched=%d, saved=%d, errors=%d", 
                    fetchedCount.get(), fetchedCount.get(), savedCount.get(), errorCount.get());
            
        } catch (Exception e) {
            logger.errorf("Error processing collection pages: %s", e.getMessage());
            errorCount.incrementAndGet();
        }
        
        result.put("fetchedCount", fetchedCount.get());
        result.put("savedCount", savedCount.get());
        result.put("errorCount", errorCount.get());
        return result;
    }
    
    /**
     * Create batches of page numbers for parallel processing
     * 
     * @param totalPages Total number of pages
     * @param pagesPerBatch Number of pages per batch
     * @return List of page number batches
     */
    private List<List<Integer>> createPageBatches(int totalPages, int pagesPerBatch) {
        List<List<Integer>> batches = new ArrayList<>();
        
        for (int i = 0; i < totalPages; i += pagesPerBatch) {
            int start = i + 1; // Pages are 1-indexed
            int end = Math.min(i + pagesPerBatch, totalPages);
            
            List<Integer> batch = IntStream.rangeClosed(start, end)
                    .boxed()
                    .collect(Collectors.toList());
            
            batches.add(batch);
        }
        
        return batches;
    }
    
    /**
     * Get the total number of pages in the collection
     * 
     * @return The total number of pages, or 0 if unable to determine
     */
    private int getTotalCollectionPages() {
        try {
            String url = COLLECTION_BASE_URL;
            logger.infof("Fetching first collection page to determine total pages: %s", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                    .header("Referer", "https://123av.com/zh/user/feed")
                    .header("Cookie", COOKIE_STRING)
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.errorf("Failed to fetch collection page: HTTP %d", response.statusCode());
                return 0;
            }
            
            // Parse the HTML to extract the total pages
            Document doc = Jsoup.parse(response.body());
            
            // Look for the navigation element with the lastPage attribute
            // <div v-scope="Navigation({ lastPage: 465 })" @vue:mounted="init($el)" class="mt-5">
            Elements navElements = doc.select("div[v-scope~=Navigation]");
            
            if (navElements.isEmpty()) {
                logger.warn("Navigation element not found in collection page");
                return 0;
            }
            
            String vScopeAttr = navElements.first().attr("v-scope");
            
            // Use regex to extract the lastPage value
            Pattern pattern = Pattern.compile("Navigation\\(\\{\\s*lastPage:\s*(\\d+)\\s*\\}\\)");
            Matcher matcher = pattern.matcher(vScopeAttr);
            
            if (matcher.find()) {
                String lastPageStr = matcher.group(1);
                try {
                    int lastPage = Integer.parseInt(lastPageStr);
                    logger.infof("Found total pages: %d", lastPage);
                    return lastPage;
                } catch (NumberFormatException e) {
                    logger.warnf("Could not parse lastPage value: %s", lastPageStr);
                }
            } else {
                logger.warn("Could not extract lastPage value from navigation element");
            }
            
            return 0;
        } catch (Exception e) {
            logger.errorf("Error determining total collection pages: %s", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Remove a range of movie IDs from favourites
     * 
     * @param startId The starting movie ID
     * @param endId The ending movie ID
     * @return Map of movie IDs to favourite response objects
     */
    public Map<Long, FavouriteResponse> removeFromFavourites(Long startId, Long endId) {
        logger.info("Step 4: Removing movies from favourites");
        Map<Long, FavouriteResponse> removeResults = favouriteService.processFavouritesForRange(startId, endId, "remove");
        
        // Track failed removals
        List<Long> failedIds = new ArrayList<>();
        removeResults.forEach((id, response) -> {
            if (!response.isSuccess()) {
                failedIds.add(id);
            }
        });
        
        // Save failed IDs to file
        if (!failedIds.isEmpty()) {
            FileUtils.saveRemoveFailedIds(failedIds);
            logger.infof("Saved %d failed removal IDs to file", failedIds.size());
        }
        
        int successCount = (int) removeResults.values().stream().filter(FavouriteResponse::isSuccess).count();
        logger.infof("Removed %d movies from favourites", successCount);
        return removeResults;
    }
    
    /**
     * Remove all processed movie IDs from favourites using multiple threads
     * 
     * @param batchSize The size of each batch to process
     * @param threadCount The number of threads to use
     * @return Map with removal statistics
     */
    public Map<String, Object> autoRemoveProcessedIds(int batchSize, int threadCount) {
        Map<String, Object> result = new HashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        try {
            // Load all processed IDs from file
            Set<Long> processedIds = FileUtils.getProcessedIds();
            logger.infof("Loaded %d processed IDs for auto-removal", processedIds.size());
            
            if (processedIds.isEmpty()) {
                logger.warn("No processed IDs found for removal");
                result.put("success", true);
                result.put("removedCount", 0);
                result.put("failedCount", 0);
                return result;
            }
            
            // Create a thread pool for parallel processing
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // Convert set to list for easier batch processing
            List<Long> idList = new ArrayList<>(processedIds);
            
            // Create batches
            List<List<Long>> batches = new ArrayList<>();
            for (int i = 0; i < idList.size(); i += batchSize) {
                batches.add(idList.subList(i, Math.min(i + batchSize, idList.size())));
            }
            
            logger.infof("Created %d batches for auto-removal", batches.size());
            
            // Process each batch in parallel
            List<CompletableFuture<Map<String, Integer>>> futures = new ArrayList<>();
            
            for (List<Long> batch : batches) {
                CompletableFuture<Map<String, Integer>> future = CompletableFuture.supplyAsync(() -> {
                    Map<String, Integer> batchResult = new HashMap<>();
                    int batchSuccess = 0;
                    int batchFailure = 0;
                    List<Long> batchFailedIds = new ArrayList<>();
                    
                    logger.infof("Processing removal batch of %d IDs", batch.size());
                    
                    for (Long id : batch) {
                        try {
                            FavouriteRequest request = new FavouriteRequest();
                            request.setAction("remove");
                            request.setType("movie");
                            request.setId(id);
                            
                            FavouriteResponse response = favouriteService.processFavourite(request);
                            
                            if (response.isSuccess()) {
                                batchSuccess++;
                            } else {
                                batchFailure++;
                                batchFailedIds.add(id);
                                logger.warnf("Failed to remove movie ID %d: %s", id, response.getMessage());
                            }
                            
                            // Add a small delay to avoid overwhelming the server
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        } catch (Exception e) {
                            batchFailure++;
                            batchFailedIds.add(id);
                            logger.errorf("Error removing movie ID %d: %s", id, e.getMessage());
                        }
                    }
                    
                    // Save failed IDs to file
                    if (!batchFailedIds.isEmpty()) {
                        FileUtils.saveRemoveFailedIds(batchFailedIds);
                    }
                    
                    batchResult.put("success", batchSuccess);
                    batchResult.put("failure", batchFailure);
                    
                    logger.infof("Completed removal batch: %d successful, %d failed", batchSuccess, batchFailure);
                    
                    return batchResult;
                }, executor);
                
                futures.add(future);
            }
            
            // Wait for all futures to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            // Wait for all futures to complete
            allFutures.join();
            
            // Combine results
            for (CompletableFuture<Map<String, Integer>> future : futures) {
                Map<String, Integer> batchResult = future.join();
                successCount.addAndGet(batchResult.get("success"));
                failureCount.addAndGet(batchResult.get("failure"));
            }
            
            // Shutdown executor
            executor.shutdown();
            
            logger.infof("Auto-removal completed: %d successful, %d failed", 
                    successCount.get(), failureCount.get());
            
        } catch (Exception e) {
            logger.errorf("Error during auto-removal: %s", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
        
        result.put("success", true);
        result.put("removedCount", successCount.get());
        result.put("failedCount", failureCount.get());
        return result;
    }
    
    /**
     * Process a collection page and extract movie information
     * 
     * @param page The page number to process
     * @return List of movies extracted from the page
     */
    protected List<Movie> processCollectionPage(int page) {
        List<Movie> movies = new ArrayList<>();
        int retryCount = 0;
        
        while (retryCount < MAX_RETRIES) {
            try {
                String url = String.format("%s?page=%d", COLLECTION_BASE_URL, page);
                logger.infof("Fetching collection page: %s", url);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                        .header("Referer", "https://123av.com/zh/user/feed")
                        .header("Cookie", COOKIE_STRING)
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    logger.errorf("Failed to fetch collection page %d: HTTP %d", page, response.statusCode());
                    retryCount++;
                    continue;
                }
                
                // Extract movies from the collection page
                List<Movie> pageMovies = extractMovieFromElement(response.body(), "https://123av.com/zh");
                if (pageMovies != null && !pageMovies.isEmpty()) {
                    movies.addAll(pageMovies);
                }

                logger.info(String.format("Successfully extracted %d movies from page %d", movies.size(), page));
                return movies;
                
            } catch (Exception e) {
                logger.errorf("Error processing collection page %d (attempt %d): %s", page, retryCount + 1, e.getMessage());
                retryCount++;
                
                // Add delay before retry
                try {
                    Thread.sleep(1000 * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        logger.errorf("Failed to process collection page %d after %d attempts", page, MAX_RETRIES);
        return movies;
    }
    
    /**
     * Extract movie information from the collection page HTML
     * 
     * @param htmlContent The HTML content of the collection page
     * @param baseUrl The base URL of the website
     * @return List of Movie objects with extracted information
     */
    private List<Movie> extractMovieFromElement(String htmlContent, String baseUrl) {
        List<Movie> movies = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(htmlContent);
            
            // Find all movie boxes in the collection page
            Elements movieElements = doc.select(".box-item");
            logger.infof("Found %d movie elements in collection page", movieElements.size());
            
            for (Element movieElement : movieElements) {
                try {
                    Movie movie = new Movie();
                    
                    // Extract link and code
                    Element linkElement = movieElement.selectFirst(".thumb a");
                    if (linkElement != null) {
                        String link = linkElement.attr("href");
                        // Convert relative URL to absolute URL if needed
                        if (!link.startsWith("http")) {
                            link = baseUrl + "/" + link;
                        }
                        movie.setLink(link);
                        
                        // Extract code from link title attribute
                        String code = linkElement.attr("title");
                        if (code != null && !code.isEmpty()) {
                            movie.setCode(code);
                        } else {
                            // Extract code from link URL
                            String[] parts = link.split("/");
                            if (parts.length > 0) {
                                code = parts[parts.length - 1];
                                movie.setCode(code);
                            }
                        }
                    }
                    
                    // Extract title
                    Element titleElement = movieElement.selectFirst(".detail a");
                    if (titleElement != null) {
                        movie.setTitle(titleElement.text().trim());
                    }
                    
                    // Extract thumbnail
                    Element imgElement = movieElement.selectFirst("img");
                    if (imgElement != null) {
                        String thumbnail = imgElement.attr("data-src");
                        if (thumbnail.isEmpty()) {
                            thumbnail = imgElement.attr("src");
                        }
                        movie.setThumbnail(thumbnail);
                    }
                    
                    // Extract duration
                    Element durationElement = movieElement.selectFirst(".duration");
                    if (durationElement != null) {
                        movie.setDuration(durationElement.text().trim());
                    }
                    
                    // Extract originalId from Favourite component
                    Element favouriteElement = movieElement.selectFirst(".favourite");
                    if (favouriteElement != null) {
                        // Look for the v-scope attribute containing Favourite('movie', X, 1)
                        String favouriteAttr = favouriteElement.attr("v-scope");
                        if (favouriteAttr != null && favouriteAttr.contains("Favourite('movie'")) {
                            // Extract the originalId using regex
                            Pattern pattern = Pattern.compile("Favourite\\('movie', (\\d+), 1\\)");
                            Matcher matcher = pattern.matcher(favouriteAttr);
                            if (matcher.find()) {
                                String originalIdStr = matcher.group(1);
                                try {
                                    movie.setOriginalId(Integer.parseInt(originalIdStr));
                                    logger.infof("Extracted originalId: %d for movie %s", movie.getOriginalId(), movie.getCode());
                                } catch (NumberFormatException e) {
                                    logger.warnf("Could not parse originalId: %s", originalIdStr);
                                }
                            }
                        }
                    }
                    
                    // Add movie to list if we have at least a code
                    if (movie.getCode() != null && !movie.getCode().isEmpty()) {
                        movie.setStatus(MovieStatus.NEW.getValue());
                        movies.add(movie);
                        logger.infof("Added movie %s to collection with originalId: %d", movie.getCode(), movie.getOriginalId());
                    }
                } catch (Exception e) {
                    logger.errorf("Error extracting movie from element: %s", e.getMessage());
                }
            }
            logger.info(JacksonUtils.toJsonString(movies));
            return movies;
        } catch (Exception e) {
            logger.errorf("Error parsing collection page: %s", e.getMessage());
            return movies;
        }
    }
    
    /**
     * Save or update a movie in the database
     * 
     * @param movie The movie to save
     * @return The saved movie
     */
    @Transactional
    protected Movie saveMovie(Movie movie) {
        if (movie == null || movie.getCode() == null) {
            throw new IllegalArgumentException("Movie or movie code cannot be null");
        }
        
        try {
            // Check if movie already exists by code
            Movie existingMovie = Movie.find("code", movie.getCode()).firstResult();
            
            if (existingMovie != null) {
                // Update existing movie
                if (movie.getTitle() != null) existingMovie.setTitle(movie.getTitle());
                if (movie.getThumbnail() != null) existingMovie.setThumbnail(movie.getThumbnail());
                if (movie.getLink() != null) existingMovie.setLink(movie.getLink());
                if (movie.getOriginalId() != null) existingMovie.setOriginalId(movie.getOriginalId());
                
                existingMovie.persist();
                return existingMovie;
            } else {
                // Create new movie with required fields
                if (movie.getDuration() == null) {
                    movie.setDuration("未知"); // Set a default value for required field
                }
                
                movie.persist();
                return movie;
            }
        } catch (Exception e) {
            logger.errorf("Error saving movie %s: %s", movie.getCode(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Process multiple batches sequentially
     * 
     * @param startId The starting movie ID for the first batch
     * @param batchSize The size of each batch
     * @param batchCount The number of batches to process
     * @return List of batch processing results
     */
    public List<Map<String, Object>> processMultipleBatches(Long startId, int batchSize, int batchCount) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (int i = 0; i < batchCount; i++) {
            Long currentStartId = startId + (i * batchSize);
            logger.infof("Processing batch %d/%d starting from ID %d", i + 1, batchCount, currentStartId);
            
            Map<String, Object> batchResult = processBatch(currentStartId, batchSize);
            results.add(batchResult);
            
            // Add delay between batches
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted during delay between batches");
            }
        }
        
        return results;
    }
}
