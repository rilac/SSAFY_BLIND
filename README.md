# 사내 익명 커뮤니티 (40% 구현 완료, 진행 중)

SSAFY Mattermost 계정 기반 사내 익명 게시판 서비스

## 프로젝트 개요

사내 구성원들이 Mattermost 계정으로 인증한 뒤, 익명으로 자유롭게 소통할 수 있는 커뮤니티 서비스입니다. 작성자 정보는 서버 내부에서만 관리되며, API 응답에는 일절 노출되지 않습니다.

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Frontend | React 18, Vite, Tailwind CSS, Axios, React Router v6 |
| Database | H2 (개발용, 인메모리) |
| 인증 | Mattermost OAuth → JWT (HttpOnly Cookie) |
| 빌드 | Gradle 8.7 |

## 프로젝트 구조

```
community/
├── backend/
│   └── src/main/java/com/company/community/
│       ├── client/                  # 외부 API 클라이언트
│       │   └── MattermostClient     # MM 로그인 프록시
│       ├── controller/
│       │   ├── AuthController       # 로그인/로그아웃/내정보
│       │   ├── OnboardingController # 온보딩 (닉네임/부서 설정)
│       │   ├── PostController       # 게시글 CRUD
│       │   └── CommentController    # 댓글 작성/조회
│       ├── domain/
│       │   ├── User                 # UserDetails 구현, 도메인 메서드
│       │   ├── UserStatus           # PENDING / ACTIVE
│       │   ├── Post                 # 게시글 (cascade 댓글 삭제)
│       │   └── Comment              # 댓글
│       ├── dto/                     # 요청/응답 DTO
│       ├── exception/               # 전역 예외 핸들러
│       ├── repository/              # JPA Repository
│       ├── security/
│       │   ├── JwtProvider          # JWT 생성/파싱
│       │   ├── JwtAuthFilter        # 쿠키 기반 인증 필터
│       │   └── SecurityConfig       # CORS, 경로별 인가
│       ├── service/                 # 비즈니스 로직
│       └── util/
│           └── CookieUtils          # 쿠키 생성/삭제 유틸리티
│
└── frontend/
    └── src/
        ├── api/
        │   └── client.js            # Axios 인스턴스 (withCredentials)
        ├── components/
        │   └── PrivateRoute.jsx     # 인증 가드
        ├── context/
        │   └── AuthContext.jsx       # 전역 인증 상태 관리
        └── pages/
            ├── LoginPage.jsx        # MM 로그인
            ├── OnboardingPage.jsx   # 닉네임/부서 설정
            ├── FeedPage.jsx         # 게시글 목록 (페이지네이션)
            ├── PostCreatePage.jsx   # 게시글 작성
            └── PostDetailPage.jsx   # 게시글 상세 + 댓글
```

## 실행 방법

### Backend

```bash
cd backend
./gradlew bootRun
```

서버가 `http://localhost:8080`에서 실행됩니다. H2 콘솔은 `http://localhost:8080/h2-console`에서 접근 가능합니다.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

개발 서버가 `http://localhost:5173`에서 실행됩니다. Vite 프록시를 통해 API 요청이 백엔드로 전달됩니다.

### 환경변수 (선택)

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) | 개발용 기본값 |
| `MM_BASE_URL` | Mattermost 서버 URL | `https://meeting.ssafy.com` |

## 주요 기능

### 인증 흐름

```
[사용자] → MM ID/PW 입력
    → [Backend] Mattermost API 인증
    → 신규 유저: PENDING 상태로 DB 저장 → PENDING JWT 쿠키 발급 → 온보딩 페이지
    → 기존 유저: ACTIVE JWT 쿠키 발급 → 피드 페이지
```

JWT는 HttpOnly Cookie로 관리됩니다. 브라우저의 JavaScript에서 토큰에 접근할 수 없으므로 XSS 공격에 안전합니다.

### 온보딩

최초 로그인 시 닉네임과 부서를 설정하면 PENDING → ACTIVE 상태로 전환됩니다. PENDING 상태에서는 온보딩 API와 `/api/auth/me`만 접근 가능합니다.

### 익명 게시판

게시글과 댓글의 작성자 정보는 DB에서만 관리되며, API 응답의 DTO 변환 시 완전히 제외됩니다.

### 게시글

- 작성, 목록 조회 (페이지네이션), 상세 조회, 삭제 (본인만)
- 목록에서 제목, 조회수, 댓글 수, 작성 시간 표시
- 조회수는 DB 레벨 벌크 UPDATE로 동시성 안전하게 처리

### 댓글

- 게시글별 댓글 작성, 목록 조회 (오래된 순)
- 게시글 삭제 시 연관 댓글 cascade 자동 삭제

## API 명세

### 인증

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/auth/login` | MM 로그인 → JWT 쿠키 발급 | 불필요 |
| POST | `/api/auth/logout` | 쿠키 삭제 | 불필요 |
| GET | `/api/auth/me` | 현재 유저 정보 조회 | 필요 |

### 온보딩

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/onboarding` | 닉네임/부서 설정 → ACTIVE 전환 | 필요 (PENDING) |

### 게시글

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/posts` | 게시글 작성 | 필요 |
| GET | `/api/posts?page=0&size=20` | 게시글 목록 (페이지네이션) | 필요 |
| GET | `/api/posts/{id}` | 게시글 상세 (조회수 +1) | 필요 |
| DELETE | `/api/posts/{id}` | 게시글 삭제 (본인만) | 필요 |

### 댓글

| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| POST | `/api/posts/{postId}/comments` | 댓글 작성 | 필요 |
| GET | `/api/posts/{postId}/comments` | 댓글 목록 조회 | 필요 |

## 개발 히스토리

### Phase 1 — 인증 스켈레톤

- Mattermost 연동 로그인
- JWT 발급 (초기 localStorage 방식)
- PENDING/ACTIVE 상태 기반 온보딩 흐름
- React 프론트엔드 기본 구조

### Phase 2 — 핵심 기능 구현 + 보안 강화

- JWT 저장소를 localStorage → HttpOnly Cookie로 전환
- PENDING 쿠키가 로그인을 차단하는 버그 수정 (`shouldNotFilter`)
- 게시글 CRUD + 댓글 기능 추가
- N+1 쿼리 해결 (JPQL JOIN으로 게시글 + 댓글 수 한 번에 조회)
- 조회수 동시성 처리 (`@Modifying @Query` 벌크 UPDATE)
- 게시글 삭제 시 댓글 cascade 처리 (`CascadeType.ALL`)
- 엔티티 `@Setter` 제거 → 도메인 메서드 패턴 적용
- 페이지네이션 적용 (`Pageable` + `PageResponse` 래퍼)
- 프론트엔드 인증 가드 (`AuthContext` + `PrivateRoute`)
- 쿠키 유틸리티 클래스 분리 (`CookieUtils`)
- 환경변수 외부화 (JWT secret, MM base-url)
- `@JsonProperty("isNewUser")` 직렬화 이슈 수정
