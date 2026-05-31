import { Menu, X, Search, Bell, Plus, Moon, Sun } from "lucide-react";
import type { Notification } from "../types";
import NotificationPanel from "./NotificationPanel";

interface TopBarProps {
  sidebarOpen: boolean;
  searchQuery: string;
  darkMode: boolean;
  notifications: Notification[];
  notificationsOpen: boolean;
  onToggleSidebar: () => void;
  onSearchChange: (query: string) => void;
  onToggleDarkMode: () => void;
  onNewPost: () => void;
  onToggleNotifications: () => void;
  onNotificationClick: (notification: Notification) => void;
  onMarkAllRead: () => void;
}

// 상단 바: 사이드바 토글 / 검색 / 다크모드 / 새 글 / 알림
export default function TopBar({
  sidebarOpen,
  searchQuery,
  darkMode,
  notifications,
  notificationsOpen,
  onToggleSidebar,
  onSearchChange,
  onToggleDarkMode,
  onNewPost,
  onToggleNotifications,
  onNotificationClick,
  onMarkAllRead,
}: TopBarProps) {
  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <header className="h-16 border-b border-border bg-card flex items-center justify-between px-6">
      <div className="flex items-center gap-4">
        <button
          onClick={onToggleSidebar}
          className="p-2 hover:bg-muted transition-colors border border-transparent hover:border-border"
        >
          {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
        </button>

        <div className="relative">
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="SEARCH_POSTS..."
            className="w-96 h-10 pl-10 pr-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
          />
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={onToggleDarkMode}
          className="p-2 hover:bg-muted transition-colors border border-transparent hover:border-border"
        >
          {darkMode ? <Sun size={20} /> : <Moon size={20} />}
        </button>

        <button
          onClick={onNewPost}
          className="px-4 h-10 bg-primary text-primary-foreground font-mono text-sm flex items-center gap-2 hover:opacity-90 transition-opacity"
        >
          <Plus size={16} />
          새 글 작성
        </button>

        <div className="relative">
          <button
            onClick={onToggleNotifications}
            className="relative p-2 hover:bg-muted transition-colors border border-transparent hover:border-border"
          >
            <Bell size={20} />
            {unreadCount > 0 && (
              <span className="absolute top-1 right-1 w-2 h-2 bg-destructive rounded-full" />
            )}
          </button>

          {notificationsOpen && (
            <NotificationPanel
              notifications={notifications}
              onItemClick={onNotificationClick}
              onMarkAllRead={onMarkAllRead}
            />
          )}
        </div>
      </div>
    </header>
  );
}
