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

---

## cohort-campus-lounge — 기수/캠퍼스 스코프 필터·라운지 (2026-06-02)

§6-5 B 2순위. 이미 수집하나 프로필에만 노출하던 `cohort`/`campus`를 **피드 스코프 필터**로 노출 — "우리 캠퍼스만"·"동기(같은 기수)만". 익명 유지·범위만 한정. **백엔드(쿼리 필터) + 프론트(라운지 UI).**

**범위/동작**
- 기존 `scope`(all|mine|bookmarked) 축을 **`campus`/`cohort`로 확장**(상호배타 뷰). 값은 **서버가 현재 유저 기준으로 해석**(프론트는 값 미전달 — `scope=campus`/`scope=cohort`만 보냄, 익명/신원 노출 없음).
- `PostService.getAllPosts`가 라운지 scope일 때만 `userRepository.findById(currentUserId)`로 유저를 로드해 `me.getCampus()`/`me.getCohort()`를 필터로 사용(컨트롤러·서비스 시그니처 불변). `cohort`/`campus`는 온보딩 `@NotBlank`라 ACTIVE 유저는 항상 보유.
- 리포지토리 `findFilteredLatest`/`findFilteredPopular`에 null-guard 필터 `AND (:cohort IS NULL OR p.author.cohort = :cohort)`·`AND (:campus IS NULL OR p.author.campus = :campus)` 추가. 카테고리/검색/정렬과 **직교 결합**(예: 동기 + 질문 + 인기순).
- UI: 사이드바 `LOUNGE` 섹션에 "우리 캠퍼스"·"동기" 버튼(현재 유저 캠퍼스/기수 값 표기). 피드 헤더 뷰 라벨에 `우리 캠퍼스 · 서울`/`동기 · 10기`. 카테고리 버튼은 기존대로 scope를 `all`로 리셋(mine/bookmarked와 동일 동작).

**마커 위치 (`[FEATURE:cohort-campus-lounge]`)**
- 백엔드: `repository/PostRepository.java`(두 쿼리의 cohort/campus AND-절 + `@Param` 2개 × 2메서드), `service/PostService.java`(getAllPosts 내 라운지 scope 해석 블록 + repo 호출 인자).
- (신규) `test/repository/PostLoungeRepositoryTest.java` — 파일 전체(@DataJpaTest 4종). `test/service/PostServiceTest.java`에 scope 와이어링 3종(마커 블록).
- 프론트: `components/Sidebar.jsx`(LOUNGE 섹션 + `MapPin`/`Users` import), `pages/FeedPage.jsx`(viewLabel 라운지 케이스 + scope 주석 + `viewLabel(...user)` 호출).

**롤백 절차**
1. 마커 블록 제거.
   - `PostRepository`: 두 메서드(latest/popular)의 cohort/campus AND-절(value+countQuery)과 `@Param("cohort")`·`@Param("campus")` 파라미터 2개씩 제거 → 시그니처를 4-필터(category,keyword,authorId,bookmarkerId,pageable)로 환원.
   - `PostService.getAllPosts`: 라운지 해석 블록 삭제, repo 호출을 `(category, kw, authorId, bookmarkerId, pageable)`로 환원.
   - 호출부 인자 환원: `PostRepositoryTest` 7곳에서 `, null, null` 제거.
   - `Sidebar.jsx` LOUNGE 섹션·import, `FeedPage.jsx` viewLabel 라운지 케이스·`user` 인자 제거.
2. 신규 파일 `test/repository/PostLoungeRepositoryTest.java` 삭제, `PostServiceTest`의 scope 마커 블록 3종 삭제.
3. DB/마이그레이션 변경 없음(스키마 무변경 — 기존 `users.cohort`/`campus` 컬럼 재사용).

**미적용(후속 후보)**: 다른 캠퍼스/기수 라운지 브라우징(현재는 본인 소속만), 캠퍼스+기수 동시 필터, 라운지 전용 게시판/공지.

---

## nested-comments — 대댓글(1-depth 답글) (2026-06-02)

§6-1. 댓글에 답글(1단계 계층) — 에타/대부분 커뮤니티식. **백엔드 + 프론트엔드.** Flyway **V3**.

