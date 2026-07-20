import { MessageSquare, ThumbsUp, Bell, X } from 'lucide-react';
import { formatTimestamp } from '../lib/format';

const typeIcon = {
  COMMENT: MessageSquare,
  LIKE: ThumbsUp,
  SYSTEM: Bell,
};

// 알림 드롭다운 패널
export default function NotificationPanel({ notifications, onItemClick, onMarkAllRead, onDelete }) {
  const hasUnread = notifications.some((n) => !n.isRead);

  return (
    <div className="absolute top-full right-0 mt-2 w-[calc(100vw-1.5rem)] max-w-xs sm:w-80 bg-card border border-border z-20">
      <div className="flex items-center justify-between px-4 py-2 border-b border-border">
        <span className="text-xs font-mono text-muted-foreground">NOTIFICATIONS</span>
        <button
          onClick={onMarkAllRead}
          disabled={!hasUnread}
          className="px-2 py-2 -mr-2 min-h-[44px] flex items-center text-xs font-mono text-primary hover:opacity-80 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
        >
          모두 읽음
        </button>
      </div>

      {notifications.length === 0 ? (
        <p className="px-4 py-6 text-xs font-mono text-muted-foreground text-center">
          알림이 없습니다
        </p>
      ) : (
        <div className="max-h-96 overflow-y-auto">
          {notifications.map((n) => {
            const Icon = typeIcon[n.type] || Bell;
            return (
              // 행 안에 삭제 버튼(button)이 들어가므로 바깥은 div — button 중첩(잘못된 HTML) 방지.
              <div
                key={n.id}
                className={`flex items-stretch border-b border-border last:border-b-0 hover:bg-muted transition-colors ${
                  n.isRead ? 'opacity-60' : ''
                }`}
              >
                <button
                  onClick={() => onItemClick(n)}
                  className="flex-1 min-w-0 flex items-start gap-3 px-4 py-3 text-left"
                >
                  <Icon size={16} className="text-primary mt-0.5 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm leading-snug">{n.message}</p>
                    <span className="text-[10px] font-mono text-muted-foreground mt-1 block">
                      {formatTimestamp(n.createdAt)}
                    </span>
                  </div>
                  {!n.isRead && (
                    <span className="w-2 h-2 bg-destructive rounded-full mt-1.5 shrink-0" />
                  )}
                </button>
                {/* 알림 삭제(숨기기) — 목록에서 즉시 제거. 부모 클릭(상세 이동)과 겹치지 않게 형제로 분리. */}
                <button
                  onClick={() => onDelete(n.id)}
                  className="px-3 shrink-0 flex items-center text-muted-foreground hover:text-destructive transition-colors"
                  aria-label="알림 삭제"
                  title="삭제"
                >
                  <X size={14} />
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
