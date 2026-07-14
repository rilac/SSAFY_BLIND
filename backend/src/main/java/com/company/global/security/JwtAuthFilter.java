package com.company.global.security;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.util.CookieUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 팬텀 토큰 인증 필터 — 매 요청 access_token 쿠키(=팬텀)로 Redis에서 실제 JWT를 조회해 인증한다.
 *
 * <p>조회된 JWT는 서버 전용이다(응답/헤더/쿠키/로그 금지). 토큰이 없거나 Redis에 없으면(폐기/만료)
 * 401 — 게스트 익명 통과는 지원하지 않는다(R5: 로그인 필수). Redis 장애 시엔 fail-closed로 503.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final AccessTokenStore accessTokenStore;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS 프리플라이트(OPTIONS)는 쿠키가 없으므로 필터를 건너뛴다(익명 통과 제거로 인한 401 회귀 방지).
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
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

        String phantom = extractTokenFromCookie(request);

        // R5: 토큰(팬텀) 없으면 401 — 게스트 익명 통과 없음. 로그인 필수.
        if (phantom == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다.");
            return;
        }

        String token;
        try {
            // 팬텀 → 실제 JWT. Redis miss면 폐기/만료 → 401. 조회된 JWT는 절대 응답에 노출하지 않는다.
            Optional<String> resolved = accessTokenStore.resolve(phantom);
            if (resolved.isEmpty()) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "세션이 만료되었습니다. 다시 로그인해주세요.");
                return;
            }
            token = resolved.get();
        } catch (DataAccessException e) {
            // Redis 연결 장애 등 — fail-closed. 401(재로그인 유도)이 아니라 503으로 응답해
            // 프론트가 쿠키를 지우거나 로그인 리다이렉트 루프에 빠지지 않게 한다.
            sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "일시적으로 인증 서버에 연결할 수 없습니다.");
            return;
        }

        try {
            Claims claims = jwtProvider.parseToken(token); // 서명·만료 방어적 재검증
            Long userId = Long.valueOf(claims.getSubject());

            // H-NEW-2: 토큰 claim이 아니라 현재 DB 상태로 재검증한다.
            // 탈퇴(WITHDRAWN)/휴면(DORMANT)/차단(BLOCKED) 처리 후에는 Redis 키가 잠시 살아있어도 즉시 차단된다.
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
                // DORMANT/WITHDRAWN/BLOCKED — 토큰 무효(재로그인 필요/차단)
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
