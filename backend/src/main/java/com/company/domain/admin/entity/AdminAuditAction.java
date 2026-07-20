package com.company.domain.admin.entity;

// 감사 대상 관리자 행위. DB에는 varchar로 저장되므로 값 추가 시 마이그레이션이 필요 없다.
public enum AdminAuditAction {
    // 변경
    BLOCK_USER,
    UNBLOCK_USER,
    HIDE_POST,
    RESTORE_POST,
    PIN_POST,
    UNPIN_POST,
    UPDATE_FEEDBACK_STATUS,
    // 관리자 권한으로 타인 글을 수정/삭제한 경우(작성자 본인 행위는 기록 대상 아님)
    UPDATE_POST_AS_ADMIN,
    DELETE_POST_AS_ADMIN,
    // 조회 — 익명 커뮤니티에서 신원 열람은 변경만큼 중요한 신호다
    SEARCH_USERS,
    VIEW_USER,
    // 인증
    STEP_UP_SUCCESS,
    STEP_UP_FAILURE
}
