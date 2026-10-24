<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDateTime, formatNumber, formatPercent } from '@zmt/shared'
import { useDiagnosisStore } from '@/stores/inspect/diagnosis'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useUiStore } from '@/stores/ui'
import {
  likelihoodPercent,
  mediaDuration,
  mediaIsVideo,
  monthDay,
  timeOnly,
  urgencyIcon,
  urgencyType
} from '@/views/inspect/inspect'
import MediaPreviewDialog, { type PreviewItem } from './components/MediaPreviewDialog.vue'
import DiagnosisHandleDrawer from './components/DiagnosisHandleDrawer.vue'
import type { DiagnosisDetail, MediaItem } from '@/api/inspect'

const store = useDiagnosisStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const reportId = computed(() => Number(route.params.id || 0))
const previewOpen = ref(false)
const previewIndex = ref(0)
const handleQueryOpened = ref(false)

const detail = computed(() => store.detail)
const report = computed(() => detail.value?.report)
const diagnosis = computed(() => detail.value?.diagnosis)
const handle = computed(() => detail.value?.handle)
const permissions = computed(() => store.permissions)
const isPending = computed(() => store.isPending)
const isClosed = computed(() => store.isClosed)
const isUrgent = computed(() => store.isUrgent)
const urgencyText = computed(() => store.urgencyText)
const showHandleButton = computed(() => !isClosed.value && (store.canHandle || store.canModifyHandle))
const canViewInspection = computed(() => !!permissions.value.can_view_inspection && !!report.value?.inspection_id)
const canRediagnose = computed(() => !!permissions.value.can_rediagnose)
const isManager = computed(() => farm.role !== 'staff')

const descParts = computed(() => {
  if (!report.value) return []
  const parts = [report.value.pen_name || '']
  if (report.value.batch_no) parts.push(report.value.batch_no)
  if (report.value.reporter?.name || report.value.reported_at) {
    parts.push(`${report.value.reporter?.name || ''} ${monthDay(report.value.reported_at)} ${timeOnly(report.value.reported_at)} 上报`.trim())
  }
  return parts
})
const media = computed(() => (report.value?.media || []) as MediaItem[])
const previewItems = computed<PreviewItem[]>(() =>
  media.value.map((item) => ({
    url: item.url || '',
    video: mediaIsVideo(item),
    duration: mediaDuration(item),
    name: item.source_text || '',
    shotAt: item.shot_at ? formatDateTime(item.shot_at) : '',
    shotBy: item.shot_by || ''
  }))
)
const steps = computed(() => store.stepItems)
const handledProgress = computed(() => {
  if (!handle.value || !report.value?.sick_count) return 0
  return Math.min(100, ((Number(handle.value.cured_count || 0) + Number(handle.value.culled_count || 0)) / Number(report.value.sick_count)) * 100)
})
const timelineItems = computed(() => {
  const info = detail.value
  if (!info) return []
  const titles: Record<string, string> = { PENDING: '上报异常', DIAGNOSED: 'AI 诊断完成', HANDLED: '登记处理结果', CLOSED: '记录已结案' }
  const tones: Record<string, string> = { PENDING: 'primary', DIAGNOSED: 'ai', HANDLED: 'success', CLOSED: 'neutral' }
  const icons: Record<string, string> = { PENDING: 'report', DIAGNOSED: 'auto_awesome', HANDLED: 'edit_note', CLOSED: 'lock' }
  const items: Array<{ title: string; time: string; desc: string; tone: string; icon: string }> = []
  for (const step of info.steps || []) {
    if (step.state === 'TODO') continue
    let desc = step.desc || ''
    if (step.code === 'PENDING') {
      desc = info.report.source === 'INSPECTION'
        ? `从 ${timeOnly(info.report.inspection_registered_at)} 的巡检转上报 · 发病 ${formatNumber(info.report.sick_count || 0)} 头`
        : `直接上报 · 发病 ${formatNumber(info.report.sick_count || 0)} 头`
    } else if (step.code === 'DIAGNOSED') {
      desc = info.diagnosis
        ? `紧急程度“${info.diagnosis.urgency_text || '一般'}” · 已通知${(info.notified_names || []).join('、') || '上报人和场长'}`
        : ''
    } else if (step.code === 'HANDLED') {
      desc = info.handle ? `${info.handle.handled_by_name || ''} ${timeOnly(info.handle.handled_at)} 登记` : ''
    } else if (step.code === 'CLOSED') {
      desc = '治愈和死淘合计达到发病数量，已结案'
    }
    items.push({
      title: titles[step.code] || step.code,
      time: timeOnly(step.time),
      desc,
      tone: tones[step.code] || 'neutral',
      icon: icons[step.code] || 'circle'
    })
  }
  if (info.last_failure && !info.diagnosis) {
    items.push({
      title: '诊断请求失败',
      time: timeOnly(info.last_failure.failed_at),
      desc: `${info.last_failure.reason_text || ''}${info.last_failure.trigger_type === 'RETRY' ? ' · 手动重新诊断' : ''}`,
      tone: 'warning',
      icon: 'cloud_off'
    })
  }
  return items
})

