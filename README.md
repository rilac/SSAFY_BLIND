# SSAFY BLIND — 사내 익명 커뮤니티

> 상태: **1차 배포 준비 (핵심 기능 구현 완료)** · 운영 완성도 약 65~70% (코드 리뷰 기준)
> 남은 과제와 개선 로드맵은 [MoreDevelopments.md](./MoreDevelopments.md) 참고

SSAFY Mattermost 계정 기반 사내 익명 게시판 서비스입니다. 구성원은 자신의 Mattermost 계정으로 인증한 뒤, **익명으로** 글·댓글·좋아요·스크랩을 주고받을 수 있습니다. 작성자 정보(실명/계정/이메일)는 서버 내부에서만 관리되며 API 응답에는 일절 노출되지 않습니다.

---

## 프로젝트 개요

- **인증**: Mattermost ID/PW를 백엔드가 프록시 인증 → 자체 JWT를 HttpOnly 쿠키로 발급
- **익명성**: 게시글·댓글의 작성자는 DB에서만 연결되고, DTO 변환 단계에서 완전히 제외
- **상태 머신**: 가입(PENDING) → 온보딩 완료(ACTIVE) → 휴면(DORMANT) / 탈퇴(WITHDRAWN)
- **권한**: 일반 유저(USER) / 관리자(ADMIN) Role 기반 인가
- **운영 신뢰성 설계**: N+1 배치 조회, 조회수 동시성(벌크 UPDATE), 좋아요/스크랩/신고 유니크 제약 + 멱등 처리

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Validation |
| 인증/토큰 | Mattermost 프록시 인증 → JWT(jjwt 0.11.5), HttpOnly Cookie |
| Database | MySQL (dev/prod 공통), H2 (테스트 전용 `@DataJpaTest`) |
| Frontend (현행) | React 18, Vite 6, TypeScript, Tailwind CSS v4, shadcn/Radix UI, MUI |
| Frontend (레거시) | React 18(JSX), Vite, Axios, React Router |
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
│       │   ├── AuthController           # 로그인/로그아웃/내정보
│       │   ├── OnboardingController     # 온보딩(닉네임/기수/캠퍼스)
│       │   ├── PostController           # 게시글 CRUD·좋아요·스크랩·신고
│       │   ├── CommentController        # 댓글 작성/조회/삭제
│       │   ├── NotificationController   # 알림 목록/읽음 처리
│       │   ├── FeedbackController        # 건의함 작성
│       │   ├── AccountController         # 휴면 전환 / 회원 탈퇴
│       │   └── AdminController           # 신고글 검수 / 건의함 열람(ADMIN)
│       ├── domain/
│       │   ├── User / UserStatus / UserRole
│       │   ├── Post / PostCategory
│       │   ├── Comment
│       │   ├── PostLike / Bookmark
│       │   ├── Report / ReportReason
│       │   ├── Feedback
│       │   └── Notification / NotificationType
│       ├── dto/                          # 요청/응답 DTO (작성자 비노출)
│       ├── repository/                   # JPA Repository (배치/벌크 쿼리)
│       ├── security/
│       │   ├── JwtProvider               # JWT 생성/파싱 (userId·status·role 클레임)
│       │   ├── JwtAuthFilter             # 쿠키 기반 인증 필터
│       │   └── SecurityConfig            # CORS·경로별 인가(/api/admin/** = ADMIN)
│       ├── service/                      # 비즈니스 로직
│       ├── exception/                    # 전역 예외 핸들러
│       └── util/CookieUtils              # 쿠키 생성/만료 유틸
│
└── frontend/
    ├── 사내 블라인드 웹 어플리케이션/    # ★ 현행 UI (Figma 기반 재설계)
    │   └── src/app/                      #   React 18 + TS + Tailwind v4 + shadcn
    │       ├── pages/                    #   Login·Onboarding·Feed·PostDetail·
    │       │                             #   PostCreate·PostEdit·Settings
    │       ├── components/               #   Sidebar·TopBar·NotificationPanel·
    │       │                             #   PostCard·PostForm·ui(shadcn)
    │       └── data/mockData.ts          #   ※ 현재 인메모리 목 데이터로 동작(백엔드 미연동)
    │
    └── src/                              # 레거시 JSX 앱 (백엔드 연동 axios 포함, 참고용)
