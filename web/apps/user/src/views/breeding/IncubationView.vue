<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber } from '@zmt/shared'
import { useIncubationStore } from '@/stores/breeding/incubation'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { monthDay, monthDayWeek, todayISO } from '@/stores/breeding/shared'
import type { IncubationTimeline } from '@/api/breeding'
import BreedingDateField from './components/BreedingDateField.vue'

const store = useIncubationStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const { isStaff } = useRole()

const today = todayISO()
const isHatched = computed(() => store.isHatched)
const currentStep = computed(() => store.currentStep)
const selectedId = computed(() => Number(store.detail?.incubationId || store.selectedId || 0))
const desc = computed(() => store.titleDesc)

const miniCards = computed(() => {
  const detail = store.detail
  if (!detail) return []
  const cards = [
    { label: '入孵蛋数', value: store.setCountText, unit: '枚', foot: `${monthDay(detail.setDate)}入孵` },
    { label: '受精蛋数', value: store.fertileCountText, unit: '枚', foot: detail.candleDate ? `${monthDay(detail.candleDate)}照蛋` : '还没有照蛋' },
    { label: '受精率', value: store.fertilityRateText, unit: '', foot: detail.fertileCount !== null && detail.fertileCount !== undefined ? `${formatNumber(detail.fertileCount)} ÷ ${formatNumber(detail.setCount || 0)}` : '照蛋后计算' }
  ]
  if (isHatched.value) {
    cards.push({ label: '孵化率', value: store.hatchRateText, unit: '', foot: `${formatNumber(detail.hatchedCount || 0)} ÷ ${formatNumber(detail.setCount || 0)}` })
  } else {
    const days = Number(detail.daysToHatch || 0)
    cards.push({
      label: '预计出雏',
      value: days === 0 ? '今天' : days < 0 ? `超${Math.abs(days)}天` : `${days} 天`,
      unit: '',
      foot: `${monthDayWeek(detail.expectedHatchDate)} · ${detail.reminderText || '提前 1 天已提醒'}`
    })
  }
  return cards
})

const stepsSub = computed(() => {
  if (isHatched.value) return '3 步已全部完成'
  if (currentStep.value === 2) return '入孵已完成，正在第 2 步'
  if (currentStep.value === 3) return '入孵、照蛋已完成，正在第 3 步'
  return '登记入孵后，按入孵、照蛋、出雏三步推进'
})

const hatchRegistrant = computed(() => {
  const timeline = store.detail?.timeline || []
  const hatch = timeline.find((item: IncubationTimeline) => item.step === 'HATCH')
  return hatch?.registrant?.name || ''
})

const stepTitle = computed(() => {
  if (isHatched.value) return '出雏结果'
  if (currentStep.value === 2) return '照蛋登记'
  if (currentStep.value === 3) return '出雏登记'
  return currentStep.value === 1 ? '入孵登记' : '孵化记录'
})
const stepSub = computed(() => {
  if (isHatched.value) return `${monthDayWeek(store.detail?.hatchDate)}${hatchRegistrant.value ? ` · ${hatchRegistrant.value}登记` : ''}`
  if (currentStep.value === 2) return '第 2 步 · 照蛋后受精率实时更新'
  if (currentStep.value === 3) return '第 3 步 · 登记后出雏待办自动完成'
  return '按品种孵化期推算预计出雏日'
})

const dueSub = computed(() => `${farm.farmName || '当前养殖场'} · ${store.dueTotal} 批`)

const dueListSorted = computed(() => {
  return [...store.dueList].sort((a, b) => Number(a.daysToHatch || 0) - Number(b.daysToHatch || 0))
})

function stepClass(days?: number): string {
  return days === 0 ? 'b03-due-tag b03-due-tag--today' : 'b03-due-tag'
}

function dueTagText(days?: number): string {
  const value = Number(days ?? 0)
  if (value === 0) return '今天'
  if (value < 0) return `超${Math.abs(value)} 天`
  return `${value} 天`
}

function fieldError(errors: Record<string, string>, field: string): string {
  return errors[field] || ''
}

function selectIncubation(id: number): void {
  void store.selectIncubation(id)
  void router.replace({ query: { ...route.query, incubationId: String(id) } })
}

function openCreate(): void {
  void store.openCreate()
}

function openEdit(): void {
  void store.openEdit()
}

function openLogs(): void {
  void store.openLogs()
}

