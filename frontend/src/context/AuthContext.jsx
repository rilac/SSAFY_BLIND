import { createContext, useContext, useState, useEffect } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

/**
 * 인증 상태를 전역으로 관리하는 Context Provider.
 * 앱 로드 시 GET /api/auth/me를 호출하여 쿠키 유효성 확인.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true); // 초기 인증 확인 중

  // 앱 최초 마운트 시 인증 상태 확인
  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = async () => {
    try {
      const res = await api.get('/auth/me');
      setUser(res.data);
    } catch {
      // Access Token(30m)이 만료됐을 수 있다 — Refresh Token(14d)으로 1회 재발급 후 /auth/me 재확인.
      // axios 인터셉터(client.js)는 무한 리다이렉트 방지를 위해 /auth/* 를 자동 재발급 대상에서 제외하므로,
      // 앱 부팅(새 탭/새로고침) 시 만료된 AT를 RT로 살리는 일은 여기서 명시적으로 처리한다.
      try {
        await api.post('/auth/refresh');
        const res = await api.get('/auth/me');
        setUser(res.data);
      } catch {
        // RT도 만료/무효 → 미인증(인터셉터가 /auth/* 401은 리다이렉트하지 않으므로 루프 없음)
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  };

  // 로그인/온보딩 성공 후 유저 상태 갱신
  const refreshUser = async () => {
    try {
      const res = await api.get('/auth/me');
      setUser(res.data);
    } catch {
      setUser(null);
    }
  };

  // 로그아웃
  const logout = async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // 실패해도 클라이언트 상태는 초기화
    }
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, refreshUser, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// 커스텀 훅: 어디서든 인증 상태에 접근
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
