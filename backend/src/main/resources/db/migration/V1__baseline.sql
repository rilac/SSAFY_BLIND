-- =====================================================================
-- Flyway V1 베이스라인 (MySQL) — M-NEW-7
-- 현재 엔티티 전체 스키마. **Hibernate가 직접 생성**한 DDL이라 prod `ddl-auto: validate`와 정확히 일치한다.
-- (생성기: backend/src/test/.../GenerateBaselineTest — 스키마 변경 시 재실행해 참고)
--
-- 적용 규칙:
--  - 빈 DB(신규 prod): Flyway가 이 V1을 실행해 전체 스키마를 만든다.
--  - 기존 DB(로컬 dev 등): spring.flyway.baseline-on-migrate=true 가 V1을 "적용됨"으로 표시만 하고
--    실행하지 않는다(기존 스키마 보존). 이후 변경은 V2__... 로 추가한다.
--  - 기존 prod가 V1보다 "뒤처진"(예: refresh_tokens 누락) 경우엔 baseline 전에 수동으로 V1 수준까지
--    맞춰야 한다 — backend/db/README.md의 전환 절차 참고.
-- =====================================================================

create table bookmarks (created_at datetime(6) not null, id bigint not null auto_increment, post_id bigint not null, user_id bigint not null, primary key (id)) engine=InnoDB;
create table comments (author_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, post_id bigint not null, content varchar(255) not null, primary key (id)) engine=InnoDB;
create table feedbacks (author_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, content TEXT not null, title varchar(255) not null, primary key (id)) engine=InnoDB;
create table notifications (is_read bit not null, created_at datetime(6) not null, id bigint not null auto_increment, post_id bigint, recipient_id bigint not null, message varchar(255) not null, type enum ('COMMENT','LIKE','SYSTEM') not null, primary key (id)) engine=InnoDB;
create table post_likes (created_at datetime(6) not null, id bigint not null auto_increment, post_id bigint not null, user_id bigint not null, primary key (id)) engine=InnoDB;
create table post_views (id bigint not null auto_increment, post_id bigint not null, user_id bigint not null, viewed_at datetime(6) not null, primary key (id)) engine=InnoDB;
create table posts (hidden bit not null, reviewed bit not null, view_count integer not null, author_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, content TEXT not null, title varchar(255) not null, category enum ('FREE','JOB','QUESTION'), primary key (id)) engine=InnoDB;
create table refresh_tokens (created_at datetime(6) not null, expires_at datetime(6) not null, id bigint not null auto_increment, user_id bigint not null, token_hash varchar(255) not null, primary key (id)) engine=InnoDB;
create table reports (created_at datetime(6) not null, id bigint not null auto_increment, post_id bigint not null, reporter_id bigint not null, reason enum ('GAMBLING_OR_ADULT','OFF_TOPIC','PERSONAL_ATTACK','SPAM','ETC'), primary key (id)) engine=InnoDB;
create table users (created_at datetime(6) not null, id bigint not null auto_increment, campus varchar(255), cohort varchar(255), email varchar(255), mm_user_id varchar(255) not null, mm_username varchar(255), nickname varchar(255), role enum ('USER','ADMIN') not null, status enum ('PENDING','ACTIVE','DORMANT','WITHDRAWN') not null, primary key (id)) engine=InnoDB;

alter table bookmarks add constraint UKavw6fntse0i19exgqcoygqq2n unique (post_id, user_id);
alter table post_likes add constraint UK5l2rj28vw5oj6f7ox746grokg unique (post_id, user_id);
alter table post_views add constraint UKgyayt4et9uio5nu46mm5xsksd unique (post_id, user_id);
alter table refresh_tokens add constraint UKo2mlirhldriil2y7krapq4frt unique (token_hash);
alter table reports add constraint UKlyovt914rro875177xymwwspl unique (post_id, reporter_id);
alter table users add constraint UK_7px6fkyyjiklbjd85q4im4nr4 unique (mm_user_id);

alter table bookmarks add constraint FK7nbb4ldgek7ux7y6lu0y4g826 foreign key (post_id) references posts (id);
alter table bookmarks add constraint FKdbsho2e05w5r13fkjqfjmge5f foreign key (user_id) references users (id);
alter table comments add constraint FKn2na60ukhs76ibtpt9burkm27 foreign key (author_id) references users (id);
alter table comments add constraint FKh4c7lvsc298whoyd4w9ta25cr foreign key (post_id) references posts (id);
alter table feedbacks add constraint FKqkvpcjkv8kkcqx0u2kaergq89 foreign key (author_id) references users (id);
alter table notifications add constraint FKqqnsjxlwleyjbxlmm213jaj3f foreign key (recipient_id) references users (id);
alter table post_likes add constraint FKa5wxsgl4doibhbed9gm7ikie2 foreign key (post_id) references posts (id);
alter table post_likes add constraint FKkgau5n0nlewg6o9lr4yibqgxj foreign key (user_id) references users (id);
alter table post_views add constraint FKm1fm9hc7487k4j6qd2g1iq0k2 foreign key (post_id) references posts (id);
alter table post_views add constraint FKiiwykhlbhjwi5cxxcx9n76cd6 foreign key (user_id) references users (id);
alter table posts add constraint FK6xvn0811tkyo3nfjk2xvqx6ns foreign key (author_id) references users (id);
alter table refresh_tokens add constraint FK1lih5y2npsf8u5o3vhdb9y0os foreign key (user_id) references users (id);
alter table reports add constraint FKneu1viyp671jjiwukyfv6dsy foreign key (post_id) references posts (id);
alter table reports add constraint FKd3qiw2om5d2oh5xb7fbdcq225 foreign key (reporter_id) references users (id);

-- 비-엔티티 성능 인덱스: 만료 RT 정리 스케줄러(DELETE WHERE expires_at < now)용.
create index idx_refresh_tokens_expires on refresh_tokens (expires_at);
