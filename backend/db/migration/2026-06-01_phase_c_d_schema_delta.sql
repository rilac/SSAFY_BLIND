-- =====================================================================
-- 수동 스키마 마이그레이션 — Phase C/D 델타 (MySQL)
-- 적용 대상: 운영(prod) DB. prod는 spring.jpa.hibernate.ddl-auto=validate 이므로
--           아래 스키마가 없으면 애플리케이션이 기동되지 않는다(검증 실패).
--           dev는 ddl-auto=update 라 자동 생성되므로 적용 불필요.
-- 적용 시점: Phase C/D가 포함된 앱 버전을 배포하기 "전".
-- 전제: 1차 배포 스키마(posts/users/comments/post_likes/bookmarks/reports/
--       notifications/feedbacks, posts.category·posts.hidden 포함)가 이미 존재.
-- 주의: MySQL은 ADD COLUMN IF NOT EXISTS를 지원하지 않는다. 이미 적용했다면 중복 실행 금지.
-- =====================================================================

-- ---------------------------------------------------------------------
-- [Phase C / §1-1] posts.reviewed — 관리자가 복원(검수 완료)한 글 표시.
--   재신고가 임계값을 넘어도 자동 숨김에서 제외(재숨김 루프 방지).
--   타입은 기존 posts.hidden 과 동일하게 맞춘다(Hibernate boolean 매핑).
--   운영 DB에서 실제 타입 확인: SHOW COLUMNS FROM posts LIKE 'hidden';
-- ---------------------------------------------------------------------
ALTER TABLE posts
    ADD COLUMN reviewed BIT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------
-- [Phase D / M-NEW-5] post_views — 조회수 서버단 중복 제거 이력.
--   (post_id, user_id) 유니크: 유저당 게시글 1행 유지, 최근 조회 시각만 갱신.
--   post_id/user_id 는 NOT NULL FK(post_likes/bookmarks 와 동일 패턴).
--   게시글 삭제 시 서비스가 먼저 자식으로 정리한다(FK 위반 방지).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS post_views (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    post_id   BIGINT      NOT NULL,
    user_id   BIGINT      NOT NULL,
    viewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_views_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_views_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_post_views_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
