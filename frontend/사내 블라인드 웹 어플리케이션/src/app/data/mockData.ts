// 데모용 목 데이터 — 백엔드 연결 전 UI 흐름 검증 용도.
// 인메모리로만 변경되며 새로고침 시 초기 상태로 돌아간다.

import type { Category, Comment, Notification, Post } from "../types";

// 사이드바 카테고리 id → 표시 라벨. 게시글의 category 문자열과 매칭에 사용.
export const CATEGORY_LABELS: Record<Category, string> = {
  all: "전체글",
  free: "자유게시판",
  job: "취업/이직",
  question: "질문",
  project: "프로젝트 모집",
  lounge: "익명 라운지",
};

// 글 작성/수정 시 선택 가능한 카테고리 (전체글 제외)
export const POST_CATEGORIES: Category[] = ["free", "job", "question", "project", "lounge"];

// 닉네임 형용사 / SSAFY 캐릭터
export const adjectives = [
  "열정적인", "똑똑한", "창의적인", "성실한", "긍정적인", "활발한",
  "차분한", "꼼꼼한", "적극적인", "친절한", "유쾌한", "신중한",
  "대담한", "세심한", "낙관적인", "침착한",
];

export const characters = ["스타티", "핏", "와이즈", "알지"];

export const COHORTS = ["11기", "12기", "13기", "14기"];
export const CAMPUSES = ["서울", "대전", "광주", "구미", "부울경"];

export const mockPosts: Post[] = [
  {
    id: 1,
    category: "자유게시판",
    title: "SSAFY 끝나고 취업 준비 어떻게 하셨나요?",
    preview:
      "11기 수료생입니다. 프로젝트 경험은 있는데 기술 면접 준비를 어떻게 해야 할지 막막하네요. 선배님들 조언 부탁드립니다...",
    content:
      "11기 수료생입니다. 프로젝트 경험은 있는데 기술 면접 준비를 어떻게 해야 할지 막막하네요.\n\n알고리즘은 어느 정도 풀 수 있는데, CS 지식 면접이나 프로젝트 기반 꼬리질문에 대비하는 게 가장 어렵습니다. 선배님들은 어떤 방식으로 준비하셨는지, 추천하는 자료나 스터디가 있으면 공유 부탁드려요.\n\n특히 백엔드 직군 면접 경험담이 궁금합니다!",
    author: "열정적인스타티",
    cohort: "13기",
    campus: "서울",
    createdAt: "2026-05-27T10:30:00",
    updatedAt: "2026-05-27T10:30:00",
    viewCount: 847,
    likeCount: 23,
    commentCount: 15,
    isLiked: false,
    isBookmarked: false,
    isMine: true,
  },
  {
    id: 2,
    category: "취업/이직",
    title: "카카오 개발자 채용 공고 떴네요",
    preview:
      "프론트엔드 신입 채용 시작했습니다. React, TypeScript 요구사항 있고요. SSAFY 프로젝트 경험 어필하면 괜찮을 것 같아요.",
    content:
      "프론트엔드 신입 채용이 시작됐습니다.\n\n주요 요구사항:\n- React, TypeScript 실무 경험\n- 상태 관리 라이브러리 사용 경험\n- 협업 툴(Git, Jira) 사용 경험\n\nSSAFY 프로젝트 경험을 잘 정리해서 어필하면 충분히 경쟁력 있어 보입니다. 지원하실 분들 화이팅!",
    author: "똑똑한핏",
    cohort: "12기",
    campus: "대전",
    createdAt: "2026-05-27T09:15:00",
    updatedAt: "2026-05-27T09:15:00",
    viewCount: 1240,
    likeCount: 45,
    commentCount: 28,
    isLiked: true,
    isBookmarked: true,
    isMine: false,
  },
  {
    id: 3,
    category: "프로젝트 모집",
    title: "AI 챗봇 사이드 프로젝트 팀원 구합니다 (백엔드 1명)",
    preview:
      "Python FastAPI + OpenAI API 사용하는 프로젝트입니다. Spring Boot 경험자도 환영합니다. DM 주세요!",
    content:
      "AI 챗봇 사이드 프로젝트 팀원을 구합니다.\n\n기술 스택: Python FastAPI + OpenAI API\n모집 인원: 백엔드 1명\n\nSpring Boot 경험자도 환영합니다. 주 2회 비대면 회의 예정이고, 포트폴리오로 만들기 좋은 주제예요. 관심 있으신 분 DM 주세요!",
    author: "창의적인와이즈",
    cohort: "13기",
    campus: "부울경",
    createdAt: "2026-05-27T08:42:00",
    updatedAt: "2026-05-27T08:42:00",
    viewCount: 523,
    likeCount: 12,
    commentCount: 7,
    isLiked: false,
    isBookmarked: false,
    isMine: false,
  },
  {
    id: 4,
    category: "질문",
    title: "Docker Compose 네트워크 설정 질문 있습니다",
    preview:
      "컨테이너 간 통신이 안 되는데 혹시 같은 문제 겪으신 분 계신가요? docker-compose.yml 파일 공유합니다.",
    content:
      "컨테이너 간 통신이 안 되는데 혹시 같은 문제 겪으신 분 계신가요?\n\n백엔드 컨테이너에서 DB 컨테이너로 접속할 때 connection refused가 발생합니다. depends_on은 설정했고, 같은 네트워크에 묶여 있는 것 같은데도 안 되네요.\n\n서비스 이름으로 호스트를 지정해야 하는 건지, 포트 매핑 문제인지 헷갈립니다. 조언 부탁드려요!",
    author: "성실한알지",
    cohort: "13기",
    campus: "광주",
    createdAt: "2026-05-27T07:20:00",
    updatedAt: "2026-05-27T07:20:00",
    viewCount: 312,
    likeCount: 8,
    commentCount: 11,
    isLiked: false,
    isBookmarked: false,
    isMine: false,
  },
  {
    id: 5,
    category: "익명 라운지",
    title: "컨설턴트님들 너무 감사합니다",
    preview:
      "프로젝트 막힐 때마다 도와주시고 진로 상담도 해주시고... 정말 좋은 분들만 만난 것 같아요. 감사합니다!",
    content:
      "프로젝트 막힐 때마다 도와주시고 진로 상담도 해주시고... 정말 좋은 분들만 만난 것 같아요.\n\n특히 막바지 프로젝트 때 밤늦게까지 같이 디버깅 봐주셨던 거 잊지 못할 것 같습니다. 덕분에 무사히 발표까지 마쳤어요.\n\n남은 기간도 열심히 하겠습니다. 정말 감사합니다!",
    author: "긍정적인스타티",
    cohort: "13기",
    campus: "서울",
    createdAt: "2026-05-26T22:35:00",
    updatedAt: "2026-05-26T22:35:00",
    viewCount: 2103,
    likeCount: 89,
    commentCount: 34,
    isLiked: true,
    isBookmarked: true,
    isMine: false,
  },
  {
    id: 6,
    category: "자유게시판",
    title: "점심 메뉴 추천해주세요 (대전 캠퍼스 근처)",
    preview: "매일 학식만 먹다가 질렸는데 근처 맛집 아시는 분 계신가요? 1만원 이하로...",
    content:
      "매일 학식만 먹다가 질렸는데 근처 맛집 아시는 분 계신가요?\n\n조건은 1만원 이하, 캠퍼스에서 도보 10분 이내입니다. 혼밥하기 좋은 곳이면 더 좋고요. 한식/일식/양식 가리지 않습니다!\n\n추천 많이 부탁드려요 🙏",
    author: "활발한핏",
    cohort: "13기",
    campus: "대전",
    createdAt: "2026-05-26T11:18:00",
    updatedAt: "2026-05-26T11:18:00",
    viewCount: 456,
    likeCount: 5,
    commentCount: 19,
    isLiked: false,
    isBookmarked: false,
    isMine: true,
  },
];

