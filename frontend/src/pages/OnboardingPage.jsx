import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import AlertDialog from '../components/AlertDialog';

const ADJECTIVES = [
  '열정적인', '똑똑한', '창의적인', '성실한', '긍정적인', '활발한',
  '차분한', '꼼꼼한', '적극적인', '친절한', '유쾌한', '신중한',
  '대담한', '세심한', '낙관적인', '침착한',
];
const CHARACTERS = ['스타티', '핏', '와이즈', '알지'];
// 현재 운영(재학) 기수 — 백엔드 OnboardingOptions.COHORTS와 1:1 동기 유지.
// 2026-07: 14기 졸업(제외), 16기 입학(활성화). 모집중 기수 없음.
const COHORTS = ['15기', '16기'];
const RECRUITING_COHORTS = [];
const CAMPUSES = ['서울', '대전', '광주', '부울경', '구미'];

// 온보딩 — 닉네임(형용사+캐릭터) + 기수 + 캠퍼스 → POST /onboarding {nickname, cohort, campus}
export default function OnboardingPage() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();
  const [adjective, setAdjective] = useState('');
  const [character, setCharacter] = useState('');
  const [cohort, setCohort] = useState('');
  const [campus, setCampus] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState(null); // 모집중 기수 등 안내 모달

  const isComplete = adjective && character && cohort && campus;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isComplete) return;
    setError('');
    setLoading(true);
    try {
      // 형용사와 명사 사이 한 칸 띄움: "긍정적인 스타티"
      await api.post('/onboarding', { nickname: `${adjective} ${character}`, cohort, campus });
      await refreshUser();
      navigate('/feed');
    } catch (err) {
      setError(err.response?.data?.message || '온보딩 처리에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const chip = (active) =>
    `px-4 py-2 text-sm font-mono border transition-colors ${
      active
        ? 'bg-primary text-primary-foreground border-primary'
        : 'bg-transparent border-border hover:border-primary'
    }`;

  return (
    <div className="min-h-screen w-full bg-background text-foreground flex items-center justify-center p-6">
      <div className="w-full max-w-2xl">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-mono tracking-tight mb-2">프로필 설정</h1>
          <p className="text-sm font-mono text-muted-foreground">
            익명 닉네임과 캠퍼스 정보를 선택해주세요
          </p>
        </div>

        <form onSubmit={handleSubmit} className="border border-border bg-card p-8">
          <div className="space-y-6">
            <div>
              <label className="block text-sm font-mono mb-3">형용사 선택</label>
              <div className="grid grid-cols-4 gap-2">
                {ADJECTIVES.map((adj) => (
                  <button key={adj} type="button" onClick={() => setAdjective(adj)} className={chip(adjective === adj)}>
                    {adj}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-mono mb-3">SSAFY 캐릭터 선택</label>
              <div className="grid grid-cols-4 gap-2">
                {CHARACTERS.map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setCharacter(c)}
                    className={`px-4 py-3 text-sm font-mono border transition-colors ${
                      character === c
                        ? 'bg-primary text-primary-foreground border-primary'
                        : 'bg-transparent border-border hover:border-primary'
                    }`}
                  >
                    {c}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-mono mb-3">기수 선택</label>
              <div className="grid grid-cols-3 gap-2">
                {COHORTS.map((c) => {
                  // 16기 등 모집중 기수: UI엔 노출하되 선택 불가 — 클릭 시 안내만.
                  if (RECRUITING_COHORTS.includes(c)) {
                    return (
                      <button
                        key={c}
                        type="button"
                        onClick={() =>
                          setNotice({ title: `${c} 모집 예정`, message: '현재 모집중입니다. 추후에 업데이트 하겠습니다.' })
                        }
                        className="px-4 py-2 text-sm font-mono border border-dashed border-border text-muted-foreground cursor-not-allowed"
                      >
                        {c} <span className="text-[10px]">(모집중)</span>
                      </button>
                    );
                  }
                  return (
                    <button key={c} type="button" onClick={() => setCohort(c)} className={chip(cohort === c)}>
                      {c}
                    </button>
                  );
                })}
              </div>
            </div>

            <div>
              <label className="block text-sm font-mono mb-3">캠퍼스 선택</label>
              <div className="grid grid-cols-3 gap-2">
                {CAMPUSES.map((c) => (
                  <button key={c} type="button" onClick={() => setCampus(c)} className={chip(campus === c)}>
                    {c}
                  </button>
                ))}
              </div>
            </div>

            {adjective && character && (
              <div className="pt-4 border-t border-border">
                <p className="text-sm font-mono text-muted-foreground mb-2">미리보기</p>
                <p className="text-lg font-mono">{adjective} {character}</p>
              </div>
            )}
          </div>

          {error && <p className="text-sm text-destructive font-mono mt-4 text-center">{error}</p>}

          <button
            type="submit"
            disabled={!isComplete || loading}
            className="w-full h-12 bg-primary text-primary-foreground font-mono text-sm mt-6 hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? '처리 중...' : '시작하기'}
          </button>
        </form>

        <AlertDialog
          open={!!notice}
          title={notice?.title}
          message={notice?.message}
          onClose={() => setNotice(null)}
        />
      </div>
    </div>
  );
}
