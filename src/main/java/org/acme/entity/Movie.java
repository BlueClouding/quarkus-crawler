package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "movies")
public class Movie extends PanacheEntity {

    @Column(name = "movie_uuid", nullable = false, unique = true)
    public UUID movieUuid = UUID.randomUUID();
    
    @Column(length = 50, nullable = false, unique = true)
    public String code;

    @Column(length = 50, nullable = false)
    public String duration;

    @Column(name = "release_date", length = 50)
    public String releaseDate;

    @Column(name = "cover_image_url", columnDefinition = "text")
    public String coverImageUrl;

    @Column(name = "preview_video_url", columnDefinition = "text")
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

    @Column(name = "original_id", unique = true)
    public Integer originalId;

    @Column(length = 255)
    public String thumbnail;

    @Column(columnDefinition = "text")
    public String title;

    @Column(length = 55)
    public String status;

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

    @Column(name = "watch_urls_info", columnDefinition = "text")
    public String watchUrlsInfo;

    @Column(name = "download_urls_info", columnDefinition = "text")
    public String downloadUrlsInfo;

    @Column(name = "magnets", columnDefinition = "text")
    public String magnets;

    @Column(name = "series", columnDefinition = "text")
    public String series;

    public UUID getMovieUuid() {
        return movieUuid;
    }
    
    public void setMovieUuid(UUID movieUuid) {
        this.movieUuid = movieUuid;
    }

    public String getCode() {
        return code;
    }

    /**
     * 用另一个 Movie 对象补全当前对象所有为 null 或空集合的字段
     */
    public void updateIfNullFields(Movie other) {
        if (this.code == null && other.code != null) this.code = other.code;
        if (this.duration == null && other.duration != null) this.duration = other.duration;
        if (this.releaseDate == null && other.releaseDate != null) this.releaseDate = other.releaseDate;
        if (this.coverImageUrl == null && other.coverImageUrl != null) this.coverImageUrl = other.coverImageUrl;
        if (this.previewVideoUrl == null && other.previewVideoUrl != null) this.previewVideoUrl = other.previewVideoUrl;
        if (this.likes == null && other.likes != null) this.likes = other.likes;
        if (this.createdAt == null && other.createdAt != null) this.createdAt = other.createdAt;
        if (this.updatedAt == null && other.updatedAt != null) this.updatedAt = other.updatedAt;
        if (this.link == null && other.link != null) this.link = other.link;
        if (this.originalId == null && other.originalId != null) this.originalId = other.originalId;
        if (this.thumbnail == null && other.thumbnail != null) this.thumbnail = other.thumbnail;
        if (this.title == null && other.title != null) this.title = other.title;
        if (this.status == null && other.status != null) this.status = other.status;
        if (this.description == null && other.description != null) this.description = other.description;
        if ((this.tags == null || this.tags.isEmpty()) && other.tags != null && !other.tags.isEmpty()) this.tags = other.tags;
        if ((this.genres == null || this.genres.isEmpty()) && other.genres != null && !other.genres.isEmpty()) this.genres = other.genres;
        if (this.director == null && other.director != null) this.director = other.director;
        if (this.maker == null && other.maker != null) this.maker = other.maker;
        if ((this.actresses == null || this.actresses.isEmpty()) && other.actresses != null && !other.actresses.isEmpty()) this.actresses = other.actresses;
        if (this.watchUrlsInfo == null && other.watchUrlsInfo != null) this.watchUrlsInfo = other.watchUrlsInfo;
        if (this.downloadUrlsInfo == null && other.downloadUrlsInfo != null) this.downloadUrlsInfo = other.downloadUrlsInfo;
        if (this.magnets == null && other.magnets != null) this.magnets = other.magnets;
        if (this.series == null && other.series != null) this.series = other.series;
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
    
    /**
     * 根据id和另一个Movie对象批量更新非空字段
     * 注意：集合字段(tags, genres, actresses)不在此处更新，避免类型不匹配错误
     */
    public void updateFieldsById(Long id, Movie other) {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();
        
        // 按照实体字段顺序依次判断并添加
        if (other.code != null) {
            sb.append("code = ?").append(params.size() + 1).append(",");
            params.add(other.code);
        }
        if (other.duration != null) {
            sb.append("duration = ?").append(params.size() + 1).append(",");
            params.add(other.duration);
        }
        if (other.releaseDate != null) {
            sb.append("releaseDate = ?").append(params.size() + 1).append(",");
            params.add(other.releaseDate);
        }
        if (other.coverImageUrl != null) {
            sb.append("coverImageUrl = ?").append(params.size() + 1).append(",");
            params.add(other.coverImageUrl);
        }
        if (other.previewVideoUrl != null) {
            sb.append("previewVideoUrl = ?").append(params.size() + 1).append(",");
            params.add(other.previewVideoUrl);
        }
        if (other.likes != null) {
            sb.append("likes = ?").append(params.size() + 1).append(",");
            params.add(other.likes);
        }
        if (other.createdAt != null) {
            sb.append("createdAt = ?").append(params.size() + 1).append(",");
            params.add(other.createdAt);
        }
        if (other.updatedAt != null) {
            sb.append("updatedAt = ?").append(params.size() + 1).append(",");
            params.add(other.updatedAt);
        }
        if (other.link != null) {
            sb.append("link = ?").append(params.size() + 1).append(",");
            params.add(other.link);
        }
        if (other.originalId != null) {
            sb.append("originalId = ?").append(params.size() + 1).append(",");
            params.add(other.originalId);
        }
        if (other.thumbnail != null) {
            sb.append("thumbnail = ?").append(params.size() + 1).append(",");
            params.add(other.thumbnail);
        }
        if (other.title != null) {
            sb.append("title = ?").append(params.size() + 1).append(",");
            params.add(other.title);
        }
        if (other.status != null) {
            sb.append("status = ?").append(params.size() + 1).append(",");
            params.add(other.status);
        }
        if (other.description != null) {
            sb.append("description = ?").append(params.size() + 1).append(",");
            params.add(other.description);
        }
        // 跳过actresses集合字段，避免类型不匹配错误
        if (other.watchUrlsInfo != null) {
            sb.append("watchUrlsInfo = ?").append(params.size() + 1).append(",");
            params.add(other.watchUrlsInfo);
        }
        if (other.downloadUrlsInfo != null) {
            sb.append("downloadUrlsInfo = ?").append(params.size() + 1).append(",");
            params.add(other.downloadUrlsInfo);
        }
        if (other.magnets != null) {
            sb.append("magnets = ?").append(params.size() + 1).append(",");
            params.add(other.magnets);
        }
        if (other.series != null) {
            sb.append("series = ?").append(params.size() + 1).append(",");
            params.add(other.series);
        }
        // 如果没有字段需要更新，直接返回
        if (sb.length() == 0) return;
        
        // 去掉最后一个逗号并添加where条件
        sb.setLength(sb.length() - 1);
        String query = sb.toString() + " where id = ?" + (params.size() + 1);
        params.add(id);
        
        // 执行更新
        update(query, params.toArray());
    }
}
