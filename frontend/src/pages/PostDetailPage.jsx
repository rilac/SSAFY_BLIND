import { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ThumbsUp, Bookmark, Flag, Eye, Pencil, Trash2, CheckCircle2 } from 'lucide-react'; // CheckCircle2: [FEATURE:qna-accept]
import api from '../api/client';
import ReportModal from '../components/ReportModal';
import ConfirmDialog from '../components/ConfirmDialog';
import AlertDialog from '../components/AlertDialog';
// [FEATURE:markdown-rendering] 마크다운 렌더러는 상세 페이지에서만 필요 → lazy 로드(초기 번들에서 분리)
const Markdown = lazy(() => import('../components/Markdown'));
// [/FEATURE:markdown-rendering]
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
  // 커스텀 모달(M-NEW-4) — 네이티브 alert/confirm 대체
  const [notice, setNotice] = useState(null); // { title?, message }
  const [confirmState, setConfirmState] = useState(null); // { ...props, onConfirm }

  // 동일 id에 대해 GET /posts/{id}를 1회만 보내기 위한 가드.
  // StrictMode(dev)는 effect를 두 번 실행하는데, 서버의 incrementViewCount가
  // 요청마다 1회 일어나므로 가드가 없으면 조회수가 +2 된다. AbortController로는
  // 서버가 이미 증가시킨 뒤일 수 있어 막을 수 없으므로, 두 번째 요청 자체를 차단한다.
  const fetchedIdRef = useRef(null);

  useEffect(() => {
    if (fetchedIdRef.current === id) return;
    fetchedIdRef.current = id;
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
      setNotice({ title: '좋아요 실패', message: '좋아요 처리에 실패했습니다.' });
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
      setNotice({ title: '스크랩 실패', message: '스크랩 처리에 실패했습니다.' });
    } finally {
      setBookmarkLoading(false);
    }
  };

  const submitReport = async (reason) => {
    setReportSubmitting(true);
    try {
      await api.post(`/posts/${id}/report`, { reason });
      setReportOpen(false);
      setNotice({ title: '신고 접수', message: '신고가 접수되었습니다.' });
    } catch {
      setNotice({ title: '신고 실패', message: '신고 처리에 실패했습니다.' });
    } finally {
      setReportSubmitting(false);
    }
  };

  const requestDelete = () => {
    setConfirmState({
      title: '게시글 삭제',
      message: '정말로 이 게시글을 삭제하시겠습니까?',
      confirmLabel: '삭제',
      danger: true,
      onConfirm: performDelete,
    });
  };

  const performDelete = async () => {
    try {
      await api.delete(`/posts/${id}`);
      navigate('/feed');
    } catch {
      setNotice({ title: '삭제 실패', message: '게시글 삭제에 실패했습니다.' });
    }
  };

  // 확인 모달 '확인' 클릭 — 모달을 닫고 저장된 액션 실행
  const handleConfirm = () => {
    const fn = confirmState?.onConfirm;
    setConfirmState(null);
    fn?.();
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
      setNotice({ title: '댓글 작성 실패', message: '댓글 작성에 실패했습니다.' });
    } finally {
      setCommentLoading(false);
    }
  };

  const handleDeleteComment = async (commentId) => {
    try {
      await api.delete(`/posts/${id}/comments/${commentId}`);
      setComments((prev) => prev.filter((c) => c.id !== commentId));
      // [FEATURE:qna-accept] 채택된 답변을 삭제하면 해결 상태도 풀린다(서버와 동기화)
      setPost((prev) => (prev?.acceptedCommentId === commentId ? { ...prev, acceptedCommentId: null } : prev));
      // [/FEATURE:qna-accept]
    } catch {
      setNotice({ title: '댓글 삭제 실패', message: '댓글 삭제에 실패했습니다.' });
    }
  };

  // [FEATURE:qna-accept] 답변 채택 토글 — QUESTION 글 작성자만(버튼은 조건부 노출), 서버가 권한 재검증
  const [acceptLoading, setAcceptLoading] = useState(null); // 토글 중인 commentId
  const handleAcceptToggle = async (commentId) => {
    if (acceptLoading) return;
    setAcceptLoading(commentId);
    try {
      const res = await api.post(`/posts/${id}/comments/${commentId}/accept`);
      setPost((prev) => ({ ...prev, acceptedCommentId: res.data.acceptedCommentId }));
    } catch (e) {
      setNotice({ title: '채택 실패', message: e.response?.data?.message || '답변 채택에 실패했습니다.' });
    } finally {
      setAcceptLoading(null);
    }
  };
  // [/FEATURE:qna-accept]

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
              {/* [FEATURE:qna-accept] 해결됨 배지 (채택된 답변 존재 시) */}
              {post.acceptedCommentId && (
                <span className="flex items-center gap-1 text-green-600 dark:text-green-400">
                  <CheckCircle2 size={12} /> 해결됨
                </span>
              )}
              {/* [/FEATURE:qna-accept] */}
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
                  onClick={requestDelete}
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

          {/* [FEATURE:markdown-rendering] 본문을 마크다운으로 렌더링(기존 평문 whitespace-pre-wrap 대체) */}
          <Suspense fallback={<p className="text-sm leading-relaxed whitespace-pre-wrap mb-6">{post.content}</p>}>
            <Markdown className="text-sm leading-relaxed mb-6">{post.content}</Markdown>
          </Suspense>
          {/* [/FEATURE:markdown-rendering] */}

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
              {comments.map((comment) => {
                // [FEATURE:qna-accept] 채택 상태/권한 (서버가 권한 재검증 — 버튼은 UX용 조건부 노출)
                const accepted = post.acceptedCommentId === comment.id;
                const canAccept = post.isMine && post.category === 'QUESTION';
                // [/FEATURE:qna-accept]
                return (
                  <div
                    key={comment.id}
                    className={`border-b border-border pb-3 last:border-b-0 last:pb-0${
                      accepted ? ' border-l-2 border-l-green-500 pl-3' : '' // [FEATURE:qna-accept]
                    }`}
                  >
                    {/* [FEATURE:qna-accept] 채택된 답변 라벨 */}
                    {accepted && (
                      <div className="flex items-center gap-1 text-xs font-mono text-green-600 dark:text-green-400 mb-1">
                        <CheckCircle2 size={12} /> 채택된 답변
                      </div>
                    )}
                    {/* [/FEATURE:qna-accept] */}
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm flex-1">{comment.content}</p>
                      <div className="flex items-center gap-2 shrink-0">
                        {/* [FEATURE:qna-accept] 질문 작성자만 채택 토글 버튼 */}
                        {canAccept && (
                          <button
                            onClick={() => handleAcceptToggle(comment.id)}
                            disabled={acceptLoading === comment.id}
                            className={`flex items-center gap-1 text-xs font-mono transition-colors disabled:opacity-50 ${
                              accepted
                                ? 'text-green-600 dark:text-green-400'
                                : 'text-muted-foreground hover:text-foreground'
                            }`}
                          >
                            <CheckCircle2 size={12} fill={accepted ? 'currentColor' : 'none'} />
                            {accepted ? '채택 해제' : '채택'}
                          </button>
                        )}
                        {/* [/FEATURE:qna-accept] */}
                        {comment.isMine && (
                          <button
                            onClick={() => handleDeleteComment(comment.id)}
                            className="text-xs font-mono text-destructive hover:opacity-80 transition-opacity"
                          >
                            삭제
                          </button>
                        )}
                      </div>
                    </div>
                    <span className="text-[10px] font-mono text-muted-foreground mt-1 block">
                      {comment.author?.nickname} · {comment.author?.cohort} {comment.author?.campus} ·{' '}
                      {formatTimestamp(comment.createdAt)}
                    </span>
                  </div>
                );
              })}
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

      <ConfirmDialog
        open={!!confirmState}
        title={confirmState?.title}
        message={confirmState?.message}
        confirmLabel={confirmState?.confirmLabel}
        danger={confirmState?.danger}
        onConfirm={handleConfirm}
        onClose={() => setConfirmState(null)}
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
