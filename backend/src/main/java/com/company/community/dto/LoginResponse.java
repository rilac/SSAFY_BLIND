package com.company.community.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String token;

    // ★ Jackson이 "newUser"로 직렬화하는 문제 방지 (#6)
    @JsonProperty("isNewUser")
    private boolean isNewUser;
}
