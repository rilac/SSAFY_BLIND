// 공용 타입 정의
// 백엔드 DTO(PostResponse / PostListResponse / CommentResponse)의 필드명에 맞춰
// 향후 API 연동 시 그대로 매핑되도록 설계했다.

export type Page =
  | "login"
  | "nickname-setup"
  | "main"
  | "settings"
  | "post-detail"
  | "post-create"
  | "post-edit";

export type Category = "all" | "free" | "job" | "question" | "project" | "lounge";

export type SortBy = "latest" | "popular";

// 피드 보조 뷰: 전체 / 스크랩 / 내가 쓴 글
export type FeedView = "all" | "bookmarks" | "mine";

export interface Post {
  id: number;
  category: string; // 디자인 전용 라벨 (예: "자유게시판") — 실제 백엔드에는 없음
  title: string;
  preview: string; // 목록 요약
  content: string; // 상세/수정 본문
  // ↓ 디자인 전용 메타. 실제 백엔드는 익명 처리되어 author를 노출하지 않는다.
  author: string;
  cohort: string;
  campus: string;
  createdAt: string; // ISO
  updatedAt: string; // ISO
  viewCount: number;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
  isBookmarked: boolean;
  isMine: boolean; // 수정/삭제 버튼 노출 분기
}

export interface Comment {
  id: number;
  content: string;
  createdAt: string; // ISO
  isMine: boolean;
}

export interface Notification {
  id: number;
  type: "comment" | "like" | "system";
  message: string;
  postId?: number; // 연결된 게시글 (있으면 클릭 시 상세 이동)
  createdAt: string; // ISO
  isRead: boolean;
}

export interface UserData {
  nickname: string;
  cohort: string;
  campus: string;
}

// 새 글 작성 / 수정 폼에서 다루는 값
export interface PostFormValues {
  category: string;
  title: string;
  content: string;
}
