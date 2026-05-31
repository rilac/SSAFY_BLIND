package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.PostLike;
import com.company.community.domain.PostView;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.dto.*;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.BookmarkRepository;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.NotificationRepository;
import com.company.community.repository.PostLikeRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.PostViewRepository;
import com.company.community.repository.ReportRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final ReportRepository reportRepository;       // C-NEW-1: 삭제 시 신고 정리
    private final NotificationRepository notificationRepository; // C-NEW-1: 삭제 시 알림 정리
    private final PostViewRepository postViewRepository;   // M-NEW-5: 조회수 중복 제거 이력
    private final NotificationService notificationService; // 좋아요 알림

    // M-NEW-5: 동일 유저의 재조회를 같은 글에 대해 이 시간 내에는 1회만 카운트.
    private static final long VIEW_DEDUP_HOURS = 24;

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
     * M-NEW-5: 조회수는 작성자 본인을 제외하고, 동일 유저는 24h 내 1회만 카운트한다.
     */
    @Transactional
    public PostResponse getPost(Long postId, Long currentUserId, UserRole role) {
        // 숨김 판정/작성자 판정을 위해 먼저 로드(이후 벌크 증가가 이 엔티티를 갱신하지 않도록 순서 주의)
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        // 숨김 글은 관리자가 아니면 존재하지 않는 것으로 처리(이때는 조회수도 올리지 않음)
        if (post.isHidden() && role != UserRole.ADMIN) {
            throw new NoSuchElementException("존재하지 않는 게시글입니다.");
        }

        // 카운트 대상이면 조회 이력을 기록/갱신하고 벌크 UPDATE로 원자적 증가(race condition 방지)
        boolean counted = registerViewIfCountable(post, currentUserId);
        if (counted) {
            postRepository.incrementViewCount(postId);
        }
        // 벌크 UPDATE는 관리 엔티티에 반영되지 않으므로 표시값만 +1 하여 응답에 전달
        int viewCount = post.getViewCount() + (counted ? 1 : 0);

        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, currentUserId);
        long likeCount = postLikeRepository.countByPostId(postId);
        boolean isBookmarked = bookmarkRepository.existsByPostIdAndUserId(postId, currentUserId);

        return PostResponse.of(post, currentUserId, isLiked, likeCount, isBookmarked, post.getAuthor(), viewCount);
    }

    /**
     * M-NEW-5: 이번 조회를 조회수로 집계해야 하면 조회 이력을 남기고 true.
     * - 작성자 본인 조회는 집계 제외(자기 새로고침으로 부풀려지지 않음).
     * - 동일 유저가 24h 내 이미 본 글이면 제외, 24h 경과 시 이력 갱신 후 집계.
     * - 최초 조회는 이력 생성 후 집계(동시 최초 조회는 유니크 제약으로 1회만 집계).
     */
    private boolean registerViewIfCountable(Post post, Long currentUserId) {
        if (post.getAuthor().getId().equals(currentUserId)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(VIEW_DEDUP_HOURS);

        Optional<PostView> existing = postViewRepository.findByPostIdAndUserId(post.getId(), currentUserId);
        if (existing.isPresent()) {
            PostView view = existing.get();
            if (view.getViewedAt().isAfter(cutoff)) {
                return false; // 24h 내 재조회 — 중복 제외
            }
            view.touch(now); // 24h 경과 — 이력 갱신(dirty checking) 후 집계
            return true;
        }

        User viewer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
        try {
            postViewRepository.save(PostView.builder().post(post).user(viewer).viewedAt(now).build());
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // 동시 최초 조회 경합 — 다른 요청이 집계
        }
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

        // C-NEW-1: Post는 comments만 cascade 삭제하므로, post_id를 FK로 참조하는
        // 자식(신고/좋아요/북마크/조회이력)을 먼저 정리하지 않으면 삭제 시 FK 제약 위반 → 500.
        // 관리자 삭제 대상은 대부분 신고 5건↑ 숨김 글이라, 정리 없이는 항상 실패한다.
        reportRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        bookmarkRepository.deleteByPostId(postId);
        postViewRepository.deleteByPostId(postId); // M-NEW-5: 조회 이력도 post_id FK → 함께 정리
        // 알림은 FK는 아니지만 죽은 링크가 남으므로 함께 정리(M-NEW-3).
        notificationRepository.deleteByPostId(postId);

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
