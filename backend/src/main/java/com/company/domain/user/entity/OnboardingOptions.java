package com.company.domain.user.entity;

import java.util.Set;

// 온보딩 화이트리스트 — 프론트 OnboardingPage.jsx의 선택지와 1:1 미러(서버단 강제 검증용).
// 클라이언트만 선택지를 강제하면 직접 API 호출로 우회되므로, cohort/campus/nickname을 서버에서 검증한다.
// ⚠️ 프론트 OnboardingPage.jsx의 ADJECTIVES/CHARACTERS/COHORTS/CAMPUSES와 수동 동기 유지.
public final class OnboardingOptions {

    private OnboardingOptions() {}

    public static final Set<String> ADJECTIVES = Set.of(
            "열정적인", "똑똑한", "창의적인", "성실한", "긍정적인", "활발한",
            "차분한", "꼼꼼한", "적극적인", "친절한", "유쾌한", "신중한",
            "대담한", "세심한", "낙관적인", "침착한");

    public static final Set<String> CHARACTERS = Set.of("스타티", "핏", "와이즈", "알지");

    // 현재 활동(재학) 기수만 허용 — 학기마다 갱신 필요. 이 집합은 온보딩 검증 + 로그인 재활성화 게이트의
    // 단일 진실원(single source of truth)이다. 여기서 빠진 기수는 졸업으로 간주되어 로그인이 차단된다.
    // 2026-07: 14기 졸업 → 제외, 16기 입학 → 추가.
    public static final Set<String> COHORTS = Set.of("15기", "16기");

    public static final Set<String> CAMPUSES = Set.of("서울", "대전", "광주", "부울경", "구미");

    // 닉네임은 "{형용사} {캐릭터}" 형식만 허용(앞뒤 공백 무시, 정확히 두 토큰).
    public static boolean isValidNickname(String nickname) {
        if (nickname == null) return false;
        String[] parts = nickname.trim().split(" ");
        return parts.length == 2 && ADJECTIVES.contains(parts[0]) && CHARACTERS.contains(parts[1]);
    }
}
