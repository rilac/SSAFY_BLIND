-- [FEATURE:poll] 익명 투표/설문 — 게시글 부속 보기(poll_options) + 표(poll_votes).
-- 보기가 2개 이상이면 그 글은 투표 글. 표는 (post_id,user_id) 유니크로 1인 1표(표시는 집계만 = 익명).
-- 컬럼/타입은 엔티티(PollOption·PollVote)에 대한 Hibernate validate와 일치. FK/유니크는 무결성용(validate는 미검사).

create table poll_options (
    id bigint not null auto_increment,
    post_id bigint not null,
    content varchar(255) not null,
    sort_order integer not null,
    primary key (id)
) engine=InnoDB;

create table poll_votes (
    id bigint not null auto_increment,
    post_id bigint not null,
    option_id bigint not null,
    user_id bigint not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

alter table poll_options add constraint fk_poll_options_post foreign key (post_id) references posts (id);
alter table poll_votes add constraint fk_poll_votes_post foreign key (post_id) references posts (id);
alter table poll_votes add constraint fk_poll_votes_user foreign key (user_id) references users (id);
alter table poll_votes add constraint uk_poll_votes_post_user unique (post_id, user_id);

create index idx_poll_options_post on poll_options (post_id);
create index idx_poll_votes_post on poll_votes (post_id);
