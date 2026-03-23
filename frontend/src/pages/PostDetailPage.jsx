import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/client';

export default function PostDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();

  const [post, setPost] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentInput, setCommentInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [commentLoading, setCommentLoading] = useState(false);
  const [error, setError] = useState('');

  // 게시글 상세 + 댓글 목록 동시 조회 (Promise.all)
  useEffect(() => {
    fetchPostAndComments();
  }, [id]);

  const fetchPostAndComments = async () => {
    try {
      const [postRes, commentsRes] = await Promise.all([
        api.get(`/posts/${id}`),
        api.get(`/posts/${id}/comments`),
      ]);
      setPost(postRes.data);
      setComments(commentsRes.data);
    } catch (err) {
      setError('게시글을 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 댓글 작성
  const handleCommentSubmit = async (e) => {
    e.preventDefault();
    if (!commentInput.trim()) return;

    setCommentLoading(true);
    try {
      const res = await api.post(`/posts/${id}/comments`, { content: commentInput });
      // 새 댓글을 목록 끝에 즉시 추가
      setComments((prev) => [...prev, res.data]);
      setCommentInput('');
    } catch (err) {
      alert('댓글 작성에 실패했습니다.');
    } finally {
      setCommentLoading(false);
    }
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

  if (loading) {
    return (
      <div className="max-w-2xl mx-auto p-4 sm:p-6 text-center py-12 text-gray-400 text-sm">
        불러오는 중...
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="max-w-2xl mx-auto p-4 sm:p-6">
        <button
          onClick={() => navigate('/feed')}
          className="text-gray-500 hover:text-gray-700 transition-colors text-sm mb-4"
        >
          ← 피드로 돌아가기
        </button>
        <div className="bg-white rounded-xl shadow-sm p-8 text-center">
          <p className="text-gray-400 text-sm">{error || '게시글을 찾을 수 없습니다.'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto p-4 sm:p-6">
      {/* 뒤로가기 */}
      <button
        onClick={() => navigate('/feed')}
        className="text-gray-500 hover:text-gray-700 transition-colors text-sm mb-4"
      >
        ← 피드로 돌아가기
      </button>

      {/* 게시글 본문 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 mb-4">
        <h1 className="text-lg font-bold text-gray-800 mb-3">{post.title}</h1>
        <div className="flex items-center gap-3 text-xs text-gray-400 mb-4">
          <span>조회 {post.viewCount}</span>
          <span>{formatTime(post.createdAt)}</span>
        </div>
        {/* 본문 — 줄바꿈 유지 */}
        <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
          {post.content}
        </p>
      </div>

      {/* 댓글 섹션 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
        <h2 className="text-sm font-semibold text-gray-800 mb-4">
          댓글 {comments.length}개
        </h2>

        {/* 댓글 목록 */}
        {comments.length === 0 ? (
          <p className="text-xs text-gray-400 mb-4">아직 댓글이 없습니다.</p>
        ) : (
          <div className="space-y-3 mb-4">
            {comments.map((comment) => (
              <div
                key={comment.id}
                className="border-b border-gray-50 pb-3 last:border-b-0 last:pb-0"
              >
                <p className="text-sm text-gray-700">{comment.content}</p>
                <span className="text-xs text-gray-400 mt-1 block">
                  {formatTime(comment.createdAt)}
                </span>
              </div>
            ))}
          </div>
        )}

        {/* 댓글 입력 */}
        <form onSubmit={handleCommentSubmit} className="flex gap-2">
          <input
            type="text"
            value={commentInput}
            onChange={(e) => setCommentInput(e.target.value)}
            placeholder="댓글을 입력하세요"
            className="flex-1 px-4 py-2 border border-gray-300 rounded-lg text-sm
                       focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
          <button
            type="submit"
            disabled={commentLoading || !commentInput.trim()}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium
                       hover:bg-blue-700 transition-colors
                       disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
          >
            {commentLoading ? '...' : '작성'}
          </button>
        </form>
      </div>
    </div>
  );
}
