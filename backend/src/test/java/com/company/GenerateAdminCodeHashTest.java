package com.company;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

// 일회용 유틸 — 관리자 2차 인증 코드의 BCrypt 해시를 생성한다.
// 실행: ./gradlew test --tests com.company.GenerateAdminCodeHashTest -Dadmin.code="원하는코드" --rerun-tasks
//
// ⚠️ 테스트는 별도 JVM에서 포크되므로 -Dadmin.code는 build.gradle이 systemProperty로 명시 전달해야 도달한다.
//    그 설정이 없던 시절 이 테스트는 조용히 기본 코드("ssafy-admin-2026")의 해시를 만들었고,
//    운영자가 지정했다고 믿은 코드와 영원히 불일치해 관리자 인증이 항상 실패했다.
//    재발 방지를 위해 코드 미지정 시 기본값으로 넘어가지 않고 건너뛴다.
class GenerateAdminCodeHashTest {

    @Test
    void printHash() throws Exception {
        String code = System.getProperty("admin.code");
        Assumptions.assumeTrue(code != null && !code.isBlank(),
                "admin.code 미지정 — 해시 생성을 건너뜁니다. 사용법: "
                        + "./gradlew test --tests com.company.GenerateAdminCodeHashTest -Dadmin.code=\"원하는코드\" --rerun-tasks");

        String hash = new BCryptPasswordEncoder().encode(code);
        Path out = Path.of("build", "admin-code-hash.txt");
        Files.writeString(out, "APP_ADMIN_ACCESS_CODE_HASH=" + hash + "\n");

        // 파일을 열지 않아도 바로 복사할 수 있도록 콘솔에도 출력한다(평문 코드는 출력하지 않는다).
        System.out.println();
        System.out.println("=== 아래 한 줄을 backend/.env 에 붙여넣으세요 (따옴표로 감싸지 말 것) ===");
        System.out.println("APP_ADMIN_ACCESS_CODE_HASH=" + hash);
        System.out.println("=== 파일: " + out.toAbsolutePath() + " ===");
        System.out.println();
    }
}
