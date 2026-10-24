import { HttpClient, type HttpOptions } from '@zmt/shared'

let client: HttpClient | null = null

export function setupHttp(options: HttpOptions): HttpClient {
  client = new HttpClient(options)
  return client
}

export function http(): HttpClient {
  if (!client) throw new Error('HTTP 客户端尚未初始化')
  return client
}
