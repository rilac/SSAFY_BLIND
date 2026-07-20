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

/**
 * /auth/refresh 단일 호출 큐(single-flight). 진행 중인 재발급이 있으면 그 Promise를 그대로 준다.
 *
 * 이 탭 안의 모든 재발급은 반드시 이 함수를 거쳐야 한다 — AuthContext.checkAuth가 인터셉터를
 * 우회해 직접 api.post('/auth/refresh')를 부르면 같은 탭에서 회전이 두 번 일어나 서버의 재사용
 * 탐지를 자극한다. (탭 '간' 경합은 서버의 회전 유예 창이 흡수하므로 여기서 조율하지 않는다.)
 *
 * 리다이렉트는 하지 않는다 — 실패 처리는 호출부(인터셉터는 로그인 이동, checkAuth는 미인증 상태)의 몫.
 */
export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = api.post('/auth/refresh').finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

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
      // 동시 401은 단일 refresh 호출로 큐잉(중복 재발급 방지) — AuthContext와 같은 큐를 공유한다.
      await refreshSession();
      return api(original); // 새 AT 쿠키로 원요청 재시도
    } catch (refreshError) {
      redirectToLogin(); // RT도 만료/무효 → 재로그인 필요
      return Promise.reject(refreshError);
    }
  }
);

export default api;
