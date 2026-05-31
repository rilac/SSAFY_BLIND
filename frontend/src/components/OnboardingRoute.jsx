import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * 온보딩 라우트 가드 (M-NEW-1) — PrivateRoute의 역가드.
 * - 초기 인증 확인 중이면 스피너
 * - 미인증 → /login (온보딩은 JWT 쿠키가 있어야 의미가 있음)
 * - 이미 온보딩을 마친 유저(PENDING이 아님 = ACTIVE) → /feed로 돌려보내 재진입 차단
 * - PENDING만 온보딩 화면 노출
 *
 * 참고: DORMANT/WITHDRAWN은 백엔드(JwtAuthFilter)가 401로 차단하므로 user가 null이 되어
 * 위 미인증 분기로 처리된다. 따라서 로드된 user의 status는 PENDING 또는 ACTIVE뿐이다.
 */
export default function OnboardingRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-gray-400 text-sm">로딩 중...</div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (user.status !== 'PENDING') {
    return <Navigate to="/feed" replace />;
  }

  return children;
}
