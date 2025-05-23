package org.acme.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enum representing supported language codes for 123AV website.
 * Each enum constant has a pathCode (used in URLs) and a dbCode (used in database).
 * URL format: https://123av.com/[languagePathCode]/[movieLink]
 */
public enum Av123LanguageCode implements ILanguageCode {
    // Traditional Chinese (default)
    TRADITIONAL_CHINESE("zh", "zh-tw"),
    // Simplified Chinese
    SIMPLIFIED_CHINESE("cn", "zh-cn"),
    // English
    ENGLISH("en", "en"),
    // Japanese
    JAPANESE("ja", "ja"),
    // Korean
    KOREAN("ko", "ko"),
    // Malay
    MALAY("ms", "ms"),
    // Thai
    THAI("th", "th"),
    // German
    GERMAN("de", "de"),
    // French
    FRENCH("fr", "fr"),
    // Vietnamese
    VIETNAMESE("vi", "vi"),
    // Indonesian
    INDONESIAN("id", "id"),
    // Filipino
    FILIPINO("fil", "fil"),
    // Portuguese
    PORTUGUESE("pt", "pt"),
    // Hindi
    HINDI("hi", "hi");

    private final String pathCode;
    private final String dbCode;

    // Cache for lookups by pathCode
    private static final Map<String, Av123LanguageCode> BY_PATH_CODE = 
            Arrays.stream(values())
                  .collect(Collectors.toMap(Av123LanguageCode::getPathCode, Function.identity(), (a, b) -> a));
    
    // Cache for lookups by dbCode
    private static final Map<String, Av123LanguageCode> BY_DB_CODE = 
            Arrays.stream(values())
                  .collect(Collectors.toMap(Av123LanguageCode::getDbCode, Function.identity(), (a, b) -> a));

    Av123LanguageCode(String pathCode, String dbCode) {
        this.pathCode = pathCode;
        this.dbCode = dbCode;
    }

    /**
     * Get the path code used in URL construction.
     * @return the path code
     */
    public String getPathCode() {
        return pathCode;
    }

    /**
     * Get the database code used for storage.
     * @return the database code
     */
    public String getDbCode() {
        return dbCode;
    }

    /**
     * Find a language by its path code.
     * 
     * @param pathCode the path code to look up
     * @return the Av123LanguageCode, or null if not found
     */
    public static Av123LanguageCode findByPathCode(String pathCode) {
        return BY_PATH_CODE.get(pathCode);
    }

    /**
     * Find a language by its database code.
     * 
     * @param dbCode the database code to look up
     * @return the Av123LanguageCode, or null if not found
     */
    public static Av123LanguageCode findByDbCode(String dbCode) {
        return BY_DB_CODE.get(dbCode);
    }

    /**
     * Get all supported language path codes.
     * 
     * @return array of all path codes
     */
    public static String[] getAllPathCodes() {
        return Arrays.stream(values())
                     .map(Av123LanguageCode::getPathCode)
                     .toArray(String[]::new);
    }

    /**
     * Get all supported database codes.
     * 
     * @return array of all database codes
     */
    public static String[] getAllDbCodes() {
        return Arrays.stream(values())
                     .map(Av123LanguageCode::getDbCode)
                     .toArray(String[]::new);
    }

    /**
     * Convert to entries map for backward compatibility.
     * 
     * @return Map of path code to database code
     */
    public static Map<String, String> toMap() {
        return Arrays.stream(values())
                     .collect(Collectors.toMap(
                         Av123LanguageCode::getPathCode,
                         Av123LanguageCode::getDbCode,
                         (a, b) -> a
                     ));
    }
}
