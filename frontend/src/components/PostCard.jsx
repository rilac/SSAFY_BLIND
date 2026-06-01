import { Flag, Eye, ThumbsUp, MessageSquare, Bookmark, CheckCircle2 } from 'lucide-react'; // CheckCircle2: [FEATURE:qna-accept]
import { formatTimestamp, formatNumber } from '../lib/format';
import { categoryLabel } from '../lib/categories';

// 피드 게시글 카드 — 작성자 비노출(익명). 카테고리 배지 + 카운트 + 시간.
export default function PostCard({ post, onOpen, onToggleBookmark, onReport }) {
  return (
    <article
      onClick={() => onOpen(post.id)}
      className="border border-border bg-card p-5 hover:border-primary transition-colors cursor-pointer group"
    >
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2 text-xs font-mono flex-wrap">
          <span className="text-primary">[{categoryLabel(post.category)}]</span>
          <span className="text-muted-foreground">{post.author?.nickname}</span>
          <span className="text-muted-foreground opacity-50">•</span>
          <span className="text-muted-foreground">
            {post.author?.cohort} {post.author?.campus}
          </span>
          <span className="text-muted-foreground opacity-50">•</span>
          <span className="text-muted-foreground">{formatTimestamp(post.createdAt)}</span>
          {/* [FEATURE:qna-accept] 해결됨 배지 — 채택된 답변이 있는 글 */}
          {post.solved && (
            <span className="flex items-center gap-1 text-green-600 dark:text-green-400">
              <CheckCircle2 size={12} /> 해결됨
            </span>
          )}
          {/* [/FEATURE:qna-accept] */}
        </div>

        <button
          onClick={(e) => {
            e.stopPropagation();
            onReport(post.id);
          }}
          className="p-1 hover:bg-muted transition-colors opacity-0 group-hover:opacity-100"
          title="신고"
        >
          <Flag size={14} className="text-muted-foreground" />
        </button>
      </div>

      <h2 className="text-base font-semibold mb-4 group-hover:text-primary transition-colors">
        {post.title}
      </h2>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4 text-xs font-mono text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <Eye size={14} />
            {formatNumber(post.viewCount)}
          </span>
          <span className="flex items-center gap-1.5">
            <ThumbsUp size={14} />
            {post.likeCount}
          </span>
          <span className="flex items-center gap-1.5">
            <MessageSquare size={14} />
            {post.commentCount}
          </span>
        </div>

        <button
          onClick={(e) => {
            e.stopPropagation();
            onToggleBookmark(post.id);
          }}
          className={`p-1.5 transition-colors ${
            post.isBookmarked ? 'text-primary' : 'text-muted-foreground hover:text-foreground'
          }`}
          title="스크랩"
        >
          <Bookmark size={14} fill={post.isBookmarked ? 'currentColor' : 'none'} />
        </button>
      </div>
    </article>
  );
}
