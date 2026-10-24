import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const src = fileURLToPath(new URL('./src', import.meta.url))
const shared = fileURLToPath(new URL('../../packages/shared/src', import.meta.url))
const backend = process.env.ZMT_BACKEND || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      { find: /^@zmt\/shared$/, replacement: shared + '/index.ts' },
      { find: /^@zmt\/shared\//, replacement: shared + '/' },
      { find: /^@\//, replacement: src + '/' },
      { find: /^vue$/, replacement: 'vue/dist/vue.esm-bundler.js' }
    ]
  },
  define: {
    __VUE_OPTIONS_API__: 'true',
    __VUE_PROD_DEVTOOLS__: 'false',
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'false'
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api/v1': { target: backend, changeOrigin: true },
      '/admin/v1': { target: backend, changeOrigin: true }
    }
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 900
  }
})
