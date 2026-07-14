package com.company.domain.admin.controller.dto;

import com.company.domain.user.entity.User;

import java.time.LocalDateTime;

// R8: 관리자 유저 관리 응답 — 검색 대상(MM 계정/기수/지역/닉네임) + 상태/역할. 관리자 전용 뷰.
public record AdminUserResponse(
        Long id,
        String mmUserId,
        String mmUsername,
        String email,
        String nickname,
        String cohort,
        String campus,
        String status,
        String role,
        LocalDateTime createdAt) {

    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(),
                u.getMmUserId(),
                u.getMmUsername(),
                u.getEmail(),
                u.getNickname(),
                u.getCohort(),
                u.getCampus(),
                u.getStatus().name(),
                u.getRole().name(),
                u.getCreatedAt());
    }
}
