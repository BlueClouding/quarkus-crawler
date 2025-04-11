package org.acme.enums;

public enum CrawlerTaskType {
    GENRES("genres"),
    GENRES_PAGES("genres_pages"),
    MOVIES("movies"),
    ACTRESSES("actresses");

    private final String value;

    CrawlerTaskType(String value) {
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
