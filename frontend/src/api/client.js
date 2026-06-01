import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // ★ 쿠키를 자동으로 요청에 포함
});

// ★ localStorage 관련 코드 완전 제거 — 쿠키 방식
// 요청 인터셉터 불필요 (브라우저가 쿠키를 자동 포함)

// 🗓️ 2026-06-02: Access/Refresh 토큰 분리 — 짧은 Access Token(30m)이 만료되면 보호 API가 401을
// 반환한다. 이때 refresh_token 쿠키로 /api/auth/refresh를 1회 호출해 새 AT를 받고 원요청을 재시도한다.
// 재발급 실패(RT 만료/무효)면 로그인으로 유도. 동시에 여러 요청이 401나도 refresh는 한 번만(single-flight).
let refreshPromise = null;

function redirectToLogin() {
  window.location.href = '/login';
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const requestUrl = original?.url || '';
    // 인증 엔드포인트(/auth/login, /auth/logout, /auth/me, /auth/refresh)의 401은
    // 호출부/refresh 로직이 직접 처리한다(여기서 리다이렉트하면 호출부 인라인 에러/모달이 사라짐).
    const isAuthEndpoint = requestUrl.includes('/auth/');

    if (status !== 401 || isAuthEndpoint || original?._retried) {
      // 비-401, 인증 엔드포인트, 또는 이미 재시도한 요청 → 기존 동작 유지
      if (status === 401 && !isAuthEndpoint && original?._retried) {
        // 재발급 후에도 401(예: 휴면/탈퇴) → 세션 만료로 간주
        redirectToLogin();
      }
      return Promise.reject(error);
    }

    // 보호 리소스의 최초 401 — Access Token 만료로 보고 재발급 시도
    original._retried = true;
    try {
      // 동시 401은 단일 refresh 호출로 큐잉(중복 재발급 방지)
      refreshPromise = refreshPromise || api.post('/auth/refresh');
      await refreshPromise;
      refreshPromise = null;
      return api(original); // 새 AT 쿠키로 원요청 재시도
    } catch (refreshError) {
      refreshPromise = null;
      redirectToLogin(); // RT도 만료/무효 → 재로그인 필요
      return Promise.reject(refreshError);
    }
  }
);

export default api;
