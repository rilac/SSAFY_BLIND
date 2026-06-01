package com.company.community.service;

import com.company.community.client.MattermostClient;
import com.company.community.domain.RefreshToken;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.dto.LoginResponse;
import com.company.community.dto.MattermostUser;
import com.company.community.dto.TokenPair;
import com.company.community.exception.InvalidCredentialsException;
import com.company.community.repository.UserRepository;
import com.company.community.security.JwtProvider;
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

    // M-NEW-6: ADMIN으로 승격할 MM username 목록(콤마 구분). 미설정이면 빈 값 → 승격 없음.
    // @RequiredArgsConstructor 대상이 아니므로 단위 테스트에서는 null로 남고 승격 로직은 건너뛴다.
    @Value("${app.admin.bootstrap-usernames:}")
    private String adminBootstrapUsernames;

    /**
     * MM 인증 → 유저 조회/생성 → JWT 발급
     */
    @Transactional
    public LoginResponse login(String loginId, String password) {
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

        // 휴면 계정이면 재로그인 시 ACTIVE로 복구 (탈퇴 계정은 mmUserId가 치환돼 매칭 안 됨 → 신규 가입)
        user.reactivateIfDormant();

        // M-NEW-6: 지정된 MM 계정을 로그인 시 ADMIN으로 승격(최초 관리자 부트스트랩).
        // user는 같은 트랜잭션의 관리 엔티티라 dirty checking으로 role 변경이 반영된다.
        if (user.getRole() != UserRole.ADMIN && isDesignatedAdmin(user.getMmUsername())) {
            user.promoteToAdmin();
        }

        // (#1) role을 JWT 클레임에 포함 (복구된 상태/권한 반영)
        // 🗓️ 2026-06-02: 짧은 Access Token + DB 저장 Refresh Token 동시 발급
        String accessToken = jwtProvider.generateToken(user.getId(), user.getStatus(), user.getRole());
        String refreshToken = refreshTokenService.issue(user);

        return new LoginResponse(accessToken, refreshToken, isNewUser);
    }

    /**
     * 🗓️ 2026-06-02: POST /api/auth/refresh — RT로 새 Access Token 재발급(+RT 회전).
     * H-NEW-2와 동일하게 현재 DB 상태로 재검증한다(ACTIVE/PENDING만 허용, DORMANT/WITHDRAWN은 거부).
     * RT가 없거나 무효/만료면 InvalidCredentialsException(→401).
     */
    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenService.findValid(rawRefreshToken);
        User user = token.getUser();

        UserStatus status = user.getStatus();
        if (status != UserStatus.ACTIVE && status != UserStatus.PENDING) {
            // 탈퇴/휴면 계정 — 재발급 거부(정상 흐름에선 RT가 이미 삭제됐지만 방어적으로 차단)
            throw new InvalidCredentialsException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        String newRefreshToken = refreshTokenService.rotate(token); // 1회용 회전
        String newAccessToken = jwtProvider.generateToken(user.getId(), status, user.getRole());
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    /** 🗓️ 2026-06-02: 로그아웃 — 제시된 RT만 DB에서 삭제(단일 세션 종료). */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.deleteByRawToken(rawRefreshToken);
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
