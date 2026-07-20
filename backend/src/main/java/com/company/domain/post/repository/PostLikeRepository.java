package com.company.domain.post.repository;

import com.company.domain.post.entity.PostLike;
import com.company.domain.post.entity.ReactionType;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
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
    @Query(value = "INSERT IGNORE INTO post_likes (post_id, user_id, reaction_type, created_at) "
            + "VALUES (:postId, :userId, :reactionType, :createdAt)", nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId,
                     @Param("reactionType") String reactionType, @Param("createdAt") LocalDateTime createdAt);
}
