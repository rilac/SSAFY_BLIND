# SSAFY SOOP — 사내 익명 커뮤니티

> 상태: **Phase A~D(리뷰 반영) + Access/Refresh 토큰 분리 + Flyway 마이그레이션 + Phase E 기능 확장 11종 완료** · 운영 완성도 약 95%
> 진행 현황은 [WORKLOG.md](./WORKLOG.md), 기능 확장(Phase E) 롤백 인덱스는 [FEATURES.md](./FEATURES.md), 분석/로드맵은 [MoreDevelopments.md](./MoreDevelopments.md) · [MoreDevelopments_V2.md](./MoreDevelopments_V2.md) 참고

SSAFY Mattermost 계정 기반 사내 익명 게시판 서비스입니다. 구성원은 자신의 Mattermost 계정으로 인증한 뒤, **익명으로** 글·댓글·좋아요·스크랩을 주고받을 수 있습니다. 작성자 정보(실명/계정/이메일)는 서버 내부에서만 관리되며 API 응답에는 일절 노출되지 않습니다.

---

## 프로젝트 개요

- **인증**: Mattermost ID/PW를 백엔드가 프록시 인증 → 짧은 Access JWT(stateless) + DB 저장 Refresh Token(회전 재발급)을 HttpOnly 쿠키로 발급
- **익명성**: 게시글·댓글의 작성자는 DB에서만 연결되고, DTO 변환 단계에서 완전히 제외(댓글 스레드는 글 단위 익명 별칭 "글쓴이/익명N"으로 표시)
- **상태 머신**: 가입(PENDING) → 온보딩 완료(ACTIVE) → 휴면(DORMANT) / 탈퇴(WITHDRAWN)
- **권한**: 일반 유저(USER) / 관리자(ADMIN) Role 기반 인가
- **운영 신뢰성 설계**: N+1 배치 조회, 조회수 동시성(벌크 UPDATE) + 24h 서버단 dedup, 좋아요·스크랩·신고·투표 유니크 제약 + 멱등 처리, Flyway 스키마 버저닝(V1~V6)

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Validation, `@Scheduled`(스케줄러) |
| 인증/토큰 | Mattermost 프록시 인증 → Access/Refresh JWT 분리(jjwt 0.11.5), HttpOnly Cookie, DB 저장 Refresh Token(SHA-256 해시) 회전 |
| Database | MySQL (dev/prod 공통), H2 (테스트 전용 `@DataJpaTest`) · **Flyway** 스키마 마이그레이션(V1 베이스라인 ~ V6) |
| Frontend | React 18 (JSX), Vite 5, Axios, React Router, Tailwind CSS, lucide-react, react-markdown(코드블록 하이라이트) |
| 빌드 | Gradle (Spring Boot Plugin), Vite |

---

## 프로젝트 구조