**범위/동작**
- 데이터: `Comment.parentId`(Long, nullable, FK 없음 — `acceptedCommentId`와 동일 방침). null이면 최상위 댓글. **Flyway V3**(`comments.parent_id` + `idx_comments_parent_id`).
- 작성: `POST /api/posts/{postId}/comments` 본문에 `parentId`(선택). 서버 검증 — 부모 존재(404)·동일 글 소속(404)·**부모가 최상위(1-depth 강제, 답글에 답글 금지 → 400 InvalidState)**.
- 삭제: 최상위 댓글 삭제 시 그 답글들도 함께 정리(`CommentRepository.deleteByParentId`, bulk). 답글이면 자식이 없어 no-op. (글 삭제는 기존 `Post.comments` cascade로 일괄 — 변경 없음.)
- 채택(qna-accept)과의 정합: **답글은 채택 불가**(`toggleAcceptAnswer`에 `parentId != null` 가드 추가, 프론트도 최상위만 채택 버튼 노출).
- 별칭(op-alias)과의 정합: 답글도 댓글이라 `buildAliasMap`이 동일하게 별칭 부여(같은 유저=글 내내 같은 별칭, 최상위/답글 무관). 변경 없음.
- 알림: 답글 작성 시 **부모 댓글 작성자**에게 알림(`NotificationService.notifyReply`, 본인 제외). enum 마이그레이션을 피하려 `NotificationType.COMMENT` 재사용 + 메시지로 "답글" 구분. 최상위 댓글은 기존대로 글 작성자에게.
- 응답: `CommentResponse.parentId`. 프론트는 평면 목록(서버 createdAt asc)을 최상위/답글로 그룹핑.
- UI: `PostDetailPage` 댓글 렌더를 `renderComment(comment, isReply)` 함수로 리팩터링 — 최상위 + 그 아래 답글(들여쓰기 `ml-6 border-l`) + "답글" 토글 버튼/입력. 채택·"답글" 버튼은 최상위만, 답글은 들여쓰기.

**알려진 동작**: ① 최상위 댓글 삭제 시 **타인의 답글까지 cascade 삭제**(v1 단순화 — 후속: soft-delete "삭제된 댓글" placeholder로 답글 보존). ② 답글 알림 type은 COMMENT 재사용(`notifications.type`이 MySQL 네이티브 enum이라 REPLY 추가 시 enum 마이그레이션 필요 → 회피). ③ 댓글 수(피드/상세)는 답글 포함 총계.

**마커 위치 (`[FEATURE:nested-comments]`)**
- 백엔드: `domain/Comment.java`(`parentId` 필드), `dto/CommentCreateRequest.java`(`parentId`), `dto/CommentResponse.java`(`parentId` 필드+factory), `repository/CommentRepository.java`(`deleteByParentId`), `service/NotificationService.java`(`notifyReply`), `service/CommentService.java`(addComment 부모검증·답글알림·`parentId`, deleteComment 답글정리, toggleAcceptAnswer 1-depth 가드).
- (신규) `resources/db/migration/V3__add_comment_parent.sql`, `test/service/CommentNestedServiceTest.java` — 파일 전체.
- 프론트: `pages/PostDetailPage.jsx`(답글 상태/핸들러, 댓글 그룹핑 + `renderComment` 리팩터링, 답글 입력 폼). ⚠️ **이 리팩터링이 기존 평면 map(qna+op-alias 마커 포함)을 대체** — 롤백 시 평면 map으로 환원 필요.

**롤백 절차**
1. 위 파일들에서 `[FEATURE:nested-comments]` 마커 블록 제거.
   - `CommentService`: addComment의 부모검증 블록·`.parentId(...)`·답글 알림 분기(`else` 포함) 제거 → 원래 `if(!post.author...)notifyComment` 복원. deleteComment의 `deleteByParentId` 제거. toggleAcceptAnswer의 1-depth 가드 제거.
   - `CommentResponse`: `parentId` 필드 + factory의 `comment.getParentId()` 인자 제거.
   - 나머지 파일(`Comment.parentId`, `CommentCreateRequest.parentId`, `CommentRepository.deleteByParentId`, `NotificationService.notifyReply`) 마커 블록 제거.
   - `PostDetailPage`: `renderComment`/그룹핑/답글 상태·핸들러·폼 제거하고, 댓글 렌더를 평면 `comments.map`(qna-accept·op-alias 마커 포함 버전)으로 환원.
2. 신규 파일 삭제: `V3__add_comment_parent.sql`, `test/service/CommentNestedServiceTest.java`.
3. DB: `drop index idx_comments_parent_id on comments; alter table comments drop column parent_id;` (V3 적용 환경) — 미적용이면 불필요.

**미적용(후속 후보)**: 답글 삭제 시 soft-delete placeholder(타인 답글 보존), 답글 N개 접기/펼치기, REPLY 알림 타입 분리(enum 마이그레이션), 답글에 멘션.
