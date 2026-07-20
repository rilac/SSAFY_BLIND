import { Flag, Eye, EyeOff, ThumbsUp, MessageSquare, Bookmark, CheckCircle2, BarChart3, Pin } from 'lucide-react'; // CheckCircle2: [FEATURE:qna-accept] · BarChart3: [FEATURE:poll] · Pin: [FEATURE:pinned-posts] · EyeOff: [FEATURE:admin-moderation] 숨김
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
          {/* [FEATURE:admin-moderation] 숨김 글 배지 — 관리자 목록, 그리고 scope=mine일 때 작성자 본인의 글에 내려온다
              ([FEATURE:hidden-author-visibility]로 작성자가 자기 숨김 글을 목록에서 볼 수 있게 됨) */}
          {post.hidden && (
            <span className="flex items-center gap-1 px-1.5 py-0.5 border border-destructive text-destructive text-[10px] font-bold leading-none">
              <EyeOff size={10} /> 숨김
            </span>
          )}
          {/* [/FEATURE:admin-moderation] */}
          {/* [FEATURE:pinned-posts] 공지 고정 배지 — 가장 앞에 노출 */}
          {post.pinned && (
            <span className="flex items-center gap-1 px-1.5 py-0.5 bg-primary text-primary-foreground text-[10px] font-bold leading-none">
              <Pin size={10} /> 공지
            </span>
          )}
          {/* [/FEATURE:pinned-posts] */}
          {/* [FEATURE:unread-new] 안 읽은 새 글 배지 */}
          {post.isNew && (
            <span className="px-1.5 py-0.5 bg-primary text-primary-foreground text-[10px] font-bold leading-none">
              NEW
            </span>
          )}
          {/* [/FEATURE:unread-new] */}
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
          {/* [FEATURE:poll] 투표 배지 — 투표가 첨부된 글 */}
          {post.hasPoll && (
            <span className="flex items-center gap-1 text-primary">
              <BarChart3 size={12} /> 투표
            </span>
          )}
          {/* [/FEATURE:poll] */}
        </div>

        {/* [FEATURE:hidden-author-visibility] 숨김 글은 서버가 신고를 400으로 막으므로 버튼을 노출하지 않는다
            (남겨두면 누를 때마다 "신고 처리에 실패했습니다"만 뜬다) */}
        {!post.hidden && (
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
        )}
      </div>

      {/* [FEATURE:unread-new] 이미 연 글(새 글 아님)은 제목을 흐리게 — 읽음 표시 */}
      <h2
        className={`text-base font-semibold mb-4 group-hover:text-primary transition-colors ${
          post.isRead && !post.isNew ? 'text-muted-foreground' : ''
        }`}
      >
        {post.title}
      </h2>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4 text-xs font-mono text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <Eye size={14} />
            {formatNumber(post.viewCount)}
          </span>
          <span className="flex items-center gap-1.5">
            {/* [FEATURE:reactions] 총 반응 수(좋아요 포함 4종 합계) */}
            <ThumbsUp size={14} />
            {post.reactionTotal}
          </span>
          <span className="flex items-center gap-1.5">
            <MessageSquare size={14} />
            {post.commentCount}
          </span>
        </div>

        {/* [FEATURE:hidden-author-visibility] 스크랩도 동일 — 숨김 글에는 서버가 400을 준다 */}
        {!post.hidden && (
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
        )}
      </div>
    </article>
  );
}
