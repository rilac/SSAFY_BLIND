package com.company.domain.post.controller;

import com.company.domain.post.controller.dto.BookmarkResponse;
import com.company.domain.post.controller.dto.PostCreateRequest;
import com.company.domain.post.controller.dto.PostListResponse;
import com.company.domain.post.controller.dto.PostResponse;
import com.company.domain.post.controller.dto.PostUpdateRequest;
import com.company.domain.post.controller.dto.ReactionRequest;
import com.company.domain.post.controller.dto.ReactionResponse;
import com.company.domain.post.controller.dto.ReportRequest;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.service.BookmarkService;
import com.company.domain.post.service.PostService;
import com.company.domain.post.service.ReportService;
import com.company.domain.user.entity.User;
import com.company.global.dto.PageResponse;

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
    private final BookmarkService bookmarkService;
    private final ReportService reportService;

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
     * GET /api/posts — 게시글 목록 (카테고리/검색/정렬/scope 통합)
     *  category: 카테고리 필터(없으면 전체) / keyword: 제목·본문 검색
     *  sort: latest|popular / scope: all|mine|bookmarked
     */
    @GetMapping
    public ResponseEntity<PageResponse<PostListResponse>> getAllPosts(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "all") String scope) {

        // 게스트(user == null)는 공개 읽기 — userId/role을 null로 전달
        return ResponseEntity.ok(postService.getAllPosts(page, size,
                user != null ? user.getId() : null, category, keyword, sort, scope,
                user != null ? user.getRole() : null));
    }

    /**
     * GET /api/posts/{id} — 게시글 상세
     * (#3)(#4) currentUserId 전달
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        // role 전달 — 숨김 글은 ADMIN만 열람. 게스트(user == null)는 null 전달(숨김글은 404).
        return ResponseEntity.ok(postService.getPost(id,
                user != null ? user.getId() : null,
                user != null ? user.getRole() : null));
    }

    /**
     * PUT /api/posts/{id} — 게시글 수정 (#3)
     * 본인만 수정 가능
     */
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateRequest request) {

        // 본인 또는 ADMIN이면 수정 가능(예: 관리자가 카테고리 교정) — role 전달
        return ResponseEntity.ok(postService.updatePost(user.getId(), id, request, user.getRole()));
    }

    /**
     * DELETE /api/posts/{id} — 게시글 삭제
     * (#1) 본인 또는 ADMIN이면 삭제 가능 — role을 서비스에 전달
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        postService.deletePost(user.getId(), id, user.getRole());
        return ResponseEntity.noContent().build();
    }

    /**
     * [FEATURE:reactions] POST /api/posts/{id}/reactions — 반응 토글(좋아요/도움돼요/정보/공감).
     * 같은 종류 재요청=취소, 다른 종류=변경. (기존 /{id}/like 대체)
     */
    @PostMapping("/{id}/reactions")
    public ResponseEntity<ReactionResponse> react(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ReactionRequest request) {

        return ResponseEntity.ok(postService.react(user.getId(), id, request.getType()));
    }

    /**
     * POST /api/posts/{id}/bookmark — 스크랩 토글
     */
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<BookmarkResponse> toggleBookmark(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        return ResponseEntity.ok(bookmarkService.toggle(user.getId(), id));
    }

    /**
     * POST /api/posts/{id}/report — 게시글 신고 (멱등)
     */
    @PostMapping("/{id}/report")
    public ResponseEntity<Void> report(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ReportRequest request) {

        reportService.report(user.getId(), id, request.getReason(), request.getDetail());
        return ResponseEntity.ok().build();
    }
}
