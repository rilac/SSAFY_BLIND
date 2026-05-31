import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Eye, Trash2, RotateCcw, Shield } from 'lucide-react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { formatTimestamp } from '../lib/format';
import AlertDialog from '../components/AlertDialog';
import ConfirmDialog from '../components/ConfirmDialog';

const REASON_LABELS = {
  GAMBLING_OR_ADULT: '사행성·선정성',
  OFF_TOPIC: '취지 부적합',
  PERSONAL_ATTACK: '인신공격',
  SPAM: '스팸·광고',
  ETC: '기타',
};

// 관리자 페이지 — 신고/숨김 게시물 검수 + 건의함. ROLE_ADMIN만 접근.
export default function AdminPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [reported, setReported] = useState([]);
  const [feedback, setFeedback] = useState([]);
  const [loading, setLoading] = useState(true);
  // 커스텀 모달(M-NEW-4): 네이티브 alert/confirm 대체
  const [notice, setNotice] = useState(null); // { title?, message }
  const [confirmState, setConfirmState] = useState(null); // { ...props, onConfirm }

  useEffect(() => {
    if (!user) return;
    if (user.role !== 'ADMIN') {
      navigate('/feed', { replace: true });
      return;
    }
    // 신고 목록과 건의함을 독립적으로 처리한다(allSettled).
    // 한쪽 요청이 실패해도(예: 신고-게시글 데이터 정합 이슈) 다른 쪽은 정상 표시.
    const fetchAll = async () => {
      const [reportedResult, feedbackResult] = await Promise.allSettled([
        api.get('/admin/posts/reported'),
        api.get('/admin/feedback'),
      ]);
      if (reportedResult.status === 'fulfilled') {
        setReported(reportedResult.value.data);
      } else {
        console.error('신고 게시물 조회 실패', reportedResult.reason);
      }
      if (feedbackResult.status === 'fulfilled') {
        setFeedback(feedbackResult.value.data);
      } else {
        console.error('건의함 조회 실패', feedbackResult.reason);
      }
      setLoading(false);
    };
    fetchAll();
  }, [user, navigate]);

  const handleRestore = async (id) => {
    try {
      await api.post(`/admin/posts/${id}/restore`);
      // 복원 시 백엔드가 reviewed=true로 표시(재자동숨김 제외) — 로컬 상태도 함께 반영
      setReported((prev) =>
        prev.map((p) => (p.postId === id ? { ...p, hidden: false, reviewed: true } : p))
      );
    } catch {
      setNotice({ title: '복원 실패', message: '복원에 실패했습니다.' });
    }
  };

  const requestDelete = (id) => {
    setConfirmState({
      title: '영구 삭제',
      message: '이 게시글을 영구 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.',
      confirmLabel: '삭제',
      danger: true,
      onConfirm: () => performDelete(id),
    });
  };

  const performDelete = async (id) => {
    try {
      await api.delete(`/posts/${id}`);
      setReported((prev) => prev.filter((p) => p.postId !== id));
    } catch {
      setNotice({ title: '삭제 실패', message: '삭제에 실패했습니다.' });
    }
  };

  // 확인 모달 '확인' 클릭 — 모달을 닫고 저장된 액션 실행
  const handleConfirm = () => {
    const fn = confirmState?.onConfirm;
    setConfirmState(null);
    fn?.();
  };

  return (
    <div className="min-h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-4xl mx-auto p-6">
        <button
          onClick={() => navigate('/feed')}
          className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          피드로 돌아가기
        </button>

        <div className="flex items-center gap-2 mb-8">
          <Shield size={22} className="text-primary" />
          <h1 className="text-2xl font-mono">관리자</h1>
        </div>

        {loading ? (
          <p className="text-sm font-mono text-muted-foreground">불러오는 중...</p>
        ) : (
          <div className="space-y-10">
            {/* 신고/숨김 게시물 */}
            <section>
              <h2 className="text-lg font-mono mb-4">신고된 게시물 ({reported.length})</h2>
              {reported.length === 0 ? (
                <div className="border border-border bg-card p-8 text-center text-sm font-mono text-muted-foreground">
                  신고된 게시물이 없습니다
                </div>
              ) : (
                <div className="space-y-3">
                  {reported.map((p) => (
                    <div key={p.postId} className="border border-border bg-card p-5">
                      <div className="flex items-start justify-between gap-4 mb-2">
                        <h3 className="text-base font-semibold flex-1">{p.title}</h3>
                        {p.hidden ? (
                          <span className="text-[10px] font-mono px-2 py-1 border border-destructive text-destructive shrink-0">
                            숨김
                          </span>
                        ) : p.reviewed ? (
                          <span className="text-[10px] font-mono px-2 py-1 border border-primary text-primary shrink-0">
                            검토완료
                          </span>
                        ) : null}
                      </div>
                      <div className="flex items-center gap-3 text-xs font-mono text-muted-foreground mb-3 flex-wrap">
                        <span className="text-destructive">신고 {p.reportCount}건</span>
                        {Object.entries(p.reasonCounts || {}).map(([reason, count]) => (
                          <span key={reason}>
                            {REASON_LABELS[reason] || reason} {count}
                          </span>
                        ))}
                        <span className="opacity-50">•</span>
                        <span>{formatTimestamp(p.createdAt)}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => navigate(`/posts/${p.postId}`)}
                          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-border hover:border-primary transition-colors"
                        >
                          <Eye size={12} />
                          상세보기
                        </button>
                        {p.hidden && (
                          <button
                            onClick={() => handleRestore(p.postId)}
                            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-border hover:border-primary transition-colors"
                          >
                            <RotateCcw size={12} />
                            숨김 해제
                          </button>
                        )}
                        <button
                          onClick={() => requestDelete(p.postId)}
                          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-destructive text-destructive hover:opacity-80 transition-opacity"
                        >
                          <Trash2 size={12} />
                          삭제
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* 건의함 */}
            <section>
              <h2 className="text-lg font-mono mb-4">건의함 ({feedback.length})</h2>
              {feedback.length === 0 ? (
                <div className="border border-border bg-card p-8 text-center text-sm font-mono text-muted-foreground">
                  접수된 건의가 없습니다
                </div>
              ) : (
                <div className="space-y-3">
                  {feedback.map((f) => (
                    <div key={f.id} className="border border-border bg-card p-5">
                      <h3 className="text-base font-semibold mb-2">{f.title}</h3>
                      <p className="text-sm whitespace-pre-wrap mb-3">{f.content}</p>
                      <div className="text-xs font-mono text-muted-foreground">
                        {f.author?.nickname} · {f.author?.cohort} {f.author?.campus} ·{' '}
                        {formatTimestamp(f.createdAt)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </div>

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
