-- R8: 관리자 차단 상태(BLOCKED) 추가.
-- users.status는 네이티브 MySQL enum이라 새 값을 쓰려면 ALTER로 enum 정의를 확장해야 한다
-- (없으면 status='BLOCKED' write 시 data truncated 오류).
ALTER TABLE users
    MODIFY status enum('PENDING','ACTIVE','DORMANT','WITHDRAWN','BLOCKED') NOT NULL;
