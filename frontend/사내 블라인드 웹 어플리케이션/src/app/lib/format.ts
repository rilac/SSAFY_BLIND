// 시간/숫자 포맷 헬퍼 (App.tsx에서 추출)

// ISO 문자열을 "방금 전 / N분 전 / N시간 전 / N일 전 / 날짜"로 변환
export function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;
  if (hours < 24) return `${hours}시간 전`;
  if (days < 7) return `${days}일 전`;

  return date.toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
}

// 1000 이상은 "1.2k" 형태로 축약
export function formatNumber(num: number): string {
  if (num >= 1000) return `${(num / 1000).toFixed(1)}k`;
  return num.toString();
}
