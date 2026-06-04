-- 기타(ETC) 신고의 상세 사유(자유 입력) 저장 컬럼. varchar이라 ENUM 매핑과 무관(validate 안전).
-- nullable: 사유 선택만 하고 상세를 안 적거나, ETC가 아닌 신고은 NULL.
alter table reports add column detail varchar(200);
