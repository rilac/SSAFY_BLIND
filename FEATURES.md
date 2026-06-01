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

---

## poll — 익명 투표/설문 (2026-06-02)

§6-5 B ☆. 게시글에 선택적 익명 투표 첨부("어느 프레임워크?" 등). **익명 보드 특성에 적합**(모집 글은 신원 필요 → 부적합, 투표는 집계만 노출). **백엔드 + 프론트엔드**, Flyway **V4**.

**범위/동작**
- 데이터: `poll_options`(post_id, content, sort_order) + `poll_votes`(post_id, option_id[Long, FK 없음], user_id, **unique(post_id,user_id)**). 보기가 2개 이상이면 그 글은 "투표 글". **Flyway V4**.
- **익명성**: 표는 `user_id`로 **1인 1표**를 강제하되, 노출은 **보기별 집계(count)만** — 누가 무엇을 골랐는지는 응답에 포함하지 않음.
- 작성: `PostCreateRequest.pollOptions`(선택, `List<String>`). `PollService.createOptions`가 공백 제거 후 **2~8개·각 100자** 검증(위반 시 400 InvalidState, 작성 트랜잭션 롤백). 빈 목록이면 투표 미첨부.
- 투표: `POST /api/posts/{postId}/poll/vote` `{optionId}` — **토글**: 처음=신규, 다른 보기=변경(changeOption), **같은 보기 재클릭=취소(delete)**. 동시 첫 투표 경합은 유니크 제약으로 1회만(`DataIntegrityViolationException` 흡수).
- 응답: `PostResponse.poll`(options[{id,content,voteCount}] + totalVotes + myOptionId; 투표 없으면 **null**). `PostListResponse.hasPoll`(피드 배지용, 배치 판별).
- 삭제: 글 삭제 시 `poll_votes`·`poll_options` 정리(`PollService.deleteForPost` → `PostService.deletePost` 경로, 다른 자식 정리와 동일하게 글 삭제 전).
- UI: `PostForm` 투표 토글 + 보기 입력(2~8, **작성 시에만** `allowPoll`), `PostDetailPage` 보기별 막대(퍼센트)+집계+내 선택 강조+클릭 투표/취소, `PostCard` "투표" 배지.

**알려진 동작/한계**: ① 단일 선택만(멀티 선택 미지원). ② 결과 **상시 공개**(투표 전 숨김 아님). ③ 마감일/마감 상태 없음. ④ 수정(updatePost)으로는 투표 변경 불가(작성 시에만 첨부, 응답엔 기존 투표 표시). ⑤ V4 DDL은 베이스라인처럼 Hibernate 생성이 아닌 **수기 작성** — 엔티티 컬럼/타입(`bigint`/`varchar(255)`/`integer`/`datetime(6)`)과 일치하도록 맞췄고 H2 테스트는 엔티티 기반(create-drop)이라 무관하나, **실 MySQL 기동 시 `validate` 통과 최종 확인 권장**.

**마커 위치 (`[FEATURE:poll]`)**
- (신규) 백엔드: `domain/PollOption.java`·`domain/PollVote.java`, `repository/PollOptionRepository.java`·`repository/PollVoteRepository.java`, `dto/PollResponse.java`·`dto/PollVoteRequest.java`, `service/PollService.java`, `controller/PollController.java`, `resources/db/migration/V4__add_poll.sql`, `test/service/PollServiceTest.java` — 파일 전체.
- 백엔드(수정): `dto/PostCreateRequest.java`(`pollOptions`), `dto/PostResponse.java`(`poll` 필드+factory), `dto/PostListResponse.java`(`hasPoll`), `service/PostService.java`(PollService 주입 + createPost/getPost/updatePost/getAllPosts/deletePost 연결). `test/service/PostServiceTest.java`(@Mock PollService), `test/service/PostDeletionIntegrationTest.java`(실 PollService 구성).
- (신규) 프론트: 없음(기존 컴포넌트에 블록 추가). 수정: `components/PostForm.jsx`(투표 작성 UI+allowPoll), `pages/PostCreatePage.jsx`(allowPoll 전달), `pages/PostDetailPage.jsx`(투표 렌더+handleVote), `components/PostCard.jsx`(투표 배지).

