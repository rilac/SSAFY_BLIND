package com.company.domain.comment.controller;

import com.company.domain.comment.controller.dto.AcceptAnswerResponse;
import com.company.domain.comment.controller.dto.CommentCreateRequest;
import com.company.domain.comment.controller.dto.CommentLikeResponse;
import com.company.domain.comment.controller.dto.CommentResponse;
import com.company.domain.comment.service.CommentService;
import com.company.domain.user.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * POST /api/posts/{postId}/comments — 댓글 작성
     */
    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {

        CommentResponse response = commentService.addComment(user.getId(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/posts/{postId}/comments — 댓글 목록
     * (#2) @AuthenticationPrincipal 추가 — isMine 판별을 위해 currentUserId 전달
     */
    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId) {

        // 게스트(user == null)도 댓글 조회 가능 — userId null 전달
        return ResponseEntity.ok(commentService.getComments(postId, user != null ? user.getId() : null));
    }

    /**
     * DELETE /api/posts/{postId}/comments/{commentId} — 댓글 삭제 (#2)
     * 본인 댓글이거나 ADMIN이면 삭제 허용
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @PathVariable Long commentId) {

        // (#1)(#2) role을 서비스에 전달하여 ADMIN 여부 체크
        commentService.deleteComment(user.getId(), user.getRole(), postId, commentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [FEATURE:qna-accept] POST /api/posts/{postId}/comments/{commentId}/accept
     * 답변 채택 토글 — QUESTION 글의 작성자만. 같은 답변 재요청 시 채택 해제.
     */
    @PostMapping("/{commentId}/accept")
    public ResponseEntity<AcceptAnswerResponse> acceptAnswer(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @PathVariable Long commentId) {

        return ResponseEntity.ok(commentService.toggleAcceptAnswer(user.getId(), postId, commentId));
    }
    // [/FEATURE:qna-accept]

    /**
     * [FEATURE:comment-likes] POST /api/posts/{postId}/comments/{commentId}/like — 좋아요 토글.
     * 응답 { "liked": bool, "likeCount": n }.
     */
    @PostMapping("/{commentId}/like")
    public ResponseEntity<CommentLikeResponse> likeComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @PathVariable Long commentId) {

        return ResponseEntity.ok(commentService.toggleLike(user.getId(), postId, commentId));
    }
    // [/FEATURE:comment-likes]
}
