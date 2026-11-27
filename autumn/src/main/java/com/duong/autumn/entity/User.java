package com.duong.autumn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@DynamicUpdate
@Table(name = "users", indexes = {
        @Index(name = "idx_users_avatar", columnList = "avatar")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "sex")
    private String sex;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "avatar")
    private String avatar;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "is_delete")
    private Integer isDelete = 0;

    @Builder.Default
    @Column(name = "token_version", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer tokenVersion = 0;

    // Chỉ giữ một chuỗi refresh đang có hiệu lực cho mỗi tài khoản.
    @JsonIgnore
    @Column(name = "refresh_token_hash", length = 64)
    private String refreshTokenHash;

    @JsonIgnore
    @Column(name = "refresh_token_family", length = 36)
    private String refreshTokenFamily;

    @JsonIgnore
    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;
}
