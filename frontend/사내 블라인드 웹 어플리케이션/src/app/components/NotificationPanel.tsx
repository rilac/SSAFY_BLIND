import { MessageSquare, ThumbsUp, Bell } from "lucide-react";
import type { Notification } from "../types";
import { formatTimestamp } from "../lib/format";

type IconComponent = React.ComponentType<{ size?: number; className?: string }>;

const typeIcon: Record<Notification["type"], IconComponent> = {
  comment: MessageSquare,
  like: ThumbsUp,
  system: Bell,
};

interface NotificationPanelProps {
  notifications: Notification[];
  onItemClick: (notification: Notification) => void;
  onMarkAllRead: () => void;
}

// 알림 벨 드롭다운 패널
export default function NotificationPanel({
  notifications,
  onItemClick,
  onMarkAllRead,
}: NotificationPanelProps) {
  const hasUnread = notifications.some((n) => !n.isRead);

  return (
    <div className="absolute top-full right-0 mt-2 w-80 bg-card border border-border z-20">
      <div className="flex items-center justify-between px-4 py-3 border-b border-border">
        <span className="text-xs font-mono text-muted-foreground">NOTIFICATIONS</span>
        <button
          onClick={onMarkAllRead}
          disabled={!hasUnread}
          className="text-xs font-mono text-primary hover:opacity-80 transition-opacity disabled:opacity-40 disabled:cursor-not-allowed"
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
            const Icon = typeIcon[n.type];
            return (
              <button
                key={n.id}
                onClick={() => onItemClick(n)}
                className={`w-full flex items-start gap-3 px-4 py-3 text-left border-b border-border last:border-b-0 hover:bg-muted transition-colors ${
                  n.isRead ? "opacity-60" : ""
                }`}
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
            );
          })}
        </div>
      )}
    </div>
  );
}