```

> **두 개의 프론트엔드**: 현재 화면/디자인의 기준은 `사내 블라인드 웹 어플리케이션`(TypeScript 재설계)이며, 인메모리 목 데이터로 동작합니다. 백엔드와의 실제 연동(axios + 쿠키)은 레거시 `frontend/src` JSX 앱에 구현되어 있습니다. 신규 UI의 API 연동은 남은 핵심 과제입니다([MoreDevelopments.md](./MoreDevelopments.md) F-1 참고).

---

## 데이터 모델

| 엔티티 | 설명 | 비고 |
|--------|------|------|
| `User` | MM 계정 매핑, 닉네임/기수/캠퍼스, 상태·권한 | `UserStatus`(PENDING/ACTIVE/DORMANT/WITHDRAWN), `UserRole`(USER/ADMIN) |
| `Post` | 게시글(제목/본문/카테고리/조회수/숨김) | `PostCategory`(FREE/JOB/QUESTION), 작성자 비노출 |
| `Comment` | 게시글 댓글 | 게시글 삭제 시 cascade 삭제 |
| `PostLike` | 좋아요 | `(post, user)` 유니크 — 중복 방지 |
| `Bookmark` | 스크랩 | `(post, user)` 유니크 |
| `Report` | 신고 | `(post, reporter)` 유니크(멱등), `ReportReason` 5종 |
| `Feedback` | 건의함(관리자 전용 열람) | 일반 피드 비노출 |
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
JWT는 HttpOnly Cookie로 관리되어 브라우저 JS에서 접근할 수 없습니다(XSS 토큰 탈취 방어). 토큰 클레임에 `userId·status·role`을 담아 권한을 식별합니다.

### 온보딩
최초 로그인 시 닉네임·기수·캠퍼스를 입력하면 PENDING → ACTIVE로 전환되고 ACTIVE JWT가 재발급됩니다. PENDING 상태에서는 온보딩 API와 `/api/auth/me`만 접근 가능합니다.

### 게시판 (익명)
- 작성 / 목록 / 상세 / 수정(본인) / 삭제(본인·관리자)
- 목록: **카테고리 필터 · 제목·본문 검색 · 정렬(최신/인기) · scope(전체/내글/스크랩) · 페이지네이션** 통합
- 댓글 수·좋아요 수·좋아요/스크랩 여부는 **IN절 배치 조회**로 N+1 방지
- 조회수는 DB 벌크 UPDATE로 동시성 안전 처리

### 상호작용
- **좋아요** 토글 (작성자에게 알림 생성, 내 글 제외) · **스크랩** 토글 · **신고**(멱등, 누적 시 자동 숨김)
- **댓글** 작성/조회/삭제(본인·관리자), `isMine` 플래그 제공

### 알림 / 건의함 / 관리자
- 알림: 내 알림 목록(최신순), 단일/전체 읽음 처리
- 건의함: 인증 사용자 누구나 작성, **관리자만 열람**
- 관리자: 신고(숨김) 게시글 검수 목록, 숨김 해제, 건의함 전체 조회 — `/api/admin/**`는 `ROLE_ADMIN`

### 계정 관리
- **휴면 전환**(재로그인 시 ACTIVE 복구) · **회원 탈퇴**(PII 익명화, 게시글/댓글 FK 보존). 처리 후 쿠키 만료로 로그아웃.

---

## API 명세

### 인증 · 온보딩
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/auth/login` | MM 인증 → JWT 쿠키 발급 | 불필요 |
| POST | `/api/auth/logout` | 쿠키 만료 | 불필요 |
| GET | `/api/auth/me` | 현재 유저 정보 | 필요 |
| POST | `/api/onboarding` | 닉네임/기수/캠퍼스 → ACTIVE | PENDING |

### 게시글
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/posts` | 게시글 작성 | 필요 |
| GET | `/api/posts?page&size&category&keyword&sort&scope` | 목록(필터/검색/정렬/scope) | 필요 |
| GET | `/api/posts/{id}` | 상세(조회수 +1) | 필요 |
| PUT | `/api/posts/{id}` | 수정(본인만) | 필요 |
| DELETE | `/api/posts/{id}` | 삭제(본인·관리자) | 필요 |
| POST | `/api/posts/{id}/like` | 좋아요 토글 | 필요 |
| POST | `/api/posts/{id}/bookmark` | 스크랩 토글 | 필요 |
| POST | `/api/posts/{id}/report` | 신고(멱등) | 필요 |

### 댓글 · 알림 · 건의함 · 계정 · 관리자
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/posts/{postId}/comments` | 댓글 작성 | 필요 |
| GET | `/api/posts/{postId}/comments` | 댓글 목록 | 필요 |
| DELETE | `/api/posts/{postId}/comments/{commentId}` | 댓글 삭제(본인·관리자) | 필요 |
| GET | `/api/notifications` | 알림 목록(최신순) | 필요 |
| PATCH | `/api/notifications/{id}/read` | 단일 읽음 | 필요 |
| POST | `/api/notifications/read-all` | 전체 읽음 | 필요 |
| POST | `/api/feedback` | 건의함 작성 | 필요 |
| POST | `/api/users/me/dormant` | 휴면 전환 | 필요 |
| DELETE | `/api/users/me` | 회원 탈퇴 | 필요 |
| GET | `/api/admin/posts/reported` | 신고(숨김) 글 검수 목록 | ADMIN |
| POST | `/api/admin/posts/{id}/restore` | 숨김 해제 | ADMIN |
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

### 2) Frontend (현행 UI)

```bash
cd "frontend/사내 블라인드 웹 어플리케이션"
npm install
npm run dev      # Vite 개발 서버
```
> 현재 화면은 인메모리 목 데이터로 동작합니다. 백엔드 연동은 [MoreDevelopments.md](./MoreDevelopments.md) F-1 참고.

### 환경변수

| 변수 | 설명 | 필수 | 적용 프로파일 |
|------|------|:---:|------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일(`dev`/`prod`) | △(기본 dev) | - |
| `JWT_SECRET` | JWT 서명 키(256bit↑). **미설정 시 기동 실패** | ✅ | 공통 |
| `MM_BASE_URL` | Mattermost 서버 URL | ✅ | 공통 |
| `DB_HOST` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 접속 정보 | ✅ | dev·prod |

> `prod` 프로파일은 `ddl-auto: validate`(스키마 변경 금지), 쿠키 `secure: true`(HTTPS 전용), SQL 로깅 비활성으로 설정됩니다. 모든 민감 값은 환경변수 주입이며 폴백이 없습니다.

---

## 테스트

```bash
cd backend && ./gradlew test
```
- 서비스 단위 테스트 9종 + 리포지토리 테스트 1종, 총 **32개 테스트**
- 리포지토리 테스트는 H2 인메모리(`@DataJpaTest`)로 JPQL 검증
- 대상: Auth · Onboarding · Post · Bookmark · Report · Notification · Feedback · Account · Admin · PostRepository

> 보안 필터/컨트롤러(MockMvc)/`CommentService`/프론트엔드 테스트는 아직 없습니다([MoreDevelopments.md](./MoreDevelopments.md) T-* 참고).

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
- 프론트엔드 **Figma 기반 재설계**(TypeScript · Tailwind v4 · shadcn)

---

## 알려진 한계 / 다음 단계

코드 리뷰 결과 **운영 완성도 약 65~70%**로 평가되었습니다. 보안 하드닝(레이트리밋·입력 길이 제한·외부 호출 타임아웃·토큰 수명/폐기), 신규 UI의 백엔드 연동, 테스트 폭 확대, 배포·관측 인프라, 익명성 정책 확정이 남은 핵심 과제입니다.

자세한 이슈 목록(심각도별)과 개선 로드맵은 **[MoreDevelopments.md](./MoreDevelopments.md)**에 정리되어 있습니다.
