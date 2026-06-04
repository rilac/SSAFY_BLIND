import { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { FileText } from 'lucide-react'; // 빈 피드 상태 글리프
import axios from 'axios';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import Sidebar from '../components/Sidebar';
import TopBar from '../components/TopBar';
import PostCard from '../components/PostCard';
import ReportModal from '../components/ReportModal';
import ConfirmDialog from '../components/ConfirmDialog';
import AlertDialog from '../components/AlertDialog';
import { CATEGORY_LABELS } from '../lib/categories';

const PAGE_SIZE = 10; // [FEATURE:feed-pagination] 카테고리별 10건/페이지

function viewLabel(scope, category, user) {
  if (scope === 'bookmarked') return '스크랩';
  if (scope === 'mine') return '내가 쓴 글';
  // [FEATURE:cohort-campus-lounge] 라운지 뷰 라벨(현재 유저의 캠퍼스/기수 표기)
  if (scope === 'campus') return `우리 캠퍼스${user?.campus ? ` · ${user.campus}` : ''}`;
  if (scope === 'cohort') return `동기${user?.cohort ? ` · ${user.cohort}` : ''}`;
  // [/FEATURE:cohort-campus-lounge]
  if (category === 'all') return '전체글';
  return CATEGORY_LABELS[category] || '전체글';
}

// [FEATURE:category-descriptions] 제목 옆 안내 문구 — 현재 뷰(카테고리/라운지/스코프)에 맞춰 한 줄.
function viewDescription(scope, category, user) {
  if (scope === 'bookmarked') return '스크랩한 게시물을 모아봤어요.';
  if (scope === 'mine') return '내가 작성한 게시물이에요.';
  if (scope === 'campus') return `${user?.campus || '우리'} 캠퍼스 사용자들의 글만 조회합니다.`;
  if (scope === 'cohort') return `${user?.cohort || '동기'} 사용자들의 글만 조회합니다.`;
  const byCategory = {
    all: '싸피숲에 올라온 게시물들을 모두 확인해보세요.',
    FREE: '자유롭게 일상과 생각을 나눠보세요.',
    JOB: '취업 준비부터 합격 후기까지, 정보와 경험을 나눠요!',
    QUESTION: '프로젝트·코드 관련 내용을 편하게 질문해보세요!',
    FOOD: '나만 알고 있는 맛집을 모두와 공유해주세요!',
  };
  return byCategory[category] || byCategory.all;
}
// [/FEATURE:category-descriptions]

// 빈 상태 안내 한 줄 — 현재 뷰(검색/스크랩/내 글/일반)에 맞춰 다음 행동을 제시한다.
function emptyHint(scope, search) {
  if (search) return '다른 키워드로 검색해보세요';
  if (scope === 'bookmarked') return '관심 있는 글을 스크랩하면 여기에 모여요';
  if (scope === 'mine') return '아직 작성한 글이 없어요';
  return '첫 글을 작성해보세요';
}

export default function FeedPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user, loading: authLoading, logout } = useAuth();
  const { darkMode, toggleDarkMode } = useTheme();

  // ★ 뷰 상태(페이지·필터·정렬·검색)는 URL 쿼리에서 파생한다.
  //   글 상세로 이동(라우트 변경 → FeedPage 언마운트) 후 뒤로가기로 돌아와도 URL에서 그대로 복원된다.
  const category = searchParams.get('category') || 'all';
  const scope = searchParams.get('scope') || 'all';
  const sort = searchParams.get('sort') || 'latest';
  const keyword = searchParams.get('q') || '';
  const pageParam = parseInt(searchParams.get('page') || '1', 10);
  const page = !Number.isFinite(pageParam) || pageParam < 1 ? 1 : pageParam; // 1-based

  // URL 쿼리 갱신 — 필터/검색은 page=1로 리셋(resetPage), 페이지 이동만 resetPage=false.
  const updateParams = (updates, { resetPage = true, replace = false } = {}) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        Object.entries(updates).forEach(([k, v]) => {
          if (v == null || v === '') next.delete(k);
          else next.set(k, v);
        });
        if (resetPage) next.delete('page');
        return next;
      },
      { replace }
    );
  };

  // 목록 상태
  const [posts, setPosts] = useState([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const mainRef = useRef(null); // 페이지 이동 시 목록 상단으로 스크롤

  // 검색 입력(타이핑용) — URL q에서 초기화, 디바운스로 URL에 반영
  const [searchInput, setSearchInput] = useState(keyword);

  // UI 상태
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);

  // 신고 모달
  const [reportTargetId, setReportTargetId] = useState(null);
  const [reportSubmitting, setReportSubmitting] = useState(false);

  // 로그아웃 확인 모달
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

  // 알림 모달(M-NEW-4) — 네이티브 alert 대체
  const [notice, setNotice] = useState(null); // { title?, message }

  // 게스트 로그인 유도 모달 — 미로그인 상태에서 쓰기/참여 시도 시 노출
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const requireLogin = () => setLoginPromptOpen(true);

  // 검색 디바운스 — 입력을 URL q로 반영(검색은 히스토리 폭주 방지로 replace).
  useEffect(() => {
    const t = setTimeout(() => {
      const trimmed = searchInput.trim();
      if (trimmed !== keyword) updateParams({ q: trimmed || null }, { replace: true });
    }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchInput]);

  // 필터 변경 시 첫 페이지부터 재조회.
  // - 인증 준비(user?.id) 후에만 실행 → 로그인 직후 인증 커밋과의 경합 방지.
  // - AbortController + cleanup → StrictMode 이중 effect/언마운트 시 첫 요청을 취소,
  //   최종 1회만 상태를 반영해 "로그인 직후 피드 미로딩"을 방지한다.
  useEffect(() => {
    if (authLoading) return; // 인증 확인이 끝나면 게스트/회원 모두 조회(게스트도 공개 피드 열람)
    const controller = new AbortController();
    fetchPosts(controller.signal);
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authLoading, category, scope, sort, keyword, page]);

  // 상세 페이지 "피드로 돌아가기"가 머물던 페이지/필터로 오도록 현재 피드 URL을 기억(탭 단위).
  // navigate(-1)은 상세→수정→상세 경유 시 엉뚱한 곳으로 가므로, 명시적 피드 URL 복귀를 쓴다.
  useEffect(() => {
    const qs = searchParams.toString();
    sessionStorage.setItem('feedReturn', qs ? `/feed?${qs}` : '/feed');
  }, [searchParams]);

  // 알림 최초 로드 — 로그인 유저만(게스트가 /notifications 호출 시 401→로그인 리다이렉트되므로 호출 안 함)
  useEffect(() => {
    if (user) fetchNotifications();
  }, [user]);

  const fetchPosts = async (signal) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page - 1); // URL은 1-based, API는 0-based
      params.set('size', PAGE_SIZE);
      params.set('sort', sort);
      params.set('scope', scope);
      if (category !== 'all') params.set('category', category);
      if (keyword) params.set('keyword', keyword);
      const res = await api.get(`/posts?${params.toString()}`, { signal });
      setPosts(res.data.content);
      setTotalPages(res.data.totalPages);
      setLoading(false);
    } catch (err) {
      // 취소된 요청은 후속 요청이 상태를 처리하므로 loading 플래그를 건드리지 않는다.
      if (axios.isCancel(err)) return;
      console.error('게시글 목록 조회 실패', err);
      setLoading(false);
    }
  };

  // [FEATURE:feed-pagination] 페이지 이동 — URL ?page 갱신(히스토리에 남겨 뒤로가기로 복원) + 목록 상단 스크롤.
  const goToPage = (p1) => {
    updateParams({ page: p1 <= 1 ? null : String(p1) }, { resetPage: false });
    mainRef.current?.scrollTo({ top: 0 });
  };

  const fetchNotifications = async () => {
    try {
      const res = await api.get('/notifications');
      setNotifications(res.data);
    } catch {
      /* 알림 조회 실패는 조용히 무시 */
    }
  };

  const handleToggleBookmark = async (id) => {
    if (!user) return requireLogin(); // 게스트는 로그인 유도
    try {
      const res = await api.post(`/posts/${id}/bookmark`);
      const { bookmarked } = res.data;
      // 스크랩 뷰에서 해제하면 목록에서 제거, 그 외엔 상태만 갱신
      if (scope === 'bookmarked' && !bookmarked) {
        setPosts((prev) => prev.filter((p) => p.id !== id));
      } else {
        setPosts((prev) => prev.map((p) => (p.id === id ? { ...p, isBookmarked: bookmarked } : p)));
      }
    } catch {
      setNotice({ title: '스크랩 실패', message: '스크랩 처리에 실패했습니다.' });
    }
  };

  const submitReport = async (reason, detail) => {
    setReportSubmitting(true);
    try {
      await api.post(`/posts/${reportTargetId}/report`, { reason, detail }); // [FEATURE:report-detail]
      setReportTargetId(null);
      setNotice({ title: '신고 접수', message: '신고가 접수되었습니다.' });
    } catch {
      setNotice({ title: '신고 실패', message: '신고 처리에 실패했습니다.' });
    } finally {
      setReportSubmitting(false);
    }
  };

  const handleSelectCategory = (c) => {
    updateParams({ scope: null, category: c === 'all' ? null : c });
  };

  // (버그픽스) 스코프(라운지·내 글·스크랩) 선택 시 카테고리를 all로 리셋한다.
  // 카테고리 선택은 scope를 all로 되돌렸지만 반대(scope 선택)는 카테고리를 안 건드려서,
  // 직전 카테고리가 라운지(동기/우리 캠퍼스)에 그대로 필터로 남아 "특정 카테고리 글만 보이거나 빈 화면"이 됐다.
  // 스코프는 '모든 카테고리'를 보는 독립 뷰이므로 선택 시 category=all로 통일한다.
  const handleSelectScope = (s) => {
    updateParams({ category: null, scope: s === 'all' ? null : s });
  };

  // 사이드바 로그아웃 클릭 → 즉시 로그아웃하지 않고 확인 모달을 연다(사용자 메뉴는 닫음).
  const requestLogout = () => {
    setUserMenuOpen(false);
    setLogoutConfirmOpen(true);
  };

  // 확인 모달에서 '로그아웃' 확정 시에만 실제 로그아웃.
  const handleLogout = async () => {
    setLogoutConfirmOpen(false);
    await logout();
    navigate('/login');
  };

  // 알림 핸들러
  const handleNotificationClick = async (n) => {
    try {
      await api.patch(`/notifications/${n.id}/read`);
    } catch {
      /* ignore */
    }
    setNotifications((prev) => prev.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)));
    setNotificationsOpen(false);
    if (n.postId) navigate(`/posts/${n.postId}`);
  };

  const handleMarkAllRead = async () => {
    try {
      await api.post('/notifications/read-all');
    } catch {
      /* ignore */
    }
    setNotifications((prev) => prev.map((x) => ({ ...x, isRead: true })));
  };

  return (
    <div className="h-screen w-full bg-background text-foreground flex overflow-hidden">
      <Sidebar
        sidebarOpen={sidebarOpen}
        category={category}
        scope={scope}
        user={user}
        userMenuOpen={userMenuOpen}
        onSelectCategory={handleSelectCategory}
        onSelectScope={handleSelectScope}
        onToggleUserMenu={() => setUserMenuOpen((o) => !o)}
        onOpenSettings={() => navigate('/settings')}
        onLogout={requestLogout}
        onHome={() => navigate('/feed')}
        onFeedback={() => navigate('/feedback')}
        onAdmin={() => navigate('/admin')}
        onLogin={() => navigate('/login')}
      />

      <div className="flex-1 flex flex-col overflow-hidden">
        <TopBar
          sidebarOpen={sidebarOpen}
          searchValue={searchInput}
          darkMode={darkMode}
          notifications={notifications}
          notificationsOpen={notificationsOpen}
          sortBy={sort}
          onToggleSidebar={() => setSidebarOpen((o) => !o)}
          onSearchChange={setSearchInput}
          onToggleDarkMode={toggleDarkMode}
          user={user}
          onNewPost={() => (user ? navigate('/posts/new') : navigate('/login'))}
          onToggleNotifications={() => setNotificationsOpen((o) => !o)}
          onNotificationClick={handleNotificationClick}
          onMarkAllRead={handleMarkAllRead}
          onSortChange={(s) => updateParams({ sort: s === 'latest' ? null : s })}
        />

        <main ref={mainRef} className="flex-1 overflow-y-auto">
          <div className="max-w-4xl mx-auto p-6">
            {/* [FEATURE:category-descriptions] 제목 옆 카테고리/라운지 설명 문구 */}
            <div className="mb-4">
              <div className="flex items-baseline gap-2 flex-wrap">
                <h2 className="text-lg font-mono font-semibold tracking-tight">{viewLabel(scope, category, user)}</h2>
                <span className="text-sm font-mono text-muted-foreground">— {viewDescription(scope, category, user)}</span>
                {keyword && (
                  <span className="ml-auto text-xs font-mono text-muted-foreground">SEARCH: "{keyword}"</span>
                )}
              </div>
            </div>
            {/* [/FEATURE:category-descriptions] */}

            {loading ? (
              <p className="text-sm font-mono text-muted-foreground py-12 text-center">불러오는 중...</p>
            ) : posts.length === 0 ? (
              <div className="border border-border bg-card p-12 text-center">
                <FileText size={32} className="mx-auto mb-3 text-muted-foreground opacity-50" />
                <p className="text-sm font-mono text-muted-foreground">표시할 게시글이 없습니다</p>
                <p className="text-xs font-mono text-muted-foreground opacity-70 mt-1.5">
                  {emptyHint(scope, keyword)}
                </p>
              </div>
            ) : (
              <>
                <div className="space-y-4">
                  {posts.map((post) => (
                    <PostCard
                      key={post.id}
                      post={post}
                      onOpen={(id) => navigate(`/posts/${id}`)}
                      onToggleBookmark={handleToggleBookmark}
                      onReport={(pid) => (user ? setReportTargetId(pid) : requireLogin())}
                    />
                  ))}
                </div>

                {/* [FEATURE:feed-pagination] 페이지네이션 — 10건/페이지, 이전/다음 + 현재/전체 */}
                <div className="mt-8 pt-6 border-t border-border">
                  <Pager page={page} pageCount={totalPages} onChange={goToPage} />
                </div>
              </>
            )}
          </div>
        </main>
      </div>

      <ReportModal
        open={reportTargetId !== null}
        onClose={() => setReportTargetId(null)}
        onSubmit={submitReport}
        submitting={reportSubmitting}
      />

      <ConfirmDialog
        open={logoutConfirmOpen}
        title="로그아웃"
        message="정말 로그아웃 하시겠습니까?"
        confirmLabel="로그아웃"
        cancelLabel="취소"
        danger
        onConfirm={handleLogout}
        onClose={() => setLogoutConfirmOpen(false)}
      />

      {/* 게스트 로그인 유도 */}
      <ConfirmDialog
        open={loginPromptOpen}
        title="로그인이 필요합니다"
        message="로그인하고 더 많은 기능을 이용해보세요."
        confirmLabel="로그인하기"
        cancelLabel="닫기"
        onConfirm={() => navigate('/login')}
        onClose={() => setLoginPromptOpen(false)}
      />

      <AlertDialog
        open={!!notice}
        title={notice?.title}
        message={notice?.message}
        onClose={() => setNotice(null)}
      />
    </div>
  );
}

// [FEATURE:feed-pagination] 페이지네이션 컨트롤 — 이전/다음 + "현재 / 전체". 1페이지 이하면 미렌더.
function Pager({ page, pageCount, onChange }) {
  if (pageCount <= 1) return null;
  return (
    <div className="flex items-center justify-center gap-3 text-xs font-mono">
      <button
        onClick={() => onChange(page - 1)}
        disabled={page <= 1}
        className="px-3 py-1.5 border border-border hover:border-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
      >
        이전
      </button>
      <span className="text-muted-foreground">{page} / {pageCount}</span>
      <button
        onClick={() => onChange(page + 1)}
        disabled={page >= pageCount}
        className="px-3 py-1.5 border border-border hover:border-primary transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
      >
        다음
      </button>
    </div>
  );
}