```
SSAFY_BLIND/
├── backend/                         # Spring Boot 애플리케이션
│   └── src/main/java/com/company/community/
│       ├── client/
│       │   └── MattermostClient         # MM 로그인 프록시
│       ├── controller/
│       │   ├── AuthController           # 로그인/로그아웃/내정보·토큰 회전(refresh)
│       │   ├── OnboardingController     # 온보딩(닉네임/기수/캠퍼스)
│       │   ├── PostController           # 게시글 CRUD·반응(4종)·스크랩·신고
│       │   ├── CommentController        # 댓글·대댓글(1-depth)·답변 채택
│       │   ├── PollController           # 익명 투표(투표/변경/취소)
│       │   ├── NotificationController   # 알림 목록/읽음 처리
│       │   ├── FeedbackController        # 건의함 작성
│       │   ├── AccountController         # 휴면 전환 / 회원 탈퇴
│       │   └── AdminController           # 신고 검수·숨김 복원·공지 고정·신고 통계 / 건의함(ADMIN)
│       ├── domain/
│       │   ├── User / UserStatus / UserRole
│       │   ├── Post / PostCategory          # pinned(공지 고정)·acceptedCommentId(답변 채택)
│       │   ├── Comment                      # parentId(대댓글 1-depth)
│       │   ├── PostLike / ReactionType      # 4종 반응(좋아요/도움돼요/정보/공감)
│       │   ├── Bookmark / PostView          # 스크랩 · 조회 이력(dedup·읽음 표시)
│       │   ├── PollOption / PollVote        # 익명 투표(집계만 노출)
│       │   ├── Report / ReportReason
│       │   ├── Feedback / RefreshToken      # 건의함 · DB 저장 리프레시 토큰(회전)
│       │   └── Notification / NotificationType
│       ├── dto/                          # 요청/응답 DTO (작성자 비노출)
│       ├── repository/                   # JPA Repository (배치/벌크 쿼리)
│       ├── security/
│       │   ├── JwtProvider               # JWT 생성/파싱 (userId·status·role 클레임)
│       │   ├── JwtAuthFilter             # 쿠키 기반 인증 필터
│       │   └── SecurityConfig            # CORS·경로별 인가(/api/admin/** = ADMIN)
│       ├── service/                      # 비즈니스 로직
│       ├── scheduler/                    # 만료 RefreshToken 정리 · 주간 다이제스트(@Scheduled)
│       ├── exception/                    # 전역 예외 핸들러
│       └── util/CookieUtils              # 쿠키 생성/만료 유틸
│   └── src/main/resources/db/migration/  # Flyway V1__baseline ~ V6 (스키마 버저닝)
│
└── frontend/                           # React 18 (JSX) · Vite · 백엔드 연동(axios + HttpOnly 쿠키)
    └── src/
        ├── pages/                       #   Login·Onboarding·Feed·PostDetail·PostCreate·
        │                                #   PostEdit·Settings·Feedback·Admin
        ├── components/                  #   Sidebar·TopBar·PostCard·PostForm·Markdown·NotificationPanel·
        │                                #   ReportModal·ConfirmDialog·AlertDialog·PrivateRoute·OnboardingRoute
        ├── context/                     #   AuthContext·ThemeContext
        ├── lib/                         #   format·categories
        └── api/client.js                #   axios 인스턴스(/api 프록시, 401 처리)
```

> **프론트엔드**: `frontend/src`(React 18 JSX, Vite)가 백엔드와 실제 연동(axios + HttpOnly 쿠키)되는 **유일한 프론트엔드**입니다. 디자인 참고용 Figma export mock(`사내 블라인드 웹 어플리케이션`, TypeScript/shadcn)은 **2026-06-01 저장소에서 제거**했습니다(git 이력에는 보존).

---

## 데이터 모델

| 엔티티 | 설명 | 비고 |
|--------|------|------|
| `User` | MM 계정 매핑, 닉네임/기수/캠퍼스, 상태·권한 | `UserStatus`(PENDING/ACTIVE/DORMANT/WITHDRAWN), `UserRole`(USER/ADMIN) |
| `Post` | 게시글(제목/본문/카테고리/조회수/숨김/공지/채택) | `PostCategory`(FREE/JOB/QUESTION), `pinned`(공지 고정)·`acceptedCommentId`(채택 답변), 작성자 비노출 |
| `Comment` | 댓글 + 대댓글(1-depth) | `parentId`(답글), 게시글 삭제 시 cascade |
| `PostLike` | 반응(4종) | `(post, user)` 유니크 — **1인 1반응**, `ReactionType`(LIKE/HELPFUL/INFORMATIVE/EMPATHY) |
| `Bookmark` | 스크랩 | `(post, user)` 유니크 |
| `PostView` | 조회 이력 | `(post, user)` 유니크 — 조회수 24h dedup + 읽음/새 글 판정 |
| `PollOption`·`PollVote` | 익명 투표 보기/표 | `PollVote (post, user)` 유니크 — **1인 1표**, 집계만 노출 |
| `Report` | 신고 | `(post, reporter)` 유니크(멱등), `ReportReason` 5종 |
| `Feedback` | 건의함(관리자 전용 열람) | 일반 피드 비노출 |
| `RefreshToken` | 리프레시 토큰 | SHA-256 해시 저장, 회전 시 삭제·재발급, 만료 정리 스케줄러 |
| `Notification` | 활동 알림 | `NotificationType`(COMMENT/LIKE/SYSTEM), 작성자(actor) 비저장 |

---

## 주요 기능

