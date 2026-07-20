import { useState, useEffect } from 'react';
import { X, KeyRound } from 'lucide-react';
import api from '../api/client';

/**
 * R8: 관리자 2차 인증(step-up) 모달.
 *
 * AdminPage는 페이지 전체를 인증 게이트로 덮지만(콘텐츠 자체가 관리자 전용이라 타당),
 * 게시글 상세처럼 일반 화면에서 관리자 액션만 수행하는 경우에는 화면을 덮으면 안 되므로
 * 같은 인증 절차를 모달로 제공한다.
 *
 * 인증 성공 시 서버가 15분 마커를 부여하므로, 호출부는 onVerified에서 실패했던 액션을 재시도하면 된다.
 */
export default function StepUpDialog({ open, onClose, onVerified }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    if (open) {
      setCode('');
      setError('');
    }
  }, [open]);

  if (!open) return null;

  const handleVerify = async (e) => {
    e.preventDefault();
    if (verifying) return;
    setVerifying(true);
    setError('');
    try {
      await api.post('/admin/verify', { code });
      setCode('');
      onVerified?.();
    } catch (err) {
      // 실패 횟수 초과(429) 등 서버 메시지를 그대로 보여준다.
      setError(err.response?.data?.message || '관리자 인증에 실패했습니다.');
    } finally {
      setVerifying(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
      onClick={onClose}
    >
      <div className="w-full max-w-md bg-card border border-border" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-border">
          <div className="flex items-center gap-2">
            <KeyRound size={18} className="text-primary" />
            <h2 className="text-base font-mono">관리자 2차 인증</h2>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-muted transition-colors">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleVerify} className="p-6 space-y-4">
          <p className="text-sm font-mono text-muted-foreground">
            관리자 작업을 계속하려면 관리자 코드를 입력해주세요. (15분간 유지)
          </p>
          <input
            type="password"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="관리자 코드"
            autoFocus
            className="w-full h-12 px-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary"
          />
          {error && <p className="text-sm text-destructive font-mono">{error}</p>}
          <button
            type="submit"
            disabled={verifying || !code}
            className="w-full h-12 bg-primary text-primary-foreground font-mono text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {verifying ? '인증 중...' : '인증'}
          </button>
        </form>
      </div>
    </div>
  );
}
