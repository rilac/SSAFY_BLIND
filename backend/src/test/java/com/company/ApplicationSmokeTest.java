package com.company;

import com.company.global.config.SecurityConfig;
import com.company.global.security.JwtAuthFilter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전체 컨텍스트 로드 + 보안 필터 체인 스모크.
 * - 관측성: /actuator/health 가 인증 없이 200(UP) — Actuator + SecurityConfig permitAll + JwtAuthFilter.shouldNotFilter 합동 검증.
 * - 보안: 보호된 API는 인증 없이 401 — JwtAuthFilter 인가.
 * profile=test(별도 yml 없음) + H2/더미 시크릿으로 외부 의존(MySQL/MM) 없이 부팅한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoketestdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", // M-NEW-7: 베이스라인은 MySQL 전용 — H2 스모크에선 Flyway 미실행
        // 팬텀 토큰용 Redis는 스모크 테스트에서 미기동 — 헬스 인디케이터를 꺼서 /actuator/health가 UP을 유지하게 한다.
        // (Lettuce 커넥션 팩토리는 지연 연결이라 부팅 시 접속하지 않고, 보호 API는 토큰 부재로 Redis 조회 전 401.)
        "management.health.redis.enabled=false",
        "jwt.secret=test-only-secret-key-at-least-32-bytes-long-0123456789",
        "mattermost.base-url=http://localhost:9999",
        "app.cookie.secure=false",
        // app.admin.access-code-hash는 application.yml에 기본값이 없어(운영 fail-fast) 미지정 시
        // 플레이스홀더 해석 실패로 컨텍스트 자체가 뜨지 않는다. 빈 값을 주면 AdminStepUpService가
        // fail-closed(항상 인증 실패)로 동작하므로, 부팅만 시키면서 보안 의미도 그대로 유지된다.
        "app.admin.access-code-hash=",
})
class ApplicationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("헬스체크는 인증 없이 200(UP)을 반환한다")
    void test_health_public() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("보호된 API는 인증 없이 401을 반환한다")
    void test_protected_requires_auth() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R5: 게스트 공개 읽기 제거 — 전체글 목록도 인증 없이 401을 반환한다")
    void test_feed_requires_auth() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized());
    }
}