**롤백 절차**
1. `[FEATURE:poll]` 마커 블록 제거.
   - `PostResponse`: `poll` 필드 + factory 2개의 `poll` 파라미터 제거 → 기존 시그니처 환원. 호출부(`PostService` createPost/getPost/updatePost) 인자 환원.
   - `PostListResponse`: `hasPoll` 필드 + factory 파라미터 제거. `PostService.getAllPosts`의 `pollPostIds`·`hasPoll` 인자 환원.
   - `PostService`: `PollService` 필드, createPost의 createOptions/buildResults, getPost/updatePost의 buildResults, deletePost의 deleteForPost 제거.
   - `PostCreateRequest.pollOptions` 제거. 테스트: `PostServiceTest`의 `@Mock PollService` 제거, `PostDeletionIntegrationTest`의 PollService 구성·인자 환원.
   - 프론트 4개 파일의 마커 블록 제거(PostForm 투표 UI/state, PostCreatePage allowPoll, PostDetailPage 투표 렌더/handleVote/import, PostCard 배지/import).
2. 신규 파일 삭제(위 "신규" 목록 + V4 sql + PollServiceTest).
3. DB: `drop table poll_votes; drop table poll_options;` (V4 적용 환경).

**미적용(후속 후보)**: 멀티 선택, 투표 전 결과 숨김, 마감일/자동 마감, 투표 수정(편집), 투표 결과 글에 고정.

---

## unread-new — 읽음 표시 / 안 읽은 새 글 배지 (2026-06-02)

§6-5 C ☆. 피드에서 안 읽은 새 글에 "NEW" 배지 + 이미 연 글은 흐리게(읽음 표시). **기존 `PostView`(M-NEW-5) 재사용 → 스키마 변경/Flyway 없음.** 프론트는 배지/스타일만.

**범위/동작**
- `PostView`(상세 조회 시 기록, 작성자 제외)의 존재 = "그 유저가 글을 연 적 있음". 이를 재사용해 피드 항목마다:
  - `isRead` = 현재 유저의 `PostView` 존재(연 적 있음).
  - `isNew` = **작성자 본인 아님 + 미열람 + 최근 작성(기본 7일 내)**. → "안 읽은 새 글".
- 글을 열면(`getPost`이 `PostView` 기록) **다음 피드 로드에서 NEW 사라지고 읽음 처리** — 별도 "읽음 처리" API 불필요.
- 배치: `PostViewRepository.findViewedPostIds(postIds, userId)`(N+1 방지)로 열람 글 id 집합을 구하고, `PostService.getAllPosts`가 `isNew`/`isRead` 계산. window는 `NEW_POST_WINDOW_DAYS=7` 상수.
- 응답: `PostListResponse.isNew`/`isRead`. UI: `PostCard` "NEW" 배지(primary) + 읽은 글(새 글 아님) 제목 `text-muted-foreground`로 흐리게.

**알려진 동작/한계**: ① "읽음"은 **상세를 연 경우만**(피드 스크롤만으로는 읽음 처리 안 됨) — `PostView` 기준. ② 작성자 본인 글은 NEW 아님(`isRead`도 false라 흐려지지 않음). ③ window 7일 고정(상수). ④ 상세 페이지엔 별도 읽음 UI 없음(피드 한정). ⑤ `PostView`는 24h 카운트 dedup이 있으나 레코드 자체는 첫 열람에 생성·유지되므로 읽음 판정에 영향 없음.