### 인증 흐름
```
[사용자] MM ID/PW 입력
   → [Backend] Mattermost API 프록시 인증
   → 신규 유저: PENDING 저장 → PENDING JWT 쿠키 → 온보딩 페이지
   → 기존 유저: ACTIVE JWT 쿠키 → 피드
   → 휴면 유저: 재로그인 시 DORMANT → ACTIVE 자동 복구
```
JWT는 HttpOnly Cookie로 관리되어 브라우저 JS에서 접근할 수 없습니다(XSS 토큰 탈취 방어). 토큰 클레임에 `userId·status·role`을 담아 권한을 식별합니다. **Access/Refresh 분리**: 짧은 Access Token(기본 30분, stateless)으로 요청을 인증하고, 만료 시 DB에 SHA-256 해시로 저장한 Refresh Token(기본 14일)을 `POST /api/auth/refresh`로 회전 재발급(delete-on-rotate)합니다. 프론트는 401 응답 시 refresh를 1회 자동 재시도(single-flight)하며, 만료된 Refresh Token은 스케줄러가 주기 정리합니다.

### 온보딩
최초 로그인 시 닉네임·기수·캠퍼스를 입력하면 PENDING → ACTIVE로 전환되고 ACTIVE JWT가 재발급됩니다. PENDING 상태에서는 온보딩 API와 `/api/auth/me`만 접근 가능합니다.

### 게시판 (익명)
- 작성 / 목록 / 상세 / 수정(본인) / 삭제(본인·관리자)
- 목록: **카테고리 필터 · 제목·본문 검색 · 정렬(최신/인기) · scope(전체/내글/스크랩/우리 캠퍼스/동기) · 페이지네이션** 통합
- 공지로 고정된 글은 항상 최상단, 안 읽은 새 글은 **NEW 배지**·이미 연 글은 흐리게(읽음 표시)
- 본문은 **마크다운 + 코드블록 하이라이트** 렌더링(react-markdown, 원시 HTML 차단)
- 댓글 수·반응 수·반응/스크랩 여부는 **IN절 배치 조회**로 N+1 방지, 조회수는 DB 벌크 UPDATE + 24h dedup

### 상호작용
- **반응(4종)** 토글 — 좋아요/도움돼요/정보/공감, **1인 1반응**(작성자에게 알림, 내 글 제외)
- **스크랩** 토글 · **신고**(멱등, 누적 시 자동 숨김)
- **댓글 + 대댓글(1-depth)** 작성/조회/삭제, 글 단위 익명 별칭(글쓴이/익명N), `isMine` 플래그
- **Q&A 답변 채택**(QUESTION 글, 작성자만 · "해결됨" 배지) · **익명 투표**(보기 2~8, 집계만 노출)

### 알림 / 건의함 / 관리자
- 알림: 내 알림 목록(최신순), 단일/전체 읽음 처리 (댓글·답글·반응·**주간 인기글 다이제스트**[매주 월 09:10])
- 건의함: 인증 사용자 누구나 작성, **관리자만 열람**
- 관리자(`/api/admin/**` = `ROLE_ADMIN`): 신고(숨김) 글 검수·숨김 해제, **공지 고정**, **신고 통계 대시보드**(요약·사유별·일별 추이), 건의함 전체 조회

### 계정 관리
- **휴면 전환**(재로그인 시 ACTIVE 복구) · **회원 탈퇴**(PII 익명화, 게시글/댓글 FK 보존). 처리 후 쿠키 만료로 로그아웃.

### 기능 확장 (Phase E, 11종)

> 기능 확장 코드는 롤백이 쉽도록 `// [FEATURE:이름]` 마커로 감싸고 [FEATURES.md](./FEATURES.md)에 인덱싱(파일·범위·롤백 절차)합니다.

| 기능 | 요약 | 마이그레이션 |
|------|------|:---:|
| 마크다운/코드블록 | 본문 마크다운 렌더 + syntax highlight(프론트 전용, 원시 HTML 차단) | - |
| Q&A 채택/해결됨 | QUESTION 답변 채택 + "해결됨" 배지 | V2 |
| OP·글 단위 익명 별칭 | 댓글 스레드에서 글쓴이/익명N 일관 별칭 | - |
| 기수·캠퍼스 라운지 | "우리 캠퍼스만/동기만" scope 필터 | - |
| 대댓글(1-depth) | 답글 + 부모 작성자 알림 | V3 |
| 익명 투표 | 보기 2~8, 1인 1표, 집계만 노출 | V4 |
| 읽음/안 읽은 새 글 배지 | `PostView` 재사용, NEW 배지·읽음 흐리게 | - |
| 다양한 반응 | 좋아요 → 4종(좋아요/도움돼요/정보/공감) | V5 |
| 주간 인기글 다이제스트 | 최근 7일 인기글 Top N 앱 알림(매주 월 09:10) | - |
| 공지/고정글 | 관리자 상단 고정 + 피드 최상단·공지 배지 | V6 |
| 신고 대시보드/통계 | 관리자 신고 통계(요약·사유별·일별 추이) | - |

