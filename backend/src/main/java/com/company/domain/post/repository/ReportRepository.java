package com.company.domain.post.repository;

import com.company.domain.post.entity.Report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 중복 신고 방지 (멱등 처리)
    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);

    // 누적 신고 수 — 자동 숨김 임계
    long countByPostId(Long postId);

    // 관리자 검수: 사유 집계 / 신고된 게시글 목록 산출
    List<Report> findByPostId(Long postId);

    List<Report> findAllByOrderByCreatedAtDesc();

    // C-NEW-1: 게시글 삭제 전 자식(신고) 정리 — FK 제약 위반 방지
    @Modifying
    @Query("DELETE FROM Report r WHERE r.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    // [FEATURE:report-dashboard] 사유별 신고 집계 — (reason, count). reason은 nullable(레거시)이라 호출측에서 null 가드.
    @Query("SELECT r.reason, COUNT(r) FROM Report r GROUP BY r.reason")
    List<Object[]> countByReason();

    // [FEATURE:report-dashboard] 일별 추이용 — since 이후 신고 시각만 가볍게 조회(엔티티 대신 스칼라).
    @Query("SELECT r.createdAt FROM Report r WHERE r.createdAt >= :since")
    List<LocalDateTime> findCreatedAtSince(@Param("since") LocalDateTime since);

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
    @Query(value = "INSERT IGNORE INTO reports (post_id, reporter_id, reason, detail, created_at) "
            + "VALUES (:postId, :reporterId, :reason, :detail, :createdAt)", nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("reporterId") Long reporterId,
                     @Param("reason") String reason, @Param("detail") String detail,
                     @Param("createdAt") LocalDateTime createdAt);
}