**마커 위치 (`[FEATURE:unread-new]`)**
- 백엔드: `repository/PostViewRepository.java`(`findViewedPostIds`), `dto/PostListResponse.java`(`isNew`/`isRead` 필드+factory), `service/PostService.java`(`NEW_POST_WINDOW_DAYS` 상수 + getAllPosts의 `viewedPostIds` 배치·`isNew`/`isRead` 계산), `test/service/PostServiceTest.java`(`test_안읽은_새글_읽음_표시` + `postWith` 헬퍼).
- 프론트: `components/PostCard.jsx`("NEW" 배지 + 읽은 글 제목 흐리게).

**롤백 절차**
1. `[FEATURE:unread-new]` 마커 블록 제거.
   - `PostListResponse`: `isNew`/`isRead` 필드 + factory 파라미터 제거 → 호출부(`PostService.getAllPosts`)의 `isNew`/`viewed` 인자 환원.
   - `PostService`: `NEW_POST_WINDOW_DAYS`·`viewedPostIds`·`newCutoff`·`isNew` 계산 제거.
   - `PostViewRepository.findViewedPostIds` 제거. `PostServiceTest`의 unread-new 테스트·`postWith` 제거.
   - `PostCard`의 NEW 배지·제목 흐리게 제거.
2. 신규 파일 없음. DB/마이그레이션 변경 없음.

**미적용(후속 후보)**: 피드 노출만으로 읽음 처리(IntersectionObserver), 마지막 방문 기준 "새 글 N개" 요약, window 사용자 설정, 상세 페이지 읽음 표시.

---

## reactions — 다양한 반응(좋아요/도움돼요/정보/공감) (2026-06-02)

§6-5 C / §6-3. 단일 "좋아요"를 **4종 반응**(좋아요·도움돼요·정보·공감)으로 확장. **1인 1반응(단일 선택)** — 페이스북식. **기존 `post_likes` 재사용**(+`reaction_type` enum), Flyway **V5**. **백엔드 + 프론트.**

**범위/동작**
- 데이터: `post_likes`에 `reaction_type enum('LIKE','HELPFUL','INFORMATIVE','EMPATHY') NOT NULL` 추가(**Flyway V5**, 기존 좋아요 행은 DEFAULT `LIKE`로 채움). `(post_id,user_id)` 유니크 유지 → **1인 1반응**. `PostLike` 엔티티에 `reactionType`(@Builder.Default LIKE) + `changeType` 도메인 메서드.
- 토글: `POST /api/posts/{postId}/reactions` `{type}` — **같은 종류 재클릭=취소(delete), 다른 종류=변경(changeType), 처음=신규(+작성자 알림)**. (기존 `POST /{id}/like` 대체.) 동시 첫 반응은 유니크로 1회.
- 응답: 상세 `PostResponse.reactions`(`ReactionResponse`: 종류별 수[4종 0 포함] + total + myReaction[없으면 null]). 피드 `PostListResponse.reactionTotal`(총합) + `myReaction`. **기존 `isLiked`/`likeCount` 제거**(PostResponse/PostListResponse).
- 집계: 상세=`countByPostIdGroupByType`+`findByPostIdAndUserId`(buildReactions), 피드=`countByPostIds`(총합)+`findUserReactions`(배치, N+1 방지).
- 알림: 신규 반응 시 작성자에게 `notifyReaction`(`NotificationType.LIKE` 재사용으로 enum 마이그레이션 회피, 메시지 "내 글에 반응이 달렸어요").
- UI: 상세 `PostDetailPage` 반응 바(이모지+라벨+수, 내 반응 강조·토글), 피드 `PostCard` 총 반응 수.

**알려진 동작/한계**: ① 1인 1반응(멀티 선택 아님 — 사용자 결정). ② 댓글 반응 미지원(게시글만). ③ 알림 type은 LIKE 재사용(반응 종류 구분 안 됨). ④ V5는 수기 DDL(enum) — 엔티티와 일치 확인했고 클론 테이블로 DDL 검증, **실 MySQL `validate`는 첫 기동 시 확인 권장**.