async function load(): Promise<void> {
  if (!reportId.value) return
  handleQueryOpened.value = false
  await store.load(reportId.value)
  maybeOpenHandle()
}

onMounted(load)
watch(
  () => route.params.id,
  () => void load()
)
useFarmReload(() => store.load(reportId.value))
onBeforeUnmount(() => store.dispose())

function maybeOpenHandle(): void {
  if (handleQueryOpened.value) return
  if (route.query.action === 'handle' && showHandleButton.value) {
    handleQueryOpened.value = true
    store.openHandle()
  }
}

watch(showHandleButton, () => maybeOpenHandle())

function goBack(): void {
  void router.push({ name: 'WU-I03' })
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goBack()
}

function openHandle(): void {
  store.openHandle()
}

function openPreview(index: number): void {
  previewIndex.value = index
  previewOpen.value = true
}

async function rediagnose(): Promise<void> {
  try {
    await store.rediagnose()
    ui.toast('已重新发起诊断')
  } catch {
    ui.toast('AI 诊断仍不可用，服务恢复后会自动重新诊断', 'error')
  }
}

function openInspection(): void {
  if (!report.value?.inspection_id) return
  const date = report.value.reported_at?.slice(0, 10) || ''
  void router.push({
    name: 'WU-I02',
    query: { penId: String(report.value.pen_id), ...(date ? { date } : {}) }
  })
}

function goMedication(): void {
  if (!report.value) return
  void router.push({
    name: 'WU-D02',
    query: {
      batchId: String(report.value.batch_id),
      penId: String(report.value.pen_id)
    }
  })
}
</script>

