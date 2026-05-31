package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.PostLike;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.dto.*;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.BookmarkRepository;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.PostLikeRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository; // (#4)
    private final CommentRepository commentRepository;   // 댓글 수 배치 집계
    private final BookmarkRepository bookmarkRepository;  // 스크랩
    private final NotificationService notificationService; // 좋아요 알림

    /**
     * 게시글 작성 — author는 서버에서만 관리, 응답에는 노출하지 않음
     */
    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .category(request.getCategory())
                .author(author)
                .build();

        Post saved = postRepository.save(post);
        // 본인 글 isMine=true, 방금 작성 — isLiked/isBookmarked=false, likeCount=0
        return PostResponse.of(saved, userId, false, 0L, false, author);
    }

    /**
     * 게시글 상세 조회 — isMine/isLiked/likeCount/isBookmarked/author 세팅.
     * 숨김(신고 누적) 글은 ADMIN만 열람 가능.
     */
    @Transactional
    public PostResponse getPost(Long postId, Long currentUserId, UserRole role) {
        // 벌크 UPDATE로 조회수 증가 — race condition 방지
        postRepository.incrementViewCount(postId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        // 숨김 글은 관리자가 아니면 존재하지 않는 것으로 처리
        if (post.isHidden() && role != UserRole.ADMIN) {
            throw new NoSuchElementException("존재하지 않는 게시글입니다.");
        }

        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, currentUserId);
        long likeCount = postLikeRepository.countByPostId(postId);
        boolean isBookmarked = bookmarkRepository.existsByPostIdAndUserId(postId, currentUserId);

        return PostResponse.of(post, currentUserId, isLiked, likeCount, isBookmarked, post.getAuthor());
    }

    /**
     * 게시글 목록 — 카테고리/검색/정렬/scope(all|mine|bookmarked) 통합 + N+1 배치 해결
     */
    @Transactional(readOnly = true)
    public PageResponse<PostListResponse> getAllPosts(int page, int size, Long currentUserId,
                                                      PostCategory category, String keyword,
                                                      String sort, String scope) {
        Long authorId = "mine".equals(scope) ? currentUserId : null;
        Long bookmarkerId = "bookmarked".equals(scope) ? currentUserId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> resultPage = "popular".equals(sort)
                ? postRepository.findFilteredPopular(category, kw, authorId, bookmarkerId, pageable)
                : postRepository.findFilteredLatest(category, kw, authorId, bookmarkerId, pageable);

        List<Post> posts = resultPage.getContent();
        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        // 카운트/여부/작성자 배치 조회 — IN 절로 N+1 방지
        Map<Long, Long> commentCountMap = new HashMap<>();
        Map<Long, Long> likeCountMap = new HashMap<>();
        Set<Long> likedSet = new HashSet<>();
        Set<Long> bookmarkedSet = new HashSet<>();
        Map<Long, User> authorMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            commentRepository.countByPostIds(postIds)
                    .forEach(row -> commentCountMap.put((Long) row[0], (Long) row[1]));
            postLikeRepository.countByPostIds(postIds)
                    .forEach(row -> likeCountMap.put((Long) row[0], (Long) row[1]));
            likedSet.addAll(postLikeRepository.findLikedPostIds(postIds, currentUserId));
            bookmarkedSet.addAll(bookmarkRepository.findBookmarkedPostIds(postIds, currentUserId));

            List<Long> authorIds = posts.stream()
                    .map(p -> p.getAuthor().getId())
                    .distinct()
                    .collect(Collectors.toList());
            userRepository.findAllById(authorIds).forEach(u -> authorMap.put(u.getId(), u));
        }

        List<PostListResponse> content = posts.stream()
                .map(post -> {
                    Long pid = post.getId();
                    long commentCount = commentCountMap.getOrDefault(pid, 0L);
                    long likeCount = likeCountMap.getOrDefault(pid, 0L);
                    boolean isLiked = likedSet.contains(pid);
                    boolean isBookmarked = bookmarkedSet.contains(pid);
                    User author = authorMap.get(post.getAuthor().getId());
                    return PostListResponse.of(post, commentCount, currentUserId, isLiked, likeCount, isBookmarked, author);
                })
                .collect(Collectors.toList());

        return PageResponse.of(resultPage, content);
    }

    /**
     * 게시글 삭제 — 본인 또는 ADMIN
     */
    @Transactional
    public void deletePost(Long userId, Long postId, UserRole role) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        boolean isAuthor = post.getAuthor().getId().equals(userId);
        boolean isAdmin = (role == UserRole.ADMIN);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("본인의 글만 삭제할 수 있습니다.");
        }

        postRepository.delete(post);
    }

    /**
     * (#3) 게시글 수정 — 본인만 가능 (ADMIN도 타인 글 수정 불가)
     */
    @Transactional
    public PostResponse updatePost(Long userId, Long postId, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("본인의 글만 수정할 수 있습니다.");
        }

        // 도메인 메서드로 변경 — @Setter 사용 금지
        post.update(request.getTitle(), request.getContent(), request.getCategory());

        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId);
        long likeCount = postLikeRepository.countByPostId(postId);
        boolean isBookmarked = bookmarkRepository.existsByPostIdAndUserId(postId, userId);
        return PostResponse.of(post, userId, isLiked, likeCount, isBookmarked, post.getAuthor());
    }

    /**
     * (#4) 좋아요 토글 + 좋아요 시 글 작성자에게 알림
     */
    @Transactional
    public PostLikeResponse toggleLike(Long userId, Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            // 이미 좋아요 → 취소
            postLikeRepository.delete(existing.get());
            long likeCount = postLikeRepository.countByPostId(postId);
            return new PostLikeResponse(false, likeCount);
        }

        try {
            postLikeRepository.save(PostLike.builder().post(post).user(user).build());
            // 내 글이 아닐 때만 좋아요 알림 생성
            if (!post.getAuthor().getId().equals(userId)) {
                notificationService.notifyLike(post.getAuthor(), postId, post.getTitle());
            }
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 유니크 위반 — 이미 좋아요 상태로 간주
            long likeCount = postLikeRepository.countByPostId(postId);
            return new PostLikeResponse(true, likeCount);
        }

        long likeCount = postLikeRepository.countByPostId(postId);
        return new PostLikeResponse(true, likeCount);
    }
}
