import { ChevronRight, Moon, Sun, Trash2, Archive } from "lucide-react";
import type { UserData } from "../types";

interface SettingsPageProps {
  userData: UserData;
  darkMode: boolean;
  onToggleDarkMode: () => void;
  onBack: () => void;
}

// 설정 페이지: 프로필 / 다크모드 / 휴면 / 탈퇴 (데모: 동작 버튼은 표시만)
export default function SettingsPage({
  userData,
  darkMode,
  onToggleDarkMode,
  onBack,
}: SettingsPageProps) {
  return (
    <div className="h-screen w-full bg-background text-foreground flex overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 border-r border-border bg-card flex flex-col">
        <div className="p-6 border-b border-border">
          <h1 className="text-xl font-mono tracking-tight">SSAFY_BLIND</h1>
          <p className="text-xs font-mono text-muted-foreground mt-1">
            INTERNAL_COMMUNITY_v1.0
          </p>
        </div>

        <div className="flex-1 p-4">
          <button
            onClick={onBack}
            className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-mono border border-border hover:border-primary transition-colors"
          >
            <ChevronRight size={16} className="rotate-180" />
            <span>메인으로 돌아가기</span>
          </button>
        </div>
      </aside>

      {/* Settings Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto p-8">
          <h1 className="text-2xl font-mono mb-8">설정</h1>

          <div className="space-y-6">
            {/* Profile Info */}
            <section className="border border-border bg-card p-6">
              <h2 className="text-lg font-mono mb-4">프로필 정보</h2>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <span className="font-mono text-muted-foreground">닉네임</span>
                  <span className="font-mono">{userData.nickname}</span>
                </div>
                <div className="flex justify-between">
                  <span className="font-mono text-muted-foreground">기수</span>
                  <span className="font-mono">{userData.cohort}</span>
                </div>
                <div className="flex justify-between">
                  <span className="font-mono text-muted-foreground">캠퍼스</span>
                  <span className="font-mono">{userData.campus}</span>
                </div>
              </div>
            </section>

            {/* Dark Mode */}
            <section className="border border-border bg-card p-6">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-mono mb-1">다크 모드</h2>
                  <p className="text-sm font-mono text-muted-foreground">
                    화면 테마를 변경합니다
                  </p>
                </div>
                <button
                  onClick={onToggleDarkMode}
                  className="p-3 border border-border hover:border-primary transition-colors"
                >
                  {darkMode ? <Sun size={20} /> : <Moon size={20} />}
                </button>
              </div>
            </section>

            {/* Dormant Account */}
            <section className="border border-border bg-card p-6">
              <h2 className="text-lg font-mono mb-2">휴면 계정 전환</h2>
              <p className="text-sm font-mono text-muted-foreground mb-4">
                일정 기간 계정을 사용하지 않을 경우 휴면 상태로 전환됩니다. 계정 정보는
                보관되며 재로그인 시 복구됩니다.
              </p>
              <button className="px-6 h-10 border border-border text-sm font-mono hover:border-primary transition-colors flex items-center gap-2">
                <Archive size={16} />
                휴면 계정으로 전환
              </button>
            </section>

            {/* Account Deletion */}
            <section className="border border-destructive bg-card p-6">
              <h2 className="text-lg font-mono mb-2 text-destructive">회원 탈퇴</h2>
              <p className="text-sm font-mono text-muted-foreground mb-4">
                회원 탈퇴 시 모든 게시글과 댓글의 작성자가 "탈퇴한 사용자"로 표시되며, 계정
                정보는 즉시 삭제됩니다. 이 작업은 되돌릴 수 없습니다.
              </p>
              <button className="px-6 h-10 bg-destructive text-destructive-foreground text-sm font-mono hover:opacity-90 transition-opacity flex items-center gap-2">
                <Trash2 size={16} />
                회원 탈퇴하기
              </button>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}
