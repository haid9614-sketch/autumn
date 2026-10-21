package com.duong.autumn.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Một dòng tham chiếu cho mỗi màn hình; không lưu danh sách bài. */
@Entity
@Table(name = "feed_trending_sessions", indexes = {
        @Index(name = "idx_trending_session_snapshot", columnList = "snapshot_id"),
        @Index(name = "idx_trending_session_release", columnList = "released_at")
})
public class TrendingFeedSession {
    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "snapshot_id", length = 36) private String snapshotId;

    // Khai báo khóa ngoại ON DELETE RESTRICT, tự động tạo trên mọi môi trường
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "snapshot_id",
            referencedColumnName = "id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_trending_session_snapshot",
                    foreignKeyDefinition = "FOREIGN KEY (snapshot_id) REFERENCES feed_trending_snapshots(id) ON DELETE RESTRICT"
            )
    )
    private TrendingFeedSnapshot snapshot;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "released_at") private Instant releasedAt;

    protected TrendingFeedSession() {}

    public TrendingFeedSession(String id, Long userId, String snapshotId,
                               Instant createdAt, Instant releasedAt) {
        this.id = id; this.userId = userId; this.snapshotId = snapshotId;
        this.createdAt = createdAt; this.releasedAt = releasedAt;
    }

    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSnapshotId() { return snapshotId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public void attach(String snapshotId) { this.snapshotId = snapshotId; }
    public void release(Instant now) { this.snapshotId = null; this.releasedAt = now; }
}