# MoreDevelopments V2 — 2차 코드 리뷰 · 배포 전 점검 · 발전 과제

> 1차 리뷰([MoreDevelopments.md](./MoreDevelopments.md)) 반영분(Phase 0: 프론트 UX 버그 5종 + 커스텀 모달)을 확인한 뒤, 코드베이스 전체를 다시 검토한 **2차 리뷰 문서**입니다.
> 본 문서는 **분석/계획 문서**이며 실제 코드 수정은 포함하지 않습니다. (리뷰어는 평가만 수행)

- 작성일: 2026-05-31
- 검토 범위: `backend/src` 전체, `frontend/src` 전체, 설정/빌드/리포 위생
- 운영 완성도 재평가: **약 70~75%** (1차 65~70% → 프론트 안정화로 소폭 상승. 다만 아래 🔴 신규 발견으로 **현재 상태로는 배포 불가**)
  - > ✅ **업데이트(2026-06-01)**: 본 문서 작성 후 **Phase A·B·C 완료** — 배포 블로커(🔴) 전부 해소 + 보안/계정 하드닝 + UX/정책 마감. 현 추정 **약 85~88%**. 상단 Phase A/B/C 섹션 및 §5 체크리스트 참조.

---

## 0. 심각도 기준

| 등급 | 의미 | 조치 |
|------|------|------|
| 🔴 CRITICAL | 핵심 기능 미동작 / 보안 취약점 / 데이터 손실 | 배포 전 **반드시** 수정 |
| 🟠 HIGH | 버그·중대한 품질 이슈 | 배포 전 수정 권장 |
| 🟡 MEDIUM | 유지보수/운영/UX 품질 | 수정 검토 |
| ⚪ LOW | 스타일/사소한 제안 | 선택 |

---

## ✅ Phase A — 배포 차단 해소 (완료: 2026-06-01)

§9 로드맵의 Phase A(§5 체크리스트 1~6)를 구현했습니다. 모두 **리뷰 반영 수정**이라 영구 반영(롤백 마커 없음). 검증: `backend`에서 `./gradlew test` **BUILD SUCCESSFUL**(전체 테스트 통과, `@Query` 삭제 메서드는 `@DataJpaTest` 부트스트랩에서 JPQL 파싱 검증).

| 항목 | 등급 | 상태 | 핵심 변경 |
|------|------|------|-----------|
| **C-NEW-1** 관리자 영구삭제 FK | 🔴 | ✅ | 하드삭제 채택. `PostService.deletePost`가 삭제 전 자식 정리(`reportRepository`/`postLikeRepository`/`bookmarkRepository`/`notificationRepository`의 `deleteByPostId`) 후 `delete`. 4개 repo에 `@Modifying @Query` 추가. `PostServiceTest`에 `InOrder`로 "자식 정리 → 글 삭제" 순서 검증 추가(회귀 방지). |
| **C-NEW-2** 로그인 레이트리밋 | 🔴 | ✅ | `LoginRateLimiter`(인메모리, IP·loginId 키, 기본 5회/300초 윈도/600초 차단) 신규. `AuthController.login`에서 시도 전 검사·실패만 집계·성공 시 리셋. 초과 시 `TooManyRequestsException`→**429**, 실패 audit 로깅. *(단일 인스턴스 전제 — 수평 확장 시 Redis로 교체. Bucket4j 미도입.)* |
| **M-NEW-2** CORS 외부화 | 🔴 | ✅ | `app.cors.allowed-origins`(콤마 구분) 프로퍼티로 외부화. 운영은 `APP_CORS_ALLOWED_ORIGINS`로 실제 도메인 지정. |
| **H-NEW-4** 리포 위생 | 🟠 | ✅ | `git rm -r --cached frontend/node_modules .idea backend/.idea`(추적 해제, 작업트리 보존). `.gitignore`에 blanket `.idea/` 추가. **2026-06-01 커밋·푸시 완료**(`83df359` — Figma mock·루트 고아 package-lock 정리 동반, 추적 파일 ~4,000→137). |
| **H-NEW-1** 입력 길이 `@Size` | 🟠 | ✅ | `PostCreate/Update`(제목≤200, 본문≤10000), `Comment`(≤1000), `Feedback`(제목≤200, 본문≤5000), `Onboarding`(각 ≤20). 위반 시 기존 핸들러로 **400**. |
| **H-NEW-3** MM 타임아웃 | 🟠 | ✅ | `MattermostClient` `RestTemplate`에 connect(3s)/read(5s) 타임아웃(외부화). 타임아웃·연결 실패 → `MattermostUnavailableException`→**503**(빠른 실패). |
| **M-NEW-3** 알림 고아 데이터 | 🟡 | ✅ | C-NEW-1 삭제 경로에서 `notificationRepository.deleteByPostId`로 함께 정리. |

> ✅ **H-NEW-2는 Phase B에서 반영됨** (아래 Phase B 현황 참조).

---

## ✅ Phase B — 보안/계정 하드닝 (완료: 2026-06-01)

§9 Phase B 중 계정/토큰 하드닝 3종을 구현했습니다. 모두 **리뷰 반영 수정**(영구, 롤백 마커 없음). 검증: `./gradlew test` **BUILD SUCCESSFUL**(신규 승격 테스트 2개 포함 전체 통과).

| 항목 | 등급 | 상태 | 핵심 변경 |
|------|------|------|-----------|
| **H-NEW-2** 토큰 계정 상태 재검증 | 🟠 | ✅ | `JwtAuthFilter`가 claim이 아닌 **DB의 현재 `UserStatus`로 재검증** — PENDING은 온보딩/`auth/me`만 허용, ACTIVE만 통과, **DORMANT/WITHDRAWN은 401**. 탈퇴/휴면 처리 후 탈취된 토큰 사본이 만료 전이라도 즉시 무효화. (`findById`는 기존에도 매 요청 수행 → 추가 쿼리 없음) |
| **JWT_SECRET 길이 검증** | 🟡 | ✅ | `JwtProvider`를 생성자 주입으로 전환 — **256bit(32byte) 미만이면 기동 시 `IllegalStateException`**(fail-fast). 서명 키 1회 생성. |
| **M-NEW-6** 최초 ADMIN 부트스트랩 | 🟡 | ✅ | `app.admin.bootstrap-usernames`(콤마 구분 MM username) 지정 계정을 **로그인 시 ADMIN 승격**(`User.promoteToAdmin()`). `AuthServiceTest`에 승격/미승격 테스트 추가. |

