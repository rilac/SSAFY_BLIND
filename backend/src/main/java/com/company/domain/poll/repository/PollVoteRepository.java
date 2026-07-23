package com.company.domain.poll.repository;

import com.company.domain.poll.entity.PollVote;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// [FEATURE:poll] 투표 표 레포지토리
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    // 1인 1표 — 현재 유저의 표(있으면 변경/취소 판단)
    Optional<PollVote> findByPostIdAndUserId(Long postId, Long userId);

    // 보기별 표 수 집계 (익명 — 누가 골랐는지는 노출 안 함)
    @Query("SELECT v.optionId, COUNT(v) FROM PollVote v WHERE v.post.id = :postId GROUP BY v.optionId")
    List<Object[]> countByPostIdGroupByOption(@Param("postId") Long postId);

    // 게시글 삭제 전 정리(post_id FK)
    @Modifying
    @Query("DELETE FROM PollVote v WHERE v.post.id = :postId")
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
     *
     * <p>⚠️ clearAutomatically를 켜지 말 것 — 삽입 행은 사전 존재 확인 때문에 영속성 컨텍스트에 있을 수
     * 없어 비울 것이 없고, 켜면 호출부가 들고 있던 엔티티가 전부 detach된다(2026-07 운영 장애: 타인 글
     * 첫 조회마다 author 프록시 LazyInitializationException → 500, 신고 자동 숨김 dirty checking 유실).
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO poll_votes (post_id, user_id, option_id, created_at) "
            + "VALUES (:postId, :userId, :optionId, :createdAt)", nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId,
                     @Param("optionId") Long optionId, @Param("createdAt") LocalDateTime createdAt);
}
