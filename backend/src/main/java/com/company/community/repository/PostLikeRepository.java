package com.company.community.repository;

import com.company.community.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// (#4) 게시글 좋아요 레포지토리
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    // (#4) N+1 방지 — 게시글 ID 리스트에 대한 총 반응 수를 한 번에 집계(타입 무관 합계, 피드용)
    @Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    // [FEATURE:reactions] 상세용 — 한 글의 반응 종류별 수 집계
    @Query("SELECT pl.reactionType, COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.id = :postId GROUP BY pl.reactionType")
    List<Object[]> countByPostIdGroupByType(@Param("postId") Long postId);

    // [FEATURE:reactions] 피드용 — 현재 유저가 각 글에 누른 반응 종류 (postId, ReactionType) 배치 조회
    @Query("SELECT pl.post.id, pl.reactionType FROM PostLike pl " +
           "WHERE pl.post.id IN :postIds AND pl.user.id = :userId")
    List<Object[]> findUserReactions(@Param("postIds") List<Long> postIds,
                                     @Param("userId") Long userId);
    // [/FEATURE:reactions]

    // C-NEW-1: 게시글 삭제 전 자식(반응) 정리 — FK 제약 위반 방지
    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
