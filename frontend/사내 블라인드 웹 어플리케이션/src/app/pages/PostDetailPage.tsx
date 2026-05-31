import { useState } from "react";
import {
  ArrowLeft,
  ThumbsUp,
  Bookmark,
  Flag,
  Eye,
  Pencil,
  Trash2,
} from "lucide-react";
import type { Comment, Post } from "../types";
import { formatTimestamp, formatNumber } from "../lib/format";

interface PostDetailPageProps {
  post: Post;
  comments: Comment[];
  onBack: () => void;
  onToggleLike: (id: number) => void;
  onToggleBookmark: (id: number) => void;
  onReport: (id: number) => void;
  onEdit: (id: number) => void;
  onDelete: (id: number) => void;
  onAddComment: (postId: number, content: string) => void;
  onDeleteComment: (postId: number, commentId: number) => void;
}

// 게시글 상세 — 본문 / 좋아요·스크랩 / 댓글
export default function PostDetailPage({
  post,
  comments,
  onBack,
  onToggleLike,
  onToggleBookmark,
  onReport,
  onEdit,
  onDelete,
  onAddComment,
  onDeleteComment,
}: PostDetailPageProps) {
  const [commentInput, setCommentInput] = useState("");

  const isEdited = post.createdAt !== post.updatedAt;

  const handleCommentSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = commentInput.trim();
    if (!trimmed) return;
    onAddComment(post.id, trimmed);
    setCommentInput("");
  };

  const handleDelete = () => {
    if (!window.confirm("정말로 이 게시글을 삭제하시겠습니까?")) return;
    onDelete(post.id);
  };

  return (
    <div className="h-screen w-full bg-background text-foreground overflow-y-auto">
      <div className="max-w-3xl mx-auto p-6">
        {/* Back */}
        <button
          onClick={onBack}
          className="flex items-center gap-2 text-sm font-mono text-muted-foreground hover:text-foreground transition-colors mb-4"
        >
          <ArrowLeft size={16} />
          피드로 돌아가기
        </button>

        {/* Post Body */}
        <article className="border border-border bg-card p-6 mb-4">
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-3 text-xs font-mono flex-wrap">
              <span className="text-primary">[{post.category}]</span>
              <span className="text-muted-foreground">{post.author}</span>
              <span className="text-muted-foreground opacity-50">•</span>
              <span className="text-muted-foreground">
                {post.cohort} {post.campus}
              </span>
            </div>

            {post.isMine && (
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => onEdit(post.id)}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-border hover:border-primary transition-colors"
                >
                  <Pencil size={12} />
                  수정
                </button>
                <button
                  onClick={handleDelete}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-mono border border-destructive text-destructive hover:opacity-80 transition-opacity"
                >
                  <Trash2 size={12} />
                  삭제
                </button>
              </div>
            )}
          </div>

          <h1 className="text-xl font-semibold mb-3">{post.title}</h1>

          <div className="flex items-center gap-3 text-xs font-mono text-muted-foreground mb-5">
            <span className="flex items-center gap-1.5">
              <Eye size={14} />
              {formatNumber(post.viewCount)}
            </span>
            <span className="opacity-50">•</span>
            <span>{formatTimestamp(post.createdAt)}</span>
            {isEdited && <span className="opacity-70">(수정됨)</span>}
          </div>

          <p className="text-sm leading-relaxed whitespace-pre-wrap mb-6">{post.content}</p>

          {/* Actions */}
          <div className="flex items-center gap-2 pt-4 border-t border-border">
            <button
              onClick={() => onToggleLike(post.id)}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-mono border transition-colors ${
                post.isLiked
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-border hover:border-primary"
              }`}
            >
              <ThumbsUp size={14} fill={post.isLiked ? "currentColor" : "none"} />
              {post.likeCount}
            </button>

            <button
              onClick={() => onToggleBookmark(post.id)}
              className={`flex items-center gap-2 px-4 py-2 text-sm font-mono border transition-colors ${
                post.isBookmarked
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-border hover:border-primary"
              }`}
            >
              <Bookmark size={14} fill={post.isBookmarked ? "currentColor" : "none"} />
              스크랩
            </button>

            <button
              onClick={() => onReport(post.id)}
              className="flex items-center gap-2 px-4 py-2 text-sm font-mono border border-border text-muted-foreground hover:border-primary hover:text-foreground transition-colors ml-auto"
            >
              <Flag size={14} />
              신고
            </button>
          </div>
        </article>

        {/* Comments */}
        <section className="border border-border bg-card p-6">
          <h2 className="text-sm font-mono mb-4">댓글 {comments.length}개</h2>

          {comments.length === 0 ? (
            <p className="text-xs font-mono text-muted-foreground mb-4">
              아직 댓글이 없습니다.
            </p>
          ) : (
            <div className="space-y-3 mb-4">
              {comments.map((comment) => (
                <div
                  key={comment.id}
                  className="border-b border-border pb-3 last:border-b-0 last:pb-0"
                >
                  <div className="flex items-start justify-between gap-2">
                    <p className="text-sm flex-1">{comment.content}</p>
                    {comment.isMine && (
                      <button
                        onClick={() => onDeleteComment(post.id, comment.id)}
                        className="text-xs font-mono text-destructive hover:opacity-80 transition-opacity shrink-0"
                      >
                        삭제
                      </button>
                    )}
                  </div>
                  <span className="text-[10px] font-mono text-muted-foreground mt-1 block">
                    {formatTimestamp(comment.createdAt)}
                  </span>
                </div>
              ))}
            </div>
          )}

          {/* Comment Input */}
          <form onSubmit={handleCommentSubmit} className="flex gap-2">
            <input
              type="text"
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="댓글을 입력하세요"
              className="flex-1 h-11 px-4 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
            />
            <button
              type="submit"
              disabled={!commentInput.trim()}
              className="px-5 h-11 bg-primary text-primary-foreground font-mono text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
            >
              작성
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}
