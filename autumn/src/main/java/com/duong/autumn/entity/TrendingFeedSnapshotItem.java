package com.duong.autumn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "feed_trending_items", indexes = {
        @Index(name = "idx_feed_snapshot_order",
                columnList = "snapshot_id, like_count, post_created_at, post_id")
})
@IdClass(TrendingFeedSnapshotItemId.class)
@Getter
@NoArgsConstructor
public class TrendingFeedSnapshotItem {
    @Id
    @Column(name = "snapshot_id", nullable = false, length = 36)
    private String snapshotId;

    @Id
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "post_created_at", nullable = false)
    private LocalDateTime postCreatedAt;

    // FK với ON DELETE CASCADE để dọn cả phiên và thứ tự bài bằng một DELETE.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_feed_item_snapshot"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TrendingFeedSnapshot snapshot;
}
