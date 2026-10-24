/** Relative media paths belong to the site root, including when shown under /admin/. */
export function resolveAvatarUrl(value?: string | null, origin?: string): string {
  const source = value?.trim()
  if (!source) return ''
  if (/^(data:image\/|blob:)/i.test(source)) return source
  const base = origin || (typeof window !== 'undefined' ? window.location.origin : '')
  try {
    const url = new URL(source, base ? `${base.replace(/\/$/, '')}/` : undefined)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

export function avatarInitial(name?: string | null): string {
  return Array.from(name?.trim() || '')[0] || '用'
}
