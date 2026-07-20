// R8: 관리자 API가 2차 인증(step-up)을 요구할 때의 신호 — 403 { code: 'STEP_UP_REQUIRED' }.
// 백엔드 AdminStepUpInterceptor가 /api/admin/** 전체에 이 응답을 낸다(마커 TTL 15분).
//
// AdminPage와 AdminUserManagement가 각각 로컬 정의를 두고 "순환 import 방지"라고 주석을 달아 두었는데,
// lib은 pages/components를 import하지 않으므로 여기 두면 순환이 생기지 않는다.
export const isStepUpError = (err) =>
  err?.response?.status === 403 && err?.response?.data?.code === 'STEP_UP_REQUIRED';

// Promise.allSettled 결과용 — 여러 요청 중 하나라도 step-up을 요구했는지 판정.
export const isStepUpRejection = (result) =>
  result?.status === 'rejected' && isStepUpError(result.reason);
