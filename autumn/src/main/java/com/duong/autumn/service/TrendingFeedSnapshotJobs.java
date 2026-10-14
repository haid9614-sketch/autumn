package com.duong.autumn.service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrendingFeedSnapshotJobs {
    private final TrendingFeedSnapshotMaintenance maintenance;
    public TrendingFeedSnapshotJobs(TrendingFeedSnapshotMaintenance maintenance) {
        this.maintenance = maintenance;
    }
    // Tách bean để transaction chạy qua Spring proxy.
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() { maintenance.buildIfDue(); }

    // Job kiểm tra mỗi phút; chỉ sinh bản mới khi đã qua ít nhất 10 phút.
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void publishWhenDue() { maintenance.buildIfDue(); }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public void cleanupUnused() { maintenance.deleteUnusedSnapshots(); }
}
