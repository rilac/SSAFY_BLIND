package com.company.domain.comment.service;

import com.company.domain.comment.controller.dto.AcceptAnswerResponse;
import com.company.domain.comment.controller.dto.CommentCreateRequest;
import com.company.domain.comment.controller.dto.CommentLikeResponse;
import com.company.domain.comment.controller.dto.CommentResponse;
import com.company.domain.comment.entity.Comment;
import com.company.domain.comment.entity.CommentLike;
import com.company.domain.comment.repository.CommentLikeRepository;
import com.company.domain.comment.repository.CommentRepository;
import com.company.domain.notification.service.NotificationService;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.ForbiddenException;
import com.company.global.exception.InvalidStateException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository; // [FEATURE:comment-likes]
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService; // 댓글 알림

    /**
     * 댓글 작성 — author는 서버에서만 관리 (익명)
     * (#2) isMine=true (방금 본인이 작성한 댓글)
     * 작성 후 글 작성자(본인 글 제외)에게 댓글 알림 생성
     */
    @Transactional
    public CommentResponse addComment(Long userId, Long postId, CommentCreateRequest request, UserRole role) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        // [FEATURE:hidden-author-visibility] 숨김 글에는 댓글 작성 불가 — 글을 볼 수 없는 작성자에게 알림이 가고,
        // 복원 시 검수 기간에 생긴 댓글이 되살아난다.
        post.assertWritable(role, userId);

        // [FEATURE:nested-comments] 답글이면 부모 검증: 존재·동일 글·1-depth(부모가 최상위여야 함).
        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 댓글입니다."));
            if (!parent.getPost().getId().equals(postId)) {
                throw new NoSuchElementException("해당 게시글의 댓글이 아닙니다.");
            }
            if (parent.getParentId() != null) {
                throw new InvalidStateException("답글에는 다시 답글을 달 수 없습니다.");
            }
        }
        // [/FEATURE:nested-comments]

        Comment comment = Comment.builder()
                .content(request.getContent())
                .post(post)
                .author(author)
                .parentId(request.getParentId()) // [FEATURE:nested-comments]
                .build();

        Comment saved = commentRepository.save(comment);

        // [FEATURE:nested-comments] 답글이면 부모 댓글 작성자에게 알림, 아니면 글 작성자에게(본인 제외)
        if (parent != null) {
            if (!parent.getAuthor().getId().equals(userId)) {
                notificationService.notifyReply(parent.getAuthor(), postId);
            }
        } else // [/FEATURE:nested-comments]
        // 내 글이 아닐 때만 댓글 알림 생성
        if (!post.getAuthor().getId().equals(userId)) {
            notificationService.notifyComment(post.getAuthor(), postId, post.getTitle());
        }

        // (#2) 작성자 본인이므로 isMine=true. author는 작성자 본인 전달.
        // [FEATURE:op-alias] 새 댓글의 글 단위 별칭/글쓴이 여부 계산 — 프론트가 즉시(낙관적 append) 표시할 수 있도록.
        Long postAuthorId = post.getAuthor().getId();
        Map<Long, String> aliasByUser =
                buildAliasMap(postAuthorId, commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId));
        // [FEATURE:comment-likes] 새 댓글은 좋아요 0·미좋아요
        return CommentResponse.of(saved, userId, author, aliasByUser.get(userId), userId.equals(postAuthorId), 0L, false);
        // [/FEATURE:op-alias]
    }

    /**
     * 특정 게시글의 댓글 목록 조회 (오래된 순)
     * (#2) currentUserId를 받아 각 댓글의 isMine 세팅
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId, Long currentUserId, UserRole role) {
        // [FEATURE:op-alias] 글쓴이(OP) 식별을 위해 글 작성자 id가 필요 → existsById 대신 findById로 로드.
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        // [FEATURE:hidden-author-visibility] 상세 조회와 동일한 게이트 — 숨김 글의 댓글이 제3자에게 새면
        // 모더레이션이 무력화된다(기존엔 GET /posts/{id}는 404인데 댓글은 200이었다).
        post.assertVisibleTo(role, currentUserId);
        Long postAuthorId = post.getAuthor().getId();
        // [/FEATURE:op-alias]

        List<Comment> comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);

        // 작성자 가명 정보 배치 조회 — N+1 방지
        List<Long> authorIds = comments.stream()
                .map(c -> c.getAuthor().getId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> authorMap = new HashMap<>();
        userRepository.findAllById(authorIds).forEach(u -> authorMap.put(u.getId(), u));

        // [FEATURE:op-alias] 글 단위 일관 별칭 맵(글쓴이/익명N) — 댓글마다 동일 유저는 동일 별칭.
        Map<Long, String> aliasByUser = buildAliasMap(postAuthorId, comments);

        // [FEATURE:comment-likes] 좋아요 수 + 내 좋아요 여부 배치 조회(N+1 방지)
        List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
        Map<Long, Long> likeCountMap = new HashMap<>();
        Set<Long> likedSet = new HashSet<>();
        if (!commentIds.isEmpty()) {
            commentLikeRepository.countByCommentIds(commentIds)
                    .forEach(r -> likeCountMap.put((Long) r[0], (Long) r[1]));
            // 내 좋아요 여부는 로그인 유저만 — 게스트(null)는 빈 집합.
            if (currentUserId != null) {
                likedSet.addAll(commentLikeRepository.findLikedCommentIds(commentIds, currentUserId));
            }
        }
        // [/FEATURE:comment-likes]

        return comments.stream()
                .map(c -> {
                    Long uid = c.getAuthor().getId();
                    return CommentResponse.of(c, currentUserId, authorMap.get(uid),
                            aliasByUser.get(uid), uid.equals(postAuthorId),
                            likeCountMap.getOrDefault(c.getId(), 0L), likedSet.contains(c.getId())); // [FEATURE:comment-likes]
                })
                .collect(Collectors.toList());
        // [/FEATURE:op-alias]
    }

    // [FEATURE:op-alias] 글 단위 일관 익명 별칭 계산.
    // 글쓴이(OP) → "글쓴이". 그 외 작성자는 첫 등장(오래된 댓글) 순으로 "익명1","익명2"… 부여하고,
    // 같은 유저는 글 내내 같은 별칭을 유지한다. orderedComments는 createdAt 오름차순이어야 번호가 결정적이다.
    private static final String OP_ALIAS = "글쓴이";

    private Map<Long, String> buildAliasMap(Long postAuthorId, List<Comment> orderedComments) {
        Map<Long, String> aliasByUser = new HashMap<>();
        aliasByUser.put(postAuthorId, OP_ALIAS); // 글쓴이는 익명 번호 대신 항상 "글쓴이"
        int counter = 0;
        for (Comment c : orderedComments) {
            Long uid = c.getAuthor().getId();
            if (!aliasByUser.containsKey(uid)) {
                aliasByUser.put(uid, "익명" + (++counter));
            }
        }
        return aliasByUser;
    }
    // [/FEATURE:op-alias]

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

        // [FEATURE:qna-accept] 채택된 답변이 삭제되면 글의 채택 상태를 정리(댕글링 방지)
        Post post = comment.getPost();
        if (commentId.equals(post.getAcceptedCommentId())) {
            post.clearAcceptedAnswer();
        }
        // [/FEATURE:qna-accept]

        // [FEATURE:comment-likes] 좋아요(comment_id FK)를 댓글 삭제 전에 정리 — 답글들의 좋아요 + 이 댓글의 좋아요.
        commentLikeRepository.deleteByCommentParentId(commentId);
        commentLikeRepository.deleteByCommentId(commentId);
        // [/FEATURE:comment-likes]

        // [FEATURE:nested-comments] 최상위 댓글 삭제 시 그 답글도 함께 정리(1-depth). 답글이면 자식이 없어 no-op.
        commentRepository.deleteByParentId(commentId);
        // [/FEATURE:nested-comments]

        commentRepository.delete(comment);
    }

    // [FEATURE:comment-likes] 댓글 좋아요 토글 — 처음=추가, 재요청=취소. (comment_id,user_id) 유니크로 1인 1좋아요.
    @Transactional
    public CommentLikeResponse toggleLike(Long userId, Long postId, Long commentId, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 댓글입니다."));
        if (!comment.getPost().getId().equals(postId)) {
            throw new NoSuchElementException("해당 게시글의 댓글이 아닙니다.");
        }
        // [FEATURE:hidden-author-visibility] 이미 로드된 연관 Post 재사용(추가 조회 없음).
        comment.getPost().assertWritable(role, userId);

        Optional<CommentLike> existing = commentLikeRepository.findByCommentIdAndUserId(commentId, userId);
        boolean liked;
        if (existing.isPresent()) {
            commentLikeRepository.delete(existing.get());
            liked = false;
        } else {
            // 멱등 삽입 — 경합이어도 최종 상태는 "좋아요됨"으로 동일. 예외가 없어 트랜잭션이 오염되지 않는다.
            commentLikeRepository.insertIgnore(commentId, userId, LocalDateTime.now());
            liked = true;
        }

        long count = commentLikeRepository.countByCommentIds(List.of(commentId)).stream()
                .findFirst().map(r -> (Long) r[1]).orElse(0L);
        return new CommentLikeResponse(liked, count);
    }
    // [/FEATURE:comment-likes]

    // [FEATURE:qna-accept] 답변 채택 토글 — QUESTION 글 + 질문 작성자만. 같은 답변 재요청 시 해제.
    @Transactional
    public AcceptAnswerResponse toggleAcceptAnswer(Long userId, Long postId, Long commentId, UserRole role) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        // [FEATURE:hidden-author-visibility] 숨김 글에서는 채택 상태를 바꿀 수 없다.
        post.assertWritable(role, userId);

        if (post.getCategory() != PostCategory.QUESTION) {
            throw new InvalidStateException("질문 글에서만 답변을 채택할 수 있습니다.");
        }
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("질문 작성자만 답변을 채택할 수 있습니다.");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 댓글입니다."));
        if (!comment.getPost().getId().equals(postId)) {
            throw new NoSuchElementException("해당 게시글의 댓글이 아닙니다.");
        }
        // [FEATURE:nested-comments] 답글(대댓글)은 채택 대상이 아님 — 최상위 답변만 채택 가능.
        if (comment.getParentId() != null) {
            throw new InvalidStateException("답글은 채택할 수 없습니다.");
        }
        // [/FEATURE:nested-comments]

        // 토글: 이미 채택된 답변이면 해제, 아니면 채택(교체)
        if (commentId.equals(post.getAcceptedCommentId())) {
            post.clearAcceptedAnswer();
        } else {
            post.acceptAnswer(commentId);
        }
        return AcceptAnswerResponse.from(post.getAcceptedCommentId());
    }
    // [/FEATURE:qna-accept]
}
