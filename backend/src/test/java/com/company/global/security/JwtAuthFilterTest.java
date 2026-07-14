package com.company.global.security;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.util.CookieUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 팬텀 토큰 인증 필터 단위 테스트 — fail-closed(503) vs 폐기/만료(401) 구분이 개편의 핵심 계약
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtProvider jwtProvider;
    @Mock private UserRepository userRepository;
    @Mock private AccessTokenStore accessTokenStore;
    @Mock private FilterChain chain;

    @InjectMocks private JwtAuthFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/posts");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("R5: 팬텀 쿠키가 없으면 401 — 게스트 익명 통과 없음")
    void test_쿠키없음_401() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("팬텀이 Redis에 없으면(폐기/만료) 401")
    void test_팬텀_miss_401() throws Exception {
        request.setCookies(new Cookie(CookieUtils.ACCESS_COOKIE_NAME, "phantom-hex"));
        given(accessTokenStore.resolve("phantom-hex")).willReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("Redis 연결 장애면 fail-closed 503 — 401이 아니어야 프론트가 쿠키 삭제/리다이렉트 루프에 빠지지 않는다")
    void test_Redis장애_503() throws Exception {
        request.setCookies(new Cookie(CookieUtils.ACCESS_COOKIE_NAME, "phantom-hex"));
        given(accessTokenStore.resolve("phantom-hex"))
                .willThrow(new RedisConnectionFailureException("connection refused"));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("유효한 팬텀 + ACTIVE 유저면 인증 컨텍스트를 채우고 체인을 진행한다")
    void test_정상_인증() throws Exception {
        User user = user(UserStatus.ACTIVE);
        wireResolvedUser(user);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
    }

    @Test
    @DisplayName("BLOCKED 유저는 Redis 키가 살아있어도 DB 상태 재확인으로 401")
    void test_차단유저_401() throws Exception {
        wireResolvedUser(user(UserStatus.BLOCKED));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("PENDING 유저는 온보딩/me 외 접근이 403으로 제한된다")
    void test_PENDING_경로제한_403() throws Exception {
        wireResolvedUser(user(UserStatus.PENDING));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("OPTIONS 프리플라이트·인증 엔드포인트는 필터를 건너뛴다(CORS 회귀 방지)")
    void test_shouldNotFilter() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("OPTIONS", "/api/posts"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/refresh"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/logout"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/posts"))).isFalse();
    }

    // 팬텀 해석 → JWT 파싱 → DB 조회까지의 정상 경로 목 배선
    private void wireResolvedUser(User user) {
        request.setCookies(new Cookie(CookieUtils.ACCESS_COOKIE_NAME, "phantom-hex"));
        given(accessTokenStore.resolve("phantom-hex")).willReturn(Optional.of("server-side-jwt"));
        Claims claims = Jwts.claims().setSubject("42");
        given(jwtProvider.parseToken("server-side-jwt")).willReturn(claims);
        given(userRepository.findById(42L)).willReturn(Optional.of(user));
    }

    private User user(UserStatus status) {
        return User.builder()
                .id(42L)
                .mmUserId("mm-1")
                .status(status)
                .role(UserRole.USER)
                .build();
    }
}
