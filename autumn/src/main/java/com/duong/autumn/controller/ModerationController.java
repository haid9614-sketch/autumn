package com.duong.autumn.controller;

import com.duong.autumn.entity.ContentReport;
import com.duong.autumn.entity.UserBlock;
import com.duong.autumn.repository.ContentReportRepository;
import com.duong.autumn.repository.UserBlockRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationController {

    private final UserBlockRepository userBlockRepository;
    private final ContentReportRepository contentReportRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getMyModerationData(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getId();

        List<Long> blockedUserIds = userBlockRepository.findByUserId(userId).stream()
                .map(UserBlock::getBlockedUserId)
                .collect(Collectors.toList());

        List<Long> reportedPostIds = contentReportRepository.findByUserId(userId).stream()
                .filter(r -> "POST".equals(r.getTargetType()))
                .map(ContentReport::getTargetId)
                .collect(Collectors.toList());

        List<Long> reportedCommentIds = contentReportRepository.findByUserId(userId).stream()
                .filter(r -> "COMMENT".equals(r.getTargetType()))
                .map(ContentReport::getTargetId)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "blockedUserIds", blockedUserIds,
                "reportedPostIds", reportedPostIds,
                "reportedCommentIds", reportedCommentIds
        ));
    }

    // Chặn người dùng + Tự động hủy Follow 2 chiều
    @PostMapping("/block/{targetUserId}")
    @Transactional
    public ResponseEntity<?> blockUser(@AuthenticationPrincipal CustomUserDetails userDetails,
                                       @PathVariable Long targetUserId) {
        Long userId = userDetails.getId();
        if (userId.equals(targetUserId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể tự chặn chính mình"));
        }

        if (!userBlockRepository.existsByUserIdAndBlockedUserId(userId, targetUserId)) {
            UserBlock block = new UserBlock();
            block.setUserId(userId);
            block.setBlockedUserId(targetUserId);
            userBlockRepository.save(block);
        }

        // Xóa luôn quan hệ follow 2 chiều giữa 2 người dùng
        userBlockRepository.deleteFollowsBetween(userId, targetUserId);

        return ResponseEntity.ok(Map.of("message", "Đã chặn người dùng thành công"));
    }

    @DeleteMapping("/block/{targetUserId}")
    @Transactional
    public ResponseEntity<?> unblockUser(@AuthenticationPrincipal CustomUserDetails userDetails,
                                         @PathVariable Long targetUserId) {
        Long userId = userDetails.getId();
        userBlockRepository.deleteByUserIdAndBlockedUserId(userId, targetUserId);
        return ResponseEntity.ok(Map.of("message", "Đã bỏ chặn thành công"));
    }

    @PostMapping("/report")
    @Transactional
    public ResponseEntity<?> reportContent(@AuthenticationPrincipal CustomUserDetails userDetails,
                                           @RequestBody Map<String, Object> request) {
        Long userId = userDetails.getId();
        String targetType = (String) request.get("targetType");
        Long targetId = Long.valueOf(request.get("targetId").toString());
        String reason = (String) request.get("reason");

        ContentReport report = new ContentReport();
        report.setUserId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        contentReportRepository.save(report);

        return ResponseEntity.ok(Map.of("message", "Đã ghi nhận báo cáo nội dung"));
    }
}