import {
  Menu,
  MessageSquare,
  Briefcase,
  HelpCircle,
  Rocket,
  Users,
  Bookmark,
  User,
  ChevronRight,
  Settings,
  LogOut,
} from "lucide-react";
import type { Category, FeedView, UserData } from "../types";
import { CATEGORY_LABELS } from "../data/mockData";

type IconComponent = React.ComponentType<{ size?: number; className?: string }>;

const categoryItems: { id: Category; icon: IconComponent }[] = [
  { id: "all", icon: Menu },
  { id: "free", icon: MessageSquare },
  { id: "job", icon: Briefcase },
  { id: "question", icon: HelpCircle },
  { id: "project", icon: Rocket },
  { id: "lounge", icon: Users },
];

interface SidebarProps {
  sidebarOpen: boolean;
  category: Category;
  feedView: FeedView;
  userData: UserData;
  userMenuOpen: boolean;
  onSelectCategory: (category: Category) => void;
  onSelectFeedView: (view: FeedView) => void;
  onToggleUserMenu: () => void;
  onOpenSettings: () => void;
  onLogout: () => void;
}

// 피드 좌측 사이드바: 카테고리 / 보조메뉴(스크랩·내가 쓴 글) / 유저 메뉴
export default function Sidebar({
  sidebarOpen,
  category,
  feedView,
  userData,
  userMenuOpen,
  onSelectCategory,
  onSelectFeedView,
  onToggleUserMenu,
  onOpenSettings,
  onLogout,
}: SidebarProps) {
  return (
    <aside
      className={`${
        sidebarOpen ? "w-64" : "w-0"
      } transition-all duration-300 border-r border-border bg-card flex flex-col overflow-hidden`}
    >
      <div className="p-6 border-b border-border">
        <h1 className="text-xl font-mono tracking-tight">SSAFY_BLIND</h1>
        <p className="text-xs font-mono text-muted-foreground mt-1">
          INTERNAL_COMMUNITY_v1.0
        </p>
      </div>

      <nav className="flex-1 overflow-y-auto p-4">
        <div className="space-y-1">
          {categoryItems.map((cat) => {
            const isActive = feedView === "all" && category === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => onSelectCategory(cat.id)}
                className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono transition-colors border ${
                  isActive
                    ? "bg-primary text-primary-foreground border-primary"
                    : "bg-transparent text-foreground border-transparent hover:border-border"
                }`}
              >
                <cat.icon size={16} />
                <span>{CATEGORY_LABELS[cat.id]}</span>
              </button>
            );
          })}
        </div>

        <div className="mt-8 pt-6 border-t border-border">
          <h3 className="text-xs font-mono text-muted-foreground mb-3 px-3">
            QUICK_ACCESS
          </h3>
          <div className="space-y-1">
            <button
              onClick={() => onSelectFeedView("bookmarks")}
              className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border transition-colors ${
                feedView === "bookmarks"
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-transparent hover:border-border"
              }`}
            >
              <Bookmark size={16} />
              <span>스크랩</span>
            </button>
            <button
              onClick={() => onSelectFeedView("mine")}
              className={`w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border transition-colors ${
                feedView === "mine"
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-transparent hover:border-border"
              }`}
            >
              <User size={16} />
              <span>내가 쓴 글</span>
            </button>
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
              <div className="text-xs">{userData.nickname}</div>
              <div className="text-[10px] text-muted-foreground">
                {userData.cohort} {userData.campus}
              </div>
            </div>
            <ChevronRight
              size={14}
              className={`transition-transform ${userMenuOpen ? "rotate-90" : ""}`}
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
