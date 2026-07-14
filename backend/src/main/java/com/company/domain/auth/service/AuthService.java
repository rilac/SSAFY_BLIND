package com.company.domain.auth.service;

import com.company.domain.auth.controller.dto.LoginResponse;
import com.company.domain.auth.controller.dto.TokenPair;
import com.company.domain.user.entity.OnboardingOptions;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.ForbiddenException;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.mattermost.MattermostClient;
import com.company.global.mattermost.MattermostUser;
import com.company.global.security.AccessTokenStore;
import com.company.global.security.JwtProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MattermostClient mmClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService; // 🗓️ 2026-06-02: AT+RT 발급/회전
    private final AccessTokenStore accessTokenStore;       // 팬텀 토큰: JWT를 Redis에 저장, 클라이언트엔 해시만

    // M-NEW-6: ADMIN으로 승격할 MM username 목록(콤마 구분). 미설정이면 빈 값 → 승격 없음.
    // @RequiredArgsConstructor 대상이 아니므로 단위 테스트에서는 null로 남고 승격 로직은 건너뛴다.
    @Value("${app.admin.bootstrap-usernames:}")
    private String adminBootstrapUsernames;

    /**
     * MM 인증 → 유저 조회/생성 → JWT 발급 → Redis 저장 → 팬텀(해시)만 반환.
     *
     * @param rememberMe "로그인 상태 유지" 여부 — true일 때만 Refresh Token을 발급한다(R6).
     */
    @Transactional
    public LoginResponse login(String loginId, String password, boolean rememberMe) {
        // 1. Mattermost 인증
        MattermostUser mmUser = mmClient.login(loginId, password);

        // 2. 기존 유저 여부 확인
        boolean isNewUser = !userRepository.existsByMmUserId(mmUser.getId());

        // 3. 신규 유저면 PENDING 상태 + USER 권한으로 저장
        if (isNewUser) {
            User newUser = User.builder()
                    .mmUserId(mmUser.getId())
                    .mmUsername(mmUser.getUsername())
                    .email(mmUser.getEmail())
                    .status(UserStatus.PENDING)
                    .role(UserRole.USER)   // (#1) 기본 권한 USER
                    .build();
            userRepository.save(newUser);
        }

        // 4. DB에서 유저 조회
        User user = userRepository.findByMmUserId(mmUser.getId()).orElseThrow();

        // R8: 관리자 차단(BLOCKED) 계정은 로그인 거부(자격증명 실패 아님 → 403).
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new ForbiddenException("차단된 계정입니다. 관리자에게 문의해주세요.");
        }
        // R1: 졸업 등으로 활동 기수 화이트리스트에서 빠진 기수는 로그인 차단(재로그인 부활 방지).
        //     온보딩을 마친 유저만 cohort를 가진다(PENDING/탈퇴는 null → 미적용).
        if (user.getCohort() != null && !OnboardingOptions.COHORTS.contains(user.getCohort())) {
            throw new ForbiddenException("졸업(비활성화)된 기수의 계정입니다. 접근이 제한됩니다.");
        }

        // 휴면 계정이면 재로그인 시 ACTIVE로 복구 (탈퇴 계정은 mmUserId가 치환돼 매칭 안 됨 → 신규 가입).
        // 위 화이트리스트 게이트를 통과한(=활동 기수) 휴면 계정만 여기 도달한다.
        user.reactivateIfDormant();

        // M-NEW-6: 지정된 MM 계정을 로그인 시 ADMIN으로 승격(최초 관리자 부트스트랩).
        // user는 같은 트랜잭션의 관리 엔티티라 dirty checking으로 role 변경이 반영된다.
        if (user.getRole() != UserRole.ADMIN && isDesignatedAdmin(user.getMmUsername())) {
            user.promoteToAdmin();
        }

        // (#1) role을 JWT 클레임에 포함 (복구된 상태/권한 반영)
        // 팬텀 토큰: JWT를 Redis(at:{phantom})에 저장하고, 클라이언트엔 SHA-256(jwt) 해시만 쿠키로 준다.
        String jwt = jwtProvider.generateToken(user.getId(), user.getStatus(), user.getRole());
        String phantom = accessTokenStore.store(jwt, user.getId());
        // rememberMe=false면 null(RT 미발급) → 컨트롤러가 세션 쿠키만 세팅.
        String refreshToken = refreshTokenService.issue(user, rememberMe);

        return new LoginResponse(phantom, refreshToken, isNewUser);
    }

    /**
     * POST /api/auth/refresh — RT로 새 Access Token 재발급(+RT 회전) → Redis 저장 → 새 팬텀 반환.
     * H-NEW-2와 동일하게 현재 DB 상태로 재검증한다(ACTIVE/PENDING만 허용, 그 외는 거부).
     * RT가 없거나 무효/만료면 InvalidCredentialsException(→401). (RT는 rememberMe=true 세션에만 존재)
     */
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        Long userId = refreshTokenService.findValidUserId(rawRefreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("세션이 만료되었습니다. 다시 로그인해주세요."));

        UserStatus status = user.getStatus();
        if (status != UserStatus.ACTIVE && status != UserStatus.PENDING) {
            // 탈퇴/휴면/차단 — 재발급 거부(정상 흐름에선 RT가 이미 삭제됐지만 방어적으로 차단)
            throw new InvalidCredentialsException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        String newRefreshToken = refreshTokenService.rotate(rawRefreshToken, user); // 1회용 회전
        String newJwt = jwtProvider.generateToken(user.getId(), status, user.getRole());
        String newPhantom = accessTokenStore.store(newJwt, user.getId());
        return new TokenPair(newPhantom, newRefreshToken);
    }

    /** 로그아웃 — 제시된 RT 삭제 + 현재 Access 팬텀을 Redis에서 폐기(진짜 서버측 무효화). */
    @Transactional
    public void logout(String rawRefreshToken, String phantom) {
        refreshTokenService.deleteByRawToken(rawRefreshToken);
        accessTokenStore.revoke(phantom);
    }

    // app.admin.bootstrap-usernames(콤마 구분)에 포함된 MM username인지 — null-safe
    private boolean isDesignatedAdmin(String mmUsername) {
        if (mmUsername == null || adminBootstrapUsernames == null || adminBootstrapUsernames.isBlank()) {
            return false;
        }
        for (String configured : adminBootstrapUsernames.split(",")) {
            if (mmUsername.equals(configured.trim())) {
                return true;
            }
        }
        return false;
    }
}
