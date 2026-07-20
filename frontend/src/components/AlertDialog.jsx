import { X } from 'lucide-react';

// 단일 확인 버튼 알림 다이얼로그 — 네이티브 alert 대신 앱 디자인에 맞춘 커스텀 모달.
// ReportModal 마크업을 미러링한다(오버레이 / bg-card 카드 / mono 폰트 / X 닫기 / 바깥 클릭 닫기).
export default function AlertDialog({ open, title = '알림', message, confirmLabel = '확인', onClose }) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 sm:p-6"
      onClick={onClose}
    >
      <div className="w-full max-w-md max-h-[90dvh] overflow-y-auto bg-card border border-border" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-6 py-4 border-b border-border">
          <h2 className="text-base font-mono">{title}</h2>
          <button onClick={onClose} className="-mr-2 p-2 hover:bg-muted transition-colors">
            <X size={18} />
          </button>
        </div>

        <div className="p-4 sm:p-6">
          <p className="text-sm font-mono text-muted-foreground whitespace-pre-wrap">{message}</p>
        </div>

        <div className="px-6 pb-6">
          <button
            onClick={onClose}
            className="w-full h-11 bg-primary text-primary-foreground font-mono text-sm hover:opacity-90 transition-opacity"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
