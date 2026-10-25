// @vitest-environment node
// 管理端部署在 /admin/ 子路径：生产构建和 vite preview 都要用 /admin/，开发服务器仍在根路径。
import { describe, expect, it } from 'vitest'
import type { ConfigEnv, UserConfig } from 'vite'
import adminConfig from '../apps/admin/vite.config'

const config = adminConfig as unknown as (env: ConfigEnv) => UserConfig

describe('admin deploy base', () => {
  it('uses /admin/ for build and preview, / for the dev server', () => {
    expect(config({ command: 'build', mode: 'production' }).base).toBe('/admin/')
    expect(config({ command: 'serve', mode: 'production', isPreview: true }).base).toBe('/admin/')
    expect(config({ command: 'serve', mode: 'development' }).base).toBe('/')
  })

  it('proxies both admin and user APIs in dev and preview', () => {
    const proxy = config({ command: 'serve', mode: 'development' }).server?.proxy || {}
    expect(Object.keys(proxy)).toEqual(expect.arrayContaining(['/admin/v1', '/api/v1']))
  })
})
