package com.company.community.domain;

// 신고 사유 — 프론트 신고 모달의 선택지와 1:1 대응
public enum ReportReason {
    GAMBLING_OR_ADULT("사행성·선정성"),
    OFF_TOPIC("커뮤니티 취지에 맞지 않음"),
    PERSONAL_ATTACK("인신공격·과도한 비방"),
    SPAM("스팸·광고"),
    ETC("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
