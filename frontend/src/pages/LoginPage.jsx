import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import AlertDialog from '../components/AlertDialog';

// Mattermost 로그인 — 데모 디자인 + 실제 /auth/login
export default function LoginPage() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await api.post('/auth/login', { loginId, password });
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
          <h1 className="text-3xl font-mono tracking-tight mb-2">SSAFY_BLIND</h1>
          <p className="text-sm font-mono text-muted-foreground">INTERNAL_COMMUNITY_v1.0</p>
        </div>

        <form onSubmit={handleLogin} className="border border-border bg-card p-8">
          <h2 className="text-lg font-mono mb-6">Mattermost 로그인</h2>

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
