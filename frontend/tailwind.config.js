/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        playful: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      colors: {
        clay: {
          ink: '#161616',
          muted: '#6F6A64',
          subtle: '#9B948C',
          base: '#F8F6F3',
          cream: '#FFF7D6',
          paper: '#FFFFFF',
          glass: '#FFFFFF',
          border: '#111111',
          primary: '#F45113',
          'primary-dark': '#C63A08',
          secondary: '#9EEBC5',
          'secondary-dark': '#63C995',
          cta: '#F45113',
          'cta-dark': '#FF6A2A',
          accent: '#BEE7F8',
          purple: '#F8B8C8',
          gold: '#FFF176',
          success: '#18A96B',
          error: '#E23B2E',
          mint: '#9EEBC5',
          yellow: '#FFF176',
          coral: '#F45113',
          indigo: '#BEE7F8',
          lavender: '#F8B8C8',
          orange: '#F45113',
          pink: '#F8B8C8',
          cyan: '#BEE7F8',
        },
      },
      boxShadow: {
        clay: '8px 10px 0 #111111',
        'clay-sm': '4px 5px 0 #111111',
        'clay-hover': '10px 12px 0 #111111',
      },
      borderRadius: {
        clay: '1.5rem',
        blob: '2rem',
      },
    },
  },
  plugins: [],
};
