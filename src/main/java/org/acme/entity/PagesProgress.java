package org.acme.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pages_progress")
public class PagesProgress extends PanacheEntity {

    @Column(name = "crawler_progress_id", nullable = false)
    private Integer crawlerProgressId;

    @Column(name = "relation_id", nullable = false)
    private Integer relationId;

    @Column(name = "page_type", length = 50, nullable = false)
    private String pageType;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "total_pages", nullable = false)
    private Integer totalPages;

    @Column(name = "status", length = 20, nullable = false, columnDefinition = "character varying(20) default 'pending'")
    private String status;

    @UpdateTimestamp
    @Column(name = "last_update")
    private OffsetDateTime lastUpdate;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "total_items")
    private Integer totalItems;

    public Integer getCrawlerProgressId() {
        return crawlerProgressId;
    }

    public void setCrawlerProgressId(Integer crawlerProgressId) {
        this.crawlerProgressId = crawlerProgressId;
    }

    public Integer getRelationId() {
        return relationId;
    }

    public void setRelationId(Integer relationId) {
        this.relationId = relationId;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(OffsetDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    
}
