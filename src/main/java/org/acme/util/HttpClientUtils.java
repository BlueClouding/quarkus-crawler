package org.acme.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for HTTP client operations
 */
public class HttpClientUtils {

    // Common user agent string
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    
    // Common accept header for HTML content
    private static final String ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
    
    // Common accept header for JSON content
    private static final String ACCEPT_JSON = "application/json, text/javascript, */*; q=0.01";
    
    // Common language header
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9";

    /**
     * Creates a standard OkHttpClient with common configuration
     * 
     * @return Configured OkHttpClient instance
     */
    public static OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Adds common request headers to a request builder
     * 
     * @param builder Request.Builder instance
     * @param cookieString Cookie string to use
     * @param jsonContent Whether to use JSON accept header (true) or HTML accept header (false)
     *                    When true, also adds x-requested-with header for AJAX requests
     * @return The same builder with headers added
     */
    public static Request.Builder addCommonHeaders(Request.Builder builder, String cookieString, boolean jsonContent) {
        // Build basic headers
        builder = builder
                .addHeader("user-agent", USER_AGENT)
                .addHeader("accept", jsonContent ? ACCEPT_JSON : ACCEPT_HTML)
                .addHeader("accept-language", ACCEPT_LANGUAGE);
        
        // Add cookie if provided
        if (cookieString != null && !cookieString.isEmpty()) {
            builder = builder.addHeader("cookie", cookieString);
        }
        
        // Add XMLHttpRequest header only for JSON/AJAX requests
        if (jsonContent) {
            builder = builder.addHeader("x-requested-with", "XMLHttpRequest");
        }
        
        return builder;
    }
    
    /**
     * Creates a request builder with url and common headers
     * 
     * @param url The URL for the request
     * @param cookieString Cookie string to use
     * @param jsonContent Whether to use JSON accept header (true) or HTML accept header (false)
     * @return Request.Builder with common headers
     */
    public static Request.Builder createRequestBuilder(String url, String cookieString, boolean jsonContent) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        
        return addCommonHeaders(builder, cookieString, jsonContent);
    }
    
    /**
     * Creates a request builder with referer header in addition to common headers
     * 
     * @param url The URL for the request
     * @param cookieString Cookie string to use
     * @param referer The referer URL
     * @param jsonContent Whether to use JSON accept header (true) or HTML accept header (false)
     * @return Request.Builder with common headers and referer
     */
    public static Request.Builder createRequestBuilderWithReferer(String url, String cookieString, String referer, boolean jsonContent) {
        return createRequestBuilder(url, cookieString, jsonContent)
                .addHeader("referer", referer);
    }
}
