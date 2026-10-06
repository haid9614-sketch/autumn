package com.duong.autumn.service;

import com.duong.autumn.entity.TrendingFeedSnapshot;
import com.duong.autumn.repository.PostRepository;
import com.duong.autumn.repository.TrendingFeedSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrendingFeedSnapshotService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final TrendingFeedSnapshotRepository snapshotRepository;
    private final PostRepository postRepository;

    @Transactional
    public String open(Long userId, String snapshotId) {
        Instant now = Instant.now();
        if (snapshotId == null) {
            String id = UUID.randomUUID().toString();
            snapshotRepository.saveAndFlush(new TrendingFeedSnapshot(
                    id, userId, now, now.plus(SESSION_TTL)));
            // INSERT ... SELECT: chốt toàn bộ sort key trong DB, không tải hết bài vào RAM.
            postRepository.populateTrendingSnapshot(id);
            return id;
        }

        TrendingFeedSnapshot snapshot = snapshotRepository.findForRead(snapshotId)
                .orElseThrow(this::expiredSession);
        if (!Objects.equals(snapshot.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phiên bảng tin không thuộc tài khoản hiện tại.");
        }
        if (!snapshot.getExpiresAt().isAfter(now)) {
            throw expiredSession();
        }
        return snapshot.getId();
    }

    @Scheduled(fixedDelay = 300_000L)
    @Transactional
    public void deleteExpiredSnapshots() {
        snapshotRepository.deleteExpired(Instant.now());
    }

    private ResponseStatusException expiredSession() {
        return new ResponseStatusException(HttpStatus.GONE,
                "Phiên xu hướng đã hết hạn. Hãy tải lại bảng tin.");
    }
}