**마커 위치 (`[FEATURE:reactions]`)**
- (신규) 백엔드: `domain/ReactionType.java`, `dto/ReactionResponse.java`·`ReactionRequest.java`, `resources/db/migration/V5__add_reaction_type.sql`. 테스트: `PostServiceTest`의 react 3종(마커 블록).
- 백엔드(수정): `domain/PostLike.java`(reactionType+changeType), `repository/PostLikeRepository.java`(countByPostIdGroupByType·findUserReactions 추가, like 전용 메서드 정리), `service/PostService.java`(react·buildReactions, getPost/getAllPosts/createPost/updatePost), `controller/PostController.java`(/reactions), `service/NotificationService.java`(notifyReaction), `dto/PostResponse.java`·`PostListResponse.java`(isLiked/likeCount→reactions). 삭제: `dto/PostLikeResponse.java`.
- 프론트: `pages/PostDetailPage.jsx`(REACTION_META·handleReact·반응 바, ThumbsUp import 제거), `components/PostCard.jsx`(likeCount→reactionTotal).

**롤백 절차** (단일 좋아요로 환원 — 비교적 큰 revert)
1. `[FEATURE:reactions]` 마커 블록 제거 + 신규 파일 삭제(ReactionType/ReactionResponse/ReactionRequest/V5/`PostServiceTest` react 블록).
2. `PostLike`에서 reactionType/changeType 제거. `PostLikeRepository`에 `existsByPostIdAndUserId`·`countByPostId`·`findLikedPostIds` 복원, 신규 쿼리 제거.
3. `PostService.react`→`toggleLike`(PostLikeResponse 반환) 복원, getPost/getAllPosts/createPost/updatePost를 isLiked/likeCount로 환원. `PostResponse`/`PostListResponse`에 isLiked/likeCount 복원. `PostLikeResponse.java` 복구. `PostController` `/like` 복원. `NotificationService.notifyReaction`→`notifyLike`.
4. 프론트 PostDetailPage 단일 좋아요 버튼·handleLike 복원(ThumbsUp import), PostCard reactionTotal→likeCount.
5. DB: `alter table post_likes drop column reaction_type;` (V5 적용 환경).

**미적용(후속 후보)**: 멀티 선택 반응, 댓글 반응, 반응 종류별 알림(REPLY처럼 type 분리), 반응한 사람 목록(익명 유지 전제).

---

## weekly-digest — 주간 인기글 다이제스트 (2026-06-02)

§6-3/§6-5 C. **지난 7일 인기글 Top N을 매주 월요일 09:10에 자동 집계해 모든 ACTIVE 유저에게 앱 내 알림(SYSTEM)으로 발송.** 익명 유지(글 제목/링크만). **백엔드 전용**(스키마 변경 없음, 프론트 무변경).

**범위/동작**
- 스케줄: 매주 월 **09:10**(Spring cron `0 10 9 * * MON`). `app.weekly-digest.cron`으로 override. **단일 인스턴스 전제**(RefreshTokenCleanupScheduler와 동일 — 수평 확장 시 ShedLock 필요).
- 인기 점수: `viewCount*1 + 반응수*2 + 댓글수*3`(조회:반응:댓글 = **1:2:3**) 내림차순, 동점이면 최신. Top N(기본 5, `app.weekly-digest.top-n`). **랭킹 쿼리의 COUNT은 DISTINCT 필수**(다중 LEFT JOIN 카티전 곱 방지). reactions=post_likes 전체 행(타입 무관), 댓글=답글 포함 총계.
- 발송: 최근 7일(작성일 기준) 인기 글 → ACTIVE 유저 전체에게 SYSTEM 알림 1건씩 `saveAll`(배치). 알림 `message`(varchar 255)는 상위 3개 제목(각 30자 미리보기) + 255자 안전망 truncate, `postId`는 단일 Long이라 **1위 글로 링크**.
- 알림 종은 TopBar에 이미 있고 SYSTEM 알림도 Bell 아이콘으로 동일 렌더 + `postId` 클릭 이동(FeedPage `handleNotificationClick`) → **프론트 무변경**.

