# MoreDevelopments — 개선 과제 및 발전 방향

코드 리뷰(운영 완성도 약 **65~70%** 평가)와 실제 코드 검증을 바탕으로, 앞으로 수정·발전시켜야 할 항목을 **심각도별**로 정리한 문서입니다. 각 항목은 리뷰에서 부여한 코드(C/H/M)를 유지하고, 실제 소스 위치와 검증 결과를 함께 적었습니다.

> 본 문서는 **분석/계획 문서**입니다. 실제 코드 수정은 포함하지 않습니다.

## 심각도 기준

| 등급 | 의미 | 조치 |
|------|------|------|
| 🔴 CRITICAL | 보안 취약점 / 데이터 손실 위험 | 배포 전 **반드시** 수정 |
| 🟠 HIGH | 버그 또는 중대한 품질 이슈 | 배포 전 수정 권장 |
| 🟡 MEDIUM | 유지보수/운영 품질 이슈 | 수정 검토 |
| ⚪ LOW | 스타일/사소한 제안 | 선택 |

---

## ✅ Phase 0 — 즉시 버그 수정 (완료: 2026-05-31)

1차 배포 직전 사용자가 직접 체감한 UX/기능 버그 5종을 수정했습니다. 모든 수정은 실제 연동 프론트(`frontend/src`)에서 이뤄졌으며, 백엔드 변경은 없습니다. 알림/확인 창은 네이티브 `alert`/`confirm` 대신 앱 디자인에 맞춘 커스텀 모달로 구현했습니다.

**공통 — 재사용 모달 컴포넌트 신규** (`ReportModal` 마크업 미러링)
- `frontend/src/components/ConfirmDialog.jsx` — 확인/취소 2버튼(`danger` 옵션).
- `frontend/src/components/AlertDialog.jsx` — 확인 1버튼(알림용).

| # | 증상 | 원인 | 수정 |
|---|------|------|------|
| **Bug 1** | 로그인 실패 알림이 잠깐 떴다 사라짐 | `api/client.js` 응답 인터셉터가 `/auth/me` 외 **모든 401에서 전체 페이지 리로드** → 로그인 실패(401)도 리로드되어 인라인 에러 소멸 | `client.js` 401 가드를 **인증 엔드포인트 전체 제외**(`/auth/`)로 변경. `LoginPage`는 실패 시 `AlertDialog` 노출(401이면 "Mattermost 인증 실패" 명시) |
| **Bug 2** | 로그인 직후 피드 미로딩(사이드바 클릭해야 표시) | StrictMode 이중 effect / 인증 커밋 타이밍 경합 | `FeedPage` 초기 로드를 **`AbortController`+cleanup**으로 보호하고 **`user?.id` 준비 후** 1회 실행. 취소된 요청은 loading 플래그를 건드리지 않음 |
| **Bug 3** | 관리자 페이지에서 건의함 미조회 | `AdminPage`가 신고·건의 요청을 `Promise.all`로 묶어 **한쪽(신고) 실패 시 전체 reject** | `Promise.allSettled`로 분리 — 한쪽 실패가 다른 쪽 렌더를 막지 않음 |
| **Bug 4** | 로그아웃이 확인 없이 즉시 실행 | 사이드바 로그아웃이 `logout()` 즉시 호출 | `FeedPage`에 `ConfirmDialog`("정말 로그아웃 하시겠습니까?") 추가. 확인 시에만 로그아웃, 사용자 메뉴는 닫힘 |
| **Bug 5** | 게시글 1회 진입 시 조회수 +2 | StrictMode가 dev에서 effect 2회 실행 → `GET /posts/{id}` 2회 → `incrementViewCount` 2회 | `PostDetailPage`에 **`useRef` 가드**(`fetchedIdRef`)로 동일 `id` fetch 1회만 전송. (AbortController는 서버가 이미 증가시킨 뒤일 수 있어 부적합) |

