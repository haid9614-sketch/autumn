package com.duong.autumn.service;

import com.duong.autumn.dto.request.CreateCommentRequest;
import com.duong.autumn.dto.response.CommentResponse;
import com.duong.autumn.dto.response.MessageResponse;
import com.duong.autumn.entity.Comment;
import com.duong.autumn.entity.Post;
import com.duong.autumn.entity.User;
import com.duong.autumn.repository.CommentRepository;
import com.duong.autumn.repository.PostRepository;
import com.duong.autumn.repository.UserRepository;
import com.duong.autumn.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập");
        }
        return userDetails.getId();
    }

    // 1. Tạo bình luận mới (Hỗ trợ cả bình luận gốc và trả lời bình luận)
    @Transactional
    public CommentResponse createComment(Long postId, CreateCommentRequest request) {
        Long currentUserId = getCurrentUserId();

        User currentUser = userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không hợp lệ"));

        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết này"));

        Comment parentComment = null;
        if (request.getParentId() != null) {
            Comment targetParent = commentRepository.findActiveById(request.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bình luận bạn đang trả lời không tồn tại hoặc đã bị xóa"));

            // Chặn lỗ hổng truyền parentId của bài viết khác vào bài viết này
            if (!targetParent.getPost().getId().equals(postId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bình luận cha không thuộc bài viết này");
            }

            // Làm phẳng thành 2 cấp: Nếu trả lời một comment cấp 2 thì gom về comment gốc cấp 1
            parentComment = targetParent.getParent() != null ? targetParent.getParent() : targetParent;
        }

        Comment savedComment = commentRepository.save(Comment.builder()
                .content(request.getContent().trim())
                .user(currentUser)
                .post(post)
                .parent(parentComment)
                .isDelete(0)
                .build());

        return toCommentResponse(savedComment, currentUserId, post.getUser().getId(), Collections.emptyList());
    }

    // 2. Lấy toàn bộ danh sách bình luận của bài viết (Kèm danh sách trả lời)
    @Transactional(readOnly = true)
    public List<CommentResponse> getPostComments(Long postId) {
        Long currentUserId = getCurrentUserId();

        Post post = postRepository.findActiveById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết này"));

        Long postOwnerId = post.getUser().getId();
        List<Comment> rootComments = commentRepository.findActiveRootCommentsByPostId(postId);

        if (rootComments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> rootIds = rootComments.stream().map(Comment::getId).toList();
        List<Comment> allReplies = commentRepository.findActiveRepliesByParentIds(rootIds);

        // Gom nhóm các câu trả lời theo ID của bình luận cha
        Map<Long, List<CommentResponse>> repliesByParentId = allReplies.stream()
                .map(reply -> toCommentResponse(reply, currentUserId, postOwnerId, Collections.emptyList()))
                .collect(Collectors.groupingBy(CommentResponse::getParentId));

        return rootComments.stream()
                .map(root -> toCommentResponse(
                        root,
                        currentUserId,
                        postOwnerId,
                        repliesByParentId.getOrDefault(root.getId(), Collections.emptyList())
                ))
                .toList();
    }

    // 3. Xóa bình luận (Cho phép người viết bình luận HOẶC chủ bài viết được quyền xóa)
    @Transactional
    public MessageResponse deleteComment(Long postId, Long commentId) {
        Long currentUserId = getCurrentUserId();

        Comment comment = commentRepository.findActiveById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận này"));

        if (!comment.getPost().getId().equals(postId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bình luận không thuộc bài viết này");
        }

        boolean isCommentAuthor = comment.getUser().getId().equals(currentUserId);
        boolean isPostOwner = comment.getPost().getUser().getId().equals(currentUserId);

        if (!isCommentAuthor && !isPostOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa bình luận này");
        }

        comment.setIsDelete(1);
        commentRepository.save(comment);

        // Nếu xóa bình luận gốc cấp 1, xóa mềm luôn các câu trả lời con
        if (comment.getParent() == null) {
            commentRepository.softDeleteRepliesByParentId(commentId);
        }

        return new MessageResponse("Đã xóa bình luận thành công");
    }

    private CommentResponse toCommentResponse(Comment comment,
                                              Long currentUserId,
                                              Long postOwnerId,
                                              List<CommentResponse> replies) {
        User author = comment.getUser();
        boolean canDelete = author.getId().equals(currentUserId) || postOwnerId.equals(currentUserId);

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .authorId(author.getId())
                .authorName(author.getName())
                .authorUsername(author.getUsername())
                .authorAvatar(author.getAvatar())
                .canDelete(canDelete)
                .replies(replies)
                .build();
    }
}
