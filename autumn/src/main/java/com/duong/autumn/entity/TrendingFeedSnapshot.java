package com.duong.autumn.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "feed_trending_snapshots", indexes = {
        @Index(name = "idx_feed_snapshot_expiry", columnList = "expires_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TrendingFeedSnapshot {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
