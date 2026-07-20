-- 관리자 행위 감사 로그 — "누가 언제 무엇을 했는지"를 남긴다.
--
-- 배경: AdminService/AdminUserService에는 로거조차 없었고, block(Long userId)처럼 시그니처가 대상 id만 받아
-- 행위자(actor)를 알 방법이 구조적으로 없었다. 관리자가 2명 이상이 되는 순간 추적이 불가능해진다.
-- step-up이 15분 TTL 세션이라 탈취되면 그 안에 임의 차단/숨김이 가능한데 사후 감사 수단이 0이었다.
--
-- 조회(USER_SEARCH/USER_VIEW)도 기록한다: AdminUserResponse가 실계정(mmUserId/email)과 커뮤니티 가명
-- (nickname/cohort/campus)을 한 레코드에 묶은 역-익명화 조회 테이블이라, 익명 커뮤니티에서는
-- "누가 무엇을 바꿨는가"보다 "누가 신원을 들여다봤는가"가 더 중요한 신호다.
--
-- append-only: 삭제 API를 만들지 않으며 엔티티에도 상태 변경 메서드를 두지 않는다.
-- actor_id/target_id에 FK를 두지 않는다 — 대상이 삭제·탈퇴해도 감사 기록은 남아야 한다.
create table admin_audit_logs (
    id          bigint       not null auto_increment,
    actor_id    bigint       not null,
    action      varchar(40)  not null,
    target_type varchar(20)  not null,
    target_id   bigint,
    detail      varchar(500),
    ip          varchar(45),
    created_at  datetime(6)  not null,
    primary key (id)
) engine=InnoDB;

-- 목록은 항상 최신순 — 정렬 인덱스. 행위자별 조회용 보조 인덱스.
create index idx_admin_audit_created on admin_audit_logs (created_at desc);
create index idx_admin_audit_actor on admin_audit_logs (actor_id, created_at desc);

-- action/target_type을 MySQL ENUM이 아니라 varchar로 둔 이유:
-- V8/V14에서 enum 컬럼에 값을 추가할 때마다 ALTER 마이그레이션을 세트로 내야 했고, 빠뜨리면
-- 'data truncated'로 쓰기가 죽었다. 감사 로그는 액션 종류가 계속 늘어나는 성격이라
-- 그 실패 모드를 감사 기록에 들이지 않는다(값 검증은 애플리케이션 enum이 담당).
