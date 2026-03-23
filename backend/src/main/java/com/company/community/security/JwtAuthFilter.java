package com.company.community.security;

import com.company.community.domain.User;
import com.company.community.repository.UserRepository;
import com.company.community.util.CookieUtils;
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

    /**
     * ★ 크리티컬 수정 (#1) — 인증 불필요 경로는 필터 자체를 건너뜀
     * PENDING 쿠키가 남아있어도 /api/auth/login 요청이 403으로 차단되지 않음
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/login")
            || path.startsWith("/api/auth/logout")
            || path.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // ★ HttpOnly 쿠키에서 JWT 추출
        String token = extractTokenFromCookie(request);

        if (token != null) {
            try {
                Claims claims = jwtProvider.parseToken(token);
                Long userId = Long.valueOf(claims.getSubject());
                String status = claims.get("status", String.class);

                // PENDING 유저는 온보딩 API와 /api/auth/me만 접근 가능
                if ("PENDING".equals(status)
                        && !request.getRequestURI().startsWith("/api/onboarding")
                        && !request.getRequestURI().equals("/api/auth/me")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"온보딩을 완료해주세요.\"}");
                    return;
                }

                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"유효하지 않은 토큰입니다.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 쿠키 배열에서 "jwt" 쿠키 값을 추출
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (CookieUtils.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
