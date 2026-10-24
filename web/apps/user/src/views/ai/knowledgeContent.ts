/**
 * 知识正文 content_html 白名单清洗（WU-K02）。
 *
 * 服务端已把 content_html 收缩到 strong / ul / ol / li / br，但前端仍按完整白名单
 * 再清洗一次：禁止脚本、事件属性、style、data/aria 属性；图片与链接只允许
 * http(s) 或站内 / 路径，外链统一补 rel，图片补 lazy/async 与默认 alt。
 */
import DOMPurify from 'dompurify'

const ALLOWED_TAGS = [
  'p',
  'br',
  'strong',
  'em',
  'u',
  'ul',
  'ol',
  'li',
  'blockquote',
  'h1',
  'h2',
  'h3',
  'h4',
  'img',
  'a'
]

const ALLOWED_ATTR = ['href', 'src', 'alt', 'title']
const EMPTY_BLOCKS = 'p,h1,h2,h3,h4,blockquote,ul,ol,li'
const XHTML_NAMESPACE = 'http://www.w3.org/1999/xhtml'

function isHttpUrl(value: string): boolean {
  if (!/^https?:\/\//i.test(value)) return false
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function isSiteRelative(value: string): boolean {
  return value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')
}

/** 图片地址只允许 http(s) 绝对地址或站内 / 路径。 */
export function safeKnowledgeImageSrc(url?: string | null): string {
  const value = (url || '').trim()
  if (!value) return ''
  return isHttpUrl(value) || isSiteRelative(value) ? value : ''
}

function normalizeImage(image: HTMLImageElement): void {
  const src = safeKnowledgeImageSrc(image.getAttribute('src'))
  if (!src) {
    image.remove()
    return
  }
  image.setAttribute('src', src)
  image.setAttribute('loading', 'lazy')
  image.setAttribute('decoding', 'async')
  if (!(image.getAttribute('alt') || '').trim()) image.setAttribute('alt', '知识配图')
}

function normalizeAnchor(anchor: HTMLAnchorElement): void {
  const href = (anchor.getAttribute('href') || '').trim()
  if (!href) {
    anchor.removeAttribute('href')
    return
  }
  if (isHttpUrl(href)) {
    const external =
      typeof window === 'undefined' || new URL(href).origin !== window.location.origin
    if (external) {
      anchor.setAttribute('target', '_blank')
      anchor.setAttribute('rel', 'noopener noreferrer')
    }
    return
  }
  if (isSiteRelative(href) || href.startsWith('#')) return
  anchor.removeAttribute('href')
}

/** 去掉只剩空白的段落、列表项和空列表，避免正文出现额外间距。 */
function pruneEmptyBlocks(root: ParentNode): void {
  const nodes = Array.from(root.querySelectorAll(EMPTY_BLOCKS))
  for (let index = nodes.length - 1; index >= 0; index -= 1) {
    const node = nodes[index]
    if (node.querySelector('img')) continue
    if ((node.textContent || '').replace(/\u200b/g, '').trim()) continue
    node.remove()
  }
}

export function sanitizeKnowledgeHtml(html?: string | null): string {
  if (!html || !html.trim()) return ''
  const sanitized = DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOWED_NAMESPACES: [XHTML_NAMESPACE],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
    KEEP_CONTENT: true
  })
  if (!sanitized) return ''
  const container = document.createElement('div')
  container.innerHTML = sanitized
  container.querySelectorAll('img').forEach((image) => normalizeImage(image))
  container.querySelectorAll('a').forEach((anchor) => normalizeAnchor(anchor))
  pruneEmptyBlocks(container)
  return container.innerHTML.trim()
}
