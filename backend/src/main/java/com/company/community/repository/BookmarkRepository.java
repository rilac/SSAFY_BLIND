package com.company.community.repository;

import com.company.community.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    // N+1 방지 — 목록에서 현재 유저가 스크랩한 게시글 ID를 한 번에 조회
    @Query("SELECT b.post.id FROM Bookmark b " +
           "WHERE b.post.id IN :postIds AND b.user.id = :userId")
    List<Long> findBookmarkedPostIds(@Param("postIds") List<Long> postIds,
                                     @Param("userId") Long userId);

    // C-NEW-1: 게시글 삭제 전 자식(스크랩) 정리 — FK 제약 위반 방지
    @Modifying
    @Query("DELETE FROM Bookmark b WHERE b.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
