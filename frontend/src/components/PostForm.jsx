import { useState } from 'react';
import { POST_CATEGORIES, CATEGORY_LABELS } from '../lib/categories';

// 새 글 작성 / 수정 공용 폼 (카테고리 · 제목 · 내용)
export default function PostForm({ initialValues, submitLabel, submitting, error, onSubmit, onCancel }) {
  const [category, setCategory] = useState(initialValues.category);
  const [title, setTitle] = useState(initialValues.title);
  const [content, setContent] = useState(initialValues.content);

  const isValid = category && title.trim() && content.trim();

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!isValid) return;
    onSubmit({ category, title: title.trim(), content: content.trim() });
  };

  return (
    <form onSubmit={handleSubmit} className="border border-border bg-card p-8 space-y-6">
      {/* Category */}
      <div>
        <label className="block text-sm font-mono mb-3">카테고리</label>
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
          {POST_CATEGORIES.map((id) => (
            <button
              key={id}
              type="button"
              onClick={() => setCategory(id)}
              className={`px-4 py-2 text-sm font-mono border transition-colors ${
                category === id
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'bg-transparent border-border hover:border-primary'
              }`}
            >
              {CATEGORY_LABELS[id]}
            </button>
          ))}
        </div>
      </div>

      {/* Title */}
      <div>
        <label className="block text-sm font-mono mb-2">제목</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          className="w-full h-12 px-4 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
          required
        />
      </div>

      {/* Content */}
      <div>
        <label className="block text-sm font-mono mb-2">내용</label>
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="내용을 입력하세요"
          rows={12}
          className="w-full px-4 py-3 bg-input-background border border-border text-sm leading-relaxed placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors resize-none"
          required
        />
      </div>

      {error && <p className="text-sm text-destructive text-center font-mono">{error}</p>}

      <div className="flex items-center gap-3 pt-2">
        <button
          type="submit"
          disabled={!isValid || submitting}
          className="flex-1 h-12 bg-primary text-primary-foreground font-mono text-sm hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? '처리 중...' : submitLabel}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-6 h-12 border border-border font-mono text-sm hover:border-primary transition-colors"
        >
          취소
        </button>
      </div>
    </form>
  );
}
