import { useState } from "react";
import type { UserData } from "../types";
import { adjectives, characters, COHORTS, CAMPUSES } from "../data/mockData";

interface OnboardingPageProps {
  onComplete: (userData: UserData) => void;
}

// 익명 닉네임 + 기수/캠퍼스 설정
export default function OnboardingPage({ onComplete }: OnboardingPageProps) {
  const [selectedAdjective, setSelectedAdjective] = useState("");
  const [selectedCharacter, setSelectedCharacter] = useState("");
  const [selectedCohort, setSelectedCohort] = useState("");
  const [selectedCampus, setSelectedCampus] = useState("");

  const isComplete =
    selectedAdjective && selectedCharacter && selectedCohort && selectedCampus;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isComplete) return;
    onComplete({
      nickname: selectedAdjective + selectedCharacter,
      cohort: selectedCohort,
      campus: selectedCampus,
    });
  };

  const selectClass = (active: boolean) =>
    `px-4 py-2 text-sm font-mono border transition-colors ${
      active
        ? "bg-primary text-primary-foreground border-primary"
        : "bg-transparent border-border hover:border-primary"
    }`;

  return (
    <div className="h-screen w-full bg-background text-foreground flex items-center justify-center p-6">
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
                {adjectives.map((adj) => (
                  <button
                    key={adj}
                    type="button"
                    onClick={() => setSelectedAdjective(adj)}
                    className={selectClass(selectedAdjective === adj)}
                  >
                    {adj}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-mono mb-3">SSAFY 캐릭터 선택</label>
              <div className="grid grid-cols-4 gap-2">
                {characters.map((char) => (
                  <button
                    key={char}
                    type="button"
                    onClick={() => setSelectedCharacter(char)}
                    className={`px-4 py-3 text-sm font-mono border transition-colors ${
                      selectedCharacter === char
                        ? "bg-primary text-primary-foreground border-primary"
                        : "bg-transparent border-border hover:border-primary"
                    }`}
                  >
                    {char}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-mono mb-3">기수 선택</label>
              <div className="grid grid-cols-6 gap-2">
                {COHORTS.map((cohort) => (
                  <button
                    key={cohort}
                    type="button"
                    onClick={() => setSelectedCohort(cohort)}
                    className={selectClass(selectedCohort === cohort)}
                  >
                    {cohort}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-mono mb-3">캠퍼스 선택</label>
              <div className="grid grid-cols-3 gap-2">
                {CAMPUSES.map((campus) => (
                  <button
                    key={campus}
                    type="button"
                    onClick={() => setSelectedCampus(campus)}
                    className={selectClass(selectedCampus === campus)}
                  >
                    {campus}
                  </button>
                ))}
              </div>
            </div>

            {selectedAdjective && selectedCharacter && (
              <div className="pt-4 border-t border-border">
                <p className="text-sm font-mono text-muted-foreground mb-2">미리보기</p>
                <p className="text-lg font-mono">
                  {selectedAdjective}
                  {selectedCharacter}
                </p>
              </div>
            )}
          </div>

          <button
            type="submit"
            disabled={!isComplete}
            className="w-full h-12 bg-primary text-primary-foreground font-mono text-sm mt-6 hover:opacity-90 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
          >
            시작하기
          </button>
        </form>
      </div>
    </div>
  );
}
