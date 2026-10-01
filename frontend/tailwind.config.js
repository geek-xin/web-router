/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', '"PingFang SC"', '"Noto Sans SC"', '"Microsoft YaHei"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      colors: {
        // 全部指向 CSS 变量，这样 Tailwind 工具类会随 data-theme 切换（见 styles.css）
        console: {
          bg: 'var(--console-bg)',
          'bg-alt': 'var(--console-bg-alt)',
          panel: 'var(--console-panel)',
          'panel-hover': 'var(--console-panel-hover)',
          raised: 'var(--console-raised)',
          line: 'var(--console-line)',
          'line-strong': 'var(--console-line-strong)',
          ink: 'var(--console-ink)',
          'ink-muted': 'var(--console-ink-muted)',
          'ink-subtle': 'var(--console-ink-subtle)',
          primary: 'var(--console-primary)',
          'primary-light': 'var(--console-primary-light)',
          'primary-dim': 'var(--console-primary-dim)',
          success: 'var(--console-success)',
          warning: 'var(--console-warning)',
          error: 'var(--console-error)',
          cyan: 'var(--console-cyan)',
          slate: 'var(--console-slate)',
        },
      },
      keyframes: {
        'status-pulse': {
          '0%, 100%': { opacity: '1', transform: 'scale(1)' },
          '50%': { opacity: '0.45', transform: 'scale(0.82)' },
        },
        'link-flow': {
          '0%': { transform: 'translateX(0)', opacity: '0' },
          '20%': { opacity: '1' },
          '80%': { opacity: '1' },
          '100%': { transform: 'translateX(var(--flow-distance, 64px))', opacity: '0' },
        },
        'drawer-in': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-160% 0' },
          '100%': { backgroundPosition: '260% 0' },
        },
      },
      animation: {
        'status-pulse': 'status-pulse 1.6s ease-in-out infinite',
        'link-flow': 'link-flow 1.8s linear infinite',
        'drawer-in': 'drawer-in 220ms cubic-bezier(0.22, 0.61, 0.36, 1)',
        shimmer: 'shimmer 1.4s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