<template>
  <zw-shell side="user" active="WU-I04" :crumbs="['防疫健康', '异常与诊断', '诊断详情']">
    <zw-page-head title="诊断详情" :desc="descParts.join(' · ')" back @click="onHeadClick">
      <template #tags>
        <zw-tag v-if="isUrgent" type="error" icon="emergency" size="small">紧急</zw-tag>
        <zw-tag v-else-if="diagnosis" :type="urgencyType(store.urgency)" :icon="urgencyIcon(store.urgency)" size="small">{{ urgencyText }}</zw-tag>
        <zw-tag v-else type="neutral" icon="hourglass_top" size="small">{{ detail?.status_text || '待诊断' }}</zw-tag>
      </template>
      <template #actions>
        <v-btn v-if="canViewInspection" variant="outlined" prepend-icon="fact_check" @click="openInspection">查看关联巡检</v-btn>
        <v-btn v-if="canRediagnose" variant="outlined" prepend-icon="refresh" :loading="store.rediagnosing" @click="rediagnose">重新诊断</v-btn>
        <v-btn v-if="showHandleButton" color="primary" prepend-icon="edit_note" @click="openHandle">
          {{ handle ? '修改处理结果' : '登记处理结果' }}
        </v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'" class="i04-loading">
      <zw-card><v-skeleton-loader type="heading" /></zw-card>
      <div class="i04-main">
        <div class="i04-left"><v-skeleton-loader type="article" /><v-skeleton-loader type="article" /></div>
        <div class="i04-right"><v-skeleton-loader type="article" /></div>
      </div>
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '你没有这条诊断记录的查看权限。员工只能查看自己上报的诊断。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-I03' })">返回异常上报</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="诊断详情加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load(reportId)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else-if="detail">
      <zw-notice v-if="isUrgent" type="error" icon="emergency" class="mb-4" title="疑似重大动物疫病。">
        请联系执业兽医并向当地动物防疫机构报告。
      </zw-notice>

      <zw-card flush class="i04-steps">
        <zw-steps :items="steps" />
      </zw-card>

      <div class="i04-main">
        <div class="i04-left">
          <zw-ai
            title="AI 诊断结果"
            :sub="diagnosis?.generated_at ? `${monthDay(diagnosis.generated_at)} ${timeOnly(diagnosis.generated_at)} 生成` : ''"
            :disclaimer="diagnosis?.disclaimer || '仅供参考，需执业兽医确认'"
            class="i04-ai"
          >
            <template #extra>
              <zw-tag v-if="diagnosis" :type="urgencyType(diagnosis.urgency)" :icon="urgencyIcon(diagnosis.urgency)" size="small">
                {{ diagnosis.urgency_text || urgencyText }}
              </zw-tag>
            </template>

            <template v-if="diagnosis">
              <div class="i04-conclusion">{{ diagnosis.conclusion || 'AI 已完成诊断。' }}</div>
              <div class="i04-ai-grid">
                <div class="i04-ai-card">
                  <div class="i04-ai-card-title">可能病因 · 按可能性从高到低</div>
                  <div v-for="cause in diagnosis.causes || []" :key="cause.rank_no" class="i04-cause">
                    <span class="i04-cause-rank num">{{ cause.rank_no }}</span>
                    <div class="i04-cause-main">
                      <div class="i04-cause-name">
                        {{ cause.disease_name }}
                        <zw-tag v-if="cause.is_major_epidemic" type="error" icon="emergency" size="small">重大动物疫病</zw-tag>
                      </div>
                      <div class="i04-cause-bar">
                        <v-progress-linear :model-value="likelihoodPercent(cause.likelihood)" height="4" rounded color="tertiary" />
                        <span class="i04-cause-level">{{ cause.likelihood_text || '' }}</span>
                      </div>
                    </div>
                  </div>
                  <div v-if="!diagnosis.causes?.length" class="i04-ai-empty">暂无可能病因</div>
                </div>
                <div class="i04-ai-card">
                  <div class="i04-ai-card-title">处理建议</div>
                  <ol class="i04-advices">
                    <li v-for="(advice, index) in diagnosis.advices || []" :key="index">{{ advice }}</li>
                  </ol>
                  <div v-if="!diagnosis.advices?.length" class="i04-ai-empty">暂无处理建议</div>
                </div>
              </div>
              <div v-if="diagnosis.emergency_notice || isUrgent" class="i04-push">
                <zw-icon name="notifications_active" :size="16" />
                已立即推送给{{ (detail.notified_names || []).join('、') || '上报人、所有者和场长' }}，不受免打扰限制
              </div>
            </template>

            <template v-else>
              <div v-if="detail.ai_state === 'DIAGNOSING'" class="i04-ai-pending">
                <div class="i04-ai-pending-title">AI 正在诊断</div>
                <v-skeleton-loader type="text" />
                <v-skeleton-loader type="text" />
                <v-skeleton-loader type="text" />
                <div class="i04-ai-pending-desc">你可以先离开本页，诊断完成后会通知你。</div>
              </div>
              <div v-else class="i04-ai-pending">
                <zw-empty icon="hourglass_top" title="等待诊断" :desc="detail.no_diagnosis_text || '诊断完成后，页面自动刷新为已诊断。'" />
              </div>
            </template>
          </zw-ai>

          <zw-card v-if="detail.ai_state !== 'DIAGNOSING'" class="mt-4">
            <template #title>
              <span>处理结果</span>
              <span v-if="handle?.handled_by_name" class="zw-card-sub">{{ handle.handled_by_name }} {{ timeOnly(handle.handled_at) }} 登记</span>
            </template>
            <template #extra>
              <a v-if="handle?.has_revisions" class="i04-link" @click="store.openHandleLogs()">查看修改记录</a>
            </template>

            <zw-notice v-if="isClosed" type="info" icon="lock" class="mb-3">已结案，处理结果不能再修改</zw-notice>

            <div v-if="!handle" class="i04-handle-empty">
              <span class="i04-handle-ico"><zw-icon name="edit_note" :size="20" /></span>
              <span v-if="showHandleButton">还没有登记处理结果。请执业兽医确认诊断后，登记诊断人、处理方式、用药和转归；用了药品可以一键转为用药记录。</span>
              <span v-else-if="!isManager">处理结果由所有者或场长登记。</span>
              <span v-else-if="detail.ai_state === 'DIAGNOSING'">AI 正在诊断，诊断完成后可以登记处理结果。</span>
              <span v-else>AI 诊断暂不可用，服务恢复后会自动重新诊断一次，也可以点“重新诊断”。</span>
            </div>

            <template v-else>
              <zw-desc :cols="1" :label-width="88">
                <zw-desc-item label="诊断人">{{ handle.diagnostician || '—' }}</zw-desc-item>
                <zw-desc-item label="处理方式">{{ handle.treatment || '—' }}</zw-desc-item>
                <zw-desc-item label="用药">
                  <template v-if="handle.used_drug">
                    使用了药品
                    <template v-if="handle.medication_converted">
                      · <a class="i04-link" @click="goMedication">已转为用药记录</a>
                    </template>
                  </template>
                  <span v-else>未使用药品</span>
                </zw-desc-item>
                <zw-desc-item label="转归">
                  <div class="i04-outcome">
                    <zw-tag type="success" icon="check" size="small">治愈 {{ formatNumber(handle.cured_count || 0) }} 头</zw-tag>
                    <zw-tag type="neutral" size="small">死淘 {{ formatNumber(handle.culled_count || 0) }} 头</zw-tag>
                  </div>
                  <div class="i04-outcome-bar">
                    <v-progress-linear :model-value="handledProgress" height="6" rounded />
                    <span class="num">合计 {{ formatNumber((handle.cured_count || 0) + (handle.culled_count || 0)) }} / {{ formatNumber(report?.sick_count || 0) }} 头</span>
                  </div>
                </zw-desc-item>
                <zw-desc-item label="AI 诊断">{{ handle.ai_match_text || '—' }}</zw-desc-item>
              </zw-desc>
              <div class="i04-handle-note">
                治愈和死淘合计达到 {{ formatNumber(report?.sick_count || 0) }} 头后自动结案，结案后不能再修改；死淘需另行登记，本模块不扣减存栏。
              </div>
            </template>
          </zw-card>
        </div>

        <div class="i04-right">
          <zw-card title="上报信息" :sub="[report?.pen_name, report?.batch_no].filter(Boolean).join(' · ')">
            <div class="i04-metrics">
              <div class="i04-metric">
                <span class="i04-metric-label">存栏</span>
                <span class="i04-metric-value num">{{ formatNumber(report?.stock_snapshot || 0) }}<small>头</small></span>
              </div>
              <div class="i04-metric">
                <span class="i04-metric-label">发病</span>
                <span class="i04-metric-value num">{{ formatNumber(report?.sick_count || 0) }}<small>头</small></span>
              </div>
              <div class="i04-metric">
                <span class="i04-metric-label">发病率</span>
                <span class="i04-metric-value num">
                  {{ report?.sick_rate !== null && report?.sick_rate !== undefined ? formatPercent(report.sick_rate, 1).replace('%', '') : '—' }}<small>%</small>
                </span>
              </div>
            </div>

            <div v-if="report?.symptom_names?.length" class="i04-symptoms">
              <zw-tag v-for="symptom in report.symptom_names" :key="symptom" size="small">{{ symptom }}</zw-tag>
            </div>
            <div class="i04-desc">{{ report?.description || '未填写描述' }}</div>

            <div v-if="media.length" class="i04-media">
              <button v-for="(item, index) in media" :key="item.file_id" type="button" class="i04-thumb" @click="openPreview(index)">
                <img v-if="!mediaIsVideo(item)" :src="item.thumb_url || item.url" alt="">
                <video v-else :src="item.url" preload="metadata" muted />
                <span v-if="mediaIsVideo(item)" class="i04-thumb-play"><zw-icon name="play_arrow" :size="18" fill /></span>
                <span v-if="mediaIsVideo(item) && mediaDuration(item)" class="i04-thumb-dur">{{ mediaDuration(item) }}</span>
              </button>
            </div>

            <div class="i04-report-foot">
              <zw-icon name="person" :size="16" />
              {{ report?.reporter?.name || '—' }} {{ timeOnly(report?.reported_at) }} 上报 · {{ formatNumber(report?.media_count || 0) }} 个照片和视频
            </div>
          </zw-card>

          <zw-card title="诊断记录" class="mt-4">
            <zw-timeline :items="timelineItems" />
          </zw-card>
        </div>
      </div>
    </template>

    <MediaPreviewDialog v-model="previewOpen" :items="previewItems" :start-index="previewIndex" source-label="异常上报" />

    <DiagnosisHandleDrawer v-model="store.handleOpen" :detail="store.detail" />

    <zw-dialog v-model="store.handleLogsOpen" title="处理结果修改记录" :width="560">
      <div v-if="store.handleLogsLoading"><v-skeleton-loader type="list-item-three-line@2" /></div>
      <zw-timeline
        v-else-if="store.handleLogs.length"
        :items="store.handleLogs.map((log) => ({
          title: `${log.operator?.name || '—'} ${log.op_action_text || '修改'}`,
          time: log.created_at ? formatDateTime(log.created_at) : '',
          desc: (log.changes || []).map((change) => `${change.field_name || ''}：${change.before || '—'} → ${change.after || '—'}`).join('；'),
          tone: 'primary',
          icon: 'history'
        }))"
      />
      <zw-empty v-else icon="history" title="没有修改记录" desc="处理结果第一次保存后，如果有修改会显示在这里。" />
      <template #actions>
        <v-btn variant="outlined" @click="store.handleLogsOpen = false">关闭</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.i04-loading { display: flex; flex-direction: column; gap: 16px; }
