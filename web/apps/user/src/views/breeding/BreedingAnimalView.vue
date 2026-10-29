<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate, formatNumber, today } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import { useBreedingAnimalStore } from '@/stores/breeding/animalList'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { animalStatusText, animalStatusTone, monthDay, monthDayWeek } from '@/stores/breeding/shared'
import type { BreedingAnimalItem, BreedingRecord } from '@/api/breeding'
import BreedingDateField from './components/BreedingDateField.vue'

const store = useBreedingAnimalStore()
const farm = useFarmStore()
const router = useRouter()
const route = useRoute()
const { isStaff } = useRole()

const normalHeaders: DataTableHeader[] = [
  { title: '耳号', key: 'earTag', sortable: false, width: 130 },
  { title: '品种', key: 'breedName', sortable: false, width: 110 },
  { title: '状态', key: 'status', sortable: false, width: 100 },
  { title: '胎次', key: 'parity', sortable: false, width: 90 },
  { title: '所在栏舍', key: 'penName', sortable: false, width: 110 },
  { title: '繁育进度', key: 'progress', sortable: false },
  { title: '操作', key: 'actions', sortable: false, width: 150, align: 'end' }
]

const exitedHeaders: DataTableHeader[] = [
  { title: '耳号', key: 'earTag', sortable: false, width: 130 },
  { title: '品种', key: 'breedName', sortable: false, width: 110 },
  { title: '状态', key: 'status', sortable: false, width: 100 },
  { title: '胎次', key: 'parity', sortable: false, width: 90 },
  { title: '所在栏舍', key: 'penName', sortable: false, width: 110 },
  { title: '登记日期', key: 'exitDate', sortable: false, width: 120 },
  { title: '原因', key: 'exitReason', sortable: false },
  { title: '操作', key: 'actions', sortable: false, width: 90, align: 'end' }
]

const headers = computed(() => (store.showExited ? exitedHeaders : normalHeaders))
const canManage = computed(() => !isStaff.value)
const desc = computed(() => {
  const animalLabel = store.overview?.animalLabel || '母猪'
  const farmName = farm.farmName || '当前养殖场'
  return `${farmName} · ${animalLabel}按耳号建档，预产期按品种妊娠期推算`
})
const tabCount = computed(() => ({
  animals: store.counts.all,
  due: Number(store.overview?.farrowDueCount || 0),
  hatch: Number(store.overview?.hatchDueCount || 0)
}))

let keywordTimer: number | undefined
watch(() => store.query.keyword, () => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => store.setKeyword(), 300)
})

onMounted(async () => {
  if (isStaff.value) {
    store.status = 'forbidden'
    store.error = '你没有登记繁育记录的权限'
    return
  }
  await store.load()
  const animalId = Number(route.query.animalId || 0)
  if (animalId) await store.openDetail(animalId)
})

watch(() => route.query.animalId, (value) => {
  const animalId = Number(value || 0)
  if (animalId && !isStaff.value) void store.openDetail(animalId)
})

useFarmReload(async () => {
  store.reset()
  if (isStaff.value) {
    store.status = 'forbidden'
    store.error = '你没有登记繁育记录的权限'
    return
  }
  await store.load()
})

function onTab(value: unknown): void {
  if (value === 'due') void router.push({ name: 'WU-B02', query: { tab: 'due' } })
  else if (value === 'hatch') void router.push({ name: 'WU-B02', query: { tab: 'hatch' } })
}

function goDue(): void {
  void router.push({ name: 'WU-B02', query: { tab: 'due' } })
}

function openRegister(item: BreedingAnimalItem): void {
  const action = String(item.nextAction || '').toLowerCase()
  if (action === 'preg_check') {
    void router.push({ name: 'WU-B02', query: { action: 'check', animalId: String(item.animalId || '') } })
  } else if (action === 'mating' || action === 'farrowing' || action === 'weaning') {
    void router.push({ name: 'WU-B02', query: { action, animalId: String(item.animalId || '') } })
  }
}

