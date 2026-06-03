-- V7은 status를 varchar(20)로 추가했으나, Hibernate 6.x는 MySQL에서 @Enumerated(STRING)을
-- 네이티브 ENUM 컬럼으로 매핑한다(V1 베이스라인의 다른 enum 컬럼과 동일: users.status, posts.category 등).
-- ddl-auto=validate가 varchar↔enum 타입 불일치로 기동 실패하므로 ENUM으로 변환한다.
-- 기존 행은 모두 'PENDING'(V7 default)이라 변환은 안전. H2 테스트는 validate OFF라 이 차이를 못 잡음.
alter table feedbacks modify column status enum ('PENDING','RESOLVED','REJECTED') not null default 'PENDING';
