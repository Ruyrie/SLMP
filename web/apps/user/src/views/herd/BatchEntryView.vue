<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate, formatNumber, formatPercent } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import { useHerdEntryStore, type EntrySource, type HerdActionTab } from '@/stores/herd/entry'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { useFarmReload } from '@/composables/useFarmReload'
import { consumeRouteAction, queryId } from '@/composables/useRouteAction'
import { useRole } from '@/composables/useRole'
import { dateText, occupancyPercent, sourceText, todayISO } from '@/stores/herd/shared'
import HerdDateField from './components/HerdDateField.vue'
import HerdPhotoUpload from './components/HerdPhotoUpload.vue'
import { downloadQrLabel, printQrLabel } from './qr'

const store = useHerdEntryStore()
const farm = useFarmStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()
const { isStaff } = useRole()
const photoBusy = ref(false)
const entryQrBox = ref<HTMLElement | null>(null)

const activeTab = computed({
  get: () => store.tab,
  set: (value: HerdActionTab) => {
    if (isStaff.value && value !== 'death') return
    store.setTab(value)
  }
})

const desc = computed(() => {
  const name = farm.farmName || '当前养殖场'
  return isStaff.value
    ? `${name} · 登记死淘后，存栏、累计死淘和死亡率同步更新`
    : `${name} · 入栏生成批次号与二维码，转栏和死淘登记后同步更新存栏`
})

const primaryAction = computed(() => {
  if (store.tab === 'transfer') return { label: '转栏登记', icon: 'swap_horiz', action: openTransferFromPage }
  if (store.tab === 'death') return { label: '登记死淘', icon: 'heart_minus', action: openDeathFromPage }
  return { label: '批次入栏', icon: 'move_to_inbox', action: store.openEntry }
})

const transferBatchId = computed({
  get: () => store.transferContext.batchId,
  set: (value: number) => {
    void store.selectTransferBatch(Number(value || 0))
  }
})
const deathBatchId = computed({
  get: () => store.deathContext.batchId,
  set: (value: number) => {
    void store.selectDeathBatch(Number(value || 0))
  }
})

const batchSelectItems = computed(() => store.batches.map((batch) => ({
  title: batch.batch_no || '',
  value: Number(batch.batch_id || 0),
  subtitle: `${batch.breed_title || batch.breed_name || ''} · 在栏 ${formatNumber(batch.current_count || 0)} ${batch.unit_text || '头'}`
})))

const sourceItems = [
  { label: '外购', value: 'PURCHASED' },
  { label: '自繁', value: 'SELF_BRED' }
]
const purposeItems = [
  { label: '商品', value: 'COMMERCIAL' },
  { label: '种用', value: 'BREEDING' }
]
const reasonItems = [
  { label: '病死', value: 'SICK' },
  { label: '淘汰', value: 'CULL' },
  { label: '意外死亡', value: 'ACCIDENT' },
  { label: '其他', value: 'OTHER' }
]
const batchHeaders: DataTableHeader[] = [
  { title: '批次号', key: 'batch_no', sortable: false, width: 138 },
  { title: '品种', key: 'breed', sortable: false },
  { title: '栏舍', key: 'pen', sortable: false },
  { title: '日龄', key: 'day_age', sortable: false, width: 100 },
  { title: '存栏', key: 'current_count', sortable: false, align: 'end', width: 96 },
  { title: '死亡率', key: 'mortality', sortable: false, align: 'end', width: 84 },
  { title: '状态', key: 'status', sortable: false, width: 92 },
  { title: '操作', key: 'actions', sortable: false, align: 'end', width: 176 }
]

const selectedTransferBatch = computed(() => store.transferContext.batch)
const selectedDeathBatch = computed(() => store.deathContext.batch)
const transferQuantityMessage = computed(() => {
  if (store.transferErrors.quantity) return store.transferErrors.quantity
  const quantity = Number(store.transferForm.quantity || 0)
  if (quantity > store.transferFromStock) {
    return `不能超过转出栏舍的在栏数 ${store.transferFromStock} ${store.transferUnit}`
  }
  return ''
})
const deathQuantityMessage = computed(() => {
  if (store.deathErrors.quantity) return store.deathErrors.quantity
  const quantity = Number(store.deathForm.quantity || 0)
  if (quantity > store.deathStock) {
    return `不能超过${deathPen.value?.pen_name || '栏舍'}的在栏数 ${store.deathStock} ${store.deathUnit}`
  }
  return ''
})
const entryPen = computed(() => store.entrySelectedPen)
const entryCategory = computed(() => store.entryCategory)
const entryBreedOptions = computed(() => store.entryBreedOptions)
const entryOver = computed(() => store.entryOver)
const entryCanSubmit = computed(() => !store.submitting && !(entryOver.value && !store.entryForm.capacityConfirmed))
const transferOver = computed(() => store.transferOver)
const transferCanSave = computed(() => {
  if (!selectedTransferBatch.value) return false
  if (transferOver.value && !store.transferForm.capacityConfirmed) return false
  return true
})
const transferDestination = computed(() => store.penOptions.find((pen) => Number(pen.pen_id) === store.transferForm.toPenId))
const deathPen = computed(() => store.deathContext.batch?.pens?.find((pen) => Number(pen.pen_id) === store.deathForm.penId))
const entryPriceVisible = computed(() => store.entryForm.source === 'PURCHASED')
const entryUnit = computed(() => store.entryUnit)