function openRegisterFromDetail(): void {
  const detail = store.detail
  if (!detail) return
  const action = String(detail.nextAction || '').toLowerCase()
  store.detailDrawer = false
  if (action === 'preg_check') {
    void router.push({ name: 'WU-B02', query: { action: 'check', animalId: String(detail.animalId || '') } })
  } else if (action === 'mating' || action === 'farrowing' || action === 'weaning') {
    void router.push({ name: 'WU-B02', query: { action, animalId: String(detail.animalId || '') } })
  }
}

function openEditFromDetail(): void {
  if (!store.detail) return
  const animalId = Number(store.detail.animalId || 0)
  store.detailDrawer = false
  void store.openEdit(animalId)
}

function askExit(): void {
  const detail = store.detail
  if (!detail) return
  const label = [detail.earTag, detail.breedName, detail.parity ? `第 ${detail.parity} 胎` : '', detail.penName]
    .filter(Boolean).join(' · ')
  store.openExit(Number(detail.animalId), detail.earTag || '', Number(detail.version || 0), label)
}

function progressText(item: BreedingAnimalItem): string {
  switch (item.status) {
    case 'PREGNANT':
      return `${monthDay(item.firstMatingDate)}配种 · 预产期 ${monthDayWeek(item.expectedDueDate)}`
    case 'LACTATING':
      return `${monthDay(item.lastFarrowDate)}产仔 · 活仔 ${formatNumber(item.liveBorn || 0)} 头 · 已哺乳 ${formatNumber(item.nursingDays || 0)} 天`
    case 'MATED':
      return `${monthDay(item.firstMatingDate)}配种 · 已 ${formatNumber(item.daysSinceMating || 0)} 天，待妊检`
    case 'EMPTY':
      return item.lastWeanDate ? `${monthDay(item.lastWeanDate)}断奶 · 可配种` : '可配种'
    default:
      return item.exitReason || '—'
  }
}

function dueTag(item: BreedingAnimalItem): { text: string; tone: string } | null {
  if (item.status !== 'PREGNANT' || item.daysToDue === null || item.daysToDue === undefined) return null
  const days = Number(item.daysToDue)
  if (days < 0) return { text: `已超预产 ${Math.abs(days)} 天`, tone: 'warning' }
  if (days === 0) return { text: '今天预产', tone: 'warning' }
  if (days <= 3) return { text: `还有 ${days} 天`, tone: 'warning' }
  return null
}

function trailingDue(item: BreedingAnimalItem): string {
  if (item.status !== 'PREGNANT' || item.daysToDue === null || item.daysToDue === undefined) return ''
  const days = Number(item.daysToDue)
  return days > 3 ? ` · 还有 ${days} 天` : ''
}

function statusText(item: BreedingAnimalItem): string {
  return item.statusText || animalStatusText(item.status)
}

function detailStatusTone(): string {
  return animalStatusTone(store.detail?.status)
}

function recordSummary(record: BreedingRecord): string {
  switch (record.recordType) {
    case 'MATING':
      return [record.matingMethodText, record.boarInfo].filter(Boolean).join(' · ') || '配种'
    case 'PREG_CHECK':
      return [record.checkResultText ? `妊检 · ${record.checkResultText}` : '妊检', record.checkMethodText].filter(Boolean).join(' · ')
    case 'FARROWING':
      return `总产 ${formatNumber(record.totalBorn || 0)} 头 · 活仔 ${formatNumber(record.liveBorn || 0)} 头 · 死胎 ${formatNumber(record.stillborn || 0)} 头${record.batchNo ? ' · 已转为新批次' : ''}`
    case 'WEANING':
      return `断奶仔猪 ${formatNumber(record.weanedCount || 0)} 头${record.litterWeight ? ` · 窝重 ${formatNumber(record.litterWeight, 1)} 千克` : ''}`
    case 'BREEDING_EXIT':
      return `${record.exitTypeText || '淘汰'} · ${record.exitReason || ''}`
    default:
      return record.recordTypeText || ''
  }
}

function submitChange(record: BreedingRecord): void {
  void router.push({
    name: 'WU-U01',
    query: {
      biz_type: record.recordType || '',
      biz_id: String(record.recordId || ''),
      action: 'create'
    }
  })
}