> 🔜 **토큰 폐기 — Access 단축 + Refresh DB 저장: 2026-06-02 구현 예정**: H-NEW-2의 DB 상태 재검증으로 **탈퇴·휴면 시 즉시 무효화(실질적 폐기)는 이미 달성**. 추가로 **짧은 Access(30m, stateless) + DB 저장 Refresh(긴 수명) + 만료 정리 스케줄러** 구조를 도입 예정. 상세 설계는 위 「(예정) Access/Refresh 토큰 분리」 섹션 참조.

---

## ✅ Phase C — UX/정책 마감 (완료: 2026-06-01)

§9 Phase C(UX/정책)를 구현했습니다. 모두 **리뷰 반영 수정**(영구, 롤백 마커 없음). 검증: `frontend` `npm run build` 성공 · `backend` `./gradlew test` **BUILD SUCCESSFUL**.

| 항목 | 등급 | 상태 | 핵심 변경 |
|------|------|------|-----------|
| **M-NEW-1** PENDING 라우팅 가드 | 🟡 | ✅ | `PrivateRoute`가 `user.status==='PENDING'`이면 `/onboarding`으로 강제 리다이렉트(빈 화면 대신 온보딩 유도). 역가드 `OnboardingRoute` 신규 — 미인증→`/login`, 이미 온보딩 완료(ACTIVE)→`/feed`로 재진입 차단. `/onboarding` 라우트를 래퍼로 감쌌다. (DORMANT/WITHDRAWN은 백엔드가 401로 차단 → user=null → 미인증 분기) |
| **M-NEW-4** 커스텀 모달 통일 | 🟡 | ✅ | AdminPage뿐 아니라 잔존 네이티브 `alert`/`window.confirm`을 **전부** `AlertDialog`/`ConfirmDialog`로 교체(AdminPage·FeedPage·PostDetailPage·SettingsPage·FeedbackPage). 확인 모달은 `{title,message,confirmLabel,danger,onConfirm}` 상태 패턴으로 통일(클릭 시 닫고 비동기 액션 실행). 건의함 전송 완료는 모달 확인 시 `/feed` 이동. |
| **§4** 한글 웹폰트 | 🟡 | ✅ | `Pretendard`(variable, dynamic-subset) CDN 임포트 + 폰트 스택 최상단 명시. `font-mono`/`h1~h6`는 브루탈리즘 라틴 룩을 위해 JetBrains Mono를 1순위로 두되 **한글 글리프만 Pretendard로 글리프 단위 폴백**. `body`에 `-webkit-font-smoothing: antialiased` + `text-rendering: optimizeLegibility` 등 렌더링 보정 → Windows "딱딱함" 해소. |
| **§1-1** 복원 후 재숨김 루프 방지 | 🟡 | ✅ | `Post.reviewed` 플래그 추가. 관리자 복원 시 `restore()`가 `hidden=false`+`reviewed=true`로 검수 완료를 표시. `ReportService` 자동 숨김 조건에 `!post.isReviewed()` 가드 → **복원 글은 신고가 다시 임계값(≥5)을 넘어도 재자동숨김되지 않음**. `AdminReportedPostResponse.reviewed` 노출 + AdminPage에 "검토완료" 배지. (DB NOT NULL 미지정 — 기존 `hidden`/`category`와 동일하게 ddl-auto update 호환) |

---

## ✅ Phase D — 운영 품질 (Flyway 제외 완료: 2026-06-01)

§9 Phase D 중 **M-NEW-5(조회수)·삭제 통합 테스트·관측성**을 구현했습니다. 모두 **리뷰 반영 수정**(영구). 검증: `backend` `./gradlew test` **BUILD SUCCESSFUL**(신규 테스트 — 조회수 dedup 3종, 삭제 통합 @DataJpaTest, 전체 컨텍스트+보안 스모크 포함). **M-NEW-7 Flyway는 보류(아래 결정 필요)**.

| 항목 | 등급 | 상태 | 핵심 변경 |
|------|------|------|-----------|
| **M-NEW-5** 조회수 서버단 중복 제거 | 🟡 | ✅ | `PostService.getPost`가 **작성자 본인 조회는 제외**하고, 동일 유저는 **24h 내 1회만** 카운트. 조회 이력 엔티티 `PostView`(`(post_id,user_id)` 유니크) 신규 — 최초 조회 시 이력 생성·증가, 24h 경과 시 이력 갱신·증가, 그 전엔 미증가(동시 최초 조회는 유니크 제약으로 1회만). 조회수는 원자적 벌크 UPDATE를 유지하되 관리 엔티티는 변경하지 않고 응답 표시값만 +1(dirty checking 2중증가 방지). 삭제 경로(C-NEW-1)에 `PostView` 정리 추가(post_id FK). 숨김 글은 비관리자 접근 시 증가도 막도록 순서 보정. |
| **삭제 통합 테스트** | 🟡 | ✅ | `@DataJpaTest`+H2(MySQL 모드) **실 FK**로, 신고/좋아요/북마크/조회이력이 달린 글의 삭제가 FK 위반 없이 성공함을 검증. 단위 테스트(Mockito 목)는 FK를 강제하지 않아 못 잡던 C-NEW-1 회귀를 방지. (`PostService`는 리포지토리만 주입해 수동 구성, 알림은 목) |
| **관측성** | 🟡 | ✅ | `spring-boot-starter-actuator` 도입 — `/actuator/health` 공개(LB/오케스트레이터 헬스체크, 상세는 `when_authorized`), liveness/readiness 프로브 활성. 그 외 엔드포인트는 비노출. `RequestIdFilter`(최우선)가 요청마다 `requestId`를 MDC·응답헤더(`X-Request-Id`)로 부여하고 로그 패턴에 `%X{requestId}` 추가(요청 추적). 전체 컨텍스트+보안 필터 체인 스모크 테스트(헬스 공개 200 / 보호 API 401)로 §8 보안 MockMvc 일부도 충족. |

