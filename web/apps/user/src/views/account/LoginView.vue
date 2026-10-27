<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@zmt/shared'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'
import { useNoticeStore } from '@/stores/notice'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const farm = useFarmStore()
const notice = useNoticeStore()
const ui = useUiStore()

const tab = ref<'pwd' | 'sms'>('pwd')
const phone = ref('')
const password = ref('')
const smsCode = ref('')
const showPassword = ref(false)
const error = ref('')
const phoneError = ref('')
const passwordError = ref('')
const codeError = ref('')
const submitting = ref(false)
const sending = ref(false)
const countdown = ref(0)
const masked = ref('')
let timer = 0

const expiredText = computed(() => {
  const reason = String(route.query.reason || '')
  if (reason === 'PASSWORD_CHANGED') return '账号密码已在其他设备上修改，请重新登录。'
  if (reason === 'ACCOUNT_RECOVERED') return '账号已通过找回流程恢复，请重新登录。'
  if (reason === 'ACCOUNT_DISABLED') return '账号已被平台停用，请联系客服。'
  if (reason === 'ACCOUNT_CANCELLED') return '账号已注销，无法登录。'
  if (reason === 'EXPIRED') return '登录已失效，请重新登录。'
  return ''
})

onMounted(() => {
  if (route.query.phone) phone.value = String(route.query.phone)
})

function startCountdown(seconds: number): void {
  countdown.value = Math.max(1, seconds || 60)
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) window.clearInterval(timer)
  }, 1000)
}

function validatePhone(): boolean {
  phoneError.value = ''
  if (!/^1\d{10}$/.test(phone.value.trim())) {
    phoneError.value = '请输入正确的手机号'
    return false
  }
  return true
}

