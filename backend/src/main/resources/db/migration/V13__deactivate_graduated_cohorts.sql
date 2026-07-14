-- R1: 14기 졸업 → 기존 활동(ACTIVE) 14기 회원을 일괄 휴면(DORMANT) 처리해 비활성화한다.
-- 재로그인으로 부활하지 못하도록 AuthService.login이 활동 기수 화이트리스트(OnboardingOptions.COHORTS)로
-- 재활성화를 게이트한다(14기는 화이트리스트에서 제거됨 → 로그인 차단).
UPDATE users SET status = 'DORMANT' WHERE cohort = '14기' AND status = 'ACTIVE';
