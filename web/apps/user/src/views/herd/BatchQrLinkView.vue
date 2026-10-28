<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { batchTraceApi, type BatchTrace } from '@/api/batchTrace'
import { herdApi } from '@/api/herd'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const farm = useFarmStore()
const trace = ref<BatchTrace | null>(null)
const loading = ref(true)
const error = ref('')
const managementError = ref('')
const opening = ref(false)
const labels: Record<string, string> = { WEIGH: '称重', IMMUNE: '免疫接种', MEDICATION: '用药记录', SALE: '出栏' }
let requestId = 0
async function load(): Promise<void> {
  const request = ++requestId
  trace.value = null
  error.value = ''
  managementError.value = ''
  loading.value = true
  const token = String(route.params.token || '')
  if (!/^[A-Za-z0-9]{10}$/.test(token)) {
    error.value = '二维码地址不完整，请重新扫描批次标签。'
    loading.value = false
    return
  }
  try {
    const data = await batchTraceApi.read(token)
    if (request === requestId) trace.value = data
  } catch (e) {
    if (request !== requestId) return
    error.value = Number((e as { code?: number }).code) === 40401
      ? '该溯源链接不存在、已停用或已更新，请向养殖场获取最新标签。'
      : '溯源信息暂时无法加载，请检查网络后重试。'
  } finally { if (request === requestId) loading.value = false }
}
async function openManagement(): Promise<void> {
  if (opening.value) return
  opening.value = true
  managementError.value = ''
  const request = requestId
  try {
    if (!auth.accessToken) {
      if (!await auth.refresh()) {
        await router.push({ name: 'WU-A01', query: { redirect: route.fullPath } })
        return
      }
      await auth.loadSession()
    }
    if (!farm.loaded) await farm.load()
    const qrContent = new URL(router.resolve({ name: 'WU-L06', params: { token: route.params.token } }).href, window.location.origin).href
    const result = await herdApi.lookupBatch({ qrContent })
    if (request !== requestId) return
    if (!['OK', 'SWITCH_FARM'].includes(result.result || '') || !result.batch_id || !result.farm_id) {
      managementError.value = '你可以查看此页公开溯源；完整管理档案仅向有权限的场内成员开放。'
      return
    }
    const changed = farm.currentFarmId !== result.farm_id
    if (changed) await farm.switchTo(result.farm_id)
    if (request !== requestId) return
    await router.push({ name: 'WU-L04', params: { batchId: result.batch_id }, query: changed ? { switched: '1' } : undefined })
  } catch { if (request === requestId) managementError.value = '管理档案暂时无法打开，请稍后重试。' }
  finally { opening.value = false }
}
watch(() => route.params.token, () => void load(), { immediate: true })
onBeforeUnmount(() => { requestId++ })
</script>

<template>
  <main class="trace-page">
    <header class="trace-header"><span>智牧通 · 公开溯源</span><span class="trace-readonly">只读</span></header>
    <zw-card v-if="loading || error">
      <zw-empty :icon="loading ? 'qr_code_2' : 'info'" :title="loading ? '正在读取溯源信息' : '暂时无法查看'" :desc="error">
        <v-progress-circular v-if="loading" indeterminate color="primary" :size="28" />
        <v-btn v-else color="primary" variant="outlined" @click="load">重新加载</v-btn>
      </zw-empty>
    </zw-card>
    <template v-else-if="trace">
      <zw-card class="trace-intro">
        <p class="trace-label">批次档案</p><h1>{{ trace.batch_no }}</h1><p class="trace-farm">{{ trace.farm_name }}</p>
        <dl class="trace-facts">
          <div><dt>品类 / 品种</dt><dd>{{ trace.category_name }} / {{ trace.breed_name }}</dd></div>
          <div><dt>来源</dt><dd>{{ trace.source === 'SELF_BRED' ? '本场自繁' : trace.source === 'PURCHASED' ? '外购入栏' : '未登记' }}</dd></div>
          <div><dt>入栏日期</dt><dd>{{ trace.entry_date }}</dd></div>
          <div><dt>入栏日龄</dt><dd>{{ trace.entry_age_days ?? '—' }} 天</dd></div>
          <div v-if="trace.entry_avg_weight != null"><dt>入栏平均体重</dt><dd>{{ trace.entry_avg_weight }} 千克</dd></div>
          <div><dt>养殖状态</dt><dd>{{ trace.status === 'ENDED' ? '已结束' : '在养' }}{{ trace.end_date ? ` · ${trace.end_date}` : '' }}</dd></div>
        </dl>
      </zw-card>
      <zw-card class="trace-records">
        <h2>养殖记录</h2><p class="trace-label">展示最近 200 条已生效的公开记录</p>
        <p v-if="!trace.events.length" class="trace-empty">暂无已登记的公开记录</p>
        <ol v-else><li v-for="(event, index) in trace.events" :key="index">
          <time>{{ event.date }}</time><div><h3>{{ labels[event.type] || event.type }}</h3>
            <p v-if="!['WEIGH', 'SALE'].includes(event.type)">{{ event.name }}</p>
            <p v-if="event.avg_weight != null">平均体重 {{ event.avg_weight }} 千克</p>
            <p v-if="event.quantity != null">数量 {{ event.quantity }}</p>
            <p v-if="event.withdrawal_end_date">休药结束日期 {{ event.withdrawal_end_date }}</p>
          </div>
        </li></ol>
      </zw-card>
      <footer><p>由养殖场登记提供 · 最近更新 {{ trace.updated_at?.replace('T', ' ') || '—' }}</p>
        <v-btn variant="outlined" color="primary" :loading="opening" @click="openManagement">{{ auth.accessToken ? '进入管理档案' : '成员登录查看管理档案' }}</v-btn>
        <p v-if="managementError" role="status">{{ managementError }}</p>
      </footer>
    </template>
  </main>
</template>

<style scoped>
.trace-page { max-width: 800px; margin: 0 auto; padding: 24px 18px 48px; }
.trace-header { display: flex; justify-content: space-between; align-items: center; color: #34597a; margin-bottom: 20px; font-weight: 600; }
.trace-readonly { background: #e8f2ff; padding: 4px 12px; border-radius: 16px; font-size: 13px; }
.trace-intro, .trace-records { margin-bottom: 18px; }
.trace-label, dt, footer, .trace-empty { color: #697480; font-size: 14px; }
h1 { font-size: 30px; overflow-wrap: anywhere; margin: 4px 0 8px; }
.trace-farm { font-size: 18px; margin-bottom: 24px; }
.trace-facts { display: grid; grid-template-columns: 1fr 1fr; gap: 22px 18px; }
dd { margin: 5px 0 0; } h2 { font-size: 21px; margin-bottom: 5px; }
ol { padding: 0; list-style: none; } li { display: grid; grid-template-columns: 110px 1fr; gap: 16px; padding: 20px 0; border-bottom: 1px solid #e3e9ef; }
li:last-child { border: 0; } time { font-size: 14px; color: #63788d; } h3 { font-size: 16px; margin: 0 0 6px; } li p { margin: 4px 0; }
footer { text-align: center; } footer p { margin: 18px 0; }
@media (max-width: 420px) { .trace-facts { grid-template-columns: 1fr; gap: 16px; } li { grid-template-columns: 92px 1fr; gap: 10px; } }
</style>
