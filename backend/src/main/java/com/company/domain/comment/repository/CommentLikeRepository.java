package com.company.domain.comment.repository;

import com.company.domain.comment.entity.CommentLike;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// [FEATURE:comment-likes] 댓글 좋아요 레포지토리 — 토글 + 배치 집계 + 삭제 정리.
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);

    // N+1 방지 — 댓글 ID 리스트의 좋아요 수 배치 집계
    @Query("SELECT cl.comment.id, COUNT(cl) FROM CommentLike cl " +
           "WHERE cl.comment.id IN :commentIds GROUP BY cl.comment.id")
    List<Object[]> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    // 현재 유저가 좋아요한 댓글 id (배치 isLiked 판별)
    @Query("SELECT cl.comment.id FROM CommentLike cl " +
           "WHERE cl.comment.id IN :commentIds AND cl.user.id = :userId")
    List<Long> findLikedCommentIds(@Param("commentIds") List<Long> commentIds,
                                   @Param("userId") Long userId);

    // 댓글 삭제 시 그 댓글의 좋아요 정리(FK 위반 방지)
    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);

    // 최상위 댓글 삭제 시 그 답글(1-depth)들의 좋아요 정리
    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.comment.id IN " +
           "(SELECT c.id FROM Comment c WHERE c.parentId = :parentId)")
    void deleteByCommentParentId(@Param("parentId") Long parentId);

    // 게시글 삭제 시 그 글의 모든 댓글 좋아요 정리(comment cascade 삭제 전)
    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.comment.id IN " +
           "(SELECT c.id FROM Comment c WHERE c.post.id = :postId)")
    void deleteByPostId(@Param("postId") Long postId);

    /**
     * 동시 요청 멱등 삽입 — 유니크 제약 위반 시 예외 대신 0행을 반환한다(영향 행 수: 1=삽입, 0=이미 존재).
     *
     * <p>왜 save() + catch(DataIntegrityViolationException)가 아닌가: 엔티티가 IDENTITY 전략이라 save()가
     * 즉시 INSERT를 날리고, 제약 위반이 나면 Hibernate가 트랜잭션을 rollback-only로 마킹한다. catch로
     * 예외를 삼켜도 커밋 시점에 UnexpectedRollbackException이 터져 사용자는 500을 받았다
     * (좋아요 더블클릭·두 탭 동시 조회로 재현). INSERT IGNORE는 애초에 예외를 만들지 않아 트랜잭션이
     * 오염될 여지가 구조적으로 없다.
     *
     * <p>⚠️ INSERT IGNORE는 유니크 위반뿐 아니라 FK·NOT NULL 위반까지 경고로 강등해 조용히 0행이 된다.
     * 호출부의 존재 확인 가드를 제거하지 말 것(제거하면 무증상 데이터 유실이 된다).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT IGNORE INTO comment_likes (comment_id, user_id, created_at) "
            + "VALUES (:commentId, :userId, :createdAt)", nativeQuery = true)
    int insertIgnore(@Param("commentId") Long commentId, @Param("userId") Long userId,
                     @Param("createdAt") LocalDateTime createdAt);
}
