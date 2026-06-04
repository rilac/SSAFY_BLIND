package com.company.domain.auth.entity;

import com.company.domain.user.entity.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 🗓️ 2026-06-02: Access/Refresh 토큰 분리 — DB 저장 Refresh Token.
// 원문(opaque 난수)은 클라이언트 쿠키에만 두고, DB엔 SHA-256 해시만 저장(유출 대비).
// (post_views 와 동일하게 user_id 는 NOT NULL FK.)
@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(columnNames = "token_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 원문이 아닌 SHA-256 해시(hex). 유니크 — 조회 인덱스 겸용.
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }
}
