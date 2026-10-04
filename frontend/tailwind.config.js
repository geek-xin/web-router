/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', '"PingFang SC"', '"Noto Sans SC"', '"Microsoft YaHei"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['Newsreader', '"Iowan Old Style"', '"Palatino Linotype"', 'Palatino', '"Source Han Serif SC"', '"Noto Serif SC"', 'Georgia', 'serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      colors: {
        // 全部指向 CSS 变量，这样 Tailwind 工具类会随 data-theme 切换（见 styles.css）
        surface: {
          canvas: 'var(--surface-canvas)',
          'canvas-deep': 'var(--surface-canvas-deep)',
          shell: 'var(--surface-shell)',
          sunken: 'var(--surface-sunken)',
          panel: 'var(--surface-panel)',
          'panel-hover': 'var(--surface-panel-hover)',
          raised: 'var(--surface-raised)',
          line: 'var(--surface-line)',
          'line-strong': 'var(--surface-line-strong)',
          'line-faint': 'var(--surface-line-faint)',
          ink: 'var(--surface-ink)',
          'ink-muted': 'var(--surface-ink-muted)',
          'ink-subtle': 'var(--surface-ink-subtle)',
          accent: 'var(--surface-accent)',
          'accent-strong': 'var(--surface-accent-strong)',
          'accent-soft': 'var(--surface-accent-soft)',
          navy: 'var(--surface-navy)',
          info: 'var(--surface-info)',
          success: 'var(--surface-success)',
          warning: 'var(--surface-warning)',
          error: 'var(--surface-error)',
        },
      },
      borderRadius: {
        control: 'var(--radius-control)',
        card: 'var(--radius-card)',
        band: 'var(--radius-band)',
        panel: 'var(--radius-panel)',
        node: 'var(--radius-node)',
      },
      keyframes: {
        'dot-pulse': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.35' },
        },
        'link-flow': {
          '0%': { transform: 'translateY(0)', opacity: '0' },
          '20%': { opacity: '1' },
          '80%': { opacity: '1' },
          '100%': { transform: 'translateY(16px)', opacity: '0' },
        },
        'drawer-in': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
        'scrim-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '160% 0' },
          '100%': { backgroundPosition: '-60% 0' },
        },
      },
      animation: {
        'dot-pulse': 'dot-pulse 1.6s ease-in-out infinite',
        'link-flow': 'link-flow 1.8s linear infinite',
        'drawer-in': 'drawer-in 220ms cubic-bezier(0.22, 0.61, 0.36, 1)',
        'scrim-in': 'scrim-in 160ms ease-out',
        shimmer: 'shimmer 1.4s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
