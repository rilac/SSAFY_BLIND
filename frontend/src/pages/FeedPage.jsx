import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import Sidebar from '../components/Sidebar';
import TopBar from '../components/TopBar';
import PostCard from '../components/PostCard';
import ReportModal from '../components/ReportModal';
import { CATEGORY_LABELS } from '../lib/categories';

const PAGE_SIZE = 20;

function viewLabel(scope, category) {
  if (scope === 'bookmarked') return '스크랩';
  if (scope === 'mine') return '내가 쓴 글';
  if (category === 'all') return '전체글';
  return CATEGORY_LABELS[category] || '전체글';
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
  const [scope, setScope] = useState('all'); // all | mine | bookmarked
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

  // 검색 디바운스
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchInput.trim()), 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  // 필터 변경 시 첫 페이지부터 재조회
  useEffect(() => {
    fetchPosts(0, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category, scope, sort, debouncedSearch]);

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

  const fetchPosts = async (pageNum, reset) => {
    reset ? setLoading(true) : setLoadingMore(true);
    try {
      const res = await api.get(`/posts?${buildQuery(pageNum)}`);
      const data = res.data;
      setPosts((prev) => (reset ? data.content : [...prev, ...data.content]));
      setPage(data.currentPage);
      setHasNext(data.hasNext);
    } catch (err) {
      console.error('게시글 목록 조회 실패', err);
    } finally {
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
      alert('스크랩 처리에 실패했습니다.');
    }
  };

  const submitReport = async (reason) => {
    setReportSubmitting(true);
    try {
      await api.post(`/posts/${reportTargetId}/report`, { reason });
      setReportTargetId(null);
      alert('신고가 접수되었습니다.');
    } catch {
      alert('신고 처리에 실패했습니다.');
    } finally {
      setReportSubmitting(false);
    }
  };

  const handleSelectCategory = (c) => {
    setScope('all');
    setCategory(c);
  };

  const handleLogout = async () => {
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
        onSelectScope={setScope}
        onToggleUserMenu={() => setUserMenuOpen((o) => !o)}
        onOpenSettings={() => navigate('/settings')}
        onLogout={handleLogout}
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
              <h2 className="text-lg font-mono">{viewLabel(scope, category)}</h2>
              {debouncedSearch && (
                <span className="text-xs font-mono text-muted-foreground">SEARCH: "{debouncedSearch}"</span>
              )}
            </div>

            {loading ? (
              <p className="text-sm font-mono text-muted-foreground py-12 text-center">불러오는 중...</p>
            ) : posts.length === 0 ? (
              <div className="border border-border bg-card p-12 text-center">
                <p className="text-sm font-mono text-muted-foreground">표시할 게시글이 없습니다</p>
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
    </div>
  );
}
