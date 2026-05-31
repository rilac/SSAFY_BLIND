import type { Category, FeedView, Post, SortBy } from "../types";
import { CATEGORY_LABELS } from "../data/mockData";
import PostCard from "../components/PostCard";

interface FeedPageProps {
  posts: Post[];
  category: Category;
  feedView: FeedView;
  sortBy: SortBy;
  searchQuery: string;
  onSortChange: (sortBy: SortBy) => void;
  onOpenPost: (id: number) => void;
  onToggleBookmark: (id: number) => void;
  onReport: (id: number) => void;
}

function viewLabel(feedView: FeedView, category: Category): string {
  if (feedView === "bookmarks") return "스크랩";
  if (feedView === "mine") return "내가 쓴 글";
  return CATEGORY_LABELS[category];
}

// 피드: 보조뷰/카테고리/검색 필터 + 정렬을 적용해 게시글 목록을 보여준다.
export default function FeedPage({
  posts,
  category,
  feedView,
  sortBy,
  searchQuery,
  onSortChange,
  onOpenPost,
  onToggleBookmark,
  onReport,
}: FeedPageProps) {
  const query = searchQuery.trim().toLowerCase();

  // ── 필터 (원본 배열을 변형하지 않고 새 배열 생성) ──
  const filtered = posts.filter((post) => {
    if (feedView === "bookmarks" && !post.isBookmarked) return false;
    if (feedView === "mine" && !post.isMine) return false;
    if (feedView === "all" && category !== "all") {
      if (post.category !== CATEGORY_LABELS[category]) return false;
    }
    if (query) {
      const haystack = `${post.title} ${post.preview} ${post.content}`.toLowerCase();
      if (!haystack.includes(query)) return false;
    }
    return true;
  });

  // ── 정렬 ([...]로 복사 후 정렬 — sort는 in-place라 원본 보호) ──
  const sorted = [...filtered].sort((a, b) => {
    if (sortBy === "popular") return b.likeCount - a.likeCount;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  return (
    <main className="flex-1 overflow-y-auto">
      <div className="max-w-4xl mx-auto p-6">
        {/* View Title */}
        <div className="mb-4">
          <h2 className="text-lg font-mono">{viewLabel(feedView, category)}</h2>
          {query && (
            <p className="text-xs font-mono text-muted-foreground mt-1">
              SEARCH: "{searchQuery}"
            </p>
          )}
        </div>

        {/* Sort Controls */}
        <div className="flex items-center justify-between mb-6 pb-4 border-b border-border">
          <div className="flex gap-2">
            <button
              onClick={() => onSortChange("latest")}
              className={`px-4 py-2 text-xs font-mono border transition-colors ${
                sortBy === "latest"
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-transparent text-foreground border-border hover:border-primary"
              }`}
            >
              최신순
            </button>
            <button
              onClick={() => onSortChange("popular")}
              className={`px-4 py-2 text-xs font-mono border transition-colors ${
                sortBy === "popular"
                  ? "bg-primary text-primary-foreground border-primary"
                  : "bg-transparent text-foreground border-border hover:border-primary"
              }`}
            >
              인기순
            </button>
          </div>

          <div className="text-xs font-mono text-muted-foreground">
            TOTAL_{sorted.length}_POSTS
          </div>
        </div>

        {/* Posts */}
        {sorted.length === 0 ? (
          <div className="border border-border bg-card p-12 text-center">
            <p className="text-sm font-mono text-muted-foreground">
              표시할 게시글이 없습니다
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {sorted.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                onOpen={onOpenPost}
                onToggleBookmark={onToggleBookmark}
                onReport={onReport}
              />
            ))}
          </div>
        )}

        {/* Load More (데모: 표시용) */}
        {sorted.length > 0 && (
          <div className="mt-8 pt-6 border-t border-border">
            <button className="w-full py-3 border border-border hover:border-primary text-sm font-mono transition-colors">
              LOAD_MORE_POSTS
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
