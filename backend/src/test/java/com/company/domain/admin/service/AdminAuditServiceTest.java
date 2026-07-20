package com.company.domain.admin.service;

import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.entity.AdminAuditLog;
import com.company.domain.admin.entity.AdminAuditTargetType;
import com.company.domain.admin.repository.AdminAuditLogRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// 관리자 감사 기록 — 대상 있는 행위/없는 행위, detail 절삭, 기록 실패 시 업무 비차단.
@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock private AdminAuditLogRepository repository;

    @InjectMocks private AdminAuditService adminAuditService;

    @Test
    @DisplayName("대상이 있는 행위는 actor/action/target이 그대로 기록된다")
    void test_기록_대상있음() {
        adminAuditService.record(7L, AdminAuditAction.BLOCK_USER, AdminAuditTargetType.USER, 42L, null);

        ArgumentCaptor<AdminAuditLog> cap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(cap.capture());
        AdminAuditLog saved = cap.getValue();
        assertThat(saved.getActorId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo(AdminAuditAction.BLOCK_USER);
        assertThat(saved.getTargetType()).isEqualTo(AdminAuditTargetType.USER);
        assertThat(saved.getTargetId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("대상이 없는 행위(검색·인증)는 targetType=NONE, targetId=null로 기록된다")
    void test_기록_대상없음() {
        adminAuditService.record(7L, AdminAuditAction.SEARCH_USERS, "keyword=kim");

        ArgumentCaptor<AdminAuditLog> cap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getTargetType()).isEqualTo(AdminAuditTargetType.NONE);
        assertThat(cap.getValue().getTargetId()).isNull();
        assertThat(cap.getValue().getDetail()).isEqualTo("keyword=kim");
    }

    @Test
    @DisplayName("detail이 컬럼 폭(500자)을 넘으면 잘라서 저장한다 — 검색어 같은 가변 입력 대비")
    void test_detail_절삭() {
        adminAuditService.record(7L, AdminAuditAction.SEARCH_USERS, "가".repeat(600));

        ArgumentCaptor<AdminAuditLog> cap = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getDetail()).hasSize(500);
    }

    @Test
    @DisplayName("기록 실패가 업무를 막지 않는다 — 예외를 삼키고 로그만 남긴다")
    void test_기록실패_비차단() {
        given(repository.save(any(AdminAuditLog.class))).willThrow(new RuntimeException("DB 장애"));

        // 감사 기록이 실패했다고 차단·숨김 같은 모더레이션 자체가 막히면 더 나쁘다.
        assertThatCode(() -> adminAuditService.record(7L, AdminAuditAction.HIDE_POST,
                AdminAuditTargetType.POST, 1L, null)).doesNotThrowAnyException();
    }
}
