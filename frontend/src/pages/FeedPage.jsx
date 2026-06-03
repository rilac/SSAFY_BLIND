import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
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

const PAGE_SIZE = 20;

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

// 빈 상태 안내 한 줄 — 현재 뷰(검색/스크랩/내 글/일반)에 맞춰 다음 행동을 제시한다.
function emptyHint(scope, search) {
  if (search) return '다른 키워드로 검색해보세요';
  if (scope === 'bookmarked') return '관심 있는 글을 스크랩하면 여기에 모여요';
  if (scope === 'mine') return '아직 작성한 글이 없어요';
  return '첫 글을 작성해보세요';
}

export default function FeedPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const { darkMode, toggleDarkMode } = useTheme();

  // 목록 상태
  const [posts, setPosts] = useState([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  // 필터 상태
  const [category, setCategory] = useState('all'); // 'all' | enum
  const [scope, setScope] = useState('all'); // all | mine | bookmarked | campus | cohort([FEATURE:cohort-campus-lounge])
  const [sort, setSort] = useState('latest'); // latest | popular
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

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

  // 검색 디바운스
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchInput.trim()), 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  // 필터 변경 시 첫 페이지부터 재조회.
  // - 인증 준비(user?.id) 후에만 실행 → 로그인 직후 인증 커밋과의 경합 방지.
  // - AbortController + cleanup → StrictMode 이중 effect/언마운트 시 첫 요청을 취소,
  //   최종 1회만 상태를 반영해 "로그인 직후 피드 미로딩"을 방지한다.
  useEffect(() => {
    if (!user?.id) return;
    const controller = new AbortController();
    fetchPosts(0, true, controller.signal);
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id, category, scope, sort, debouncedSearch]);

  // 알림 최초 로드
  useEffect(() => {
    fetchNotifications();
  }, []);

  const buildQuery = (pageNum) => {
    const params = new URLSearchParams();
    params.set('page', pageNum);
    params.set('size', PAGE_SIZE);
    params.set('sort', sort);
    params.set('scope', scope);
    if (category !== 'all') params.set('category', category);
    if (debouncedSearch) params.set('keyword', debouncedSearch);
    return params.toString();
  };

  const fetchPosts = async (pageNum, reset, signal) => {
    reset ? setLoading(true) : setLoadingMore(true);
    try {
      const res = await api.get(`/posts?${buildQuery(pageNum)}`, { signal });
      const data = res.data;
      setPosts((prev) => (reset ? data.content : [...prev, ...data.content]));
      setPage(data.currentPage);
      setHasNext(data.hasNext);
      reset ? setLoading(false) : setLoadingMore(false);
    } catch (err) {
      // 취소된 요청은 후속 요청이 상태를 처리하므로 loading 플래그를 건드리지 않는다.
      if (axios.isCancel(err)) return;
      console.error('게시글 목록 조회 실패', err);
      reset ? setLoading(false) : setLoadingMore(false);
    }
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

  const submitReport = async (reason) => {
    setReportSubmitting(true);
    try {
      await api.post(`/posts/${reportTargetId}/report`, { reason });
      setReportTargetId(null);
      setNotice({ title: '신고 접수', message: '신고가 접수되었습니다.' });
    } catch {
      setNotice({ title: '신고 실패', message: '신고 처리에 실패했습니다.' });
    } finally {
      setReportSubmitting(false);
    }
  };

  const handleSelectCategory = (c) => {
    setScope('all');
    setCategory(c);
  };

  // (버그픽스) 스코프(라운지·내 글·스크랩) 선택 시 카테고리를 all로 리셋한다.
  // 카테고리 선택은 scope를 all로 되돌렸지만 반대(scope 선택)는 카테고리를 안 건드려서,
  // 직전 카테고리가 라운지(동기/우리 캠퍼스)에 그대로 필터로 남아 "특정 카테고리 글만 보이거나 빈 화면"이 됐다.
  // 스코프는 '모든 카테고리'를 보는 독립 뷰이므로 선택 시 category=all로 통일한다.
  const handleSelectScope = (s) => {
    setCategory('all');
    setScope(s);
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
          onNewPost={() => navigate('/posts/new')}
          onToggleNotifications={() => setNotificationsOpen((o) => !o)}
          onNotificationClick={handleNotificationClick}
          onMarkAllRead={handleMarkAllRead}
          onSortChange={setSort}
        />

        <main className="flex-1 overflow-y-auto">
          <div className="max-w-4xl mx-auto p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-mono font-semibold tracking-tight">{viewLabel(scope, category, user)}</h2>
              {debouncedSearch && (
                <span className="text-xs font-mono text-muted-foreground">SEARCH: "{debouncedSearch}"</span>
              )}
            </div>

            {loading ? (
              <p className="text-sm font-mono text-muted-foreground py-12 text-center">불러오는 중...</p>
            ) : posts.length === 0 ? (
              <div className="border border-border bg-card p-12 text-center">
                <FileText size={32} className="mx-auto mb-3 text-muted-foreground opacity-50" />
                <p className="text-sm font-mono text-muted-foreground">표시할 게시글이 없습니다</p>
                <p className="text-xs font-mono text-muted-foreground opacity-70 mt-1.5">
                  {emptyHint(scope, debouncedSearch)}
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
                      onReport={setReportTargetId}
                    />
                  ))}
                </div>

                {hasNext && (
                  <div className="mt-8 pt-6 border-t border-border">
                    <button
                      onClick={() => fetchPosts(page + 1, false)}
                      disabled={loadingMore}
                      className="w-full py-3 border border-border hover:border-primary text-sm font-mono transition-colors disabled:opacity-50"
                    >
                      {loadingMore ? '불러오는 중...' : 'LOAD_MORE_POSTS'}
                    </button>
                  </div>
                )}
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

      <AlertDialog
        open={!!notice}
        title={notice?.title}
        message={notice?.message}
        onClose={() => setNotice(null)}
      />
    </div>
  );
}
