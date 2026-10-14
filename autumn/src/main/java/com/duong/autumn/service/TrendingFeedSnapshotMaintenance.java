package com.duong.autumn.service;
import com.duong.autumn.entity.TrendingFeedSnapshot;
import com.duong.autumn.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TrendingFeedSnapshotMaintenance {
    private static final Duration BUILD_INTERVAL = Duration.ofMinutes(10);
    private final TrendingFeedSnapshotRepository snapshots;
    private final TrendingFeedHeadRepository heads;
    private final TrendingFeedSessionRepository sessions;
    private final PostRepository posts;
    public TrendingFeedSnapshotMaintenance(TrendingFeedSnapshotRepository snapshots,
                                          TrendingFeedHeadRepository heads,
                                          TrendingFeedSessionRepository sessions,
                                          PostRepository posts) {
        this.snapshots = snapshots; this.heads = heads; this.sessions = sessions; this.posts = posts;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean buildIfDue() {
        // Khóa DB dùng chung giữa các backend, không chỉ synchronized trong một JVM.
        if (heads.acquireBuildLock() == null) throw new IllegalStateException("Chưa chạy SQL trending.");
        var head = heads.findById(1).orElseThrow(
                () -> new IllegalStateException("Thiếu dòng head trending id = 1."));
        Instant now = Instant.now();
        if (head.getSnapshotId() != null && head.getGeneratedAt() != null
                && head.getGeneratedAt().plus(BUILD_INTERVAL).isAfter(now)) return false;
        String id = UUID.randomUUID().toString();
        snapshots.saveAndFlush(new TrendingFeedSnapshot(id, now));
        posts.populateTrendingSnapshot(id);
        // Chỉ publish sau khi item đã đầy đủ; job commit/rollback cùng nhau.
        head.publish(id, Instant.now());
        heads.saveAndFlush(head);
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int deleteUnusedSnapshots() {
        int deleted = 0;
        sessions.deleteReleasedBefore(Instant.now().minus(Duration.ofDays(1)));
        for (String id : snapshots.findUnusedIds(PageRequest.of(0, 50))) {
            var snapshot = snapshots.findForWrite(id).orElse(null);
            // Kiểm tra lại sau khi khóa: phiên khác có thể vừa ghim snapshot.
            if (snapshot != null && !heads.existsBySnapshotId(id)
                    && !sessions.existsBySnapshotId(id)) {
                snapshots.delete(snapshot);
                deleted++;
            }
        }
        snapshots.flush();
        return deleted;
    }
}
