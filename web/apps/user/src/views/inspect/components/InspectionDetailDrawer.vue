<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatDateTime, formatNumber } from '@zmt/shared'
import type { InspectionDetail, MediaItem, RecordLog } from '@/api/inspect'
import { dateWithWeek, locationLabel, mediaDuration, mediaIsVideo } from '../inspect'
import MediaPreviewDialog, { type PreviewItem } from './MediaPreviewDialog.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    detail: InspectionDetail | null
    loading?: boolean
    error?: string
    logs?: RecordLog[]
    logsLoading?: boolean
  }>(),
  {
    loading: false,
    error: '',
    logs: () => [],
    logsLoading: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  modify: []
  changeRequest: []
}>()

const previewOpen = ref(false)
const previewIndex = ref(0)

const media = computed(() => (props.detail?.media || []) as MediaItem[])
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
const subTitle = computed(() => {
  const detail = props.detail
  if (!detail) return ''
  const parts = [detail.pen_name || '', ...(detail.batch_nos || [])]
  if (detail.inspect_date) parts.push(dateWithWeek(detail.inspect_date))
  parts.push('只读')
  return parts.filter(Boolean).join(' · ')
})
const canModify = computed(() => props.detail?.edit_mode === 'DIRECT')
const canChangeRequest = computed(() => props.detail?.edit_mode === 'CHANGE_REQUEST')
const manualMarked = computed(() => !!props.detail?.is_manual_marked)
const isProxy = computed(() => !!props.detail?.is_proxy)
const recordTag = computed(() => {
  if (!props.detail) return ''
  if (props.detail.is_backfill) return '补录'
  if (props.detail.is_abnormal) return '异常'
  if (props.detail.is_overdue) return '超时'
  return '正常'
})

function openPreview(index: number): void {
  previewIndex.value = index
  previewOpen.value = true
}
</script>

<template>
  <zw-drawer :model-value="modelValue" title="巡检详情" :sub="subTitle" width="560" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="loading" class="detail-loading">
      <v-skeleton-loader type="heading" />
      <v-skeleton-loader type="paragraph@3" />
      <v-skeleton-loader type="list-item-three-line@4" />
    </div>
    <zw-notice v-else-if="error" type="warning" title="巡检详情加载失败">{{ error }}</zw-notice>
    <template v-else-if="detail">
      <zw-desc :cols="2" bordered :label-width="88">
        <zw-desc-item label="登记人">
          {{ detail.registrant?.name || '—' }}<template v-if="detail.registrant?.role_text">（{{ detail.registrant.role_text }}）</template>
        </zw-desc-item>
        <zw-desc-item label="登记时间">{{ detail.registered_at ? formatDateTime(detail.registered_at) : '—' }}</zw-desc-item>
        <zw-desc-item label="实际执行人">
          {{ detail.executor?.name || '—' }}<span v-if="isProxy" class="detail-proxy"> · 代登记</span>
        </zw-desc-item>
        <zw-desc-item label="记录标注">
          <zw-tag v-if="detail.is_abnormal" type="error" :icon="detail.abnormal_source === 'MANUAL' ? 'flag' : 'error'" size="small">{{ recordTag }}</zw-tag>
          <zw-tag v-else-if="detail.is_backfill" size="small">补录</zw-tag>
          <zw-tag v-else-if="detail.is_overdue" size="small">超时</zw-tag>
          <span v-else class="zw-faint">正常</span>
        </zw-desc-item>
        <zw-desc-item label="位置" :span="2">
          <span :class="{ 'detail-warn': detail.location_status === 'OFF_SITE' }">{{ locationLabel(detail.location_status, detail.distance_m) }}</span>
        </zw-desc-item>
      </zw-desc>

      <div class="detail-section-title">巡检项目</div>
      <div class="detail-items">
        <div v-for="item in detail.item_results || []" :key="item.item_id" class="detail-item">
          <span>{{ item.item_name }}</span>
          <zw-tag :type="item.option_abnormal ? 'error' : 'neutral'" :icon="item.option_abnormal ? 'error' : 'check'" size="small">
            {{ item.option_name }}
          </zw-tag>
        </div>
        <div v-if="!detail.item_results?.length" class="detail-empty">没有巡检项目结果</div>
      </div>

      <div v-if="manualMarked" class="detail-manual">
        <zw-icon name="flag" :size="18" fill />
        <div>
          <strong>手动标记异常</strong>
          <span> · {{ detail.manual_reason || '未填写原因' }}</span>
          <div class="detail-manual-by">
            {{ detail.manual_marked_by_name || '' }}<template v-if="detail.manual_marked_at"> {{ formatDateTime(detail.manual_marked_at) }}</template>
          </div>
        </div>
      </div>

      <div class="detail-section-title">说明</div>
      <div class="detail-text">{{ detail.remark || '未填写' }}</div>

      <div class="detail-section-title">照片和视频</div>
      <div v-if="media.length" class="detail-media">
        <button
          v-for="(item, index) in media"
          :key="item.file_id"
          class="detail-thumb"
          type="button"
          @click="openPreview(index)"
        >
          <img v-if="!mediaIsVideo(item)" :src="item.thumb_url || item.url" alt="">
          <video v-else :src="item.url" preload="metadata" muted />
          <span v-if="mediaIsVideo(item)" class="detail-thumb-play"><zw-icon name="play_arrow" :size="18" fill /></span>
          <span v-if="mediaIsVideo(item) && mediaDuration(item)" class="detail-thumb-dur">{{ mediaDuration(item) }}</span>
        </button>
      </div>
      <div v-else class="detail-text">没有照片和视频</div>

      <div class="detail-section-title">修改记录</div>
      <div v-if="logsLoading" class="detail-text">加载中…</div>
      <zw-timeline
        v-else-if="logs.length"
        :items="logs.map((log) => ({
          title: `${log.operator?.name || '—'} ${log.op_action_text || '修改'}`,
          time: log.created_at ? formatDateTime(log.created_at) : '',
          desc: (log.changes || []).map((change) => `${change.field_name || ''}：${change.before || '—'} → ${change.after || '—'}`).join('；'),
          tone: 'primary',
          icon: 'history'
        }))"
      />
      <div v-else class="detail-text">没有修改记录</div>

      <div class="detail-rule">
        <zw-icon name="info" :size="16" />
        <span v-if="canChangeRequest">这条记录已超过 3 天，修改需要提交修改申请，管理员审核通过后才会修改。审核期间原记录照常有效。</span>
        <span v-else-if="canModify">今天和前 3 天内的记录可以直接修改，系统保留修改记录。</span>
        <span v-else>这条记录当前不能修改。</span>
      </div>
    </template>

    <template #foot>
      <span class="detail-foot-note">共 {{ formatNumber(detail?.item_results?.length || 0) }} 项巡检结果</span>
      <v-btn variant="outlined" @click="emit('update:modelValue', false)">关闭</v-btn>
      <v-btn v-if="canChangeRequest" color="primary" prepend-icon="edit_note" @click="emit('changeRequest')">提交修改申请</v-btn>
      <v-btn v-else-if="canModify" color="primary" prepend-icon="edit" @click="emit('modify')">修改巡检</v-btn>
    </template>

    <MediaPreviewDialog v-model="previewOpen" :items="previewItems" :start-index="previewIndex" source-label="巡检" />
  </zw-drawer>
