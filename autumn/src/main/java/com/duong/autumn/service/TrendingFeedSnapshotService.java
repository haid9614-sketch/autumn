package com.duong.autumn.service;
import com.duong.autumn.entity.TrendingFeedSession;
import com.duong.autumn.feed.FeedCursor;
import com.duong.autumn.feed.TrendingFeedSessionRef;
import com.duong.autumn.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class TrendingFeedSnapshotService {
    private final TrendingFeedSnapshotRepository snapshots;
    private final TrendingFeedHeadRepository heads;
    private final TrendingFeedSessionRepository sessions;
    public TrendingFeedSnapshotService(TrendingFeedSnapshotRepository snapshots,
                                      TrendingFeedHeadRepository heads,
                                      TrendingFeedSessionRepository sessions) {
        this.snapshots = snapshots; this.heads = heads; this.sessions = sessions;
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
            var head = heads.findForRead().orElseThrow(this::notReady);
            if (head.getSnapshotId() == null) throw notReady();
            snapshots.findForRead(head.getSnapshotId()).orElseThrow(this::notReady);
            session.attach(head.getSnapshotId());
            sessions.saveAndFlush(session);
        } else {
            // Retry lần đầu không đổi snapshot dù head đã được cập nhật.
            snapshots.findForRead(session.getSnapshotId()).orElseThrow(this::closedSession);
        }
        return new TrendingFeedSessionRef(id, session.getSnapshotId());
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