**알려진 동작/한계**: ① 수신 거부(opt-out) 없음 — 앱 내 알림이라 부담 적다는 판단. ② 인기글 0건이면 발송 스킵. ③ 재실행 중복 가드 없음(단일 인스턴스 전제 MVP — 필요 시 "이번 주 이미 발송" 체크 후속). ④ window 7일 고정(상수). ⑤ 익명: 작성자 신원 미노출, 제목/링크만.

**마커 위치 (`[FEATURE:weekly-digest]`)**
- (신규) 백엔드: `scheduler/WeeklyDigestScheduler.java`, `service/WeeklyDigestService.java`. 테스트: `test/service/WeeklyDigestServiceTest.java`(단위 4종), `test/repository/WeeklyDigestRepositoryTest.java`(@DataJpaTest 4종 — 점수/DISTINCT·TopN·숨김·기간) — 파일 전체.
- 백엔드(수정): `repository/PostRepository.java`(`findTopByScoreSince` + import), `repository/UserRepository.java`(`findByStatus` + import), `service/NotificationService.java`(`notifyDigest(List<User>,…)` 배치 SYSTEM), `resources/application.yml`(`app.weekly-digest.cron`/`top-n`).
- 프론트: 없음.

**롤백 절차**
1. 위 수정 파일들에서 `[FEATURE:weekly-digest]` 마커 블록 제거(PostRepository 쿼리+import, UserRepository `findByStatus`+import, NotificationService `notifyDigest`, application.yml `weekly-digest` 키).
2. 신규 파일 4종 삭제(`WeeklyDigestScheduler`, `WeeklyDigestService`, `WeeklyDigestServiceTest`, `WeeklyDigestRepositoryTest`).
3. DB/마이그레이션 변경 없음(notifications 테이블·SYSTEM enum 재사용).

**미적용(후속 후보)**: 수신 거부(opt-out) 플래그, 중복 발송 가드("이번 주 이미 발송" 체크), 채널 확장(MM DM/이메일), window/Top N 사용자 설정, 다이제스트 전용 알림 타입(현재 SYSTEM 재사용).

---

## pinned-posts — 공지/고정글 (2026-06-02)

§6-2. 관리자가 글을 **상단 고정(공지)** → 피드에서 항상 최상단 + "공지" 배지. **백엔드 + 프론트엔드**, Flyway **V6**.

**범위/동작**
- 데이터: `posts.pinned`(boolean, `@Builder.Default false`) — 기존 `hidden`/`reviewed`와 동일하게 `bit not null`. **Flyway V6**(`alter table posts add column pinned bit not null default 0`).
- 토글: `POST /api/admin/posts/{id}/pin` — **관리자 전용**(SecurityConfig `/api/admin/**` = ROLE_ADMIN), 호출 시 고정↔해제 토글, 응답 `{ "pinned": bool }`(새 상태). `Post.togglePin()` 도메인 메서드(@Setter 금지).
- 정렬: `PostRepository.findFilteredLatest`/`findFilteredPopular` 두 쿼리의 `ORDER BY` **맨 앞에 `p.pinned DESC` 추가** → 현재 필터(카테고리/검색/라운지) 결과 내에서 고정 글이 항상 먼저, 동순위는 기존 정렬(최신/인기순). **전역 강제 노출이 아니라 정렬 키**(필터에 안 걸리면 그 뷰에는 안 보임, 페이지 2+에는 고정 글 미노출 — 일반적 공지 동작).
- 응답: `PostListResponse.pinned`(피드 배지) + `PostResponse.pinned`(상세 버튼 상태/배지). 둘 다 factory에서 `post.isPinned()`를 직접 읽어 **PostService 호출부 무변경**(solved 패턴과 동일).
- UI: `PostCard` "공지" 배지(Pin 아이콘, 가장 앞). `PostDetailPage`는 `useAuth`로 관리자 판별 → 헤더 액션 영역에 "공지 고정/고정 해제" 토글 버튼(관리자에게만, 본인 글 여부 무관) + 상단 "공지" 배지.

