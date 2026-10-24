<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import RecoveryCodeCard from './components/RecoveryCodeCard.vue'
import { useRegisterStore } from '@/stores/account/register'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const store = useRegisterStore()
const auth = useAuthStore()
const farm = useFarmStore()
const ui = useUiStore()

onMounted(async () => {
  store.stopCountdown()
  store.$reset()
  const phone = String(route.query.phone || '')
  if (phone) store.setPhone(phone)
  void store.loadAgreement()

  const step = String(route.query.step || '')
  if (step === 'SAVE_RECOVERY_CODE' || auth.nextStep === 'SAVE_RECOVERY_CODE') {
    if (!auth.isLoggedIn) await auth.refresh()
    if (auth.isLoggedIn) {
      const ok = await store.startSaveCodeStep()
      if (!ok && auth.nextStep === 'RECOVERY_CHANGE_PASSWORD') {
        await router.replace({ name: 'WU-A03', query: { step: 'RECOVERY_CHANGE_PASSWORD' } })
      }
      return
    }
  }
  if (auth.isLoggedIn && auth.nextStep === 'RECOVERY_CHANGE_PASSWORD') {
    await router.replace({ name: 'WU-A03', query: { step: 'RECOVERY_CHANGE_PASSWORD' } })
  }
})

onBeforeUnmount(() => store.stopCountdown())

function goLogin(): void {
  if (auth.isLoggedIn) {
    void router.push('/')
    return
  }
  void router.push({
    name: 'WU-A01',
    query: store.phone ? { phone: store.phone } : undefined
  })
}

function openAgreement(which: 'user' | 'privacy'): void {
  const item = which === 'user' ? store.agreement?.user_agreement : store.agreement?.privacy_policy
  if (!item?.url) {
    void store.loadAgreement()
    ui.toast('协议加载失败，请稍后重试', 'error')
    return
  }
  window.open(item.url, '_blank', 'noopener,noreferrer')
}

async function enter(): Promise<void> {
  const ok = await store.confirm()
  if (!ok) return
  try {
    await farm.load(true)
  } catch {
    /* 养殖场加载失败仍进入工作台，由工作台提示 */
  }
  if (!farm.farms.length) await router.replace('/farms/new')
  else await router.replace('/')
}
</script>

