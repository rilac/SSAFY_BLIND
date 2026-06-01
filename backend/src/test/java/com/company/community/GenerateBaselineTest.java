package com.company.community;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Flyway V1 베이스라인 생성기 — Spring Boot의 실제 네이밍 전략 + MySQL 방언으로
 * 현재 엔티티 전체의 CREATE DDL을 build/generated-baseline.sql 로 출력한다.
 * (H2 데이터소스는 부팅용일 뿐, JDBC 메타데이터 접근을 끄고 스크립트만 생성한다.)
 *
 * 평소 테스트 실행에서 제외하기 위해 @Disabled. 스키마가 바뀌면 아래로 수동 실행해
 * 출력(build/generated-baseline.sql)을 새 V*__ 마이그레이션 작성에 참고한다:
 *   ./gradlew.bat -p backend test --tests "com.company.community.GenerateBaselineTest" -D...(아래 @Disabled 임시 제거)
 */
@Disabled("Flyway 베이스라인 재생성용 — 수동 실행")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:baselinegen;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/generated-baseline.sql",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
        "jwt.secret=test-only-secret-key-at-least-32-bytes-long-0123456789",
        "mattermost.base-url=http://localhost:9999",
        "app.cookie.secure=false",
})
class GenerateBaselineTest {

    @Test
    void generate() {
        // 컨텍스트 로딩 시점에 schema-generation 스크립트가 build/generated-baseline.sql 로 생성된다.
    }
}
