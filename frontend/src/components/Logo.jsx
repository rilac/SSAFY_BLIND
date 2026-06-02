// SSAFY_SOOP 로고 — '대나무숲(SOOP)' 모티프. 세로 줄기 + 마디(node) + 댓잎. 브루탈리즘 2px 라인.
export default function Logo({ size = 22, className = '' }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {/* 줄기 + 마디: 좌(짧음) · 중앙(높음) · 우(중간) */}
      <path d="M6 9 V21" />
      <path d="M4.3 13 H7.7" />
      <path d="M4.3 17.5 H7.7" />
      <path d="M12 5 V21" />
      <path d="M10.3 9 H13.7" />
      <path d="M10.3 13.5 H13.7" />
      <path d="M10.3 18 H13.7" />
      <path d="M18 7 V21" />
      <path d="M16.3 11 H19.7" />
      <path d="M16.3 15.5 H19.7" />
      {/* 댓잎 */}
      <path d="M12 5.5 C10.4 4.4 8.7 4.1 7.6 4.5 C8.7 5.2 10.5 6 12 5.5 Z" />
      <path d="M12 5.5 C13.6 4.4 15.3 4.1 16.4 4.5 C15.3 5.2 13.5 6 12 5.5 Z" />
      <path d="M18 8 C19.2 7.2 20.5 7 21.4 7.3 C20.5 8 19.2 8.6 18 8 Z" />
    </svg>
  );
}
