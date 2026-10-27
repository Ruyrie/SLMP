<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import { useBreedingPendingStore } from '@/stores/breeding/pending'
import { useFarmStore } from '@/stores/farm'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import {
  animalStatusText,
  dueBadgeText,
  dueGroupLabel,
  dueTone,
  monthDay,
  monthDayWeek,
  todayISO
} from '@/stores/breeding/shared'
import type { BreedingAnimalOption, FarrowDueItem, HatchDueItem, PendingCheck } from '@/api/breeding'
import BreedingDateField from './components/BreedingDateField.vue'

interface DueRow {
  key: string
  group: boolean
  groupText?: string
  item?: FarrowDueItem
}

interface HatchRow {
  key: string
  group: boolean
  groupText?: string
  item?: HatchDueItem
}

const store = useBreedingPendingStore()
const farm = useFarmStore()
const route = useRoute()
const router = useRouter()
const { isStaff, isOwner } = useRole()

const dueHeaders = computed<DataTableHeader[]>(() => {
  const list: DataTableHeader[] = [
    { title: '距预产', key: 'days', sortable: false, width: 84 },
    { title: '耳号', key: 'earTag', sortable: false, width: 90 },
    { title: '品种', key: 'breedName', sortable: false, width: 100 },
    { title: '栏舍', key: 'penName', sortable: false, width: 90 },
    { title: '胎次', key: 'parity', sortable: false, width: 84 },
    { title: '配种日期', key: 'firstMatingDate', sortable: false, width: 100 },
    { title: '预产期', key: 'expectedDueDate', sortable: false, width: 140 },
    { title: '提醒', key: 'reminderText', sortable: false },
    { title: '负责员工', key: 'staff', sortable: false, width: 110 }
  ]
  if (!isStaff.value) list.push({ title: '操作', key: 'actions', sortable: false, width: 150, align: 'end' })
  return list
})

const hatchHeaders = computed<DataTableHeader[]>(() => {
  const list: DataTableHeader[] = [
    { title: '距出雏', key: 'days', sortable: false, width: 84 },
    { title: '种禽群', key: 'flockText', sortable: false },
    { title: '入孵日期', key: 'setDate', sortable: false, width: 100 },
    { title: '入孵蛋数', key: 'setCount', sortable: false, width: 100, align: 'end' },
    { title: '受精蛋数', key: 'fertileCount', sortable: false, width: 100, align: 'end' },
    { title: '预计出雏', key: 'expectedHatchDate', sortable: false, width: 140 },
    { title: '提醒', key: 'reminderText', sortable: false },
    { title: '负责员工', key: 'staff', sortable: false, width: 110 }
  ]
  if (!isStaff.value) list.push({ title: '操作', key: 'actions', sortable: false, width: 150, align: 'end' })
  return list
})

const headers = computed(() => (store.tab === 'due' ? dueHeaders.value : hatchHeaders.value))

function buildRows<T extends { group?: string | null; groupText?: string | null }>(list: T[], unit: string): Array<{ key: string; group: boolean; groupText?: string; item?: T }> {
  const rows: Array<{ key: string; group: boolean; groupText?: string; item?: T }> = []
  const counts = new Map<string, number>()
  for (const item of list) {
    const group = item.group || ''
    counts.set(group, (counts.get(group) || 0) + 1)
  }
  let last = ''
  const used = new Set<string>()
  for (const item of list) {
    const group = item.group || ''
    if (group !== last) {
      if (!used.has(group)) {
        rows.push({
          key: `g-${group}-${rows.length}`,
          group: true,
          groupText: `${dueGroupLabel(group)} · ${counts.get(group) || 0} ${unit}`
        })
        used.add(group)
      }
      last = group
    }
    rows.push({ key: `r-${(item as { animalId?: number; incubationId?: number }).animalId || (item as { incubationId?: number }).incubationId || rows.length}`, group: false, item })
  }
  return rows
}

const rows = computed<any[]>(() => {
  if (store.tab === 'due') return buildRows(store.dueList, '头')
  return buildRows(store.hatchList, '批')
})

const cardTitle = computed(() => (store.tab === 'due' ? '待产' : '待出雏'))
const cardSub = computed(() => {
  const count = store.tab === 'due' ? store.dueTotal : store.hatchTotal
  const unit = store.tab === 'due' ? '头' : '批'
  return `${formatNumber(count)} ${unit}${isStaff.value ? ' · 只读' : ''}`
})
const reminderText = computed(() => {
  const targetRole = isOwner.value ? '发给你和场长' : isStaff.value ? '发给所有者和场长' : '发给所有者和你'
  return store.tab === 'due'
    ? `预产期提前 3 天和当天各提醒一次，${targetRole}`
    : `预计出雏日前 1 天和当天各提醒一次，${targetRole}`
})
const desc = computed(() => {
  const farmName = farm.farmName || '当前养殖场'
  if (isStaff.value) {
    return `${farmName} · 只列出你负责的栏舍里的种畜，登记由所有者或场长完成`
  }
  return `${farmName} · ${store.animalLabel}按耳号建档，预产期按品种妊娠期推算`
})
const pendingCheckTitle = computed(() => `待妊检 · ${store.pendingChecks.length} 头`)

