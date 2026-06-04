package com.company.domain.user.entity;

public enum UserStatus {
    PENDING,   // 가입 후 온보딩 미완료
    ACTIVE,    // 정상 활동
    DORMANT,   // 휴면 — 재로그인 시 ACTIVE로 복구
    WITHDRAWN  // 탈퇴 — PII 익명화, 재로그인 불가
}
