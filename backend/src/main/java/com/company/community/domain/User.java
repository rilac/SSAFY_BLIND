package com.company.community.domain;

import com.company.community.exception.InvalidStateException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String mmUserId;

    private String mmUsername;

    private String email;

    private String nickname;

    // 온보딩에서 수집 — 본인 프로필(사이드바/설정)에만 노출, 게시글엔 비노출(익명)
    private String cohort;   // 기수

    private String campus;   // 캠퍼스

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    // (#1) Role 시스템 — 신규 유저는 기본적으로 USER 권한
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // (#1) 온보딩 완료 도메인 메서드 — @Setter 사용 금지
    public void completeOnboarding(String nickname, String cohort, String campus) {
        if (this.status != UserStatus.PENDING) {
            throw new InvalidStateException("이미 온보딩을 완료한 유저입니다.");
        }
        this.nickname = nickname;
        this.cohort = cohort;
        this.campus = campus;
        this.status = UserStatus.ACTIVE;
    }

    // 휴면 전환 — ACTIVE 상태에서만 가능
    public void goDormant() {
        if (this.status != UserStatus.ACTIVE) {
            throw new InvalidStateException("활성 상태의 계정만 휴면 전환할 수 있습니다.");
        }
        this.status = UserStatus.DORMANT;
    }

    // 재로그인 시 휴면 → 활성 복구 (그 외 상태는 변화 없음)
    public void reactivateIfDormant() {
        if (this.status == UserStatus.DORMANT) {
            this.status = UserStatus.ACTIVE;
        }
    }

    // M-NEW-6: 최초 관리자 부트스트랩 — 환경변수로 지정한 계정을 ADMIN으로 승격. @Setter 금지.
    public void promoteToAdmin() {
        this.role = UserRole.ADMIN;
    }

    // 회원 탈퇴 — PII 익명화 + 재로그인 매칭 불가 처리. 게시글/댓글은 FK 보존.
    public void withdraw() {
        this.nickname = null;
        this.email = null;
        this.mmUsername = null;
        this.cohort = null;
        this.campus = null;
        // 동일 MM 계정으로 재로그인 시 이 행에 매칭되지 않도록 토큰으로 치환 → 신규 가입 처리됨
        this.mmUserId = "withdrawn-" + this.id;
        this.status = UserStatus.WITHDRAWN;
    }

    // --- UserDetails 구현 ---

    // (#1) role에 따라 ROLE_USER 또는 ROLE_ADMIN 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = (this.role == UserRole.ADMIN) ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return null; // MM 인증 사용, 자체 비밀번호 없음
    }

    @Override
    public String getUsername() {
        return mmUsername;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
