package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 电影多语言信息实体类
 * 存储特定语言的电影信息，如标题、描述等
 */
@Entity
@Table(name = "movie_info")
@NamedQueries({
    @NamedQuery(name = "MovieInfo.findByMovieUuidAndLanguage", 
                query = "SELECT m FROM MovieInfo m WHERE m.movieUuid = :movieUuid AND m.language = :language")
})
public class MovieInfo extends PanacheEntity {

    @Column(name = "movie_uuid", nullable = false)
    public UUID movieUuid;

    @Column(length = 10, nullable = false)
    public String language;  // 语言代码，如 'zh', 'en', 'ja' 等

    @Column(columnDefinition = "text")
    public String title;

    @Column(name = "description", columnDefinition = "text")
    public String description;

    @Column(columnDefinition = "varchar(255)[]")
    public List<String> tags;
    
    @Column(columnDefinition = "varchar(255)[]")
    public List<String> genres;

    @Column(length = 55)
    public String director;

    @Column(length = 55)
    public String maker;

    @Column(columnDefinition = "varchar(255)[]")
    public List<String> actresses;

    @Column(name = "series", columnDefinition = "text")
    public String series;

    @CreationTimestamp
    @Column(name = "created_at")
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    // 构造函数
    public MovieInfo() {
    }

    public MovieInfo(UUID movieUuid, String language) {
        this.movieUuid = movieUuid;
        this.language = language;
    }

    // 从Movie对象创建MovieInfo对象的工厂方法
    public static MovieInfo fromMovie(Movie movie, String language) {
        MovieInfo info = new MovieInfo(movie.movieUuid, language);
        info.title = movie.title;
        info.description = movie.description;
        info.tags = movie.tags;
        info.genres = movie.genres;
        info.director = movie.director;
        info.maker = movie.maker;
        info.actresses = movie.actresses;
        info.series = movie.series;
        return info;
    }

    // Getters and Setters
    public UUID getMovieUuid() {
        return movieUuid;
    }

    public void setMovieUuid(UUID movieUuid) {
        this.movieUuid = movieUuid;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getMaker() {
        return maker;
    }

    public void setMaker(String maker) {
        this.maker = maker;
    }

    public List<String> getActresses() {
        return actresses;
    }

    public void setActresses(List<String> actresses) {
        this.actresses = actresses;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
