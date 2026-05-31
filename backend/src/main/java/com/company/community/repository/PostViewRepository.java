package com.company.community.repository;

import com.company.community.domain.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// M-NEW-5: 조회 이력 — 유저·게시글 단위 최근 조회 시각 조회/정리
public interface PostViewRepository extends JpaRepository<PostView, Long> {

    Optional<PostView> findByPostIdAndUserId(Long postId, Long userId);

    // 게시글 삭제 전 자식(조회 이력) 정리 — post_id FK 제약 위반 방지(C-NEW-1과 동일 패턴)
    @Modifying
    @Query("DELETE FROM PostView pv WHERE pv.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
