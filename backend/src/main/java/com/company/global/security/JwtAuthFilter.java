package com.company.global.security;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.config.SecurityConfig;
import com.company.global.util.CookieUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/login")
            || path.startsWith("/api/auth/logout")
            || path.startsWith("/api/auth/refresh") // 🗓️ 재발급은 AT 없이도 동작해야 함(RT 쿠키로 검증)
            || path.startsWith("/actuator/health"); // 헬스체크는 토큰 없이 통과(SecurityConfig permitAll)
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractTokenFromCookie(request);

        // 게스트 공개 읽기 지원: 토큰이 없으면 401하지 않고 익명으로 통과한다.
        // 보호 리소스 차단은 SecurityConfig(authenticated + 401 엔트리포인트)가 담당.
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtProvider.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());

            // H-NEW-2: 토큰 claim이 아니라 현재 DB 상태로 재검증한다.
            // 탈퇴(WITHDRAWN)/휴면(DORMANT) 처리 후에는 (정상 흐름에선 쿠키가 만료되지만)
            // 탈취된 토큰 사본이 만료 전이라도 즉시 무효화된다.
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰입니다.");
                return;
            }

            UserStatus status = user.getStatus();
            String path = request.getRequestURI();

            if (status == UserStatus.PENDING) {
                // 온보딩 미완료 — 온보딩/me 외 접근 차단
                if (!path.startsWith("/api/onboarding") && !path.equals("/api/auth/me")) {
                    sendError(response, HttpServletResponse.SC_FORBIDDEN, "온보딩을 완료해주세요.");
                    return;
                }
            } else if (status != UserStatus.ACTIVE) {
                // DORMANT/WITHDRAWN — 토큰 무효(재로그인 필요)
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해주세요.");
                return;
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰입니다.");
            return;
        }

        chain.doFilter(request, response);
    }

    // JSON 에러 응답 헬퍼 — 메시지의 따옴표/역슬래시만 최소 이스케이프
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"message\":\"" + escaped + "\"}");
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (CookieUtils.ACCESS_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
