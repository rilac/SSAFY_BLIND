-- 신고 기록 보존 — 게시글이 삭제돼도 "누가 신고당했는지"가 남도록 한다.
--
-- 배경: PostService.deletePost는 FK 제약 때문에 reports 행을 함께 지운다(reportRepository.deleteByPostId).
-- 그런데 관리자 검수 큐(AdminService.getReportedPosts)와 신고 통계(getReportStats)가 모두 reports에서만
-- 파생되므로, 글이 지워지면 신고 사유·건수·신고자 기록이 흔적 없이 사라져 상습 위반자 추적이 불가능했다.
-- 숨김 글 작성자에게 "숨김 처리됨"을 알려주기 시작하면서 이 삭제가 안내된 1클릭 경로가 되므로 먼저 막는다.
--
-- posts를 FK로 참조하지 않는다(글이 이미 없으므로). post_id는 사후 대조용 값으로만 보관한다.
-- reporter_id도 FK를 두지 않는다 — 신고자가 탈퇴해도 집계는 남아야 한다.
create table report_archives (
    id          bigint       not null auto_increment,
    post_id     bigint       not null,
    post_title  varchar(255) not null,
    author_id   bigint       not null,
    reporter_id bigint       not null,
    reason      enum ('GAMBLING_OR_ADULT','OFF_TOPIC','PERSONAL_ATTACK','SPAM','ETC'),
    detail      varchar(200),
    reported_at datetime(6)  not null,
    archived_at datetime(6)  not null,
    primary key (id)
) engine=InnoDB;

-- 상습 위반자 조회(작성자 기준)와 보존 기간 관리(아카이브 시각)를 위한 인덱스.
create index idx_report_archives_author on report_archives (author_id, archived_at);
