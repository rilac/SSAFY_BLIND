import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // ★ 쿠키를 자동으로 요청에 포함
});

// ★ localStorage 관련 코드 완전 제거 — 쿠키 방식
// 요청 인터셉터 불필요 (브라우저가 쿠키를 자동 포함)

// 응답 인터셉터: 401이면 로그인 페이지로 이동
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 인증 엔드포인트(/auth/login, /auth/logout, /auth/me 등)의 401은
      // 호출부가 직접 처리한다(로그인 실패 메시지 노출 등). 여기서 리다이렉트하면
      // 전체 페이지 리로드가 일어나 호출부가 set한 인라인 에러/모달이 즉시 사라진다.
      // 보호 리소스의 401만 세션 만료로 간주해 로그인으로 리다이렉트한다.
      const requestUrl = error.config?.url || '';
      const isAuthEndpoint = requestUrl.includes('/auth/');
      if (!isAuthEndpoint) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
