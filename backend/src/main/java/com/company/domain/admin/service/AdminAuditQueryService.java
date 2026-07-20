package com.company.domain.admin.service;

import com.company.domain.admin.controller.dto.AdminAuditLogResponse;
import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.entity.AdminAuditLog;
import com.company.domain.admin.repository.AdminAuditLogRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 감사 로그 조회 — 기록(AdminAuditService)과 분리한 이유는 기록이 REQUIRES_NEW라
 * 조회까지 새 트랜잭션을 열 필요가 없기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> list(Long actorId, AdminAuditAction action, Pageable pageable) {
        Page<AdminAuditLog> logs = auditLogRepository.findFiltered(actorId, action, pageable);

        // 행위자 닉네임 배치 조회 — N+1 방지.
        List<Long> actorIds = logs.getContent().stream().map(AdminAuditLog::getActorId).distinct().toList();
        Map<Long, String> nicknames = actorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(actorIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getNickname() != null ? u.getNickname() : "(닉네임 없음)",
                                (a, b) -> a));

        return logs.map(l -> AdminAuditLogResponse.of(l, nicknames.getOrDefault(l.getActorId(), "(탈퇴한 관리자)")));
    }
}
