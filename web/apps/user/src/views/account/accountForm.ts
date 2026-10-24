import { dayjs, formatDate } from '@zmt/shared'

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 20
export const NAME_MAX = 20
export const DESCRIPTION_MAX = 200
export const RECOVERY_CODE_LENGTH = 16

export const MSG_PHONE_EMPTY = '请输入手机号'
export const MSG_PHONE_FORMAT = '手机号格式不正确，请输入 11 位手机号'
export const MSG_CODE_FORMAT = '请输入 6 位验证码'
export const MSG_PASSWORD_EMPTY = '请输入密码'
export const MSG_PASSWORD_LENGTH = '密码需要 8–20 位'
export const MSG_PASSWORD_MIX = '密码需同时包含字母和数字'
export const MSG_PASSWORD_MISMATCH = '两次输入的密码不一致'
export const MSG_PASSWORD_SAME_CURRENT = '新密码不能和当前密码相同'
export const MSG_AGREE_TERMS = '请先阅读并同意《用户协议》和《隐私政策》'
export const MSG_RECOVERY_CODE = '恢复码应为 16 位字母和数字'
export const MSG_SAME_PHONE = '新手机号不能和原手机号相同'
export const MSG_DESCRIPTION = '请写明情况说明'
export const MSG_NAME_EMPTY = '请输入姓名'
export const MSG_NAME_TOO_LONG = '姓名不能超过 20 个字'
export const MSG_NETWORK = '网络连接失败，已保留填写的内容'
export const MSG_TOO_FREQUENT = '获取验证码太频繁，请稍后再试'
export const MSG_SMS_SEND_FAILED = '验证码发送失败，请稍后重试'

/** 去掉空格（含全角）、短横线与制表符，只保留数字 */
export function digits(value: unknown): string {
  return String(value ?? '')
    .replace(/[^\d]/g, '')
    .slice(0, 11)
}

/** 手机号按 3-4-4 显示 */
export function formatPhone(value: unknown): string {
  const text = digits(value)
  if (text.length <= 3) return text
  if (text.length <= 7) return `${text.slice(0, 3)} ${text.slice(3)}`
  return `${text.slice(0, 3)} ${text.slice(3, 7)} ${text.slice(7)}`
}

/** 恢复码：去空格短横线后转大写，每 4 位一个空格 */
export function formatRecoveryCode(value: unknown): string {
  const text = normalizeRecoveryCode(value)
  return text.replace(/(.{4})/g, '$1 ').trim()
}

/** 恢复码归一化：只留 A-Z0-9，最长 16 位 */
export function normalizeRecoveryCode(value: unknown): string {
  return String(value ?? '')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toUpperCase()
    .slice(0, RECOVERY_CODE_LENGTH)
}

export function phoneProblem(value: unknown): string {
  const phone = digits(value)
  if (!phone) return MSG_PHONE_EMPTY
  return /^1\d{10}$/.test(phone) ? '' : MSG_PHONE_FORMAT
}

export function smsCodeProblem(value: unknown): string {
  return /^\d{6}$/.test(String(value ?? '').trim()) ? '' : MSG_CODE_FORMAT
}

/** 新密码规则：8–20 位，同时包含字母和数字 */
export function passwordProblem(value: unknown): string {
  const text = String(value ?? '')
  if (!text) return MSG_PASSWORD_EMPTY
  if (text.length < PASSWORD_MIN || text.length > PASSWORD_MAX) return MSG_PASSWORD_LENGTH
  if (!/[A-Za-z]/.test(text) || !/\d/.test(text)) return MSG_PASSWORD_MIX
  return ''
}

export function recoveryCodeProblem(value: unknown): string {
  return /^[A-Z0-9]{16}$/.test(normalizeRecoveryCode(value)) ? '' : MSG_RECOVERY_CODE
}

/** 按 Unicode 码点计数（中文与表情均算 1 个字） */
export function textLength(value: unknown): number {
  return Array.from(String(value ?? '')).length
}

export function nameProblem(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text) return MSG_NAME_EMPTY
  if (textLength(text) > NAME_MAX) return MSG_NAME_TOO_LONG
  return ''
}

export function formatMonthDay(value: string | null | undefined): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('M月D日') : String(value)
}

export function formatMonthDayWeek(value: string | null | undefined): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('M月D日 dddd') : String(value)
}

export function formatMonthDayTime(value: string | null | undefined, suffix = ''): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? `${d.format('M月D日 HH:mm')}${suffix}` : `${String(value)}${suffix}`
}

export function formatFullDate(value: string | null | undefined): string {
  if (!value) return '—'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY年M月D日') : String(value)
}

/** 生成设备标识（注册必填，最长 64 位） */
export function deviceId(): string {
  const key = 'zmt.device.id'
  let value = typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null
  if (!value) {
    value =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value)
  }
  return value
}

/** 复制文本；剪贴板不可用时退回 textarea + execCommand */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    /* 继续使用兜底方案 */
  }
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

/** 下载“智牧通恢复码.png”（竖版，白底） */
export function downloadRecoveryCodeImage(code: string, phoneMasked?: string, generatedAt?: string): boolean {
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 720
    canvas.height = 1000
    const ctx = canvas.getContext('2d')
    if (!ctx) return false
    const font = '"Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif'
    ctx.fillStyle = '#FFFFFF'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.textAlign = 'center'
    ctx.fillStyle = '#0B57D0'
    ctx.font = `600 34px ${font}`
    ctx.fillText('智牧通 · 账号恢复码', 360, 120)
    ctx.fillStyle = '#4E5969'
    ctx.font = `400 22px ${font}`
    ctx.fillText(`账号 ${phoneMasked || ''}`, 360, 178)
    ctx.fillText(`生成于 ${formatDate(generatedAt)}`, 360, 218)

    ctx.fillStyle = '#F7F8FA'
    roundRect(ctx, 80, 290, 560, 300, 8)
    ctx.fill()
    ctx.strokeStyle = '#E5E6EB'
    ctx.lineWidth = 2
    roundRect(ctx, 80, 290, 560, 300, 8)
    ctx.stroke()
    ctx.fillStyle = '#86909C'
    ctx.font = `400 20px ${font}`
    ctx.fillText('你的恢复码 · 16 位字母和数字', 360, 350)

    const groups = formatRecoveryCode(code).split(' ')
    ctx.fillStyle = '#1D2129'
    ctx.font = `600 54px Consolas, "SFMono-Regular", Menlo, monospace`
    ctx.fillText(groups.slice(0, 2).join(' '), 360, 445)
    ctx.fillText(groups.slice(2).join(' '), 360, 525)

    ctx.fillStyle = '#4E5969'
    ctx.font = `400 20px ${font}`
    ctx.fillText('手机号不能使用时，用这个恢复码找回账号', 360, 850)
    ctx.fillStyle = '#86909C'
    ctx.font = `400 18px ${font}`
    ctx.fillText('请截图或抄下来，放在安全的地方', 360, 892)

    const link = document.createElement('a')
    link.download = '智牧通恢复码.png'
    link.href = canvas.toDataURL('image/png')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    return true
  } catch {
    return false
  }
}
