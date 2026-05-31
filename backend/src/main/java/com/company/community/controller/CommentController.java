package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.dto.CommentCreateRequest;
import com.company.community.dto.CommentResponse;
import com.company.community.service.CommentService;
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

        return ResponseEntity.ok(commentService.getComments(postId, user.getId()));
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
}
