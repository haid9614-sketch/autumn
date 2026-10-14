package com.duong.autumn.entity;
import jakarta.persistence.*;
import java.time.Instant;

/** Một dòng id = 1, trỏ tới phiên bản đã tạo hoàn chỉnh. */
@Entity
@Table(name = "feed_trending_head")
public class TrendingFeedHead {
    @Id private Integer id;
    @Column(name = "snapshot_id", length = 36)
    private String snapshotId;
    @Column(name = "generated_at")
    private Instant generatedAt;
    protected TrendingFeedHead() {}
    public TrendingFeedHead(Integer id, String snapshotId, Instant generatedAt) {
        this.id = id; this.snapshotId = snapshotId; this.generatedAt = generatedAt;
    }
    public Integer getId() { return id; }
    public String getSnapshotId() { return snapshotId; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void publish(String snapshotId, Instant generatedAt) {
        this.snapshotId = snapshotId; this.generatedAt = generatedAt;
    }
}
