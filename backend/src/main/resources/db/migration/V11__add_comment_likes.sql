-- [FEATURE:comment-likes] 댓글 좋아요(단일). post_likes와 동일 스타일 — 컬럼/타입은 CommentLike 엔티티와 일치(validate 통과).
create table comment_likes (created_at datetime(6) not null, id bigint not null auto_increment, comment_id bigint not null, user_id bigint not null, primary key (id)) engine=InnoDB;
alter table comment_likes add constraint UK_comment_likes_comment_user unique (comment_id, user_id);
alter table comment_likes add constraint FK_comment_likes_comment foreign key (comment_id) references comments (id);
alter table comment_likes add constraint FK_comment_likes_user foreign key (user_id) references users (id);
