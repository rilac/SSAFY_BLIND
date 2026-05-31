// SSAFY_BLIND 로고 — '블라인드(창문 블라인드 슬랫)' 모티프. 브루탈리즘 샤프 스타일.
export default function Logo({ size = 22, className = '' }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <rect x="2.5" y="2.5" width="19" height="19" stroke="currentColor" strokeWidth="2" />
      <line x1="6" y1="9" x2="18" y2="9" stroke="currentColor" strokeWidth="2" />
      <line x1="6" y1="13" x2="18" y2="13" stroke="currentColor" strokeWidth="2" />
      <line x1="6" y1="17" x2="13" y2="17" stroke="currentColor" strokeWidth="2" />
    </svg>
  );
}
