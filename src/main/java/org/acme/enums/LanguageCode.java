package org.acme.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for language codes with backward compatibility.
 * This class delegates to Av123LanguageCode by default for backward compatibility.
 */
public class LanguageCode {
    
    // Prevent instantiation
    private LanguageCode() {}
    
    /**
     * Get all values of the 123AV language codes.
     * 
     * @return array of Av123LanguageCode values
     */
    public static Av123LanguageCode[] values() {
        return Av123LanguageCode.values();
    }
    
    /**
     * Find a language by its path code.
     * 
     * @param pathCode the path code to look up
     * @return the Av123LanguageCode, or null if not found
     */
    public static Av123LanguageCode findByPathCode(String pathCode) {
        return Av123LanguageCode.findByPathCode(pathCode);
    }
    
    /**
     * Find a language by its database code.
     * 
     * @param dbCode the database code to look up
     * @return the Av123LanguageCode, or null if not found
     */
    public static Av123LanguageCode findByDbCode(String dbCode) {
        return Av123LanguageCode.findByDbCode(dbCode);
    }

    /**
     * Get all supported language path codes.
     * 
     * @return array of all path codes
     */
    public static String[] getAllPathCodes() {
        return Av123LanguageCode.getAllPathCodes();
    }

    /**
     * Get all supported database codes.
     * 
     * @return array of all database codes
     */
    public static String[] getAllDbCodes() {
        return Av123LanguageCode.getAllDbCodes();
    }

    /**
     * Convert to entries map for backward compatibility.
     * 
     * @return Map of path code to database code
     */
    public static Map<String, String> toMap() {
        return Av123LanguageCode.toMap();
    }
    
    /**
     * Get site-specific language code implementation based on the site identifier.
     * 
     * @param site "missav" or "123av"
     * @return appropriate language code values array
     */
    public static ILanguageCode[] getSiteLanguageCodes(String site) {
        if ("missav".equalsIgnoreCase(site)) {
            MissAvLanguageCode[] values = MissAvLanguageCode.values();
            return Arrays.copyOf(values, values.length, ILanguageCode[].class);
        } else {
            // Default to 123av
            Av123LanguageCode[] values = Av123LanguageCode.values();
            return Arrays.copyOf(values, values.length, ILanguageCode[].class);
        }
    }
    
    /**
     * Get all language codes from all supported sites.
     * 
     * @return a combined map of all language codes keyed by database code
     */
    public static Map<String, ILanguageCode> getAllLanguageCodes() {
        Map<String, ILanguageCode> allCodes = Arrays.stream(MissAvLanguageCode.values())
            .collect(Collectors.toMap(
                MissAvLanguageCode::getDbCode,
                languageCode -> languageCode,
                (a, b) -> a
            ));
            
        Map<String, ILanguageCode> av123Codes = Arrays.stream(Av123LanguageCode.values())
            .collect(Collectors.toMap(
                Av123LanguageCode::getDbCode,
                languageCode -> languageCode,
                (a, b) -> a
            ));
            
        allCodes.putAll(av123Codes);
        return allCodes;
    }
}
