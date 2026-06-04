package com.company.domain.feedback.entity;

/**
 * 건의 처리 상태.
 * - PENDING : 미처리(관리자 화면 기본 노출)
 * - RESOLVED: 처리 완료(수용)
 * - REJECTED: 수용 안 함
 * RESOLVED·REJECTED는 "처리됨"으로 간주되어 기본 목록에서 숨겨지고, '처리된 건의 보기' 토글로만 열람된다.
 */
public enum FeedbackStatus {
    PENDING("미처리"),
    RESOLVED("처리 완료"),
    REJECTED("수용 안 함");

    private final String label;

    FeedbackStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
