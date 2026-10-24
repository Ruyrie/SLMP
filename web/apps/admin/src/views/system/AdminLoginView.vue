<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '@zmt/shared'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const route = useRoute()
const admin = useAdminStore()
const ui = useUiStore()

const username = ref(localStorage.getItem('zmt.admin.username') || '')
const password = ref('')
const captchaCode = ref('')
const captchaId = ref('')
const captchaImage = ref('')
const remember = ref(!!username.value)
const error = ref('')
const submitting = ref(false)
const showPassword = ref(false)
const captchaLoading = ref(false)
const fieldErrors = ref<Record<string, string>>({})

const changeOpen = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changeSubmitting = ref(false)
const changeError = ref('')

async function loadCaptcha(): Promise<void> {
  captchaLoading.value = true
  captchaCode.value = ''
  try {
    const data = await admin.fetchCaptcha()
    captchaId.value = data.captcha_id
    captchaImage.value = data.image
  } catch {
    error.value = '验证码加载失败，请点击图片重试'
  } finally {
    captchaLoading.value = false
  }
}

onMounted(() => {
  void loadCaptcha()
  if (route.query.reason) error.value = String(route.query.reason)
})

function safeRedirect(): string {
  const redirect = String(route.query.redirect || '')
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
}

async function submit(): Promise<void> {
  error.value = ''
  fieldErrors.value = {}
  if (!username.value.trim()) {
    fieldErrors.value.username = '请输入管理员账号'
    return
  }
  if (!password.value) {
    fieldErrors.value.password = '请输入密码'
    return
  }
  if (!captchaCode.value.trim()) {
    fieldErrors.value.captcha_code = '请输入验证码'
    return
  }
  submitting.value = true
  try {
    const result = await admin.login(username.value.trim(), password.value, captchaId.value, captchaCode.value.trim())
    if (remember.value) localStorage.setItem('zmt.admin.username', username.value.trim())
    else localStorage.removeItem('zmt.admin.username')
    if (result.must_change_password) {
      currentPassword.value = password.value
      changeOpen.value = true
      return
    }
    admin.startPolling()
    await router.replace(safeRedirect())
  } catch (e) {
    if (e instanceof ApiError) {
      for (const item of e.fieldErrors) fieldErrors.value[item.field] = item.message
      error.value = e.message
      if (e.code === 40103) void loadCaptcha()
    } else {
      error.value = '登录失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

async function changePassword(): Promise<void> {
  changeError.value = ''
  if (!newPassword.value || newPassword.value.length < 8) {
    changeError.value = '新密码至少 8 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    changeError.value = '两次输入的新密码不一致'
    return
  }
  changeSubmitting.value = true
  try {
    await admin.changePassword(currentPassword.value, newPassword.value, confirmPassword.value)
    admin.reset()
    changeOpen.value = false
    ui.toast('密码已修改，请使用新密码重新登录')
    password.value = ''
    await router.replace({ name: 'WA-S01' })
    await loadCaptcha()
  } catch (e) {
    changeError.value = e instanceof ApiError ? e.message : '密码修改失败，请稍后重试'
  } finally {
    changeSubmitting.value = false
  }
}
</script>

<template>
  <div class="zw-auth">
    <aside class="zw-auth-brand admin-brand">
      <div>
        <div class="admin-brand-row">
          <div class="a01-brand-name">智牧通</div>
          <span class="admin-brand-tag">管理端</span>
        </div>
        <div class="a01-brand-intro">养殖生产与经营管理平台 · 平台管理员使用</div>
        <ul class="admin-feature">
          <li><zw-icon name="fact_check" :size="20" /><div><b>审核中心</b><span>养殖场注册、资料变更、修改申请和账号找回</span></div></li>
          <li><zw-icon name="campaign" :size="20" /><div><b>内容运营</b><span>平台通知、市场行情、养殖课堂、知识库与 AI 问答更正</span></div></li>
          <li><zw-icon name="database" :size="20" /><div><b>基础数据</b><span>品种与生长标准、免疫模板、兽药疫苗目录、巡检模板和字典</span></div></li>
          <li><zw-icon name="manage_accounts" :size="20" /><div><b>用户与系统</b><span>用户账号、养殖场管理、管理员权限和操作日志</span></div></li>
        </ul>
      </div>
      <div class="a01-brand-foot">
        管理端只显示汇总数据，不显示养殖户的单笔账目和凭证。所有审核、冻结、停用和发布操作都会记录操作人、原因和时间。
      </div>
    </aside>

    <main class="zw-auth-main">
      <div class="zw-auth-card">
        <div class="a01-title">登录管理端</div>
        <div class="a01-desc">使用超级管理员分配的管理员账号登录</div>

        <zw-notice v-if="error" class="admin-notice" type="error" icon="error">{{ error }}</zw-notice>

        <div class="admin-form">
          <zw-form-item label="管理员账号" :error="fieldErrors.username">
            <v-text-field v-model="username" placeholder="请输入管理员账号" prepend-inner-icon="person" :error="!!fieldErrors.username" @keydown.enter="submit" />
          </zw-form-item>
          <zw-form-item label="密码" :error="fieldErrors.password">
            <v-text-field
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              prepend-inner-icon="lock"
              :append-inner-icon="showPassword ? 'visibility' : 'visibility_off'"
              :error="!!fieldErrors.password"
              @click:append-inner="showPassword = !showPassword"
              @keydown.enter="submit"
            />
          </zw-form-item>
          <zw-form-item label="图形验证码" :error="fieldErrors.captcha_code">
            <div class="admin-captcha-row">
              <v-text-field v-model="captchaCode" placeholder="请输入右侧字符" maxlength="4" :error="!!fieldErrors.captcha_code" @keydown.enter="submit" />
              <button class="admin-captcha" type="button" :disabled="captchaLoading" @click="loadCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
                <span v-else>加载中…</span>
              </button>
            </div>
          </zw-form-item>
          <div class="admin-row">
            <v-checkbox v-model="remember" label="记住账号" hide-details density="compact" />
            <a class="zw-link" @click="loadCaptcha">换一张验证码</a>
          </div>
          <v-btn color="primary" size="large" block :loading="submitting" @click="submit">登录</v-btn>
        </div>

        <div class="admin-foot">
          忘记密码请联系超级管理员重置。连续 5 次密码错误，账号锁定 30 分钟。管理员账号不能登录 App 和用户端网页。
        </div>
      </div>
      <div class="admin-copyright">智牧通 · 第六组 · 2026</div>
    </main>

    <v-dialog v-model="changeOpen" :max-width="440" persistent>
      <div class="zw-dialog">
        <header class="zw-dialog-head">
          <zw-icon name="lock" :size="22" class="zw-dialog-ico zw-dialog-ico--primary" fill />
          <div class="zw-dialog-title">首次登录请修改密码</div>
        </header>
        <div class="zw-dialog-body zw-dialog-body--ico">
          <p class="change-tip">当前使用初始密码，修改后本次登录会失效，需要用新密码重新登录。</p>
          <zw-form-item label="当前密码"><v-text-field v-model="currentPassword" type="password" placeholder="请输入当前密码" /></zw-form-item>
          <zw-form-item label="新密码" hint="至少 8 位，包含字母和数字"><v-text-field v-model="newPassword" type="password" placeholder="请输入新密码" /></zw-form-item>
          <zw-form-item label="确认新密码"><v-text-field v-model="confirmPassword" type="password" placeholder="再次输入新密码" @keydown.enter="changePassword" /></zw-form-item>
          <zw-notice v-if="changeError" type="error" icon="error">{{ changeError }}</zw-notice>
        </div>
        <footer class="zw-dialog-actions">
          <v-btn variant="outlined" @click="changeOpen = false">稍后</v-btn>
          <v-btn color="primary" :loading="changeSubmitting" @click="changePassword">修改密码</v-btn>
        </footer>
      </div>
    </v-dialog>
  </div>
</template>

<style scoped>
.a01-brand-name { font-size: 40px; line-height: 48px; font-weight: 600; letter-spacing: 4px; color: #ffffff; }
.a01-brand-intro { margin-top: 12px; font-size: 16px; line-height: 26px; color: var(--sider-text); }
.a01-brand-foot { max-width: 400px; font-size: 12px; line-height: 20px; color: var(--sider-group); }
.a01-title { font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.a01-desc { margin-top: 4px; font-size: 14px; line-height: 22px; color: var(--text-2); }
.admin-brand-row { display: flex; align-items: center; gap: 12px; }
.admin-brand-tag { height: 22px; padding: 0 8px; border-radius: var(--radius-xs); background: var(--primary); color: var(--on-primary); font-size: 12px; line-height: 22px; }
.admin-feature { list-style: none; margin: 48px 0 0; padding: 0; display: flex; flex-direction: column; gap: 26px; }
.admin-feature li { display: flex; gap: 14px; color: var(--sider-text); }
.admin-feature li :deep(.zw-mso) { width: 36px; height: 36px; border-radius: var(--radius-sm); background: #ffffff14; color: #ffffff; font-size: 20px; flex-shrink: 0; }
.admin-feature b { display: block; font-size: 15px; line-height: 22px; font-weight: 500; color: #ffffff; }
.admin-feature span { display: block; margin-top: 2px; font-size: 12px; line-height: 18px; color: var(--sider-text); }
.admin-form { margin-top: 24px; display: flex; flex-direction: column; gap: 18px; }
.admin-captcha-row { display: flex; align-items: center; gap: 8px; }
.admin-captcha-row :deep(.zw-form-control) { flex: 1; }
.admin-captcha { width: 104px; height: 40px; border: 1px solid var(--control-border); border-radius: var(--radius-sm); background: var(--fill-1); display: flex; align-items: center; justify-content: center; padding: 0; cursor: pointer; overflow: hidden; }
.admin-captcha img { width: 100%; height: 100%; object-fit: cover; }
.admin-row { display: flex; align-items: center; justify-content: space-between; margin-top: -8px; }
.admin-notice { margin-top: 20px; }
.admin-foot { margin-top: 20px; padding-top: 18px; border-top: 1px solid var(--line-1); font-size: 12px; line-height: 20px; color: var(--text-3); }
.admin-copyright { position: absolute; right: 32px; bottom: 24px; color: var(--text-3); font-size: 12px; }
.change-tip { margin: 0 0 16px; color: var(--text-2); font-size: 13px; line-height: 20px; }
</style>
