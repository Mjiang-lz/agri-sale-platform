import { defineConfig } from 'vite'

import { tanstackRouter } from '@tanstack/router-plugin/vite'

import viteReact from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// 后端地址：开发态代理用，可用 VITE_API_TARGET 覆盖（例如 teammates 指向别人的机器）
const API_TARGET = process.env.VITE_API_TARGET ?? 'http://localhost:8080'

const config = defineConfig({
  resolve: { tsconfigPaths: true },
  plugins: [
    tailwindcss(),
    tanstackRouter({ target: 'react', autoCodeSplitting: true }),
    viteReact(),
  ],
  server: {
    proxy: {
      // 平台 REST 接口（白名单里的公开只读接口游客可访问）
      '/api': { target: API_TARGET, changeOrigin: true },
      // 本地兜底模式下后端用 GET /files/** 提供上传产物（商品图 / 知识配图）
      '/files': { target: API_TARGET, changeOrigin: true },
    },
  },
})

export default config
