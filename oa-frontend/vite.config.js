import { defineConfig } from 'vite'

// 前端页面（public/ 下的原型页）以相对路径 /api 调用后端，
// 因此 dev server 必须把 /api 代理到后端（默认 8080），否则请求会打到静态服务自身
// （python -m http.server 之类的纯静态服务不支持 POST，会返回 501）。
export default defineConfig({
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.OA_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
