import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ThumbsUp, Bookmark, Flag, Eye, Pencil, Trash2 } from 'lucide-react';
import api from '../api/client';
import ReportModal from '../components/ReportModal';
import { formatTimestamp, formatNumber } from '../lib/format';
import { categoryLabel } from '../lib/categories';

// 게시글 상세 — 가명(닉네임·기수·지역) 노출. 카테고리 배지 + 좋아요/스크랩/신고 + 댓글.
export default function PostDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();

  const [post, setPost] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentInput, setCommentInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [likeLoading, setLikeLoading] = useState(false);
  const [bookmarkLoading, setBookmarkLoading] = useState(false);
  const [commentLoading, setCommentLoading] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportSubmitting, setReportSubmitting] = useState(false);

  useEffect(() => {
    const fetchAll = async () => {
      setLoading(true);
      try {
        const [p, c] = await Promise.all([
          api.get(`/posts/${id}`),
          api.get(`/posts/${id}/comments`),
        ]);
        setPost(p.data);
        setComments(c.data);
      } catch {
        setError('게시글을 불러올 수 없습니다.');
      } finally {
        setLoading(false);
      }
    };
    fetchAll();
  }, [id]);

  const handleLike = async () => {
    if (likeLoading) return;
    setLikeLoading(true);
    try {
      const res = await api.post(`/posts/${id}/like`);
      setPost((prev) => ({ ...prev, isLiked: res.data.liked, likeCount: res.data.likeCount }));
    } catch {
      alert('좋아요 처리에 실패했습니다.');
    } finally {
      setLikeLoading(false);
    }
  };

  const handleBookmark = async () => {
    if (bookmarkLoading) return;
    setBookmarkLoading(true);
    try {
      const res = await api.post(`/posts/${id}/bookmark`);
      setPost((prev) => ({ ...prev, isBookmarked: res.data.bookmarked }));
    } catch {
      alert('스크랩 처리에 실패했습니다.');
    } finally {
      setBookmarkLoading(false);
    }
  };

  const submitReport = async (reason) => {
    setReportSubmitting(true);
    try {
      await api.post(`/posts/${id}/report`, { reason });
      setReportOpen(false);
      alert('신고가 접수되었습니다.');
    } catch {
      alert('신고 처리에 실패했습니다.');
    } finally {
      setReportSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('정말로 이 게시글을 삭제하시겠습니까?')) return;
    try {
      await api.delete(`/posts/${id}`);
      navigate('/feed');
    } catch {
      alert('게시글 삭제에 실패했습니다.');
    }
  };

  const handleCommentSubmit = async (e) => {
    e.preventDefault();
    const trimmed = commentInput.trim();
    if (!trimmed) return;
    setCommentLoading(true);
    try {
      const res = await api.post(`/posts/${id}/comments`, { content: trimmed });
      setComments((prev) => [...prev, res.data]);
      setCommentInput('');
    } catch {
      alert('댓글 작성에 실패했습니다.');
    } finally {
      setCommentLoading(false);
    }
  };

  const handleDeleteComment = async (commentId) => {
    try {
      await api.delete(`/posts/${id}/comments/${commentId}`);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
    } catch {
      alert('댓글 삭제에 실패했습니다.');
    }
  };

  const backBtn = (
    <button
      onClick={() => navigate('/feed')}
      className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
    >
      <ArrowLeft size={16} />
      피드로 돌아가기
    </button>
  );

  if (loading) {
    return (
      <div className="min-h-screen w-full bg-background text-foreground">
        <div className="max-w-3xl mx-auto p-6 text-sm font-mono text-muted-foreground">불러오는 중...</div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="min-h-screen w-full bg-background text-foreground">
        <div className="max-w-3xl mx-auto p-6">
          {backBtn}
          <div className="border border-border bg-card p-12 text-center">
            <p className="text-sm font-mono text-muted-foreground">{error || '게시글을 찾을 수 없습니다.'}</p>
          </div>
        </div>
      </div>
    );
  }

  const isEdited = post.createdAt !== post.updatedAt;

  return (
    <div className="min-h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-6">
        {backBtn}

        {/* 본문 */}
        <article className="border border-border bg-card p-6 mb-4">
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-2 text-xs font-mono flex-wrap">
              <span className="text-primary">[{categoryLabel(post.category)}]</span>
              <span className="text-muted-foreground">{post.author?.nickname}</span>
              <span className="text-muted-foreground opacity-50">•</span>
              <span className="text-muted-foreground">
                {post.author?.cohort} {post.author?.campus}
              </span>
            </div>
            {post.isMine && (
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => navigate(`/posts/${id}/edit`)}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-border hover:border-primary transition-colors"
                >
                  <Pencil size={12} />
                  수정
                </button>
                <button
                  onClick={handleDelete}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-destructive text-destructive hover:opacity-80 transition-opacity"
                >
                  <Trash2 size={12} />
                  삭제
                </button>
              </div>
            )}
          </div>

          <h1 className="text-xl font-semibold mb-3">{post.title}</h1>

          <div className="flex items-center gap-3 text-xs font-mono text-muted-foreground mb-5">
            <span className="flex items-center gap-1.5">
              <Eye size={14} />
              {formatNumber(post.viewCount)}
            </span>
            <span className="opacity-50">•</span>
            <span>{formatTimestamp(post.createdAt)}</span>
            {isEdited && <span className="opacity-70">(수정됨)</span>}
          </div>

          <p className="text-sm leading-relaxed whitespace-pre-wrap mb-6">{post.content}</p>

          <div className="flex items-center gap-2 pt-4 border-t border-border">
            <button
              onClick={handleLike}
              disabled={likeLoading}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-mono border transition-colors disabled:opacity-50 ${
                post.isLiked
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'border-border hover:border-primary'
              }`}
            >
              <ThumbsUp size={14} fill={post.isLiked ? 'currentColor' : 'none'} />
              {post.likeCount}
            </button>

            <button
              onClick={handleBookmark}
              disabled={bookmarkLoading}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-mono border transition-colors disabled:opacity-50 ${
                post.isBookmarked
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'border-border hover:border-primary'
              }`}
            >
              <Bookmark size={14} fill={post.isBookmarked ? 'currentColor' : 'none'} />
              스크랩
            </button>

            <button
              onClick={() => setReportOpen(true)}
              className="flex items-center gap-2 px-4 py-2 text-sm font-mono border border-border text-muted-foreground hover:border-primary hover:text-foreground transition-colors ml-auto"
            >
              <Flag size={14} />
              신고
            </button>
          </div>
        </article>

        {/* 댓글 */}
        <section className="border border-border bg-card p-6">
          <h2 className="text-sm font-mono mb-4">댓글 {comments.length}개</h2>

          {comments.length === 0 ? (
            <p className="text-xs font-mono text-muted-foreground mb-4">아직 댓글이 없습니다.</p>
          ) : (
            <div className="space-y-3 mb-4">
              {comments.map((comment) => (
                <div key={comment.id} className="border-b border-border pb-3 last:border-b-0 last:pb-0">
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm flex-1">{comment.content}</p>
                    {comment.isMine && (
                      <button
                        onClick={() => handleDeleteComment(comment.id)}
                        className="text-xs font-mono text-destructive hover:opacity-80 transition-opacity shrink-0"
                      >
                        삭제
                      </button>
                    )}
                  </div>
                  <span className="text-[10px] font-mono text-muted-foreground mt-1 block">
                    {comment.author?.nickname} · {comment.author?.cohort} {comment.author?.campus} ·{' '}
                    {formatTimestamp(comment.createdAt)}
                  </span>
                </div>
              ))}
            </div>
          )}

          <form onSubmit={handleCommentSubmit} className="flex gap-2">
            <input
              type="text"
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="댓글을 입력하세요"
              className="flex-1 h-11 px-4 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
            />
            <button
              type="submit"
              disabled={commentLoading || !commentInput.trim()}
              className="px-5 h-11 bg-primary text-primary-foreground font-mono text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {commentLoading ? '...' : '작성'}
            </button>
          </form>
        </section>
      </div>

      <ReportModal
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        onSubmit={submitReport}
        submitting={reportSubmitting}
      />
    </div>
  );
}
