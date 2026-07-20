import { Menu, X, Search, Bell, Plus, Moon, Sun, LogIn } from 'lucide-react';
import NotificationPanel from './NotificationPanel';

// 상단 바: 사이드바 토글 / 검색 / 다크모드 / 새 글 / 알림
export default function TopBar({
  user,
  sidebarOpen,
  searchValue,
  darkMode,
  notifications,
  notificationsOpen,
  sortBy,
  onToggleSidebar,
  onSearchChange,
  onToggleDarkMode,
  onNewPost,
  onToggleNotifications,
  onNotificationClick,
  onMarkAllRead,
  onDeleteNotification,
  onSortChange,
}) {
  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <header className="h-16 shrink-0 border-b border-border bg-card flex items-center justify-between gap-2 px-3 sm:px-6">
      <div className="flex items-center gap-2 sm:gap-4 flex-1 min-w-0">
        <button
          onClick={onToggleSidebar}
          className="p-2 min-w-[44px] min-h-[44px] flex items-center justify-center shrink-0 hover:bg-muted transition-colors border border-transparent hover:border-border"
          aria-label="메뉴 열기/닫기"
        >
          {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
        </button>

        <div className="relative flex-1 min-w-0">
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <input
            type="text"
            value={searchValue}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="SEARCH_POSTS..."
            className="w-full max-w-full sm:w-72 lg:w-96 h-10 pl-10 pr-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
          />
        </div>

        {/* 정렬 */}
        <div className="hidden md:flex gap-2">
          <button
            onClick={() => onSortChange('latest')}
            className={`px-3 h-10 text-xs font-mono border transition-colors ${
              sortBy === 'latest'
                ? 'bg-primary text-primary-foreground border-primary'
                : 'bg-transparent border-border hover:border-primary'
            }`}
          >
            최신순
          </button>
          <button
            onClick={() => onSortChange('popular')}
            className={`px-3 h-10 text-xs font-mono border transition-colors ${
              sortBy === 'popular'
                ? 'bg-primary text-primary-foreground border-primary'
                : 'bg-transparent border-border hover:border-primary'
            }`}
          >
            인기순
          </button>
        </div>
      </div>

      <div className="flex items-center gap-1 sm:gap-3 shrink-0">
        <button
          onClick={onToggleDarkMode}
          className="p-2 min-w-[44px] min-h-[44px] flex items-center justify-center hover:bg-muted transition-colors border border-transparent hover:border-border"
          aria-label="다크모드 전환"
        >
          {darkMode ? <Sun size={20} /> : <Moon size={20} />}
        </button>

        {/* 로그인=새 글 작성 / 게스트=로그인 버튼. 모바일은 아이콘만(라벨 숨김). */}
        <button
          onClick={onNewPost}
          className="px-3 sm:px-4 h-10 min-h-[44px] shrink-0 bg-primary text-primary-foreground font-mono text-sm flex items-center gap-2 hover:opacity-90 transition-opacity"
          aria-label={user ? '새 글 작성' : '로그인'}
        >
          {user ? (
            <>
              <Plus size={16} />
              <span className="hidden sm:inline">새 글 작성</span>
            </>
          ) : (
            <>
              <LogIn size={16} />
              <span className="hidden sm:inline">로그인</span>
            </>
          )}
        </button>

        {/* 알림은 로그인 유저만 */}
        {user && (
          <div className="relative">
            <button
              onClick={onToggleNotifications}
              className="relative p-2 min-w-[44px] min-h-[44px] flex items-center justify-center hover:bg-muted transition-colors border border-transparent hover:border-border"
              aria-label="알림"
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
                onDelete={onDeleteNotification}
              />
            )}
          </div>
        )}
      </div>
    </header>
  );
}
