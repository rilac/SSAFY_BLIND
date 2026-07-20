import { useState, useEffect, useCallback } from 'react';
import { ScrollText } from 'lucide-react';
import api from '../api/client';
import { formatTimestamp } from '../lib/format';

// 서버 AdminAuditAction enum과 1:1. 값이 늘면 여기도 추가(미등록 값은 원문 그대로 표시된다).
const ACTION_LABELS = {
  BLOCK_USER: '회원 차단',
  UNBLOCK_USER: '차단 해제',
  HIDE_POST: '게시글 숨김',
  RESTORE_POST: '숨김 해제',
  PIN_POST: '공지 고정',
  UNPIN_POST: '고정 해제',
  UPDATE_FEEDBACK_STATUS: '건의 상태 변경',
  UPDATE_POST_AS_ADMIN: '타인 글 수정',
  DELETE_POST_AS_ADMIN: '타인 글 삭제',
  SEARCH_USERS: '회원 검색',
  VIEW_USER: '회원 상세 조회',
  STEP_UP_SUCCESS: '2차 인증 성공',
  STEP_UP_FAILURE: '2차 인증 실패',
};

// 변경 행위와 조회/인증을 시각적으로 구분 — 조회 폭주 같은 패턴이 눈에 띄어야 한다.
const MUTATING = new Set([
  'BLOCK_USER', 'UNBLOCK_USER', 'HIDE_POST', 'RESTORE_POST', 'PIN_POST', 'UNPIN_POST',
  'UPDATE_FEEDBACK_STATUS', 'UPDATE_POST_AS_ADMIN', 'DELETE_POST_AS_ADMIN',
]);

const PAGE_SIZE = 20;

/**
 * 관리자 감사 로그 — 누가 언제 무엇을 했는지.
 *
 * runAdminAction: 부모(AdminPage)가 주입하는 관리자 액션 래퍼. step-up 만료 시 모달 재인증 후 재시도한다.
 */
export default function AdminAuditLog({ runAdminAction }) {
  const [logs, setLogs] = useState([]);
  const [pageInfo, setPageInfo] = useState({ totalPages: 1, totalElements: 0 });
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const [loading, setLoading] = useState(false);

  const fetchLogs = useCallback(async (nextPage, nextAction) => {
    setLoading(true);
    await runAdminAction(async () => {
      const params = { page: nextPage, size: PAGE_SIZE };
      if (nextAction) params.action = nextAction;
      const res = await api.get('/admin/audit-logs', { params });
      setLogs(res.data.content || []);
      setPageInfo({
        totalPages: res.data.totalPages ?? 1,
        totalElements: res.data.totalElements ?? 0,
      });
      setPage(nextPage);
    }, { title: '조회 실패', message: '감사 로그를 불러오지 못했습니다.' });
    setLoading(false);
  }, [runAdminAction]);

  useEffect(() => {
    fetchLogs(0, action);
    // action 변경 시 1페이지부터 다시 조회
  }, [action, fetchLogs]);

  return (
    <section>
      <div className="flex items-center justify-between mb-4 gap-3 flex-wrap">
        <h2 className="flex items-center gap-2 text-lg font-mono font-semibold tracking-tight">
          <ScrollText size={18} className="text-primary" />
          감사 로그 ({pageInfo.totalElements})
        </h2>
        <select
          value={action}
          onChange={(e) => setAction(e.target.value)}
          className="h-9 px-2 bg-input-background border border-border text-xs font-mono focus:outline-none focus:border-primary"
        >
          <option value="">전체 행위</option>
          {Object.entries(ACTION_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </div>

      {loading ? (
        <p className="text-sm font-mono text-muted-foreground">불러오는 중...</p>
      ) : logs.length === 0 ? (
        <p className="text-sm font-mono text-muted-foreground">기록이 없습니다.</p>
      ) : (
        <div className="border border-border divide-y divide-border">
          {logs.map((l) => (
            <div key={l.id} className="px-4 py-3 text-xs font-mono flex flex-wrap items-center gap-x-3 gap-y-1">
              <span className="text-muted-foreground shrink-0">{formatTimestamp(l.createdAt)}</span>
              <span className="text-foreground shrink-0">{l.actorNickname}</span>
              <span
                className={`px-1.5 py-0.5 leading-none shrink-0 ${
                  MUTATING.has(l.action)
                    ? 'bg-destructive text-destructive-foreground'
                    : 'border border-border text-muted-foreground'
                }`}
              >
                {ACTION_LABELS[l.action] || l.action}
              </span>
              {l.targetType !== 'NONE' && l.targetId != null && (
                <span className="text-muted-foreground">
                  {l.targetType === 'POST' ? '글' : l.targetType === 'USER' ? '회원' : '건의'} #{l.targetId}
                </span>
              )}
              {l.detail && <span className="text-muted-foreground opacity-70 break-all">{l.detail}</span>}
              {l.ip && <span className="ml-auto text-muted-foreground opacity-50 shrink-0">{l.ip}</span>}
            </div>
          ))}
        </div>
      )}

      {pageInfo.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-4 text-xs font-mono">
          <button
            onClick={() => fetchLogs(page - 1, action)}
            disabled={page <= 0}
            className="px-3 py-1.5 border border-border hover:border-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            이전
          </button>
          <span className="text-muted-foreground">{page + 1} / {pageInfo.totalPages}</span>
          <button
            onClick={() => fetchLogs(page + 1, action)}
            disabled={page + 1 >= pageInfo.totalPages}
            className="px-3 py-1.5 border border-border hover:border-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            다음
          </button>
        </div>
      )}
    </section>
  );
}
