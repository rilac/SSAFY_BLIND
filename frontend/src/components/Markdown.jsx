// [FEATURE:markdown-rendering] 게시글 본문 마크다운 렌더링 (코드블록 syntax highlight 포함)
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeHighlight from 'rehype-highlight';

// 보안/정책:
//  - react-markdown은 기본적으로 원시 HTML을 렌더링하지 않는다(rehype-raw 미사용) → XSS 차단.
//  - 단일 줄바꿈 보존: remark-breaks 로 \n → <br> (기존 평문 whitespace-pre-wrap 동작 유지 + 캐주얼 작성 친화).
//  - GFM: 표/취소선/체크리스트/자동링크.
//  - 이미지(원격 URL 임베드)는 익명 환경에서 조회자 IP 유출/어뷰즈 우려가 있어 비활성 → 링크로 대체.
const components = {
  // 외부 링크는 새 탭 + noopener/nofollow
  a: ({ node, ...props }) => (
    <a
      {...props}
      target="_blank"
      rel="noopener noreferrer nofollow"
      className="text-primary underline underline-offset-2 hover:opacity-80"
    />
  ),
  // 이미지 임베드 비활성 — 안전한 링크로 노출(클릭 시에만 외부 요청)
  img: ({ node, src, alt }) => (
    <a
      href={src}
      target="_blank"
      rel="noopener noreferrer nofollow"
      className="text-primary underline underline-offset-2 hover:opacity-80"
    >
      🖼 {alt || '이미지 링크'}
    </a>
  ),
};

// 게시글/답변 본문을 마크다운으로 렌더링. className은 바깥 래퍼에 적용된다.
export default function Markdown({ children, className = '' }) {
  return (
    <div className={`markdown-body ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[[rehypeHighlight, { detect: true, ignoreMissing: true }]]}
        components={components}
      >
        {children || ''}
      </ReactMarkdown>
    </div>
  );
}
// [/FEATURE:markdown-rendering]