function parseTab(value: unknown): HerdActionTab {
  const text = Array.isArray(value) ? String(value[0] || '') : String(value || '')
  if (text === 'transfer' || text === 'death' || text === 'entry') return text
  return 'entry'
}

function batchIdFromQuery(): number {
  return queryId(route.query, 'batchId', 'batch_id')
}

/** 没指定批次时默认第一个在养批次，抽屉里还能改选；没有在养批次时提示。 */
function defaultBatchId(selected?: number): number {
  return Number(selected || store.batches[0]?.batch_id || 0)
}

function openTransferFromPage(): void {
  if (!selectedTransferBatch.value) {
    ui.toast('请先选择一个在养批次', 'info')
    return
  }
  void store.openTransfer(selectedTransferBatch.value.batch_id)
}

function openDeathFromPage(): void {
  if (!selectedDeathBatch.value) {
    ui.toast('请先选择一个在养批次', 'info')
    return
  }
  void store.openDeath(selectedDeathBatch.value.batch_id)
}

function selectTransferFor(batchId?: number): void {
  if (!batchId) return
  void store.selectTransferBatch(batchId)
}

function selectDeathFor(batchId?: number): void {
  if (!batchId) return
  store.setTab('death')
  void store.selectDeathBatch(batchId)
}

function openBatch(batchId?: number): void {
  if (!batchId) return
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function onEntryDate(value: string): void {
  store.entryForm.entryDate = value
  delete store.entryErrors.entry_date
}

function onTransferDate(value: string): void {
  store.transferForm.eventDate = value
  delete store.transferErrors.event_date
}

function onDeathDate(value: string): void {
  store.deathForm.eventDate = value
  delete store.deathErrors.event_date
}

function onEntryPen(penId: number): void {
  store.chooseEntryPen(Number(penId || 0))
}

function onEntryBreed(breedId: number): void {
  store.chooseEntryBreed(Number(breedId || 0))
}

function onEntrySource(value: string): void {
  store.setEntrySource(value as EntrySource)
}

function onEntryPrice(value: string | number): void {
  store.entryForm.introUnitPrice = value === '' ? null : Number(value)
  delete store.entryErrors.intro_unit_price
}

function onTransferFrom(value: number): void {
  store.selectTransferFromPen(Number(value || 0))
}

function onTransferTo(value: number): void {
  store.selectTransferToPen(Number(value || 0))
}

function onDeathBatch(value: number): void {
  void store.selectDeathBatch(Number(value || 0))
}

function onDeathPen(value: number): void {
  void store.selectDeathPen(Number(value || 0))
}

const entryQrContent = computed(() => String(store.entryResult?.qr_content || store.entryResult?.batch_no || '').trim())

async function exportEntryQr(): Promise<void> {
  const result = store.entryResult
  if (!result || !entryQrContent.value) return
  try {
    await downloadQrLabel(entryQrContent.value, {
      batchNo: result.batch_no || '',
      label: result.label_text || `${result.farm_name} · ${result.pen_name} · ${result.breed_name}`,
      filename: `${result.batch_no || 'batch'}.png`
    })
    ui.toast('二维码已下载')
  } catch (error) {
    ui.toast(error instanceof Error ? error.message : '下载失败，请重试', 'error')
  }
}

async function printEntryQr(): Promise<void> {
  const result = store.entryResult
  if (!result || !entryQrContent.value) return
  try {
    await printQrLabel(entryQrContent.value, {
      batchNo: result.batch_no || '',
      label: result.label_text || `${result.farm_name} · ${result.pen_name} · ${result.breed_name}`
    })
  } catch (error) {
    ui.toast(error instanceof Error ? error.message : '打印失败，请重试', 'error')
  }
}

function viewEntryBatch(): void {
  const id = store.entryResult?.batch_id
  store.entrySuccess = false
  if (id) void router.push({ name: 'WU-L04', params: { batchId: String(id) } })
}

async function submitEntry(): Promise<void> {
  if (!store.canSubmitEntry) return
  await store.submitEntry()
}

async function submitTransfer(): Promise<void> {
  if (!store.canSubmitTransfer) return
  await store.submitTransfer()
}

async function submitDeath(): Promise<void> {
  if (photoBusy.value) {
    ui.toast('照片还在上传，请稍候', 'info')
    return
  }
  await store.submitDeath()
}

async function applyRouteQuery(): Promise<void> {
  const queryTab = parseTab(route.query.tab)
  store.setTab(isStaff.value ? 'death' : queryTab)
  const batchId = batchIdFromQuery()
  const create = route.query.action === 'create'
  // 先去掉 action 再开抽屉（临时抽屉在路由变化时会自动关闭）
  const penId = queryId(route.query, 'penId', 'pen_id')
  if (create) await consumeRouteAction(route, router)
  const tab = isStaff.value ? 'death' : queryTab
  if (tab === 'transfer') {
    if (batchId) await store.selectTransferBatch(batchId)
    if (create) await openFromQuery('transfer', defaultBatchId(batchId || selectedTransferBatch.value?.batch_id))
  } else if (tab === 'death') {
    if (batchId) await store.selectDeathBatch(batchId)
    if (create) await openFromQuery('death', defaultBatchId(batchId || selectedDeathBatch.value?.batch_id), penId)
  } else if (create) {
    await store.openEntry()
  }
}

async function openFromQuery(kind: 'transfer' | 'death', batchId: number, penId = 0): Promise<void> {
  if (!batchId) {
    ui.toast('还没有在养批次，请先登记批次入栏', 'info')
    return
  }
  if (kind === 'transfer') {
    if (store.transferContext.batchId !== batchId) await store.selectTransferBatch(batchId)
    await store.openTransfer(batchId)
  } else {
    if (store.deathContext.batchId !== batchId) await store.selectDeathBatch(batchId)
    await store.openDeath(batchId)
    // 诊断处理后“去登记”死淘会带上栏舍
    if (penId && (selectedDeathBatch.value?.pens || []).some((pen) => Number(pen.pen_id) === penId)) {
      await store.selectDeathPen(penId)
    }
  }
}

onMounted(async () => {
  // 先核对缓存归属（切场后清空），再按地址设置页签，避免页签被清空覆盖
  store.prepareFarmScope()
  const initialTab = isStaff.value ? 'death' : parseTab(route.query.tab)
  store.setTab(initialTab)
  await store.load(isStaff.value)
  await applyRouteQuery()
})

watch(
  () => [route.query.action, route.query.tab, route.query.batchId],
  async (value, previous) => {
    if (store.status === 'loading') return
    // 处理完 action 后去掉它会再触发一次，此时不必重复加载
    if (!value[0] && previous?.[0] && value[1] === previous[1] && value[2] === previous[2]) return
    await applyRouteQuery()
  }
)

useFarmReload(async () => {
  store.reset()
  photoBusy.value = false
  await store.load(isStaff.value)
  await applyRouteQuery()
})
</script>

<template>
  <zw-shell
    side="user"
    :role="isStaff ? 'staff' : undefined"
    active="WU-L03"
    :crumbs="isStaff ? ['我的栏舍', '死淘登记'] : ['生产管理', '批次登记']"
  >
    <zw-page-head title="批次登记" :desc="desc">
      <template #actions>
        <v-btn color="primary" :prepend-icon="primaryAction.icon" @click="primaryAction.action">{{ primaryAction.label }}</v-btn>
      </template>
      <v-tabs v-if="!isStaff" v-model="activeTab" class="zw-tabs-line">
        <v-tab value="entry">批次入栏</v-tab>
        <v-tab value="transfer">转栏</v-tab>
        <v-tab value="death">死淘</v-tab>
      </v-tabs>
    </zw-page-head>

    <div v-if="store.status === 'loading'">
      <v-skeleton-loader type="article, article, table-row@4" />
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权登记" :desc="store.error || '当前角色无权登记批次。'">
        <v-btn variant="outlined" @click="router.push({ name: isStaff ? 'WU-T02' : 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="登记数据加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load(isStaff, true)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div class="l05-layout">
        <zw-card v-if="!isStaff && store.tab === 'entry'" title="批次入栏" sub="生成批次号与二维码">
          <div class="l05-guide">
            <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="move_to_inbox" :size="20" /></span>
            <div>
              <div class="title-s">登记一批新入栏的畜禽</div>
              <div class="zw-faint body-s">选择栏舍与品种，填写入栏日期、日龄、数量和引种单价。提交后系统生成全平台唯一的批次号。</div>
            </div>
          </div>
          <zw-desc :cols="2" class="mt-4">
            <zw-desc-item label="可用栏舍">{{ formatNumber(store.penOptions.length) }} 个</zw-desc-item>
            <zw-desc-item label="当前在养批次">{{ formatNumber(store.batches.length) }} 个</zw-desc-item>
          </zw-desc>
          <zw-notice icon="qr_code_2" class="mt-4">提交后系统生成批次号和二维码，批次号不能修改。</zw-notice>
          <div class="l05-card-actions">
            <v-btn color="primary" prepend-icon="add" @click="store.openEntry()">批次入栏</v-btn>
          </div>
        </zw-card>

        <zw-card v-else-if="!isStaff && store.tab === 'transfer'" title="转栏登记" sub="批次总存栏不变">
          <div class="l05-field">
            <div class="l05-field-label">选择批次</div>
            <v-select
              :model-value="transferBatchId || undefined"
              :items="batchSelectItems"
              item-title="title"
              item-value="value"
              placeholder="请选择在养批次"
              @update:model-value="transferBatchId = Number($event || 0)"
            />
          </div>
          <div v-if="store.transferContext.loading" class="mt-4"><v-skeleton-loader type="article" /></div>
          <zw-empty v-else-if="store.transferContext.error" icon="cloud_off" tone="error" title="批次加载失败" :desc="store.transferContext.error" />
          <template v-else-if="selectedTransferBatch">
            <div class="l05-batch-summary">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="folder_open" :size="18" /></span>
              <div class="l05-batch-main">
                <div class="zw-cell-main num">{{ selectedTransferBatch.batch_no }}</div>
                <div class="zw-cell-sub">{{ selectedTransferBatch.breed_title }} · 共 {{ formatNumber(selectedTransferBatch.current_count || 0) }} {{ selectedTransferBatch.unit_text || '头' }}</div>
              </div>
              <zw-tag type="primary">在养</zw-tag>
            </div>
            <div class="l05-context-grid">
              <div v-for="pen in selectedTransferBatch.pens || []" :key="pen.pen_id" class="l05-context-pen">
                <span>{{ pen.pen_name }}</span>
                <span class="num">{{ formatNumber(pen.stock_count || 0) }} {{ selectedTransferBatch.unit_text || '头' }}</span>
              </div>
            </div>
            <div class="l05-card-actions">
              <v-btn color="primary" prepend-icon="swap_horiz" @click="store.openTransfer(selectedTransferBatch.batch_id)">转栏登记</v-btn>
            </div>
          </template>
          <div v-else class="l05-placeholder">选择批次后可登记转栏，批次总存栏不变，两个栏舍的在栏数同步变化。</div>
        </zw-card>

        <zw-card v-else title="死淘登记" sub="保存后存栏与死亡率同步更新">
          <div class="l05-field">
            <div class="l05-field-label">选择批次</div>
            <v-select
              :model-value="deathBatchId || undefined"
              :items="batchSelectItems"
              item-title="title"
              item-value="value"
              placeholder="请选择在养批次"
              @update:model-value="deathBatchId = Number($event || 0)"
            />
          </div>
          <div v-if="store.deathContext.loading" class="mt-4"><v-skeleton-loader type="article" /></div>
          <zw-empty v-else-if="store.deathContext.error" icon="cloud_off" tone="error" title="批次加载失败" :desc="store.deathContext.error" />
          <template v-else-if="selectedDeathBatch">
            <div class="l05-batch-summary">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="folder_open" :size="18" /></span>
              <div class="l05-batch-main">
                <div class="zw-cell-main num">{{ selectedDeathBatch.batch_no }}</div>
                <div class="zw-cell-sub">{{ selectedDeathBatch.breed_title }} · 在栏 {{ formatNumber(selectedDeathBatch.current_count || 0) }} {{ selectedDeathBatch.unit_text || '头' }}</div>
              </div>
              <zw-tag type="primary">在养</zw-tag>
            </div>
            <div class="l05-context-grid">
              <div v-for="pen in selectedDeathBatch.pens || []" :key="pen.pen_id" class="l05-context-pen">
                <span>{{ pen.pen_name }}</span>
                <span class="num">{{ formatNumber(pen.stock_count || 0) }} {{ selectedDeathBatch.unit_text || '头' }}</span>
              </div>
            </div>
            <div class="l05-card-actions">
              <v-btn color="primary" prepend-icon="heart_minus" @click="store.openDeath(selectedDeathBatch.batch_id)">登记死淘</v-btn>
            </div>
          </template>
          <div v-else class="l05-placeholder">选择批次后可登记死淘。数量不能超过该批次在当前栏舍的在栏数。</div>
        </zw-card>

        <zw-card title="在养批次" :sub="`${store.batches.length} 个`" flush class="l05-batches">
          <div v-if="!store.batches.length" class="l05-empty">
            <zw-empty icon="folder_open" title="还没有在养批次" :desc="isStaff ? '请联系所有者或场长登记入栏。' : '点“批次入栏”登记第一个批次。'">
              <v-btn v-if="!isStaff" color="primary" @click="store.openEntry()">批次入栏</v-btn>
            </zw-empty>
          </div>
          <v-data-table v-else :headers="batchHeaders" :items="store.batches" item-value="batch_id" hide-default-footer>
            <template #item.batch_no="{ item }"><a class="zw-link num" @click="openBatch(item.batch_id)">{{ item.batch_no }}</a></template>
            <template #item.breed="{ item }">
              <div>{{ item.breed_name }}</div>
              <div class="zw-cell-sub">{{ item.category_name }}</div>
            </template>
            <template #item.pen="{ item }">{{ (item.pens || []).map((pen) => pen.pen_name).join('、') || '—' }}</template>
            <template #item.day_age="{ item }">
              <div class="num">{{ formatNumber(item.day_age || 0) }} 天</div>
              <div class="zw-cell-sub">{{ item.stage_name }}</div>
            </template>
            <template #item.current_count="{ item }"><span class="num">{{ formatNumber(item.current_count || 0) }} {{ item.unit_text || '头' }}</span></template>
            <template #item.mortality="{ item }"><span class="num">{{ formatPercent(item.mortality_rate, 1) }}</span></template>
            <template #item.status="{ item }"><zw-tag type="primary">在养</zw-tag></template>
            <template #item.actions="{ item }">
              <span class="zw-cell-actions">
                <a v-if="!isStaff" @click="store.setTab('transfer'); selectTransferFor(item.batch_id)">转栏</a>
                <a @click="selectDeathFor(item.batch_id)">死淘</a>
                <a @click="openBatch(item.batch_id)">档案</a>
              </span>
            </template>
          </v-data-table>
        </zw-card>
      </div>
    </template>

    <!-- 批次入栏抽屉 -->
    <zw-drawer v-model="store.entryOpen" title="批次入栏" :sub="`${farm.farmName} · 提交后系统生成批次号和二维码`" width="560">
      <zw-notice icon="qr_code_2" class="mb-5">提交后系统生成批次号和二维码，批次号不能修改。</zw-notice>
      <div class="zw-form-2">
        <zw-form-item label="栏舍" required :error="store.entryErrors.pen_id" style="grid-column: 1 / -1" :hint="entryPen ? `在栏 ${formatNumber(entryPen.stock_count || 0)} ${entryPen.unit_text || entryUnit} · 容量 ${formatNumber(entryPen.capacity || 0)} ${entryPen.unit_text || entryUnit}${entryCategory ? ` · ${entryCategory.category_name}` : ''}` : '只显示启用中的栏舍'">
          <v-select
            :model-value="store.entryForm.penId || undefined"
            :items="store.entryPenItems"
            item-title="title"
            item-value="value"
            placeholder="请选择栏舍"
            :error="!!store.entryErrors.pen_id"
            @update:model-value="onEntryPen"
          >
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps" :subtitle="item.raw.subtitle" :disabled="item.raw.disabled" />
            </template>
          </v-select>
        </zw-form-item>
        <zw-form-item label="品种" required :error="store.entryErrors.breed_id" style="grid-column: 1 / -1" :hint="entryCategory ? `品类：${entryCategory.category_name}` : '请先选择栏舍'">
          <v-select
            :model-value="store.entryForm.breedId || undefined"
            :items="entryBreedOptions"
            item-title="title"
            item-value="value"
            :disabled="!entryPen"
            placeholder="请选择品种"
            :error="!!store.entryErrors.breed_id"
            @update:model-value="onEntryBreed"
          />
        </zw-form-item>
        <zw-form-item label="用途" required>
          <zw-seg v-model="store.entryForm.purpose" :items="purposeItems" />
        </zw-form-item>
        <zw-form-item label="来源" required>
          <zw-seg :model-value="store.entryForm.source" :items="sourceItems" @update:model-value="onEntrySource" />
        </zw-form-item>
        <zw-form-item label="入栏日期" required :error="store.entryErrors.entry_date">
          <HerdDateField :model-value="store.entryForm.entryDate" :max="todayISO()" :error="!!store.entryErrors.entry_date" @update:model-value="onEntryDate" />
        </zw-form-item>
        <zw-form-item label="入栏日龄" required :error="store.entryErrors.entry_age_days" :hint="`单位：天`">
          <v-text-field
            :model-value="store.entryForm.entryAgeDays ?? ''"
            type="number"
            min="0"
            placeholder="例如 70"
            suffix="天"
            :error="!!store.entryErrors.entry_age_days"
            @update:model-value="store.onEntryAge($event === '' ? null : Number($event))"
          />
        </zw-form-item>
        <zw-form-item label="数量" required :error="store.entryErrors.entry_count">
          <v-text-field
            :model-value="store.entryForm.entryCount ?? ''"
            type="number"
            min="1"
            placeholder="例如 90"
            :suffix="entryUnit"
            :error="!!store.entryErrors.entry_count"
            @update:model-value="store.onEntryCount($event === '' ? null : Number($event))"
          />
        </zw-form-item>
        <zw-form-item label="入栏平均体重" optional :error="store.entryErrors.entry_avg_weight">
          <v-text-field
            :model-value="store.entryForm.entryAvgWeight ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="例如 28.5"
            suffix="千克"
            :error="!!store.entryErrors.entry_avg_weight"
            @update:model-value="store.onEntryWeight($event === '' ? null : Number($event))"
          />
        </zw-form-item>
        <div v-if="entryOver" class="l05-over" style="grid-column: 1 / -1">
          <zw-notice type="warning" title="入栏后超过栏舍容量。">
            {{ entryOver.penName }}容量 {{ formatNumber(entryOver.capacity) }} {{ entryUnit }}，现有 {{ formatNumber(entryOver.current) }} {{ entryUnit }}，入栏后共 {{ formatNumber(entryOver.after) }} {{ entryUnit }}，超出 {{ formatNumber(entryOver.over) }} {{ entryUnit }}。
          </zw-notice>
          <v-checkbox v-model="store.entryForm.capacityConfirmed" label="我已确认，仍要入栏" color="warning" class="l05-check" />
          <div v-if="store.entryErrors.capacity" class="zw-form-error">{{ store.entryErrors.capacity }}</div>
        </div>
        <zw-form-item v-if="entryPriceVisible" label="引种单价" required :error="store.entryErrors.intro_unit_price" hint="计入批次成本" style="grid-column: 1 / -1">
          <v-text-field
            :model-value="store.entryForm.introUnitPrice ?? ''"
            type="number"
            min="0"
            step="0.01"
            placeholder="例如 520.00"
            :suffix="`元 / ${entryUnit}`"
            :error="!!store.entryErrors.intro_unit_price"
            @update:model-value="onEntryPrice"
          />
        </zw-form-item>
      </div>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.entryOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.submitting || !entryCanSubmit" @click="submitEntry">提交</v-btn>
      </template>
    </zw-drawer>

    <!-- 转栏抽屉 -->
    <zw-drawer v-model="store.transferOpen" title="转栏登记" :sub="`${farm.farmName} · 批次总存栏不变，两个栏舍的在栏数同步变化`" width="560">
      <div v-if="store.transferContext.loading"><v-skeleton-loader type="article, table-row@4" /></div>
      <zw-empty v-else-if="!selectedTransferBatch" icon="folder_open" title="请先选择批次" desc="返回页面选择一个在养批次后再登记转栏。" />
      <template v-else>
        <div class="l05-batch-summary">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="folder_open" :size="18" /></span>
          <div class="l05-batch-main">
            <div class="zw-cell-main num">{{ selectedTransferBatch.batch_no }}</div>
            <div class="zw-cell-sub">{{ selectedTransferBatch.breed_title }} · 共 {{ formatNumber(selectedTransferBatch.current_count || 0) }} {{ selectedTransferBatch.unit_text || '头' }}</div>
          </div>
        </div>
        <div class="zw-form-2 mt-5">
          <zw-form-item label="转出栏舍" required :error="store.transferErrors.from_pen_id" :hint="`本批次在栏 ${formatNumber(store.transferFromStock)} ${store.transferUnit}`">
            <v-select :model-value="store.transferForm.fromPenId || undefined" :items="store.transferFromItems" item-title="title" item-value="value" placeholder="请选择转出栏舍" @update:model-value="onTransferFrom" />
          </zw-form-item>
          <zw-form-item label="转入栏舍" required :error="store.transferErrors.to_pen_id" :hint="transferDestination ? `在栏 ${formatNumber(transferDestination.stock_count || 0)} ${transferDestination.unit_text || '头'} · 容量 ${formatNumber(transferDestination.capacity || 0)} ${transferDestination.unit_text || '头'}` : '选择不含转出栏舍与已停用栏舍'">
            <v-select :model-value="store.transferForm.toPenId || undefined" :items="store.transferToItems" item-title="title" item-value="value" placeholder="请选择转入栏舍" @update:model-value="onTransferTo" />
          </zw-form-item>
          <zw-form-item label="数量" required :error="transferQuantityMessage" :hint="`不能超过转出栏舍的在栏数 ${formatNumber(store.transferFromStock)} ${store.transferUnit}`">
            <v-text-field
              :model-value="store.transferForm.quantity ?? ''"
              type="number"
              min="1"
              placeholder="例如 100"
              :suffix="store.transferUnit"
              :error="!!transferQuantityMessage"
              @update:model-value="store.onTransferQuantity($event === '' ? null : Number($event))"
            />
          </zw-form-item>
          <zw-form-item label="转栏日期" required :error="store.transferErrors.event_date" hint="登记后 3 天内可以自行修改">
            <HerdDateField :model-value="store.transferForm.eventDate" :max="todayISO()" :error="!!store.transferErrors.event_date" @update:model-value="onTransferDate" />
          </zw-form-item>
          <div v-if="transferOver" style="grid-column: 1 / -1">
            <zw-notice type="warning" title="转入后超过栏舍容量。">
              {{ transferOver.penName }}容量 {{ formatNumber(transferOver.capacity) }} {{ store.transferUnit }}，现有 {{ formatNumber(transferOver.current) }} {{ store.transferUnit }}，转入后共 {{ formatNumber(transferOver.after) }} {{ store.transferUnit }}，超出 {{ formatNumber(transferOver.over) }} {{ store.transferUnit }}。确认后才能保存。
            </zw-notice>
            <v-checkbox v-model="store.transferForm.capacityConfirmed" label="我已确认，仍要转入" color="warning" class="l05-check" />
            <div v-if="store.transferErrors.capacity" class="zw-form-error">{{ store.transferErrors.capacity }}</div>
          </div>
          <zw-form-item label="备注" optional style="grid-column: 1 / -1">
            <v-textarea v-model="store.transferForm.remark" placeholder="选填，例如转栏原因" rows="2" maxlength="100" counter="100" persistent-counter />
          </zw-form-item>
        </div>
      </template>
      <template #foot>
        <span class="zw-foot-note">{{ transferOver ? '勾选容量确认后才能保存' : '带 * 为必填' }}</span>
        <v-btn variant="outlined" @click="store.transferOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.submitting || !transferCanSave" @click="submitTransfer">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 死淘抽屉 -->
    <zw-drawer v-model="store.deathOpen" title="死淘登记" :sub="`${farm.farmName} · 保存后存栏、累计死淘和死亡率同步更新`" width="560">
      <div v-if="store.deathContext.loading"><v-skeleton-loader type="article, table-row@4" /></div>
      <zw-empty v-else-if="!selectedDeathBatch" icon="folder_open" title="请先选择批次" desc="返回页面选择一个在养批次后再登记死淘。" />
      <template v-else>
        <div class="zw-form-2">
          <zw-form-item label="批次" required :error="store.deathErrors.batch_id" style="grid-column: 1 / -1" :hint="store.deathQuantityHint">
            <v-select :model-value="store.deathForm.batchId || undefined" :items="batchSelectItems" item-title="title" item-value="value" placeholder="请选择批次" @update:model-value="onDeathBatch" />
          </zw-form-item>
          <zw-form-item v-if="(selectedDeathBatch.pens || []).length > 1" label="栏舍" required :error="store.deathErrors.pen_id">
            <v-select :model-value="store.deathForm.penId || undefined" :items="store.deathPenItems" item-title="title" item-value="value" placeholder="请选择栏舍" @update:model-value="onDeathPen" />
          </zw-form-item>
          <zw-form-item label="数量" required :error="deathQuantityMessage" :hint="deathPen ? `不能超过 ${deathPen.pen_name} 的在栏数 ${formatNumber(store.deathStock)} ${store.deathUnit}` : '请选择栏舍'">
            <v-text-field
              :model-value="store.deathForm.quantity ?? ''"
              type="number"
              min="1"
              placeholder="例如 2"
              :suffix="store.deathUnit"
              :error="!!deathQuantityMessage"
              :append-inner-icon="deathQuantityMessage ? 'error' : undefined"
              @update:model-value="store.onDeathQuantity($event === '' ? null : Number($event))"
            />
          </zw-form-item>
          <zw-form-item label="原因" required :error="store.deathErrors.reason_code" style="grid-column: 1 / -1">
            <zw-seg v-model="store.deathForm.reasonCode" :items="reasonItems" />
          </zw-form-item>
          <zw-form-item label="日期" required :error="store.deathErrors.event_date" :style="isStaff ? 'grid-column: 1 / -1' : ''">
            <HerdDateField :model-value="store.deathForm.eventDate" :max="todayISO()" :error="!!store.deathErrors.event_date" @update:model-value="onDeathDate" />
          </zw-form-item>
          <zw-form-item v-if="!isStaff" label="实际执行人" required :error="store.deathErrors.executor_user_id" hint="代员工登记时选择">
            <v-select
              :model-value="store.deathForm.executorUserId || null"
              :items="store.executorItems"
              @update:model-value="store.deathForm.executorUserId = Number($event || 0)"
              item-title="title"
              item-value="value"
              :loading="store.deathContext.executorsLoading"
              placeholder="请选择实际执行人"
            />
          </zw-form-item>
          <zw-form-item label="说明" :required="store.deathForm.reasonCode === 'OTHER'" :optional="store.deathForm.reasonCode !== 'OTHER'" :error="store.deathErrors.remark" style="grid-column: 1 / -1" :hint="store.deathForm.reasonCode === 'OTHER' ? '选择“其他”时请写明原因' : ''">
            <v-textarea v-model="store.deathForm.remark" placeholder="选填，例如症状、处理方式" rows="2" maxlength="200" counter="200" persistent-counter />
          </zw-form-item>
          <zw-form-item label="照片" optional style="grid-column: 1 / -1">
            <HerdPhotoUpload v-model="store.deathForm.fileIds" @update:busy="photoBusy = $event" />
          </zw-form-item>
        </div>
      </template>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.deathOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.submitting || photoBusy" @click="submitDeath">保存</v-btn>
      </template>
    </zw-drawer>

    <!-- 入栏成功 -->
    <zw-dialog v-model="store.entrySuccess" title="入栏成功" width="520">
      <div v-if="store.entryResult" class="l05-success">
        <span class="l05-success-ico"><zw-icon name="check_circle" :size="28" fill /></span>
        <div class="title-card mt-2">批次已生成</div>
        <div class="zw-muted">
          {{ store.entryResult.pen_name }}入栏 {{ formatNumber(store.entryResult.entry_count || 0) }} {{ store.entryResult.unit_text || '头' }}，可以开始登记投喂和称重
        </div>
        <div class="zw-faint body-s mt-4">批次号</div>
        <div class="l05-success-no num">{{ store.entryResult.batch_no }}</div>
        <div ref="entryQrBox" class="l05-qrbox"><zw-qr :size="156" :seed="store.entryResult.qr_content || store.entryResult.batch_no || ''" /></div>
        <div class="zw-muted body-s mt-2">{{ store.entryResult.label_text || `${store.entryResult.farm_name} · ${store.entryResult.pen_name} · ${store.entryResult.breed_name}` }}</div>
      </div>
      <zw-desc v-if="store.entryResult" :cols="1" bordered label-width="132" class="mt-4">
        <zw-desc-item label="入栏">{{ dateText(store.entryResult.entry_date) }} · {{ formatNumber(store.entryResult.entry_count || 0) }} {{ store.entryResult.unit_text || '头' }} · {{ store.entryResult.entry_age_days }} 日龄</zw-desc-item>
        <zw-desc-item :label="`${store.entryResult.pen_name}现有`">
          <span class="num">{{ formatNumber(store.entryResult.pen_stock_after || 0) }} / {{ formatNumber(store.entryResult.capacity || 0) }} {{ store.entryResult.unit_text || '头' }}</span>
          <zw-tag v-if="Number(store.entryResult.over_capacity_count || 0) > 0" type="warning" size="small" class="ml-2">超出 {{ formatNumber(store.entryResult.over_capacity_count) }} {{ store.entryResult.unit_text || '头' }}</zw-tag>
        </zw-desc-item>
      </zw-desc>
      <zw-notice class="mt-3">批次号全平台唯一，生成后不能修改，二维码与批次号一一对应。</zw-notice>
      <template #actions>
        <v-btn variant="outlined" prepend-icon="download" :disabled="!entryQrContent" @click="exportEntryQr">下载图片</v-btn>
        <v-btn variant="outlined" prepend-icon="print" :disabled="!entryQrContent" @click="printEntryQr">打印标签</v-btn>
        <v-btn color="primary" @click="viewEntryBatch">查看档案</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.l05-layout { display: flex; flex-direction: column; gap: 16px; }
.l05-guide { display: flex; align-items: flex-start; gap: 12px; }
.l05-card-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
.l05-field { max-width: 420px; }
.l05-field-label { margin-bottom: 6px; font-size: 14px; color: var(--text-1); }
.l05-batch-summary { display: flex; align-items: center; gap: 12px; padding: 12px 16px; margin-top: 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--fill-1); }
.l05-batch-main { flex: 1; min-width: 0; }
.l05-context-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.l05-context-pen { display: inline-flex; align-items: center; gap: 12px; padding: 6px 12px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); font-size: 13px; }
.l05-placeholder { margin-top: 16px; padding: 32px 20px; border: 1px dashed var(--line-2); border-radius: var(--radius-sm); color: var(--text-3); font-size: 14px; text-align: center; }
.l05-over { margin: 0; }
.l05-check { margin-top: 6px; margin-left: -6px; }
.l05-check :deep(.v-label) { font-weight: 500; }
.l05-success { display: flex; flex-direction: column; align-items: center; text-align: center; }
.l05-success-ico { display: flex; align-items: center; justify-content: center; width: 48px; height: 48px; border-radius: 50%; background: var(--success-container); color: var(--on-success-container); }
.l05-success-no { margin-top: 4px; font-size: 28px; line-height: 36px; font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: .5px; }
.l05-qrbox { margin-top: 12px; padding: 12px; border: 1px solid var(--line-1); border-radius: var(--radius-md); background: #FFFFFF; }
.l05-qrbox :deep(.zw-qr) { border: 0; }
.l05-batches { min-width: 0; }
.l05-empty { padding: 8px 0; }
</style>
