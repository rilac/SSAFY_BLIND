package com.company.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 팬텀 토큰(access) / Refresh Token 저장소 Redis 설정.
 *
 * <p>연결 팩토리(Lettuce)는 {@code spring.data.redis.*}(application-{profile}.yml)에서 Spring Boot가
 * 자동 구성한다. 여기서는 모든 키/값이 hex·JSON 문자열이므로 키·값 모두 {@link StringRedisSerializer}를
 * 명시한 {@link StringRedisTemplate}만 노출한다({@code AccessTokenStore}·{@code RefreshTokenService}가 주입).
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        return template;
    }
}