> 참고: Bug 5의 **서버단 정식 중복제거**(작성자 제외 + 사용자/24h 단위)는 [M-2](#m-2-조회수-자가-증가어뷰징--본인새로고침마다-1)로 별도 진행. Bug 2의 PENDING 라우팅 가드 강화는 [M-3](#m-3-pending-유저의-직접-접근-시-ux-부재)와 연계.

---

## ⭐ 가장 먼저 손볼 3가지 (리뷰 권고)

리뷰어가 보안 위험을 가장 크게 줄이는 항목으로 꼽은 우선순위입니다.

1. **C-1 — 로그인 레이트리밋**: 무차별 대입(brute-force)·MM 계정 도용 방어
2. **H-2 — 토큰 상태 재검증/폐기**: 탈퇴·휴면 후에도 24시간 토큰이 살아 있는 문제
3. **H-3 — 입력 길이 제한**: 긴 제목 등으로 인한 500 에러 차단

---

## 🔴 CRITICAL

### C-1. 로그인 레이트리밋 부재
- **현상**: `POST /api/auth/login`에 시도 횟수 제한이 없어 무차별 대입이 가능. 백엔드가 MM에 매 시도를 그대로 프록시하므로(`MattermostClient.login`) MM 계정 잠금·차단까지 유발할 수 있음.
- **근거**: `security/SecurityConfig.java`에 `login`/`logout`은 `permitAll`, 레이트리밋 필터/버킷 없음. `JwtAuthFilter.shouldNotFilter`도 로그인 경로를 통과시킴.
- **제안**: IP/계정 단위 시도 제한(예: Bucket4j 또는 Redis 카운터), 실패 누적 시 지연·임시 차단, 로그인 실패 audit 로깅.

---

## 🟠 HIGH

### H-2. 발급된 토큰의 상태 재검증·폐기 부재
- **현상**: JWT 수명이 24시간 고정이며, 인증 필터가 토큰의 `status` 클레임/현재 계정 상태를 재검증하지 않음. 탈퇴(`WITHDRAWN`)·휴면(`DORMANT`) 처리 직후에도 **기존 토큰이 탈취되면 만료 전까지 API 호출이 통과**될 수 있음. 정상 플로우에선 쿠키가 만료되지만 토큰 탈취 시 위험.
- **근거**: `security/JwtProvider.java` `EXPIRATION_MS = 24h`. `security/JwtAuthFilter.java`는 `PENDING`만 차단하고, 그 외에는 `userRepository.findById`로 사용자만 로드해 인증을 세팅 — `isEnabled()`(ACTIVE 여부) 미검사. `AccountService.withdraw/goDormant`는 상태만 바꿀 뿐 토큰을 무효화하지 못함.
- **제안**: ① Access 토큰 수명 단축 + Refresh 토큰 도입, ② 필터에서 현재 `UserStatus` 재확인(ACTIVE만 통과), ③ 탈퇴/휴면 시 토큰 블랙리스트 또는 토큰 버전(`tokenVersion`) 클레임 비교로 즉시 폐기.

### H-3. 입력 길이 제한 부재 → 긴 제목 입력 시 500
- **현상**: 게시글 제목이 `VARCHAR(255)`인데 요청 DTO에 길이 검증이 없어, 256자 이상 제목 입력 시 DB 제약 위반으로 500 에러.
- **근거**: `domain/Post.java`의 `title`은 `@Column(nullable=false)`(기본 255). `dto/PostCreateRequest.java`·`PostUpdateRequest`는 `@NotBlank`만 있고 `@Size` 없음. 동일하게 `CommentCreateRequest`, `FeedbackRequest`, `OnboardingRequest` 모두 길이 상한 없음.
- **제안**: 모든 텍스트 입력에 `@Size(max=...)` 추가(제목 ≤200, 닉네임/기수/캠퍼스 적정 상한, 본문/댓글 상한), 위반 시 400으로 매핑(`GlobalExceptionHandler`). 본문은 `TEXT`이므로 애플리케이션 레벨 상한을 명시.

### H-4. Mattermost 호출 타임아웃 부재 → 스레드 고갈
- **현상**: MM 서버 지연 시 로그인 요청이 무한 대기. 동시 요청이 쌓이면 서블릿 스레드가 고갈되어 **전체 응답 저하**로 번질 수 있음.
- **근거**: `client/MattermostClient.java`가 `new RestTemplate()`을 타임아웃 설정 없이 사용.
- **제안**: connect/read 타임아웃 설정(`ClientHttpRequestFactory` 또는 `RestClient`/`WebClient`), 실패 시 빠른 503 응답, 가능하면 서킷 브레이커(Resilience4j) 적용.

### H-anon. 닉네임 유일성 미보장 → 사칭/식별 혼동 (리뷰 4항)
- **현상**: 같은 가명·기수·캠퍼스 조합의 유저가 다수 존재할 수 있어, 글 작성자 식별 혼동·사칭 오해 가능. 익명 정책상 "구별 가능성"과 "익명성"의 균형 문제.
- **근거**: `domain/User.java`의 `nickname`에 유니크 제약 없음. 온보딩에서 중복 검사 없음.
- **제안**: 익명성 정책을 먼저 확정(완전 익명 vs 안정적 가명). 가명 정책이면 닉네임 유니크 제약 + 온보딩 중복 검사, 또는 글 단위 일관 익명 별칭(예: 게시글별 해시 별칭) 도입.

---

## 🟡 MEDIUM

### M-1. 인기순 정렬의 운영 MySQL 동작/성능 차이
- **현상**: 인기순이 `GROUP BY p ... ORDER BY COUNT(pl) DESC` + 페이징 구조라 운영 MySQL에서 H2와 다르게 동작하거나(특히 `ONLY_FULL_GROUP_BY`/정렬·페이징 상호작용) 성능이 저하될 수 있음.
- **근거**: `repository/PostRepository.findFilteredPopular`(엔티티 GROUP BY + 집계 정렬). 테스트는 H2(`@DataJpaTest`)에서만 검증됨.
- **제안**: 좋아요 수 비정규화 컬럼(`likeCount`) 유지 + 인덱스, 또는 카운트 테이블/캐시. 운영 DB(MySQL)로 통합 테스트 추가.

### M-2. 조회수 자가 증가(어뷰징) — 본인/새로고침마다 +1
- **현상**: 상세 조회마다 무조건 +1이라 본인이 새로고침할 때도 증가 → 조회수가 비정상적으로 부풀려짐.
- **근거**: `service/PostService.getPost`가 `incrementViewCount`를 무조건 호출(작성자/중복 방문 구분 없음).
- **제안**: 작성자 본인 제외, 사용자·세션·기간(예: 24h) 단위 중복 제거(조회 이력 테이블 또는 캐시 키).
- **진행**: Phase 0(Bug 5)에서 dev StrictMode 이중 fetch로 인한 **+2 문제는 프론트 `useRef` 가드로 해결**. 본 항목(작성자 제외·새로고침 중복 제거)은 서버단 정식 처리로 후속(Phase 3).

### M-3. PENDING 유저의 직접 접근 시 UX 부재
- **현상**: PENDING 유저가 주소창으로 `/feed` 등에 접근하면 빈 화면 + 콘솔 403만 뜨고 온보딩으로 유도되지 않음.
- **근거**: 백엔드 `JwtAuthFilter`는 PENDING에 대해 온보딩/`auth/me` 외 403 반환(정상). 프론트(현행 Figma 앱)는 라우팅 가드가 없고 목 데이터 기반이라 상태 분기 미처리.
- **제안**: 프론트에서 `/auth/me`의 `status`에 따라 라우팅 가드(PENDING → 온보딩 강제 리다이렉트), 403 응답 시 온보딩 안내.
- **연계**: Phase 0(Bug 2)에서 피드 초기 로드 경합은 해결됐으나, PENDING 계정의 `/feed` 직접 접근 시 온보딩 강제는 미구현. 실제 연동 프론트(`frontend/src/components/PrivateRoute.jsx`)에 status 분기 추가 필요(Phase 2).

### M-4. 카테고리 정의 불일치 (mock 프론트 한정 — 실제 연동 프론트는 해결됨)
- **정정(2026-05-31 검증)**: 실제 연동 프론트 `frontend/src/lib/categories.js`는 백엔드와 **이미 일치**(`FREE/JOB/QUESTION`, 라벨까지 동일). 불일치는 **mock 프론트(`사내 블라인드 웹 어플리케이션`)** 에만 남아 있음.
- **현상**: mock 프론트 타입은 `free/job/question/project/lounge`(+all) 6종으로 정의되어 백엔드 3종과 불일치.
- **근거**: `domain/PostCategory.java`(FREE/JOB/QUESTION) = `frontend/src/lib/categories.js`(일치) vs `frontend/사내 블라인드 웹 어플리케이션/src/app/types.ts`(6종).
- **제안**: 실제 연동 프론트는 조치 불요. mock 프론트를 본 연동에 이식·아카이브할 때 백엔드 enum 기준으로 정렬(F-3/F-4와 함께 처리).

### M-5. CORS 허용 오리진 하드코딩
- **현상**: 운영 배포 시 CORS 오리진을 코드에서 직접 고쳐야 함.
- **근거**: `security/SecurityConfig.java`가 `http://localhost:5173`을 하드코딩(프로파일/환경변수 외부화 안 됨).
- **제안**: 허용 오리진을 환경변수/프로파일 설정으로 외부화(`app.cors.allowed-origins`).

### M-6. 첫 관리자 생성 수단 부재
- **현상**: 운영에서 최초 ADMIN을 DB에서 수동으로 만들어야 함(신규 유저는 항상 USER 기본값).
- **근거**: `domain/User.java` `role` 기본값 `USER`, 승격 API/시드 없음.
- **제안**: 부트스트랩 시드(환경변수로 지정한 MM 계정을 ADMIN 승격) 또는 관리자 승격 전용 운영 절차/엔드포인트.

### M-7. "OAuth" 표방과 실제 비밀번호 프록시의 정합성
- **현상**: 문서/명칭상 "Mattermost OAuth"로 보일 수 있으나 실제로는 `login_id+password`를 백엔드가 받아 MM API로 프록시하는 **비밀번호 프록시** 방식. 사용자 비밀번호가 우리 서버를 경유.
- **근거**: `client/MattermostClient.login`이 자격증명을 직접 POST.
- **제안**: 명칭을 실제 방식에 맞게 표기(완료: README), 또는 실제 OAuth2/OIDC 흐름으로 전환해 비밀번호 비경유. 최소한 자격증명 미로깅·전송 구간 TLS 보장 명시.

### M-8. 스키마 마이그레이션 도구 부재 / dev `ddl-auto: update`
- **현상**: dev 프로파일이 `ddl-auto: update`라 스키마 드리프트 위험. 운영(`validate`)과의 스키마 동기화를 수동 의존.
- **근거**: `application-dev.yml`(update) vs `application-prod.yml`(validate). 마이그레이션 도구 없음.
- **제안**: Flyway/Liquibase 도입으로 버전 관리된 스키마 이관. `Post.category`/`hidden`처럼 DB NOT NULL을 못 건 컬럼도 마이그레이션으로 정합화.

---

## 🧪 테스트 보강 (T-*)

현재 32개 테스트는 서비스 9종 + 리포지토리 1종에 집중되어 있고, 보안·컨트롤러·일부 서비스·프론트가 비어 있습니다.

- **T-1. 보안 테스트**: `JwtAuthFilter`/`JwtProvider`(만료·위조·PENDING 차단·상태 재검증), `SecurityConfig` 인가(`/api/admin/**`=ADMIN) — MockMvc/`spring-security-test`.
- **T-2. 컨트롤러 테스트**: 주요 엔드포인트 MockMvc 슬라이스(인증/검증/에러 매핑).
- **T-3. `CommentService` 단위 테스트**: 작성/조회(`isMine`)/삭제(본인·ADMIN) — 현재 누락.
- **T-4. 운영 DB 통합 테스트**: 인기순 정렬/검색 등 MySQL 기준 검증(M-1 연계).
- **T-5. 프론트엔드 테스트**: 라우팅 가드·폼 검증·상태 분기(Vitest/RTL).

---

## 🎨 프론트엔드 (F-*)

- **F-1. 신규 UI ↔ 백엔드 연동(최우선)**: 현행 `사내 블라인드 웹 어플리케이션`은 인메모리 목 데이터로만 동작. 레거시 `frontend/src`의 axios+쿠키 연동 패턴(`api/client.js`, `AuthContext`, `PrivateRoute`)을 신규 UI로 이식해 실제 API 연동.
- **F-2. 인증/세션 처리**: `/auth/me` 기반 부팅 인증, 401 → 로그인 리다이렉트, PENDING → 온보딩(M-3 연계).
- **F-3. 두 프론트엔드 정리**: 현행/레거시 역할 확정 후 중복 제거(레거시는 참고용으로 보존 또는 아카이브).
- **F-4. 카테고리/enum 동기화**: 백엔드 enum 기준으로 프론트 값·라벨 정렬(M-4 연계).

---

## 🚀 배포 · 운영 · 관측 (D-*)

- **D-1. 컨테이너화/CI**: Dockerfile·docker-compose(앱+MySQL), 빌드·테스트 CI 파이프라인(현재 리포에 인프라 산출물 없음).
- **D-2. 설정 외부화**: CORS 오리진·MM URL·DB·시크릿을 환경/시크릿 매니저로 일원화(M-5 연계).
- **D-3. 관측성**: 구조화 로깅·요청 추적·헬스체크(Actuator)·기본 메트릭. base `application.yml`의 `DEBUG` 로깅을 환경별로 점검.
- **D-4. 운영 런북**: 첫 관리자 생성(M-6), 신고 임계치/숨김 정책, 백업·롤백 절차 문서화.

---

## 📌 전략적 갭 요약 (리뷰 결론)

남은 30~35%의 핵심은 다음 5가지입니다.

1. **보안 하드닝** — 레이트리밋(C-1) · 입력 제한(H-3) · 외부 호출 타임아웃(H-4) · 토큰 수명/폐기(H-2)
2. **"OAuth" 표방과 비밀번호 프록시 정합** — 명칭/흐름 정리(M-7)
3. **테스트 폭** — 시큐리티·컨트롤러·`CommentService`·프론트(T-*)
4. **배포·문서·관측 인프라** — 컨테이너/CI/관측(D-*)
5. **익명성 정책 확정** — 가명 일관성/유일성(H-anon, M-4)

---

## 권장 진행 순서 (로드맵)

| 단계 | 범위 | 항목 | 상태 |
|------|------|------|------|
| **Phase 0. 즉시 버그 수정** | 1차 배포 직전 체감 버그 | Bug 1~5 + 공통 모달 | ✅ 완료 (2026-05-31) |
| **Phase 1. 보안 핫픽스** | 배포 전 필수 | C-1, H-2, H-3, H-4 | 계획 |
| **Phase 2. 정책 확정 & 연동/UX** | 설계 합의·사용성 | H-anon(익명성), M-7(인증 명칭), M-3(PENDING 가드), M-4(mock 정리) | 계획 |
| **Phase 3. 운영 품질** | 안정화 | M-2(조회수 서버단), M-1, M-5, M-6, M-8 | 계획 |
| **Phase 4. 테스트 & 인프라** | 신뢰성 | T-1~T-5, D-1~D-4, F-1~F-4(mock 통합) | 계획 |

> Phase 0은 실제 연동 프론트(`frontend/src`)의 UX/기능 버그 수정으로 완료. Phase 1~4는 본 문서의 C/H/M/T/D 항목을 우선순위에 맞춰 단계화한 것으로, 아직 미실행(문서화 단계).
