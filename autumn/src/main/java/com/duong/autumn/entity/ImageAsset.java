package com.duong.autumn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_assets", indexes = {
        @Index(name = "idx_image_asset_owner_state", columnList = "owner_id, state")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_image_asset_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uk_image_asset_url", columnNames = "image_url")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;

    @Column(name = "folder", nullable = false, length = 32)
    private String folder;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16, columnDefinition = "varchar(16)")
    @Builder.Default
    private ImageAssetState state = ImageAssetState.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

