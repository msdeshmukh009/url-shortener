package com.urlshortener.url_shortener.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "url_shortener")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlShortener {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "original_url", nullable = false, length = 100)
    private String originalUrl;

    @Column(name = "short_code", nullable = false, unique = true, length = 50)
    private String shortCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "visit_count")
    private Integer visitCount;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.visitCount = 0;
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }
}
