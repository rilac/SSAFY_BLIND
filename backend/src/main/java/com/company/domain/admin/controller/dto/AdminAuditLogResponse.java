package com.company.domain.admin.controller.dto;

import com.company.domain.admin.entity.AdminAuditLog;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// 감사 로그 목록 응답. 관리자에게만 노출(/api/admin/** = ROLE_ADMIN + step-up).
@Getter
@AllArgsConstructor
public class AdminAuditLogResponse {

    private Long id;
    private Long actorId;
    private String actorNickname; // 행위자 식별용 — 조회 편의(관리자는 소수)
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;

    public static AdminAuditLogResponse of(AdminAuditLog log, String actorNickname) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getActorId(),
                actorNickname,
                log.getAction().name(),
                log.getTargetType().name(),
                log.getTargetId(),
                log.getDetail(),
                log.getIp(),
                log.getCreatedAt());
    }
}
