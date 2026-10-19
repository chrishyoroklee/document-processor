import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // In development the page is served from :5173 and the API runs on :8080.
    // The proxy forwards /api/* to Spring Boot, so the browser sees a single
    // origin and we don't need CORS. In Docker, Spring serves the built page
    // itself, so it's a single origin there too.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
