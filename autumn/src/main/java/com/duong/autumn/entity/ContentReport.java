package com.duong.autumn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "content_reports")
@Getter
@Setter
public class ContentReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // Người báo cáo

    @Column(name = "target_type", nullable = false)
    private String targetType; // "POST" hoặc "COMMENT" hoặc "USER"

    @Column(name = "target_id", nullable = false)
    private Long targetId; // ID của bài viết hoặc bình luận bị báo cáo

    @Column(name = "reason")
    private String reason; // Lý do báo cáo

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
