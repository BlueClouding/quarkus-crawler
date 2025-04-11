package org.acme.enums;

public enum SupportedLanguage {
    ENGLISH("en"),
    JAPANESE("ja"),
    CHINESE("zh");

    private final String value;

    SupportedLanguage(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
