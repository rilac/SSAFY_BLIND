package com.company.domain.comment.repository;

import com.company.domain.comment.entity.CommentLike;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
