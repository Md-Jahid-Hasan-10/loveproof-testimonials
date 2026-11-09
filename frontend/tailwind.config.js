/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        /*
          The brand ramp. Indigo rather than a pure blue: a blue primary on
          white reads as "link", while indigo reads as "brand", so the
          primary buttons and the navigation do not compete with the links
          inside the copy.
        */
        brand: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
          950: '#1e1b4b',
        },
        /*
          The page background is a hair warmer than pure slate, so the white
          cards sitting on it separate by temperature as well as by shadow.
        */
        canvas: '#f7f8fc',
      },
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
      /*
        Three shadows and no more. Every elevation in the application is one
        of these three, so "raised" always means the same amount of raised.
      */
      boxShadow: {
        card: '0 1px 2px 0 rgb(15 23 42 / 0.04), 0 1px 3px 0 rgb(15 23 42 / 0.06)',
        raised:
          '0 2px 4px -1px rgb(15 23 42 / 0.06), 0 4px 12px -2px rgb(15 23 42 / 0.10)',
        lifted:
          '0 8px 24px -6px rgb(15 23 42 / 0.12), 0 16px 48px -12px rgb(15 23 42 / 0.16)',
        /* The brand-tinted glow used on primary actions. */
        brand: '0 1px 2px 0 rgb(79 70 229 / 0.20), 0 4px 12px -2px rgb(79 70 229 / 0.28)',
      },
      borderRadius: {
        card: '0.875rem',
        control: '0.625rem',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.35s cubic-bezier(0.16, 1, 0.3, 1) both',
        'fade-in': 'fade-in 0.25s ease-out both',
      },
    },
  },
  plugins: [],
}
