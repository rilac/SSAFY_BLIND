package com.company.community.service;

import com.company.community.domain.Comment;
import com.company.community.domain.Post;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.dto.CommentCreateRequest;
import com.company.community.dto.CommentResponse;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService; // 댓글 알림

    /**
     * 댓글 작성 — author는 서버에서만 관리 (익명)
     * (#2) isMine=true (방금 본인이 작성한 댓글)
     * 작성 후 글 작성자(본인 글 제외)에게 댓글 알림 생성
     */
    @Transactional
    public CommentResponse addComment(Long userId, Long postId, CommentCreateRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .post(post)
                .author(author)
                .build();

        Comment saved = commentRepository.save(comment);

        // 내 글이 아닐 때만 댓글 알림 생성
        if (!post.getAuthor().getId().equals(userId)) {
            notificationService.notifyComment(post.getAuthor(), postId, post.getTitle());
        }

        // (#2) 작성자 본인이므로 isMine=true. author는 작성자 본인 전달.
        return CommentResponse.of(saved, userId, author);
    }

    /**
     * 특정 게시글의 댓글 목록 조회 (오래된 순)
     * (#2) currentUserId를 받아 각 댓글의 isMine 세팅
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId, Long currentUserId) {
        if (!postRepository.existsById(postId)) {
            throw new NoSuchElementException("존재하지 않는 게시글입니다.");
        }

        List<Comment> comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);

        // 작성자 가명 정보 배치 조회 — N+1 방지
        List<Long> authorIds = comments.stream()
                .map(c -> c.getAuthor().getId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> authorMap = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> authorMap.put(u.getId(), u));

        return comments.stream()
                .map(c -> CommentResponse.of(c, currentUserId, authorMap.get(c.getAuthor().getId())))
                .collect(Collectors.toList());
    }

    /**
     * (#2) 댓글 삭제 — 본인 댓글이거나 ADMIN이면 삭제 허용
     */
    @Transactional
    public void deleteComment(Long userId, UserRole role, Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 댓글입니다."));

        // 댓글이 해당 게시글에 속하는지 검증
        if (!comment.getPost().getId().equals(postId)) {
            throw new NoSuchElementException("해당 게시글의 댓글이 아닙니다.");
        }

        // (#2) 본인 댓글이거나 ADMIN이면 삭제 허용
        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        boolean isAdmin = (role == UserRole.ADMIN);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("본인의 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.delete(comment);
    }
}
