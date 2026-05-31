/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        border: 'var(--border)',
        input: 'var(--input)',
        'input-background': 'var(--input-background)',
        ring: 'var(--ring)',
        background: 'var(--background)',
        foreground: 'var(--foreground)',
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: 'var(--primary-foreground)',
        },
        secondary: {
          DEFAULT: 'var(--secondary)',
          foreground: 'var(--secondary-foreground)',
        },
        destructive: {
          DEFAULT: 'var(--destructive)',
          foreground: 'var(--destructive-foreground)',
        },
        muted: {
          DEFAULT: 'var(--muted)',
          foreground: 'var(--muted-foreground)',
        },
        accent: {
          DEFAULT: 'var(--accent)',
          foreground: 'var(--accent-foreground)',
        },
        card: {
          DEFAULT: 'var(--card)',
          foreground: 'var(--card-foreground)',
        },
        popover: {
          DEFAULT: 'var(--popover)',
          foreground: 'var(--popover-foreground)',
        },
      },
      fontFamily: {
        // §4: 앱 전반이 한글 텍스트에도 font-mono를 쓰므로, 라틴은 JetBrains Mono(브루탈리즘)로
        // 두되 한글 글리프는 Pretendard로 폴백시켜 Windows 가독성("딱딱함")을 개선한다.
        mono: ['JetBrains Mono', 'Pretendard Variable', 'Pretendard', 'monospace'],
        sans: ['Pretendard Variable', 'Pretendard', 'Work Sans', 'Apple SD Gothic Neo', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
