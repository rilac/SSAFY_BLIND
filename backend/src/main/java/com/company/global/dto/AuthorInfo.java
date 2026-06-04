package com.company.global.dto;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 게시글/댓글에 노출하는 작성자 가명 정보.
 * ★ 닉네임·기수·지역(캠퍼스)까지만 — mmUserId/email 등 실제 신원은 절대 포함하지 않는다.
 * 탈퇴/익명화된 작성자는 "탈퇴한 사용자"로 표시.
 */
@Getter
@AllArgsConstructor
public class AuthorInfo {

    private String nickname;
    private String cohort;
    private String campus;

    public static AuthorInfo of(User user) {
        if (user == null || user.getStatus() == UserStatus.WITHDRAWN || user.getNickname() == null) {
            return new AuthorInfo("탈퇴한 사용자", null, null);
        }
        return new AuthorInfo(user.getNickname(), user.getCohort(), user.getCampus());
    }
}
