package com.company.community.service;

import com.company.community.client.MattermostClient;
import com.company.community.domain.User;
import com.company.community.domain.UserStatus;
import com.company.community.dto.LoginResponse;
import com.company.community.dto.MattermostUser;
import com.company.community.repository.UserRepository;
import com.company.community.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MattermostClient mmClient;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * MM 인증 → 유저 조회/생성 → JWT 발급
     */
    @Transactional
    public LoginResponse login(String loginId, String password) {
        // 1. Mattermost 인증
        MattermostUser mmUser = mmClient.login(loginId, password);

        // 2. 기존 유저 여부 확인
        boolean isNewUser = !userRepository.existsByMmUserId(mmUser.getId());

        // 3. 신규 유저면 PENDING 상태로 저장
        if (isNewUser) {
            User newUser = User.builder()
                    .mmUserId(mmUser.getId())
                    .mmUsername(mmUser.getUsername())
                    .email(mmUser.getEmail())
                    .status(UserStatus.PENDING)
                    .build();
            userRepository.save(newUser);
        }

        // 4. DB에서 유저 조회 후 JWT 발급
        User user = userRepository.findByMmUserId(mmUser.getId()).orElseThrow();
        String jwt = jwtProvider.generateToken(user.getId(), user.getStatus());

        return new LoginResponse(jwt, isNewUser);
    }
}
