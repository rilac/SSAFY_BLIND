import { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Bookmark, Flag, Eye, EyeOff, Pencil, Trash2, CheckCircle2, BarChart3, Check, Pin, MessageSquare, FileX2 } from 'lucide-react'; // CheckCircle2: [FEATURE:qna-accept] · BarChart3/Check: [FEATURE:poll] · Pin: [FEATURE:pinned-posts] · EyeOff: 관리자 숨김 · MessageSquare/FileX2: 빈/오류 상태 글리프
import api from '../api/client';
import { useAuth } from '../context/AuthContext'; // [FEATURE:pinned-posts] 관리자 여부 판별
import ReportModal from '../components/ReportModal';
import ConfirmDialog from '../components/ConfirmDialog';
import AlertDialog from '../components/AlertDialog';
// [FEATURE:markdown-rendering] 마크다운 렌더러는 상세 페이지에서만 필요 → lazy 로드(초기 번들에서 분리)
const Markdown = lazy(() => import('../components/Markdown'));
// [/FEATURE:markdown-rendering]
import { formatTimestamp, formatNumber } from '../lib/format';
import { categoryLabel } from '../lib/categories';

// [FEATURE:reactions] 반응 종류 메타(이모지+라벨). 키는 서버 ReactionType과 일치.
const REACTION_META = {
  LIKE: { emoji: '👍', label: '좋아요' },
  HELPFUL: { emoji: '🙏', label: '도움돼요' },
  INFORMATIVE: { emoji: '💡', label: '정보' },
  EMPATHY: { emoji: '🤝', label: '공감' },
};

