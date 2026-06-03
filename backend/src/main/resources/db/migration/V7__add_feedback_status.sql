-- 건의 처리 상태 — feedbacks에 status 추가(PENDING/RESOLVED/REJECTED).
-- 기존/신규 건의 모두 기본 PENDING. 처리됨(RESOLVED/REJECTED)은 관리자 기본 화면에서 숨겨진다.
-- VARCHAR + NOT NULL DEFAULT 'PENDING' → 기존 행 NOT NULL 충족, Hibernate validate 통과(EnumType.STRING 매핑).
alter table feedbacks add column status varchar(20) not null default 'PENDING';
