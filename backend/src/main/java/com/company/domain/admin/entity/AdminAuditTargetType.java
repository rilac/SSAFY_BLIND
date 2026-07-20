package com.company.domain.admin.entity;

// 감사 대상의 종류. targetId가 없는 행위(검색, 인증)는 NONE.
public enum AdminAuditTargetType {
    USER,
    POST,
    FEEDBACK,
    NONE
}