function dueBadgeClass(days?: number | null): string {
  return `b02-day b02-day--${dueTone(days)}`
}

function formatExpected(days: number, date?: string): string {
  if (days === 0) return `${monthDay(date)}（今天）`
  return monthDayWeek(date)
}

function staffText(names?: string[]): string {
  return (names || []).filter(Boolean).join('、') || '无负责员工'
}

function openArchive(animalId?: number): void {
  if (!animalId || !canOpenArchive()) return
  void router.push({ name: 'WU-B01', query: { animalId: String(animalId) } })
}

function canOpenArchive(): boolean {
  return !isStaff.value
}

function openFarrowing(animalId?: number): void {
  if (isStaff.value) return
  void store.openFarrowing(animalId)
}

function openMating(animalId?: number, checkAnimalId?: number): void {
  if (isStaff.value) return
  void store.openMating(animalId, checkAnimalId)
}

function openHatch(item: HatchDueItem): void {
  if (!item.incubationId || isStaff.value) return
  void router.push({ name: 'WU-B03', query: { incubationId: String(item.incubationId) } })
}

function onIncubationClick(item?: HatchDueItem): void {
  if (item) openHatch(item)
}

function onTab(value: unknown): void {
  if (value === 'animals') {
    void router.push({ name: 'WU-B01' })
    return
  }
  const tab = value === 'hatch' ? 'hatch' : 'due'
  store.setTab(tab)
  void router.replace({ query: { ...route.query, tab } })
}

function switchFarm(): void {
  const target = store.switchFarms[0]
  if (!target?.farmId) return
  void (async () => {
    await farm.switchTo(Number(target.farmId))
    store.setTab('hatch')
    await router.replace({ query: { tab: 'hatch' } })
  })()
}

function selectMatingAnimal(value: unknown): void {
  store.matingForm.animalId = Number(value || 0)
  void store.loadMatingContext()
}

function selectFarrowAnimal(value: unknown): void {
  store.farrowForm.animalId = Number(value || 0)
  store.farrowCapacity = null
  store.farrowForm.capacityConfirmed = false
  void store.loadFarrowContext()
}

function selectWeanAnimal(value: unknown): void {
  store.weanForm.animalId = Number(value || 0)
  void store.loadWeanContext()
}

function onTransferChange(value: unknown): void {
  store.farrowCapacity = null
  if (!value) store.farrowForm.capacityConfirmed = false
}

function matingItemTitle(option: BreedingAnimalOption): string {
  return `${option.earTag || ''} · ${option.breedName || ''} · 第 ${option.parity || 0} 胎`
}

function matingItemValue(option: BreedingAnimalOption): number {
  return Number(option.animalId || 0)
}

function optionTitle(option: BreedingAnimalOption): string {
  return `${option.earTag || ''} · ${option.breedName || ''} · 第 ${option.parity || 0} 胎`
}

function optionValue(option: BreedingAnimalOption): number {
  return Number(option.animalId || 0)
}

function selectedOptionText(option?: BreedingAnimalOption): string {
  return option?.statusText || animalStatusText(option?.status)
}

function closeDrawer(): void {
  store.closeDrawer()
  if (route.query.action) {
    const query = { ...route.query }
    delete query.action
    delete query.animalId
    void router.replace({ query })
  }
}

function fieldError(errors: Record<string, string>, field: string): string {
  return errors[field] || ''
}

async function handleQuery(): Promise<void> {
  if (isStaff.value) return
  const action = String(route.query.action || '')
  const animalId = Number(route.query.animalId || 0)
  if (action === 'mating') await store.openMating(animalId || undefined)
  else if (action === 'check') await store.openMating(undefined, animalId || undefined)
  else if (action === 'farrowing') await store.openFarrowing(animalId || undefined)
  else if (action === 'weaning') await store.openWeaning(animalId || undefined)
}

onMounted(async () => {
  const initialTab = route.query.tab === 'hatch' ? 'hatch' : 'due'
  store.tab = initialTab
  await store.load()
  await handleQuery()
})

useFarmReload(async () => {
  store.reset()
  store.tab = route.query.tab === 'hatch' ? 'hatch' : 'due'
  await store.load()
})

watch(() => [route.query.action, route.query.animalId], () => {
  if (route.query.action) void handleQuery()
})

