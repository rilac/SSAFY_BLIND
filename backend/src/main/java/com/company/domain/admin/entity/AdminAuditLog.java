package com.company.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 관리자 행위 감사 로그 — append-only.
 *
 * <p>상태 변경 도메인 메서드를 두지 않아 구조적으로 immutable하며, 삭제 API도 제공하지 않는다.
 * 관리자가 자기 흔적을 지울 수 있으면 감사 로그의 의미가 없어지기 때문이다.
 *
 * <p>actorId/targetId에 FK를 두지 않는다 — 대상 글이 삭제되거나 회원이 탈퇴해도 기록은 남아야 한다.
 * action/targetType은 varchar로 저장한다(값 추가 시 ALTER 마이그레이션 불필요, V17 주석 참조).
 */
@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorId;

    // @JdbcTypeCode(VARCHAR)가 필수다 — Hibernate 6은 MySQL에서 @Enumerated(STRING)을 기본적으로
    // 네이티브 ENUM으로 매핑하므로, 이게 없으면 varchar로 선언한 V17과 어긋나 기동 시 validate가 실패한다
    // ("wrong column type ... found [varchar], but expecting [enum(...)]").
    // varchar를 고수하는 이유는 V17 주석 참조(액션 값 추가 때마다 ALTER 마이그레이션을 내지 않기 위함).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AdminAuditTargetType targetType;

    // 검색·인증처럼 대상이 없는 행위는 null.
    private Long targetId;

    // 사람이 읽을 보조 정보(검색어, 상태 전이 등). PII가 섞일 수 있어 500자로 제한하고 원문은 자른다.
    @Column(length = 500)
    private String detail;

    // IPv6까지 고려해 45자.
    @Column(length = 45)
    private String ip;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
