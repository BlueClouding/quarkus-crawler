package org.acme.entity;

/**
 * Enum representing different video sources
 */
public enum VideoSource {
    MISSAV("missav"),
    AV123("123av");
    
    private final String value;
    
    VideoSource(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static VideoSource fromValue(String value) {
        for (VideoSource source : VideoSource.values()) {
            if (source.getValue().equals(value)) {
                return source;
            }
        }
        return null;
    }
}
