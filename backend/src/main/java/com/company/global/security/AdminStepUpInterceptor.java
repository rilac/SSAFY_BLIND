package com.company.global.security;

import com.company.domain.admin.service.AdminStepUpService;
import com.company.domain.user.entity.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * R8: 관리자 페이지 2차 인증(step-up) 강제 — {@code /api/admin/**} 접근 시 Redis step-up 마커를 요구한다.
 *
 * <p>{@code /api/admin/verify}(코드 제출)와 CORS 프리플라이트(OPTIONS)는 예외. 마커가 없으면
 * {@code 403 {"code":"STEP_UP_REQUIRED"}}를 반환해 프론트가 코드 입력 모달을 띄우도록 한다.
 * (ROLE_ADMIN 자체는 SecurityConfig가 이미 강제하므로, 여기서는 "관리자 + 2차 인증"의 두 번째 관문만 담당.)
 */
@Component
@RequiredArgsConstructor
public class AdminStepUpInterceptor implements HandlerInterceptor {

    private static final String VERIFY_PATH = "/api/admin/verify";

    private final AdminStepUpService adminStepUpService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (VERIFY_PATH.equals(request.getRequestURI())) {
            return true; // 2차 인증 제출 자체는 통과(ROLE_ADMIN만 요구)
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            // 방어적 — 정상 흐름에선 SecurityConfig(hasRole ADMIN)가 이미 차단.
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "{\"message\":\"인증이 필요합니다.\"}");
            return false;
        }
        if (!adminStepUpService.isVerified(user.getId())) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"code\":\"STEP_UP_REQUIRED\",\"message\":\"관리자 2차 인증이 필요합니다.\"}");
            return false;
        }
        return true;
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
