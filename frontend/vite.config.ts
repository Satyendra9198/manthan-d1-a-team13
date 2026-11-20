/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  // Dev server: /api goes to the Spring Boot backend, so the browser sees one origin. The Origin
  // header is dropped because the proxied call is same-origin; otherwise the backend's CORS check
  // would refuse any dev port it doesn't list.
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        configure: (proxy) => proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Unit tests always run on the in-browser mocks, even when .env.local points the app at the backend.
    env: { VITE_USE_MOCKS: 'true' },
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
})
