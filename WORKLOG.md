# WORKLOG — SSAFY_BLIND 개선 작업 내역

사내 블라인드형 익명 커뮤니티(Spring Boot + React)의 1차/2차 리뷰 반영 작업 기록입니다.
분석/계획 문서는 [MoreDevelopments.md](./MoreDevelopments.md)(1차) · [MoreDevelopments_V2.md](./MoreDevelopments_V2.md)(2차)이며, 본 문서는 **무엇을 실제로 구현했는지**를 한곳에 모은 진행 현황입니다.

- 최종 업데이트: 2026-06-01
- 진행 단계: **Phase 0 ✅ · A ✅ · B ✅ · C ✅** 완료 · **Phase D 🔄 Flyway 제외 완료**(M-NEW-5 조회수·삭제 통합 테스트·관측성) → **남은 것: Phase D의 M-NEW-7(Flyway)·테스트 폭 + Phase E/§6 기능**
- 운영 완성도 추이: 1차 65~70% → Phase 0 후 70~75% → Phase A·B 후 배포 가능 → Phase C 후 UX/정책 마감 → **Phase D(운영품질) 반영 중(약 88~90%)**
- 작업 범위 원칙: **리뷰 반영 수정은 영구**(롤백 마커 없음). **기능 확장(§6)은 롤백 용이하도록 마커**(아래 [롤백 마커 규약](#롤백-마커-규약)) — Phase 0/A/B는 전부 수정이라 마커 미사용.

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
| **H-NEW-4** 리포 위생 | 🟠 | `git rm -r --cached frontend/node_modules .idea backend/.idea`(3,937개 추적 해제, 작업트리 보존) + `.gitignore` blanket `.idea/`. **스테이징만 — 커밋 미실행** | 리포 전반, `.gitignore` |
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

> ⏳ **M-NEW-7 Flyway는 보류 — 대신 수동 DDL 스크립트 채택**: prod=`ddl-auto: validate`라 Flyway 베이스라인이 엔티티 스키마와 **정확히** 일치해야 기동되는데, 실 MySQL 검증 환경이 없어 손으로 쓴 베이스라인은 prod 기동 실패 위험이 크다. 따라서 **Phase C/D 스키마 델타를 명시적 SQL로 제공**(`backend/db/migration/2026-06-01_phase_c_d_schema_delta.sql`, `backend/db/README.md`) — 배포 전 수동 적용으로 안전하게 언블록. Flyway 전체 도입은 실 MySQL 베이스라인을 생성·검증 가능할 때. 테스트 폭 추가(컨트롤러 슬라이스·`CommentService` 단위)도 후속.

**기록 문서**: `MoreDevelopments_V2.md` 상단 Phase D 섹션.

---

## ⏳ 이연/후속 항목 (의도적 보류)

| 항목 | 사유 | 출처 |
|---|---|---|
| 토큰 폐기 — **전체 Refresh 토큰 + Access 단축 + tokenVersion** | 탈퇴/휴면 즉시 무효화(실질적 폐기)는 H-NEW-2로 달성. 임의 세션 폐기("모든 기기 로그아웃")용 Refresh 회전은 프론트 변경 동반 → 별도 작업, 우선순위 낮음(MM-프록시 인증) | Phase B |
| ~~**M-NEW-5/M-2** 조회수 서버단 중복제거~~ → ✅ **Phase D 완료** | 작성자 제외 + 24h dedup(`PostView`)로 서버단 정식 처리 | V2 §3 |
| ~~삭제 **통합 테스트**(@DataJpaTest 실제 FK)~~ → ✅ **Phase D 완료** | `PostDeletionIntegrationTest`로 실 FK 회귀 검증 | V2 §8 |

---

## 🔜 남은 작업

> Phase D는 Flyway 제외 완료(M-NEW-5·삭제 통합 테스트·관측성). 남은 것은 아래.

### Phase D 잔여 — 운영 품질
- **M-NEW-7 Flyway**(결정 필요): 실 MySQL로 베이스라인 생성·검증 가능한 환경에서 도입. prod=`validate`라 스키마 정합 필수.
- 테스트 폭 추가: 컨트롤러 슬라이스(@WebMvcTest), `CommentService` 단위, 인가 케이스 확장. (삭제 통합·헬스/인가 스모크는 완료)

### Phase E / §6 — 기능 확장 (롤백 마커 적용 대상)
- 모더레이션 강화(소프트삭제+휴지통, 감사 로그, 강제 숨김), 마크다운+코드블록, Q&A 채택, 대댓글/리액션, 기수·캠퍼스 라운지, 알림 확장/실시간, MM DM 연동 등. 상세는 V2 §6.

---

## 🔧 신규 환경변수 / 설정 (Phase A·B 도입)

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

---

## ⚠️ 운영/배포 주의사항

1. **`JWT_SECRET`은 32바이트 이상**이어야 기동됨(HS256 256bit). 짧으면 부팅 단계에서 명확한 메시지로 실패.
2. **운영 배포 시 `APP_CORS_ALLOWED_ORIGINS`에 실제 프론트 도메인**을 반드시 지정.
3. **최초 관리자**: `APP_ADMIN_BOOTSTRAP_USERNAMES`에 MM **username**(loginId/email 아님) 지정 → 해당 계정이 로그인하면 ADMIN 승격(로그인 시점).
4. **H-NEW-4 추적 해제는 스테이징 상태**(`git rm --cached`, 작업트리 파일 보존). `git status` 검토 후 커밋 필요. 소스 변경분은 unstaged 상태.
5. **계정 휴면/탈퇴 시** 해당 계정의 기존 토큰은 즉시 거부됨(H-NEW-2, 의도된 동작).
6. **헬스체크(Phase D)**: `GET /actuator/health`는 **인증 없이 공개**(LB/오케스트레이터 프로브용). 상세 컴포넌트는 인증 시에만 노출. `liveness`/`readiness` 프로브는 `/actuator/health/{liveness,readiness}`.
7. 🔴 **prod 스키마 델타(배포 전 필수)**: prod는 `ddl-auto: validate`라 엔티티가 요구하는 테이블/컬럼이 **없으면 기동 실패**한다. Flyway 미도입 상태이므로 **배포 전 수동 DDL**로 반영(dev는 `update`라 자동 생성). → 스크립트·절차: **`backend/db/migration/2026-06-01_phase_c_d_schema_delta.sql`** + `backend/db/README.md`.
   - Phase C: `posts.reviewed` 컬럼 추가(기존 `hidden` 타입과 일치).
   - Phase D(M-NEW-5): `post_views` 테이블 신규(`(post_id,user_id)` 유니크, post_id/user_id FK).
   - 조회수 동작: 작성자 본인 조회 미집계 + 동일 유저 24h 1회 집계.

---

## ✅ 검증 방법

- 백엔드: `cd backend && ./gradlew.bat test` (현재 BUILD SUCCESSFUL). 실행은 dev 프로파일 + MySQL + 환경변수(`DB_HOST/DB_NAME/DB_USERNAME/DB_PASSWORD/JWT_SECRET`) 후 `./gradlew.bat bootRun`.
- 프론트: `cd frontend && npm install && npm run dev` (Vite proxy `/api`→`localhost:8080`), 빌드 확인 `npm run build`.

---

## 롤백 마커 규약

기능 확장(§6) 코드는 추후 롤백이 쉽도록 다음 규약을 적용합니다(Phase 0/A/B는 수정이라 미적용):

- 코드 블록을 주석 마커로 감싼다: `// [FEATURE:기능명] … // [/FEATURE:기능명]`
- 루트 `FEATURES.md`에 기능명·파일·범위·롤백 절차를 인덱싱 → 마커 검색만으로 일괄 제거 가능.

> 참고: 실제 연동 프론트는 `frontend/src`이며, `frontend/사내 블라인드 웹 어플리케이션`은 Figma export mock(작업 대상 아님).
