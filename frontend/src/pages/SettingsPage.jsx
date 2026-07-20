import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, Moon, Sun, Archive, Trash2, LogOut } from 'lucide-react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import Logo from '../components/Logo';
import ConfirmDialog from '../components/ConfirmDialog';
import AlertDialog from '../components/AlertDialog';

// 설정 — 내 정보(닉네임/기수/캠퍼스) / 다크모드 / 휴면 / 탈퇴 / 로그아웃
export default function SettingsPage() {
  const navigate = useNavigate();
  const { user, refreshUser, logout } = useAuth();
  const { darkMode, toggleDarkMode } = useTheme();

  // 커스텀 모달(M-NEW-4) — 네이티브 alert/confirm 대체
  const [notice, setNotice] = useState(null); // { title?, message }
  const [confirmState, setConfirmState] = useState(null); // { ...props, onConfirm }

  const requestDormant = () => {
    setConfirmState({
      title: '휴면 계정 전환',
      message: '휴면 계정으로 전환하시겠습니까?\n다시 로그인하면 복구됩니다.',
      confirmLabel: '휴면 전환',
      onConfirm: performDormant,
    });
  };

  const performDormant = async () => {
    try {
      await api.post('/users/me/dormant');
      await refreshUser();
      navigate('/login');
    } catch {
      setNotice({ title: '휴면 전환 실패', message: '휴면 전환에 실패했습니다.' });
    }
  };

  const requestWithdraw = () => {
    setConfirmState({
      title: '회원 탈퇴',
      message: '정말로 회원 탈퇴하시겠습니까?\n이 작업은 되돌릴 수 없습니다.',
      confirmLabel: '탈퇴',
      danger: true,
      onConfirm: performWithdraw,
    });
  };

  const performWithdraw = async () => {
    try {
      await api.delete('/users/me');
      await refreshUser();
      navigate('/login');
    } catch {
      setNotice({ title: '회원 탈퇴 실패', message: '회원 탈퇴에 실패했습니다.' });
    }
  };

  // 확인 모달 '확인' 클릭 — 모달을 닫고 저장된 액션 실행
  const handleConfirm = () => {
    const fn = confirmState?.onConfirm;
    setConfirmState(null);
    fn?.();
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  // 피드와 동일하게 확인 모달을 거친 뒤 로그아웃(설정에서 즉시 로그아웃되던 동작 통일)
  const requestLogout = () => {
    setConfirmState({
      title: '로그아웃',
      message: '정말 로그아웃 하시겠습니까?',
      confirmLabel: '로그아웃',
      danger: true,
      onConfirm: handleLogout,
    });
  };

  return (
    <div className="h-[100dvh] w-full bg-background text-foreground flex flex-col md:flex-row overflow-y-auto md:overflow-hidden">
      {/* Sidebar */}
      <aside className="w-full md:w-64 md:shrink-0 border-b md:border-b-0 md:border-r border-border bg-card flex flex-col">
        <button
          onClick={() => navigate('/feed')}
          className="p-6 border-b border-border text-left hover:bg-muted transition-colors"
        >
          <div className="flex items-center gap-2">
            <Logo size={20} className="text-primary" />
            <h1 className="text-xl font-mono tracking-tight">SSAFY_SOOP</h1>
          </div>
          <p className="text-xs font-mono text-muted-foreground mt-1">싸피인들을_위한_대나무숲v2.1</p>
        </button>
        <div className="flex-1 p-4 space-y-1">
          <button
            onClick={() => navigate('/feed')}
            className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border border-border hover:border-primary transition-colors"
          >
            <ChevronRight size={16} className="rotate-180" />
            <span>메인으로 돌아가기</span>
          </button>
          <button
            onClick={requestLogout}
            className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border border-transparent hover:border-border transition-colors text-destructive"
          >
            <LogOut size={16} />
            <span>로그아웃</span>
          </button>
        </div>
      </aside>

      {/* Content */}
      <div className="flex-1 min-w-0 overflow-y-auto">
        <div className="max-w-3xl mx-auto p-4 md:p-8">
          <h1 className="text-2xl font-mono mb-8">설정</h1>

          <div className="space-y-6">
            {/* Profile */}
            <section className="border border-border bg-card p-6">
              <h2 className="text-lg font-mono font-semibold tracking-tight mb-4">프로필 정보</h2>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="font-mono text-muted-foreground">닉네임</span>
                  <span className="font-mono">{user?.nickname || '-'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="font-mono text-muted-foreground">기수</span>
                  <span className="font-mono">{user?.cohort || '-'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="font-mono text-muted-foreground">캠퍼스</span>
                  <span className="font-mono">{user?.campus || '-'}</span>
                </div>
              </div>
            </section>

            {/* Dark Mode */}
            <section className="border border-border bg-card p-6">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-mono font-semibold tracking-tight mb-1">다크 모드</h2>
                  <p className="text-sm font-mono text-muted-foreground">화면 테마를 변경합니다</p>
                </div>
                <button
                  onClick={toggleDarkMode}
                  className="p-3 border border-border hover:border-primary transition-colors"
                >
                  {darkMode ? <Sun size={20} /> : <Moon size={20} />}
                </button>
              </div>
            </section>

            {/* Dormant */}
            <section className="border border-border bg-card p-6">
              <h2 className="text-lg font-mono font-semibold tracking-tight mb-2">휴면 계정 전환</h2>
              <p className="text-sm font-mono text-muted-foreground mb-4">
                일정 기간 계정을 사용하지 않을 경우 휴면 상태로 전환됩니다. 계정 정보는 보관되며 재로그인 시 복구됩니다.
              </p>
              <button
                onClick={requestDormant}
                className="px-6 h-10 border border-border text-sm font-mono hover:border-primary transition-colors flex items-center gap-2"
              >
                <Archive size={16} />
                휴면 계정으로 전환
              </button>
            </section>

            {/* Withdrawal */}
            <section className="border border-destructive bg-card p-6">
              <h2 className="text-lg font-mono font-semibold tracking-tight mb-2 text-destructive">회원 탈퇴</h2>
              <p className="text-sm font-mono text-muted-foreground mb-4">
                회원 탈퇴 시 작성한 게시글과 댓글은 익명으로 보존되며, 계정 정보는 즉시 삭제됩니다. 이 작업은 되돌릴 수 없습니다.
              </p>
              <button
                onClick={requestWithdraw}
                className="px-6 h-10 bg-destructive text-destructive-foreground text-sm font-mono hover:opacity-90 transition-opacity flex items-center gap-2"
              >
                <Trash2 size={16} />
                회원 탈퇴하기
              </button>
            </section>
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={!!confirmState}
        title={confirmState?.title}
        message={confirmState?.message}
        confirmLabel={confirmState?.confirmLabel}
        danger={confirmState?.danger}
        onConfirm={handleConfirm}
        onClose={() => setConfirmState(null)}
      />
      <AlertDialog
        open={!!notice}
        title={notice?.title}
        message={notice?.message}
        onClose={() => setNotice(null)}
      />
    </div>
  );
}