// 게시글 상세 — 가명(닉네임·기수·지역) 노출. 카테고리 배지 + 좋아요/스크랩/신고 + 댓글.
export default function PostDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const { user } = useAuth(); // [FEATURE:pinned-posts]
  const isAdmin = user?.role === 'ADMIN'; // [FEATURE:pinned-posts]

  const [post, setPost] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentInput, setCommentInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reactLoading, setReactLoading] = useState(false); // [FEATURE:reactions]
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

  // [FEATURE:reactions] 반응 토글 — 같은 종류 재클릭=취소, 다른 종류=변경. 서버가 집계 반환.
  const handleReact = async (type) => {
    if (reactLoading) return;
    setReactLoading(true);
    try {
      const res = await api.post(`/posts/${id}/reactions`, { type });
      setPost((prev) => ({ ...prev, reactions: res.data }));
    } catch {
      setNotice({ title: '반응 실패', message: '반응 처리에 실패했습니다.' });
    } finally {
      setReactLoading(false);
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

  // 댓글/대댓글 삭제 확인 — 게시글 삭제와 동일하게 ConfirmDialog를 거친다(즉시 삭제 방지)
  const requestDeleteComment = (commentId) => {
    setConfirmState({
      title: '댓글 삭제',
      message: '정말로 이 댓글을 삭제하시겠습니까?',
      confirmLabel: '삭제',
      danger: true,
      onConfirm: () => handleDeleteComment(commentId),
    });
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

  // [FEATURE:nested-comments] 답글 입력 상태 + 작성 핸들러 (replyingTo = 답글 대상 부모 댓글 id)
  const [replyingTo, setReplyingTo] = useState(null);
  const [replyInput, setReplyInput] = useState('');
  const [replyLoading, setReplyLoading] = useState(false);
  const handleReplySubmit = async (parentId) => {
    const trimmed = replyInput.trim();
    if (!trimmed || replyLoading) return;
    setReplyLoading(true);
    try {
      const res = await api.post(`/posts/${id}/comments`, { content: trimmed, parentId });
      setComments((prev) => [...prev, res.data]);
      setReplyInput('');
      setReplyingTo(null);
    } catch (e) {
      setNotice({ title: '답글 작성 실패', message: e.response?.data?.message || '답글 작성에 실패했습니다.' });
    } finally {
      setReplyLoading(false);
    }
  };
  // [/FEATURE:nested-comments]

  // [FEATURE:poll] 익명 투표 — 토글(같은 보기 재클릭=취소). 서버가 집계만 반환(누가 골랐는지는 미노출).
  const [voteLoading, setVoteLoading] = useState(false);
  const handleVote = async (optionId) => {
    if (voteLoading) return;
    setVoteLoading(true);
    try {
      const res = await api.post(`/posts/${id}/poll/vote`, { optionId });
      setPost((prev) => ({ ...prev, poll: res.data }));
    } catch (e) {
      setNotice({ title: '투표 실패', message: e.response?.data?.message || '투표에 실패했습니다.' });
    } finally {
      setVoteLoading(false);
    }
  };
  // [/FEATURE:poll]

  // [FEATURE:pinned-posts] 관리자 공지 고정 토글 — 서버가 새 상태 반환, 로컬 post에 반영.
  const [pinLoading, setPinLoading] = useState(false);
  const handleTogglePin = async () => {
    if (pinLoading) return;
    setPinLoading(true);
    try {
      const res = await api.post(`/admin/posts/${id}/pin`);
      setPost((prev) => ({ ...prev, pinned: res.data.pinned }));
    } catch {
      setNotice({ title: '고정 실패', message: '공지 고정에 실패했습니다.' });
    } finally {
      setPinLoading(false);
    }
  };
  // [/FEATURE:pinned-posts]

  // [FEATURE:admin-moderation] 관리자 숨김/숨김 해제 토글 — post.hidden에 따라 hide/restore 호출, 서버 반영 후 로컬 post 갱신.
  const [hideLoading, setHideLoading] = useState(false);
  const handleToggleHide = async () => {
    if (hideLoading) return;
    setHideLoading(true);
    try {
      if (post.hidden) {
        await api.post(`/admin/posts/${id}/restore`);
        setPost((prev) => ({ ...prev, hidden: false }));
      } else {
        await api.post(`/admin/posts/${id}/hide`);
        setPost((prev) => ({ ...prev, hidden: true }));
      }
    } catch {
      setNotice({ title: '처리 실패', message: '숨김 처리에 실패했습니다.' });
    } finally {
      setHideLoading(false);
    }
  };
  // [/FEATURE:admin-moderation]

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
            <FileX2 size={32} className="mx-auto mb-3 text-muted-foreground opacity-50" />
            <p className="text-sm font-mono text-muted-foreground">{error || '게시글을 찾을 수 없습니다.'}</p>
            <p className="text-xs font-mono text-muted-foreground opacity-70 mt-1.5">삭제되었거나 주소가 올바르지 않을 수 있어요</p>
          </div>
        </div>
      </div>
    );
  }

  const isEdited = post.createdAt !== post.updatedAt;

  // [FEATURE:nested-comments] 평면 댓글 목록(서버, createdAt asc)을 최상위/답글(1-depth)로 그룹핑
  const topLevelComments = comments.filter((c) => !c.parentId);
  const repliesByParent = comments.reduce((acc, c) => {
    if (c.parentId) (acc[c.parentId] = acc[c.parentId] || []).push(c);
    return acc;
  }, {});

  // 댓글 1건 렌더 — 최상위/답글 공용. 답글(isReply)은 들여쓰기, 채택·답글 버튼 없음.
  const renderComment = (comment, isReply) => {
    const accepted = !isReply && post.acceptedCommentId === comment.id; // [FEATURE:qna-accept] 답글은 채택 대상 아님
    const canAccept = !isReply && post.isMine && post.category === 'QUESTION'; // [FEATURE:qna-accept]
    return (
      <div
        key={comment.id}
        className={
          isReply
            ? 'mt-3 ml-6 pl-3 border-l border-border'
            : accepted
              ? 'border-l-2 border-l-green-500 pl-3'
              : ''
        }
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
          <div className="flex items-center gap-1 shrink-0">
            {/* [FEATURE:qna-accept] 질문 작성자만 채택 토글(최상위 답변 한정) */}
            {canAccept && (
              <button
                onClick={() => handleAcceptToggle(comment.id)}
                disabled={acceptLoading === comment.id}
                className={`flex items-center gap-1 text-sm font-mono px-2 py-1.5 hover:bg-muted transition-colors disabled:opacity-50 ${
                  accepted ? 'text-green-600 dark:text-green-400' : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <CheckCircle2 size={12} fill={accepted ? 'currentColor' : 'none'} />
                {accepted ? '채택 해제' : '채택'}
              </button>
            )}
            {/* [/FEATURE:qna-accept] */}
            {!isReply && (
              <button
                onClick={() => {
                  setReplyingTo(replyingTo === comment.id ? null : comment.id);
                  setReplyInput('');
                }}
                className="text-sm font-mono px-2 py-1.5 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
              >
                답글
              </button>
            )}
            {/* [FEATURE:admin-moderation] 삭제 — 본인 댓글 또는 관리자(타인 댓글 모더레이션). 백엔드 deleteComment가 ADMIN 허용 */}
            {(comment.isMine || isAdmin) && (
              <button
                onClick={() => requestDeleteComment(comment.id)}
                className="text-sm font-mono px-2 py-1.5 text-destructive hover:bg-muted transition-colors"
              >
                삭제
              </button>
            )}
            {/* [/FEATURE:admin-moderation] */}
          </div>
        </div>
        <span className="text-xs font-mono text-muted-foreground mt-1 block">
          {/* [FEATURE:op-alias] 닉네임 대신 글 단위 별칭(글쓴이/익명N). 글쓴이(OP)는 강조. */}
          <span className={comment.isAuthor ? 'text-primary font-semibold' : undefined}>{comment.alias}</span>
          {/* [/FEATURE:op-alias] */}
          {' · '}{comment.author?.cohort} {comment.author?.campus} ·{' '}
          {formatTimestamp(comment.createdAt)}
        </span>
      </div>
    );
  };
  // [/FEATURE:nested-comments]

  return (
    <div className="min-h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-6">
        {backBtn}

        {/* 본문 */}
        <article className="border border-border bg-card p-6 mb-4">
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-2 text-sm font-mono flex-wrap">
              {/* [FEATURE:pinned-posts] 공지 고정 배지 */}
              {post.pinned && (
                <span className="flex items-center gap-1 px-1.5 py-0.5 bg-primary text-primary-foreground text-[10px] font-bold leading-none">
                  <Pin size={10} /> 공지
                </span>
              )}
              {/* [/FEATURE:pinned-posts] */}
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
            {(post.isMine || isAdmin) && (
              <div className="flex items-center gap-2 shrink-0">
                {/* [FEATURE:pinned-posts] 관리자 공지 고정/해제 토글 — 본인 글 여부와 무관, 관리자에게만 노출 */}
                {isAdmin && (
                  <button
                    onClick={handleTogglePin}
                    disabled={pinLoading}
                    className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border transition-colors disabled:opacity-50 ${
                      post.pinned
                        ? 'border-primary text-primary'
                        : 'border-border hover:border-primary'
                    }`}
                  >
                    <Pin size={12} fill={post.pinned ? 'currentColor' : 'none'} />
                    {post.pinned ? '고정 해제' : '공지 고정'}
                  </button>
                )}
                {/* [/FEATURE:pinned-posts] */}
                {/* [FEATURE:admin-moderation] 관리자 숨김/숨김 해제 — 본인 글 여부와 무관, 관리자에게만 노출 */}
                {isAdmin && (
                  <button
                    onClick={handleToggleHide}
                    disabled={hideLoading}
                    className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border transition-colors disabled:opacity-50 ${
                      post.hidden
                        ? 'border-primary text-primary'
                        : 'border-border hover:border-primary'
                    }`}
                  >
                    <EyeOff size={12} />
                    {post.hidden ? '숨김 해제' : '숨김'}
                  </button>
                )}
                {/* [/FEATURE:admin-moderation] */}
                {post.isMine && (
                  <button
                    onClick={() => navigate(`/posts/${id}/edit`)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-border hover:border-primary transition-colors"
                  >
                    <Pencil size={12} />
                    수정
                  </button>
                )}
                {/* [FEATURE:admin-moderation] 삭제 — 본인 또는 관리자(타인 글 모더레이션). 백엔드 deletePost가 ADMIN 허용 */}
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

          {/* [FEATURE:admin-moderation] 관리자에게만: 숨김 상태 안내(일반 사용자에겐 이 글 자체가 비노출) */}
          {isAdmin && post.hidden && (
            <div className="flex items-center gap-2 mb-3 px-3 py-2 border border-destructive text-destructive text-xs font-mono">
              <EyeOff size={14} />
              이 글은 숨김 상태입니다 — 일반 사용자에게 보이지 않습니다.
            </div>
          )}
          {/* [/FEATURE:admin-moderation] */}

          <h1 className="text-xl font-semibold mb-3">{post.title}</h1>

          <div className="flex items-center gap-3 text-sm font-mono text-muted-foreground mb-5">
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

          {/* [FEATURE:poll] 익명 투표 — 보기별 막대(퍼센트)·집계, 클릭으로 투표/변경/취소 */}
          {post.poll && (
            <div className="mb-6 border border-border p-4">
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm font-mono flex items-center gap-1.5">
                  <BarChart3 size={14} /> 투표
                </span>
                <span className="text-xs font-mono text-muted-foreground">{post.poll.totalVotes}표</span>
              </div>
              <div className="space-y-2">
                {post.poll.options.map((opt) => {
                  const pct = post.poll.totalVotes > 0 ? Math.round((opt.voteCount / post.poll.totalVotes) * 100) : 0;
                  const mine = post.poll.myOptionId === opt.id;
                  return (
                    <button
                      key={opt.id}
                      onClick={() => handleVote(opt.id)}
                      disabled={voteLoading}
                      className={`relative w-full text-left border px-3 py-2 overflow-hidden transition-colors disabled:opacity-60 ${
                        mine ? 'border-primary' : 'border-border hover:border-primary'
                      }`}
                    >
                      <div
                        className={`absolute inset-y-0 left-0 ${mine ? 'bg-primary/20' : 'bg-muted'}`}
                        style={{ width: `${pct}%` }}
                      />
                      <div className="relative flex items-center justify-between text-sm font-mono">
                        <span className="flex items-center gap-1.5">
                          {mine && <Check size={12} className="text-primary" />}
                          {opt.content}
                        </span>
                        <span className="text-muted-foreground text-xs">{pct}% · {opt.voteCount}</span>
                      </div>
                    </button>
                  );
                })}
              </div>
              <p className="text-[10px] font-mono text-muted-foreground mt-2">익명 투표 · 보기를 다시 누르면 취소</p>
            </div>
          )}
          {/* [/FEATURE:poll] */}

          {/* [FEATURE:reactions] 반응 바(좋아요/도움돼요/정보/공감) — 단일 좋아요 버튼 대체. 같은 종류 재클릭=취소 */}
          <div className="flex flex-wrap items-center gap-2 pt-4 border-t border-border">
            {post.reactions?.reactions.map((r) => {
              const meta = REACTION_META[r.type];
              if (!meta) return null;
              const mine = post.reactions.myReaction === r.type;
              return (
                <button
                  key={r.type}
                  onClick={() => handleReact(r.type)}
                  disabled={reactLoading}
                  title={meta.label}
                  className={`flex items-center gap-1.5 px-3 py-2 text-sm font-mono border transition-colors disabled:opacity-50 ${
                    mine ? 'bg-primary text-primary-foreground border-primary' : 'border-border hover:border-primary'
                  }`}
                >
                  <span>{meta.emoji}</span>
                  <span>{meta.label}</span>
                  {r.count > 0 && <span className="opacity-80">{r.count}</span>}
                </button>
              );
            })}
            {/* [/FEATURE:reactions] */}

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
          <h2 className="text-base font-mono font-semibold tracking-tight mb-4">댓글 {comments.length}개</h2>

          {comments.length === 0 ? (
            <div className="text-center py-6 mb-4">
              <MessageSquare size={24} className="mx-auto mb-2 text-muted-foreground opacity-50" />
              <p className="text-xs font-mono text-muted-foreground">아직 댓글이 없습니다.</p>
              <p className="text-[10px] font-mono text-muted-foreground opacity-70 mt-1">가장 먼저 댓글을 남겨보세요</p>
            </div>
          ) : (
            <div className="space-y-3 mb-4">
              {/* [FEATURE:nested-comments] 최상위 댓글 + 답글(들여쓰기) + 답글 입력 (평면 map → 그룹핑 렌더로 대체) */}
              {topLevelComments.map((comment) => (
                <div key={comment.id} className="border-b border-border pb-3 last:border-b-0 last:pb-0">
                  {renderComment(comment, false)}
                  {(repliesByParent[comment.id] || []).map((reply) => renderComment(reply, true))}
                  {replyingTo === comment.id && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        handleReplySubmit(comment.id);
                      }}
                      className="flex gap-2 mt-3 ml-6"
                    >
                      <input
                        type="text"
                        value={replyInput}
                        onChange={(e) => setReplyInput(e.target.value)}
                        placeholder="답글을 입력하세요"
                        autoFocus
                        className="flex-1 h-9 px-3 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
                      />
                      <button
                        type="submit"
                        disabled={replyLoading || !replyInput.trim()}
                        className="px-4 h-9 bg-primary text-primary-foreground font-mono text-xs hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {replyLoading ? '...' : '답글'}
                      </button>
                    </form>
                  )}
                </div>
              ))}
              {/* [/FEATURE:nested-comments] */}
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
