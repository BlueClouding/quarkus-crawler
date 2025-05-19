package org.acme.util;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlPrefixReplacer {

    public static String replaceUrlPrefix(String originalUrl, String newPrefix) {
        try {
            URI uri = new URI(originalUrl);
            String path = uri.getPath(); // Gets everything from the first "/" after the domain
            String query = uri.getQuery();
            String fragment = uri.getFragment();

            // Ensure newPrefix ends with a "/" if the path is not empty or doesn't start with "/"
            // Or, ensure newPrefix does not end with "/" if path starts with "/"
            String effectivelyNewPrefix = newPrefix;
            if (path != null && !path.isEmpty()) {
                if (effectivelyNewPrefix.endsWith("/") && path.startsWith("/")) {
                    effectivelyNewPrefix = effectivelyNewPrefix.substring(0, effectivelyNewPrefix.length() - 1);
                } else if (!effectivelyNewPrefix.endsWith("/") && !path.startsWith("/")) {
                    effectivelyNewPrefix = effectivelyNewPrefix + "/";
                }
            } else if (effectivelyNewPrefix.endsWith("/")) { // Path is empty, newPrefix shouldn't end with / unless it's the whole URL
                 if (query == null && fragment == null) {
                    // keep the trailing slash if newPrefix is meant to be just "http://domain/"
                 } else {
                    effectivelyNewPrefix = effectivelyNewPrefix.substring(0, effectivelyNewPrefix.length() - 1);
                 }
            }


            StringBuilder newUrlBuilder = new StringBuilder(effectivelyNewPrefix);
            if (path != null) {
                newUrlBuilder.append(path);
            }
            if (query != null) {
                newUrlBuilder.append("?").append(query);
            }
            if (fragment != null) {
                newUrlBuilder.append("#").append(fragment);
            }
            return newUrlBuilder.toString();

        } catch (URISyntaxException e) {
            System.err.println("Invalid original URL: " + originalUrl + " - " + e.getMessage());
            // Fallback or error handling:
            // You might want to return the original URL, null, or throw an exception
            // For a simple string manipulation fallback if URI parsing fails:
            int firstSlashAfterProtocol = originalUrl.indexOf("//");
            if (firstSlashAfterProtocol != -1) {
                int pathStart = originalUrl.indexOf('/', firstSlashAfterProtocol + 2);
                if (pathStart != -1) {
                    return newPrefix + originalUrl.substring(pathStart);
                } else {
                     // URL might be just "http://domain.com" without a path
                    if (originalUrl.contains("?")) { // domain.com?query
                         return newPrefix + originalUrl.substring(originalUrl.indexOf("?"));
                    } else if (originalUrl.contains("#")) { // domain.com#fragment
                         return newPrefix + originalUrl.substring(originalUrl.indexOf("#"));
                    }
                    // If newPrefix is "http://new.com/" and original is "http://old.com", result is "http://new.com/"
                    // If newPrefix is "http://new.com" and original is "http://old.com", result is "http://new.com"
                    return newPrefix.endsWith("/") ? newPrefix : (originalUrl.endsWith("/") ? newPrefix + "/" : newPrefix) ;
                }
            }
            // If all else fails, a very basic concatenation (might lead to "newprefixoriginal/path")
            // This part depends heavily on expected inputs if URI parsing fails.
            // A robust solution would involve more careful string splitting.
            // For the sake_of_simplicity for this prompt focusing on URI:
            System.err.println("Could not parse original URL as URI, returning original URL or a basic attempt.");
            return originalUrl; // Or throw an exception
        }
    }

    public static void main(String[] args) {
        String originalUrl1 = "https://s315.skyearth10.xyz/heyzo/ba/a4/d8wv1rx2_5c49e63931008f03cadd4bf052a24576dd/video.m3u8";
        String newPrefix1 = "http://mynewdomain.com/some/path"; // New prefix can include a path
        String newPrefix2 = "https://another.cdn.net";
        String newPrefix3 = "http://localhost:8080/";

        System.out.println("Original: " + originalUrl1);
        System.out.println("Replaced (1): " + replaceUrlPrefix(originalUrl1, newPrefix1));
        // Expected (1): http://mynewdomain.com/some/path/heyzo/ba/a4/d8wv1rx2_5c49e63931008f03cadd4bf052a24576dd/video.m3u8

        System.out.println("Replaced (2): " + replaceUrlPrefix(originalUrl1, newPrefix2));
        // Expected (2): https://another.cdn.net/heyzo/ba/a4/d8wv1rx2_5c49e63931008f03cadd4bf052a24576dd/video.m3u8

        System.out.println("Replaced (3): " + replaceUrlPrefix(originalUrl1, newPrefix3));
        // Expected (3): http://localhost:8080/heyzo/ba/a4/d8wv1rx2_5c49e63931008f03cadd4bf052a24576dd/video.m3u8

        String originalUrl2 = "http://old.server/path/to/resource?query=123#fragment";
        String newPrefix4 = "https://new.server/app";
        System.out.println("\nOriginal: " + originalUrl2);
        System.out.println("Replaced (4): " + replaceUrlPrefix(originalUrl2, newPrefix4));
        // Expected (4): https://new.server/app/path/to/resource?query=123#fragment

        String originalUrl3 = "http://domainonly.com"; // No path
        String newPrefix5 = "https://newdomain.com/newpath";
        System.out.println("\nOriginal: " + originalUrl3);
        System.out.println("Replaced (5): " + replaceUrlPrefix(originalUrl3, newPrefix5));
         // Expected (5): https://newdomain.com/newpath

        String originalUrl4 = "http://domainwithslash.com/"; // Path is "/"
        String newPrefix6 = "https://newdomain.com/newpath"; // newPrefix does not end with "/"
        System.out.println("\nOriginal: " + originalUrl4);
        System.out.println("Replaced (6): " + replaceUrlPrefix(originalUrl4, newPrefix6));
        // Expected (6): https://newdomain.com/newpath/ (if original path "/" is preserved as is)

        String originalUrl5 = "http://domainwithquery.com?name=test"; // No path, only query
        String newPrefix7 = "https://newcdn";
        System.out.println("\nOriginal: " + originalUrl5);
        System.out.println("Replaced (7): " + replaceUrlPrefix(originalUrl5, newPrefix7));
        // Expected (7): https://newcdn?name=test
    }
}
