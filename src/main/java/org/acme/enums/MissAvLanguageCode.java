package org.acme.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enum representing supported language codes for MissAV website.
 * Each enum constant has a pathCode (used in URLs) and a dbCode (used in database).
 * URL format: https://missav.ai/[languagePathCode]/[movieCode]
 */
public enum MissAvLanguageCode implements ILanguageCode {
    // Traditional Chinese (default)
    TRADITIONAL_CHINESE("", "zh-tw", "zh-Hant"),
    // Simplified Chinese
    SIMPLIFIED_CHINESE("cn", "zh-cn", "zh-Hans"),
    // English
    ENGLISH("en", "en", "en"),
    // Japanese
    JAPANESE("ja", "ja", "ja"),
    // Korean
    KOREAN("ko", "ko", "ko"),
    // Malay
    MALAY("ms", "ms", "ms"),
    // Thai
    THAI("th", "th", "th"),
    // German
    GERMAN("de", "de", "de"),
    // French
    FRENCH("fr", "fr", "fr"),
    // Vietnamese
    VIETNAMESE("vi", "vi", "vi"),
    // Indonesian
    INDONESIAN("id", "id", "id"),
    // Filipino
    FILIPINO("fil", "fil", "fil"),
    // Portuguese
    PORTUGUESE("pt", "pt", "pt");

    private final String pathCode;
    private final String dbCode;
    private final String hreflang;

    // Cache for lookups by pathCode
    private static final Map<String, MissAvLanguageCode> BY_PATH_CODE = 
            Arrays.stream(values())
                  .collect(Collectors.toMap(MissAvLanguageCode::getPathCode, Function.identity(), (a, b) -> a));
    
    // Cache for lookups by dbCode
    private static final Map<String, MissAvLanguageCode> BY_DB_CODE = 
            Arrays.stream(values())
                  .collect(Collectors.toMap(MissAvLanguageCode::getDbCode, Function.identity(), (a, b) -> a));

    // Cache for lookups by hreflang
    private static final Map<String, MissAvLanguageCode> BY_HREFLANG = 
            Arrays.stream(values())
                  .collect(Collectors.toMap(MissAvLanguageCode::getHreflang, Function.identity(), (a, b) -> a));

    MissAvLanguageCode(String pathCode, String dbCode, String hreflang) {
        this.pathCode = pathCode;
        this.dbCode = dbCode;
        this.hreflang = hreflang;
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
     * Get the hreflang attribute used in HTML.
     * @return the hreflang attribute
     */
    public String getHreflang() {
        return hreflang;
    }

    /**
     * Find a language by its path code.
     * 
     * @param pathCode the path code to look up
     * @return the LanguageCode, or null if not found
     */
    public static MissAvLanguageCode findByPathCode(String pathCode) {
        return BY_PATH_CODE.get(pathCode);
    }

    /**
     * Find a language by its database code.
     * 
     * @param dbCode the database code to look up
     * @return the LanguageCode, or null if not found
     */
    public static MissAvLanguageCode findByDbCode(String dbCode) {
        return BY_DB_CODE.get(dbCode);
    }

    /**
     * Find a language by its hreflang attribute.
     * 
     * @param hreflang the hreflang attribute to look up
     * @return the LanguageCode, or null if not found
     */
    public static MissAvLanguageCode findByHreflang(String hreflang) {
        return BY_HREFLANG.get(hreflang);
    }

    /**
     * Get all supported language path codes.
     * 
     * @return array of all path codes
     */
    public static String[] getAllPathCodes() {
        return Arrays.stream(values())
                     .map(MissAvLanguageCode::getPathCode)
                     .toArray(String[]::new);
    }

    /**
     * Get all supported database codes.
     * 
     * @return array of all database codes
     */
    public static String[] getAllDbCodes() {
        return Arrays.stream(values())
                     .map(MissAvLanguageCode::getDbCode)
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
                         MissAvLanguageCode::getPathCode,
                         MissAvLanguageCode::getDbCode,
                         (a, b) -> a
                     ));
    }
}