</template>

<style scoped>
.detail-loading { display: flex; flex-direction: column; gap: 12px; }
.detail-proxy { color: var(--text-3); font-size: 12px; }
.detail-warn { color: var(--warning); }
.detail-section-title { font-size: 14px; font-weight: 600; color: var(--text-1); margin: 20px 0 8px; }
.detail-items { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.detail-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 44px; padding: 0 16px; font-size: 14px; }
.detail-item + .detail-item { border-top: 1px solid var(--line-1); }
.detail-empty { padding: 12px 16px; font-size: 12px; color: var(--text-3); }
.detail-manual { display: flex; align-items: flex-start; gap: 8px; margin-top: 12px; padding: 10px 14px; border-radius: var(--radius-sm); background: var(--error-container); color: var(--on-error-container); font-size: 13px; line-height: 20px; }
.detail-manual-by { font-size: 12px; color: var(--on-error-container); opacity: .8; margin-top: 2px; }
.detail-text { font-size: 14px; line-height: 22px; color: var(--text-1); }
.detail-media { display: flex; flex-wrap: wrap; gap: 8px; }
.detail-thumb { position: relative; width: 104px; height: 78px; padding: 0; border: 0; border-radius: var(--radius-sm); overflow: hidden; background: var(--fill-2); cursor: zoom-in; }
.detail-thumb img, .detail-thumb video { width: 100%; height: 100%; object-fit: cover; display: block; }
.detail-thumb-play { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 30px; height: 30px; border-radius: 50%; background: #00000073; color: #fff; display: inline-flex; align-items: center; justify-content: center; }
.detail-thumb-dur { position: absolute; left: 5px; bottom: 5px; padding: 0 4px; border-radius: 2px; background: #000000b3; color: #fff; font-size: 11px; line-height: 17px; }
.detail-rule { display: flex; align-items: flex-start; gap: 6px; margin-top: 20px; padding: 9px 14px; border-radius: var(--radius-sm); background: var(--primary-subtle); color: var(--text-1); font-size: 12px; line-height: 18px; }
.detail-rule .zw-mso { color: var(--primary); margin-top: 1px; }
.detail-foot-note { margin-right: auto; font-size: 12px; color: var(--text-3); }
</style>
