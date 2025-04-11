package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "genres")
public class Genre extends PanacheEntity {

    @CreationTimestamp
    @Column(name = "created_at")
    public Instant createdAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    public List<String> urls;

    @Column(length = 255)
    public String code;

    @Column(columnDefinition = "integer default 0")
    public Integer total;
}
