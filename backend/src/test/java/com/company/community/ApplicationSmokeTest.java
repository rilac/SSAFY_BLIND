package com.company.community;

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
        "jwt.secret=test-only-secret-key-at-least-32-bytes-long-0123456789",
        "mattermost.base-url=http://localhost:9999",
        "app.cookie.secure=false",
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
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isUnauthorized());
    }
}
