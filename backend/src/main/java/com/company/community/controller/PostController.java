package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.dto.PageResponse;
import com.company.community.dto.PostCreateRequest;
import com.company.community.dto.PostListResponse;
import com.company.community.dto.PostResponse;
import com.company.community.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * POST /api/posts — 게시글 작성
     */
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PostCreateRequest request) {

        PostResponse response = postService.createPost(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/posts?page=0&size=20 — 게시글 목록 (페이지네이션 #10)
     */
    @GetMapping
    public ResponseEntity<PageResponse<PostListResponse>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(postService.getAllPosts(page, size));
    }

    /**
     * GET /api/posts/{id} — 게시글 상세 (조회수 벌크 업데이트 #4)
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPost(id));
    }

    /**
     * DELETE /api/posts/{id} — 게시글 삭제 (본인만, cascade 댓글 삭제 #5)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        postService.deletePost(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