.i04-main { display: flex; align-items: flex-start; gap: 16px; margin-top: 16px; }
.i04-left { flex: 1; min-width: 0; }
.i04-right { width: 380px; flex: 0 0 380px; }
.i04-steps { margin-bottom: 0; }
.i04-steps :deep(.zw-steps) { padding: 16px 20px; }
.i04-ai { width: 100%; }
.i04-conclusion { font-size: 14px; line-height: 22px; margin-bottom: 12px; }
.i04-ai-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.i04-ai-card { background: var(--bg-card); border-radius: var(--radius-sm); padding: 12px 16px; }
.i04-ai-card-title { font-size: 12px; line-height: 18px; color: var(--text-3); margin-bottom: 8px; }
.i04-cause { display: flex; align-items: flex-start; gap: 8px; padding: 8px 0; }
.i04-cause + .i04-cause { border-top: 1px solid var(--line-1); }
.i04-cause-rank { flex: 0 0 22px; width: 22px; height: 22px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: var(--tertiary-container); color: var(--on-tertiary-container); font-size: 12px; }
.i04-cause-main { flex: 1; min-width: 0; }
.i04-cause-name { display: flex; align-items: center; gap: 6px; font-size: 14px; color: var(--text-1); }
.i04-cause-bar { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.i04-cause-bar .v-progress-linear { width: 72px; flex: 0 0 72px; }
.i04-cause-level { font-size: 12px; color: var(--text-3); }
.i04-ai-empty { padding: 8px 0; font-size: 12px; color: var(--text-3); }
.i04-advices { margin: 0; padding-left: 18px; font-size: 14px; line-height: 22px; color: var(--text-1); }
.i04-advices li + li { margin-top: 4px; }
.i04-push { display: flex; align-items: center; gap: 6px; margin-top: 12px; font-size: 12px; color: var(--text-2); }
.i04-ai-pending { padding: 8px 0; }
.i04-ai-pending-title { font-size: 14px; font-weight: 500; margin-bottom: 8px; color: var(--on-tertiary-container); }
.i04-ai-pending-desc { font-size: 12px; color: var(--text-2); margin-top: 8px; }
.i04-link { color: var(--primary); cursor: pointer; }
.i04-handle-empty { display: flex; align-items: flex-start; gap: 10px; font-size: 14px; line-height: 22px; color: var(--text-2); }
.i04-handle-ico { width: 32px; height: 32px; flex: 0 0 32px; border-radius: var(--radius-sm); display: inline-flex; align-items: center; justify-content: center; background: var(--fill-2); color: var(--text-2); }
.i04-outcome { display: flex; gap: 6px; flex-wrap: wrap; }
.i04-outcome-bar { display: flex; align-items: center; gap: 10px; margin-top: 6px; }
.i04-outcome-bar .v-progress-linear { width: 200px; flex: 0 0 200px; }
.i04-outcome-bar span { font-size: 12px; color: var(--text-2); }
.i04-handle-note { margin-top: 12px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.i04-metrics { display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.i04-metric { display: flex; flex-direction: column; gap: 2px; padding: 10px 14px; }
.i04-metric + .i04-metric { border-left: 1px solid var(--line-1); }
.i04-metric-label { font-size: 12px; color: var(--text-3); }
.i04-metric-value { font-size: 20px; line-height: 28px; font-weight: 600; color: var(--text-1); }
.i04-metric-value small { font-size: 12px; font-weight: 400; color: var(--text-2); margin-left: 2px; }
.i04-symptoms { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.i04-desc { margin-top: 10px; font-size: 14px; line-height: 22px; color: var(--text-1); }
.i04-media { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 12px; }
.i04-thumb { position: relative; width: 100%; aspect-ratio: 4 / 3; padding: 0; border: 0; border-radius: var(--radius-sm); overflow: hidden; background: var(--fill-2); cursor: zoom-in; }
.i04-thumb img, .i04-thumb video { width: 100%; height: 100%; object-fit: cover; display: block; }
.i04-thumb-play { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 30px; height: 30px; border-radius: 50%; background: #00000073; color: #fff; display: inline-flex; align-items: center; justify-content: center; }
.i04-thumb-dur { position: absolute; left: 5px; bottom: 5px; padding: 0 4px; border-radius: 2px; background: #000000b3; color: #fff; font-size: 11px; line-height: 17px; }
.i04-report-foot { display: flex; align-items: center; gap: 4px; margin-top: 12px; font-size: 12px; color: var(--text-3); }
</style>
