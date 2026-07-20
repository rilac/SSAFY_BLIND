import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import AlertDialog from '../components/AlertDialog';
import { submitOnEnter } from '../lib/formEnter';

// Mattermost 로그인 — 데모 디자인 + 실제 /auth/login
export default function LoginPage() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      // rememberMe=false면 서버가 Refresh Token을 발급하지 않고 세션 쿠키만 → 브라우저 종료 시 로그아웃.
      const res = await api.post('/auth/login', { loginId, password, rememberMe });
      await refreshUser();
      navigate(res.data.isNewUser ? '/onboarding' : '/feed');
    } catch (err) {
      // 서버 메시지 우선, 401(인증 실패)은 원인을 명시.
      const serverMessage = err.response?.data?.message;
      const fallback =
        err.response?.status === 401
          ? 'Mattermost 인증에 실패했습니다. ID/비밀번호를 확인해주세요.'
          : '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
      setError(serverMessage || fallback);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-background text-foreground flex items-center justify-center p-6">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-3xl font-mono tracking-tight mb-2">SSAFY_SOOP</h1>
          <p className="text-sm font-mono text-muted-foreground">싸피인들을_위한_대나무숲v2.1</p>
          <p className="text-sm font-mono text-primary mt-3">16기 교육생분들 진심으로 축하하고 환영합니다!</p>
        </div>

        <form onSubmit={handleLogin} onKeyDown={submitOnEnter} className="border border-border bg-card p-6 sm:p-8">
          <h2 className="text-lg font-mono font-semibold tracking-tight mb-6">Mattermost 로그인</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-mono mb-2">Mattermost ID</label>
              <input
                type="text"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                placeholder="your.id@ssafy.com"
                required
                className="w-full h-12 px-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
              />
            </div>

            <div>
              <label className="block text-sm font-mono mb-2">비밀번호</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
                className="w-full h-12 px-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
              />
            </div>
          </div>

          <label className="flex items-center gap-2 mt-4 text-sm font-mono text-muted-foreground cursor-pointer select-none">
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              className="h-4 w-4 accent-primary"
            />
            로그인 상태 유지
          </label>

          <button
            type="submit"
            disabled={loading}
            className="w-full h-12 bg-primary text-primary-foreground font-mono text-sm mt-6 hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>

          <p className="text-xs font-mono text-muted-foreground mt-4 text-center">
            Mattermost 계정으로 인증됩니다
          </p>
        </form>
      </div>

      <AlertDialog
        open={!!error}
        title="로그인 실패"
        message={error}
        onClose={() => setError('')}
      />
    </div>
  );
}
