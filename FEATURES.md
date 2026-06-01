# FEATURES — 기능 확장(Phase E/§6) 롤백 인덱스

Phase E부터의 **기능 확장**은 추후 롤백이 쉽도록 코드 블록을 `// [FEATURE:이름] … // [/FEATURE:이름]`
(JSX는 `{/* … */}`, CSS는 `/* … */`) 마커로 감싸고, 본 문서에 인덱싱합니다.
롤백 시: 해당 이름의 마커 블록을 전부 제거 + 아래 "신규 파일/의존성"을 되돌리면 됩니다.

> 리뷰 반영 수정(Phase 0/A/B/C/D)은 **영구**라 마커를 쓰지 않습니다. 본 문서는 §6 기능 확장 전용.

---

## markdown-rendering — 게시글 본문 마크다운 + 코드블록 (2026-06-02)

§6-5 A: 개발 교육 커뮤니티 특화. 게시글 본문을 마크다운으로 렌더링하고 코드블록 syntax highlight 지원.
**프론트엔드 전용**(본문은 평문 그대로 저장·반환, 렌더링만 마크다운화 — 백엔드 무변경).

**범위/동작**
- 본문 렌더링: `PostDetailPage`의 평문 `<p whitespace-pre-wrap>` → `<Markdown>` 컴포넌트.
- 라이브러리: `react-markdown` + `remark-gfm`(표/취소선/체크리스트) + `remark-breaks`(단일 줄바꿈 보존) + `rehype-highlight`(코드 하이라이트).
- 보안: react-markdown 기본값 = 원시 HTML 미렌더(rehype-raw 미사용) → XSS 차단. 이미지 임베드는 익명 환경 IP 유출/어뷰즈 우려로 비활성(링크로 대체). 외부 링크는 `target=_blank rel=noopener noreferrer nofollow`.
- 코드블록은 앱 테마와 무관하게 항상 어두운 "터미널" 룩.
- 작성 폼(`PostForm`)에 "마크다운 지원" 힌트.

성능: 마크다운/하이라이트 라이브러리(~334KB)는 `React.lazy`로 코드 분할 → 상세 페이지 진입 시에만 로드, 초기 번들 영향 없음(메인 ~268KB 유지). 로딩 중에는 Suspense fallback으로 평문 표시.

**마커 위치 (`[FEATURE:markdown-rendering]`)**
- (신규) `frontend/src/components/Markdown.jsx` — 파일 전체.
- `frontend/src/pages/PostDetailPage.jsx` — lazy import 블록 + Suspense 본문 렌더 블록.
- `frontend/src/components/PostForm.jsx` — 내용 라벨의 힌트 블록.
- `frontend/src/index.css` — `.markdown-body` + `.hljs-*` 스타일 블록.

**신규 의존성** (`frontend/package.json`)
- `react-markdown`, `remark-gfm`, `remark-breaks`, `rehype-highlight`.

**롤백 절차**
1. 위 4개 파일에서 `[FEATURE:markdown-rendering]` 마커 블록 제거.
   - `PostDetailPage`: 본문을 기존 `<p className="text-sm leading-relaxed whitespace-pre-wrap mb-6">{post.content}</p>`로 복원하고, react import에서 추가했던 `lazy, Suspense` 되돌리기(`useState, useEffect, useRef`).
2. `frontend/src/components/Markdown.jsx` 삭제.
3. `npm --prefix frontend uninstall react-markdown remark-gfm remark-breaks rehype-highlight`.

**미적용(후속 후보)**: 댓글 마크다운(현재 단일 라인 input), 피드 카드 미리보기는 본문 스니펫 미표시라 변경 없음.
