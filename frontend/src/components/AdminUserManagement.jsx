import { useState } from 'react';
import { Search, Ban, RotateCcw, Users } from 'lucide-react';
import api from '../api/client';
import { formatTimestamp } from '../lib/format';

// R8: 관리자 회원 관리 — MM계정/닉네임 검색 + 기수/지역/상태 필터 + 차단/차단해제.
const STATUS_LABELS = {
  PENDING: '대기', ACTIVE: '활동', DORMANT: '휴면', WITHDRAWN: '탈퇴', BLOCKED: '차단',
};
const COHORTS = ['15기', '16기'];
const CAMPUSES = ['서울', '대전', '광주', '부울경', '구미'];
const STATUSES = ['PENDING', 'ACTIVE', 'DORMANT', 'WITHDRAWN', 'BLOCKED'];

const PAGE_SIZE = 50;

// runAdminAction: 부모(AdminPage)가 주입하는 관리자 액션 래퍼 —
// step-up 만료면 모달로 재인증받고 막혔던 액션을 그대로 재시도한다(검색어·필터·페이지 상태 보존).
export default function AdminUserManagement({ runAdminAction }) {
  const [keyword, setKeyword] = useState('');
  const [cohort, setCohort] = useState('');
  const [campus, setCampus] = useState('');
  const [status, setStatus] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [page, setPage] = useState(0);
  const [pageInfo, setPageInfo] = useState(null); // { totalPages, totalElements }

  // 폼 제출은 1페이지부터, 페이저/차단 후 재조회는 현재 페이지 유지.
  const search = async (e, nextPage = 0) => {
    e?.preventDefault();
    setLoading(true);
    await runAdminAction(async () => {
      const params = { size: PAGE_SIZE, page: nextPage };
      if (keyword.trim()) params.keyword = keyword.trim();
      if (cohort) params.cohort = cohort;
      if (campus) params.campus = campus;
      if (status) params.status = status;
      const res = await api.get('/admin/users', { params });
      setUsers(res.data.content || []);
      setPageInfo({ totalPages: res.data.totalPages ?? 1, totalElements: res.data.totalElements ?? 0 });
      setPage(nextPage);
      setSearched(true);
    }, { title: '조회 실패', message: '회원 조회에 실패했습니다.' });
    setLoading(false);
  };

  const toggleBlock = (u) => {
    // 재시도 시점에 u.status가 갱신돼 있을 수 있으므로 방향을 지금 값으로 고정한다.
    const action = u.status === 'BLOCKED' ? 'unblock' : 'block';
    return runAdminAction(async () => {
      await api.post(`/admin/users/${u.id}/${action}`);
      // 변경 반영 — 현재 페이지 재조회.
      await search(undefined, page);
    }, { title: '변경 실패', message: '상태 변경에 실패했습니다.' });
  };

  const select = 'h-9 px-2 bg-input-background border border-border text-xs font-mono focus:outline-none focus:border-primary';

  return (
    <section>
      <div className="flex items-center gap-2 mb-4">
        <Users size={18} className="text-primary" />
        <h2 className="text-lg font-mono font-semibold tracking-tight">회원 관리</h2>
      </div>

      <form onSubmit={search} className="border border-border bg-card p-4 mb-4 space-y-3">
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="MM 계정 / 이메일 / 닉네임 검색"
            className="flex-1 h-9 px-3 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary"
          />
          <button
            type="submit"
            className="flex items-center gap-1.5 px-3 h-9 text-xs font-mono border border-primary text-primary hover:bg-primary hover:text-primary-foreground transition-colors"
          >
            <Search size={14} /> 검색
          </button>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <select value={cohort} onChange={(e) => setCohort(e.target.value)} className={select}>
            <option value="">전체 기수</option>
            {COHORTS.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <select value={campus} onChange={(e) => setCampus(e.target.value)} className={select}>
            <option value="">전체 지역</option>
            {CAMPUSES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)} className={select}>
            <option value="">전체 상태</option>
            {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
          </select>
        </div>
      </form>

      {loading ? (
        <p className="text-sm font-mono text-muted-foreground">불러오는 중...</p>
      ) : !searched ? (
        <div className="border border-border bg-card p-8 text-center text-sm font-mono text-muted-foreground">
          검색 조건을 입력하고 검색하세요
        </div>
      ) : users.length === 0 ? (
        <div className="border border-border bg-card p-8 text-center text-sm font-mono text-muted-foreground">
          조건에 맞는 회원이 없습니다
        </div>
      ) : (
        <div className="space-y-2">
          <p className="text-xs font-mono text-muted-foreground">총 {pageInfo?.totalElements ?? users.length}명</p>
          {users.map((u) => (
            <div key={u.id} className="border border-border bg-card p-4 flex items-center justify-between gap-3 flex-wrap">
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-mono font-semibold">{u.nickname || '(온보딩 전)'}</span>
                  <span className={`text-[10px] font-mono px-1.5 py-0.5 border ${
                    u.status === 'BLOCKED' ? 'border-destructive text-destructive' : 'border-border text-muted-foreground'
                  }`}>
                    {STATUS_LABELS[u.status] || u.status}
                  </span>
                  {u.role === 'ADMIN' && (
                    <span className="text-[10px] font-mono px-1.5 py-0.5 border border-primary text-primary">관리자</span>
                  )}
                </div>
                <div className="text-xs font-mono text-muted-foreground mt-1 break-all">
                  {u.mmUsername || u.email} · {u.cohort || '-'} {u.campus || ''} · 가입 {formatTimestamp(u.createdAt)}
                </div>
              </div>
              {/* 탈퇴 계정·관리자는 차단 대상에서 제외 */}
              {u.status !== 'WITHDRAWN' && u.role !== 'ADMIN' && (
                u.status === 'BLOCKED' ? (
                  <button
                    onClick={() => toggleBlock(u)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-border hover:border-primary transition-colors shrink-0"
                  >
                    <RotateCcw size={12} /> 차단 해제
                  </button>
                ) : (
                  <button
                    onClick={() => toggleBlock(u)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-destructive text-destructive hover:opacity-80 transition-opacity shrink-0"
                  >
                    <Ban size={12} /> 차단
                  </button>
                )
              )}
            </div>
          ))}
          {pageInfo && pageInfo.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3 pt-2 text-xs font-mono">
              <button
                onClick={() => search(undefined, page - 1)}
                disabled={page === 0}
                className="px-3 py-1.5 border border-border hover:border-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                이전
              </button>
              <span className="text-muted-foreground">{page + 1} / {pageInfo.totalPages}</span>
              <button
                onClick={() => search(undefined, page + 1)}
                disabled={page + 1 >= pageInfo.totalPages}
                className="px-3 py-1.5 border border-border hover:border-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                다음
              </button>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
