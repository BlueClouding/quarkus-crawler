package org.acme.enums;

public enum CrawlerStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    STOPPED("stopped"),
    NEW("new");

    private final String value;

    CrawlerStatus(String value) {
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
