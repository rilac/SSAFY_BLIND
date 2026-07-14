package com.company;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

// 일회용 유틸 — 관리자 2차 인증 코드의 BCrypt 해시를 build/admin-code-hash.txt 로 출력한다.
// 실행: ./gradlew test --tests com.company.GenerateAdminCodeHashTest -Dadmin.code="원하는코드" --rerun-tasks
class GenerateAdminCodeHashTest {

    @Test
    void printHash() throws Exception {
        String code = System.getProperty("admin.code", "ssafy-admin-2026");
        String hash = new BCryptPasswordEncoder().encode(code);
        String out = "ADMIN_CODE_PLAINTEXT=" + code + "\nAPP_ADMIN_ACCESS_CODE_HASH=" + hash + "\n";
        Files.writeString(Path.of("build", "admin-code-hash.txt"), out);
    }
}