function submitChange(): void {
  if (!store.detail) return
  void router.push({
    name: 'WU-U01',
    query: {
      biz_type: 'INCUBATION',
      biz_id: String(store.detail.incubationId || ''),
      action: 'create'
    }
  })
}

function viewBatch(): void {
  const batchId = store.detail?.batchId
  if (!batchId) return
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function goBack(): void {
  void router.push({ name: 'WU-B02', query: { tab: 'hatch' } })
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) goBack()
}

function onHatchTransfer(value: unknown): void {
  store.hatchCapacity = null
  if (!value) store.hatchForm.capacityConfirmed = false
}

function closeForm(): void {
  store.formDrawer = false
}

function closeLogs(): void {
  store.logsDrawer = false
}

async function handleQuery(): Promise<void> {
  if (isStaff.value) return
  const action = String(route.query.action || '')
  if (action !== 'incubate' && action !== 'create') return
  await store.openCreate()
  const query = { ...route.query }
  delete query.action
  await router.replace({ query })
}

onMounted(async () => {
  if (isStaff.value) {
    store.status = 'forbidden'
    store.error = '你没有登记繁育记录的权限'
    return
  }
  const queryId = Number(route.query.incubationId || 0)
  if (queryId) store.selectedId = queryId
  await store.load()
  await handleQuery()
})

useFarmReload(async () => {
  const queryId = Number(route.query.incubationId || 0)
  store.reset()
  if (isStaff.value) {
    store.status = 'forbidden'
    store.error = '你没有登记繁育记录的权限'
    return
  }
  if (queryId) store.selectedId = queryId
  await store.load()
})

watch(() => route.query.incubationId, (value) => {
  const id = Number(value || 0)
  if (id && id !== selectedId.value) void store.selectIncubation(id)
})

watch(() => route.query.action, (value) => {
  if (value) void handleQuery()
})
</script>