**알려진 동작/한계**: ① 고정은 **정렬 키**일 뿐(필터 무시 전역 공지 아님) — 라운지/카테고리 필터에 안 맞으면 그 뷰엔 안 보임. ② 다중 고정 가능(여러 글 동시 고정 시 그들끼리는 createdAt/인기순). ③ 고정 글도 페이지네이션 적용(1페이지 상단에만, 2페이지+엔 미노출). ④ 고정 전용 알림 없음. ⑤ V6는 단순 컬럼 추가(boolean=bit not null) — Hibernate가 boolean을 bit로 매핑(V1 baseline의 hidden/reviewed와 동일)하므로 validate 통과, **실 MySQL 첫 기동 시 확인 권장**.

**마커 위치 (`[FEATURE:pinned-posts]`)**
- 백엔드(수정): `domain/Post.java`(`pinned` 필드 + `togglePin()`), `repository/PostRepository.java`(두 쿼리 ORDER BY `p.pinned DESC`), `dto/PostListResponse.java`·`dto/PostResponse.java`(`pinned` 필드 + factory `post.isPinned()`), `service/AdminService.java`(`togglePin`), `controller/AdminController.java`(`POST /posts/{id}/pin` + `Map` import). 테스트: `AdminServiceTest`(토글 1종, 마커 블록).
- (신규) 백엔드: `resources/db/migration/V6__add_post_pinned.sql`, `test/repository/PostPinnedRepositoryTest.java`(@DataJpaTest 3종 — 최신순/인기순 고정 우선·고정없음) — 파일 전체.
- 프론트: `components/PostCard.jsx`(공지 배지 + `Pin` import), `pages/PostDetailPage.jsx`(`useAuth`/`Pin` import, `isAdmin`, `handleTogglePin`/`pinLoading`, 헤더 토글 버튼[`post.isMine` → `post.isMine || isAdmin`로 확장 + 수정/삭제를 `post.isMine` 프래그먼트로], 상단 공지 배지).

**롤백 절차**
1. 위 파일들에서 `[FEATURE:pinned-posts]` 마커 블록 제거.
   - `PostRepository`: 두 메서드 ORDER BY에서 `p.pinned DESC, ` 제거 → `ORDER BY p.createdAt DESC` / `ORDER BY COUNT(pl) DESC, p.createdAt DESC`로 환원.
   - `PostListResponse`/`PostResponse`: `pinned` 필드 + factory의 `post.isPinned()` 인자 제거.
   - `AdminController`: pin 엔드포인트 + `Map` import 제거. `AdminService.togglePin`, `Post.pinned`/`togglePin` 제거.
   - `PostDetailPage`: 헤더 액션을 `{post.isMine && (...수정/삭제...)}`로 환원, `useAuth`/`Pin` import·`isAdmin`·`handleTogglePin`·`pinLoading`·공지 배지 제거. `PostCard` 공지 배지·`Pin` import 제거.
2. 신규 파일 삭제: `V6__add_post_pinned.sql`, `test/repository/PostPinnedRepositoryTest.java`. `AdminServiceTest`의 토글 블록 제거.
3. DB: `alter table posts drop column pinned;` (V6 적용 환경) — 미적용이면 불필요.

**미적용(후속 후보)**: 전역 공지(필터 무시 항상 노출), 고정 만료/예약, 고정 순서 지정, 카테고리별 공지, 고정 시 작성자/유저 알림, 관리자 페이지에서 고정 목록 관리.