---

## API 명세

### 인증 · 온보딩
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/auth/login` | MM 인증 → Access/Refresh 쿠키 발급 | 불필요 |
| POST | `/api/auth/refresh` | Refresh Token 회전 → Access 재발급 | 쿠키(refresh) |
| POST | `/api/auth/logout` | 쿠키 만료 + Refresh Token 삭제 | 불필요 |
| GET | `/api/auth/me` | 현재 유저 정보 | 필요 |
| POST | `/api/onboarding` | 닉네임/기수/캠퍼스 → ACTIVE | PENDING |

### 게시글
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/posts` | 게시글 작성(투표 보기 선택 첨부) | 필요 |
| GET | `/api/posts?page&size&category&keyword&sort&scope` | 목록(scope: all/mine/bookmarked/campus/cohort) | 필요 |
| GET | `/api/posts/{id}` | 상세(조회수 +1, 24h dedup) | 필요 |
| PUT | `/api/posts/{id}` | 수정(본인만) | 필요 |
| DELETE | `/api/posts/{id}` | 삭제(본인·관리자) | 필요 |
| POST | `/api/posts/{id}/reactions` | 반응 토글(4종, 1인 1반응) | 필요 |
| POST | `/api/posts/{id}/bookmark` | 스크랩 토글 | 필요 |
| POST | `/api/posts/{id}/report` | 신고(멱등) | 필요 |
| POST | `/api/posts/{id}/poll/vote` | 투표/변경/취소 | 필요 |

### 댓글 · 알림 · 건의함 · 계정 · 관리자
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/posts/{postId}/comments` | 댓글/대댓글 작성(`parentId` 선택) | 필요 |
| GET | `/api/posts/{postId}/comments` | 댓글 목록(익명 별칭·답글 포함) | 필요 |
| DELETE | `/api/posts/{postId}/comments/{commentId}` | 댓글 삭제(본인·관리자) | 필요 |
| POST | `/api/posts/{postId}/comments/{commentId}/accept` | 답변 채택 토글(질문 작성자) | 필요 |
| GET | `/api/notifications` | 알림 목록(최신순) | 필요 |
| PATCH | `/api/notifications/{id}/read` | 단일 읽음 | 필요 |
| POST | `/api/notifications/read-all` | 전체 읽음 | 필요 |
| POST | `/api/feedback` | 건의함 작성 | 필요 |
| POST | `/api/users/me/dormant` | 휴면 전환 | 필요 |
| DELETE | `/api/users/me` | 회원 탈퇴 | 필요 |
| GET | `/api/admin/posts/reported` | 신고(숨김) 글 검수 목록 | ADMIN |
| POST | `/api/admin/posts/{id}/restore` | 숨김 해제 | ADMIN |
| POST | `/api/admin/posts/{id}/pin` | 공지 고정 토글 | ADMIN |
| GET | `/api/admin/reports/stats` | 신고 통계(요약·사유별·일별) | ADMIN |
| GET | `/api/admin/feedback` | 건의함 전체 조회 | ADMIN |

---

## 실행 방법

### 1) Backend

`dev`/`prod` 모두 MySQL을 사용합니다. 실행 전 DB와 필수 환경변수를 준비하세요.

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev \
JWT_SECRET=<256bit 이상 시크릿> \
MM_BASE_URL=https://meeting.ssafy.com \
DB_HOST=localhost DB_NAME=ssafy_blind \
DB_USERNAME=<user> DB_PASSWORD=<pw> \
./gradlew bootRun
```
서버는 `http://localhost:8080`에서 실행됩니다.

### 2) Frontend