> ⏳ **M-NEW-7 Flyway는 보류 — 수동 DDL 스크립트 채택**: prod는 `ddl-auto: validate`라 Flyway가 생성/관리하는 스키마가 엔티티 매핑과 **정확히** 일치해야 기동된다. 현재 스키마는 Hibernate가 생성해 왔으므로 베이스라인 SQL을 손으로 작성하면 미세 불일치로 **prod 기동 실패** 위험이 크다. 그래서 지금은 **Phase C/D 스키마 델타만 명시적 SQL로 제공**(`backend/db/migration/2026-06-01_phase_c_d_schema_delta.sql` + `backend/db/README.md`)하여 배포 전 수동 적용으로 안전하게 언블록한다. **Flyway 전체 도입**은 실 MySQL에서 현재 스키마를 덤프해 V1 베이스라인을 생성·검증할 수 있을 때(테스트는 H2라 `spring.flyway.enabled=false`로 분리). 추가 테스트 폭(컨트롤러 슬라이스 @WebMvcTest, `CommentService` 단위)도 후속.

---

## 🔜 (예정 — 2026-06-02 구현) Access/Refresh 토큰 분리 · Refresh DB 저장 · 만료 정리 스케줄러

> Phase B에서 이연한 "토큰 폐기(Refresh)" 항목을 실무 표준 구조로 도입 예정. **본 섹션은 설계만이며 코드는 미반영**(2026-06-01 기준).

### 현재 → 목표
- **현재**: 단일 JWT(24h, stateless)가 HttpOnly 쿠키에만 존재(사실상 Access Token). Refresh 없음. 폐기는 H-NEW-2(매 요청 DB `UserStatus` 재검증)로 탈퇴/휴면만 즉시 무효화 → Access 수명이 길어(24h) 탈취 노출 창이 크고 재발급/세션 폐기 흐름 부재.
- **목표**: **짧은 Access(stateless) + DB 저장 Refresh(긴 수명)** 의 표준 2토큰 구조.

### 토큰 설계
| | Access Token | Refresh Token |
|---|---|---|
| 저장 | **DB 미저장**(stateless JWT) | **DB 저장**(`refresh_tokens`) |
| 수명 | **30분** | 길게(예: **14일**) |
| 쿠키 | HttpOnly `access_token`, path=`/` | HttpOnly `refresh_token`, **path=`/api/auth`로 한정**(일반 API엔 미전송) |
| 값/claims | userId·status·role(현행 유지) | opaque 난수(32B secure random) — **원문 대신 SHA-256 해시로 DB 저장**(유출 대비) |
| 검증 | JwtAuthFilter 재파싱 + DB 상태 재검증(H-NEW-2 유지) | `/api/auth/refresh`에서 DB 조회·만료·폐기 확인 |

### 흐름
- **로그인/온보딩**: AT(30m) + RT(DB 저장) 동시 발급, 쿠키 2개 세팅.
- **재발급** `POST /api/auth/refresh`: refresh 쿠키 → DB 조회·검증 → 새 AT 발급. **회전(rotation) 권장**: RT도 1회용으로 교체 + 기존 폐기, 재사용 감지 시 해당 유저 RT 전체 폐기(탈취 대응).
- **로그아웃**: 해당 RT를 DB 삭제 + 쿠키 2개 만료.
- **탈퇴/휴면**: 유저의 모든 RT 삭제 → AT는 최대 30분 내 자연 만료 + RT 즉시 폐기 = 완전 무효화(H-NEW-2와 결합).

### 엔티티 / 스키마
- `RefreshToken` { id, userId(FK), tokenHash(unique), expiresAt, createdAt, (회전 시) revoked }.
- 인덱스: `tokenHash`(유니크·조회), `expiresAt`(정리 스케줄러), `userId`(유저별 일괄 폐기).
- prod=`validate`이므로 **`refresh_tokens` 테이블 수동 DDL 필요**(`backend/db`, Flyway 도입 전).

### 만료 정리 스케줄러 — ✅ 필요함(질문 확인)
- 회전/로그아웃으로 즉시 삭제되는 것 외에 **자연 만료된 RT가 DB에 누적**되므로 **주기적 일괄 삭제 잡이 필요**하다.
- `@EnableScheduling` + `@Scheduled`(예: 매일 04:00) → 벌크 `DELETE FROM refresh_tokens WHERE expires_at < :now`(`@Modifying`).
- 단일 인스턴스 전제(수평 확장 시 ShedLock 등으로 중복 실행 방지 고려).

### 설정(예정 프로퍼티) / 프론트
- `app.jwt.access-ttl`(기본 30m), `app.jwt.refresh-ttl`(기본 14d), `app.refresh-cleanup.cron`(기본 `0 0 4 * * *`).
- 프론트 `api/client.js`: AT 401 → `/api/auth/refresh` 1회 호출 후 원요청 재시도, 실패 시 로그인. 동시 401은 단일 refresh로 큐잉.

### 구현 체크리스트(2026-06-02)
1. `RefreshToken` 엔티티 + 리포(`findByTokenHash`, `deleteByUserId`, `deleteByExpiresAtBefore`)
2. 로그인/온보딩에서 AT(30m)+RT 발급, 쿠키 유틸 분리(access/refresh)
3. `POST /api/auth/refresh`(+회전) · 로그아웃/탈퇴/휴면 시 RT 삭제
4. AT 수명 30분 단축
5. `@EnableScheduling` + 만료 정리 스케줄러
6. 프론트 axios refresh 인터셉터
7. 테스트(재발급/회전/만료/로그아웃 폐기) + `refresh_tokens` 수동 DDL(`backend/db`)

---

## 1. 요청 검증 결과 (신고 자동 숨김 · 관리자 관리)

### 1-1. "신고 누적 5건 초과 시 자동 숨김" — ✅ 구현됨 (단, 임계 의미 1건 확인 필요)

