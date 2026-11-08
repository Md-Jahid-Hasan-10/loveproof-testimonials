import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Same-origin proxy: the SPA and the API share an origin in development,
    // so no CORS configuration is needed anywhere.
    proxy: {
      '/api': {
        // Overridable so the dev server can target a backend running on a
        // different port when 8080 is already taken by another process.
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
  },
})
