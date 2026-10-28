<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate, formatMoney, formatNumber, formatPercent, formatTime } from '@zmt/shared'
import type { TimelineItem } from '@/api/herd'
import { herdApi } from '@/api/herd'
import { useHerdRecordEditStore } from '@/stores/herd/recordEdit'
import HerdRecordEditDrawer from './components/HerdRecordEditDrawer.vue'
import { stockRecordTarget } from './recordTarget'
import { batchTraceApi } from '@/api/batchTrace'
import { useBatchDetailStore, type TimelineFilter } from '@/stores/herd/batchDetail'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { detailStatusText, monthDay, sourceText } from '@/stores/herd/shared'
import { downloadQrLabel, printQrLabel } from './qr'

const store = useBatchDetailStore()
const farm = useFarmStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()
const { isStaff } = useRole()
const qrBox = ref<HTMLElement | null>(null)
const traceSaving = ref(false)
const rotateDialog = ref(false)
const editor = useHerdRecordEditStore()
const openingRecord = ref(false)
onUnmounted(() => editor.reset())

async function saveTrace(rotate = false): Promise<void> {
  if (!batch.value || traceSaving.value) return
  traceSaving.value = true
  try {
    await batchTraceApi.settings(batchId.value, rotate ? true : batch.value.trace_enabled === false, rotate, batch.value.version || 0)
    rotateDialog.value = false
    await load()
    ui.toast(rotate ? '溯源链接已更新，请重新下载并更换标签' : '公开溯源设置已更新')
  } catch (e) {
    ui.toast(e instanceof Error ? e.message : '设置失败，请重试', 'error')
  } finally { traceSaving.value = false }
}

const batchId = computed(() => Number(route.params.batchId || 0))
const batch = computed(() => store.batch)
const batchNo = computed(() => batch.value?.batch_no || String(route.query.batch_no || route.params.batchId || ''))
const showFarmTag = computed(() => route.query.switched === '1' || (!!batch.value?.farm_id && batch.value.farm_id !== farm.currentFarmId))
const desc = computed(() => {
  if (!batch.value) return ''
  const pens = (batch.value.pens || [])
    .map((pen) => `${pen.pen_name || '栏舍'} ${formatNumber(pen.stock_count || 0)} ${batch.value?.unit_text || '头'}`)
    .join(' · ')
  return `${batch.value.breed_title || batch.value.breed_name || ''} · ${pens || '—'} · ${batch.value.farm_name || farm.farmName}`
})
const pageTitle = computed(() => `批次 ${batchNo.value}`)
const crumbs = computed(() => (isStaff.value ? ['我的栏舍', '批次档案', batchNo.value] : ['生产管理', '批次档案', batchNo.value]))
const timelineFilter = ref<TimelineFilter>(store.timelineFilter)

const filterItems = [
  { label: '全部', value: 'ALL' },
  { label: '投喂', value: 'FEED' },
  { label: '称重', value: 'WEIGH' },
  { label: '免疫', value: 'IMMUNE' },
  { label: '用药', value: 'DRUG' },
  { label: '巡检', value: 'INSPECT' },
  { label: '存栏变动', value: 'STOCK' }
]

const statusTagType = computed(() => {
  if (batch.value?.withdrawal?.in_withdrawal) return 'warning'
  if (batch.value?.status === 'ENDED') return 'neutral'
  return 'primary'
})
const withdrawalText = computed(() => {
  const info = batch.value?.withdrawal
  if (!info?.in_withdrawal) return ''
  const parts: string[] = [`休药中，剩 ${formatNumber(info.remaining_days || 0)} 天。`]
  if (info.sellable_date) parts.push(`可出栏日期 ${monthDay(info.sellable_date)}。`)
  if (info.last_drug_name) {
    const drugDate = info.last_drug_date ? `末次用药 ${monthDay(info.last_drug_date)}` : '已用药'
    parts.push(`${info.last_drug_name}${drugDate}${info.withdrawal_days ? `，休药期 ${info.withdrawal_days} 天` : ''}。`)
  }
  return parts.join('')
})
const infoRows = computed(() => {
  if (!batch.value) return []
  const unit = batch.value.unit_text || '头'
  const rows: Array<{ label: string; value: string }> = [
    { label: '品种', value: batch.value.breed_name || '—' },
    { label: '品类', value: batch.value.category_name || '—' },
    { label: '来源', value: sourceText(batch.value.source) },
    { label: '所在栏舍', value: (batch.value.pens || []).map((pen) => `${pen.pen_name || '栏舍'} ${formatNumber(pen.stock_count || 0)} ${unit}`).join(' · ') || '—' },
    { label: '入栏日期', value: monthDay(batch.value.entry_date) },
    { label: '入栏日龄', value: `${formatNumber(batch.value.entry_age_days || 0)} 天` },
    { label: '入栏数量', value: `${formatNumber(batch.value.entry_count || 0)} ${unit}` },
    { label: '累计死淘', value: `${formatNumber(batch.value.death_count || 0)} ${unit}` }
  ]
  if (batch.value.entry_avg_weight !== undefined && batch.value.entry_avg_weight !== null) {
    rows.push({ label: '入栏平均体重', value: `${formatNumber(batch.value.entry_avg_weight, 1)} 千克` })
  }
  if (!isStaff.value && batch.value.intro_unit_price !== undefined && batch.value.intro_unit_price !== null) {
    rows.push({ label: '引种单价', value: `${formatNumber(batch.value.intro_unit_price, 2)} 元 / ${unit}` })
  }
  if (batch.value.status === 'ENDED' && batch.value.end_date) {
    rows.push({ label: '结束日期', value: monthDay(batch.value.end_date) })
  }
  return rows
})

function handleHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goBack()
}

function goBack(): void {
  if (window.history.length > 1) router.back()
  else void router.push({ name: 'WU-L03' })
}

function openTransfer(): void {
  void router.push({ name: 'WU-L05', query: { action: 'create', tab: 'transfer', batchId: String(batchId.value) } })
}

function openDeath(): void {
  void router.push({ name: 'WU-L05', query: { action: 'create', tab: 'death', batchId: String(batchId.value) } })
}

async function openChangeRequest(item?: TimelineItem): Promise<void> {
  if (!batch.value || openingRecord.value) return
  openingRecord.value = true
  const id = batchId.value
  const revision = farm.revision
  try {
    const record = item || (await herdApi.batchTimeline(id, { type: 'ENTRY', size: 1 })).list?.[0]
    if (id !== batchId.value || revision !== farm.revision) return
    const target = record && stockRecordTarget(record)
    if (!record || !target || !canChange(record)) throw new Error('这条记录当前无法修改，请刷新后重试')
    if (record.record_type === 'SALE') {
      await router.push({ name: 'WU-E04', params: { id: target.biz_id } })
    } else if (record.action === 'EDIT') {
      await editor.open(Number(target.biz_id), id, record.record_type || '')
    } else {
      await router.push({ name: 'WU-U01', query: { action: 'create', ...target } })
    }
  } catch (error) {
    ui.toast(error instanceof Error ? error.message : '记录加载失败，请重试', 'error')
  } finally { openingRecord.value = false }
}

function openMyChanges(): void {
  void router.push({
    name: 'WU-U01',
  })
}

function setFilter(value: TimelineFilter): void {
  timelineFilter.value = value
  store.setFilter(value)
}

function timelineIcon(item: TimelineItem): string {
  const map: Record<string, string> = {
    ENTRY: 'move_to_inbox',
    TRANSFER: 'swap_horiz',
    DEATH: 'heart_minus',
    SALE: 'local_shipping',
    FEED: 'rice_bowl',
    WEIGH: 'scale',
    IMMUNE: 'vaccines',
    DRUG: 'medication',
    INSPECT: 'fact_check'
  }
  return map[item.record_type || ''] || 'event_note'
}

function timelineTone(item: TimelineItem): string {
  if (item.record_type === 'DRUG') return 'warning'
  if (['ENTRY', 'TRANSFER', 'DEATH', 'SALE'].includes(item.record_type || '')) return 'neutral'
  return 'primary'
}

function timelineTime(item: TimelineItem): string {
  if (item.is_today) return `今天 ${formatTime(item.occurred_at)}`
  return formatDate(item.occurred_at || item.biz_date)
}

function canChange(item: TimelineItem): boolean {
  return !!stockRecordTarget(item) && ['EDIT', 'CHANGE_REQUEST'].includes(item.action || '')
    && (!isStaff.value || (item.record_type === 'DEATH' && item.action === 'EDIT'))
}

function toggleRecord(item: TimelineItem): void {
  if (!item.expandable) return
  store.toggleExpanded(item.id)
}

/** 与页面上的 zw-qr 同一内容；按内容直接生成图片，内容为空时按钮禁用。 */
const qrContent = computed(() => String(batch.value?.qr_content || batch.value?.batch_no || '').trim())
const qrBusy = ref(false)

