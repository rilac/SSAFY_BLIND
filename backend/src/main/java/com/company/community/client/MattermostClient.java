package com.company.community.client;

import com.company.community.dto.MattermostUser;
import com.company.community.exception.InvalidCredentialsException;
import com.company.community.exception.MattermostUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class MattermostClient {

    @Value("${mattermost.base-url}")
    private String baseUrl;

    // H-NEW-3: 타임아웃 없는 RestTemplate은 MM 지연 시 스레드 고갈을 유발하므로
    // connect/read 타임아웃을 명시한다(설정 외부화: app.mattermost.*-timeout-ms).
    private final RestTemplate restTemplate;

    public MattermostClient(
            @Value("${app.mattermost.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.mattermost.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

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
            // 4xx — 자격증명 오류
            throw new InvalidCredentialsException("Mattermost 인증에 실패했습니다.");
        } catch (RestClientException e) {
            // 타임아웃(ResourceAccessException)·연결 실패·MM 5xx 등 → 빠른 503
            throw new MattermostUnavailableException("Mattermost 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
