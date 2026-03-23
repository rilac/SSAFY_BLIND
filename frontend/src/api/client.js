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
      // /api/auth/me 호출의 401은 AuthContext에서 처리하므로 여기서는 무시
      // 그 외 API 호출의 401은 세션 만료를 의미 → 로그인으로 리다이렉트
      const requestUrl = error.config?.url || '';
      if (!requestUrl.includes('/auth/me')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
