import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import PrivateRoute from './components/PrivateRoute';
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
        <div className="min-h-screen bg-background text-foreground">
          <Routes>
            {/* 공개 라우트 */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/onboarding" element={<OnboardingPage />} />

            {/* 보호된 라우트 */}
            <Route path="/feed" element={
              <PrivateRoute><FeedPage /></PrivateRoute>
            } />
            <Route path="/posts/new" element={
              <PrivateRoute><PostCreatePage /></PrivateRoute>
            } />
            <Route path="/posts/:id" element={
              <PrivateRoute><PostDetailPage /></PrivateRoute>
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

            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </div>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
