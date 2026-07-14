package com.company.domain.user.entity;

public enum UserStatus {
    PENDING,   // 가입 후 온보딩 미완료
    ACTIVE,    // 정상 활동
    DORMANT,   // 휴면 — 재로그인 시 ACTIVE로 복구(단, 활동 기수 화이트리스트 통과 시에만)
    WITHDRAWN, // 탈퇴 — PII 익명화, 재로그인 불가
    BLOCKED    // 관리자 차단 — 재로그인 불가, 즉시 토큰 폐기(R8). DB 네이티브 enum이라 V14 마이그레이션 필요.
}
