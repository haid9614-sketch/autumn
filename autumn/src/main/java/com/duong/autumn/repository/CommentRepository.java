package com.duong.autumn.repository;

import com.duong.autumn.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Tìm bình luận đang hoạt động (kiểm tra cả comment, người viết và bài viết đều chưa bị xóa)
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.user u
            JOIN FETCH c.post p
            JOIN FETCH p.user pu
            WHERE c.id = :commentId
              AND c.isDelete = 0
              AND u.isDelete = 0
              AND p.isDelete = 0
              AND pu.isDelete = 0
            """)
    Optional<Comment> findActiveById(@Param("commentId") Long commentId);

    // Lấy toàn bộ bình luận gốc (cấp 1) của bài viết - mới nhất lên đầu
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.user u
            WHERE c.post.id = :postId
              AND c.parent IS NULL
              AND c.isDelete = 0
              AND u.isDelete = 0
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Comment> findActiveRootCommentsByPostId(@Param("postId") Long postId);

    // Batch Query lấy toàn bộ câu trả lời (cấp 2) của các bình luận gốc - sắp xếp cũ trước mới sau theo mạch hội thoại
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.user u
            WHERE c.parent.id IN :parentIds
              AND c.isDelete = 0
              AND u.isDelete = 0
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<Comment> findActiveRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    // Xóa mềm toàn bộ các câu trả lời con khi bình luận cha bị xóa
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Comment c
            SET c.isDelete = 1
            WHERE c.parent.id = :parentId AND c.isDelete = 0
            """)
    int softDeleteRepliesByParentId(@Param("parentId") Long parentId);
}