async function downloadQr(): Promise<void> {
  if (!batch.value || !qrContent.value || qrBusy.value) return
  qrBusy.value = true
  try {
    await downloadQrLabel(qrContent.value, {
      batchNo: batch.value.batch_no || batchNo.value,
      label: batch.value.label_text || desc.value,
      filename: `${batch.value.batch_no || 'batch'}.png`
    })
    ui.toast('二维码已下载')
  } catch (error) {
    ui.toast(error instanceof Error ? error.message : '下载失败，请重试', 'error')
  } finally {
    qrBusy.value = false
  }
}

async function printQr(): Promise<void> {
  if (!batch.value || !qrContent.value) return
  try {
    await printQrLabel(qrContent.value, {
      batchNo: batch.value.batch_no || batchNo.value,
      label: batch.value.label_text || desc.value
    })
  } catch (error) {
    ui.toast(error instanceof Error ? error.message : '打印失败，请重试', 'error')
  }
}

async function load(): Promise<void> {
  editor.reset()
  await store.load(batchId.value, true)
  timelineFilter.value = store.timelineFilter
}

onMounted(() => {
  void load()
})
useFarmReload(async () => {
  store.reset()
  await load()
})
watch(batchId, async () => {
  store.reset()
  await load()
})
</script>

<template>
  <zw-shell side="user" :role="isStaff ? 'staff' : undefined" active="WU-L04" :crumbs="crumbs">
    <v-dialog v-model="rotateDialog" max-width="440">
      <v-card title="更新公开溯源链接" text="更新后旧二维码将无法打开，需要重新下载并更换已打印的标签。新链接将开放公开只读溯源。">
        <v-card-actions><v-spacer /><v-btn @click="rotateDialog = false">取消</v-btn><v-btn color="primary" :loading="traceSaving" @click="saveTrace(true)">更新链接</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <div @click="handleHeadClick">
      <zw-page-head :title="pageTitle" :desc="desc" back>
        <template #tags>
          <zw-tag v-if="showFarmTag" type="farm" icon="domain">{{ batch?.farm_name || farm.farmName }}</zw-tag>
          <zw-tag v-if="batch" :type="statusTagType" :icon="batch.withdrawal?.in_withdrawal ? 'hourglass_top' : undefined">
            {{ detailStatusText(batch) }}
          </zw-tag>
          <zw-tag v-if="batch">{{ sourceText(batch.source) }}</zw-tag>
          <zw-tag v-if="batch?.purpose === 'BREEDING'">种用</zw-tag>
        </template>
        <template v-if="store.status === 'ready' && batch" #actions>
          <template v-if="!isStaff">
            <v-btn v-if="store.canTransfer" variant="outlined" prepend-icon="swap_horiz" @click="openTransfer">转栏</v-btn>
            <v-btn v-if="store.canRegisterDeath" variant="outlined" prepend-icon="heart_minus" @click="openDeath">登记死淘</v-btn>
            <v-menu location="bottom end">
              <template #activator="{ props: menuProps }">
                <v-btn v-bind="menuProps" variant="outlined" icon="more_horiz" aria-label="更多" />
              </template>
              <div class="zw-pop l04-menu">
                <div v-if="store.canRequestChange" class="l04-menu-item" @click="openChangeRequest()">
                  <zw-icon name="edit_note" :size="18" />
                  <span class="l04-menu-label">修改入栏记录</span>
                </div>
                <div class="l04-menu-item" @click="openMyChanges">
                  <zw-icon name="history" :size="18" />
                  <span class="l04-menu-label">查看我的修改申请</span>
                </div>
              </div>
            </v-menu>
          </template>
          <v-btn v-else-if="store.canRegisterDeath" color="primary" prepend-icon="heart_minus" @click="openDeath">登记死淘</v-btn>
        </template>
      </zw-page-head>
    </div>

    <div v-if="store.status === 'loading'">
      <v-skeleton-loader type="article, article, table-row@5" />
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '你没有这个批次的查看权限。'">
        <v-btn variant="outlined" @click="goBack">返回上一页</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'deleted'">
      <zw-empty icon="delete" tone="warning" title="该批次记录已被删除" desc="该批次记录已被删除，无法查看。">
        <v-btn variant="outlined" @click="goBack">返回上一页</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'not_found'">
      <zw-empty icon="search_off" title="批次不存在" :desc="store.error || '批次不存在或不属于当前养殖场。'">
        <v-btn variant="outlined" @click="goBack">返回批次列表</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="批次档案加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="load">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="batch">
      <zw-card flush class="mb-4">
        <div class="l04-sum">
          <div class="l04-sum-main">
            <div class="l04-kpis">
              <div class="l04-kpi">
                <div class="l04-kpi-l">日龄</div>
                <div class="l04-kpi-v num">{{ formatNumber(batch.day_age || 0) }}<small>天</small></div>
                <div class="l04-kpi-f">入栏日龄 {{ formatNumber(batch.entry_age_days || 0) }} 天 · 已养 {{ formatNumber(batch.days_raised || 0) }} 天</div>
              </div>
              <div class="l04-kpi">
                <div class="l04-kpi-l">生长阶段</div>
                <div class="l04-kpi-v">{{ batch.stage_name || '—' }}</div>
                <div class="l04-kpi-f">按品种和日龄自动计算</div>
              </div>
              <div class="l04-kpi">
                <div class="l04-kpi-l">存栏</div>
                <div class="l04-kpi-v num">{{ formatNumber(batch.current_count || 0) }}<small>{{ batch.unit_text || '头' }}</small></div>
                <div class="l04-kpi-f">入栏 {{ formatNumber(batch.entry_count || 0) }} {{ batch.unit_text || '头' }} · 累计死淘 {{ formatNumber(batch.death_count || 0) }} {{ batch.unit_text || '头' }}</div>
              </div>
              <div class="l04-kpi">
                <div class="l04-kpi-l">死亡率</div>
                <div class="l04-kpi-v num">{{ formatPercent(batch.mortality_rate, 1) }}</div>
                <div class="l04-kpi-f">累计死淘 ÷ 入栏数</div>
              </div>
            </div>
            <div class="l04-div" />
            <zw-desc :cols="4">
              <zw-desc-item v-for="row in infoRows" :key="row.label" :label="row.label"><span class="num">{{ row.value }}</span></zw-desc-item>
            </zw-desc>
          </div>
          <div class="l04-qr">
            <div ref="qrBox"><zw-qr :size="112" :seed="batch.qr_content || batch.batch_no || batchNo" /></div>
            <div class="l04-qr-no num">{{ batch.batch_no || batchNo }}</div>
            <div class="l04-qr-sub">{{ batch.label_text || desc }}</div>
            <div class="l04-qr-btns">
              <v-btn size="small" variant="outlined" prepend-icon="download" :disabled="!qrContent" :loading="qrBusy" @click="downloadQr">下载图片</v-btn>
              <v-btn size="small" variant="outlined" prepend-icon="print" :disabled="!qrContent" @click="printQr">打印标签</v-btn>
            </div>
            <div v-if="!isStaff" class="mt-3">
              <p class="text-caption">公开溯源{{ batch.trace_enabled === false ? '已关闭' : '已开放' }}</p>
              <v-btn size="small" variant="text" :loading="traceSaving" @click="saveTrace()">{{ batch.trace_enabled === false ? '开放溯源' : '关闭溯源' }}</v-btn>
              <v-btn size="small" variant="text" :disabled="traceSaving" @click="rotateDialog = true">更新链接</v-btn>
            </div>
          </div>
        </div>
      </zw-card>

      <zw-notice v-if="batch.withdrawal?.in_withdrawal" type="warning" icon="medication" class="mb-4">
        <b>{{ withdrawalText }}</b>
        <template #action><a @click="router.push({ name: 'WU-D02' })">查看用药档案</a></template>
      </zw-notice>

      <zw-notice v-if="Number(batch.pending_change_request_count || 0) > 0" class="mb-4">
        有 {{ batch.pending_change_request_count }} 条修改申请待审核，审核期间原记录照常有效。
        <template #action><a @click="openMyChanges">查看</a></template>
      </zw-notice>

      <zw-card title="生产记录" sub="按时间倒序" flush>
        <template #extra>
          <zw-seg size="small" :items="filterItems" :model-value="timelineFilter" @update:model-value="setFilter" />
        </template>
        <div class="l04-tl">
          <div v-if="store.timelineStatus === 'loading'">
            <v-skeleton-loader type="list-item-two-line@4" />
          </div>
          <zw-empty v-else-if="store.timelineStatus === 'error'" icon="cloud_off" tone="error" title="生产记录加载失败" :desc="store.timelineError">
            <v-btn variant="outlined" @click="store.loadTimeline(true)">重新加载</v-btn>
          </zw-empty>
          <zw-empty v-else-if="!store.timeline.length" icon="history" :title="store.timelineEmptyText" />
          <ul v-else class="zw-tl l04-timeline">
            <li v-for="item in store.timeline" :key="item.id" class="zw-tl-item">
              <span class="zw-tl-dot" :class="`zw-tl-dot--${timelineTone(item)}`"><zw-icon :name="timelineIcon(item)" :size="14" /></span>
              <div class="zw-tl-main">
                <div class="zw-tl-head">
                  <span class="zw-tl-title l04-record-title" :class="{ 'l04-record-title--click': item.expandable }" @click="toggleRecord(item)">
                    {{ item.title }}
                    <zw-icon v-if="item.expandable" :name="store.isExpanded(item.id) ? 'expand_less' : 'expand_more'" :size="16" />
                  </span>
                  <span class="zw-tl-time num">{{ timelineTime(item) }}</span>
                </div>
                <div v-if="item.subtitle" class="zw-tl-desc">{{ item.subtitle }}</div>
                <div v-if="store.isExpanded(item.id) && item.detail?.length" class="l04-record-detail">
                  <div v-for="detail in item.detail" :key="detail.key" class="l04-record-kv">
                    <span class="zw-faint">{{ detail.key }}</span>
                    <span>{{ detail.value }}</span>
                  </div>
                  <div v-if="item.operator_name" class="l04-record-kv">
                    <span class="zw-faint">登记人</span>
                    <span>{{ item.operator_name }}</span>
                  </div>
                  <div v-if="isStaff && item.action_note" class="l04-record-note">{{ item.action_note }}</div>
                </div>
                <div v-if="canChange(item)" class="l04-record-actions">
                  <a @click.stop="openChangeRequest(item)">{{ item.record_type === 'SALE' ? '查看出栏单' : item.action === 'EDIT' ? '修改记录' : '提交修改申请' }}</a>
                  <zw-tag v-if="item.record_type !== 'SALE' && item.action === 'CHANGE_REQUEST'" size="small" type="warning">需审核</zw-tag>
                </div>
              </div>
            </li>
          </ul>
        </div>
        <template #foot>
          <div class="l04-record-foot">
            <span class="zw-faint body-s">{{ isStaff ? '超过当天的死淘记录由所有者或场长修改' : '可直接修改的记录显示“修改记录”；需要审核的记录显示“提交修改申请”' }}</span>
            <div>
              <v-btn v-if="store.hasMore" variant="text" color="primary" size="small" append-icon="expand_more" :loading="store.loadingMore" @click="store.loadMore()">
                加载更早的记录
              </v-btn>
              <span v-else-if="store.timeline.length" class="zw-faint body-s">没有更多了</span>
            </div>
          </div>
        </template>
      </zw-card>
    </template>
    <HerdRecordEditDrawer @saved="load" />
  </zw-shell>
