package com.duong.autumn.repository;

import com.duong.autumn.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    long countByFollowerId(Long followerId);

}
