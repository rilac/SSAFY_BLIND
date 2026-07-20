package com.company.domain.admin.repository;

import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.entity.AdminAuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// append-only — 삭제 메서드를 두지 않는다(관리자가 자기 흔적을 지울 수 없어야 감사 로그의 의미가 있다).
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    // 최신순 목록 + 선택 필터(행위자/액션). 파라미터가 null이면 해당 조건 미적용.
    @Query("SELECT l FROM AdminAuditLog l "
            + "WHERE (:actorId IS NULL OR l.actorId = :actorId) "
            + "AND (:action IS NULL OR l.action = :action) "
            + "ORDER BY l.createdAt DESC")
    Page<AdminAuditLog> findFiltered(@Param("actorId") Long actorId,
                                     @Param("action") AdminAuditAction action,
                                     Pageable pageable);
}