<template>
  <div class="zw-auth">
    <aside class="zw-auth-brand">
      <div>
        <div class="a02-brand-name">智牧通</div>
        <div class="a02-brand-intro">面向中小养殖场的生产与经营管理系统</div>
      </div>
      <div class="a02-brand-foot">
        <div>客服电话 400-123-4567</div>
        <div>用户协议 · 隐私政策 · 版本 1.0.0</div>
      </div>
    </aside>

    <main class="zw-auth-main">
      <div class="zw-auth-card">
        <a class="a02-back" @click="goLogin"><zw-icon name="arrow_back" :size="18" />返回登录</a>
        <div class="a02-title">注册账号</div>
        <div class="a02-desc">
          用你自己的手机号注册。注册后立即可以登录，不需要审核；App 和网页版使用同一个账号。
        </div>

        <div class="a02-form">
          <zw-form-item label="手机号" hint="一个手机号只能注册一个账号" :error="store.errors.phone">
            <v-text-field
              :model-value="store.phoneDisplay"
              prefix="+86"
              placeholder="请输入手机号"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="13"
              :error="!!store.errors.phone"
              @update:model-value="store.setPhone"
            />
          </zw-form-item>

          <zw-form-item
            label="短信验证码"
            :hint="store.masked ? `验证码已发送至 ${store.masked}` : ''"
            :error="store.errors.sms_code"
          >
            <v-text-field
              :model-value="store.smsCode"
              placeholder="6 位验证码"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              :error="!!store.errors.sms_code"
              @update:model-value="store.setSmsCode"
              @keydown.enter="store.register"
            >
              <template #append-inner>
                <span v-if="store.countdown > 0" class="a02-count">{{ store.countdown }} 秒后重新获取</span>
                <span v-else class="a02-get" @click="store.sendCode">
                  {{ store.sending ? '发送中…' : '获取验证码' }}
                </span>
              </template>
            </v-text-field>
          </zw-form-item>

          <zw-form-item
            label="设置密码"
            hint="8–20 位，需同时包含字母和数字"
            :error="store.errors.password"
          >
            <v-text-field
              :model-value="store.password"
              :type="store.showPassword ? 'text' : 'password'"
              placeholder="请设置密码"
              autocomplete="new-password"
              :append-inner-icon="store.showPassword ? 'visibility' : 'visibility_off'"
              :error="!!store.errors.password"
              @update:model-value="store.setPassword"
              @click:append-inner="store.showPassword = !store.showPassword"
              @keydown.enter="store.register"
            />
          </zw-form-item>
        </div>

        <v-checkbox
          class="a02-agree"
          :class="{ 'a02-agree-err': !!store.errors.agree_terms }"
          :model-value="store.agree"
          @update:model-value="store.setAgree"
        >
          <template #label>
            我已阅读并同意<a class="zw-link" @click.stop.prevent="openAgreement('user')">《用户协议》</a>和<a
              class="zw-link"
              @click.stop.prevent="openAgreement('privacy')"
              >《隐私政策》</a
            >
          </template>
        </v-checkbox>
        <div v-if="store.errors.agree_terms" class="a02-agree-msg">{{ store.errors.agree_terms }}</div>

        <v-btn
          color="primary"
          size="large"
          block
          class="mt-5"
          :loading="store.submitting"
          :disabled="!store.canSubmit"
          @click="store.register"
        >
          注册
        </v-btn>
        <div class="a02-foot">已有账号？<a class="zw-link" @click="goLogin">登录</a></div>
      </div>
    </main>

    <zw-dialog
      v-model="store.dialogOpen"
      title="保存恢复码"
      icon="key"
      tone="info"
      width="520"
      persistent
      no-close
    >
      <p class="a02-lead">注册成功。手机号不能使用（停机、丢失、换号）时，需要用这个恢复码找回账号。</p>
      <RecoveryCodeCard
        v-if="store.recoveryCode"
        :code="store.recoveryCode.code"
        :phone-masked="store.recoveryCode.phone_masked || store.masked"
        :generated-at="store.recoveryCode.generated_at"
      />
      <zw-notice type="warning" class="mt-4">
        <b>恢复码只显示这一次。</b>请截图或抄下来，放在安全的地方。没有恢复码的找回申请一律不受理。
      </zw-notice>
      <ul class="a02-uses">
        <li>
          <span class="zw-ico-tile"><zw-icon name="phonelink_erase" :size="18" /></span>
          <div>
            <div class="a02-uses-t">找回账号时使用</div>
            <div class="a02-uses-d">同时填写原手机号和新手机号，由客服核实</div>
          </div>
        </li>
        <li>
          <span class="zw-ico-tile"><zw-icon name="autorenew" :size="18" /></span>
          <div>
            <div class="a02-uses-t">可以重新生成</div>
            <div class="a02-uses-d">
              在“个人中心 → 账号与安全”中经短信验证后生成，旧恢复码同时作废
            </div>
          </div>
        </li>
      </ul>
      <div class="a02-check">
        <v-checkbox v-model="store.saved" label="我已保存恢复码" />
      </div>
      <template #actions>
        <v-btn
          color="primary"
          :disabled="!store.saved"
          :loading="store.confirming"
          @click="enter"
        >
          进入智牧通
        </v-btn>
      </template>
    </zw-dialog>
  </div>
</template>

<style scoped>
.a02-brand-name {
  font-size: 40px;
  line-height: 48px;
  font-weight: 600;
  letter-spacing: 4px;
  color: #ffffff;
}
.a02-brand-intro {
  margin-top: 12px;
  font-size: 16px;
  line-height: 26px;
  color: var(--sider-text);
}
.a02-brand-foot {
  font-size: 12px;
  line-height: 20px;
  color: var(--sider-group);
}
.a02-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  color: var(--text-2);
  cursor: pointer;
}
.a02-title {
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  color: var(--text-1);
}
.a02-desc {
  margin-top: 4px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.a02-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 24px;
}
.a02-count {
  font-size: 14px;
  color: var(--text-3);
  white-space: nowrap;
}
.a02-get {
  font-size: 14px;
  color: var(--primary);
  white-space: nowrap;
  cursor: pointer;
}
.a02-agree {
  margin-top: 12px;
  margin-left: -4px;
}
.a02-agree-err :deep(.v-selection-control__input > .v-icon) {
  color: var(--error) !important;
}
.a02-agree-msg {
  margin: 0 0 0 24px;
  font-size: 12px;
  line-height: 18px;
  color: var(--error);
}
.a02-foot {
  margin-top: 20px;
  text-align: center;
  color: var(--text-2);
}
.a02-lead {
  margin: 0 0 16px;
  color: var(--text-1);
}
.a02-uses {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.a02-uses li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.a02-uses .zw-ico-tile {
  margin-top: 2px;
}
.a02-uses-t {
  font-weight: 500;
  color: var(--text-1);
}
.a02-uses-d {
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.a02-check {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-1);
}
</style>
