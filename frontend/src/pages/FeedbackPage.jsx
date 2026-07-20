import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Inbox } from 'lucide-react';
import api from '../api/client';
import AlertDialog from '../components/AlertDialog';
import { submitOnEnter } from '../lib/formEnter';

// 건의함 — 관리자에게만 전달되는 비밀 피드백/서비스 제안
export default function FeedbackPage() {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  // 전송 완료 알림(M-NEW-4) — 확인 시 피드로 이동
  const [submitted, setSubmitted] = useState(false);

  const isValid = title.trim() && content.trim();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isValid) return;
    setError('');
    setSubmitting(true);
    try {
      await api.post('/feedback', { title: title.trim(), content: content.trim() });
      // 전송 성공 — submitting을 유지(재제출 방지)한 채 완료 모달을 띄우고, 확인 시 이동.
      setSubmitted(true);
    } catch (err) {
      setError(err.response?.data?.message || '전송에 실패했습니다.');
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-4 sm:p-6">
        <button
          onClick={() => navigate('/feed')}
          className="flex items-center gap-2 -ml-2 px-2 py-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          피드로 돌아가기
        </button>

        <div className="flex items-center gap-2 mb-2">
          <Inbox size={22} className="text-primary" />
          <h1 className="text-2xl font-mono">건의함</h1>
        </div>
        <p className="text-sm font-mono text-muted-foreground mb-6">
          관리자에게만 전달되는 비밀 게시물입니다. 피드백·서비스 제안을 남겨주세요.
        </p>

        <form onSubmit={handleSubmit} onKeyDown={submitOnEnter} className="border border-border bg-card p-4 sm:p-8 space-y-6">
          <div>
            <label className="block text-sm font-mono mb-2">제목</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="제목을 입력하세요"
              className="w-full h-12 px-4 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-mono mb-2">내용</label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="건의·제안 내용을 입력하세요"
              rows={10}
              className="w-full px-4 py-3 bg-input-background border border-border text-sm leading-relaxed placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors resize-none"
              required
            />
          </div>

          {error && <p className="text-sm text-destructive text-center font-mono">{error}</p>}

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={!isValid || submitting}
              className="flex-1 h-12 bg-primary text-primary-foreground font-mono text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {submitting ? '전송 중...' : '제출하기'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/feed')}
              className="px-6 h-12 border border-border font-mono text-sm hover:border-primary transition-colors"
            >
              취소
            </button>
          </div>
        </form>
      </div>

      <AlertDialog
        open={submitted}
        title="전송 완료"
        message="소중한 의견 감사합니다. 관리자에게 전달되었습니다."
        onClose={() => navigate('/feed')}
      />
    </div>
  );
}
