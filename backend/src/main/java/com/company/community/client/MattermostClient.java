package com.company.community.client;

import com.company.community.dto.MattermostUser;
import com.company.community.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class MattermostClient {

    @Value("${mattermost.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Mattermost API로 로그인 요청을 프록시한다.
     * 성공 시 MM 유저 정보를 반환하고, 실패 시 예외를 던진다.
     */
    public MattermostUser login(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "login_id", loginId,
                "password", password
        );

        try {
            ResponseEntity<MattermostUser> response = restTemplate.postForEntity(
                    baseUrl + "/api/v4/users/login",
                    new HttpEntity<>(body, headers),
                    MattermostUser.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new InvalidCredentialsException("Mattermost 인증에 실패했습니다.");
        }
    }
}
