package org.acme.service.favourite;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.model.FavouriteRequest;
import org.acme.model.FavouriteResponse;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class FavouriteService {

    private static final Logger logger = Logger.getLogger(FavouriteService.class);
    private static final String FAVOURITE_API_URL = "https://123av.com/zh/ajax/user/favourite";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 200; // Start with 500ms delay
    
    private final HttpClient httpClient;
    
    // Cookie string for authentication - in a real application, this would be managed properly
    private static final String COOKIE_STRING = "_ga=GA1.1.102313145.1744379567; dom3ic8zudi28v8lr6fgphwffqoz0j6c=074613d3-a62f-4b54-9e34-57d7d7215ec0%3A1%3A1; remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=336255%7CuICKPKngTFAtH1xtQ8sAUD5vcWj1DfyXvIqd58W0kWmsQjPudM4ZOTkVK0Th%7C%242y%2412%24MrWKIiD0dnB.ALudK1g7Se.CGkahhbqcVmnhLAib6x8eu8mvsPWsu; locale=zh; session=q2cQvi0CshyKvHkZTVQYToCwpHahZ2mMpVZAhTvX; _ga_VZGC2QQBZ8=GS1.1.1744450092.3.1.1744450183.0.0.0; x-token=36e90443ed5a3626c1a6b3d65deb8e23";

    public FavouriteService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Process favourite operations for a range of movie IDs
     * 
     * @param startId The starting movie ID
     * @param endId The ending movie ID
     * @param action The action to perform (add or remove)
     * @return A map of movie IDs to response objects
     */
    public Map<Long, FavouriteResponse> processFavouritesForRange(Long startId, Long endId, String action) {
        Map<Long, FavouriteResponse> results = new HashMap<>();
        
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
        
        for (long id = startId; id <= endId; id++) {
            FavouriteRequest request = new FavouriteRequest();
            request.setAction(action);
            request.setType("movie");
            request.setId(id);
            
            FavouriteResponse response = processFavourite(request);
            results.put(id, response);
            
            // Add a small delay to avoid overwhelming the server
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Thread interrupted during delay between requests");
            }
        }
        
        return results;
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
            String requestBody = String.format(
                "{\"action\":\"%s\",\"type\":\"%s\",\"id\":%d}",
                request.getAction(), request.getType(), request.getId()
            );
            
            logger.infof("Sending request to external API: %s", requestBody);
            
            // Build the HTTP request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(FAVOURITE_API_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://123av.com")
                .header("Referer", "https://123av.com/zh/user/collection")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", COOKIE_STRING)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            // Send the request
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            // Log the response
            logger.infof("External API response for movie ID %d: %d - %s", 
                    request.getId(), response.statusCode(), response.body());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                logger.warnf("API request failed with status code %d: %s", 
                        response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            logger.errorf("Error calling external API for movie ID %d: %s", 
                    request.getId(), e.getMessage());
            return null;
        }
    }
}