function viewBatch(batchId?: number): void {
  if (!batchId) return
  store.detailDrawer = false
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function fieldError(field: string): string {
  return store.formErrors[field] || ''
}

async function exportCsv(): Promise<void> {
  const rows = await store.exportRows()
  if (!rows.length) return
  const header = store.showExited
    ? ['耳号', '品种', '状态', '胎次', '栏舍', '登记日期', '原因']
    : ['耳号', '品种', '状态', '胎次', '栏舍', '首次配种日期', '预产期', '最近产仔', '最近断奶']
  const lines = rows.map((item) => {
    if (store.showExited) {
      return [item.earTag, item.breedName, statusText(item), item.parity, item.penName, item.exitDate, item.exitReason]
    }
    return [item.earTag, item.breedName, statusText(item), item.parity, item.penName, item.firstMatingDate, item.expectedDueDate, item.lastFarrowDate, item.lastWeanDate]
  })
  const csv = [header, ...lines].map((line) => line.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\r\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `种畜档案-${today()}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <zw-shell side="user" active="WU-B01" :crumbs="['生产管理', '繁育管理', '种畜档案']">
    <zw-page-head title="繁育管理" :desc="desc">
      <template v-if="canManage && !store.showExited" #actions>
        <v-btn variant="outlined" prepend-icon="download" :loading="store.exportBusy" @click="exportCsv">导出</v-btn>
        <v-menu location="bottom end">
          <template #activator="{ props: activatorProps }">
            <v-btn v-bind="activatorProps" color="primary" prepend-icon="add" append-icon="keyboard_arrow_down">登记</v-btn>
          </template>
          <div class="zw-pop b01-reg">
            <div class="zw-pop-title">繁育登记<span class="zw-pop-sub">登记到{{ farm.farmName || '当前养殖场' }}</span></div>
            <a class="zw-pop-item" @click="router.push({ name: 'WU-B02', query: { action: 'mating' } })"><zw-icon name="favorite" :size="18" />登记配种 / 妊检</a>
            <a class="zw-pop-item" @click="router.push({ name: 'WU-B02', query: { action: 'farrowing' } })"><zw-icon name="add_box" :size="18" />登记产仔</a>
            <a class="zw-pop-item" @click="router.push({ name: 'WU-B02', query: { action: 'weaning' } })"><zw-icon name="child_care" :size="18" />登记断奶</a>
            <div class="zw-pop-div" />
            <a class="zw-pop-item" @click="store.openCreate()"><zw-icon name="add" :size="18" />新增种畜</a>
          </div>
        </v-menu>
      </template>
      <v-tabs v-if="canManage" :model-value="'animals'" class="zw-tabs-line" @update:model-value="onTab">
        <v-tab value="animals">种畜档案<span class="b01-tab-n num">{{ tabCount.animals }}</span></v-tab>
        <v-tab value="due">待产<span class="b01-tab-n num">{{ tabCount.due }}</span></v-tab>
        <v-tab value="hatch">待出雏<span class="b01-tab-n num">{{ tabCount.hatch }}</span></v-tab>
      </v-tabs>
    </zw-page-head>

    <div v-if="store.status === 'forbidden'">
      <zw-card>
        <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '你没有登记繁育记录的权限'">
          <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
        </zw-empty>
      </zw-card>
    </div>

    <div v-else-if="store.status === 'loading'">
      <v-skeleton-loader type="article, table-row@8" />
    </div>

    <template v-else>
      <zw-notice v-if="store.dueSoonText && !store.showExited" type="info" icon="event_upcoming" class="mb-4">
        {{ store.dueSoonText }}
        <template #action><a @click="goDue">查看待产</a></template>
      </zw-notice>

      <div class="zw-toolbar">
        <div class="zw-toolbar-l">
          <template v-if="!store.showExited">
            <zw-seg :model-value="store.query.status" :items="store.segItems" @update:model-value="(value: unknown) => store.setFilter(String(value))" />
            <v-select
              :model-value="store.query.penId"
              :items="store.penItems"
              item-title="title"
              item-value="value"
              class="zw-w-140"
              @update:model-value="(value) => store.setPen(Number(value))"
            />
            <v-text-field v-model="store.query.keyword" placeholder="输入耳号" prepend-inner-icon="search" class="zw-w-160" clearable />
          </template>
          <template v-else>
            <span class="b01-history-title">已淘汰和死亡的种畜</span>
            <v-text-field v-model="store.query.keyword" placeholder="输入耳号" prepend-inner-icon="search" class="zw-w-160" clearable />
          </template>
        </div>
        <div class="zw-toolbar-r">
          <v-btn variant="text" color="primary" :prepend-icon="store.showExited ? 'arrow_back' : 'history'" @click="store.toggleExited()">
            {{ store.showExited ? '返回在养种畜' : '已淘汰和死亡的种畜' }}
          </v-btn>
        </div>
      </div>

      <zw-card v-if="store.status === 'error'" flush>
        <zw-empty icon="cloud_off" tone="error" title="种畜档案加载失败" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
        </zw-empty>
      </zw-card>

      <zw-card v-else-if="store.status === 'empty'" flush>
        <zw-empty icon="female" title="还没有种畜档案" desc="先新增种畜，再登记配种。">
          <v-btn color="primary" @click="store.openCreate()">新增种畜</v-btn>
        </zw-empty>
      </zw-card>

      <zw-card v-else-if="store.status === 'filtered_empty'" flush>
        <zw-empty icon="search_off" title="没有找到匹配的种畜" desc="换一个耳号、状态或栏舍试试。">
          <v-btn variant="text" @click="store.clearFilters()">清除筛选</v-btn>
        </zw-empty>
      </zw-card>

      <zw-card v-else flush>
        <v-data-table :headers="headers" :items="store.list" item-value="animalId" hide-default-footer class="b01-table">
          <template #item.earTag="{ item }">
            <div class="b01-ear">
              <span class="zw-ico-tile" :class="item.status === 'PREGNANT' || item.status === 'LACTATING' ? 'zw-ico-tile--primary' : null">
                <zw-icon :name="item.status === 'CULLED' || item.status === 'DEAD' ? 'heart_minus' : 'female'" :size="18" />
              </span>
              <span class="zw-cell-main num">{{ item.earTag }}</span>
            </div>
          </template>
          <template #item.status="{ item }">
            <zw-tag :type="animalStatusTone(item.status)">{{ statusText(item) }}</zw-tag>
          </template>
          <template #item.parity="{ item }"><span class="num">第 {{ item.parity }} 胎</span></template>
          <template #item.progress="{ item }">
            <div class="b01-prog">
              <span>{{ progressText(item) }}<span class="zw-faint">{{ trailingDue(item) }}</span></span>
              <zw-tag v-if="dueTag(item)" :type="dueTag(item)?.tone" size="small">{{ dueTag(item)?.text }}</zw-tag>
            </div>
          </template>
          <template #item.exitReason="{ item }">{{ item.exitReason || '—' }}</template>
          <template #item.actions="{ item }">
            <span class="zw-cell-actions">
              <a v-if="!store.showExited && item.nextActionText" @click="openRegister(item)">{{ item.nextActionText }}</a>
              <a @click="store.openDetail(Number(item.animalId))">档案</a>
            </span>
          </template>
        </v-data-table>
        <div class="zw-table-foot zw-table-foot--in">
          <span class="b01-rule"><zw-icon name="info" :size="14" />{{ store.showExited ? '已淘汰和死亡的种畜只保留历史档案' : '3 天内预产的种畜置顶，其余按栏舍排列（产房在前）' }}</span>
          <zw-pager
            :total="store.total"
            :page="store.query.page"
            :size="store.query.size"
            @update:page="store.setPage"
            @update:size="store.setSize"
          />
        </div>
      </zw-card>
    </template>

    <!-- 种畜档案 -->
    <zw-drawer v-model="store.detailDrawer" title="种畜档案" :sub="`${farm.farmName || '当前养殖场'} · 档案可直接修改，系统保留修改记录`" :width="760">
      <div v-if="store.detailLoading">
        <v-skeleton-loader type="image, article, table-row@4" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="cloud_off" tone="error" title="种畜档案加载失败" :desc="store.detailError">
        <v-btn variant="outlined" @click="store.detailDrawer = false">关闭</v-btn>
      </zw-empty>
      <template v-else-if="store.detail">
        <div class="b01-head">
          <div>
            <div class="b01-head-no">
              <span class="num">{{ store.detail.earTag }}</span>
              <zw-tag :type="detailStatusTone()">{{ store.detail.statusText || animalStatusText(store.detail.status) }}</zw-tag>
            </div>
            <div class="b01-head-sub">{{ store.detailSubtitle }}</div>
          </div>
          <div v-if="canManage && store.detail.nextAction" class="b01-head-act">
            <v-btn color="primary" :prepend-icon="store.detail.nextAction === 'MATING' ? 'favorite' : undefined" @click="openRegisterFromDetail">
              {{ store.detail.nextActionText }}
            </v-btn>
            <v-menu location="bottom end">
              <template #activator="{ props: activatorProps }">
                <v-btn v-bind="activatorProps" variant="outlined" prepend-icon="more_horiz">更多</v-btn>
              </template>
              <div class="zw-pop b01-more">
                <a class="zw-pop-item" @click="openEditFromDetail"><zw-icon name="edit" :size="18" />编辑档案</a>
                <a class="zw-pop-item" @click="store.openAnimalLogs()"><zw-icon name="history" :size="18" />查看修改记录</a>
                <div class="zw-pop-div" />
                <a class="zw-pop-item b01-danger" @click="askExit"><zw-icon name="heart_minus" :size="18" />登记死亡 / 淘汰</a>
                <div class="b01-danger-desc">淘汰后不再出现在配种选择中，历史记录保留</div>
              </div>
            </v-menu>
          </div>
        </div>

        <div class="zw-section-title">档案信息</div>
        <zw-desc :cols="3" bordered :label-width="96">
          <zw-desc-item label="耳号"><span class="num">{{ store.detail.earTag }}</span></zw-desc-item>
          <zw-desc-item label="品种">{{ store.detail.breedName || '—' }}</zw-desc-item>
          <zw-desc-item label="出生日期">{{ store.detail.birthDate ? formatDate(store.detail.birthDate) : '—' }}</zw-desc-item>
          <zw-desc-item label="来源">{{ store.detail.sourceText || '—' }}</zw-desc-item>
          <zw-desc-item label="所在栏舍">{{ store.detail.penName || '—' }}</zw-desc-item>
          <zw-desc-item label="建档日期">{{ store.detail.filingDate ? formatDate(store.detail.filingDate) : '—' }}</zw-desc-item>
        </zw-desc>

        <div class="zw-section-title mt-6">繁育表现</div>
        <div class="zw-grid-3">
          <zw-stat label="胎次" :value="formatNumber(store.detail.stats?.farrowingCount || 0)" unit="胎" :foot="`已登记 ${formatNumber(store.detail.stats?.farrowingCount || 0)} 次产仔`" />
          <zw-stat label="窝均活仔" :value="store.detail.stats?.avgLiveBorn ? formatNumber(store.detail.stats.avgLiveBorn, 1) : '无数据'" :unit="store.detail.stats?.avgLiveBorn ? '头' : ''" foot="按每胎产仔时的活仔数计算" />
          <zw-stat label="上次断奶" :value="store.detail.stats?.lastWeanDate ? formatDate(store.detail.stats.lastWeanDate) : '无数据'" :foot="store.detail.stats?.lastWeanedCount ? `断奶仔猪 ${formatNumber(store.detail.stats.lastWeanedCount)} 头` : '还没有断奶记录'" />
        </div>

        <div class="b01-rec-head">
          <div class="zw-section-title" style="margin: 0">繁育记录</div>
          <span class="body-s zw-faint">按时间倒序 · 已转为新批次的产仔记录修改需审核</span>
        </div>
        <div class="b01-recs">
          <div v-for="record in store.records" :key="`${record.recordType}-${record.recordId}`" class="b01-rec-row">
            <span class="zw-ico-tile"><zw-icon :name="record.recordType === 'FARROWING' ? 'add_box' : record.recordType === 'MATING' ? 'favorite' : record.recordType === 'PREG_CHECK' ? 'fact_check' : record.recordType === 'WEANING' ? 'child_care' : 'heart_minus'" :size="18" /></span>
            <div class="b01-rec-main">
              <div class="zw-cell-main">{{ record.recordTypeText }}<span class="b01-rec-date num">{{ formatDate(record.recordDate) }}</span></div>
              <div class="zw-cell-sub">{{ recordSummary(record) }}<template v-if="record.registrant?.name"> · 登记人 {{ record.registrant.name }}</template></div>
            </div>
            <span class="zw-cell-actions">
              <template v-if="record.batchId">
                <a @click="viewBatch(record.batchId)">查看批次</a>
                <a v-if="record.canRequestChange" @click="submitChange(record)">提交修改申请</a>
                <span class="b01-audit">需审核</span>
              </template>
              <template v-else-if="record.canRequestChange">
                <a @click="submitChange(record)">提交修改申请</a>
                <span class="b01-audit">需审核</span>
              </template>
              <a v-else-if="record.hasChangeLog" @click="store.openRecordLogs(record)">查看修改记录</a>
              <span v-else class="zw-faint">—</span>
            </span>
          </div>
          <div v-if="!store.records.length" class="b01-rec-empty">
            <span class="zw-faint">还没有繁育记录。登记配种后，记录会按时间显示在这里。</span>
          </div>
        </div>
        <div v-if="store.recordsTotal > 10" class="b01-rec-pager">
          <zw-pager :total="store.recordsTotal" :page="store.recordsPage" :size="store.recordsSize" @update:page="store.loadRecords" @update:size="store.setRecordsSize" />
        </div>
      </template>
    </zw-drawer>

    <!-- 修改记录 -->
    <zw-drawer v-model="store.logsDrawer" :title="store.logsTitle" width="560">
      <v-skeleton-loader v-if="store.logsLoading" type="list-item-two-line@3" />
      <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
      <zw-empty v-else icon="history" title="还没有修改记录" desc="档案保存后，每次修改都会记录在这里。" />
    </zw-drawer>

    <!-- 新增 / 编辑档案 -->
    <zw-drawer v-model="store.formDrawer" :title="store.formTitle" :sub="farm.farmName" :width="560">
      <div v-if="store.optionsLoading">
        <v-skeleton-loader type="article, table-row@4" />
      </div>
      <template v-else>
        <div class="zw-form-2">
          <zw-form-item label="耳号" required :error="fieldError('ear_tag')" hint="本场唯一，最多 20 个字">
            <v-text-field v-model="store.form.earTag" placeholder="例如 0161" maxlength="20" :error="!!fieldError('ear_tag')" />
          </zw-form-item>
          <zw-form-item label="品种" required :error="fieldError('breed_id')" hint="来自平台品类品种字典">
            <v-select :model-value="store.form.breedId || null" :items="store.breedItems" item-title="title" item-value="value" placeholder="请选择品种" :error="!!fieldError('breed_id')" @update:model-value="store.form.breedId = Number($event || 0)" />
          </zw-form-item>
          <zw-form-item label="出生日期" :error="fieldError('birth_date')" optional>
            <BreedingDateField v-model="store.form.birthDate" :error="!!fieldError('birth_date')" />
          </zw-form-item>
          <zw-form-item label="来源" required>
            <zw-seg v-model="store.form.source" :items="[{ label: '外购', value: 'PURCHASED' }, { label: '自繁', value: 'SELF_BRED' }]" />
          </zw-form-item>
          <zw-form-item label="所在栏舍" required :error="fieldError('pen_id')">
            <v-select :model-value="store.form.penId || null" :items="store.penOptions" item-title="title" item-value="value" placeholder="请选择栏舍" :error="!!fieldError('pen_id')" @update:model-value="store.form.penId = Number($event || 0)" />
          </zw-form-item>
          <zw-form-item label="建档时胎次" required :error="fieldError('initial_parity')" hint="0–30 胎">
            <v-text-field v-model.number="store.form.initialParity" type="number" min="0" max="30" suffix="胎" :error="!!fieldError('initial_parity')" />
          </zw-form-item>
        </div>
        <zw-notice icon="info" class="mt-4">建档后耳号仍可修改；每次修改都会保留修改记录。</zw-notice>
      </template>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.formDrawer = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.submitting || store.optionsLoading" @click="store.saveForm()">保存档案</v-btn>
      </template>
    </zw-drawer>

    <!-- 登记死亡 / 淘汰 -->
    <zw-dialog v-model="store.exitDialog" title="登记死亡 / 淘汰" icon="heart_minus" tone="error" :width="480">
      <p class="zw-muted mb-4">{{ store.exitAnimalLabel }}</p>
      <div class="zw-form">
        <zw-form-item label="类型" required>
          <zw-seg v-model="store.exitForm.exitType" :items="[{ label: '淘汰', value: 'CULL' }, { label: '死亡', value: 'DEATH' }]" />
        </zw-form-item>
        <zw-form-item label="日期" required :error="store.exitErrors.exit_date">
          <BreedingDateField v-model="store.exitForm.exitDate" :error="!!store.exitErrors.exit_date" />
        </zw-form-item>
        <zw-form-item label="原因" required :error="store.exitErrors.reason" hint="最多 50 字">
          <v-textarea v-model="store.exitForm.reason" rows="2" maxlength="50" counter="50" :error="!!store.exitErrors.reason" />
        </zw-form-item>
      </div>
      <zw-notice type="info" class="mt-4">淘汰后不再出现在配种选择中，历史记录保留。</zw-notice>
      <template #actions>
        <v-btn variant="outlined" @click="store.exitDialog = false">取消</v-btn>
        <v-btn color="error" :loading="store.exitSubmitting" @click="store.confirmExit()">确认登记</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.b01-tab-n { margin-left: 6px; font-size: 12px; color: var(--text-3); }
.b01-reg { width: 232px; }
.b01-reg .zw-pop-item { height: 40px; }
.b01-ear { display: flex; align-items: center; gap: 12px; }
.b01-ear .zw-ico-tile { width: 28px; height: 28px; }
.b01-prog { display: inline-flex; align-items: center; gap: 8px; white-space: nowrap; }
.b01-rule { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-3); }
.b01-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 20px; margin-bottom: 20px; border-bottom: 1px solid var(--line-1); }
.b01-head-no { display: flex; align-items: center; gap: 10px; font-size: 28px; line-height: 36px; font-weight: 600; }
.b01-head-sub { margin-top: 4px; color: var(--text-2); }
.b01-head-act { display: flex; gap: 8px; flex-shrink: 0; }
.b01-more { width: 272px; }
.b01-more .zw-pop-item { height: 40px; }
.b01-more .b01-danger, .b01-more .b01-danger :deep(.zw-mso) { color: var(--error); }
.b01-danger-desc { padding: 0 16px 6px 44px; margin-top: -6px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.b01-rec-head { display: flex; align-items: center; justify-content: space-between; margin: 24px 0 12px; }
.b01-recs { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.b01-rec-row { display: flex; align-items: center; gap: 12px; padding: 12px 16px; }
.b01-rec-row + .b01-rec-row { border-top: 1px solid var(--line-1); }
.b01-rec-main { flex: 1; min-width: 0; }
.b01-rec-date { margin-left: 8px; font-weight: 400; color: var(--text-2); }
.b01-audit { margin-left: 4px; font-size: 12px; color: var(--text-3); }
.b01-rec-empty { padding: 24px 16px; text-align: center; }
.b01-rec-pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.b01-history-title { font-size: 14px; font-weight: 600; color: var(--text-1); }
.b01-table :deep(td) { white-space: nowrap; }
</style>
