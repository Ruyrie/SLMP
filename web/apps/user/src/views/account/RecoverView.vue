<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RecoveryCodeCard from './components/RecoveryCodeCard.vue'
import { useRecoverStore } from '@/stores/account/recover'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { copyText, formatMonthDay } from './accountForm'

const route = useRoute()
const router = useRouter()
const store = useRecoverStore()
const auth = useAuthStore()
const farm = useFarmStore()
const ui = useUiStore()

const discardOpen = ref(false)

const cancelAvailableText = computed(() =>
  formatMonthDay(store.force.recoveryInfo?.cancel_available_date)
)
const forcedSteps = computed(() => [
  {
    title: '修改密码',
    status: store.mode === 'save' ? 'done' : 'current'
  },
  {
    title: '保存新的恢复码',
    status: store.mode === 'save' ? 'current' : 'wait'
  }
])

onMounted(async () => {
  store.stopTimers()
  store.$reset()
  const step = String(route.query.step || '')
  const tab = String(route.query.tab || '')
  const phone = String(route.query.phone || '')
  await store.init(step, tab)
  if (phone) {
    store.setResetPhone(phone)
    store.setOldPhone(phone)
  }
})

onBeforeUnmount(() => store.stopTimers())

function goLogin(): void {
  if (store.mode === 'recover' && store.recoverDirty) {
    discardOpen.value = true
    return
  }
  void router.push({ name: 'WU-A01' })
}

function discard(): void {
  discardOpen.value = false
  void router.push({ name: 'WU-A01' })
}

async function submitReset(): Promise<void> {
  const ok = await store.submitReset()
  if (!ok) return
  ui.toast('密码已重置，请用新密码登录')
  await router.replace({
    name: 'WU-A01',
    query: store.reset.phone ? { phone: store.reset.phone } : undefined
  })
}

async function submitRecover(): Promise<void> {
  const masked = await store.submitRecover()
  if (!masked) return
  ui.toast(`找回申请已提交，客服核实后会把新密码发到 ${masked}`)
  await router.replace({
    name: 'WU-A01',
    query: store.recover.oldPhone ? { phone: store.recover.oldPhone } : undefined
  })
}

function toRecover(): void {
  if (store.reset.phone) store.setOldPhone(store.reset.phone)
  store.setMode('recover')
}

async function copyServicePhone(): Promise<void> {
  const ok = await copyText(store.servicePhone)
  if (ok) ui.toast('已复制客服电话')
  else window.location.href = `tel:${store.servicePhone}`
}

async function submitForcedPassword(): Promise<void> {
  await store.submitForcedPassword()
}

async function enter(): Promise<void> {
  const ok = await store.confirmForcedCode()
  if (!ok) return
  try {
    await farm.load(true)
  } catch {
    /* 养殖场加载失败仍进入工作台 */
  }
  const redirect = String(route.query.redirect || '')
  if (redirect.startsWith('/') && !redirect.startsWith('//') && redirect !== '/') await router.replace(redirect)
  else if (!farm.farms.length) await router.replace('/farms/new')
  else await router.replace('/')
}
</script>