<template>
  <zw-shell side="user" active="WU-B03" :crumbs="['生产管理', '繁育管理', '禽类孵化']">
    <zw-page-head back :title="'禽类孵化'" :desc="desc" @click="onHeadClick">
      <template #tags>
        <zw-tag type="farm" icon="domain">{{ farm.farmName || '当前养殖场' }}</zw-tag>
        <zw-tag v-if="store.detail" :type="store.statusTag.type" :icon="store.statusTag.icon || undefined">{{ store.statusTag.text }}</zw-tag>
      </template>
      <template #actions>
        <template v-if="!isStaff">
          <v-menu location="bottom end">
            <template #activator="{ props: activatorProps }">
              <v-btn v-bind="activatorProps" variant="outlined" prepend-icon="more_horiz">更多</v-btn>
            </template>
            <div class="zw-pop b03-more">
              <template v-if="!isHatched">
                <a class="zw-pop-item" @click="openEdit"><zw-icon name="edit" :size="18" />修改入孵 / 照蛋记录</a>
              </template>
              <template v-else>
                <a class="zw-pop-item" @click="submitChange"><zw-icon name="edit_note" :size="18" />提交修改申请<span class="b03-audit">需审核</span></a>
              </template>
              <a class="zw-pop-item" @click="openLogs"><zw-icon name="history" :size="18" />查看修改记录</a>
            </div>
          </v-menu>
          <v-btn variant="outlined" prepend-icon="add" @click="openCreate">登记入孵</v-btn>
        </template>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'forbidden'">
      <zw-card>
        <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '你没有登记繁育记录的权限'">
          <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
        </zw-empty>
      </zw-card>
    </div>

    <div v-else-if="store.status === 'loading' || store.detailLoading">
      <v-skeleton-loader type="article, table-row@6" />
    </div>

    <template v-else-if="store.status === 'empty'">
      <zw-card>
        <zw-empty icon="egg" :title="farm.farmName ? `${farm.farmName}没有孵化记录` : '还没有孵化记录'" desc="禽类养殖场登记入孵后，会按预计出雏日出现在这里。">
          <v-btn color="primary" @click="openCreate">登记入孵</v-btn>
        </zw-empty>
      </zw-card>
    </template>

    <template v-else-if="store.status === 'error'">
      <zw-card>
        <zw-empty icon="cloud_off" tone="error" title="孵化记录加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
        </zw-empty>
      </zw-card>
    </template>

    <template v-else-if="store.detail">
      <div class="b03-layout">
        <div class="b03-main">
          <zw-card title="孵化进度" :sub="stepsSub">
            <zw-steps :items="store.steps" />
            <div class="zw-grid-4 mt-6">
              <div v-for="card in miniCards" :key="card.label" class="b03-mini">
                <div class="b03-mini-l">{{ card.label }}</div>
                <div class="b03-mini-v num">{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></div>
                <div class="b03-mini-f">{{ card.foot }}</div>
              </div>
            </div>
          </zw-card>

          <zw-card :title="stepTitle" :sub="stepSub">
            <!-- 出雏结果 -->
            <template v-if="isHatched">
              <zw-desc :cols="3" bordered :label-width="96">
                <zw-desc-item label="出雏日期">{{ monthDayWeek(store.detail.hatchDate) }}</zw-desc-item>
                <zw-desc-item label="出雏数"><span class="num">{{ formatNumber(store.detail.hatchedCount || 0) }} 只</span></zw-desc-item>
                <zw-desc-item label="孵化率"><span class="num">{{ store.hatchRateText }}</span></zw-desc-item>
                <zw-desc-item label="健雏数"><span class="num">{{ formatNumber(store.detail.healthyCount || 0) }} 只</span></zw-desc-item>
                <zw-desc-item label="弱雏数"><span class="num">{{ formatNumber(store.detail.weakCount || 0) }} 只</span></zw-desc-item>
                <zw-desc-item label="入栏鸡舍">{{ store.detail.targetPenName || '—' }}</zw-desc-item>
                <zw-desc-item label="新批次" :span="3">
                  <template v-if="store.detail.batchNo">
                    <a class="zw-link num" @click="viewBatch">{{ store.detail.batchNo }}</a>
                    <span class="zw-faint"> · {{ store.detail.offspringBreedName || store.detail.breedName }} · 入栏日龄 0 · 来源自繁 · {{ formatNumber(store.detail.healthyCount || 0) }} 只</span>
                  </template>
                  <span v-else class="zw-faint">健雏没有转为新批次</span>
                </zw-desc-item>
              </zw-desc>
              <zw-notice type="info" class="mt-4">健雏已转为新批次，入栏日龄 0、来源自繁。出雏记录要修改时，在“更多”中提交修改申请，审核通过后系统重新计算批次存栏。</zw-notice>
            </template>

            <!-- 照蛋登记 -->
            <template v-else-if="currentStep === 2">
              <div class="zw-form-2">
                <zw-form-item label="照蛋日期" required :error="fieldError(store.candleErrors, 'candle_date')">
                  <BreedingDateField v-model="store.candleForm.candleDate" :max="today" :min="store.detail.setDate || ''" :error="!!fieldError(store.candleErrors, 'candle_date')" />
                </zw-form-item>
                <zw-form-item label="受精蛋数" required :error="fieldError(store.candleErrors, 'fertile_count')" :hint="`不能多于入孵蛋数 ${formatNumber(store.detail.setCount || 0)} 枚 · 受精率 ${store.fertilityRateText}`">
                  <v-text-field v-model.number="store.candleForm.fertileCount" type="number" min="1" :max="store.detail.setCount" suffix="枚" :error="!!fieldError(store.candleErrors, 'fertile_count')" />
                </zw-form-item>
              </div>
              <zw-notice type="info" icon="notifications" class="mt-4">预计出雏日提前 1 天和当天各提醒一次。照蛋后再登记受精蛋数。</zw-notice>
            </template>

            <!-- 出雏登记 -->
            <template v-else-if="currentStep >= 3">
              <div class="zw-form-3">
                <zw-form-item label="出雏日期" required style="grid-column: 1 / -1" :error="fieldError(store.hatchErrors, 'hatch_date')">
                  <BreedingDateField v-model="store.hatchForm.hatchDate" :max="today" :min="store.detail.candleDate || store.detail.setDate || ''" :error="!!fieldError(store.hatchErrors, 'hatch_date')" />
                </zw-form-item>
                <zw-form-item label="出雏数" required :error="fieldError(store.hatchErrors, 'hatched_count')" :hint="`不能多于受精蛋数 ${formatNumber(store.detail.fertileCount || 0)} 枚 · 孵化率 ${store.hatchRatePreview === null ? '—' : `${(store.hatchRatePreview * 100).toFixed(1)}%`}`">
                  <v-text-field v-model.number="store.hatchForm.hatchedCount" type="number" min="1" :max="store.detail.fertileCount" suffix="只" :error="!!fieldError(store.hatchErrors, 'hatched_count')" />
                </zw-form-item>
                <zw-form-item label="健雏数" required :error="fieldError(store.hatchErrors, 'healthy_count')">
                  <v-text-field v-model.number="store.hatchForm.healthyCount" type="number" min="0" :max="store.hatchForm.hatchedCount || undefined" suffix="只" :error="!!fieldError(store.hatchErrors, 'healthy_count')" />
                </zw-form-item>
                <zw-form-item label="弱雏数" hint="按出雏减健雏自动计算">
                  <div class="zw-readonly num">{{ store.weakCount === null ? '—' : `${formatNumber(store.weakCount)} 只` }}</div>
                </zw-form-item>
              </div>

              <div class="b03-batch">
                <div class="b03-batch-head">
                  <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="add_box" :size="18" /></span>
                  <div>
                    <div class="title-s">健雏转为新批次</div>
                    <div class="body-s zw-faint">{{ formatNumber(store.hatchForm.healthyCount || 0) }} 只 · 入栏日龄 0 · 来源自繁</div>
                  </div>
                  <v-switch v-model="store.hatchForm.transferToBatch" color="primary" hide-details inset @update:model-value="onHatchTransfer" />
                </div>
                <div v-if="store.hatchForm.transferToBatch" class="b03-batch-body">
                  <zw-form-item label="入栏鸡舍" required :error="fieldError(store.hatchErrors, 'target_pen_id')" :hint="`${store.detail.offspringBreedName || store.detail.breedName} · 批次号提交后由系统生成`">
                    <v-select v-model="store.hatchForm.targetPenId" :items="store.penItems" item-title="title" item-value="value" placeholder="请选择入栏鸡舍" :error="!!fieldError(store.hatchErrors, 'target_pen_id')">
                      <template #item="{ props: itemProps, item }">
                        <v-list-item v-bind="itemProps" :subtitle="item.raw.hint" />
                      </template>
                    </v-select>
                  </zw-form-item>
                  <div v-if="store.hatchCapacityInfo" class="b03-cap">
                    <zw-icon name="fence" :size="16" />{{ store.hatchCapacityInfo.penName }}现有 {{ formatNumber(store.hatchCapacityInfo.currentCount) }} 只 · 容量 {{ formatNumber(store.hatchCapacityInfo.capacity) }} 只，入栏后共 {{ formatNumber(store.hatchCapacityInfo.afterCount) }} 只
                  </div>
                </div>
              </div>

              <zw-notice v-if="store.hatchCapacityInfo && store.hatchCapacityInfo.overCount > 0" type="warning" class="mt-4">
                <b>入栏后超过栏舍容量。</b>{{ store.hatchCapacityInfo.penName }}容量 {{ formatNumber(store.hatchCapacityInfo.capacity) }} 只，现有 {{ formatNumber(store.hatchCapacityInfo.currentCount) }} 只，入栏后共 {{ formatNumber(store.hatchCapacityInfo.afterCount) }} 只，超出 {{ formatNumber(store.hatchCapacityInfo.overCount) }} 只。
              </zw-notice>
              <v-checkbox v-if="store.hatchCapacityInfo && store.hatchCapacityInfo.overCount > 0" v-model="store.hatchForm.capacityConfirmed" label="我已确认，仍要入栏" density="compact" hide-details class="mt-2" />
              <div v-if="store.hatchErrors.capacity" class="b03-err"><zw-icon name="error" :size="16" fill />{{ store.hatchErrors.capacity }}</div>
              <div v-if="store.hatchErrors.transfer_to_batch" class="b03-err"><zw-icon name="error" :size="16" fill />{{ store.hatchErrors.transfer_to_batch }}</div>

            </template>

            <!-- 入孵 / 尚无步骤 -->
            <template v-else>
              <zw-empty icon="egg" title="正在等待照蛋" desc="入孵已登记，完成照蛋后在这里登记出雏。">
                <v-btn variant="outlined" @click="openEdit">修改入孵记录</v-btn>
              </zw-empty>
            </template>

            <template #foot>
              <template v-if="currentStep === 2">
                <span class="zw-foot-note">带 * 为必填 · 受精率 = 受精蛋数 ÷ 入孵蛋数</span>
                <v-btn color="primary" :loading="store.candleSubmitting" @click="store.submitCandling()">登记照蛋</v-btn>
              </template>
              <template v-else-if="currentStep >= 3 && !isHatched">
                <span class="zw-foot-note">带 * 为必填 · 出雏数、健雏数修改后孵化率和弱雏数实时更新</span>
                <v-btn color="primary" :loading="store.hatchSubmitting" @click="store.submitHatch()">登记出雏</v-btn>
              </template>
              <template v-else-if="isHatched">
                <span class="zw-foot-note">出雏记录已登记，历史记录保留</span>
                <v-btn v-if="store.detail.batchNo" color="primary" @click="viewBatch">查看批次</v-btn>
              </template>
              <template v-else>
                <span class="zw-foot-note">入孵已登记，接下来登记照蛋</span>
                <v-btn variant="outlined" @click="openEdit">修改入孵记录</v-btn>
              </template>
            </template>
          </zw-card>
        </div>

        <div class="b03-side">
          <zw-card title="待出雏" :sub="dueSub">
            <template #extra>
              <a class="zw-link body-s" @click="store.load(true)">刷新</a>
            </template>
            <div v-if="!dueListSorted.length" class="b03-side-empty">
              <span class="zw-faint">当前养殖场没有待出雏的批次。</span>
            </div>
            <div
              v-for="item in dueListSorted"
              :key="item.incubationId"
              class="b03-due"
              :class="{ on: Number(item.incubationId) === selectedId }"
              @click="item.incubationId && selectIncubation(Number(item.incubationId))"
            >
              <span class="zw-ico-tile" :class="Number(item.daysToHatch) <= 0 ? 'zw-ico-tile--warning' : null"><zw-icon name="egg" :size="18" /></span>
              <div>
                <div class="b03-due-t">
                  <span class="zw-cell-main num">{{ item.flockText }}</span>
                  <span :class="stepClass(Number(item.daysToHatch))">{{ dueTagText(Number(item.daysToHatch)) }}</span>
                </div>
                <div class="zw-cell-sub">{{ monthDay(item.setDate) }}入孵 · 入孵 {{ formatNumber(item.setCount || 0) }} 枚 · 受精蛋 {{ item.fertileCount === undefined || item.fertileCount === null ? '未照蛋' : `${formatNumber(item.fertileCount)} 枚` }}</div>
                <div class="zw-cell-sub">预计出雏 {{ monthDayWeek(item.expectedHatchDate) }}</div>
              </div>
            </div>
          </zw-card>

          <zw-card title="登记记录">
            <v-skeleton-loader v-if="store.detailLoading" type="list-item-two-line@2" />
            <zw-timeline v-else-if="store.timelineItems.length" :items="store.timelineItems" />
            <div v-else class="b03-side-empty"><span class="zw-faint">还没有登记记录</span></div>
          </zw-card>
        </div>
      </div>
    </template>

    <!-- 登记入孵 / 修改孵化记录 -->
    <zw-drawer v-model="store.formDrawer" :title="store.formMode === 'create' ? '登记入孵' : '修改孵化记录'" :sub="`${farm.farmName || '当前养殖场'} · ${store.formMode === 'create' ? '第 1 步，预计出雏日按品种孵化期推算' : '入孵与照蛋信息可直接修改'}`" :width="560" @update:model-value="(value: unknown) => { if (!value) closeForm() }">
      <zw-steps small :items="store.formMode === 'create'
        ? [{ title: '入孵', status: 'current' }, { title: '照蛋', status: 'wait' }, { title: '出雏', status: 'wait' }]
        : store.steps" />
      <div class="zw-form-2 mt-6">
        <zw-form-item label="入孵日期" required :error="fieldError(store.formErrors, 'set_date')">
          <BreedingDateField v-model="store.form.setDate" :max="today" :error="!!fieldError(store.formErrors, 'set_date')" />
        </zw-form-item>
        <zw-form-item label="种禽群" required :error="fieldError(store.formErrors, 'breeding_batch_id')" :hint="store.formMode === 'edit' ? '种禽群入孵后不能修改' : '列出本场用途为种用的在栏禽类批次'">
          <v-select v-model="store.form.breedingBatchId" :items="store.batchItems" item-title="title" item-value="value" :disabled="store.formMode === 'edit'" :error="!!fieldError(store.formErrors, 'breeding_batch_id')">
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps" :subtitle="item.raw.hint" />
            </template>
          </v-select>
        </zw-form-item>
        <zw-form-item label="入孵蛋数" required :error="fieldError(store.formErrors, 'set_count')" hint="整数，单位为枚">
          <v-text-field v-model.number="store.form.setCount" type="number" min="1" suffix="枚" :error="!!fieldError(store.formErrors, 'set_count')" />
        </zw-form-item>
        <template v-if="store.formMode === 'edit'">
          <zw-form-item label="照蛋日期" :error="fieldError(store.formErrors, 'candle_date')" optional>
            <BreedingDateField v-model="store.form.candleDate" :max="today" :min="store.form.setDate" :error="!!fieldError(store.formErrors, 'candle_date')" />
          </zw-form-item>
          <zw-form-item label="受精蛋数" :error="fieldError(store.formErrors, 'fertile_count')" optional>
            <v-text-field v-model.number="store.form.fertileCount" type="number" min="0" :max="store.form.setCount || undefined" suffix="枚" :error="!!fieldError(store.formErrors, 'fertile_count')" />
          </zw-form-item>
        </template>
      </div>
      <div v-if="store.formMode === 'create' && store.predictedHatchDate" class="b03-pred">
        <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="event" :size="18" /></span>
        <div>
          <div class="body-s zw-muted">预计出雏日</div>
          <div class="b03-pred-date num">{{ monthDayWeek(store.predictedHatchDate) }}</div>
          <div class="body-s zw-faint">按{{ store.selectedBatch?.breed_name || '品种' }}孵化期 {{ store.predictedHatchDays }} 天推算</div>
        </div>
      </div>
      <zw-notice type="info" icon="notifications" class="mt-4">预计出雏日提前 1 天和当天各提醒一次，发给所有者和场长。照蛋后再登记受精蛋数。</zw-notice>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="closeForm">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" @click="store.submitForm()">{{ store.formMode === 'create' ? '登记入孵' : '保存修改' }}</v-btn>
      </template>
    </zw-drawer>

    <zw-drawer v-model="store.logsDrawer" :title="store.logsTitle" width="560" @update:model-value="(value: unknown) => { if (!value) closeLogs() }">
      <v-skeleton-loader v-if="store.logsLoading" type="list-item-two-line@3" />
      <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
      <zw-empty v-else icon="history" title="还没有修改记录" desc="修改入孵、照蛋或出雏后，都会记录在这里。" />
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.b03-more { width: 248px; }
.b03-audit { margin-left: auto; font-size: 12px; color: var(--text-3); }
.b03-layout { display: flex; gap: 16px; align-items: flex-start; }
.b03-main { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; gap: 16px; }
.b03-side { width: 340px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.b03-mini { padding: 12px 16px; border-radius: var(--radius-sm); background: var(--fill-1); }
.b03-mini-l { font-size: 12px; line-height: 20px; color: var(--text-2); }
.b03-mini-v { margin-top: 2px; font-size: 20px; line-height: 28px; font-weight: 600; color: var(--text-1); }
.b03-mini-v small { margin-left: 4px; font-size: 13px; font-weight: 400; color: var(--text-2); }
.b03-mini-f { font-size: 12px; line-height: 18px; color: var(--text-3); }
.b03-due { display: flex; align-items: flex-start; gap: 12px; padding: 12px 14px; border-radius: var(--radius-sm); cursor: pointer; }
.b03-due + .b03-due { margin-top: 4px; }
.b03-due.on { background: var(--primary-subtle); }
.b03-due:hover { background: var(--fill-1); }
.b03-due > div { flex: 1; min-width: 0; }
.b03-due-t { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.b03-due-tag { font-size: 12px; color: var(--text-3); white-space: nowrap; }
.b03-due-tag--today { color: var(--warning); font-weight: 600; }
.b03-side-empty { padding: 12px 0; text-align: center; }
.b03-pred { display: flex; gap: 12px; align-items: flex-start; margin-top: 20px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--fill-1); border: 1px solid var(--line-1); }
.b03-pred-date { font-size: 20px; line-height: 28px; font-weight: 600; margin: 2px 0; }
.b03-batch { margin-top: 20px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.b03-batch-head { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid var(--line-1); }
.b03-batch-head > div { flex: 1; }
.b03-batch-body { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 24px; align-items: start; padding: 14px 16px; }
.b03-cap { display: flex; align-items: center; gap: 6px; margin-top: 33px; font-size: 13px; color: var(--text-2); }
.b03-err { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--error); display: flex; align-items: center; gap: 4px; }
</style>
