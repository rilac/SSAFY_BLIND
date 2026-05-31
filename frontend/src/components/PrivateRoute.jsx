import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * 인증 가드 — 인증되지 않은 상태면 /login으로 리다이렉트 (#9)
 * loading 중이면 스피너 표시 (초기 /api/auth/me 호출 대기)
 * M-NEW-1: 온보딩 미완료(PENDING) 유저는 보호 라우트 진입을 막고 /onboarding으로 강제.
 */
export default function PrivateRoute({ children }) {
  const { user, loading } = useAuth();

  // 초기 인증 확인 중 — 스피너
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-gray-400 text-sm">로딩 중...</div>
      </div>
    );
  }

  // 미인증 → 로그인 페이지로
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  // M-NEW-1: 온보딩 미완료(PENDING)면 어떤 보호 라우트로 직접 접근해도 온보딩으로 강제.
  // (백엔드도 PENDING은 온보딩/auth/me만 허용하므로 빈 화면 대신 온보딩으로 유도)
  if (user.status === 'PENDING') {
    return <Navigate to="/onboarding" replace />;
  }

  return children;
}
