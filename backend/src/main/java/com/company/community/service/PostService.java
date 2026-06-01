package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.PostLike;
import com.company.community.domain.PostView;
import com.company.community.domain.ReactionType; // [FEATURE:reactions]
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
    private final PollService pollService;                 // [FEATURE:poll] 익명 투표

    // M-NEW-5: 동일 유저의 재조회를 같은 글에 대해 이 시간 내에는 1회만 카운트.
    private static final long VIEW_DEDUP_HOURS = 24;

    // [FEATURE:unread-new] "새 글" NEW 배지를 띄울 최대 기간 — 이 기간 내 작성됐고 아직 안 연 글만 NEW.
    private static final long NEW_POST_WINDOW_DAYS = 7;

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
        // [FEATURE:poll] 작성 시 투표 보기 첨부(있으면). 응답에 갓 생성된 투표(0표) 포함.
        pollService.createOptions(saved, request.getPollOptions());
        PollResponse poll = pollService.buildResults(saved.getId(), userId);
        // [/FEATURE:poll]
        // 본인 글 isMine=true, 방금 작성 — 반응/스크랩 없음
        return PostResponse.of(saved, userId, ReactionResponse.of(List.of(), null), false, author, poll); // [FEATURE:reactions]
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

        ReactionResponse reactions = buildReactions(postId, currentUserId); // [FEATURE:reactions]
        boolean isBookmarked = bookmarkRepository.existsByPostIdAndUserId(postId, currentUserId);
        PollResponse poll = pollService.buildResults(postId, currentUserId); // [FEATURE:poll]

        return PostResponse.of(post, currentUserId, reactions, isBookmarked, post.getAuthor(), viewCount, poll);
    }

    // [FEATURE:reactions] 글의 반응 집계(종류별 수 + 내 반응) 빌드 — 상세/수정/토글 공용.
    private ReactionResponse buildReactions(Long postId, Long userId) {
        List<Object[]> typeCounts = postLikeRepository.countByPostIdGroupByType(postId);
        ReactionType myType = postLikeRepository.findByPostIdAndUserId(postId, userId)
                .map(PostLike::getReactionType)
                .orElse(null);
        return ReactionResponse.of(typeCounts, myType);
    }
    // [/FEATURE:reactions]

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
     * 게시글 목록 — 카테고리/검색/정렬/scope(all|mine|bookmarked|campus|cohort) 통합 + N+1 배치 해결
     */
    @Transactional(readOnly = true)
    public PageResponse<PostListResponse> getAllPosts(int page, int size, Long currentUserId,
                                                      PostCategory category, String keyword,
                                                      String sort, String scope) {
        Long authorId = "mine".equals(scope) ? currentUserId : null;
        Long bookmarkerId = "bookmarked".equals(scope) ? currentUserId : null;
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        // [FEATURE:cohort-campus-lounge] 라운지 scope는 현재 유저의 기수/캠퍼스로 한정(서버가 해석 — 프론트는 값 미전달).
        // 온보딩 필수값이라 ACTIVE 유저는 항상 값 보유. 라운지 scope일 때만 유저 로드.
        String cohortFilter = null;
        String campusFilter = null;
        if ("cohort".equals(scope) || "campus".equals(scope)) {
            User me = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
            cohortFilter = "cohort".equals(scope) ? me.getCohort() : null;
            campusFilter = "campus".equals(scope) ? me.getCampus() : null;
        }
        // [/FEATURE:cohort-campus-lounge]

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> resultPage = "popular".equals(sort)
                ? postRepository.findFilteredPopular(category, kw, authorId, bookmarkerId, cohortFilter, campusFilter, pageable)
                : postRepository.findFilteredLatest(category, kw, authorId, bookmarkerId, cohortFilter, campusFilter, pageable);

        List<Post> posts = resultPage.getContent();
        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        // 카운트/여부/작성자 배치 조회 — IN 절로 N+1 방지
        Map<Long, Long> commentCountMap = new HashMap<>();
        Map<Long, Long> reactionTotalMap = new HashMap<>(); // [FEATURE:reactions] 글별 총 반응 수
        Map<Long, String> myReactionMap = new HashMap<>();   // [FEATURE:reactions] 글별 내 반응 종류
        Set<Long> bookmarkedSet = new HashSet<>();
        Map<Long, User> authorMap = new HashMap<>();
        Set<Long> pollPostIds = new HashSet<>(); // [FEATURE:poll] 투표 있는 글 id
        Set<Long> viewedPostIds = new HashSet<>(); // [FEATURE:unread-new] 현재 유저가 이미 연 글 id
        if (!postIds.isEmpty()) {
            commentRepository.countByPostIds(postIds)
                    .forEach(row -> commentCountMap.put((Long) row[0], (Long) row[1]));
            // [FEATURE:reactions] 총 반응 수(타입 무관) + 내가 누른 반응 종류 배치
            postLikeRepository.countByPostIds(postIds)
                    .forEach(row -> reactionTotalMap.put((Long) row[0], (Long) row[1]));
            postLikeRepository.findUserReactions(postIds, currentUserId)
                    .forEach(row -> myReactionMap.put((Long) row[0], ((ReactionType) row[1]).name()));
            // [/FEATURE:reactions]
            bookmarkedSet.addAll(bookmarkRepository.findBookmarkedPostIds(postIds, currentUserId));
            pollPostIds.addAll(pollService.hasPollPostIds(postIds)); // [FEATURE:poll]
            viewedPostIds.addAll(postViewRepository.findViewedPostIds(postIds, currentUserId)); // [FEATURE:unread-new]

            List<Long> authorIds = posts.stream()
                    .map(p -> p.getAuthor().getId())
                    .distinct()
                    .collect(Collectors.toList());
            userRepository.findAllById(authorIds).forEach(u -> authorMap.put(u.getId(), u));
        }

        LocalDateTime newCutoff = LocalDateTime.now().minusDays(NEW_POST_WINDOW_DAYS); // [FEATURE:unread-new]
        List<PostListResponse> content = posts.stream()
                .map(post -> {
                    Long pid = post.getId();
                    long commentCount = commentCountMap.getOrDefault(pid, 0L);
                    long reactionTotal = reactionTotalMap.getOrDefault(pid, 0L); // [FEATURE:reactions]
                    String myReaction = myReactionMap.get(pid); // [FEATURE:reactions] null이면 미반응
                    boolean isBookmarked = bookmarkedSet.contains(pid);
                    User author = authorMap.get(post.getAuthor().getId());
                    // [FEATURE:unread-new] 작성자 본인 글 제외 · 미열람 + 최근이면 NEW · 연 적 있으면 읽음(isRead).
                    boolean viewed = viewedPostIds.contains(pid);
                    boolean isNew = !post.getAuthor().getId().equals(currentUserId)
                            && !viewed && post.getCreatedAt().isAfter(newCutoff);
                    return PostListResponse.of(post, commentCount, currentUserId, reactionTotal, myReaction, isBookmarked,
                            author, pollPostIds.contains(pid), isNew, viewed); // [FEATURE:reactions]·[FEATURE:poll]·[FEATURE:unread-new]
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
        pollService.deleteForPost(postId); // [FEATURE:poll] 투표 표·보기도 post_id FK → 함께 정리
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

        ReactionResponse reactions = buildReactions(postId, userId); // [FEATURE:reactions]
        boolean isBookmarked = bookmarkRepository.existsByPostIdAndUserId(postId, userId);
        PollResponse poll = pollService.buildResults(postId, userId); // [FEATURE:poll]
        return PostResponse.of(post, userId, reactions, isBookmarked, post.getAuthor(), poll);
    }

    /**
     * [FEATURE:reactions] 반응 토글 — 같은 종류 재클릭=취소, 다른 종류=변경, 처음=신규(+작성자 알림). 1인 1반응.
     * (기존 toggleLike를 대체. like는 ReactionType.LIKE로 흡수.)
     */
    @Transactional
    public ReactionResponse react(Long userId, Long postId, ReactionType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        Optional<PostLike> existing = postLikeRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            PostLike r = existing.get();
            if (r.getReactionType() == type) {
                postLikeRepository.delete(r); // 같은 종류 재클릭 → 취소
            } else {
                r.changeType(type); // 다른 종류 → 변경(dirty checking)
            }
        } else {
            try {
                postLikeRepository.save(PostLike.builder().post(post).user(user).reactionType(type).build());
                // 내 글이 아닐 때만 반응 알림 생성(신규 반응에 한함)
                if (!post.getAuthor().getId().equals(userId)) {
                    notificationService.notifyReaction(post.getAuthor(), postId, post.getTitle());
                }
            } catch (DataIntegrityViolationException e) {
                // 동시 첫 반응 경합 — 유니크 제약으로 1회만
            }
        }
        return buildReactions(postId, userId);
    }
}
