package com.company.community.dto;

import com.company.community.domain.User;
import com.company.community.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 현재 로그인된 유저 정보 응답.
 * email, mmUserId 등 민감 정보는 제외.
 */
@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String nickname;
    private String department;
    private UserStatus status;

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getDepartment(),
                user.getStatus()
        );
    }
}
