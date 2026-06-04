-- 맛집 공유(FOOD) 카테고리 추가. posts.category는 네이티브 ENUM이다
-- (V1 베이스라인: enum ('FREE','JOB','QUESTION'); Hibernate 6.x가 @Enumerated(STRING)을 MySQL ENUM으로 매핑).
-- Java enum에 FOOD만 추가하면 DB ENUM 정의에 없어 INSERT가 'Data truncated for column category'로 실패(→ 500).
-- Hibernate가 기대하는 DDL과 일치하도록 Java 선언 순서(FREE,JOB,QUESTION,FOOD)대로 ENUM을 확장한다.
-- nullable·default 없음 유지(V1 baseline과 동일 — posts.category는 NOT NULL 미지정).
-- H2 테스트는 flyway OFF + create-drop이라 이 변경과 무관.
alter table posts modify column category enum ('FREE','JOB','QUESTION','FOOD');
