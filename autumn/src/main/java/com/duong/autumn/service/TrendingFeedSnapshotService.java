package com.duong.autumn.service;
import com.duong.autumn.entity.TrendingFeedSession;
import com.duong.autumn.entity.TrendingFeedSnapshot;
import com.duong.autumn.feed.FeedCursor;
import com.duong.autumn.feed.TrendingFeedSessionRef;
import com.duong.autumn.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Service
public class TrendingFeedSnapshotService {
    private static final Duration BUILD_INTERVAL = Duration.ofMinutes(10);
    private static final Duration CLOSED_SESSION_RETENTION = Duration.ofDays(1);
    private final TrendingFeedSnapshotRepository snapshots;
    private final TrendingFeedSessionRepository sessions;
    private final PostRepository posts;
    public TrendingFeedSnapshotService(TrendingFeedSnapshotRepository snapshots,
                                       TrendingFeedSessionRepository sessions,
                                       PostRepository posts) {
        this.snapshots = snapshots;
        this.sessions = sessions;
        this.posts = posts;
    }

    // Chỉ gọi từ Jobs: monitor của Jobs bao trùm cả lúc transaction này commit.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean buildIfDue() {
        Instant now = Instant.now();
        var latest = snapshots.findFirstByOrderByCreatedAtDescIdDesc();
        if (latest.isPresent()
                && latest.get().getCreatedAt().plus(BUILD_INTERVAL).isAfter(now)) {
            return false;
        }
        String id = UUID.randomUUID().toString();
        snapshots.saveAndFlush(new TrendingFeedSnapshot(id, now));
        posts.populateTrendingSnapshot(id);
        // Header và item commit cùng nhau. Lỗi thì rollback cả snapshot mới.
        return true;
    }

    // Jobs tuần tự hóa build và cleanup trong một instance backend.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int deleteUnusedSnapshots() {
        sessions.deleteReleasedBefore(Instant.now().minus(CLOSED_SESSION_RETENTION));
        var latest = snapshots.findFirstByOrderByCreatedAtDescIdDesc();
        if (latest.isEmpty()) return 0;
        String latestId = latest.get().getId();
        int deleted = 0;
        for (String id : snapshots.findUnusedIds(latestId, PageRequest.of(0, 50))) {
            var snapshot = snapshots.findForWrite(id).orElse(null);
            // Sau khi chờ khóa, kiểm tra lại: một request có thể vừa ghim bản này.
            if (snapshot != null && !latestId.equals(id)
                    && !sessions.existsBySnapshotId(id)) {
                snapshots.delete(snapshot);
                deleted++;
            }
        }
        snapshots.flush();
        return deleted;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrendingFeedSessionRef open(Long userId, FeedCursor cursor, String requestedSessionId) {
        if (cursor != null) {
            // Migration tạo session cho cursor v1 với id = snapshotId cũ.
            String id = cursor.version() == 1 ? cursor.snapshotId() : cursor.sessionId();
            validateId(id);
            if (requestedSessionId != null && !id.equals(requestedSessionId)) throw badSession();
            TrendingFeedSession session = sessions.findForRead(id).orElseThrow(this::closedSession);
            validateOwner(session, userId);
            if (session.getReleasedAt() != null || session.getSnapshotId() == null) throw closedSession();
            if (!Objects.equals(session.getSnapshotId(), cursor.snapshotId())) throw badSession();
            snapshots.findForRead(session.getSnapshotId()).orElseThrow(this::closedSession);
            return new TrendingFeedSessionRef(id, session.getSnapshotId());
        }
        String id = requestedSessionId == null ? UUID.randomUUID().toString() : requestedSessionId;
        validateId(id);
        sessions.ensureExists(id, userId, Instant.now());
        TrendingFeedSession session = sessions.findForWrite(id).orElseThrow(this::closedSession);
        validateOwner(session, userId);
        if (session.getReleasedAt() != null) throw closedSession();
        if (session.getSnapshotId() == null) {
            session.attach(latestSnapshotForRead().getId());
            sessions.saveAndFlush(session);
        } else {
            // Retry lần đầu không đổi snapshot dù đã có bản mới hơn.
            snapshots.findForRead(session.getSnapshotId()).orElseThrow(this::closedSession);
        }
        return new TrendingFeedSessionRef(id, session.getSnapshotId());
    }

    private TrendingFeedSnapshot latestSnapshotForRead() {
        // Không khóa cả truy vấn ORDER BY: người mở feed vẫn đọc được bản đã commit
        // trong lúc job đang tạo bản mới. Khóa theo id bảo vệ bước gắn Session.
        for (int attempt = 0; attempt < 3; attempt++) {
            var latest = snapshots.findFirstByOrderByCreatedAtDescIdDesc()
                    .orElseThrow(this::notReady);
            var locked = snapshots.findForRead(latest.getId());
            if (locked.isPresent()) return locked.get();
            // Cleanup đã xóa bản vừa chọn sau khi một bản mới hơn được publish.
        }
        throw notReady();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(Long userId, String id) {
        validateId(id);
        Instant now = Instant.now();
        sessions.ensureExists(id, userId, now);
        TrendingFeedSession session = sessions.findForWrite(id).orElseThrow(this::closedSession);
        validateOwner(session, userId);
        // Giữ marker đóng 24h, không cho GET bị hủy đến muộn mở lại phiên.
        session.release(now);
        sessions.saveAndFlush(session);
    }

    private void validateOwner(TrendingFeedSession session, Long userId) {
        if (userId == null || !Objects.equals(session.getUserId(), userId)) throw badSession();
    }
    private void validateId(String id) {
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id)) throw badSession();
        } catch (IllegalArgumentException exception) { throw badSession(); }
    }
    private ResponseStatusException badSession() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Phiên bảng tin không hợp lệ hoặc không thuộc tài khoản hiện tại.");
    }
    private ResponseStatusException closedSession() {
        return new ResponseStatusException(HttpStatus.GONE,
                "Phiên bảng tin đã được đóng. Hãy mở lại bảng tin.");
    }
    private ResponseStatusException notReady() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Bảng xếp hạng đang được chuẩn bị. Hãy thử lại sau.");
    }



}
