package org.acme.enums;

public enum MovieStatus {
    NEW("new"),
    PROCESSING("processing"),
    FAILED("failed"),
    ONLINE("online"),
    OFFLINE("offline");

    private final String value;

    MovieStatus(String value) {
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
