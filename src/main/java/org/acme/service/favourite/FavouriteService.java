package org.acme.service.favourite;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import org.acme.model.FavouriteRequest;
import org.acme.model.FavouriteResponse;
import org.jboss.logging.Logger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class FavouriteService {

    private static final Logger logger = Logger.getLogger(FavouriteService.class);
    private static final String FAVOURITE_API_URL = "https://123av.com/zh/ajax/user/favourite";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 200; // Start with 200ms delay
    private static final int FAVOURITE_THREAD_POOL_SIZE = 5; // Number of threads for processing favourites
    private static final int BATCH_SIZE = 50; // Number of IDs to process in each batch
    
    private final OkHttpClient httpClient;
    private final ExecutorService favouriteThreadPool;
    
    // Cookie string for authentication - in a real application, this would be managed properly
    private static final String COOKIE_STRING = "_ga=GA1.1.102313145.1744379567; dom3ic8zudi28v8lr6fgphwffqoz0j6c=074613d3-a62f-4b54-9e34-57d7d7215ec0%3A1%3A1; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=336255%7CuICKPKngTFAtH1xtQ8sAUD5vcWj1DfyXvIqd58W0kWmsQjPudM4ZOTkVK0Th%7C%242y%2412%24MrWKIiD0dnB.ALudK1g7Se.CGkahhbqcVmnhLAib6x8eu8mvsPWsu; locale=zh; session=q2cQvi0CshyKvHkZTVQYToCwpHahZ2mMpVZAhTvX; _ga_VZGC2QQBZ8=GS1.1.1744450092.3.1.1744450183.0.0.0; x-token=36e90443ed5a3626c1a6b3d65deb8e23";

    public FavouriteService() {
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
        
        this.favouriteThreadPool = Executors.newFixedThreadPool(FAVOURITE_THREAD_POOL_SIZE);
    }
    
    @PreDestroy
    public void cleanup() {
        if (favouriteThreadPool != null && !favouriteThreadPool.isShutdown()) {
            favouriteThreadPool.shutdown();
        }
    }

    /**
     * Process favourite operations for a range of movie IDs using multiple threads
     * with retry mechanism
     * 
     * @param startId The starting movie ID
     * @param endId The ending movie ID
     * @param action The action to perform (add or remove)
     * @return A map of movie IDs to response objects
     */
    public Map<Long, FavouriteResponse> processFavouritesForRange(Long startId, Long endId, String action) {
        // Use ConcurrentHashMap for thread safety
        Map<Long, FavouriteResponse> results = new ConcurrentHashMap<>();
        
        if (startId == null || endId == null) {
            FavouriteResponse response = new FavouriteResponse(false, "Invalid parameters: startId and endId are required");
            results.put(0L, response);
            return results;
        }
        
        if (startId > endId) {
            FavouriteResponse response = new FavouriteResponse(false, "Invalid range: startId must be less than or equal to endId");
            results.put(0L, response);
            return results;
        }
        
        // Create batches of IDs for parallel processing
        List<List<Long>> batches = createIdBatches(startId, endId, BATCH_SIZE);
        logger.infof("Created %d batches for processing %d to %d", batches.size(), startId, endId);
        
        // Track progress
        AtomicInteger completedBatches = new AtomicInteger(0);
        int totalBatches = batches.size();
        
        // Process each batch in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (List<Long> batch : batches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                logger.infof("Processing %s batch of %d IDs from %d to %d", 
                        action, batch.size(), batch.get(0), batch.get(batch.size() - 1));
                
                for (Long id : batch) {
                    FavouriteRequest request = new FavouriteRequest();
                    request.setAction(action);
                    request.setType("movie");
                    request.setId(id);
                    
                    // Process with retry mechanism
                    FavouriteResponse response = processFavouriteWithRetry(request);
                    results.put(id, response);
                    
                    // Add a small delay to avoid overwhelming the server
                    try {
                        Thread.sleep(50); // Reduced delay since we're using multiple threads
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Thread interrupted during delay between requests");
                    }
                }
                
                int completed = completedBatches.incrementAndGet();
                logger.infof("Completed batch %d/%d for %s operation", completed, totalBatches, action);
            }, favouriteThreadPool);
            
            futures.add(future);
        }
        
        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        
        // Wait for all futures to complete
        allFutures.join();
        
        logger.infof("Completed processing %s for %d IDs from %d to %d", action, results.size(), startId, endId);
        return results;
    }
    
    /**
     * Create batches of IDs for parallel processing
     * 
     * @param startId The starting ID
     * @param endId The ending ID
     * @param batchSize The size of each batch
     * @return List of ID batches
     */
    private List<List<Long>> createIdBatches(Long startId, Long endId, int batchSize) {
        List<List<Long>> batches = new ArrayList<>();
        long currentId = startId;
        
        while (currentId <= endId) {
            List<Long> batch = new ArrayList<>();
            long batchEndId = Math.min(currentId + batchSize - 1, endId);
            
            for (long id = currentId; id <= batchEndId; id++) {
                batch.add(id);
            }
            
            batches.add(batch);
            currentId = batchEndId + 1;
        }
        
        return batches;
    }
    
    /**
     * Process a favourite request with retry mechanism
     * 
     * @param request The favourite request
     * @return The response after processing
     */
    private FavouriteResponse processFavouriteWithRetry(FavouriteRequest request) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount <= MAX_RETRIES) {
            try {
                // If this is a retry, log it
                if (retryCount > 0) {
                    logger.infof("Retry attempt %d/%d for movie ID %d", retryCount, MAX_RETRIES, request.getId());
                }
                
                // Call the external API
                String apiResponse = callExternalFavouriteApi(request);
                
                if (apiResponse != null) {
                    // Success - return immediately
                    if (retryCount > 0) {
                        logger.infof("Successfully processed movie ID %d after %d retries", request.getId(), retryCount);
                    }
                    return new FavouriteResponse(true, "Successfully processed favourite operation for movie ID: " + request.getId());
                }
                
                // If we get here, the API call failed but didn't throw an exception
                logger.warnf("API call failed for movie ID %d (attempt %d/%d)", request.getId(), retryCount + 1, MAX_RETRIES + 1);
            } catch (Exception e) {
                lastException = e;
                logger.warnf("Error processing favourite for movie ID %d (attempt %d/%d): %s", 
                        request.getId(), retryCount + 1, MAX_RETRIES + 1, e.getMessage());
            }
            
            // If we've reached max retries, break out of the loop
            if (retryCount >= MAX_RETRIES) {
                break;
            }
            
            // Calculate backoff time with exponential increase
            long backoffTime = INITIAL_BACKOFF_MS * (long)Math.pow(2, retryCount);
            logger.infof("Waiting %d ms before retry %d for movie ID %d", backoffTime, retryCount + 1, request.getId());
            
            try {
                Thread.sleep(backoffTime);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted during retry backoff");
                break; // Exit the retry loop if interrupted
            }
            
            retryCount++;
        }
        
        // If we get here, all retries failed
        String errorMessage = lastException != null ? lastException.getMessage() : "API call failed after all retry attempts";
        logger.errorf("Failed to process favourite for movie ID %d after %d attempts: %s", 
                request.getId(), retryCount, errorMessage);
        return new FavouriteResponse(false, "Error processing favourite after " + retryCount + " attempts: " + errorMessage);
    }

    /**
     * Process a single favourite operation by making an HTTP request to the external API
     * with retry mechanism
     * 
     * @param request The favourite request
     * @return The response object
     */
    public FavouriteResponse processFavourite(FavouriteRequest request) {
        if (request == null) {
            return new FavouriteResponse(false, "Invalid request");
        }
        
        if (request.getAction() == null || request.getType() == null || request.getId() == null) {
            return new FavouriteResponse(false, "Missing required fields: action, type, or id");
        }
        
        // Implement retry logic with exponential backoff
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount <= MAX_RETRIES) {
            try {
                // If this is a retry, log it
                if (retryCount > 0) {
                    logger.infof("Retry attempt %d/%d for movie ID %d", retryCount, MAX_RETRIES, request.getId());
                }
                
                // Call the external API
                String apiResponse = callExternalFavouriteApi(request);
                
                if (apiResponse != null) {
                    // Success - return immediately
                    if (retryCount > 0) {
                        logger.infof("Successfully processed movie ID %d after %d retries", request.getId(), retryCount);
                    }
                    return new FavouriteResponse(true, "Successfully processed favourite operation for movie ID: " + request.getId());
                }
                
                // If we get here, the API call failed but didn't throw an exception
                logger.warnf("API call failed for movie ID %d (attempt %d/%d)", request.getId(), retryCount + 1, MAX_RETRIES + 1);
            } catch (Exception e) {
                lastException = e;
                logger.warnf("Error processing favourite for movie ID %d (attempt %d/%d): %s", 
                        request.getId(), retryCount + 1, MAX_RETRIES + 1, e.getMessage());
            }
            
            // If we've reached max retries, break out of the loop
            if (retryCount >= MAX_RETRIES) {
                break;
            }
            
            // Calculate backoff time with exponential increase
            long backoffTime = INITIAL_BACKOFF_MS * (long)Math.pow(2, retryCount);
            logger.infof("Waiting %d ms before retry %d for movie ID %d", backoffTime, retryCount + 1, request.getId());
            
            try {
                Thread.sleep(backoffTime);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted during retry backoff");
                break; // Exit the retry loop if interrupted
            }
            
            retryCount++;
        }
        
        // If we get here, all retries failed
        String errorMessage = lastException != null ? lastException.getMessage() : "API call failed after all retry attempts";
        logger.errorf("Failed to process favourite for movie ID %d after %d attempts: %s", 
                request.getId(), retryCount, errorMessage);
        return new FavouriteResponse(false, "Error processing favourite after " + retryCount + " attempts: " + errorMessage);
    }

    /**
     * Call the external favourite API
     * 
     * @param request The favourite request
     * @return The response from the API
     */
    private String callExternalFavouriteApi(FavouriteRequest request) {
        try {
            // Prepare the request body
            String requestBodyStr = String.format(
                "{\"action\":\"%s\",\"type\":\"%s\",\"id\":%d}",
                request.getAction(), request.getType(), request.getId()
            );
            
            logger.infof("Sending request to external API: %s", requestBodyStr);
            
            // Build the HTTP request
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody requestBody = RequestBody.create(requestBodyStr, JSON);
            
            Request httpRequest = new Request.Builder()
                .url(FAVOURITE_API_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .addHeader("Origin", "https://123av.com")
                .addHeader("Referer", "https://123av.com/zh/user/collection")
                .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                .addHeader("x-requested-with", "XMLHttpRequest")
                .addHeader("cookie", COOKIE_STRING)
                .build();
            
            // Send the request
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body().string();
                
                // Log the response
                logger.infof("External API response for movie ID %d: %d - %s", 
                        request.getId(), response.code(), responseBody);
                
                if (response.isSuccessful()) {
                    return responseBody;
                } else {
                    logger.warnf("API request failed with status code %d: %s", 
                            response.code(), responseBody);
                    return null;
                }
            }
        } catch (Exception e) {
            logger.errorf("Error calling external API for movie ID %d: %s", 
                    request.getId(), e.getMessage());
            return null;
        }
    }
}
