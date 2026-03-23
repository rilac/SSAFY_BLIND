import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * 인증 가드 — 인증되지 않은 상태면 /login으로 리다이렉트 (#9)
 * loading 중이면 스피너 표시 (초기 /api/auth/me 호출 대기)
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

  return children;
}