<template>
  <div class="zw-auth">
    <aside class="zw-auth-brand">
      <div>
        <div class="a03-brand-name">智牧通</div>
        <div class="a03-brand-intro">面向中小养殖场的生产与经营管理系统</div>
      </div>
      <div class="a03-brand-foot">
        <div>客服电话 400-123-4567</div>
        <div>用户协议 · 隐私政策 · 版本 1.0.0</div>
      </div>
    </aside>

    <main class="zw-auth-main">
      <!-- A03 重置密码 -->
      <div v-if="store.mode === 'reset'" class="zw-auth-card">
        <a class="a03-back" @click="goLogin"><zw-icon name="arrow_back" :size="18" />返回登录</a>
        <div class="a03-title">重置密码</div>
        <div class="a03-desc">
          手机号还能使用时，用短信验证码就能自行重置密码，不需要联系客服。
        </div>
        <div class="a03-form">
          <zw-form-item label="手机号" :error="store.reset.errors.phone">
            <v-text-field
              :model-value="store.reset.phone"
              prefix="+86"
              placeholder="请输入手机号"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="11"
              :error="!!store.reset.errors.phone"
              @update:model-value="store.setResetPhone"
            />
          </zw-form-item>
          <zw-form-item
            label="短信验证码"
            :hint="store.reset.masked ? `验证码已发送至 ${store.reset.masked}` : ''"
            :error="store.reset.errors.sms_code"
          >
            <v-text-field
              :model-value="store.reset.smsCode"
              placeholder="6 位验证码"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              :error="!!store.reset.errors.sms_code"
              @update:model-value="store.setResetCode"
            >
              <template #append-inner>
                <span v-if="store.reset.countdown > 0" class="a03-count"
                  >{{ store.reset.countdown }} 秒后重新获取</span
                >
                <span v-else class="a03-get" @click="store.sendResetCode">
                  {{ store.reset.sending ? '发送中…' : '获取验证码' }}
                </span>
              </template>
            </v-text-field>
          </zw-form-item>
          <zw-form-item
            label="新密码"
            hint="8–20 位，需同时包含字母和数字"
            :error="store.reset.errors.new_password"
          >
            <v-text-field
              :model-value="store.reset.password"
              :type="store.reset.showPassword ? 'text' : 'password'"
              placeholder="请输入新密码"
              autocomplete="new-password"
              :append-inner-icon="store.reset.showPassword ? 'visibility' : 'visibility_off'"
              :error="!!store.reset.errors.new_password"
              @update:model-value="store.setResetPassword"
              @click:append-inner="store.reset.showPassword = !store.reset.showPassword"
              @keydown.enter="submitReset"
            />
          </zw-form-item>
        </div>
        <zw-notice class="a03-notice" icon="devices">
          重置后，其他设备上的登录会自动失效，需要用新密码重新登录。
        </zw-notice>
        <v-btn
          color="primary"
          size="large"
          block
          class="mt-5"
          :loading="store.reset.submitting"
          @click="submitReset"
        >
          重置密码
        </v-btn>
        <div class="a03-foot">
          手机号已停机或丢失？<a class="zw-link" @click="toRecover">申请找回账号</a>
        </div>
      </div>

      <!-- A05 找回账号 -->
      <div v-else-if="store.mode === 'recover'" class="zw-auth-card">
        <a class="a03-back" @click="goLogin"><zw-icon name="arrow_back" :size="18" />返回登录</a>
        <div class="a03-title">找回账号</div>
        <div class="a03-desc">
          客服核实后为你重置密码并换绑新手机号，新密码以短信发到新手机号。
        </div>
        <zw-notice v-if="store.recover.lockBanner" type="error" icon="block" class="a03-notice">
          <b>{{ store.recover.lockBanner }}</b>
        </zw-notice>
        <zw-notice v-else type="warning" class="a03-notice">
          <b>恢复码连续填错 5 次，24 小时内不能再提交。</b>没有恢复码的申请不受理。
        </zw-notice>
        <div class="a03-form a03-form--tight">
          <zw-form-item label="原手机号" :error="store.recover.errors.old_phone">
            <v-text-field
              :model-value="store.recover.oldPhone"
              prefix="+86"
              placeholder="请输入原手机号"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="11"
              :error="!!store.recover.errors.old_phone"
              @update:model-value="store.setOldPhone"
            />
          </zw-form-item>
          <zw-form-item
            label="恢复码"
            :error="store.recover.errors.recovery_code"
            hint="输入时自动转大写，每 4 位一个空格"
          >
            <v-text-field
              :model-value="store.recoveryCodeDisplay"
              placeholder="16 位字母和数字"
              maxlength="19"
              :error="!!store.recover.errors.recovery_code"
              :append-inner-icon="store.recover.errors.recovery_code ? 'error' : undefined"
              @update:model-value="store.setRecoveryCode"
            />
          </zw-form-item>
          <zw-form-item label="新手机号" :error="store.recover.errors.new_phone">
            <v-text-field
              :model-value="store.recover.newPhone"
              prefix="+86"
              placeholder="请输入新手机号"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="11"
              :error="!!store.recover.errors.new_phone"
              @update:model-value="store.setNewPhone"
            />
          </zw-form-item>
          <zw-form-item
            label="新手机号验证码"
            :hint="store.recover.masked ? `验证码已发送至 ${store.recover.masked}` : ''"
            :error="store.recover.errors.new_phone_sms_code"
          >
            <v-text-field
              :model-value="store.recover.newPhoneCode"
              placeholder="6 位验证码"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              :error="!!store.recover.errors.new_phone_sms_code"
              @update:model-value="store.setNewPhoneCode"
            >
              <template #append-inner>
                <span v-if="store.recover.countdown > 0" class="a03-count"
                  >{{ store.recover.countdown }} 秒后重新获取</span
                >
                <span v-else class="a03-get" @click="store.sendRecoverCode">
                  {{ store.recover.sending ? '发送中…' : '获取验证码' }}
                </span>
              </template>
            </v-text-field>
          </zw-form-item>
          <zw-form-item label="情况说明" :error="store.recover.errors.description">
            <template #label-extra>
              <span class="a03-lc num">{{ store.descriptionCount }} / 200</span>
            </template>
            <v-textarea
              class="a03-ta"
              :model-value="store.recover.description"
              rows="2"
              maxlength="200"
              placeholder="请说明手机号不能使用的原因"
              :error="!!store.recover.errors.description"
              @update:model-value="store.setDescription"
            />
          </zw-form-item>
        </div>
        <v-btn
          color="primary"
          size="large"
          block
          class="mt-5"
          :loading="store.recover.submitting"
          :disabled="!!store.recover.lockBanner"
          @click="submitRecover"
        >
          提交找回申请
        </v-btn>
        <div class="a03-svc">
          <zw-icon name="support_agent" :size="18" />
          <span>也可以拨打客服电话</span>
          <a class="zw-link num" @click="copyServicePhone">{{ store.servicePhone }}</a>
        </div>
      </div>

      <!-- A06 找回后首次登录 · 第 1 步 -->
      <div v-else-if="store.mode === 'change'" class="zw-auth-card">
        <zw-steps small class="a03-steps" :items="forcedSteps" />
        <div class="a03-title">修改密码</div>
        <zw-notice class="a03-notice" icon="verified_user">
          <b>账号已找回。</b>你已用新手机号
          {{ store.force.recoveryInfo?.new_phone_masked || '' }} 登录，请先修改密码。其他设备上的登录已自动失效。
        </zw-notice>
        <div class="a03-form">
          <zw-form-item
            label="新密码"
            hint="8–20 位，需同时包含字母和数字，不能使用短信中的临时密码"
            :error="store.force.errors.new_password"
          >
            <v-text-field
              :model-value="store.force.password"
              :type="store.force.showPassword ? 'text' : 'password'"
              placeholder="请输入新密码"
              autocomplete="new-password"
              :append-inner-icon="store.force.showPassword ? 'visibility' : 'visibility_off'"
              :error="!!store.force.errors.new_password"
              @update:model-value="store.setForcePassword($event)"
              @click:append-inner="store.force.showPassword = !store.force.showPassword"
            />
          </zw-form-item>
          <zw-form-item label="再次输入新密码" :error="store.force.errors.confirm_password">
            <v-text-field
              :model-value="store.force.confirmPassword"
              :type="store.force.showPassword ? 'text' : 'password'"
              placeholder="请再输入一次"
              autocomplete="new-password"
              :error="!!store.force.errors.confirm_password"
              @update:model-value="store.setForceConfirm($event)"
              @keydown.enter="submitForcedPassword"
            />
          </zw-form-item>
        </div>
        <zw-notice
          v-if="store.force.recoveryInfo?.cancel_available_date"
          type="warning"
          icon="event_busy"
          class="a03-notice"
        >
          <b>找回后 7 天内不能注销账号。</b>{{ cancelAvailableText }}前注销不可用，防止账号被他人冒名找回后恶意注销。
        </zw-notice>
        <v-btn
          color="primary"
          size="large"
          block
          class="mt-5"
          :loading="store.force.submitting"
          @click="submitForcedPassword"
        >
          修改密码并继续
        </v-btn>
      </div>

      <!-- A06 找回后首次登录 · 第 2 步 -->
      <div v-else class="zw-auth-card">
        <zw-steps small class="a03-steps" :items="forcedSteps" />
        <div class="a03-title">保存新的恢复码</div>
        <div class="a03-desc">密码已修改。旧恢复码已作废，下面是新的恢复码。</div>
        <RecoveryCodeCard
          v-if="store.force.code"
          :code="store.force.code.code"
          :phone-masked="store.force.code.phone_masked || ''"
          :generated-at="store.force.code.generated_at"
          class="a03-code"
        />
        <zw-notice type="warning" class="a03-notice">
          <b>恢复码只显示这一次。</b>请截图或抄下来，放在安全的地方。没有恢复码的找回申请一律不受理。
        </zw-notice>
        <v-checkbox v-model="store.force.saved" class="a03-check" label="我已保存恢复码" />
        <v-btn
          color="primary"
          size="large"
          block
          class="mt-4"
          :disabled="!store.force.saved"
          :loading="store.force.submitting"
          @click="enter"
        >
          进入智牧通
        </v-btn>
      </div>
    </main>

    <zw-dialog v-model="discardOpen" title="放弃这次找回申请？" width="420">
      <p class="a03-discard">已填写的内容不会保存。</p>
      <template #actions>
        <v-btn variant="outlined" @click="discardOpen = false">继续填写</v-btn>
        <v-btn color="primary" @click="discard">放弃</v-btn>
      </template>
    </zw-dialog>
  </div>
