package com.company.community.repository;

import com.company.community.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

// 🗓️ 2026-06-02: Refresh Token 저장소 — 해시 조회 / 유저별 일괄 폐기 / 만료 정리
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 로그아웃(전체)/탈퇴/휴면 — 해당 유저의 모든 RT 일괄 폐기
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // 만료 정리 스케줄러 — 자연 만료된 RT 벌크 삭제(삭제 건수 반환)
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now);
}