- `ReportService`(`REPORT_HIDE_THRESHOLD = 5`)가 신고 저장 후 `reportRepository.countByPostId(postId) >= 5`이면 `post.hide()` 호출 → **자동 숨김 동작 정상**.
- **신고자 중복 방지**도 잘 되어 있음: `(post_id, reporter_id)` 유니크 제약 + `existsByPostIdAndReporterId` 멱등 처리 + `DataIntegrityViolationException` 동시성 방어. → **서로 다른 신고자 5명**이 모여야 숨김(한 명이 부풀릴 수 없음). 설계 양호.
- 숨김 글은 일반 피드/검색에서 제외됨이 **확인됨**(`findFilteredLatest`·`findFilteredPopular`의 본문/카운트 쿼리 모두 `WHERE p.hidden = false`). 상세는 `PostService.getPost`에서 `hidden && role != ADMIN`이면 404 처리 → **관리자만 열람** 가능. 정책 일관성 양호.

> ✅ **임계 정책 확정(2026-05-31)**: "**5건째에 숨김**"(=서로 다른 신고자 5명 도달 시 즉시 숨김)으로 유지하기로 결정. 현재 코드 `countByPostId(postId) >= 5`가 **의도대로 정확**합니다. 코드 변경 불필요.
> - 권장 후속(선택, ⚪): 상수/주석을 "5건 **이상**(>=5) 시 숨김"으로 표기해 ">초과"와의 오해 소지를 제거. 임계값은 §아래대로 `application.yml`로 외부화하면 운영 튜닝 가능.

> 🟡 **부수 이슈**: 임계값이 코드 상수로 하드코딩되어 운영 중 튜닝 불가 → `application.yml` 프로퍼티(`app.report.hide-threshold`)로 외부화 권장.

> ✅ **복원 후 재숨김 루프 — Phase C 반영(2026-06-01)**: ~~관리자가 숨김 해제한 글에 신고 1건만 더 들어와도 즉시 다시 숨김됨.~~ `Post.reviewed` 플래그 도입 — 복원 시 `reviewed=true`로 표시하고 `ReportService` 자동 숨김 조건에 `!reviewed` 가드를 추가해 **복원 글은 재자동숨김 대상에서 제외**. 관리자의 "정상 판정"이 신고로 무력화되지 않는다.

### 1-2. 관리자 페이지의 숨김 게시물 관리 — 숨김 해제 ✅ / 영구 삭제 🔴 결함

| 기능 | 상태 | 비고 |
|------|------|------|
| 신고/숨김 목록 조회 | ✅ | `GET /api/admin/posts/reported` — 신고 수·사유별 집계·숨김 여부 포함. `Promise.allSettled`로 건의함과 독립 처리(1차 Bug 3 반영 확인). |
| **숨김 해제(복원)** | ✅ | `POST /api/admin/posts/{id}/restore` → `AdminPage`의 "숨김 해제" 버튼. 정상 연동. |
| **영구 삭제** | ✅ **수정됨 (Phase A)** | C-NEW-1 해결 — 삭제 전 신고/좋아요/북마크/알림을 정리 후 삭제. 숨김 글(신고 5건↑)도 정상 삭제. |

---

## 2. 🔴 이번 리뷰 신규 발견 (배포 차단)

### C-NEW-1. 관리자 영구 삭제 시 FK 제약 위반 → 500 (핵심 기능 미동작)
- **현상**: `PostService.deletePost`는 `postRepository.delete(post)`만 호출합니다. `Post` 엔티티는 **`comments`만 cascade 삭제**(`@OneToMany cascade=ALL, orphanRemoval`)하고, **`reports` / `post_likes` / `bookmarks`는 정리하지 않습니다.**
- **근거**: `Report`·`PostLike`·`Bookmark`는 각각 `@ManyToOne @JoinColumn(name="post_id", nullable=false)`로 `posts`를 참조(자식 측 FK). `Post`에는 이들에 대한 cascade 컬렉션이 없고, 엔티티에 `@OnDelete(CASCADE)`도 없음. 서비스 어디에도 `deleteByPostId` 류 정리 호출 없음(`Grep` 확인).
- **결과**: 신고/좋아요/북마크가 1건이라도 있는 글을 삭제하면 `posts` 행 삭제 시 FK 위반 → `DataIntegrityViolationException` → `GlobalExceptionHandler`의 일반 핸들러 → **HTTP 500 "서버 오류가 발생했습니다."**
- **왜 치명적인가**: 관리자 삭제의 주 대상은 **숨김 글**이고, 숨김 글은 정의상 **신고 5건 이상**을 가집니다. 따라서 **관리자 영구 삭제는 의도된 모든 케이스에서 항상 실패**합니다. (일반 유저가 좋아요/신고 0건인 자기 글을 지우는 경우에만 우연히 성공)
- **테스트가 못 잡은 이유**: 서비스 단위 테스트가 리포지토리를 Mockito로 목 처리 → 실제 FK가 강제되지 않아 통과. `@DataJpaTest` 기반 **삭제 통합 테스트(신고 달린 글 삭제)** 가 있었다면 적발됐을 결함.
- **제안**:
  1. 삭제 전 자식 정리: `reportRepository.deleteByPostId`, `postLikeRepository.deleteByPostId`, `bookmarkRepository.deleteByPostId`(+ 알림 §M-NEW-3) 호출 후 `postRepository.delete`. (가장 직접적)
  2. 또는 엔티티에 `@OnDelete(action = OnDeleteAction.CASCADE)` + Post 측 cascade 컬렉션 정리.
  3. 또는 **소프트 삭제(`deleted` 플래그)** 로 전환 — 모더레이션 감사 추적·복구 측면에서 더 권장(아래 §4).
  4. 회귀 방지: 신고/좋아요/북마크가 달린 글 삭제 **통합 테스트** 추가.

