import { useState, useEffect } from 'react';
import { X } from 'lucide-react';

// 신고 사유 — 백엔드 ReportReason enum과 1:1
const REASONS = [
  { value: 'GAMBLING_OR_ADULT', label: '사행성·선정성' },
  { value: 'OFF_TOPIC', label: '블라인드 취지에 맞지 않음' },
  { value: 'PERSONAL_ATTACK', label: '인신공격·과도한 비방' },
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'ETC', label: '기타' },
];

// 신고 사유 선택 모달 — 즉시 접수가 아니라 사유를 고른 뒤 제출
export default function ReportModal({ open, onClose, onSubmit, submitting }) {
  const [reason, setReason] = useState('');

  useEffect(() => {
    if (open) setReason('');
  }, [open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
      onClick={onClose}
    >
      <div className="w-full max-w-md bg-card border border-border" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-border">
          <h2 className="text-base font-mono">게시글 신고</h2>
          <button onClick={onClose} className="p-1 hover:bg-muted transition-colors">
            <X size={18} />
          </button>
        </div>

        <div className="p-6 space-y-2">
          <p className="text-sm font-mono text-muted-foreground mb-3">신고 사유를 선택해주세요</p>
          {REASONS.map((r) => (
            <label
              key={r.value}
              className={`flex items-center gap-3 px-4 py-3 border cursor-pointer transition-colors ${
                reason === r.value ? 'border-primary bg-muted' : 'border-border hover:border-primary'
              }`}
            >
              <input
                type="radio"
                name="report-reason"
                value={r.value}
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
              />
              <span className="text-sm">{r.label}</span>
            </label>
          ))}
        </div>

        <div className="flex gap-3 px-6 pb-6">
          <button
            onClick={() => reason && onSubmit(reason)}
            disabled={!reason || submitting}
            className="flex-1 h-11 bg-destructive text-destructive-foreground font-mono text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? '접수 중...' : '신고하기'}
          </button>
          <button
            onClick={onClose}
            className="px-6 h-11 border border-border font-mono text-sm hover:border-primary transition-colors"
          >
            취소
          </button>
        </div>
      </div>
    </div>
  );
}
