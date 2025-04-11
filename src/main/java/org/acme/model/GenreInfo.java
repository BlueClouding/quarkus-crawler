package org.acme.model;

import java.util.Optional;
import jakarta.annotation.Nullable;

public class GenreInfo {

    private String name;

    private String url;
    @Nullable
    private Long id;
    @Nullable
    private String code;
    @Nullable
    private String originalName;
    private int total;

    public GenreInfo() {
    }

    public GenreInfo(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public GenreInfo(String name, String url, @Nullable Long id, @Nullable String code, @Nullable String originalName, int total) {
        this.name = name;
        this.url = url;
        this.id = id;
        this.code = code;
        this.originalName = originalName;
        this.total = total;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    @Nullable
    public String getCode() {
        return code;
    }

    public void setCode(@Nullable String code) {
        this.code = code;
    }

    @Nullable
    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(@Nullable String originalName) {
        this.originalName = originalName;
    }

    @Override
    public String toString() {
        return "GenreInfo{" +
               "name='" + name + '\'' +
               ", url='" + url + '\'' +
               ", id=" + id +
               ", code='" + code + '\'' +
               ", originalName='" + originalName + '\'' +
               '}';
    }
}
