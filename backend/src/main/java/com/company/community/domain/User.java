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
@Getter                    // ★ @Setter 제거 — 상태 변경은 도메인 메서드로만 (#8)
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

    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ★ 도메인 메서드: 온보딩 완료 처리 (#8)
    // 상태 검증 + 닉네임/부서 설정 + ACTIVE 전환을 원자적으로 수행
    public void completeOnboarding(String nickname, String department) {
        if (this.status != UserStatus.PENDING) {
            throw new InvalidStateException("이미 온보딩을 완료한 유저입니다.");
        }
        this.nickname = nickname;
        this.department = department;
        this.status = UserStatus.ACTIVE;
    }

    // --- UserDetails 구현 ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
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
