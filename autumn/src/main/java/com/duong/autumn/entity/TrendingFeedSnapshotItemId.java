package com.duong.autumn.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrendingFeedSnapshotItemId implements Serializable {
    private static final long serialVersionUID = 1L;

    private String snapshotId;
    private Long postId;
}

