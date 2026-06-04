import { useState } from 'react';
import { POST_CATEGORIES, CATEGORY_LABELS } from '../lib/categories';

// [FEATURE:content-counter] 백엔드 @Size(PostCreateRequest/PostUpdateRequest)와 일치 — 클라이언트단 1차 차단, 서버가 최종 방어선.
const TITLE_MAX = 200;
const CONTENT_MAX = 10000;
// [/FEATURE:content-counter]

// 새 글 작성 / 수정 공용 폼 (카테고리 · 제목 · 내용)
export default function PostForm({ initialValues, submitLabel, submitting, error, onSubmit, onCancel, allowPoll }) {
  const [category, setCategory] = useState(initialValues.category);
  const [title, setTitle] = useState(initialValues.title);
  const [content, setContent] = useState(initialValues.content);

  // [FEATURE:poll] 작성 시에만 노출(allowPoll). 익명 투표 토글 + 보기 입력(2~8개)
  const [pollEnabled, setPollEnabled] = useState(false);
  const [pollOptions, setPollOptions] = useState(['', '']);
  const validPollOptions = pollOptions.map((o) => o.trim()).filter(Boolean);
  const pollValid = !pollEnabled || validPollOptions.length >= 2;
  const updatePollOption = (i, v) => setPollOptions((prev) => prev.map((o, idx) => (idx === i ? v : o)));
  const addPollOption = () => setPollOptions((prev) => (prev.length < 8 ? [...prev, ''] : prev));
  const removePollOption = (i) => setPollOptions((prev) => (prev.length > 2 ? prev.filter((_, idx) => idx !== i) : prev));
  // [/FEATURE:poll]

  const isValid = category && title.trim() && content.trim() && pollValid; // pollValid: [FEATURE:poll]

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!isValid) return;
    const payload = { category, title: title.trim(), content: content.trim() };
    // [FEATURE:poll] 투표 첨부 시 보기 배열 포함(공백 제거 후 2개 이상일 때만)
    if (allowPoll && pollEnabled && validPollOptions.length >= 2) {
      payload.pollOptions = validPollOptions;
    }
    // [/FEATURE:poll]
    onSubmit(payload);
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
        <div className="flex items-baseline justify-between mb-2">
          <label className="block text-sm font-mono">제목</label>
          {/* [FEATURE:content-counter] 제목 글자수 카운터 */}
          <span
            className={`text-xs font-mono ${
              title.length >= TITLE_MAX * 0.9 ? 'text-destructive' : 'text-muted-foreground'
            }`}
          >
            {title.length.toLocaleString()} / {TITLE_MAX.toLocaleString()}
          </span>
          {/* [/FEATURE:content-counter] */}
        </div>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          maxLength={TITLE_MAX}
          className="w-full h-12 px-4 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
          required
        />
      </div>

      {/* Content */}
      <div>
        {/* [FEATURE:markdown-rendering] 마크다운 작성 힌트 */}
        <div className="flex items-baseline justify-between gap-2 mb-2">
          <label className="block text-sm font-mono">
            내용
            <span className="ml-2 text-xs text-muted-foreground">
              마크다운 지원 · `코드` ```코드블록``` # 제목 **굵게** - 목록
            </span>
          </label>
          {/* [FEATURE:content-counter] 본문 글자수 카운터 */}
          <span
            className={`shrink-0 text-xs font-mono ${
              content.length >= CONTENT_MAX * 0.9 ? 'text-destructive' : 'text-muted-foreground'
            }`}
          >
            {content.length.toLocaleString()} / {CONTENT_MAX.toLocaleString()}
          </span>
          {/* [/FEATURE:content-counter] */}
        </div>
        {/* [/FEATURE:markdown-rendering] */}
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="내용을 입력하세요"
          rows={12}
          maxLength={CONTENT_MAX}
          className="w-full px-4 py-3 bg-input-background border border-border text-sm leading-relaxed placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors resize-none"
          required
        />
      </div>

      {/* [FEATURE:poll] 익명 투표(작성 시에만 노출) */}
      {allowPoll && (
        <div>
          <label className="flex items-center gap-2 text-sm font-mono mb-3 cursor-pointer w-fit">
            <input
              type="checkbox"
              checked={pollEnabled}
              onChange={(e) => setPollEnabled(e.target.checked)}
              className="accent-primary"
            />
            익명 투표 추가
          </label>
          {pollEnabled && (
            <div className="space-y-2 border border-border p-4">
              <p className="text-xs text-muted-foreground font-mono mb-1">보기 2~8개 · 익명으로 집계됩니다</p>
              {pollOptions.map((opt, i) => (
                <div key={i} className="flex gap-2">
                  <input
                    type="text"
                    value={opt}
                    onChange={(e) => updatePollOption(i, e.target.value)}
                    maxLength={100}
                    placeholder={`보기 ${i + 1}`}
                    className="flex-1 h-10 px-3 bg-input-background border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
                  />
                  {pollOptions.length > 2 && (
                    <button
                      type="button"
                      onClick={() => removePollOption(i)}
                      className="px-3 h-10 border border-border text-muted-foreground hover:border-destructive hover:text-destructive font-mono text-xs transition-colors"
                    >
                      삭제
                    </button>
                  )}
                </div>
              ))}
              {pollOptions.length < 8 && (
                <button type="button" onClick={addPollOption} className="text-xs font-mono text-primary hover:underline">
                  + 보기 추가
                </button>
              )}
            </div>
          )}
        </div>
      )}
      {/* [/FEATURE:poll] */}

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
