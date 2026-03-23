import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function FeedPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);

  // 최초 로드
  useEffect(() => {
    fetchPosts(0, true);
  }, []);

  /**
   * 게시글 목록 조회 — 페이지네이션 (#10)
   * @param pageNum 요청할 페이지 번호
   * @param reset true면 목록 초기화 (최초 로드), false면 기존 목록에 추가 (더 보기)
   */
  const fetchPosts = async (pageNum, reset = false) => {
    if (reset) setLoading(true);
    else setLoadingMore(true);

    try {
      const res = await api.get(`/posts?page=${pageNum}&size=20`);
      const data = res.data;

      if (reset) {
        setPosts(data.content);
      } else {
        setPosts((prev) => [...prev, ...data.content]);
      }
      setPage(data.currentPage);
      setHasNext(data.hasNext);
    } catch (err) {
      console.error('게시글 목록 조회 실패', err);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  // "더 보기" 버튼 클릭
  const handleLoadMore = () => {
    fetchPosts(page + 1, false);
  };

  // 로그아웃 — AuthContext의 logout 사용
  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  // 상대 시간 포맷팅
  const formatTime = (dateStr) => {
    const now = new Date();
    const date = new Date(dateStr);
    const diff = Math.floor((now - date) / 1000);

    if (diff < 60) return '방금 전';
    if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
    if (diff < 2592000) return `${Math.floor(diff / 86400)}일 전`;
    return date.toLocaleDateString('ko-KR');
  };

  return (
    <div className="max-w-2xl mx-auto p-4 sm:p-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-800">익명 피드</h1>
        <button
          onClick={handleLogout}
          className="text-sm text-gray-500 hover:text-gray-700 transition-colors"
        >
          로그아웃
        </button>
      </div>

      {/* 게시글 목록 */}
      {loading ? (
        <div className="text-center py-12 text-gray-400 text-sm">불러오는 중...</div>
      ) : posts.length === 0 ? (
        <div className="bg-white rounded-xl shadow-sm p-8 text-center">
          <p className="text-gray-400 text-sm">
            아직 게시글이 없습니다. 첫 번째 글을 작성해보세요!
          </p>
        </div>
      ) : (
        <>
          <div className="space-y-3">
            {posts.map((post) => (
              <div
                key={post.id}
                onClick={() => navigate(`/posts/${post.id}`)}
                className="bg-white rounded-xl shadow-sm p-5 cursor-pointer
                           hover:shadow-md transition-shadow border border-gray-100"
              >
                <h2 className="text-base font-semibold text-gray-800 mb-2 line-clamp-1">
                  {post.title}
                </h2>
                <div className="flex items-center gap-3 text-xs text-gray-400">
                  <span>조회 {post.viewCount}</span>
                  <span>댓글 {post.commentCount}</span>
                  <span>{formatTime(post.createdAt)}</span>
                </div>
              </div>
            ))}
          </div>

          {/* ★ "더 보기" 버튼 — hasNext가 false면 숨김 (#10) */}
          {hasNext && (
            <div className="text-center mt-6">
              <button
                onClick={handleLoadMore}
                disabled={loadingMore}
                className="px-6 py-2.5 text-sm text-blue-600 bg-white border border-blue-200
                           rounded-lg hover:bg-blue-50 transition-colors
                           disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loadingMore ? '불러오는 중...' : '더 보기'}
              </button>
            </div>
          )}
        </>
      )}

      {/* 글 쓰기 플로팅 버튼 */}
      <button
        onClick={() => navigate('/posts/new')}
        className="fixed bottom-6 right-6 w-14 h-14 bg-blue-600 text-white rounded-full
                   shadow-lg hover:bg-blue-700 transition-colors flex items-center justify-center
                   text-2xl font-light"
        title="글 쓰기"
      >
        +
      </button>
    </div>
  );
}
