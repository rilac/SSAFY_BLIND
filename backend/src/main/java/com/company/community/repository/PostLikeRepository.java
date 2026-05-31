package com.company.community.repository;

import com.company.community.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// (#4) 게시글 좋아요 레포지토리
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    // (#4) N+1 방지 — 게시글 ID 리스트에 대한 좋아요 수를 한 번에 집계
    @Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl " +
           "WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    // (#4) N+1 방지 — 현재 유저가 좋아요한 게시글 ID 목록을 한 번에 조회
    @Query("SELECT pl.post.id FROM PostLike pl " +
           "WHERE pl.post.id IN :postIds AND pl.user.id = :userId")
    List<Long> findLikedPostIds(@Param("postIds") List<Long> postIds,
                                @Param("userId") Long userId);
}
