package com.company.global.security;

import com.company.domain.admin.service.AdminStepUpService;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

// R8: 관리자 2차 인증(step-up) 강제 인터셉터 — 게이트/예외 경로 검증
@ExtendWith(MockitoExtension.class)
class AdminStepUpInterceptorTest {

    @Mock private AdminStepUpService adminStepUpService;

    @InjectMocks private AdminStepUpInterceptor interceptor;

    private final Object handler = new Object();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OPTIONS 프리플라이트는 step-up 없이 통과한다")
    void test_OPTIONS_통과() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/users");

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        verifyNoInteractions(adminStepUpService);
    }

    @Test
    @DisplayName("/api/admin/verify(코드 제출)는 step-up 없이 통과한다")
    void test_verify_엔드포인트_통과() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/verify");

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        verifyNoInteractions(adminStepUpService);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 방어적으로 401을 반환한다")
    void test_미인증_401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");

        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("step-up 마커가 없으면 403 + STEP_UP_REQUIRED 코드를 반환한다")
    void test_마커없음_403_STEP_UP_REQUIRED() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        authenticate(admin(5L));
        given(adminStepUpService.isVerified(5L)).willReturn(false);

        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("STEP_UP_REQUIRED");
    }

    @Test
    @DisplayName("step-up 마커가 있으면(15분 이내) 통과한다")
    void test_마커있음_통과() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        authenticate(admin(5L));
        given(adminStepUpService.isVerified(5L)).willReturn(true);

        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private User admin(Long id) {
        return User.builder()
                .id(id)
                .mmUserId("mm-admin")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build();
    }
}