```bash
cd frontend
npm install
npm run dev      # Vite 개발 서버 (http://localhost:5173, /api → :8080 프록시)
```
> 프로덕션 빌드는 `npm run build`. 백엔드와 axios + HttpOnly 쿠키로 연동됩니다.

### 환경변수

| 변수 | 설명 | 필수 | 적용 프로파일 |
|------|------|:---:|------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일(`dev`/`prod`) | △(기본 dev) | - |
| `JWT_SECRET` | JWT 서명 키(256bit↑). **미설정 시 기동 실패** | ✅ | 공통 |
| `MM_BASE_URL` | Mattermost 서버 URL | ✅ | 공통 |
| `DB_HOST` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 접속 정보 | ✅ | dev·prod |
| `APP_JWT_ACCESS_TTL` / `APP_JWT_REFRESH_TTL` | Access/Refresh 토큰 수명(기본 `30m` / `14d`) | - | 공통 |
| `APP_CORS_ALLOWED_ORIGINS` | CORS 허용 오리진(콤마 구분, **운영 필수**) | △ | 공통 |
| `APP_WEEKLY_DIGEST_CRON` | 주간 다이제스트 스케줄(기본 매주 월 09:10) | - | 공통 |
| `APP_REFRESH_CLEANUP_CRON` | 만료 Refresh Token 정리 스케줄(기본 매일 04:00) | - | 공통 |
| `APP_ADMIN_BOOTSTRAP_USERNAMES` | 최초 ADMIN 승격 MM username(콤마, 미설정 시 비활성) | - | 공통 |

> 그 외 로그인 레이트리밋(`LOGIN_RATE_*`)·MM 호출 타임아웃(`MM_*_TIMEOUT_MS`) 등 운영 튜닝 값은 `application.yml`의 `app.*`에서 환경변수로 override 가능합니다.
> `prod` 프로파일은 `ddl-auto: validate`(스키마 변경 금지), 쿠키 `secure: true`(HTTPS 전용), SQL 로깅 비활성으로 설정됩니다. 모든 민감 값은 환경변수 주입이며 폴백이 없습니다.
> **Flyway가 스키마를 관리합니다**(`backend/src/main/resources/db/migration`, V1 베이스라인 ~ V6). dev/prod 모두 `flyway.enabled=true`+`baseline-on-migrate=true` — 빈 DB는 V1부터 생성, 기존 DB는 V1을 베이스라인으로 표시 후 V2~만 적용합니다. 테스트(H2)는 Flyway OFF(엔티티 기반 create-drop). 기존 수동 DDL은 V1 베이스라인으로 흡수·폐기했습니다(`backend/db/README.md`).

---

## 테스트

```bash
cd backend && ./gradlew test
```
- 서비스·리포지토리 단위 테스트 **22개 클래스 / 108개 케이스**
- 리포지토리 테스트는 H2 인메모리(`@DataJpaTest`, MySQL 모드)로 JPQL 검증(라운지 필터·인기 점수 랭킹·공지 정렬 등)
- 대상: Auth · Onboarding · Post · Comment(채택/별칭/대댓글) · Bookmark · Report · Notification · Feedback · Account · Admin · Poll · RefreshToken · WeeklyDigest · 삭제 통합 · 각종 Repository

> 보안 필터/컨트롤러 슬라이스(MockMvc)/프론트엔드 테스트는 아직 없습니다([MoreDevelopments.md](./MoreDevelopments.md) T-* 참고). 프론트엔드는 `npm run build`로 빌드 검증합니다.

---

## 개발 히스토리

### Phase 1 — 인증 스켈레톤
- Mattermost 연동 로그인, JWT 발급(초기 localStorage), PENDING/ACTIVE 온보딩 흐름, React 기본 구조

### Phase 2 — 핵심 기능 + 보안 강화
- JWT 저장소 localStorage → HttpOnly Cookie 전환, PENDING 쿠키 로그인 차단 버그 수정
- 게시글 CRUD + 댓글, N+1 해결(JPQL JOIN/배치), 조회수 동시성(벌크 UPDATE), 댓글 cascade
- `@Setter` 제거 → 도메인 메서드 패턴, 페이지네이션, 프론트 인증 가드, 환경변수 외부화

