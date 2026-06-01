-- [FEATURE:pinned-posts] 공지/고정글 — posts에 pinned 플래그 추가.
-- 기존 hidden/reviewed와 동일하게 boolean을 bit not null 로 매핑(V1 baseline 참고) → Hibernate validate 통과.
-- 신규/기존 글 모두 기본 미고정(default 0). DEFAULT는 validate 무관(default 미검사)이나 기존 행 NOT NULL 충족에 필요.
alter table posts add column pinned bit not null default 0;
