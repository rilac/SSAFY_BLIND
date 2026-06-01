-- [FEATURE:nested-comments] 대댓글(1-depth) — 댓글의 부모 댓글 id.
-- null이면 최상위 댓글. FK 없이 bigint(부모 삭제 시 서비스가 답글 정리) — Comment.parentId 와 일치.
alter table comments add column parent_id bigint;
create index idx_comments_parent_id on comments (parent_id);
