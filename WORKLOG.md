# WORKLOG — SSAFY_BLIND 개선 작업 내역

사내 블라인드형 익명 커뮤니티(Spring Boot + React)의 1차/2차 리뷰 반영 작업 기록입니다.
분석/계획 문서는 [MoreDevelopments.md](./MoreDevelopments.md)(1차) · [MoreDevelopments_V2.md](./MoreDevelopments_V2.md)(2차)이며, 본 문서는 **무엇을 실제로 구현했는지**를 한곳에 모은 진행 현황입니다.

- 최종 업데이트: 2026-06-02
- Git: **Phase 0~D + Access/Refresh + Flyway + Phase E 8기능 + 후속 UX 버그픽스 커밋·푸시 완료**(`origin/main` — 최신 `cdd7d4d` + 본 동기화 커밋). Phase E 커밋: `2805f0f` markdown → `e2e6c17` qna-accept → `0ea272b` op-alias → `72667be` cohort-campus-lounge → `804ce98` docs-sync → `e3c3995` nested-comments(V3) → `2f1ef16` poll(V4) → `45a3567` unread-new → `d88fd47` UX 버그픽스 3종 → `cdd7d4d` reactions(V5, 단일 좋아요→4종 반응). **미커밋 없음.**
- 진행 단계: **Phase 0 ✅ · A ✅ · B ✅ · C ✅ · D ✅ 완료**(M-NEW-5·삭제 통합·관측성 + **M-NEW-7 Flyway**) → **Phase E 8기능 완료**(✅ 마크다운 · ✅ Q&A 채택 · ✅ OP/익명 별칭 · ✅ 기수/캠퍼스 라운지 · ✅ 대댓글 · ✅ 익명 투표 · ✅ 읽음/새 글 배지 · ✅ 다양한 반응) + 후속 UX 버그픽스 3종 → **남은 것: §6 백로그(모더레이션 강화·북마크 폴더 등) + 테스트 폭**
- ✅ **Access/Refresh 토큰 분리 구현 완료(2026-06-02)**: 짧은 Access(30m, stateless) + DB 저장 Refresh(14d, `refresh_tokens`) + `/api/auth/refresh` 회전 재발급 + 만료 정리 스케줄러. 검증: backend `./gradlew.bat test` BUILD SUCCESSFUL, frontend `npm run build` 성공. *(아래 "✅ Refresh Token 도입" 섹션)*
- ✅ **M-NEW-7 Flyway 도입 완료(2026-06-02)**: Hibernate가 생성한 `V1__baseline.sql`(=validate와 정확히 일치) + `baseline-on-migrate`로 기존/신규 DB 모두 안전 처리. dev/prod 모두 Flyway ON·`ddl-auto: validate`, 테스트(H2)는 Flyway OFF. **배포 전 수동 DDL 폐기.** *(아래 "✅ M-NEW-7 Flyway" 섹션)*
- 운영 완성도 추이: 1차 65~70% → Phase 0 후 70~75% → Phase A·B 후 배포 가능 → Phase C 후 UX/정책 마감 → Phase D(운영품질)+Access/Refresh+Flyway 완료(약 92%) → **Phase E(기능 확장) 진행 중**
- 작업 범위 원칙: **리뷰 반영 수정은 영구**(롤백 마커 없음). **기능 확장(§6)은 롤백 용이하도록 마커**(아래 [롤백 마커 규약](#롤백-마커-규약)) — Phase 0/A/B는 전부 수정이라 마커 미사용.

---

## ▶ 다음 작업 (바로 이어서 시작)

> 재개용 체크리스트. 상세는 각 섹션 참조. 현재 모든 작업트리 변경은 **검증 완료(테스트/빌드 통과)** 상태.

1. ✅ **Phase E 8기능 + UX 버그픽스 전부 커밋·푸시 완료**(`origin/main` — reactions `cdd7d4d`, 본 계획 doc 커밋까지). 미커밋 없음.
2. ✅ **주간 인기글 다이제스트(weekly-digest) 구현·검증 완료(2026-06-02), 미커밋**. 백엔드 전용(스키마 무변경, 프론트 무변경). 마커 `[FEATURE:weekly-digest]` + `FEATURES.md` 인덱싱 완료. 검증: backend `./gradlew test` BUILD SUCCESSFUL(신규 단위 4 + @DataJpaTest 4 포함 전체 통과). 상세는 아래 [🗓️ 주간 인기글 다이제스트](#️-계획--주간-인기글-다이제스트-weekly-digest-내일-구현) 섹션. **다음 후보: 모더레이션 강화 / 북마크 폴더.**
3. **검증 리마인더** — dev MySQL로 `bootRun` 1회 시 **Flyway V1~V5 자동 적용**(`flyway_schema_history`; `accepted_comment_id`·`parent_id`·`poll_*`·`post_likes.reaction_type`). weekly-digest·op-alias·라운지·unread-new는 스키마 무변경. E2E는 MM+MySQL 스택 필요(단위·@DataJpaTest·프론트 빌드 검증은 완료). ⚠️ V4·V5는 수기 DDL이라 실 MySQL `validate` 최종 확인 권장(V5는 클론 테이블로 DDL 검증 완료). weekly-digest 실발송 수동 확인은 cron 임박 시각 설정 또는 `WeeklyDigestService.sendWeeklyDigest()` 직접 호출.
4. **테스트 폭(여력 시)** — 컨트롤러 슬라이스(@WebMvcTest), 인가 케이스 확장. (CommentService 단위[채택·별칭·대댓글]·PostService 단위[scope]·PollService 단위·삭제 통합·라운지 @DataJpaTest·헬스/인가 스모크·RefreshTokenService는 완료)

---

## 🗓️ 주간 인기글 다이제스트 (weekly-digest) — ✅ 구현 완료(2026-06-02, 미커밋)

§6-3/§6-5 C. **지난 7일 인기글 Top N을 매주 월요일 09:10에 자동 집계해 모든 ACTIVE 유저에게 앱 내 알림으로 발송.** 사용자와 스펙 확정(2026-06-02) → **구현·검증 완료**. 마커 `[FEATURE:weekly-digest]` + `FEATURES.md` 인덱싱 완료. 상세 마커/롤백은 `FEATURES.md`의 weekly-digest 섹션 참조.

### 구현 결과(파일)
- (신규) `scheduler/WeeklyDigestScheduler` — `@Scheduled(cron="${app.weekly-digest.cron:0 10 9 * * MON}")` → `WeeklyDigestService.sendWeeklyDigest()`(수신자>0이면 로그).
- (신규) `service/WeeklyDigestService` — `@Transactional` `sendWeeklyDigest()`: 최근 7일 Top N(`@Value app.weekly-digest.top-n:5`) 조회 → 0건이면 스킵(0 반환) → ACTIVE 유저 전체 → 상위 3개 제목(각 30자 미리보기·255자 truncate)으로 메시지 구성 → 1위 글 링크로 `notifyDigest`.
- `repository/PostRepository.findTopByScoreSince(since, Pageable)` — 점수=`viewCount + COUNT(DISTINCT pl)*2 + COUNT(DISTINCT c)*3` desc, 동점 최신. hidden 제외 + createdAt≥since.
- `repository/UserRepository.findByStatus(UserStatus)` — ACTIVE 유저.
- `service/NotificationService.notifyDigest(List<User>, message, topPostId)` — SYSTEM 알림 배치 `saveAll`.
- `resources/application.yml` — `app.weekly-digest.cron`/`top-n`.
- 프론트: **무변경 확인 완료**(NotificationPanel SYSTEM=Bell 렌더, FeedPage `handleNotificationClick`이 `postId`로 이동).
- 테스트: `WeeklyDigestServiceTest`(단위 4 — 발송/인기글0/수신자0/긴제목truncate), `WeeklyDigestRepositoryTest`(@DataJpaTest 4 — 점수순+DISTINCT 검출/TopN/숨김/기간).

**검증**: `./gradlew test` BUILD SUCCESSFUL(전체). **스키마/Flyway 변경 없음**. 실발송 수동 확인은 미수행(cron 임박 설정 또는 메서드 직접 호출로 가능).

<details><summary>최초 계획(접기)</summary>

§6-3/§6-5 C. 지난 7일 인기글 Top N을 매주 월요일 09:10에 자동 집계해 모든 ACTIVE 유저에게 앱 내 알림으로 발송.

### 확정 스펙
- **발송 채널**: 앱 내 알림(기존 `NotificationService` + `NotificationType.SYSTEM` 재사용). MM DM/이메일 아님.
- **기간**: 최근 7일(작성일 기준).
- **인기 점수**: `viewCount*1 + 반응수*2 + 댓글수*3` (조회:반응:댓글 = **1:2:3**) 내림차순. Top N(기본 5).
- **스케줄**: 매주 월요일 **09:10** — Spring cron `0 10 9 * * MON`. (외부화: `app.weekly-digest.cron`, 단일 인스턴스 전제 — RefreshToken 정리 스케줄러와 동일 방식.)
- **수신 거부 없음**(앱 내 알림이라 부담 적음 — opt-out 플래그 미도입).
- **익명 유지**: 다이제스트는 글 제목/링크만, 작성자 신원 미노출.

### 구현 계획(파일·요지)
- (신규) `scheduler/WeeklyDigestScheduler` — `@Scheduled(cron=app.weekly-digest.cron)` → `WeeklyDigestService.sendWeeklyDigest()`. `CommunityApplication`은 이미 `@EnableScheduling`.
- (신규) `service/WeeklyDigestService` — ① `PostRepository`로 최근 7일 인기 Top N 조회 ② ACTIVE 유저 전체 조회 ③ 각 유저에게 SYSTEM 알림 1건 생성(saveAll).
- `repository/PostRepository` — 점수 랭킹 쿼리 추가(예: `@Query("SELECT p FROM Post p LEFT JOIN PostLike pl ON pl.post=p LEFT JOIN Comment c ON c.post=p WHERE p.hidden=false AND p.createdAt>=:since GROUP BY p ORDER BY (p.viewCount*1 + COUNT(DISTINCT pl)*2 + COUNT(DISTINCT c)*3) DESC")` + `Pageable`(Top N). **COUNT은 DISTINCT 필수**(다중 LEFT JOIN 카티전 곱 방지).
- `repository/UserRepository` — ACTIVE 유저 조회(`findByStatus(UserStatus.ACTIVE)` 추가 또는 기존 확인).
- `service/NotificationService` — `notifyDigest(recipient, message, topPostId)`(SYSTEM 타입) 추가. **message는 varchar(255)** → 상위 제목 3개 정도만 + 글자수 truncate. `postId`는 단일 Long이라 **1위 글로 링크**(클릭 시 1위 글로 이동).
- 프론트: **변경 없음 예상** — 알림 종은 TopBar에 이미 있고 SYSTEM 알림도 동일 렌더·`postId` 클릭 이동. (확인만)
- 테스트: `WeeklyDigestService` 단위(점수순 Top N + 유저별 알림 생성). 랭킹 쿼리는 @DataJpaTest(H2) 고려.
- **스키마 변경 없음**(notifications 테이블·SYSTEM enum 재사용) → **Flyway 마이그레이션 불필요**.

### 구현 시 주의/열린 점
- 알림 message 255자 제한 → 제목 N개·길이 truncate 설계.
- 인기글 0건(지난 주 글 없음)이면 발송 스킵.
- 재실행 중복(스케줄러 재기동 시) — 단일 인스턴스 전제로 MVP는 가드 생략(필요 시 "이번 주 이미 발송" 체크 후속).
- 검증: 단위 테스트 + 수동 트리거(테스트용으로 cron 임박 시각 설정하거나 메서드 직접 호출)로 알림 생성 확인.

</details>

---

## ✅ Phase 0 — 프론트 UX 버그 5종 + 커스텀 모달

1차 배포 직전 체감 버그 수정. 실제 연동 프론트(`frontend/src`)에서만 작업, 백엔드 무변경. 네이티브 `alert`/`confirm` 대신 앱 디자인 커스텀 모달 도입.

**신규**
- `frontend/src/components/ConfirmDialog.jsx` — 확인/취소 2버튼(`danger` 옵션)
- `frontend/src/components/AlertDialog.jsx` — 확인 1버튼 (둘 다 `ReportModal` 마크업 미러링)

**수정 (버그별)**
| 버그 | 증상 | 수정 | 파일 |
|---|---|---|---|
| Bug 1 | 로그인 실패 알림이 떴다 사라짐 | 401 가드를 인증 엔드포인트 전체(`/auth/`) 제외로 변경 + 실패 시 `AlertDialog` | `api/client.js`, `pages/LoginPage.jsx` |
| Bug 2 | 로그인 직후 피드 미로딩 | 초기 로드 `AbortController`+cleanup + `user?.id` 준비 후 1회 실행 | `pages/FeedPage.jsx` |
| Bug 3 | 관리자 건의함 미조회 | `Promise.all` → `Promise.allSettled`(한쪽 실패가 다른 쪽 막지 않음) | `pages/AdminPage.jsx` |
| Bug 4 | 로그아웃 즉시 실행 | `ConfirmDialog` 확인 후에만 로그아웃 | `pages/FeedPage.jsx` |
| Bug 5 | 조회수 +2 | `useRef` 가드로 StrictMode 이중 fetch 차단(서버 1회 호출) | `pages/PostDetailPage.jsx` |

**검증**: `frontend`에서 `npm run build` 성공.
**기록 문서**: `MoreDevelopments.md` 상단 Phase 0 섹션.

---

## ✅ Phase A — 배포 차단 해소 (§5 체크리스트 1~6)

2차 리뷰의 배포 블로커 수정. 검증: `backend`에서 `./gradlew test` **BUILD SUCCESSFUL**.

| 항목 | 등급 | 핵심 변경 | 신규/수정 파일 |
|---|---|---|---|
| **C-NEW-1** 관리자 영구삭제 FK | 🔴 | 하드삭제 + 자식 정리: 삭제 전 신고/좋아요/북마크/알림 `deleteByPostId` 후 글 삭제. `PostServiceTest`에 `InOrder` 순서 검증 추가 | `service/PostService`, `repository/{Report,PostLike,Bookmark,Notification}Repository`, `test/PostServiceTest` |
| **C-NEW-2** 로그인 레이트리밋 | 🔴 | 인메모리 `LoginRateLimiter`(IP·loginId, 기본 5회/300초/600초차단) → 429 + 실패 audit 로깅 | (신규) `security/LoginRateLimiter`, `exception/TooManyRequestsException`; `controller/AuthController`, `exception/GlobalExceptionHandler` |
| **M-NEW-2** CORS 외부화 | 🔴 | `app.cors.allowed-origins`(콤마 구분) 프로퍼티로 외부화 | `security/SecurityConfig`, `resources/application.yml` |
| **H-NEW-1** 입력 길이 `@Size` | 🟠 | 제목≤200/본문≤10000/댓글≤1000/건의 본문≤5000/온보딩 각≤20 → 위반 시 400 | `dto/{PostCreate,PostUpdate,CommentCreate,Feedback,Onboarding}Request` |
| **H-NEW-3** MM 호출 타임아웃 | 🟠 | `RestTemplate` connect(3s)/read(5s) 타임아웃 + 실패 시 503 | (신규) `exception/MattermostUnavailableException`; `client/MattermostClient`, `exception/GlobalExceptionHandler` |
| **H-NEW-4** 리포 위생 | 🟠 | `git rm -r --cached frontend/node_modules .idea backend/.idea`(3,937개 추적 해제, 작업트리 보존) + `.gitignore` blanket `.idea/`. **2026-06-01 커밋·푸시 완료**(`83df359` — Figma mock 81개·루트 고아 `package-lock.json` 정리 동반, 추적 파일 ~4,000→137) | 리포 전반, `.gitignore` |
| **M-NEW-3** 알림 고아 데이터 | 🟡 | C-NEW-1 삭제 경로에서 `notificationRepository.deleteByPostId`로 함께 정리 | `repository/NotificationRepository` |

**기록 문서**: `MoreDevelopments_V2.md` 상단 Phase A 섹션.

---

## ✅ Phase B — 보안/계정 하드닝

검증: `./gradlew test` **BUILD SUCCESSFUL**(신규 승격 테스트 2개 포함).

| 항목 | 등급 | 핵심 변경 | 파일 |
|---|---|---|---|
| **H-NEW-2** 토큰 계정 상태 재검증 | 🟠 | `JwtAuthFilter`가 claim이 아닌 **DB 현재 `UserStatus`로 재검증** — PENDING은 온보딩/`auth/me`만, ACTIVE만 통과, DORMANT/WITHDRAWN은 401. 탈퇴·휴면 후 탈취 토큰 즉시 무효화(추가 쿼리 없음) | `security/JwtAuthFilter` |
| **JWT_SECRET 길이 검증** | 🟡 | `JwtProvider` 생성자 주입 전환 → 시크릿 **32byte 미만이면 기동 시 `IllegalStateException`**(fail-fast), 서명 키 1회 생성 | `security/JwtProvider` |
| **M-NEW-6** 최초 ADMIN 부트스트랩 | 🟡 | `app.admin.bootstrap-usernames` 지정 MM username을 **로그인 시 ADMIN 승격**(`User.promoteToAdmin()`) | `service/AuthService`, `domain/User`, `resources/application.yml`, `test/AuthServiceTest` |

**기록 문서**: `MoreDevelopments_V2.md` 상단 Phase B 섹션.

---

## ✅ Phase C — UX/정책 마감

검증: `frontend`에서 `npm run build` 성공 · `backend`에서 `./gradlew test` **BUILD SUCCESSFUL**(reviewed 관련 테스트 2종 갱신/추가).

| 항목 | 등급 | 핵심 변경 | 신규/수정 파일 |
|---|---|---|---|
| **M-NEW-1** PENDING 라우팅 가드 | 🟡 | `PrivateRoute`가 `user.status==='PENDING'`이면 `/onboarding`으로 강제. 역가드 `OnboardingRoute` 신규 — 미인증→`/login`, 이미 온보딩(ACTIVE)→`/feed`로 재진입 차단. `/onboarding` 라우트를 래퍼로 감쌈 | (신규) `components/OnboardingRoute.jsx`; `components/PrivateRoute.jsx`, `App.jsx` |
| **M-NEW-4** 커스텀 모달 통일 | 🟡 | 잔존 네이티브 `alert`/`window.confirm`을 전부 `AlertDialog`/`ConfirmDialog`로 교체. 확인 모달은 `{title,message,confirmLabel,danger,onConfirm}` 상태로 통일(클릭 시 닫고 액션 실행) | `pages/{Admin,Feed,PostDetail,Settings,Feedback}Page.jsx` |
| **§4** 한글 웹폰트 | 🟡 | `Pretendard`(variable dynamic-subset) 임포트 + 폰트 스택 최상단 명시. `font-mono`/`h1~h6`는 라틴 JetBrains Mono 유지하되 한글 글리프만 Pretendard로 폴백. `body`에 `-webkit-font-smoothing: antialiased` 등 렌더링 보정 → Windows "딱딱함" 개선 | `index.css`, `tailwind.config.js` |
| **§1-1** 복원 후 재숨김 루프 방지 | 🟡 | `Post.reviewed` 플래그 추가 — 관리자 복원 시 `restore()`가 `hidden=false`+`reviewed=true`. `ReportService` 자동 숨김 조건에 `!reviewed` 가드 → 복원 글은 재신고가 임계값을 넘어도 재자동숨김 안 됨. `AdminReportedPostResponse.reviewed` 노출 + AdminPage "검토완료" 배지 | `domain/Post`, `service/ReportService`, `dto/AdminReportedPostResponse`, `pages/AdminPage.jsx`, `test/{Report,Admin}ServiceTest` |

**기록 문서**: `MoreDevelopments_V2.md` 상단 Phase C 섹션.

---

## ✅ Phase D — 운영 품질 (Flyway 제외 완료)

검증: `backend`에서 `./gradlew test` **BUILD SUCCESSFUL**(신규 테스트 — 조회수 dedup 3종, 삭제 통합(@DataJpaTest 실 FK), 전체 컨텍스트+보안 스모크 포함).

| 항목 | 등급 | 핵심 변경 | 신규/수정 파일 |
|---|---|---|---|
| **M-NEW-5** 조회수 서버단 중복 제거 | 🟡 | `getPost`가 **작성자 본인 조회 제외 + 동일 유저 24h 내 1회**만 카운트. 조회 이력 `PostView`(`(post_id,user_id)` 유니크) 신규 — 최초 조회 시 기록·증가, 24h 경과 시 갱신·증가, 그 전엔 미증가. 원자적 벌크 UPDATE 유지하되 응답 표시값만 +1(관리 엔티티 미변경→2중증가 방지). 삭제 경로에 `PostView` 정리 추가(post_id FK) | (신규) `domain/PostView`, `repository/PostViewRepository`; `service/PostService`, `dto/PostResponse`(명시 viewCount 팩토리), `test/PostServiceTest` |
| **삭제 통합 테스트**(C-NEW-1 회귀) | 🟡 | `@DataJpaTest`+H2 실 FK로 신고/좋아요/북마크/조회이력이 달린 글 삭제가 FK 위반 없이 성공함을 검증(단위 목으로는 못 잡던 회귀 방지) | (신규) `test/PostDeletionIntegrationTest` |
| **관측성**(Actuator + 요청 추적) | 🟡 | `spring-boot-starter-actuator` — `/actuator/health` 공개(LB/프로브용, 상세는 인증 시), liveness/readiness 프로브 활성. `RequestIdFilter`(최우선)가 요청마다 `requestId`를 MDC·응답헤더(X-Request-Id)로 부여 + 로그 패턴 `%X{requestId}`. 전체 컨텍스트+보안 필터 체인 스모크(헬스 공개 200 / 보호 API 401) | (신규) `security/RequestIdFilter`, `test/ApplicationSmokeTest`; `build.gradle`, `application.yml`, `security/{SecurityConfig,JwtAuthFilter}` |

> ✅ **M-NEW-7 Flyway는 2026-06-02 도입 완료**(아래 "✅ M-NEW-7 Flyway" 섹션). 베이스라인을 손으로 쓰지 않고 **Hibernate가 생성**해 `validate` 정합을 보장하는 방식으로, 보류 사유(정합 검증 리스크)를 해소했다. 기존 수동 DDL 스크립트(`backend/db/migration/*.sql`)는 `V1__baseline.sql`에 흡수·폐기. 테스트 폭 추가(컨트롤러 슬라이스·`CommentService` 단위)는 후속.

**기록 문서**: `MoreDevelopments_V2.md` 상단 Phase D 섹션.

---

## ✅ Refresh Token 도입 — Access/Refresh 분리 (2026-06-02, 커밋·푸시 완료)

Phase B에서 이연했던 "토큰 폐기(Refresh)"를 실무 표준 2토큰 구조로 구현. 단일 JWT(24h)를 **짧은 Access Token(30m, stateless) + DB 저장 Refresh Token(14d)**으로 분리.
검증: `backend`에서 `./gradlew.bat test` **BUILD SUCCESSFUL**(신규 `RefreshTokenServiceTest` 8종 + AuthService refresh 3종 + AccountService RT 폐기 검증 포함) · `frontend`에서 `npm run build` 성공.

| 항목 | 핵심 변경 | 신규/수정 파일 |
|---|---|---|
| **RT 엔티티/저장** | opaque 32B 난수 → **SHA-256 해시만 DB 저장**(원문 미저장). `(token_hash)` 유니크, `user_id` NOT NULL FK, `expires_at` 인덱스 | (신규) `domain/RefreshToken`, `repository/RefreshTokenRepository` |
| **발급/검증/회전** | `RefreshTokenService` — `issue`(발급)·`findValid`(조회+만료검사)·`rotate`(**삭제 후 재발급** = 1회용 회전)·`deleteByRawToken`(단일 세션)·`deleteAllForUser`(전체 폐기)·`deleteExpired`(정리) | (신규) `service/RefreshTokenService`, `dto/TokenPair` |
| **AT 30분 단축** | `JwtProvider` 수명을 `app.jwt.access-ttl`(기본 30m)로 외부화·단축(기존 24h 상수) | `security/JwtProvider` |
| **쿠키 2개 분리** | `access_token`(path=`/`, maxAge=AT TTL) + `refresh_token`(path=`/api/auth`, maxAge=RT TTL). 둘 다 HttpOnly·secure(프로파일)·SameSite=Lax | `util/CookieUtils` |
| **로그인/온보딩 발급** | 로그인 시 AT+RT 동시 발급(쿠키 2개). 온보딩(PENDING→ACTIVE)은 AT만 재발급(RT 유지) | `service/AuthService`, `controller/{Auth,Onboarding}Controller`, `dto/LoginResponse` |
| **재발급 엔드포인트** | `POST /api/auth/refresh` — RT 쿠키 검증 → 회전 + 새 AT 발급. **H-NEW-2와 동일하게 DB 상태 재검증**(ACTIVE/PENDING만, 휴면/탈퇴 거부). 실패 시 401 + 쿠키 만료 | `controller/AuthController`, `service/AuthService`, `security/{SecurityConfig,JwtAuthFilter}`(permit/skip) |
| **로그아웃/탈퇴/휴면 폐기** | 로그아웃=제시된 RT 삭제, 탈퇴/휴면=유저 RT 전체 삭제 → AT 자연만료(≤30m)+RT 즉시폐기 = 완전 무효화 | `controller/AuthController`, `service/AccountService`, `controller/AccountController` |
| **만료 정리 스케줄러** | `@EnableScheduling` + `@Scheduled`(기본 매일 04:00, `app.refresh-cleanup.cron`) 벌크 DELETE. 단일 인스턴스 전제 | (신규) `scheduler/RefreshTokenCleanupScheduler`, `CommunityApplication` |
| **프론트 인터셉터** | AT 401 → `/api/auth/refresh` 1회 호출 후 원요청 재시도, 동시 401은 단일 refresh로 큐잉(single-flight), 실패 시 로그인 | `frontend/src/api/client.js` |
| **prod 스키마** | `refresh_tokens`는 최초 수동 DDL 스크립트로 도입했으나, 직후 **M-NEW-7 Flyway 도입 시 `V1__baseline.sql`에 통합**(수동 스크립트 삭제) | (현재) `db/migration/V1__baseline.sql` |

**기록 문서**: `MoreDevelopments_V2.md`의 「Access/Refresh 토큰 분리」 섹션.
> ⓘ 위 표의 "prod 수동 DDL"은 도입 당시 기준. **현재는 Flyway가 스키마를 관리**하므로 별도 수동 DDL 불필요(아래 "M-NEW-7 Flyway" 참조).

---

## ✅ M-NEW-7 — Flyway 스키마 마이그레이션 (2026-06-02, 커밋·푸시 완료)

보류했던 Flyway를, **"베이스라인을 Hibernate가 생성"** 하는 방식으로 정합 리스크 없이 도입. **배포 전 수동 DDL 단계 제거.**
검증: `backend`에서 `./gradlew.bat test` **BUILD SUCCESSFUL**(Flyway 클래스패스 존재 + 테스트(H2)는 OFF 유지 확인).

| 항목 | 핵심 내용 | 파일 |
|---|---|---|
| **의존성** | `flyway-core` + `flyway-mysql` | `build.gradle` |
| **V1 베이스라인** | 현 엔티티 전체 스키마(10테이블 + enum/FK/unique). **`GenerateBaselineTest`로 Spring 네이밍전략+MySQL방언 DDL을 추출**해 그대로 사용 → `validate`와 정확히 일치 보장. RT 정리용 `expires_at` 인덱스 추가 | (신규) `src/main/resources/db/migration/V1__baseline.sql`, (신규·@Disabled) `test/GenerateBaselineTest` |
| **프로파일 설정** | dev/prod: `flyway.enabled=true` + `baseline-on-migrate=true` + `ddl-auto: validate`(dev는 update→validate 전환). 공통 기본 OFF, 테스트(H2)는 OFF(`@TestPropertySource`) | `application.yml`, `application-dev.yml`, `application-prod.yml`, `test/{ApplicationSmokeTest,PostRepositoryTest,PostDeletionIntegrationTest}` |
| **수동 DDL 폐기** | `backend/db/migration/*.sql`(Phase C/D·refresh_tokens) 삭제 → V1에 흡수. README는 Flyway 안내·전환 절차로 교체 | `backend/db/README.md` (삭제: 두 `*.sql`) |

- **동작**: 빈 DB(신규 prod) → V1 실행해 전체 생성 / 기존 DB(dev) → baseline-on-migrate가 V1을 "적용됨"으로만 기록(미실행, 스키마 보존). 이후 변경은 `V2__...`만 추가하면 dev·prod 자동 적용 + `validate` 재검증(이중 안전망).
- **검증 권장**: dev MySQL로 `bootRun` 1회 → `flyway_schema_history` 생성·`validate` 통과 확인. (실 MySQL 기동은 사용자 환경에서 최종 확인 필요 — 베이스라인은 Hibernate 생성이라 문법·정합은 보장)

**기록 문서**: `MoreDevelopments_V2.md` §3 M-NEW-7 + `backend/db/README.md`.

---

## ✅ Phase E — 기능 확장 (롤백 마커 적용)

§6 기능 백로그 착수. **여기부터는 롤백 마커**(`[FEATURE:이름]`)**+ 루트 `FEATURES.md` 인덱싱** 적용.

### markdown-rendering — 게시글 본문 마크다운 + 코드블록 (2026-06-02, 커밋·푸시 완료)
§6-5 A "개발 교육 커뮤니티 특화" 1순위. 게시글 본문 평문 → 마크다운 렌더링 + 코드블록 syntax highlight. **프론트 전용**(본문은 평문 그대로 저장·반환, 렌더링만 마크다운화 — 백엔드 무변경). 검증: `frontend`에서 `npm run build` 성공.

| 항목 | 핵심 | 파일 |
|---|---|---|
| 렌더러 | `react-markdown` + `remark-gfm`(표/취소선/체크) + `remark-breaks`(단일 줄바꿈 보존) + `rehype-highlight`(코드 하이라이트) | (신규) `components/Markdown.jsx`, `package.json` |
| 본문 적용 | `PostDetailPage` 평문 `<p>` → `<Markdown>` (`React.lazy`로 코드 분할 → 상세 페이지에서만 로드, 메인 번들 268KB 유지) | `pages/PostDetailPage.jsx` |
| 보안/정책 | 원시 HTML 미렌더(rehype-raw 미사용)로 XSS 차단 · 이미지 임베드 비활성(익명 IP 유출/어뷰즈 방지 → 링크 대체) · 외부 링크 `noopener/nofollow` | `components/Markdown.jsx` |
| 스타일 | 코드블록은 앱 테마 무관 어두운 "터미널" 룩, 인라인 코드는 테마 추종. `.markdown-body`/`.hljs-*` CSS | `index.css` |
| 작성 힌트 | 작성 폼 내용 라벨에 "마크다운 지원" 안내 | `components/PostForm.jsx` |

**롤백**: `FEATURES.md`의 `markdown-rendering` 절차(마커 제거 + 컴포넌트 삭제 + deps uninstall).
**후속 후보**: 댓글 마크다운(현재 단일 라인 input).

### qna-accept — Q&A 답변 채택/해결됨 (2026-06-02, 커밋·푸시 완료)
§6-5 A 2순위. QUESTION 글 작성자가 답변(댓글)을 채택 → "해결됨" 배지(StackOverflow식). **백엔드+프론트**. **Flyway V2 마이그레이션 첫 실전 적용**(새 워크플로 검증). 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(신규 `CommentServiceTest` 6종), frontend `npm run build` 성공.

| 항목 | 핵심 | 파일 |
|---|---|---|
| 데이터 | `Post.acceptedCommentId`(Long, FK 없음, null=미해결) + 도메인 메서드. **Flyway `V2__add_accepted_comment.sql`** | `domain/Post`, (신규) `db/migration/V2__...sql` |
| API | `POST /…/comments/{commentId}/accept` 토글. 서버 검증: QUESTION(400)+작성자(403)+댓글 소속(404). 채택 댓글 삭제 시 정리 | `service/CommentService`, `controller/CommentController`, (신규) `dto/AcceptAnswerResponse` |
| 응답 | `PostResponse.acceptedCommentId`, `PostListResponse.solved`(factory에서 post로부터 파생 — 호출부 무변경) | `dto/{PostResponse,PostListResponse}` |
| UI | 상세: "해결됨" 배지 + 댓글 채택 토글(작성자·QUESTION 한정)·"채택된 답변" 강조. 피드 카드 "해결됨" 배지 | `pages/PostDetailPage.jsx`, `components/PostCard.jsx` |
| 테스트 | 채택/토글해제/비작성자/비질문/타글댓글/삭제정리 6종 (CommentService 단위 테스트 잔여 항목도 겸함) | (신규) `test/CommentServiceTest` |

**롤백**: `FEATURES.md`의 `qna-accept` 절차.
**후속 후보**: 채택 시 답변자 알림.

### op-alias — 댓글 글쓴이(OP) 표시 + 글 단위 익명 별칭 (2026-06-02, 커밋·푸시 완료)
§6-5 B 1순위. 닉네임 비유일성(H-anon)으로 스레드에서 글쓴이/동일인 식별이 혼동되는 문제를 **글 단위 일관 익명 별칭**(에타식)으로 해결. **별칭은 서버에서 `user_id` 기준 계산**(프론트에 신원 미노출). 글쓴이 → "글쓴이", 그 외는 첫 등장 순 "익명1·2…"(같은 유저=같은 별칭). 사용자 결정으로 **댓글 스레드에서 닉네임을 별칭으로 대체**(기수·캠퍼스 유지), 피드·상세 헤더 닉네임은 유지. 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(신규 `CommentServiceAliasTest` 4종), frontend `npm run build` 성공(메인 번들 269KB 유지).

| 항목 | 핵심 | 파일 |
|---|---|---|
| 별칭 계산 | `buildAliasMap(postAuthorId, ordered)` — OP=글쓴이, 나머지 첫 등장 순 익명N. `getComments`는 OP id 필요로 `existsById`→`findById` 전환, `addComment`는 글 전체 1회 재조회로 새 댓글 별칭 계산 | `service/CommentService` |
| 응답 | `CommentResponse.alias`(글쓴이/익명N) + `op`(글쓴이 여부, JSON `isAuthor`). boolean `isAuthor` 필드는 `author` 게터와 충돌해 Lombok 미생성 → 필드명 `op`+`@JsonProperty("isAuthor")` | `dto/CommentResponse` |
| UI | 댓글 작성자 줄 닉네임 → `alias`로 대체, 글쓴이는 primary·굵게 강조. 기수·캠퍼스·시간 유지 | `pages/PostDetailPage.jsx` |
| 테스트 | getComments 별칭 부여/없는글, addComment 글쓴이/타인 첫댓글 4종 | (신규) `test/CommentServiceAliasTest` |

**롤백**: `FEATURES.md`의 `op-alias` 절차(스키마 무변경).
**후속 후보**: 댓글 페이로드의 비-OP 닉네임 제거(진짜 페이로드 익명화), 별칭의 피드/게시글 확장.

### cohort-campus-lounge — 기수/캠퍼스 스코프 필터·라운지 (2026-06-02, 커밋·푸시 완료)
§6-5 B 2순위. 프로필에만 노출하던 `cohort`/`campus`를 **피드 스코프 필터**로 확장("우리 캠퍼스"·"동기"). **익명 유지·범위만 한정**(서버가 현재 유저 기준으로 해석 — 프론트는 `scope=campus|cohort`만 전달). 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(신규 `PostLoungeRepositoryTest` 4종 + `PostServiceTest` scope 3종), frontend `npm run build` 성공(메인 271KB).

| 항목 | 핵심 | 파일 |
|---|---|---|
| 쿼리 | `findFilteredLatest`/`findFilteredPopular`에 null-guard `cohort`/`campus` 필터 추가(카테고리·검색·정렬과 직교) | `repository/PostRepository` |
| scope 해석 | `getAllPosts`가 라운지 scope일 때만 유저 로드 → `me.getCampus()/getCohort()`를 필터로. 컨트롤러·서비스 시그니처 불변. `@NotBlank`라 ACTIVE 유저는 항상 값 보유 | `service/PostService` |
| UI | 사이드바 `LOUNGE` 섹션(우리 캠퍼스/동기 + 값 표기), 피드 뷰 라벨(`우리 캠퍼스 · 서울`/`동기 · 10기`) | `components/Sidebar.jsx`, `pages/FeedPage.jsx` |
| 테스트 | 캠퍼스/기수/전체/인기순 필터 4종(@DataJpaTest) + scope→repo 와이어링 3종(단위) | (신규) `test/repository/PostLoungeRepositoryTest`, `test/service/PostServiceTest`(scope 블록) |

**롤백**: `FEATURES.md`의 `cohort-campus-lounge` 절차(스키마 무변경 — 기존 `users.cohort/campus` 재사용).
**후속 후보**: 타 캠퍼스/기수 브라우징, 캠퍼스+기수 동시 필터, 라운지 전용 게시판.

### nested-comments — 대댓글(1-depth 답글) (2026-06-02, 커밋·푸시 `e3c3995`)
§6-1. 댓글에 1단계 답글(에타식). **백엔드 + 프론트**, **Flyway V3**. 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(신규 `CommentNestedServiceTest` 5종 + 기존 댓글 테스트 그린), frontend `npm run build` 성공(메인 273KB).

| 항목 | 핵심 | 파일 |
|---|---|---|
| 데이터 | `Comment.parentId`(Long, FK 없음, null=최상위) + **Flyway V3**(`comments.parent_id`+index) | `domain/Comment`, (신규) `db/migration/V3__add_comment_parent.sql` |
| 작성 | `POST …/comments` 본문 `parentId`(선택). 검증: 부모 존재·동일 글·**1-depth**(답글에 답글 금지 400) | `dto/CommentCreateRequest`, `service/CommentService`(addComment) |
| 삭제/채택 | 최상위 삭제 시 답글 정리(`deleteByParentId`), **답글은 채택 불가**(toggleAccept 가드) | `service/CommentService`, `repository/CommentRepository` |
| 알림 | 답글 → 부모 댓글 작성자(`notifyReply`, COMMENT 타입 재사용으로 enum 마이그레이션 회피) | `service/NotificationService` |
| 응답/UI | `CommentResponse.parentId`. 프론트는 평면 목록 그룹핑 + `renderComment` 리팩터링(답글 들여쓰기·답글 버튼/입력, 채택은 최상위만) | `dto/CommentResponse`, `pages/PostDetailPage.jsx` |
| 테스트 | 답글 생성/2depth 거부/타글부모 거부/삭제 정리/답글 채택 거부 5종 | (신규) `test/service/CommentNestedServiceTest` |

**롤백**: `FEATURES.md`의 `nested-comments` 절차(V3 컬럼·인덱스 drop 포함). ⚠️ PostDetailPage는 `renderComment` 리팩터링이 평면 map(qna+op 마커 포함)을 대체 → 롤백 시 평면 map 환원.
**후속 후보**: 답글 삭제 시 soft-delete placeholder(타인 답글 보존), REPLY 알림 타입 분리, 멘션.

### poll — 익명 투표/설문 (2026-06-02, 커밋·푸시 `2f1ef16`)
§6-5 B ☆. 게시글에 선택적 익명 투표(집계만 노출 → 익명 보드에 적합). **백엔드 + 프론트**, **Flyway V4**. 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(신규 `PollServiceTest` 11종 + PostService/삭제통합 갱신), frontend `npm run build` 성공(메인 277KB).

| 항목 | 핵심 | 파일 |
|---|---|---|
| 데이터 | `poll_options`(post_id·content·sort_order) + `poll_votes`(post_id·option_id[FK없음]·user_id·**unique(post_id,user_id)**). **Flyway V4** | (신규) `domain/PollOption`·`PollVote`, `db/migration/V4__add_poll.sql` |
| 익명/1인1표 | user_id로 중복 방지하되 노출은 보기별 집계만(누가 뭘 골랐는지 미노출) | `repository/PollVoteRepository` |
| 작성/검증 | `PostCreateRequest.pollOptions`(선택) → `PollService.createOptions` 2~8개·100자 검증 | `dto/PostCreateRequest`, `service/PollService` |
| 투표 | `POST …/poll/vote {optionId}` 토글(신규/변경/같은 보기=취소), 동시성 유니크 흡수 | `controller/PollController`, `service/PollService` |
| 응답/배지 | `PostResponse.poll`(보기·집계·myOptionId; 없으면 null), `PostListResponse.hasPoll` | `dto/PostResponse`·`PostListResponse`, `service/PostService` |
| UI | 작성 폼 투표 토글+보기 입력(작성만), 상세 막대그래프+퍼센트+클릭 투표, 피드 "투표" 배지 | `components/PostForm`·`PostCard`, `pages/PostCreatePage`·`PostDetailPage` |
| 테스트 | createOptions 검증·vote 신규/변경/취소·잘못된 보기·집계 11종 | (신규) `test/service/PollServiceTest` |

**롤백**: `FEATURES.md`의 `poll` 절차(V4 테이블 drop 포함).
**후속 후보**: 멀티 선택, 투표 전 결과 숨김, 마감일, 투표 수정.

### unread-new — 읽음 표시 / 안 읽은 새 글 배지 (2026-06-02, 커밋·푸시 `45a3567`)
§6-5 C ☆. 피드에서 안 읽은 새 글 "NEW" 배지 + 이미 연 글 흐리게. **기존 `PostView` 재사용 → 스키마/Flyway 변경 없음.** 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(`PostServiceTest`에 isNew/isRead 1종 추가, 11종), frontend `npm run build` 성공(메인 277KB).

| 항목 | 핵심 | 파일 |
|---|---|---|
| 판정 | `isRead`=PostView 존재(연 적 있음), `isNew`=작성자 아님+미열람+최근(7일). 글 열면 PostView 기록→다음 로드에 읽음 처리 | `service/PostService`(getAllPosts), `repository/PostViewRepository`(`findViewedPostIds` 배치) |
| 응답/UI | `PostListResponse.isNew`/`isRead`. PostCard "NEW" 배지 + 읽은 글 제목 흐리게 | `dto/PostListResponse`, `components/PostCard.jsx` |
| 테스트 | 미열람+최근=NEW / 열람=읽음 / 본인 글·7일 초과=NEW 아님 | `test/service/PostServiceTest` |

**롤백**: `FEATURES.md`의 `unread-new` 절차(스키마/신규 파일 없음).
**후속 후보**: 피드 노출만으로 읽음 처리, 마지막 방문 기준 "새 글 N개" 요약, 상세 페이지 읽음 표시.

### reactions — 다양한 반응(좋아요/도움돼요/정보/공감) (2026-06-02, 커밋·푸시 `cdd7d4d`)
§6-5 C. 단일 좋아요 → 4종 반응(1인 1반응·단일 선택). **기존 post_likes 재사용**(+reaction_type), **Flyway V5**. 검증: backend `./gradlew.bat test` **BUILD SUCCESSFUL**(`PostServiceTest` react 3종 + 기존 getPost/getAllPosts 스텁 갱신), frontend `npm run build` 성공(메인 278KB), V5 DDL 클론 테이블 검증.

| 항목 | 핵심 | 파일 |
|---|---|---|
| 데이터 | `post_likes.reaction_type` enum(4종, 기존 행=LIKE) + (post_id,user_id) 유니크 유지=1인 1반응. **Flyway V5** | `domain/PostLike`(+`ReactionType`), `db/migration/V5__add_reaction_type.sql` |
| API/토글 | `POST …/reactions {type}` — 같은 종류=취소·다른 종류=변경·신규=+알림. 기존 `/like` 대체 | `controller/PostController`, `service/PostService.react`, `NotificationService.notifyReaction` |
| 응답 | 상세 `PostResponse.reactions`(종류별 집계+myReaction), 피드 `PostListResponse.reactionTotal`+`myReaction`. isLiked/likeCount 제거 | `dto/ReactionResponse`·`ReactionRequest`(신규), `PostResponse`·`PostListResponse` |
| UI | 상세 반응 바(이모지+라벨+수·토글), 피드 총 반응 수 | `pages/PostDetailPage.jsx`, `components/PostCard.jsx` |
| 테스트 | react 신규/취소/변경 3종 | `test/service/PostServiceTest`(react 블록) |

**롤백**: `FEATURES.md`의 `reactions` 절차(단일 좋아요로 환원 — 비교적 큰 revert, V5 컬럼 drop 포함).
**후속 후보**: 멀티 선택, 댓글 반응, 반응 종류별 알림.

### Phase E 후속 — 사용자 보고 UX 버그픽스 3종 (2026-06-02)
사용자 사용 중 보고된 불편 3건. **프론트 전용·영구 수정**(롤백 마커 없음). 검증: `npm run build` 성공.

| # | 증상 | 원인 | 수정 |
|---|---|---|---|
| 1 (치명) | 라운지(우리 캠퍼스/동기)에서 일부 카테고리 글만 보이거나 빈 화면. 탭 전환 시 빈발 | `category` 선택은 `scope`를 all로 리셋했으나 **scope 선택은 직전 category를 안 건드려** 라운지에 stuck category가 필터로 남음(예: cohort+JOB만 조회) | `FeedPage`에 `handleSelectScope` 추가 — 스코프(라운지·내 글·스크랩) 선택 시 `category=all` 리셋. 라운지=모든 카테고리 독립 뷰로 통일 |
| 2 | 기수/캠퍼스 목록 부정확 | 온보딩 목록이 옛 데이터(`11~14기`) | `OnboardingPage` 기수 `14·15·16기`로 정리, **16기는 노출·비활성**(클릭 시 AlertDialog "현재 모집중입니다. 추후에 업데이트 하겠습니다."), 캠퍼스 `서울·대전·광주·부울경·구미` 순 정리. 백엔드 무변경(문자열) |
| 3 | `/settings` 로그아웃이 확인 없이 즉시 실행(`/feed`는 확인 모달) | 설정 페이지 로그아웃이 `handleLogout` 직접 호출 | `SettingsPage`에 `requestLogout`(ConfirmDialog "정말 로그아웃 하시겠습니까?") 경유로 통일 |

---

## ⏳ 이연/후속 항목 (의도적 보류)

| 항목 | 사유 | 출처 |
|---|---|---|
| ~~토큰 폐기 — **Access 단축 + Refresh DB 저장**~~ → ✅ **구현 완료(2026-06-02)** | 짧은 AT(30m) + DB 저장 RT(14d) + `/api/auth/refresh` 회전 + 만료 정리 스케줄러 도입(위 "Refresh Token 도입" 섹션). H-NEW-2 상태 재검증과 결합해 완전 무효화 | Phase B |
| ~~**M-NEW-5/M-2** 조회수 서버단 중복제거~~ → ✅ **Phase D 완료** | 작성자 제외 + 24h dedup(`PostView`)로 서버단 정식 처리 | V2 §3 |
| ~~삭제 **통합 테스트**(@DataJpaTest 실제 FK)~~ → ✅ **Phase D 완료** | `PostDeletionIntegrationTest`로 실 FK 회귀 검증 | V2 §8 |

---

## 🔜 남은 작업

> 즉시 다음 행동은 상단 [▶ 다음 작업 (바로 이어서 시작)](#-다음-작업-바로-이어서-시작) 참조. 아래는 전체 백로그.
> **Phase 0~D + Access/Refresh + Flyway + Phase E 4기능 모두 커밋·푸시 완료**(`origin/main` 최신 `72667be`).

### 테스트 폭 (상시)
- 컨트롤러 슬라이스(@WebMvcTest), 인가 케이스 확장. (단위[CommentService·RefreshTokenService 등]·삭제 통합·헬스/인가 스모크는 완료)

### Phase E / §6 — 기능 확장 (롤백 마커 적용 대상)
- ✅ **마크다운+코드블록**(완료 — `FEATURES.md` markdown-rendering).
- ✅ **Q&A 채택/해결됨**(완료 — `FEATURES.md` qna-accept).
- ✅ **글쓴이(OP) 표시·글 단위 익명 별칭**(완료 — `FEATURES.md` op-alias).
- ✅ **기수·캠퍼스 스코프 필터·라운지**(완료 — `FEATURES.md` cohort-campus-lounge).
- ✅ **대댓글(1-depth 답글)**(완료·커밋 `e3c3995` — `FEATURES.md` nested-comments, Flyway V3).
- ✅ **익명 투표/설문**(완료·커밋 `2f1ef16` — `FEATURES.md` poll, Flyway V4).
- ✅ **읽음 표시/안 읽은 새 글 배지**(완료·커밋 `45a3567` — `FEATURES.md` unread-new, 스키마 무변경·PostView 재사용).
- ✅ **다양한 반응(좋아요/도움돼요/정보/공감)**(완료·커밋 `cdd7d4d` — `FEATURES.md` reactions, Flyway V5).
- ⭐ **주간 인기글 다이제스트(weekly-digest)** — 스펙 확정, **내일 구현 예정**(위 [🗓️ 계획](#️-계획--주간-인기글-다이제스트-weekly-digest-내일-구현) 섹션).
- 남음(§6 백로그): 모더레이션 강화(소프트삭제+휴지통·감사 로그·강제 숨김), 북마크 폴더/메모, 알림 확장/실시간, MM DM 연동 등. (스터디/팀원 모집은 익명 보드 특성상 제외.) 상세는 V2 §6.

---

## 🔧 신규 환경변수 / 설정 (Phase A·B + Access/Refresh)

| 키(환경변수) | 프로퍼티 | 기본값 | 비고 |
|---|---|---|---|
| `APP_CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | `http://localhost:5173` | **운영 필수** — 미설정 시 실제 도메인이 CORS로 차단 |
| `LOGIN_RATE_MAX_ATTEMPTS` | `app.login-rate-limit.max-attempts` | `5` | 윈도 내 허용 실패 횟수 |
| `LOGIN_RATE_WINDOW_SECONDS` | `app.login-rate-limit.window-seconds` | `300` | 카운트 윈도(초) |
| `LOGIN_RATE_BLOCK_SECONDS` | `app.login-rate-limit.block-seconds` | `600` | 차단 시간(초) |
| `MM_CONNECT_TIMEOUT_MS` | `app.mattermost.connect-timeout-ms` | `3000` | MM connect 타임아웃 |
| `MM_READ_TIMEOUT_MS` | `app.mattermost.read-timeout-ms` | `5000` | MM read 타임아웃 |
| `APP_ADMIN_BOOTSTRAP_USERNAMES` | `app.admin.bootstrap-usernames` | (빈 값) | 콤마 구분 MM username, 로그인 시 ADMIN 승격 |
| `JWT_SECRET` | `jwt.secret` | (없음, 필수) | **이제 ≥ 32바이트 필수** — 미달 시 기동 실패(fail-fast) |
| `APP_JWT_ACCESS_TTL` | `app.jwt.access-ttl` | `30m` | Access Token 수명(JWT exp + access_token 쿠키 maxAge). Duration 표기(`30m`,`1h`) |
| `APP_JWT_REFRESH_TTL` | `app.jwt.refresh-ttl` | `14d` | Refresh Token 수명(DB `expires_at` + refresh_token 쿠키 maxAge) |
| `APP_REFRESH_CLEANUP_CRON` | `app.refresh-cleanup.cron` | `0 0 4 * * *` | 만료 RT 정리 스케줄러 cron(기본 매일 04:00). 단일 인스턴스 전제 |

---

## ⚠️ 운영/배포 주의사항

1. **`JWT_SECRET`은 32바이트 이상**이어야 기동됨(HS256 256bit). 짧으면 부팅 단계에서 명확한 메시지로 실패.
2. **운영 배포 시 `APP_CORS_ALLOWED_ORIGINS`에 실제 프론트 도메인**을 반드시 지정.
3. **최초 관리자**: `APP_ADMIN_BOOTSTRAP_USERNAMES`에 MM **username**(loginId/email 아님) 지정 → 해당 계정이 로그인하면 ADMIN 승격(로그인 시점).
4. **H-NEW-4 리포 위생 — 커밋·푸시 완료**(2026-06-01): node_modules·.idea 추적 해제 + Figma mock 제거를 `83df359`(위생) / Phase A~D 소스를 `9a3bd3e`로 분리 커밋해 `origin/main` 반영. 작업트리의 node_modules는 보존(ignore됨).
5. **계정 휴면/탈퇴 시** 해당 계정의 기존 토큰은 즉시 거부됨(H-NEW-2, 의도된 동작). **추가로 휴면/탈퇴 시 해당 유저의 Refresh Token 전체를 DB에서 삭제**하므로 재발급도 불가(완전 무효화).
6. **헬스체크(Phase D)**: `GET /actuator/health`는 **인증 없이 공개**(LB/오케스트레이터 프로브용). 상세 컴포넌트는 인증 시에만 노출. `liveness`/`readiness` 프로브는 `/actuator/health/{liveness,readiness}`.
7. ✅ **스키마 마이그레이션은 Flyway가 자동 처리(M-NEW-7)** — 더 이상 배포 전 수동 DDL 불필요. dev/prod 기동 시 `classpath:db/migration`의 `V*__*.sql`을 적용하고 `ddl-auto: validate`로 재검증한다. 마이그레이션 추가는 `backend/src/main/resources/db/migration/V{N}__...sql`. (기존 수동 스크립트는 `V1__baseline.sql`에 흡수·삭제 — `backend/db/README.md` 참고.)
   - 현 스키마: `posts.reviewed`, `post_views`, `refresh_tokens`는 V1 베이스라인 / `posts.accepted_comment_id`는 **V2** / `comments.parent_id`(+index)는 **V3** / `poll_options`·`poll_votes`는 **V4** / `post_likes.reaction_type`(enum)는 **V5**. (V4·V5는 수기 DDL — 엔티티와 컬럼/타입 일치, 실 MySQL `validate` 최종 확인 권장.)
   - 조회수 동작: 작성자 본인 조회 미집계 + 동일 유저 24h 1회 집계.
   - ⚠️ 기존 prod가 V1보다 뒤처졌다면(누락 테이블 존재) baseline 전 1회 전환 작업 필요 — `backend/db/README.md` 「기존 prod 전환」.
8. **토큰 수명 변경**: Access Token이 **24h → 30m**로 단축됨. 프론트는 401 시 `/api/auth/refresh`로 자동 재발급(`api/client.js`)하므로 사용자 체감 영향 없음. `refresh_tokens` 테이블은 Flyway V1이 생성하므로 별도 수동 작업 불필요(7번).

---

## ✅ 검증 방법

- 백엔드: `cd backend && ./gradlew.bat test` (현재 BUILD SUCCESSFUL). 실행은 dev 프로파일 + MySQL + 환경변수(`DB_HOST/DB_NAME/DB_USERNAME/DB_PASSWORD/JWT_SECRET`) 후 `./gradlew.bat bootRun`.
  - **dev `bootRun` 시 Flyway가 `V1`(baseline-on-migrate로 기존 DB는 기록만)·`V2`를 자동 적용** → `flyway_schema_history` 확인. 엔티티 변경 시 `V*__...sql` 누락하면 `validate` 단계에서 기동 실패(의도된 조기 검출).
- 프론트: `cd frontend && npm install && npm run dev` (Vite proxy `/api`→`localhost:8080`), 빌드 확인 `npm run build`(마크다운 라이브러리는 `React.lazy`로 분리 — 메인 번들 ~269KB).

---

## 롤백 마커 규약

기능 확장(§6) 코드는 추후 롤백이 쉽도록 다음 규약을 적용합니다(Phase 0/A/B는 수정이라 미적용):

- 코드 블록을 주석 마커로 감싼다: `// [FEATURE:기능명] … // [/FEATURE:기능명]`
- 루트 `FEATURES.md`에 기능명·파일·범위·롤백 절차를 인덱싱 → 마커 검색만으로 일괄 제거 가능.

> 참고: 실제 연동 프론트는 `frontend/src`(유일한 프론트엔드)입니다. 디자인 참고용 Figma export mock(`frontend/사내 블라인드 웹 어플리케이션`)은 2026-06-01 저장소에서 제거(`83df359`) — git 이력에는 남아 있음.