### C-NEW-2. (1차 C-1 미반영) 로그인 레이트리밋 부재
- 1차 리뷰의 C-1이 **여전히 미반영**. `SecurityConfig`에서 `/api/auth/login`은 `permitAll`이고 시도 제한 필터/버킷 없음. 백엔드가 매 시도를 MM에 프록시(`MattermostClient.login`)하므로 무차별 대입 + MM 계정 잠금 유발 가능.
- **제안**: IP/계정 단위 시도 제한(Bucket4j/Redis), 실패 누적 시 지연·임시 차단, 실패 audit 로깅. → 배포 전 필수.

---

## 3. 🟠/🟡 그 외 발견 (배포 전 권장)

### H-NEW-1. (1차 H-3 미반영) 입력 길이 제한 부재 → 긴 제목 500
- `PostCreateRequest`/`PostUpdateRequest`/`CommentCreateRequest`/`FeedbackRequest`/`OnboardingRequest` 모두 `@NotBlank`/`@NotNull`만 있고 **`@Size` 없음**. `Post.title`은 `VARCHAR(255)`라 256자↑ 제목은 DB 제약 위반 → 500.
- **제안**: 전 텍스트 입력에 `@Size(max=…)`(제목 ≤200, 본문 상한, 닉네임/기수/캠퍼스 상한, 댓글 상한). 위반 시 400 매핑(핸들러는 이미 있음).

### H-NEW-2. (1차 H-2) 토큰의 계정 상태 재검증 — ✅ Phase B 반영
- ~~`JwtAuthFilter`는 `PENDING`만 차단하고, 그 외에는 `findById`로 유저만 로드해 인증을 세팅 — `User.isEnabled()`(=ACTIVE 여부)를 호출하지 않음.~~
- **반영(2026-06-01)**: `JwtAuthFilter`가 **DB의 현재 `UserStatus`로 재검증** — PENDING은 온보딩/`auth/me`만, ACTIVE만 통과, DORMANT/WITHDRAWN은 401. 탈퇴/휴면 후 탈취된 토큰 사본도 즉시 무효화.
- **후속(🔜 2026-06-02 예정)**: Access 토큰 30분 단축 + Refresh DB 저장 + 만료 정리 스케줄러. 설계는 위 「(예정) Access/Refresh 토큰 분리」 섹션. *(상태 재검증으로 탈퇴/휴면 즉시 폐기는 이미 달성.)*

### H-NEW-3. (1차 H-4 미반영) Mattermost 호출 타임아웃 부재
- `MattermostClient`가 `new RestTemplate()`을 **타임아웃 없이** 사용 → MM 지연 시 로그인 스레드 무한 대기 → 서블릿 스레드 고갈 → 전체 장애 전파.
- **제안**: connect/read 타임아웃 설정(`ClientHttpRequestFactory`/`RestClient`), 실패 시 빠른 503, 가능하면 서킷 브레이커.

### H-NEW-4. 리포지토리 위생 — `node_modules`·IDE 파일이 git에 커밋됨 — ✅ 해결(커밋·푸시 완료)
- ~~`git ls-files frontend/node_modules` → 3,919개 파일이 추적 중. `.gitignore`에 `node_modules/`가 있으나 이미 커밋된 뒤라 무효. `backend/.idea/*`도 추적 중.~~
- **영향(당시)**: 저장소 비대화, 무의미한 diff 폭증, 머지 충돌, 빌드 재현성 저하, CRLF 경고 다발.
- **반영(2026-06-01)**: `git rm -r --cached frontend/node_modules .idea backend/.idea` + Figma mock 제거 + 루트 고아 `package-lock.json` 정리 → `83df359`로 커밋, `origin/main` 푸시. **추적 파일 ~4,000 → 137개**, 추적-but-ignore 0건. `package-lock.json`(frontend)만 정상 추적.

### M-NEW-1. (1차 M-3) PENDING 유저 라우팅 가드 — ✅ Phase C 반영
- ~~`PrivateRoute`는 `!user`만 검사하고 `user.status`를 보지 않아 PENDING 유저가 `/feed` 직접 접근 시 빈 화면.~~
- **반영(2026-06-01)**: `PrivateRoute`가 `status === 'PENDING'` → `/onboarding` 강제. 역가드 `OnboardingRoute` 신규 — 이미 온보딩한(ACTIVE) 유저가 `/onboarding` 접근 시 `/feed`로, 미인증은 `/login`으로.

### M-NEW-2. (1차 M-5 미반영) CORS 오리진 하드코딩
- `SecurityConfig`가 `http://localhost:5173`을 코드에 고정. **운영 배포 시 실제 도메인이 차단**되어 전 API가 CORS로 막힘.
- **제안**: `app.cors.allowed-origins`로 외부화(프로파일/환경변수).

### M-NEW-3. 게시글 삭제 시 알림 고아 데이터
- `Notification.postId`는 FK가 아닌 단순 `Long` → 삭제를 막지는 않지만, 글 삭제 후에도 **죽은 게시글을 가리키는 알림**이 남아 클릭 시 404.
- **제안**: 삭제 시 해당 `postId` 알림 정리(또는 클릭 시 graceful 처리).

### M-NEW-4. 관리자 페이지가 네이티브 `confirm`/`alert` 사용 — ✅ Phase C 반영
- ~~1차 Phase 0에서 `ConfirmDialog`/`AlertDialog`를 도입했는데 `AdminPage`는 여전히 `window.confirm`/`alert` 사용.~~
- **반영(2026-06-01)**: AdminPage뿐 아니라 FeedPage·PostDetailPage·SettingsPage·FeedbackPage의 **잔존 네이티브 alert/confirm을 전부** 커스텀 모달로 교체(사용자 선호 일치). 확인 모달은 공통 상태 패턴(`{title,message,confirmLabel,danger,onConfirm}`)으로 통일.
- **후속(선택)**: 비차단 토스트 시스템(§7) 도입 시 실패/성공 알림을 토스트로 전환.

### M-NEW-5. (1차 M-2 연속) 조회수 서버단 중복 제거 — ✅ Phase D 반영
- ~~`PostService.getPost`가 무조건 `incrementViewCount` 호출(작성자/세션/기간 구분 없음). 운영에선 본인 새로고침마다 +1.~~
- **반영(2026-06-01)**: 작성자 본인 제외 + 동일 유저 24h 1회 dedup(`PostView` 이력 테이블, `(post_id,user_id)` 유니크). 최초/24h 경과 시에만 원자적 벌크 UPDATE로 증가. 삭제 시 `PostView`도 함께 정리(post_id FK).

