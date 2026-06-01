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

---

## qna-accept — Q&A 답변 채택/해결됨 (2026-06-02)

§6-5 A 2순위. QUESTION 글의 작성자가 답변(댓글) 하나를 "채택"하고 "해결됨" 배지를 노출(StackOverflow식). **백엔드 + 프론트엔드.**

**범위/동작**
- 데이터: `Post.acceptedCommentId`(Long, nullable, FK 없음). null이면 미해결. Flyway **V2** 마이그레이션으로 `posts.accepted_comment_id` 컬럼 추가.
- API: `POST /api/posts/{postId}/comments/{commentId}/accept` — **토글**(같은 답변 재요청 시 해제). 서버 검증: QUESTION 글(400 InvalidState) + 질문 작성자(403 Forbidden) + 댓글이 해당 글 소속(404).
- 채택 댓글 삭제 시 `Post.clearAcceptedAnswer()`로 정리(댕글링 방지) — `CommentService.deleteComment` + 프론트 동기화.
- UI: 상세 페이지 "해결됨" 배지 + 댓글별 "채택/채택 해제" 토글 버튼(작성자·QUESTION 한정) + "채택된 답변" 강조. 피드 카드 "해결됨" 배지(`PostListResponse.solved`).

**마커 위치 (`[FEATURE:qna-accept]`)**
- 백엔드: `domain/Post.java`(필드+`acceptAnswer`/`clearAcceptedAnswer`), `service/CommentService.java`(`toggleAcceptAnswer`+삭제 정리+imports), `controller/CommentController.java`(accept 엔드포인트+import), `dto/PostResponse.java`(`acceptedCommentId`), `dto/PostListResponse.java`(`solved`).
- (신규) `dto/AcceptAnswerResponse.java`, `resources/db/migration/V2__add_accepted_comment.sql`, `test/CommentServiceTest.java` — 파일 전체.
- 프론트: `pages/PostDetailPage.jsx`(아이콘 import·핸들러·해결됨 배지·댓글 채택 UI), `components/PostCard.jsx`(아이콘 import·해결됨 배지).

**롤백 절차**
1. 위 파일들에서 `[FEATURE:qna-accept]` 마커 블록 제거(`PostResponse`/`PostListResponse` 생성자 인자, import 라인 포함).
2. 신규 파일 3종 삭제(`AcceptAnswerResponse.java`, `V2__add_accepted_comment.sql`, `CommentServiceTest.java`).
3. DB: `alter table posts drop column accepted_comment_id;` (이미 V2 적용된 환경) — 또는 V2 미적용이면 불필요.

**미적용(후속 후보)**: 채택 시 답변자에게 알림, QUESTION 외 카테고리 확장.

---

## op-alias — 댓글 글쓴이(OP) 표시 + 글 단위 익명 별칭 (2026-06-02)

§6-5 B 1순위. 닉네임이 유일하지 않아(H-anon) 스레드에서 글쓴이/동일인 식별이 혼동되는 문제를, **글 단위로 일관된 익명 별칭**으로 해결(에타식). **백엔드(별칭 계산) + 프론트(표시 대체).**

**범위/동작**
- 별칭 계산은 **서버에서 `user_id` 기준**으로 수행(프론트에는 신원/유저 id 미노출 유지). 글쓴이(OP) → `"글쓴이"`, 그 외 작성자는 **첫 등장(오래된 댓글) 순**으로 `"익명1"·"익명2"…`. 같은 유저는 그 글 안에서 항상 같은 별칭.
- 프론트는 댓글 작성자 줄에서 **닉네임을 별칭으로 대체**(기수·캠퍼스는 맥락용 유지). 글쓴이는 primary 색 + 굵게 강조.
- 적용 범위는 **댓글 스레드 한정**(사용자 결정). 피드 카드·상세 헤더의 닉네임 표시는 그대로 유지 → `PostResponse`/`PostListResponse`/`PostCard` 무변경.
- 댓글 작성 응답(낙관적 append)도 새 댓글의 별칭/글쓴이 여부를 포함하도록 `addComment`가 글 전체 댓글을 1회 재조회해 계산.

**알려진 동작**: 별칭은 매 조회 시 createdAt 순서로 재계산되므로, 중간 익명N의 댓글이 모두 삭제되면 이후 번호가 한 칸씩 당겨질 수 있다(에타와 동일, 스레드 내 일관성은 매 렌더 기준 보장). API 페이로드(`CommentResponse.author`)에는 기존처럼 닉네임이 포함되나 UI는 렌더하지 않음 — 페이로드에서도 비노출하려면 후속에서 댓글 전용 AuthorInfo 분기.

**마커 위치 (`[FEATURE:op-alias]`)**
- 백엔드: `dto/CommentResponse.java`(`alias`+`op` 필드, `of` 시그니처에 `alias,isAuthor` 추가), `service/CommentService.java`(`buildAliasMap` 헬퍼·`OP_ALIAS` 상수, `getComments`의 `existsById`→`findById`+별칭 적용, `addComment`의 별칭 계산).
- (신규) `test/CommentServiceAliasTest.java` — 파일 전체(4종: getComments 별칭/없는글, addComment 글쓴이/타인).
- 프론트: `pages/PostDetailPage.jsx`(댓글 작성자 줄의 닉네임 → 별칭 + 글쓴이 강조).

**롤백 절차**
1. 위 파일들에서 `[FEATURE:op-alias]` 마커 블록 제거.
   - `CommentResponse.of`를 기존 3-인자 시그니처 `of(comment, currentUserId, author)`로 되돌리고 `alias`/`op` 필드 제거.
   - `CommentService.getComments`의 `findById`를 다시 `existsById` 가드로 되돌리고, 두 호출부의 `CommentResponse.of(...)`를 3-인자로 환원. `addComment` 반환을 `CommentResponse.of(saved, userId, author)`로 환원. `buildAliasMap`/`OP_ALIAS` 삭제.
   - `PostDetailPage`의 댓글 작성자 줄을 `{comment.author?.nickname} · {cohort} {campus} · {time}`로 복원.
2. 신규 파일 `test/CommentServiceAliasTest.java` 삭제.
3. DB/마이그레이션 변경 없음(스키마 무변경).

**미적용(후속 후보)**: 댓글 페이로드에서 비-OP 닉네임 제거(진짜 페이로드 익명화), 게시글/피드까지 별칭 확장(에타 완전 적용), 별칭 호버 시 기수/캠퍼스 외 부가정보.
