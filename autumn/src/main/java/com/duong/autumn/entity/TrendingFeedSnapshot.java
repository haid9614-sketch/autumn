package com.duong.autumn.entity;
import jakarta.persistence.*;
import java.time.Instant;

/** Một phiên bản xếp hạng chung; sort key không bị sửa sau khi tạo. */
@Entity
@Table(name = "feed_trending_snapshots", indexes = {
        @Index(name = "idx_trending_snapshot_created", columnList = "created_at, id")
})
public class TrendingFeedSnapshot {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected TrendingFeedSnapshot() {}
    public TrendingFeedSnapshot(String id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
}
