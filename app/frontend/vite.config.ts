import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// 開発では、Vite の開発サーバー (5173) が /api をバックエンド (8080) へ中継する
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