</template>

<style scoped>
.a03-brand-name {
  font-size: 40px;
  line-height: 48px;
  font-weight: 600;
  letter-spacing: 4px;
  color: #ffffff;
}
.a03-brand-intro {
  margin-top: 12px;
  font-size: 16px;
  line-height: 26px;
  color: var(--sider-text);
}
.a03-brand-foot {
  font-size: 12px;
  line-height: 20px;
  color: var(--sider-group);
}
.a03-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  color: var(--text-2);
  cursor: pointer;
}
.a03-title {
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  color: var(--text-1);
}
.a03-desc {
  margin-top: 4px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.a03-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 20px;
}
.a03-form--tight {
  gap: 12px;
}
.a03-lc {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
}
.a03-svc {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 14px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-2);
}
.a03-svc .zw-mso {
  color: var(--text-3);
}
.a03-count {
  font-size: 14px;
  color: var(--text-3);
  white-space: nowrap;
}
.a03-get {
  font-size: 14px;
  color: var(--primary);
  white-space: nowrap;
  cursor: pointer;
}
.a03-notice {
  margin-top: 16px;
}
.a03-foot {
  margin-top: 20px;
  text-align: center;
  color: var(--text-2);
}
.a03-steps {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line-1);
}
.a03-code {
  margin-top: 16px;
}
.a03-check {
  margin: 12px 0 0 -4px;
}
.a03-discard {
  margin: 0;
  color: var(--text-1);
}
.a03-ta :deep(.v-field__input) {
  min-height: 52px;
}
</style>
