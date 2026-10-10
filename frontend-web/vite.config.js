import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发态通过代理转发到后端，避免跨域；生产态由 Nginx 或后端静态目录托管
//
// base：部署到子路径时用，由 RXT_BASE 覆盖，默认 '/'（即根路径）。
// Nginx 分流下本应用挂在 /app/ 下，构建时传 RXT_BASE=/app/。
// 用环境变量而不是写死，是为了不改变开发态体验 —— 本地 npm run dev 仍然是
// http://localhost:5173/ ，不变成 http://localhost:5173/app/ 。
// 路由侧配套读 import.meta.env.BASE_URL，两边同源同一个值。
//
// 代理目标：可用 VITE_API_TARGET 覆盖（与 frontend-landing 同一个约定）。
// 必须留这个口子 —— scripts/demo-start.sh 支持 BACKEND_PORT 换端口，
// 写死 8080 的话换了端口前端请求还打 8080：那里要么没有后端（连不上），
// 要么正躺着别的进程（拿到看不懂的响应，且日志里毫无线索）。
const API_TARGET = process.env.VITE_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  base: process.env.RXT_BASE ?? '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
})
