import { describe, expect, it } from 'vitest'
import { AxiosError } from 'axios'
import { setupHttp } from '../apps/user/src/api/client'
import { uploadFile } from '../apps/user/src/api/inspect'

describe('inspection upload transport', () => {
  it('sends multipart data, progress and cancellation with a bounded timeout', async () => {
    const client = setupHttp({ baseURL: '/api/v1', client: 'WEB' })
    const controller = new AbortController()
    const file = new File(['image'], 'photo.png', { type: 'image/png' })
    const progress: number[] = []
    client.instance.defaults.adapter = async config => {
      expect(config.url).toBe('/files')
      expect(config.timeout).toBe(120_000)
      expect(config.signal).toBe(controller.signal)
      expect(config.data.get('file')).toBe(file)
      expect(config.data.get('scene')).toBe('INSPECTION')
      config.onUploadProgress?.({ loaded: 47, total: 100, bytes: 47, lengthComputable: true })
      return { data: { code: 0, data: { file_id: 80 } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    expect(await uploadFile(file, 'INSPECTION', percent => progress.push(percent), controller.signal)).toEqual({ file_id: 80 })
    expect(progress).toEqual([47])
  })

  it('turns upload timeout into a recoverable request error', async () => {
    const client = setupHttp({ baseURL: '/api/v1', client: 'WEB' })
    client.instance.defaults.adapter = async config => { throw new AxiosError('timeout', 'ECONNABORTED', config) }
    await expect(uploadFile(new File(['image'], 'photo.png'), 'INSPECTION')).rejects.toThrow('请求超时')
  })
})
