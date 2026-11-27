package com.duong.autumn.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_blocks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_blocks_pair", columnNames = {"user_id", "blocked_user_id"})
        },
        indexes = {
                @Index(name = "idx_user_blocks_blocked_user", columnList = "blocked_user_id, user_id")
        }
)
@Getter
@Setter
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "blocked_user_id", nullable = false)
    private Long blockedUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}