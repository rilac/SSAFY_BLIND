import { useState, useEffect } from 'react';
import { X } from 'lucide-react';

// 신고 사유 — 백엔드 ReportReason enum과 1:1
const REASONS = [
  { value: 'GAMBLING_OR_ADULT', label: '사행성·선정성' },
  { value: 'OFF_TOPIC', label: '커뮤니티 취지에 맞지 않음' },
  { value: 'PERSONAL_ATTACK', label: '인신공격·과도한 비방' },
  { value: 'SPAM', label: '스팸·광고' },
  { value: 'ETC', label: '기타' },
];

// [FEATURE:report-detail] 기타 상세 사유 글자수 상한 — 백엔드 @Size(200)·DB varchar(200)와 일치.
const DETAIL_MAX = 200;

// 신고 사유 선택 모달 — 즉시 접수가 아니라 사유를 고른 뒤 제출
export default function ReportModal({ open, onClose, onSubmit, submitting }) {
  const [reason, setReason] = useState('');
  const [detail, setDetail] = useState(''); // [FEATURE:report-detail] 기타 직접 입력

  useEffect(() => {
    if (open) {
      setReason('');
      setDetail(''); // [FEATURE:report-detail]
    }
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

          {/* [FEATURE:report-detail] 기타 선택 시 직접 사유 입력(선택) + 글자수 카운터 */}
          {reason === 'ETC' && (
            <div className="pt-1">
              <div className="flex items-baseline justify-between mb-1.5">
                <label className="text-xs font-mono text-muted-foreground">상세 사유 (선택)</label>
                <span
                  className={`text-xs font-mono ${
                    detail.length >= DETAIL_MAX * 0.9 ? 'text-destructive' : 'text-muted-foreground'
                  }`}
                >
                  {detail.length} / {DETAIL_MAX}
                </span>
              </div>
              <textarea
                value={detail}
                onChange={(e) => setDetail(e.target.value)}
                maxLength={DETAIL_MAX}
                rows={3}
                placeholder="어떤 점이 문제인지 간단히 적어주세요"
                className="w-full px-3 py-2 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors resize-none"
              />
            </div>
          )}
          {/* [/FEATURE:report-detail] */}
        </div>

        <div className="flex gap-3 px-6 pb-6">
          <button
            onClick={() => reason && onSubmit(reason, reason === 'ETC' ? detail.trim() : undefined)}
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
