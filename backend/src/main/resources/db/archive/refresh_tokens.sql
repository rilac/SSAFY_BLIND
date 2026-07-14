-- ============================================================================
-- ARCHIVE — 더 이상 사용하지 않는 refresh_tokens 테이블의 DDL/쿼리 보존용.
-- Flyway는 db/archive 경로를 스캔하지 않는다(순수 문서). 실제 삭제는 V12__drop_refresh_tokens.sql.
--
-- Refresh Token은 팬텀 토큰 개편으로 Redis로 이전됨:
--   rt:{sha256(raw)}      -> userId            (TTL = app.jwt.refresh-ttl)
--   rt:uid:{userId}       -> Set<rt-hash>      (유저별 일괄 폐기)
--   rt:used:{sha256(raw)} -> userId            (회전 재사용 탐지 tombstone)
-- ============================================================================

-- 제거된 테이블 DDL (V1__baseline.sql 발췌) -----------------------------------
create table refresh_tokens (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id         bigint      not null auto_increment,
    user_id    bigint      not null,
    token_hash varchar(255) not null,
    primary key (id)
) engine=InnoDB;

alter table refresh_tokens
    add constraint UK_refresh_tokens_token_hash unique (token_hash);

alter table refresh_tokens
    add constraint FK_refresh_tokens_user
    foreign key (user_id) references users (id);

create index idx_refresh_tokens_expires on refresh_tokens (expires_at);

-- 제거된 리포지토리 쿼리 (RefreshTokenRepository JPQL) ------------------------
-- Optional<RefreshToken> findByTokenHash(String tokenHash);
--   SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash
-- void deleteByUserId(Long userId);
--   DELETE FROM RefreshToken rt WHERE rt.user.id = :userId
-- int deleteByExpiresAtBefore(LocalDateTime now);
--   DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now
