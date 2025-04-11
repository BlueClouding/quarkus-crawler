package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "movies")
public class Movie extends PanacheEntity {

    @Column(length = 50, nullable = false, unique = true)
    public String code;

    @Column(length = 50, nullable = false)
    public String duration;

    @Column(length = 50)
    public String releaseDate;

    @Column(columnDefinition = "text")
    public String coverImageUrl;

    @Column(columnDefinition = "text")
    public String previewVideoUrl;

    @Column(columnDefinition = "integer default 0")
    public Integer likes = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public Instant updatedAt;

    @Column(length = 255)
    public String link;

    @Column(unique = true)
    public Integer originalId;

    @Column(length = 255)
    public String thumbnail;

    @Column(columnDefinition = "text")
    public String title;

    @Column(length = 55)
    public String status;

    @Column(columnDefinition = "text")
    public String description;

    @ElementCollection
    public List<String> tags;

    @ElementCollection
    public List<String> genres;

    @Column(length = 55)
    public String director;

    @Column(length = 55)
    public String maker;

    @ElementCollection
    public List<String> actresses;

    @Column(columnDefinition = "text")
    public String watchUrlsInfo;

    @Column(columnDefinition = "text")
    public String downloadUrlsInfo;

    @Column(columnDefinition = "text")
    public String magnets;

    @Column(columnDefinition = "text")
    public String series;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getPreviewVideoUrl() {
        return previewVideoUrl;
    }

    public void setPreviewVideoUrl(String previewVideoUrl) {
        this.previewVideoUrl = previewVideoUrl;
    }

    public Integer getLikes() {
        return likes;
    }

    public void setLikes(Integer likes) {
        this.likes = likes;
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

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Integer getOriginalId() {
        return originalId;
    }

    public void setOriginalId(Integer originalId) {
        this.originalId = originalId;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getWatchUrlsInfo() {
        return watchUrlsInfo;
    }

    public void setWatchUrlsInfo(String watchUrlsInfo) {
        this.watchUrlsInfo = watchUrlsInfo;
    }

    public String getDownloadUrlsInfo() {
        return downloadUrlsInfo;
    }

    public void setDownloadUrlsInfo(String downloadUrlsInfo) {
        this.downloadUrlsInfo = downloadUrlsInfo;
    }

    public String getMagnets() {
        return magnets;
    }

    public void setMagnets(String magnets) {
        this.magnets = magnets;
    }

    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }
}
