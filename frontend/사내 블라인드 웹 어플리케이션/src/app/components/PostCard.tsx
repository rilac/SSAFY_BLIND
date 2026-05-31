import { Flag, Eye, ThumbsUp, MessageSquare, Bookmark } from "lucide-react";
import type { Post } from "../types";
import { formatTimestamp, formatNumber } from "../lib/format";

interface PostCardProps {
  post: Post;
  onOpen: (id: number) => void;
  onToggleBookmark: (id: number) => void;
  onReport: (id: number) => void;
}

// 피드 게시글 카드 — 카드 전체 클릭 시 상세로 이동
export default function PostCard({ post, onOpen, onToggleBookmark, onReport }: PostCardProps) {
  return (
    <article
      onClick={() => onOpen(post.id)}
      className="border border-border bg-card p-5 hover:border-primary transition-colors cursor-pointer group"
    >
      {/* Post Header */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-3 text-xs font-mono">
          <span className="text-primary">[{post.category}]</span>
          <span className="text-muted-foreground">{post.author}</span>
          <span className="text-muted-foreground opacity-50">•</span>
          <span className="text-muted-foreground">
            {post.cohort} {post.campus}
          </span>
          <span className="text-muted-foreground opacity-50">•</span>
          <span className="text-muted-foreground">{formatTimestamp(post.createdAt)}</span>
        </div>

        <button
          onClick={(e) => {
            e.stopPropagation();
            onReport(post.id);
          }}
          className="p-1 hover:bg-muted transition-colors opacity-0 group-hover:opacity-100"
        >
          <Flag size={14} className="text-muted-foreground" />
        </button>
      </div>

      {/* Post Title */}
      <h2 className="text-base font-semibold mb-2 group-hover:text-primary transition-colors">
        {post.title}
      </h2>

      {/* Post Preview */}
      <p className="text-sm text-muted-foreground mb-4 line-clamp-2">{post.preview}</p>

      {/* Post Footer */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4 text-xs font-mono text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <Eye size={14} />
            <span>{formatNumber(post.viewCount)}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <ThumbsUp size={14} />
            <span>{post.likeCount}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <MessageSquare size={14} />
            <span>{post.commentCount}</span>
          </div>
        </div>

        <button
          onClick={(e) => {
            e.stopPropagation();
            onToggleBookmark(post.id);
          }}
          className={`p-1.5 transition-colors ${
            post.isBookmarked
              ? "text-primary"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <Bookmark size={14} fill={post.isBookmarked ? "currentColor" : "none"} />
        </button>
      </div>
    </article>
  );
}
