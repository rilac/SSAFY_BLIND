import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PrivateRoute from './components/PrivateRoute';
import LoginPage from './pages/LoginPage';
import OnboardingPage from './pages/OnboardingPage';
import FeedPage from './pages/FeedPage';
import PostCreatePage from './pages/PostCreatePage';
import PostDetailPage from './pages/PostDetailPage';

function App() {
  return (
    // ★ AuthProvider로 전체 앱을 감싸서 인증 상태 전역 관리 (#9)
    <AuthProvider>
      <div className="min-h-screen bg-gray-50">
        <Routes>
          {/* 공개 라우트 */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/onboarding" element={<OnboardingPage />} />

          {/* ★ 보호된 라우트 — PrivateRoute로 감싸기 (#9) */}
          <Route path="/feed" element={
            <PrivateRoute><FeedPage /></PrivateRoute>
          } />
          <Route path="/posts/new" element={
            <PrivateRoute><PostCreatePage /></PrivateRoute>
          } />
          <Route path="/posts/:id" element={
            <PrivateRoute><PostDetailPage /></PrivateRoute>
          } />

          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </div>
    </AuthProvider>
  );
}

export default App;
