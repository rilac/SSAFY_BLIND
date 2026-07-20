import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import PrivateRoute from './components/PrivateRoute';
import OnboardingRoute from './components/OnboardingRoute';
import LoginPage from './pages/LoginPage';
import OnboardingPage from './pages/OnboardingPage';
import FeedPage from './pages/FeedPage';
import PostCreatePage from './pages/PostCreatePage';
import PostDetailPage from './pages/PostDetailPage';
import PostEditPage from './pages/PostEditPage'; // (#3)
import SettingsPage from './pages/SettingsPage';
import FeedbackPage from './pages/FeedbackPage';
import AdminPage from './pages/AdminPage';

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <div className="min-h-dvh bg-background text-foreground">
          <Routes>
            {/* 공개 라우트 */}
            <Route path="/login" element={<LoginPage />} />
            {/* 온보딩 — PENDING 전용 (M-NEW-1): 미인증→/login, 이미 온보딩 완료→/feed */}
            <Route path="/onboarding" element={
              <OnboardingRoute><OnboardingPage /></OnboardingRoute>
            } />

            {/* R5: 조회성 화면도 로그인 필수 — 게스트 접근 차단(익명성 보장) */}
            <Route path="/feed" element={
              <PrivateRoute><FeedPage /></PrivateRoute>
            } />
            <Route path="/posts/:id" element={
              <PrivateRoute><PostDetailPage /></PrivateRoute>
            } />

            {/* 보호된 라우트 */}
            <Route path="/posts/new" element={
              <PrivateRoute><PostCreatePage /></PrivateRoute>
            } />
            {/* (#3) 게시글 수정 라우트 */}
            <Route path="/posts/:id/edit" element={
              <PrivateRoute><PostEditPage /></PrivateRoute>
            } />
            <Route path="/settings" element={
              <PrivateRoute><SettingsPage /></PrivateRoute>
            } />
            <Route path="/feedback" element={
              <PrivateRoute><FeedbackPage /></PrivateRoute>
            } />
            <Route path="/admin" element={
              <PrivateRoute><AdminPage /></PrivateRoute>
            } />
            {/* 알 수 없는 경로 → 공개 피드로 */}
            <Route path="*" element={<Navigate to="/feed" replace />} />
          </Routes>
        </div>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