</template>

<style scoped>
.l04-sum { display: flex; }
.l04-sum-main { flex: 1; min-width: 0; padding: 20px 24px; }
.l04-kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); }
.l04-kpi + .l04-kpi { border-left: 1px solid var(--line-1); padding-left: 24px; }
.l04-kpi-l { font-size: 14px; line-height: 22px; color: var(--text-2); }
.l04-kpi-v { margin-top: 4px; font-size: 24px; line-height: 32px; font-weight: 600; color: var(--text-1); }
.l04-kpi-v small { font-size: 14px; font-weight: 400; color: var(--text-2); margin-left: 4px; }
.l04-kpi-f { margin-top: 2px; font-size: 12px; line-height: 20px; color: var(--text-3); }
.l04-div { height: 1px; background: var(--line-1); margin: 16px 0; }
.l04-qr { width: 232px; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; padding: 20px 16px; border-left: 1px solid var(--line-1); text-align: center; }
.l04-qr-no { margin-top: 8px; font-size: 16px; line-height: 24px; font-weight: 600; font-variant-numeric: tabular-nums; }
.l04-qr-sub { font-size: 12px; line-height: 18px; color: var(--text-3); }
.l04-qr-btns { display: flex; gap: 8px; margin-top: 12px; }
.l04-tl { padding: 20px 24px 4px; }
.l04-timeline :deep(.zw-tl-title) { display: inline-flex; align-items: center; gap: 4px; }
.l04-record-title--click { cursor: pointer; }
.l04-record-title--click:hover { color: var(--primary); }
.l04-record-detail { margin-top: 8px; padding: 8px 12px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 12px; line-height: 20px; }
.l04-record-kv { display: flex; gap: 8px; }
.l04-record-kv > span:first-child { width: 72px; flex-shrink: 0; }
.l04-record-note { margin-top: 4px; color: var(--text-3); }
.l04-record-actions { display: flex; align-items: center; gap: 8px; margin-top: 6px; font-size: 13px; }
.l04-record-actions a { color: var(--primary); cursor: pointer; }
.l04-record-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.l04-menu { width: 240px; }
.l04-menu-item { display: flex; align-items: center; gap: 10px; height: 40px; padding: 0 16px; cursor: pointer; font-size: 14px; color: var(--text-1); }
.l04-menu-item:hover { background: var(--fill-1); }
.l04-menu-label { flex: 1; }
</style>
