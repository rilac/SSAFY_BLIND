import {
  Menu,
  MessageSquare,
  Briefcase,
  HelpCircle,
  Utensils, // [FEATURE:food-board]
  Bookmark,
  User,
  ChevronRight,
  Settings,
  LogOut,
  Inbox,
  Shield,
  MapPin, // [FEATURE:cohort-campus-lounge]
  Users, // [FEATURE:cohort-campus-lounge]
} from 'lucide-react';
import { CATEGORY_LABELS } from '../lib/categories';
import Logo from './Logo';

const categoryItems = [
  { id: 'all', label: '전체글', icon: Menu },
  { id: 'FREE', label: CATEGORY_LABELS.FREE, icon: MessageSquare },
  { id: 'JOB', label: CATEGORY_LABELS.JOB, icon: Briefcase },
  { id: 'QUESTION', label: CATEGORY_LABELS.QUESTION, icon: HelpCircle },
  { id: 'FOOD', label: CATEGORY_LABELS.FOOD, icon: Utensils }, // [FEATURE:food-board]
];

// 피드 좌측 사이드바: 헤더(홈) / 카테고리 / 스크랩·내가 쓴 글·건의함·관리자 / 유저 메뉴
export default function Sidebar({
  sidebarOpen,
  category,
  scope,
  user,
  userMenuOpen,
  onSelectCategory,
  onSelectScope,
  onToggleUserMenu,
  onOpenSettings,
  onLogout,
  onHome,
  onFeedback,
  onAdmin,
}) {
  const isAdmin = user?.role === 'ADMIN';

  return (
    <aside
      className={`${
        sidebarOpen ? 'w-64' : 'w-0'
      } transition-all duration-300 border-r border-border bg-card flex flex-col overflow-hidden`}
    >
      {/* 헤더 — 클릭 시 피드로 */}
      <button
        onClick={onHome}
        className="p-6 border-b border-border text-left hover:bg-muted transition-colors"
      >
        <div className="flex items-center gap-2">
          <Logo size={20} className="text-primary" />
          <h1 className="text-xl font-mono tracking-tight">SSAFY_SOOP</h1>
        </div>
        <p className="text-xs font-mono text-muted-foreground mt-1">싸피인들을_위한_대나무숲v1.2</p>
      </button>

      <nav className="flex-1 overflow-y-auto p-4">
        <div className="space-y-1">
          {categoryItems.map((cat) => {
            const isActive = scope === 'all' && category === cat.id;
            const Icon = cat.icon;
            return (
              <button
                key={cat.id}
                onClick={() => onSelectCategory(cat.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono transition-colors border ${
                  isActive
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'bg-transparent text-foreground border-transparent hover:border-border'
                }`}
              >
                <Icon size={16} />
                <span>{cat.label}</span>
              </button>
            );
          })}
        </div>

        {/* [FEATURE:cohort-campus-lounge] 기수/캠퍼스 라운지 — 같은 캠퍼스·동기(기수) 글만 보기. 익명 유지·범위만 한정. */}
        <div className="mt-8 pt-6 border-t border-border">
          <h3 className="text-xs font-mono text-muted-foreground mb-3 px-3">LOUNGE</h3>
          <div className="space-y-1">
            <button
              onClick={() => onSelectScope('campus')}
              className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border transition-colors ${
                scope === 'campus'
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'border-transparent hover:border-border'
              }`}
            >
              <MapPin size={16} />
              <span className="flex-1 text-left">우리 캠퍼스</span>
              {user?.campus && <span className="text-[10px] opacity-70">{user.campus}</span>}
            </button>
            <button
              onClick={() => onSelectScope('cohort')}
              className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border transition-colors ${
                scope === 'cohort'
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'border-transparent hover:border-border'
              }`}
            >
              <Users size={16} />
              <span className="flex-1 text-left">동기</span>
              {user?.cohort && <span className="text-[10px] opacity-70">{user.cohort}</span>}
            </button>
          </div>
        </div>
        {/* [/FEATURE:cohort-campus-lounge] */}

        <div className="mt-8 pt-6 border-t border-border">
          <h3 className="text-xs font-mono text-muted-foreground mb-3 px-3">QUICK_ACCESS</h3>
          <div className="space-y-1">
            <button
              onClick={() => onSelectScope('bookmarked')}
              className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border transition-colors ${
                scope === 'bookmarked'
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'border-transparent hover:border-border'
              }`}
            >
              <Bookmark size={16} />
              <span>스크랩</span>
            </button>
            <button
              onClick={() => onSelectScope('mine')}
              className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border transition-colors ${
                scope === 'mine'
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'border-transparent hover:border-border'
              }`}
            >
              <User size={16} />
              <span>내가 쓴 글</span>
            </button>
            <button
              onClick={onFeedback}
              className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border border-transparent hover:border-border transition-colors"
            >
              <Inbox size={16} />
              <span>건의함</span>
            </button>
            {isAdmin && (
              <button
                onClick={onAdmin}
                className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border border-transparent hover:border-border transition-colors text-primary"
              >
                <Shield size={16} />
                <span>관리자</span>
              </button>
            )}
          </div>
        </div>
      </nav>

      <div className="p-4 border-t border-border">
        <div className="relative">
          <button
            onClick={onToggleUserMenu}
            className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border border-border hover:border-primary transition-colors"
          >
            <User size={16} />
            <div className="flex-1 text-left">
              <div className="text-xs">{user?.nickname || '익명'}</div>
              <div className="text-[10px] text-muted-foreground">
                {user?.cohort} {user?.campus}
              </div>
            </div>
            <ChevronRight
              size={14}
              className={`transition-transform ${userMenuOpen ? 'rotate-90' : ''}`}
            />
          </button>

          {userMenuOpen && (
            <div className="absolute bottom-full left-0 right-0 mb-2 bg-card border border-border">
              <button
                onClick={onOpenSettings}
                className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono hover:bg-muted transition-colors"
              >
                <Settings size={14} />
                <span>설정</span>
              </button>
              <button
                onClick={onLogout}
                className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono hover:bg-muted transition-colors text-destructive"
              >
                <LogOut size={14} />
                <span>로그아웃</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