watch(() => route.query.tab, (value) => {
  const tab = value === 'hatch' ? 'hatch' : 'due'
  if (tab !== store.tab) store.setTab(tab)
})

const today = todayISO()
</script>

<template>
  <zw-shell side="user" active="WU-B02" :crumbs="isStaff ? ['我的栏舍', '待产与待出雏'] : ['生产管理', '繁育管理', '待产与登记']">
    <zw-page-head :title="isStaff ? '待产与待出雏' : '繁育管理'" :desc="desc">
      <template v-if="!isStaff" #actions>
        <v-menu location="bottom end">
          <template #activator="{ props: activatorProps }">
            <v-btn v-bind="activatorProps" color="primary" prepend-icon="add" append-icon="keyboard_arrow_down">登记</v-btn>
          </template>
          <div class="zw-pop b02-reg">
            <div class="zw-pop-title">繁育登记<span class="zw-pop-sub">登记到{{ farm.farmName || '当前养殖场' }}</span></div>
            <a class="zw-pop-item" @click="openMating()"><zw-icon name="favorite" :size="18" />登记配种 / 妊检</a>
            <a class="zw-pop-item" @click="openFarrowing()"><zw-icon name="add_box" :size="18" />登记产仔</a>
            <a class="zw-pop-item" @click="store.openWeaning()"><zw-icon name="child_care" :size="18" />登记断奶</a>
          </div>
        </v-menu>
      </template>
      <v-tabs :model-value="store.tab" class="zw-tabs-line" @update:model-value="onTab">
        <v-tab v-if="!isStaff" value="animals" @click="router.push({ name: 'WU-B01' })">种畜档案<span class="b02-tab-n num">{{ formatNumber(store.overview?.counts?.all || 0) }}</span></v-tab>
        <v-tab value="due">待产<span class="b02-tab-n num">{{ formatNumber(store.dueCount) }}</span></v-tab>
        <v-tab value="hatch">待出雏<span class="b02-tab-n num">{{ formatNumber(store.hatchCount) }}</span></v-tab>
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
      <v-skeleton-loader type="article, table-row@6" />
    </div>

    <template v-else>
      <zw-notice v-if="!isStaff && !store.overview?.hasIncubation && store.crossFarmText" type="info" icon="egg" class="mb-4">
        {{ store.crossFarmText }}
        <template #action><a @click="switchFarm">切换</a></template>
      </zw-notice>

      <zw-card :title="cardTitle" :sub="cardSub" flush>
        <template #extra>
          <span class="b02-rule"><zw-icon name="notifications" :size="16" />{{ reminderText }}</span>
          <v-text-field
            v-model="store.query.keyword"
            :placeholder="store.tab === 'due' ? '输入耳号' : '输入批次号或栏舍名'"
            prepend-inner-icon="search"
            class="zw-w-180"
            clearable
            @keydown.enter="store.setKeyword()"
            @click:clear="store.setKeyword()"
          />
        </template>

        <zw-empty v-if="store.status === 'error'" icon="cloud_off" tone="error" :title="store.tab === 'due' ? '待产列表加载失败' : '待出雏列表加载失败'" :desc="store.error">
          <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
        </zw-empty>

        <zw-empty v-else-if="store.status === 'filtered_empty'" icon="search_off" title="没有找到匹配的记录" desc="换一个耳号、批次号或栏舍名试试。">
          <v-btn variant="text" @click="store.query.keyword = ''; store.setKeyword()">清除搜索</v-btn>
        </zw-empty>

        <zw-empty v-else-if="store.status === 'empty'" :icon="store.tab === 'due' ? 'female' : 'egg'" :title="store.tab === 'due' ? '没有待产的种畜' : '还没有入孵记录'" :desc="store.tab === 'due'
          ? (isStaff ? '你负责的栏舍里没有待产的种畜。' : '没有待产的种畜。妊检结果为阳性后，会按预产期出现在这里。')
          : '登记入孵后，这里会按预计出雏日提醒你。'">
          <v-btn v-if="store.tab === 'hatch' && !isStaff" color="primary" @click="router.push({ name: 'WU-B03', query: { action: 'incubate' } })">登记入孵</v-btn>
        </zw-empty>

        <template v-else>
          <v-data-table :headers="headers" :items="rows" hide-default-footer :hover="!isStaff" class="b02-table">
            <template #body="{ items }">
              <template v-for="row in items" :key="row.key">
                  <tr v-if="row.group" class="b02-group"><td :colspan="headers.length">{{ row.groupText }}</td></tr>
                  <tr v-else-if="store.tab === 'due' && row.item" class="b02-row">
                    <td>
                      <span :class="dueBadgeClass(row.item.daysToDue)" :title="`距预产 ${row.item.daysToDue} 天`">
                        <template v-if="row.item.daysToDue === 0">今天</template>
                        <template v-else><b class="num">{{ dueBadgeText(row.item.daysToDue) }}</b><i>{{ Number(row.item.daysToDue) < 0 ? '天逾期' : '天' }}</i></template>
                      </span>
                    </td>
                    <td><span class="zw-cell-main num">{{ row.item.earTag }}</span></td>
                    <td>{{ row.item.breedName || '—' }}</td>
                    <td>{{ row.item.penName || '—' }}</td>
                    <td class="num">第 {{ row.item.parity || 0 }} 胎</td>
                    <td class="num b02-nowrap">{{ monthDay(row.item.firstMatingDate) }}</td>
                    <td class="num b02-nowrap">{{ formatExpected(Number(row.item.daysToDue || 0), row.item.expectedDueDate) }}</td>
                    <td class="b02-nowrap zw-muted">{{ row.item.reminderText || '—' }}</td>
                    <td :class="{ 'zw-faint': !row.item.staffNames?.length }">{{ staffText(row.item.staffNames) }}</td>
                    <td v-if="!isStaff" class="text-end">
                      <span class="zw-cell-actions b02-actions">
                        <v-btn v-if="row.item.canRegister && Number(row.item.daysToDue) <= 0" size="small" variant="tonal" color="primary" @click="openFarrowing(Number(row.item.animalId))">登记产仔</v-btn>
                        <a v-if="row.item.canOpenArchive" @click="openArchive(Number(row.item.animalId))">档案</a>
                      </span>
                    </td>
                  </tr>
                  <tr v-else-if="store.tab === 'hatch' && row.item" class="b02-row b02-hatch-row" @click="onIncubationClick(row.item)">
                    <td>
                      <span :class="dueBadgeClass(row.item.daysToHatch)" :title="`距出雏 ${row.item.daysToHatch} 天`">
                        <template v-if="row.item.daysToHatch === 0">今天</template>
                        <template v-else><b class="num">{{ dueBadgeText(row.item.daysToHatch) }}</b><i>天</i></template>
                      </span>
                    </td>
                    <td><span class="zw-cell-main num">{{ row.item.flockText }}</span></td>
                    <td class="num">{{ monthDay(row.item.setDate) }}</td>
                    <td class="num">{{ formatNumber(row.item.setCount || 0) }} 枚</td>
                    <td class="num">{{ row.item.fertileCount === undefined || row.item.fertileCount === null ? '—' : `${formatNumber(row.item.fertileCount)} 枚` }}</td>
                    <td class="num b02-nowrap">{{ monthDayWeek(row.item.expectedHatchDate) }}</td>
                    <td class="b02-nowrap zw-muted">{{ row.item.reminderText || '—' }}</td>
                    <td :class="{ 'zw-faint': !row.item.staffNames?.length }">{{ staffText(row.item.staffNames) }}</td>
                    <td v-if="!isStaff" class="text-end">
                      <span class="zw-cell-actions b02-actions">
                        <v-btn v-if="row.item.canRegister && Number(row.item.daysToHatch) <= 0" size="small" variant="tonal" color="primary" @click.stop="openHatch(row.item)">登记出雏</v-btn>
                        <a v-if="row.item.canOpen" @click.stop="openHatch(row.item)">孵化记录</a>
                      </span>
                    </td>
                  </tr>
                </template>
            </template>
          </v-data-table>
          <div class="zw-table-foot zw-table-foot--in">
            <span class="b02-rule"><zw-icon name="info" :size="14" />{{ isStaff ? '列表只读：员工不能登记繁育记录，也不能打开种畜档案' : (store.tab === 'due' ? '按距预产天数分组：今天、7 天内、更晚；0–3 天标为临产' : '按预计出雏日分组：今天、7 天内、更晚') }}</span>
            <zw-pager :total="store.tab === 'due' ? store.dueTotal : store.hatchTotal" :page="store.query.page" :size="store.query.size" @update:page="store.setPage" @update:size="store.setSize" />
          </div>
        </template>
      </zw-card>
    </template>

    <!-- 登记配种 / 妊检 -->
    <zw-drawer :model-value="store.drawer === 'mating'" title="登记配种 / 妊检" :sub="`${farm.farmName || '当前养殖场'} · 预产期按品种妊娠期自动推算`" :width="560" @update:model-value="(value: unknown) => { if (!value) closeDrawer() }">
      <div class="zw-section-title">登记配种</div>
      <div class="zw-form-2">
        <zw-form-item label="母猪" required style="grid-column: 1 / -1" :error="fieldError(store.matingErrors, 'animal_id')" hint="只能选择空怀或已配种的母猪，妊娠、哺乳中的不可选">
          <v-select
            :model-value="store.matingForm.animalId"
            :items="store.matingOptions"
            :item-title="matingItemTitle"
            :item-value="matingItemValue"
            :loading="store.optionsLoading"
            :error="!!fieldError(store.matingErrors, 'animal_id')"
            placeholder="输入耳号或选择母猪"
            clearable
            @update:model-value="selectMatingAnimal"
          >
            <template #append-inner>
              <zw-tag v-if="store.selectedMatingAnimal" size="small" :type="store.selectedMatingAnimal.status === 'MATED' ? 'neutral' : 'neutral'">{{ selectedOptionText(store.selectedMatingAnimal) }}</zw-tag>
            </template>
          </v-select>
        </zw-form-item>
        <zw-form-item label="配种日期" required :error="fieldError(store.matingErrors, 'mating_date')">
          <BreedingDateField v-model="store.matingForm.matingDate" :max="today" :min="store.matingMinDate" :error="!!fieldError(store.matingErrors, 'mating_date')" />
        </zw-form-item>
        <zw-form-item label="配种方式" required>
          <zw-seg v-model="store.matingForm.matingMethod" :items="[{ label: '人工授精', value: 'ARTIFICIAL' }, { label: '本交', value: 'NATURAL' }]" />
        </zw-form-item>
        <zw-form-item label="与配公猪" optional style="grid-column: 1 / -1" :error="fieldError(store.matingErrors, 'boar_info')" hint="输入公猪耳号或精液批号，最多 20 个字">
          <v-text-field v-model="store.matingForm.boarInfo" maxlength="20" :error="!!fieldError(store.matingErrors, 'boar_info')" />
        </zw-form-item>
      </div>

      <div v-if="store.matingDueDate" class="b02-due">
        <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="event" :size="18" /></span>
        <div>
          <div class="body-s zw-muted">系统推算预产期</div>
          <div class="b02-due-date num">{{ monthDayWeek(store.matingDueDate) }}</div>
          <div class="body-s zw-faint">按品种妊娠期 {{ store.matingGestationDays }} 天推算<template v-if="store.matingRemating">；同一情期再次配种时，仍按首次配种日期计算</template></div>
        </div>
      </div>
      <div v-else class="b02-due b02-due--empty">
        <span class="zw-ico-tile"><zw-icon name="event" :size="18" /></span>
        <div class="body-s zw-muted">选择母猪后显示系统推算的预产期</div>
      </div>

      <div class="b02-sep" />
      <div v-if="store.pendingChecks.length" class="zw-section-title">{{ pendingCheckTitle }}</div>
      <div v-if="store.pendingChecks.length" class="b02-preg">
        <div v-for="check in store.pendingChecks" :key="check.animalId" class="b02-preg-row" :class="{ on: store.activeCheckId === check.animalId }">
          <div class="b02-preg-head">
            <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="female" :size="18" /></span>
            <div>
              <div class="zw-cell-main"><span class="num">{{ check.earTag }}</span> · {{ check.breedName }} · 第 {{ check.parity }} 胎</div>
              <div class="zw-cell-sub">{{ monthDay(check.firstMatingDate) }}配种 · 已 {{ check.daysSinceMating }} 天</div>
            </div>
            <zw-tag v-if="store.activeCheckId === check.animalId" type="primary" size="small">正在登记妊检</zw-tag>
            <v-btn v-else variant="outlined" size="small" @click="store.startCheck(check)">登记妊检</v-btn>
          </div>
          <div v-if="store.activeCheckId === check.animalId" class="b02-preg-form">
            <div class="b02-preg-grid">
              <zw-form-item label="妊检日期" required :error="fieldError(store.checkErrors, 'check_date')">
                <BreedingDateField v-model="store.checkForm.checkDate" :max="today" :error="!!fieldError(store.checkErrors, 'check_date')" />
              </zw-form-item>
              <zw-form-item label="妊检结果" required :error="fieldError(store.checkErrors, 'result')">
                <v-radio-group v-model="store.checkForm.result" hide-details>
                  <v-radio value="POSITIVE" color="primary">
                    <template #label>
                      <span>阳性</span>
                      <span class="zw-cell-sub">转为妊娠，预产期 {{ monthDayWeek(check.expectedDueDate) }}</span>
                    </template>
                  </v-radio>
                  <v-radio value="NEGATIVE" color="primary">
                    <template #label>
                      <span>阴性</span>
                      <span class="zw-cell-sub">回到空怀，可以再次配种</span>
                    </template>
                  </v-radio>
                </v-radio-group>
              </zw-form-item>
            </div>
            <div class="b02-preg-act">
              <v-btn variant="text" @click="store.cancelCheck()">取消</v-btn>
              <v-btn color="primary" size="small" :loading="store.checkSubmitting" @click="store.submitCheck(check)">确认结果</v-btn>
            </div>
          </div>
        </div>
      </div>
      <template #foot>
        <span class="zw-foot-note">“登记配种”只提交上方的配种表单</span>
        <v-btn variant="outlined" @click="closeDrawer">取消</v-btn>
        <v-btn color="primary" :loading="store.matingSubmitting" @click="store.submitMating()">登记配种</v-btn>
      </template>
    </zw-drawer>

    <!-- 登记产仔 -->
    <zw-drawer :model-value="store.drawer === 'farrowing'" title="登记产仔" :sub="`${farm.farmName || '当前养殖场'} · 登记后预产期待办自动完成`" :width="560" @update:model-value="(value: unknown) => { if (!value) closeDrawer() }">
      <div class="zw-form-2">
        <zw-form-item label="母猪" required style="grid-column: 1 / -1" :error="fieldError(store.farrowErrors, 'animal_id')">
          <v-select
            :model-value="store.farrowForm.animalId"
            :items="store.farrowingOptions"
            :item-title="optionTitle"
            :item-value="optionValue"
            :loading="store.optionsLoading"
            :error="!!fieldError(store.farrowErrors, 'animal_id')"
            placeholder="只列出妊娠中的母猪"
            @update:model-value="selectFarrowAnimal"
          />
        </zw-form-item>
        <div v-if="store.selectedFarrowAnimal" class="b02-sow" style="grid-column: 1 / -1">
          <span class="zw-ico-tile zw-ico-tile--warning"><zw-icon name="female" :size="18" /></span>
          <div>
            <div class="zw-cell-main"><span class="num">{{ store.selectedFarrowAnimal.earTag }}</span> · {{ store.selectedFarrowAnimal.breedName }} · 第 {{ store.selectedFarrowAnimal.parity }} 胎</div>
            <div class="zw-cell-sub">{{ store.selectedFarrowAnimal.penName }} · {{ monthDay(store.selectedFarrowAnimal.expectedDueDate) }}预产</div>
          </div>
          <zw-tag type="primary">{{ store.selectedFarrowAnimal.statusText || '妊娠' }}</zw-tag>
        </div>
        <zw-form-item label="产仔日期" required style="grid-column: 1 / -1" :error="fieldError(store.farrowErrors, 'farrow_date')">
          <BreedingDateField v-model="store.farrowForm.farrowDate" :max="today" :error="!!fieldError(store.farrowErrors, 'farrow_date')" />
        </zw-form-item>
        <zw-form-item label="总产仔数" required :error="fieldError(store.farrowErrors, 'total_born')">
          <v-text-field v-model.number="store.farrowForm.totalBorn" type="number" min="1" suffix="头" :error="!!fieldError(store.farrowErrors, 'total_born')" />
        </zw-form-item>
        <zw-form-item label="活仔数" required :error="fieldError(store.farrowErrors, 'live_born')">
          <v-text-field v-model.number="store.farrowForm.liveBorn" type="number" min="0" suffix="头" :error="!!fieldError(store.farrowErrors, 'live_born')" />
        </zw-form-item>
        <zw-form-item label="死胎数" required :error="fieldError(store.farrowErrors, 'stillborn')">
          <v-text-field v-model.number="store.farrowForm.stillborn" type="number" min="0" suffix="头" :error="!!fieldError(store.farrowErrors, 'stillborn')" />
        </zw-form-item>
      </div>
      <div v-if="store.farrowSumError" class="b02-sum-err"><zw-icon name="error" :size="16" fill />{{ store.farrowSumError }}</div>

      <div class="b02-batch">
        <div class="b02-batch-head">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="add_box" :size="18" /></span>
          <div><div class="title-s">活仔一键转为新批次</div><div class="body-s zw-faint">登记后自动入栏，出现在存栏中</div></div>
          <v-switch v-model="store.farrowForm.transferToBatch" color="primary" hide-details inset @update:model-value="onTransferChange" />
        </div>
        <div v-if="store.farrowForm.transferToBatch" class="b02-batch-body">
          <zw-desc :cols="2" :label-width="100">
            <zw-desc-item label="入栏栏舍">
              <v-select
                v-model="store.farrowForm.targetPenId"
                :items="store.farrowPens.map((pen) => ({ title: `${pen.pen_name} · 在栏 ${pen.stock_count || 0} · 容量 ${pen.capacity || 0}`, value: Number(pen.pen_id) }))"
                density="compact"
                hide-details
                :error="!!fieldError(store.farrowErrors, 'target_pen_id')"
              />
            </zw-desc-item>
            <zw-desc-item label="品种">{{ store.farrowContext?.offspringBreed?.breedName || '加载后代品种中' }}</zw-desc-item>
            <zw-desc-item label="入栏数量"><span class="num">{{ formatNumber(store.farrowForm.liveBorn || 0) }} 头</span>（按活仔数）</zw-desc-item>
            <zw-desc-item label="入栏日龄 · 来源">0 日龄 · 自繁</zw-desc-item>
            <zw-desc-item label="批次号" :span="2"><span class="zw-faint">提交后由系统生成</span></zw-desc-item>
          </zw-desc>
        </div>
      </div>

      <div v-if="store.farrowForm.transferToBatch && !store.farrowContextLoading && !store.farrowPens.length" class="b02-sum-err">没有与后代品类匹配的启用栏舍，请先新增或启用对应栏舍。</div>
      <div v-if="store.farrowErrors.target_pen_id || store.farrowErrors.offspring_breed_id" class="b02-sum-err">{{ store.farrowErrors.target_pen_id || store.farrowErrors.offspring_breed_id }}</div>
      <zw-notice v-if="store.farrowCapacityInfo && store.farrowCapacityInfo.overCount > 0" type="warning" class="mt-4">
        <b>入栏后超过栏舍容量。</b>{{ store.farrowCapacityInfo.penName }}容量 {{ formatNumber(store.farrowCapacityInfo.capacity) }} 头，现有 {{ formatNumber(store.farrowCapacityInfo.currentCount) }} 头，入栏后共 {{ formatNumber(store.farrowCapacityInfo.afterCount) }} 头，超出 {{ formatNumber(store.farrowCapacityInfo.overCount) }} 头。
      </zw-notice>
      <v-checkbox v-if="store.farrowCapacityInfo && store.farrowCapacityInfo.overCount > 0" v-model="store.farrowForm.capacityConfirmed" label="我已确认，仍要入栏" density="compact" hide-details class="mt-2" />
      <div v-if="store.farrowErrors.capacity" class="b02-sum-err"><zw-icon name="error" :size="16" fill />{{ store.farrowErrors.capacity }}</div>

      <zw-notice v-if="store.selectedFarrowAnimal" type="info" class="mt-4">登记后 {{ store.selectedFarrowAnimal.earTag }} 转为{{ store.farrowForm.liveBorn === 0 ? '空怀' : '哺乳' }}，预产期待办自动完成。</zw-notice>
      <div v-if="store.farrowErrors.transfer_to_batch" class="b02-sum-err"><zw-icon name="error" :size="16" fill />{{ store.farrowErrors.transfer_to_batch }}</div>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="closeDrawer">取消</v-btn>
        <v-btn color="primary" :loading="store.farrowSubmitting" :disabled="store.farrowContextLoading" @click="store.submitFarrowing()">登记产仔</v-btn>
      </template>
    </zw-drawer>

    <!-- 登记断奶 -->
    <zw-drawer :model-value="store.drawer === 'weaning'" title="登记断奶" :sub="`${farm.farmName || '当前养殖场'} · 登记后母猪回到空怀`" :width="560" @update:model-value="(value: unknown) => { if (!value) closeDrawer() }">
      <div class="zw-form-2">
        <zw-form-item label="母猪" required style="grid-column: 1 / -1" :error="fieldError(store.weanErrors, 'animal_id')" hint="只列出哺乳中的母猪">
          <v-select
            :model-value="store.weanForm.animalId"
            :items="store.weaningOptions"
            :item-title="optionTitle"
            :item-value="optionValue"
            :loading="store.optionsLoading"
            :error="!!fieldError(store.weanErrors, 'animal_id')"
            placeholder="输入耳号或选择母猪"
            @update:model-value="selectWeanAnimal"
          />
        </zw-form-item>
        <div v-if="store.selectedWeanAnimal" class="b02-sow" style="grid-column: 1 / -1">
          <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="child_care" :size="18" /></span>
          <div>
            <div class="zw-cell-main"><span class="num">{{ store.selectedWeanAnimal.earTag }}</span> · {{ store.selectedWeanAnimal.breedName }} · 第 {{ store.selectedWeanAnimal.parity }} 胎</div>
            <div class="zw-cell-sub">{{ store.selectedWeanAnimal.penName }} · {{ monthDay(store.weanContext?.farrowDate || store.selectedWeanAnimal.lastFarrowDate) }}产仔 · 已哺乳 {{ store.weanContext?.nursingDays ?? 0 }} 天 · 产仔时活仔 {{ store.weanContext?.liveBorn ?? store.selectedWeanAnimal.liveBorn ?? 0 }} 头</div>
          </div>
          <zw-tag type="primary">哺乳</zw-tag>
        </div>
        <zw-form-item label="断奶日期" required :error="fieldError(store.weanErrors, 'wean_date')">
          <BreedingDateField v-model="store.weanForm.weanDate" :max="today" :error="!!fieldError(store.weanErrors, 'wean_date')" />
        </zw-form-item>
        <zw-form-item label="断奶仔猪数" required :error="fieldError(store.weanErrors, 'weaned_count')" :hint="`不能多于产仔时的活仔 ${store.weanContext?.liveBorn ?? store.selectedWeanAnimal?.liveBorn ?? 0} 头`">
          <v-text-field v-model.number="store.weanForm.weanedCount" type="number" min="1" suffix="头" :error="!!fieldError(store.weanErrors, 'weaned_count')" />
        </zw-form-item>
        <zw-form-item label="断奶窝重" optional :error="fieldError(store.weanErrors, 'litter_weight')" style="grid-column: 1 / -1" hint="最多 1 位小数">
          <v-text-field v-model.number="store.weanForm.litterWeight" type="number" min="0" step="0.1" suffix="千克" :error="!!fieldError(store.weanErrors, 'litter_weight')" />
        </zw-form-item>
      </div>
      <div class="b02-state">
        <span class="zw-muted">提交后母猪状态</span>
        <span class="b02-state-flow"><zw-tag>哺乳</zw-tag><zw-icon name="arrow_forward" :size="18" /><zw-tag type="primary">空怀</zw-tag></span>
      </div>
      <zw-notice type="info" class="mt-4">{{ store.selectedWeanAnimal?.earTag || '母猪' }} 回到空怀，出现在配种选择中，可以再次配种。</zw-notice>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="closeDrawer">取消</v-btn>
        <v-btn color="primary" :loading="store.weanSubmitting" @click="store.submitWeaning()">登记断奶</v-btn>
      </template>
    </zw-drawer>
  </zw-shell>
