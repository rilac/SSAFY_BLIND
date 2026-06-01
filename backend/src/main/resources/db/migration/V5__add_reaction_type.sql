-- [FEATURE:reactions] 게시글 반응 확장 — 기존 좋아요(post_likes)에 반응 종류 컬럼 추가.
-- (post_id,user_id) 유니크는 유지 → 1인 1반응(단일 선택). 기존 좋아요 행은 LIKE로 채운다(DEFAULT).
-- enum 값/순서는 ReactionType 과 일치(= Hibernate validate 통과). DEFAULT는 검증에 무관(validate는 default 미검사).
alter table post_likes
    add column reaction_type enum('LIKE','HELPFUL','INFORMATIVE','EMPATHY') not null default 'LIKE';
