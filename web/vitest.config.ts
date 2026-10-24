import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

function project(name: string, app: 'admin' | 'user') {
  return {
    plugins: [vue()],
    resolve: {
      alias: [
        { find: /^@\//, replacement: fileURLToPath(new URL(`./apps/${app}/src/`, import.meta.url)) },
        { find: '@zmt/shared', replacement: fileURLToPath(new URL('./packages/shared/src/index.ts', import.meta.url)) }
      ]
    },
    test: { name, environment: 'jsdom' as const, include: [`tests/${name}*.test.ts`] }
  }
}

export default defineConfig({
  test: {
    projects: [project('admin', 'admin'), project('avatar', 'user'), project('user', 'user')]
  }
})
