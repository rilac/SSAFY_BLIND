package com.company.domain.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "아이디를 입력해주세요.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;

    // R6: "로그인 상태 유지" — true일 때만 Refresh Token을 발급(영속 세션).
    // 미체크(기본 false)면 RT 미발급 + Access(팬텀) 쿠키를 세션 쿠키로 → 브라우저 종료 시 만료.
    private boolean rememberMe;
}
