import { useState } from "react";

interface LoginPageProps {
  onLogin: () => void;
}

// Mattermost 로그인 (데모: 입력값이 모두 있으면 통과)
export default function LoginPage({ onLogin }: LoginPageProps) {
  const [mattermostId, setMattermostId] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (mattermostId && password) {
      onLogin();
    }
  };

  return (
    <div className="h-screen w-full bg-background text-foreground flex items-center justify-center p-6">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-3xl font-mono tracking-tight mb-2">SSAFY_BLIND</h1>
          <p className="text-sm font-mono text-muted-foreground">
            INTERNAL_COMMUNITY_v1.0
          </p>
        </div>

        <form onSubmit={handleSubmit} className="border border-border bg-card p-8">
          <h2 className="text-lg font-mono mb-6">Mattermost 로그인</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-mono mb-2">Mattermost ID</label>
              <input
                type="text"
                value={mattermostId}
                onChange={(e) => setMattermostId(e.target.value)}
                placeholder="your.id@ssafy.com"
                className="w-full h-12 px-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-mono mb-2">비밀번호</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full h-12 px-4 bg-input-background border border-border text-sm font-mono placeholder:text-muted-foreground focus:outline-none focus:border-primary transition-colors"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            className="w-full h-12 bg-primary text-primary-foreground font-mono text-sm mt-6 hover:opacity-90 transition-opacity"
          >
            로그인
          </button>

          <p className="text-xs font-mono text-muted-foreground mt-4 text-center">
            Mattermost 계정으로 인증됩니다
          </p>
        </form>
      </div>
    </div>
  );
}