async function sendCode(): Promise<void> {
  if (!validatePhone() || sending.value || countdown.value > 0) return
  sending.value = true
  try {
    const data = await auth.sendSms(phone.value.trim(), 'LOGIN')
    masked.value = data.phone_masked || ''
    startCountdown(data.cooldown_seconds || 60)
    ui.toast('验证码已发送')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '验证码发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

function targetAfterLogin(): string {
  const redirect = String(route.query.redirect || '')
  if (redirect.startsWith('/') && !redirect.startsWith('//')) return redirect
  return '/'
}

async function afterLogin(): Promise<void> {
  await farm.load(true)
  notice.start()
  const step = auth.nextStep
  if (step === 'SAVE_RECOVERY_CODE' || step === 'RECOVERY_CHANGE_PASSWORD') {
    await router.replace({ name: 'WU-A03', query: { step, redirect: targetAfterLogin() } })
    return
  }
  await router.replace(targetAfterLogin())
}

async function submit(): Promise<void> {
  error.value = ''
  passwordError.value = ''
  codeError.value = ''
  if (!validatePhone()) return
  if (tab.value === 'pwd' && !password.value) {
    passwordError.value = '请输入密码'
    return
  }
  if (tab.value === 'sms' && !/^\d{4,6}$/.test(smsCode.value)) {
    codeError.value = '请输入验证码'
    return
  }
  submitting.value = true
  try {
    if (tab.value === 'pwd') await auth.loginPassword(phone.value.trim(), password.value)
    else await auth.loginSms(phone.value.trim(), smsCode.value)
    await afterLogin()
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 40103) codeError.value = e.message
      else if (e.code === 40001) {
        for (const item of e.fieldErrors) {
          if (item.field === 'phone') phoneError.value = item.message
          else if (item.field === 'password') passwordError.value = item.message
          else if (item.field === 'sms_code') codeError.value = item.message
          else error.value = item.message
        }
        if (!e.fieldErrors.length) error.value = e.message
      } else {
        error.value = e.message
      }
    } else {
      error.value = '登录失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="zw-auth">
    <aside class="zw-auth-brand">
      <div>
        <div class="a01-brand-name">智牧通</div>
        <div class="a01-brand-intro">面向中小养殖场的生产与经营管理系统</div>
      </div>
      <div class="a01-brand-foot">
        <div>客服电话 400-123-4567</div>
        <div>用户协议 · 隐私政策 · 版本 1.0.0</div>
      </div>
    </aside>
    <main class="zw-auth-main">
      <div class="zw-auth-card">
        <div class="a01-title">登录</div>
        <div class="a01-desc">App 和网页版使用同一个账号</div>

        <zw-notice v-if="expiredText" class="a01-notice" icon="info">{{ expiredText }}</zw-notice>
        <zw-notice v-else-if="error" class="a01-notice" type="error" icon="error">{{ error }}</zw-notice>

        <v-tabs v-model="tab" class="zw-tabs-line a01-tabs" grow>
          <v-tab value="pwd">密码登录</v-tab>
          <v-tab value="sms">验证码登录</v-tab>
        </v-tabs>

        <div class="a01-form">
          <zw-form-item label="手机号" :error="phoneError">
            <v-text-field v-model="phone" prefix="+86" placeholder="请输入手机号" maxlength="11" :error="!!phoneError" @keydown.enter="submit" />
          </zw-form-item>

          <template v-if="tab === 'pwd'">
            <zw-form-item label="密码" :error="passwordError">
              <v-text-field
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
                :append-inner-icon="showPassword ? 'visibility' : 'visibility_off'"
                :error="!!passwordError"
                @click:append-inner="showPassword = !showPassword"
                @keydown.enter="submit"
              />
            </zw-form-item>
            <div class="a01-row"><a class="zw-link" @click="router.push({ name: 'WU-A03', query: { tab: 'reset' } })">忘记密码</a></div>
          </template>

          <template v-else>
            <zw-form-item label="短信验证码" :error="codeError" :hint="masked ? `验证码已发送至 ${masked}` : ''">
              <v-text-field v-model="smsCode" placeholder="6 位验证码" maxlength="6" :error="!!codeError" @keydown.enter="submit">
                <template #append-inner>
                  <span v-if="countdown > 0" class="a01-count">{{ countdown }} 秒后重新获取</span>
                  <span v-else class="a01-get" @click="sendCode">{{ sending ? '发送中…' : '获取验证码' }}</span>
                </template>
              </v-text-field>
            </zw-form-item>
          </template>

          <v-btn color="primary" size="large" block :loading="submitting" @click="submit">登录</v-btn>

          <div class="a01-alt">
            <a class="zw-link" @click="router.push({ name: 'WU-A03' })">
              <zw-icon name="phone_iphone" :size="16" />手机号无法使用
            </a>
          </div>
        </div>

        <div class="a01-foot">
          还没有账号？<a class="zw-link" @click="router.push({ name: 'WU-A02' })">注册</a>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.a01-brand-name { font-size: 40px; line-height: 48px; font-weight: 600; letter-spacing: 4px; color: #ffffff; }
.a01-brand-intro { margin-top: 12px; font-size: 16px; line-height: 26px; color: var(--sider-text); }
.a01-brand-foot { font-size: 12px; line-height: 20px; color: var(--sider-group); }
.a01-title { font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.a01-desc { margin-top: 4px; font-size: 14px; line-height: 22px; color: var(--text-2); }
.a01-tabs { margin: 20px 0 24px; }
.a01-form { display: flex; flex-direction: column; gap: 20px; }
.a01-row { display: flex; justify-content: flex-end; margin-top: -8px; }
.a01-alt { display: flex; justify-content: center; margin-top: 16px; }
.a01-alt a { display: inline-flex; align-items: center; gap: 4px; }
.a01-foot { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--line-1); text-align: center; color: var(--text-2); }
.a01-count { font-size: 14px; color: var(--text-3); white-space: nowrap; }
.a01-get { font-size: 14px; color: var(--primary); white-space: nowrap; cursor: pointer; }
.a01-notice { margin-top: 20px; }
</style>
