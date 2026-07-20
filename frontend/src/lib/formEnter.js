// 폼 입력창에서 Enter → 제출을 모든 폼에서 동일하게 동작시키는 공통 핸들러.
//
// 배경: 브라우저의 암묵적 제출(submit 버튼이 있으면 input에서 Enter 시 자동 제출)이
// 일부 환경에서 동작하지 않는다는 제보가 있어, 로그인 폼은 명시적으로 처리해 왔다.
// 그 동작을 관리자 2차 인증·검색·건의 등 모든 폼에 통일한다.
//
// 규칙:
// - Enter 키에만 반응. isComposing(한글 조합 확정 Enter)은 무시 — 마지막 글자 확정이 제출로 오인되면 안 된다.
// - textarea에서는 제출하지 않는다(줄바꿈이 정상 동작). 게시글 본문·건의 내용이 여기 해당.
// - Shift+Enter도 제출하지 않는다(줄바꿈 의도).
// - requestSubmit()으로 제출 — 클릭과 동일하게 required 등 네이티브 검증을 거친다(submit()은 검증 생략이라 미사용).
// - preventDefault로 암묵적 제출과 겹쳐 두 번 호출되는 것을 막는다.
export function submitOnEnter(e) {
  if (e.key !== 'Enter' || e.nativeEvent.isComposing || e.shiftKey) return;

  const target = e.target;
  if (target && target.tagName === 'TEXTAREA') return; // 줄바꿈 허용

  const form = e.currentTarget;
  e.preventDefault();
  if (typeof form.requestSubmit === 'function') {
    form.requestSubmit();
  } else if (typeof form.submit === 'function') {
    // 구형 폴백 — 이 경로에선 네이티브 검증이 생략되므로 최신 브라우저에선 위 분기가 항상 우선.
    form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
  }
}
