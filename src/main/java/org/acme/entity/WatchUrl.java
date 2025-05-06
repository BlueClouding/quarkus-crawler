package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "watch_urls", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"movie_id", "url"})
})
public class WatchUrl extends PanacheEntity {

    @Column(name = "movie_id", nullable = false)
    public Integer movieId;

    @Column(nullable = false)
    public String url;

    @Column(name = "original_url")
    public String originalUrl;

    public String name;

    // 'index' 是 SQL 保留关键字。可以在数据库中用反引号包裹或映射成其他字段名
    @Column(name = "`index`", nullable = false)
    public Integer index;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    public ZonedDateTime createdAt = ZonedDateTime.now();

    public Integer getMovieId() {
        return movieId;
    }

    public void setMovieId(Integer movieId) {
        this.movieId = movieId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
}