</template>

<style scoped>
.b02-tab-n { margin-left: 6px; font-size: 12px; color: var(--text-3); }
.b02-reg { width: 232px; }
.b02-reg .zw-pop-item { height: 40px; }
.b02-rule { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-3); white-space: nowrap; }
.b02-day { display: inline-flex; flex-direction: column; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: var(--radius-sm); background: var(--fill-2); color: var(--text-2); line-height: 1; font-size: 12px; font-weight: 600; }
.b02-day b { font-size: 14px; font-weight: 600; }
.b02-day i { font-style: normal; font-size: 10px; margin-top: 2px; }
.b02-day--warning { background: var(--warning-container); color: var(--on-warning-container); }
.b02-day--primary { background: var(--primary-subtle); color: var(--primary); }
.b02-day--neutral { background: var(--fill-2); color: var(--text-2); }
.b02-nowrap { white-space: nowrap; }
.b02-group :deep(td), .b02-group td { height: 36px; background: var(--fill-1); font-size: 13px; font-weight: 600; color: var(--primary); }
.b02-hatch-row { cursor: pointer; }
.b02-actions { align-items: center; }
.b02-due { display: flex; gap: 12px; align-items: flex-start; margin-top: 16px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--fill-1); border: 1px solid var(--line-1); }
.b02-due--empty { color: var(--text-2); }
.b02-due-date { font-size: 20px; line-height: 28px; font-weight: 600; margin: 2px 0; }
.b02-sep { height: 1px; background: var(--line-1); margin: 24px 0 20px; }
.b02-preg { border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.b02-preg-row { padding: 12px 16px; }
.b02-preg-row + .b02-preg-row { border-top: 1px solid var(--line-1); }
.b02-preg-row.on { background: var(--primary-subtle); }
.b02-preg-head { display: flex; align-items: center; gap: 12px; }
.b02-preg-head > div { flex: 1; min-width: 0; }
.b02-preg-form { margin: 12px 0 0; padding: 12px 16px; background: var(--bg-card); border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.b02-preg-form :deep(.v-selection-control) { align-items: flex-start; min-height: 44px; }
.b02-preg-form :deep(.v-label) { flex-direction: column; align-items: flex-start; padding-top: 3px; }
.b02-preg-act { display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px; }
.b02-preg-grid { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 16px; }
.b02-sow { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-radius: var(--radius-sm); background: var(--fill-1); border: 1px solid var(--line-1); }
.b02-sow > div { flex: 1; min-width: 0; }
.b02-sum-err { margin-top: 6px; font-size: 12px; line-height: 18px; color: var(--error); display: flex; align-items: center; gap: 4px; }
.b02-batch { margin-top: 20px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.b02-batch-head { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border-bottom: 1px solid var(--line-1); }
.b02-batch-head > div { flex: 1; }
.b02-batch-body { padding: 14px 16px; }
.b02-state { display: flex; align-items: center; gap: 12px; margin-top: 16px; padding: 12px 16px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.b02-state-flow { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; }
.b02-row :deep(td) { white-space: nowrap; }
.b02-table :deep(table) { table-layout: fixed; width: 100%; }
.b02-table :deep(td), .b02-table :deep(th) { overflow: hidden; text-overflow: ellipsis; }
</style>
