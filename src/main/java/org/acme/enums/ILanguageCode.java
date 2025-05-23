package org.acme.enums;

/**
 * Interface for language code enums to ensure consistency across different website implementations.
 */
public interface ILanguageCode {
    /**
     * Get the path code used in URL construction.
     * @return the path code
     */
    String getPathCode();
    
    /**
     * Get the database code used for storage.
     * @return the database code
     */
    String getDbCode();
}