### M-NEW-6. 운영 최초 관리자 생성 수단 부재 (1차 M-6)
- 신규 유저는 항상 `USER`. 승격 API/시드 없음 → 운영 DB에서 수동 `UPDATE` 필요.
- **제안**: 부트스트랩 시드(환경변수로 지정한 MM 계정 → ADMIN 승격) 또는 운영 승격 절차 문서화.

### M-NEW-7. 스키마 마이그레이션 부재 + dev `ddl-auto: update`
- dev=update / prod=validate. Flyway/Liquibase 없음 → **운영 DB의 최초 스키마 생성·정합을 수동 의존.** 특히 `Post.category`·`hidden`은 DB NOT NULL 미지정이라 드리프트 위험.
- **제안**: Flyway 도입, 운영 스키마를 버전 관리. 배포 파이프라인에 마이그레이션 단계.

---

## 4. ⌨️ 폰트 — Windows에서 글씨체가 "딱딱하다"는 피드백 (요청 반영)

- **현상**: macOS에서는 폰트가 자연스럽지만 **Windows에서는 글씨체가 너무 딱딱**하게 보인다는 사용자 피드백.
- **원인 분석(코드 근거)**: `frontend/src/index.css`는 본문에 `'Work Sans'`, 제목/대부분 UI에 `'JetBrains Mono'`(모노스페이스)를 지정하고 Google Fonts로 로드합니다. **두 폰트 모두 라틴 전용으로 한글 글리프가 없습니다.** 따라서 **모든 한글 텍스트는 OS 기본 한글 폰트로 폴백**됩니다.
  - macOS → **Apple SD Gothic Neo**(부드러운 곡선·우수한 힌팅·그레이스케일 AA) → 자연스러움.
  - Windows → **맑은 고딕(Malgun Gothic)** + ClearType/GDI 힌팅 → **자획이 더 또렷·경직되게("딱딱") 렌더링**. 게다가 제목/버튼에 모노스페이스(`font-mono`)를 광범위하게 써서 라틴까지 기계적으로 보임.
  - 즉, **앱이 한글 웹폰트를 지정하지 않아 OS별로 전혀 다른 폰트가 적용**되는 것이 근본 원인이며, 이 불일치는 의도된 동작이 아니라 누락입니다.
- **제안(권장 순)**:
  1. **한글 웹폰트를 폰트 스택 최상단에 명시** — `Pretendard`(시스템 UI와 잘 어울리도록 설계, 두 OS에서 일관) 강력 권장, 대안 `Noto Sans KR`.
     예: `font-family: 'Pretendard', 'Work Sans', -apple-system, 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif;`
  2. **본문·UI의 모노스페이스 남용 축소** — `JetBrains Mono`는 코드/숫자/뱃지 등 한정 용도로만. 한글 본문·제목은 산세리프로.
  3. **렌더링 보정** — `body`에 `-webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility;` (특히 Windows 체감 개선) + 적정 `letter-spacing`/`line-height`.
  4. 웹폰트는 `display=swap` 유지 + 가능하면 self-host(서브셋)로 FOUT·외부 의존 최소화.

---

## 5. 배포 전 필수 수정 체크리스트 (Deployment Blockers)

> "개발 단계에서 실제 배포 전에 반드시 처리" 항목을 한 곳에 모았습니다. 위 항목의 요약 인덱스입니다.

| # | 항목 | 등급 | 상태 | 근거 위치 |
|---|------|------|------|-----------|
| 1 | **관리자 영구 삭제 FK 정리**(하드삭제 + 자식 정리) | 🔴 | ✅ Phase A | `PostService.deletePost` |
| 2 | **로그인 레이트리밋** | 🔴 | ✅ Phase A | `LoginRateLimiter`, `AuthController` |
| 3 | **CORS 오리진 외부화**(운영 도메인 미설정 시 전 API 차단) | 🔴 | ✅ Phase A | `SecurityConfig`, `app.cors.allowed-origins` |
| 4 | **node_modules·.idea git 추적 제거** | 🟠 | ✅ Phase A(커밋·푸시 완료 `83df359`) | 리포 전반, `.gitignore` |
| 5 | **입력 길이 `@Size` 추가**(긴 제목 500 방지) | 🟠 | ✅ Phase A | `dto/*Request.java` |
| 6 | **MM 호출 타임아웃** | 🟠 | ✅ Phase A | `MattermostClient` |
| 7 | **토큰 계정 상태 재검증**(DB `UserStatus` 재확인) | 🟠 | ✅ Phase B | `JwtAuthFilter` |
| 8 | **PENDING 라우팅 가드**(온보딩 강제) | 🟡 | ✅ Phase C | `PrivateRoute`, `OnboardingRoute` |
| 9 | **JWT_SECRET 길이 검증** — HS256 `secret.getBytes()`는 **32바이트(256bit) 미만이면 기동 시 `WeakKeyException`**. 운영 시크릿 길이 보장 | 🟡 | ✅ Phase B(기동 fail-fast) | `JwtProvider` |
| 10 | **최초 ADMIN 생성 수단** | 🟡 | ✅ Phase B | `app.admin.bootstrap-usernames`, `AuthService` |
| 11 | **운영 스키마 생성/마이그레이션 전략**(Flyway) — prod=`validate`라 사전 스키마 필수 | 🟡 | ⏳ Phase D | `application-prod.yml` |
| 12 | **AdminPage 커스텀 모달 통일** | 🟡 | ✅ Phase C | `AdminPage.jsx`(+ Feed/PostDetail/Settings/Feedback) |
| 13 | **한글 웹폰트 적용**(Windows 가독성) | 🟡 | ✅ Phase C | `index.css`, `tailwind.config.js` |

> 참고: dev 프로파일은 `useSSL=false&allowPublicKeyRetrieval=true`라 **로컬 전용**임이 명확. 운영은 `application-prod.yml`(useSSL=true, cookie.secure=true)을 사용하되 위 시크릿/도메인/스키마 선결 필요.

