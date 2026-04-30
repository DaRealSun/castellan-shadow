/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,ts,tsx,md,mdx}'],
  theme: {
    extend: {
      colors: {
        ink:    '#0b1020',
        paper:  '#f7f7f5',
        accent: '#0f766e',
        muted:  '#64748b',
        rule:   '#e5e7eb',
        plus:   '#16a34a',
        minus:  '#dc2626',
      },
      fontFamily: {
        sans: ['"Inter"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
};
