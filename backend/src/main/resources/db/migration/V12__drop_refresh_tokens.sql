-- Refresh Token 저장소를 MySQL → Redis로 이전(팬텀 토큰 개편).
-- refresh_tokens 테이블은 더 이상 사용하지 않으므로 삭제한다.
-- DROP TABLE이 unique(token_hash) 제약, users FK, idx_refresh_tokens_expires 인덱스를 함께 정리한다.
-- (아카이브: db/archive/refresh_tokens.sql — 제거된 DDL/쿼리 보존)
DROP TABLE IF EXISTS refresh_tokens;
