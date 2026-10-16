package com.duong.autumn.service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TrendingFeedSnapshotJobs {
    private static final Logger log = LoggerFactory.getLogger(TrendingFeedSnapshotJobs.class);
    private final TrendingFeedSnapshotService snapshots;
    public TrendingFeedSnapshotJobs(TrendingFeedSnapshotService snapshots) {
        this.snapshots = snapshots;
    }
    // Jobs gọi Service khác bean để transaction thật sự chạy qua Spring proxy.
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { publishWhenDue(); }

    // Job kiểm tra mỗi phút; chỉ sinh bản mới khi đã qua ít nhất 10 phút.
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public synchronized void publishWhenDue() {
        try {
            if (snapshots.buildIfDue()) log.info("Đã tạo snapshot Trending chung mới.");
        } catch (RuntimeException exception) {
            // Service đã rollback trước khi tới đây; lần kiểm tra sau sẽ thử lại.
            log.error("Chưa tạo được snapshot Trending; giữ bản đã commit và thử lại sau.", exception);
        }
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public synchronized void cleanupUnused() {
        try {
            int deleted = snapshots.deleteUnusedSnapshots();
            if (deleted > 0) log.info("Đã dọn {} snapshot Trending không còn dùng.", deleted);
        } catch (RuntimeException exception) {
            log.error("Chưa dọn được snapshot Trending; sẽ thử lại ở lượt sau.", exception);
        }
    }
}