---

## 6. 🚀 추천 신규 기능 (Feature Backlog)

> "추가로 넣으면 좋을 기능" — 본 익명 사내 커뮤니티(SSAFY/Mattermost 연동) 성격에 맞춰 우선순위/난이도와 함께 제안합니다. (난이도: S=소, M=중, L=대)

### 6-1. 모더레이션/관리자 강화 (가장 ROI 높음)
- **소프트 삭제 + 휴지통/복구**(M): 영구삭제 대신 `deleted` 플래그 → 오삭제 복구·감사 추적. C-NEW-1도 동시 해결.
- **관리자 액션 감사 로그**(M): 누가/언제/무엇을 숨김·복원·삭제했는지 기록(분쟁 대비).
- **검토 완료/화이트리스트 플래그**(S): 복원한 글의 재자동숨김 방지(§1-1 루프 해결).
- **유저 제재**(L): 반복 위반 작성자 일시 정지/차단(익명이라 `mmUserId` 기준 내부 처리).
- **관리자 강제 숨김**(S): 신고 임계 전에도 관리자가 즉시 숨김.
- **신고 대시보드/통계**(M): 일별 신고 추이, 사유 분포, 처리율. 관리 의사결정 지원.
- **자동 금칙어/욕설 필터**(M): 작성·신고 시 1차 자동 모더레이션.

### 6-2. 커뮤니티 참여도
- **대댓글(답글)·댓글 좋아요·멘션**(M): 현재 1depth 댓글만. 토론 활성화.
- **공지/고정글(pin)**(S): 관리자 상단 고정.
- **주간 베스트/인기글 위젯**(S): 인기순 정렬 자산 재활용.
- **태그/해시태그 + 태그 검색**(M): 카테고리 3종을 보완.
- **이미지/파일 첨부**(L): 단, 익명성·용량·검열 정책 동반 필요.
- **임시저장(draft)·작성 중 이탈 방지**(S~M).

### 6-3. 알림/연동
- **알림 종류 확장**(S~M): 현재 좋아요 알림 외 **댓글/대댓글/멘션/공지** 알림.
- **실시간 알림(SSE/WebSocket)**(M): 폴링 대신 푸시.
- **Mattermost DM 연동 알림**(M): 이미 MM 인증을 쓰므로, 주요 알림을 MM DM으로 전송(차별화 포인트).

### 6-4. 개인화/마이페이지
- **내 활동 탭**(S): 작성/댓글/좋아요/스크랩(서버 `scope`가 일부 지원 — UI 노출).
- **작성자 단위 글 숨김/뮤트**(M): 익명 환경에서 글 단위 숨김.
- **온보딩 닉네임 중복 검사 + 금칙어**(S): 1차 H-anon(가명 유일성)과 연계.

### 6-5. 도메인 특화 확장 (SSAFY 개발교육 × 익명 사내 커뮤니티)

> 본 앱의 정체성(개발 교육과정 + Mattermost 인증 + 기수/캠퍼스 + 완전 익명)에 직접 들어맞는, 차별화 ROI가 높은 확장입니다. 우선순위 ★(상)~☆(보)로 표기.

**A. 개발자 교육 커뮤니티 특화 (차별화 ROI 최상)**
- ★ **마크다운 + 코드 블록 문법 강조**(M): 현재 본문은 plain text. 개발자 대상이라 코드 스니펫·syntax highlight·마크다운 렌더링은 사실상 필수급. QUESTION 품질을 결정적으로 끌어올림. (XSS 방지 위해 sanitize 필수)
- ★ **Q&A 채택/해결됨 시스템**(M): QUESTION 글에 "베스트 답변 채택 + 해결됨 뱃지"(StackOverflow식). 교육 질문 커뮤니티에 가장 적합, 검색 자산 축적.
- ★ **스터디/팀원 모집 게시판 + 모집상태**(M): 관통PJT/사이드프로젝트 팀빌딩. `모집중/마감`·인원·포지션 태그. SSAFY 맥락 수요 확실.
- ☆ **취업/이직 정보 특화(JOB 강화)**(M): 기업 태그, 코테/면접 후기 템플릿, (익명에 적합한) 처우 정보. JOB을 "정보 DB"로 육성.

**B. 블라인드형 익명 경험의 핵심 (현재 누락, 체감 큼)**
- ★ **댓글 "글쓴이(OP)" 표시 + 글 단위 일관 익명 별칭**(M): 익명이라 스레드에서 글쓴이/동일인 구분 불가. 게시글별 해시 별칭("작성자"·"익명1·2…") 부여로 블라인드 특유 가독성 확보. (1차 H-anon 익명성 정책과 함께 확정)
- ★ **기수/캠퍼스 스코프 필터·라운지**(M): `cohort`/`campus`를 이미 수집하나 프로필에만 노출. "우리 캠퍼스만"·"동기(같은 기수)만" 필터/라운지로 관련성 급상승. 익명 유지·범위만 한정.
- ☆ **익명 투표/설문(Poll)**(S~M): "어느 프레임워크?"·점심 투표 등. 낮은 진입장벽으로 리텐션 기여.

**C. 참여·리텐션**
- ☆ **다양한 리액션**(S): 단일 좋아요 외 "도움돼요/정보/공감" 이모지 반응. 정보성 글 변별.
- ☆ **읽음 표시 / 안 읽은 새 글 뱃지**(S): 마지막 방문 기준 새 글 표시.
- ☆ **북마크 폴더·메모**(S): 스크랩 분류(취업·CS 등).
- ☆ **주간 다이제스트(인기글 요약) → Mattermost DM/이메일**(M): MM 인증 자산 활용, 휴면 방지. (§6-3 MM 연동과 연계)
- ☆ **OG 링크 프리뷰**(S~M): URL 첨부 시 제목·썸네일 미리보기.