### Phase 3 — 커뮤니티 기능 확장 + 1차 배포 준비
- **Role 시스템**(USER/ADMIN) 및 관리자 검수(신고글 숨김/복원, 건의함 열람)
- **좋아요 · 스크랩 · 신고(멱등) · 건의함 · 알림** 기능 추가
- 목록 통합: **카테고리·검색·정렬(최신/인기)·scope(전체/내글/스크랩)**
- 게시글 **수정**, 계정 **휴면/탈퇴**(PII 익명화)
- **MySQL 전환** 및 `dev`/`prod` 프로파일 분리, 시크릿 폴백 제거(미설정 시 즉시 실패)
- 프론트엔드 디자인 시안 **Figma export**(TypeScript/shadcn) 추가 — *참고용 mock, 2026-06-01 저장소에서 제거(실제 연동 프론트는 `frontend/src`)*

### Phase 4 — 운영 하드닝 + 토큰/스키마 (1·2차 리뷰 반영 Phase 0~D)
- 보안/계정: 로그인 레이트리밋, 입력 길이 제한, MM 호출 타임아웃, 토큰 상태 재검증, CORS 외부화, PENDING 라우팅 가드
- 운영 품질: 관리자 삭제 FK 정합, 조회수 서버단 24h dedup(`PostView`), Actuator health + 요청 추적(RequestId), 한글 웹폰트
- **Access/Refresh 토큰 분리**: 짧은 Access(stateless) + DB 저장 Refresh(SHA-256 해시, 회전 재발급, 만료 정리 스케줄러)
- **Flyway 도입**: Hibernate 생성 V1 베이스라인 + `baseline-on-migrate`, dev/prod `ddl-auto: validate` 전환

### Phase 5 — 기능 확장 (Phase E, 11종)
- 마크다운/코드블록 · Q&A 채택 · OP/글 단위 익명 별칭 · 기수·캠퍼스 라운지 · 대댓글 · 익명 투표 · 읽음/새 글 배지 · 다양한 반응(4종) · 주간 인기글 다이제스트 · 공지/고정글 · 신고 대시보드/통계
- 각 기능은 `[FEATURE:이름]` 롤백 마커 + [FEATURES.md](./FEATURES.md) 인덱싱(파일·범위·롤백 절차). 스키마 변경은 Flyway V2~V6.

---

## 알려진 한계 / 다음 단계

1·2차 리뷰의 배포 블로커와 보안/UX/운영 품질 이슈는 **Phase A~D에서 반영 완료**, 이후 **Access/Refresh 토큰 분리 · Flyway 마이그레이션 · Phase E 기능 확장 11종**까지 완료했습니다. 운영 완성도 약 **95%**.

**남은 과제**: 테스트 폭 확대(컨트롤러 슬라이스 @WebMvcTest·인가 케이스·프론트엔드 테스트), §6 백로그(모더레이션 강화[관리자 강제 숨김·감사 로그·소프트 삭제]·북마크 폴더·실시간 알림·MM DM 연동 등). 실 MySQL 기동 시 수기 DDL(V4~V6)의 `validate` 통과 최종 확인 권장.

진행 현황은 **[WORKLOG.md](./WORKLOG.md)**, 기능 확장 롤백 인덱스는 **[FEATURES.md](./FEATURES.md)**, 심각도별 이슈/로드맵은 **[MoreDevelopments.md](./MoreDevelopments.md)** · **[MoreDevelopments_V2.md](./MoreDevelopments_V2.md)**에 정리되어 있습니다.

---

## 배포

`main` 브랜치에 push하면 GitHub Actions가 자동으로 빌드·테스트·배포합니다 (`.github/workflows/deploy.yml`).

1. **빌드** — 백엔드 `./gradlew clean build`(테스트 포함), 프론트엔드 `npm run build`
2. **배포** — EC2로 jar/dist 전송 후 교체. 프론트는 스테이징 디렉터리에 풀고 통째로 교체해 무중단에 가깝게 동작합니다.
3. **검증** — `/actuator/health`를 최대 90초간 폴링. 실패하면 직전 jar와 dist로 자동 롤백하고 워크플로를 실패 처리합니다.

Pull Request에서는 빌드·테스트만 수행하고 배포하지 않습니다. 수동 재배포는 Actions 탭의 `Run workflow` 버튼으로 가능합니다.
