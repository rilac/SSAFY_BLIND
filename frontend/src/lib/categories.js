// 백엔드 PostCategory enum ↔ 한글 라벨 매핑

export const CATEGORY_LABELS = {
  FREE: '자유게시판',
  JOB: '취업/이직',
  QUESTION: '질문',
  FOOD: '맛집 공유', // [FEATURE:food-board]
};

// 글 작성/수정 시 선택 가능한 카테고리 (enum 값)
export const POST_CATEGORIES = ['FREE', 'JOB', 'QUESTION', 'FOOD']; // [FEATURE:food-board] FOOD 추가

export function categoryLabel(category) {
  return CATEGORY_LABELS[category] || category || '기타';
}