**D. 운영·신뢰 (관리자 경험)**
- ☆ **신고 이의제기/소명 플로우**(M): 숨김 글 작성자 소명 → 관리자 검토. 자동 숨김 오탐 완화. (§1-1 복원 루프·§4 reviewed 플래그와 연계)
- ☆ **커뮤니티 가이드/규칙·FAQ + 공지 CMS**(S): 신고 사유와 연결된 규칙 문서, 관리자 공지/카테고리 화면 관리.
- ☆ **PWA + 모바일 푸시 알림**(M~L): 모바일 접근성·알림 도달률.

> **권장 우선 착수**: A의 마크다운/코드블록 → Q&A 채택 → B의 OP 표시·기수/캠퍼스 필터. 적은 노력으로 "개발 교육 + 익명" 정체성을 가장 크게 강화.

---

## 7. 🧭 사용성 개선 (UX/Usability)

- **토스트 알림 시스템**(S): 잔존 `alert`/`window.confirm` 전면 대체, 비차단 피드백. (M-NEW-4 포함)
- **폼 인라인 검증 + 글자수 카운터**(S): 제목/본문 `@Size`와 짝을 이뤄 프론트에서 선제 안내.
- **로딩 스켈레톤 + 빈/에러 상태 공통 컴포넌트**(S): 일관된 빈 상태("게시글 없음"/"네트워크 오류").
- **페이지네이션/무한 스크롤 UI**(M): 백엔드 `page/size`는 준비됨 — 프론트 UI 노출 확인·보강.
- **모바일 반응형 + 접근성**(M): 모달 포커스 트랩, 키보드 내비, `aria-*`, 색 대비(스카이블루 테마 대비비 점검).
- **다크모드 완성도**(S): `ThemeContext` 존재 — 토글 접근성/지속성(localStorage) 점검.
- **신고 후 상태 피드백**(S): "이미 신고함" 표시(멱등 동작을 UI로 노출).
- **상세페이지 깨진 링크/404 graceful 처리**(S): 숨김·삭제 글 접근 시 안내(M-NEW-3 연계).
- **한글 웹폰트 적용**(S): §4. Windows 가독성 직접 개선.

---

## 8. 🛠 운영 · 관측 · 품질 (1차 D-*/T-* 연속)

- **컨테이너화/CI**(M): Dockerfile + docker-compose(app+MySQL), 빌드·테스트 파이프라인.
- **설정 외부화 일원화**(S): CORS·MM URL·DB·시크릿을 환경/시크릿 매니저로(M-NEW-2 포함).
- **관측성**(M): ✅ **Phase D 반영** — Actuator 헬스체크(`/actuator/health` 공개+프로브), 요청 추적(`RequestIdFilter`→MDC `requestId`+`X-Request-Id`). 후속: 구조화(JSON) 로깅·에러 모니터링(Sentry 등).
- **테스트 폭 보강**(M): ③ **삭제 통합 테스트 ✅ Phase D**, ① 보안 인가 일부 ✅(스모크: 헬스 공개/보호 401). 남음: ② 컨트롤러 슬라이스(@WebMvcTest), ④ `CommentService` 단위, ⑤ 프론트 Vitest/RTL.
- **운영 런북**(S): 최초 관리자 생성, 신고 임계/숨김 정책, 백업·롤백.

---

## 9. 권장 진행 순서 (로드맵)

| 단계 | 목표 | 항목 | 비고 |
|------|------|------|------|
| **Phase A. 배포 차단 해소** | 배포 가능 상태 | C-NEW-1, C-NEW-2, M-NEW-2(CORS), H-NEW-1·3, H-NEW-4(리포 위생), M-NEW-3 | ✅ **완료 2026-06-01** (§5 1~6) |
| **Phase B. 보안/계정 하드닝** | 안전성 | H-NEW-2(토큰 상태), JWT_SECRET 검증, M-NEW-6(최초 ADMIN) | ✅ **완료 2026-06-01** · Access/Refresh 분리는 🔜 **2026-06-02 구현 예정**(설계 문서화 완료) |
| **Phase C. UX/정책 마감** | 사용성 | M-NEW-1(PENDING 가드), M-NEW-4(모달), 폰트(§4), §1-1 복원 후 재숨김 루프(`reviewed` 플래그) | ✅ **완료 2026-06-01** (임계값은 "5건째 숨김" 유지) |
| **Phase D. 운영 품질** | 안정화 | M-NEW-5(조회수), M-NEW-7(Flyway), 관측성, 테스트(삭제 통합) | 🔄 **Flyway 제외 완료 2026-06-01** (M-NEW-5·삭제 통합·관측성). M-NEW-7은 실 MySQL 검증 환경에서 도입 |
| **Phase E. 기능 확장** | 성장 | §6 기능 백로그(모더레이션·참여도·알림·개인화) | 소프트삭제는 Phase A와 함께 검토 |

---

## 10. 총평

- **강점**: 계층 분리·도메인 메서드(세터 금지)·익명성 보장(작성자 응답 비노출)·신고/좋아요 멱등+유니크 제약·N+1 배치 해소·프로파일 분리·HttpOnly+SameSite 쿠키·시크릿 폴백 제거(fail-fast) 등 **설계 기본기가 탄탄**합니다. 1차 Phase 0(프론트 UX 버그·커스텀 모달)도 잘 반영됐습니다.
- **결정적 공백**: ① **관리자 영구 삭제가 실제로는 동작하지 않음(FK)** — 요청하신 핵심 관리 기능이라 최우선. ② 1차에서 지목한 **보안 하드닝(레이트리밋·입력 제한·타임아웃·토큰 상태)이 대부분 미반영** 상태로 남아 있습니다. ③ **리포 위생(node_modules 커밋)** 과 ④ **한글 폰트 누락**.
- 위 Phase A를 끝내면 **배포 가능**, Phase B~C까지면 **운영 신뢰 가능** 수준입니다. 현재 평가 **70~75%**, Phase A~B 완료 시 **85%+** 도달 예상.
- > ✅ **반영 완료(2026-06-01)**: Phase A·B·C 모두 구현 — 배포 가능 + 운영 신뢰 가능 수준 도달(약 85~88%). 남은 것은 운영 품질(Phase D: 조회수 서버단·Flyway·관측성·테스트 폭)과 기능 확장(Phase E/§6).
</content>
</invoke>
