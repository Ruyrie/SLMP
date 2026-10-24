/** 服务端 SSE（AI 问答）读取：event/data 行协议，支持 AbortSignal 与心跳。 */
export interface SseHandlers {
  onEvent?: (event: string, data: unknown) => void
  onError?: (error: Error) => void
  onOpen?: (response: Response) => void
}

export async function readSse(
  url: string,
  init: RequestInit,
  handlers: SseHandlers,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(url, {
    credentials: 'include',
    ...init,
    headers: { Accept: 'text/event-stream', ...(init.headers || {}) },
    signal: signal || init.signal
  })
  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => '')
    throw new Error(text || `SSE 请求失败（${response.status}）`)
  }
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    const text = await response.text()
    try {
      const json = JSON.parse(text) as { message?: string }
      throw new Error(json.message || 'AI 服务未返回事件流')
    } catch (e) {
      if (e instanceof Error) throw e
      throw new Error('AI 服务未返回事件流')
    }
  }
  handlers.onOpen?.(response)
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split(/\r?\n\r?\n/)
      buffer = parts.pop() || ''
      for (const chunk of parts) {
        let event = 'message'
        const dataLines: string[] = []
        for (const line of chunk.split(/\r?\n/)) {
          if (!line || line.startsWith(':')) continue
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        if (!dataLines.length) continue
        const raw = dataLines.join('\n')
        let data: unknown = raw
        try {
          data = JSON.parse(raw)
        } catch {
          /* 保留纯文本 */
        }
        handlers.onEvent?.(event, data)
      }
    }
  } catch (error) {
    if ((error as Error).name === 'AbortError') return
    handlers.onError?.(error as Error)
    throw error
  } finally {
    reader.releaseLock()
  }
}
