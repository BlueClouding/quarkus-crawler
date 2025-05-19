package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "watch_urls", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"movie_id", "url"})
})
public class WatchUrl extends PanacheEntity {
    
    // Default source for this entity
    public static final VideoSource DEFAULT_SOURCE = VideoSource.MISSAV;

    @Column(name = "movie_id")
    public Integer movieId;
    
    @Column(name = "code")
    public String code;

    @Column(name = "source")
    public String source;

    public String url;

    @Column(name = "original_url")
    public String originalUrl;

    public String name;

    // 'index' 是 SQL 保留关键字。可以在数据库中用反引号包裹或映射成其他字段名
    @Column(name = "`index`")
    public Integer index;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    public ZonedDateTime createdAt = ZonedDateTime.now();

    public String status;

    public Integer getMovieId() {
        return movieId;
    }

    public void setMovieId(Integer movieId) {
        this.movieId = movieId;
    }
    
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
    
    /**
     * Check if this WatchUrl has a specific source
     */
    public boolean hasSource(VideoSource videoSource) {
        return videoSource.getValue().equals(this.source);
    }
}