// 게시글별 댓글 (postId → 댓글 목록). 익명 처리되어 작성자 정보는 없다.
export const mockComments: Record<number, Comment[]> = {
  1: [
    { id: 101, content: "저는 CS 면접 대비로 스터디 만들어서 모의면접 돌렸어요. 강추합니다!", createdAt: "2026-05-27T11:05:00", isMine: false },
    { id: 102, content: "프로젝트 README에 트러블슈팅 정리해두면 꼬리질문 대응이 훨씬 수월해요.", createdAt: "2026-05-27T11:40:00", isMine: true },
    { id: 103, content: "백엔드 면접은 DB 인덱스랑 트랜잭션 격리수준 자주 물어봅니다.", createdAt: "2026-05-27T12:10:00", isMine: false },
  ],
  2: [
    { id: 201, content: "정보 감사합니다! 바로 지원해봐야겠네요.", createdAt: "2026-05-27T09:50:00", isMine: false },
    { id: 202, content: "자소서 마감일 혹시 아시나요?", createdAt: "2026-05-27T10:20:00", isMine: false },
  ],
  4: [
    { id: 401, content: "서비스 이름을 호스트로 써보세요. localhost 말고요!", createdAt: "2026-05-27T07:45:00", isMine: false },
    { id: 402, content: "저도 같은 문제였는데 네트워크 명시적으로 선언하니까 해결됐어요.", createdAt: "2026-05-27T08:02:00", isMine: false },
  ],
  5: [
    { id: 501, content: "진짜 공감합니다. 컨설턴트님들 최고예요 👍", createdAt: "2026-05-26T23:10:00", isMine: false },
  ],
};

export const mockNotifications: Notification[] = [
  { id: 1, type: "comment", message: "내 글에 새 댓글이 달렸어요: \"SSAFY 끝나고 취업 준비...\"", postId: 1, createdAt: "2026-05-27T12:10:00", isRead: false },
  { id: 2, type: "like", message: "내 글이 좋아요 5개를 받았어요: \"점심 메뉴 추천...\"", postId: 6, createdAt: "2026-05-27T09:30:00", isRead: false },
  { id: 3, type: "system", message: "커뮤니티 운영 정책이 업데이트되었습니다.", createdAt: "2026-05-26T18:00:00", isRead: true },
];
