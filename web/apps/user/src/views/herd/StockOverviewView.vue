<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatNumber, formatPercent } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import { useStockOverviewStore } from '@/stores/herd/overview'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { monthDay, occupancyPercent } from '@/stores/herd/shared'
import { useUiStore } from '@/stores/ui'
import type { ProductionReminder } from '@/api/herd'
import { reminderIcon, reminderTarget } from './reminder'

const store = useStockOverviewStore()
const router = useRouter()
const ui = useUiStore()
const { isStaff } = useRole()

const desc = computed(() => {
  const farm = store.farmLabel || '当前养殖场'
  return `${farm} · 存栏、日龄、生长阶段和死亡率由系统按北京时间计算`
})

const activeTab = computed({
  get: () => store.tab,
  set: (value: 'pen' | 'category' | 'batch') => store.setTab(value)
})

const canAddPen = computed(() => !isStaff.value)

const penHeaders: DataTableHeader[] = [
  { title: '栏舍', key: 'pen_name', sortable: false, width: 132 },
  { title: '在栏批次', key: 'batch', sortable: false },
  { title: '存栏 / 容量', key: 'stock', sortable: false, align: 'end', width: 118 },
  { title: '占用', key: 'occupancy', sortable: false, width: 138 },
  { title: '死亡率', key: 'mortality', sortable: false, align: 'end', width: 78 },
  { title: '状态', key: 'status', sortable: false, width: 92 },
  { title: '操作', key: 'actions', sortable: false, align: 'end', width: 72 }
]

const batchHeaders: DataTableHeader[] = [
  { title: '批次号', key: 'batch_no', sortable: false, width: 138 },
  { title: '品种', key: 'breed', sortable: false },
  { title: '栏舍', key: 'pen', sortable: false },
  { title: '日龄', key: 'day_age', sortable: false, width: 104 },
  { title: '存栏', key: 'current_count', sortable: false, align: 'end', width: 90 },
  { title: '死亡率', key: 'mortality', sortable: false, align: 'end', width: 84 },
  { title: '状态', key: 'status', sortable: false, width: 96 },
  { title: '操作', key: 'actions', sortable: false, align: 'end', width: 72 }
]

const penSortItems = [
  { title: '按编号', value: 'CODE' },
  { title: '按存栏', value: 'STOCK' },
  { title: '按占用率', value: 'OCCUPANCY' }
]
const batchSortItems = [
  { title: '按入栏日期', value: 'ENTRY_DATE' },
  { title: '按日龄', value: 'DAY_AGE' }
]

const noMatching = computed(() => {
  if (store.status !== 'ready' || !store.keyword.trim()) return false
  return store.currentTotal === 0
})

const batchEmpty = computed(() => {
  if (store.status !== 'ready' || store.keyword.trim()) return false
  return store.tab === 'batch' && store.filteredBatches.length === 0
})

const showContent = computed(() => store.status === 'ready' || store.status === 'empty')

onMounted(() => {
  void store.load(true)
})
useFarmReload(async () => {
  store.reset()
  await store.load()
})

function openPen(penId?: number): void {
  if (!penId) return
  void router.push({ name: 'WU-L02', query: { penId: String(penId) } })
}

function openBatch(batchId?: number): void {
  if (!batchId) return
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function openBreeding(): void {
  void router.push({ name: 'WU-B01' })
}

function openCreatePen(): void {
  void router.push({ name: 'WU-L02', query: { action: 'create' } })
}

function openEntry(): void {
  void router.push({ name: 'WU-L05', query: { action: 'create', tab: 'entry' } })
}

function reminderClickable(item: ProductionReminder): boolean {
  return !!reminderTarget(item)
}

/** 去向规则见 reminder.ts；未处理异常上报（I06 + report_id）进诊断详情。 */
function openReminder(item: ProductionReminder): void {
  const target = reminderTarget(item)
  if (target) void router.push({ name: target.name, params: target.params, query: target.query })
  else ui.toast('这条提醒暂时不能在网页端打开', 'info')
}

function categoryBatchCount(category: { batch_count?: number }): string {
  return `${formatNumber(category.batch_count || 0)} 个批次`
}

function categoryPens(category: { pen_names?: string[] }): string {
  return category.pen_names?.length ? category.pen_names.join('、') : '—'
}

function rowBatchLabel(pen: { batch_count?: number; first_batch?: { batch_no?: string } }): string {
  if ((pen.batch_count || 0) > 1) return `${pen.first_batch?.batch_no || '在栏批次'} 等 ${pen.batch_count} 个批次`
  return pen.first_batch?.batch_no || ''
}

function rowBatchNote(pen: { batch_count?: number; first_batch?: { day_age?: number; stage_name?: string } }): string {
  if ((pen.batch_count || 0) > 1) return '在栏多个批次'
  if (pen.first_batch) return `${formatNumber(pen.first_batch.day_age || 0)} 日龄 · ${pen.first_batch.stage_name || '—'}`
  return ''
}

function penEmptyNote(pen: {
  is_breeding_pen?: boolean
  batch_count?: number
  last_ended_batch?: { batch_no?: string; end_date?: string }
}): string {
  if (pen.is_breeding_pen) return '种猪按耳号建档，在繁育中查看'
  if (pen.last_ended_batch?.batch_no) {
    return `${pen.last_ended_batch.batch_no} 已于 ${monthDay(pen.last_ended_batch.end_date)} 全部出栏`
  }
  if ((pen.batch_count || 0) === 0) return '这个栏舍现在是空栏'
  return ''
}

function penActionText(pen: { is_breeding_pen?: boolean }): string {
  return pen.is_breeding_pen ? '去繁育' : '详情'
}
</script>

<template>
  <zw-shell side="user" active="WU-L01" :crumbs="['生产管理', '存栏总览']">
    <zw-page-head title="存栏总览" :desc="desc">
      <template #actions>
        <v-btn v-if="canAddPen" variant="outlined" prepend-icon="add" @click="openCreatePen">新增栏舍</v-btn>
        <v-btn v-if="canAddPen" color="primary" prepend-icon="move_to_inbox" @click="openEntry">批次入栏</v-btn>
      </template>
      <v-tabs v-if="!isStaff" v-model="activeTab" class="zw-tabs-line">
        <v-tab value="pen">按栏舍</v-tab>
        <v-tab value="category">按品类</v-tab>
        <v-tab value="batch">按批次</v-tab>
      </v-tabs>
    </zw-page-head>

    <div v-if="store.status === 'loading'" class="l01-loading">
      <div class="zw-grid-4 mb-4">
        <v-skeleton-loader v-for="item in 4" :key="item" type="article" />
      </div>
      <zw-card>
        <v-skeleton-loader type="table-row@6" />
      </zw-card>
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '当前角色无权查看存栏总览。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="存栏加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <zw-notice v-if="store.isFarmBlocked" type="warning" class="mb-4">
        这个养殖场还没有通过审核，审核通过后才能使用栏舍和批次功能。
        <template #action>
          <a @click="router.push({ name: 'WU-F03', params: { farmId: String(store.overview?.farm_id || '') } })">查看审核结果</a>
        </template>
      </zw-notice>

      <div class="zw-grid-4 mb-4">
        <zw-stat label="总存栏" icon="fence" :value="formatNumber(store.summary?.total_stock || 0)" :unit="store.unit" :foot="store.categoryStockFoot" tone="primary" />
        <zw-stat label="在养批次" icon="folder_open" :value="formatNumber(store.summary?.active_batch_count || 0)" unit="个" :foot="store.batchFoot" />
        <zw-stat
          label="死亡率"
          icon="heart_minus"
          :value="store.mortalityText"
          :unit="store.mortalityUnit"
          foot="在养批次累计死淘 ÷ 累计入栏"
          info="按在养批次的累计死淘数除以累计入栏数计算，入栏数为 0 时显示“无数据”"
        />
        <zw-stat label="栏舍占用" icon="donut_small" :value="store.occupancyText" unit="%" :foot="store.capacityFoot" />
      </div>

      <div class="l01-main">
        <div class="l01-main-left">
          <zw-card v-if="store.tab === 'pen'" title="栏舍" :sub="`${store.filteredPens.length} 个`" flush>
            <template #extra>
              <span class="zw-faint body-s">排序</span>
              <v-select v-model="store.penSort" :items="penSortItems" class="zw-w-140" />
            </template>
            <div v-if="noMatching">
              <zw-empty icon="search_off" title="没有找到匹配的栏舍或批次" desc="换一个关键词试试。">
                <v-btn variant="text" @click="store.clearKeyword()">清除搜索</v-btn>
              </zw-empty>
            </div>
            <div v-else-if="store.hasNoPens">
              <zw-empty icon="fence" title="还没有栏舍" desc="先新增栏舍，再登记批次入栏。">
                <v-btn v-if="canAddPen" color="primary" @click="openCreatePen">新增栏舍</v-btn>
              </zw-empty>
            </div>
            <template v-else>
              <v-data-table class="l01-tbl" :headers="penHeaders" :items="store.pagedPens" item-value="pen_id" hide-default-footer>
                <template #item.pen_name="{ item }">
                  <div class="zw-cell-main">{{ item.pen_name }}</div>
                  <div class="zw-cell-sub num">{{ item.pen_code }} · {{ item.pen_type_text || item.category_name }}</div>
                </template>
                <template #item.batch="{ item }">
                  <template v-if="item.first_batch">
                    <div class="num">{{ rowBatchLabel(item) }}</div>
                    <div class="zw-cell-sub">{{ rowBatchNote(item) }}</div>
                  </template>
                  <span v-else class="zw-faint">{{ penEmptyNote(item) }}</span>
                </template>
                <template #item.stock="{ item }">
                  <span v-if="!item.is_breeding_pen" class="num" :class="{ 'zw-faint': !item.stock_count }">
                    {{ formatNumber(item.stock_count || 0) }} / {{ formatNumber(item.capacity || 0) }} {{ store.unit }}
                  </span>
                  <span v-else class="zw-faint body-s">在繁育中查看</span>
                </template>
                <template #item.occupancy="{ item }">
                  <div v-if="!item.is_breeding_pen" class="l01-occ">
                    <v-progress-linear :model-value="occupancyPercent(item.occupancy_rate)" height="6" rounded />
                    <span class="num zw-muted">{{ formatPercent(item.occupancy_rate, 1) }}</span>
                  </div>
                  <span v-else class="zw-faint">—</span>
                </template>
                <template #item.mortality="{ item }">
                  <span class="num">{{ item.is_breeding_pen ? '—' : formatPercent(item.mortality_rate, 1) }}</span>
                </template>
                <template #item.status="{ item }">
                  <zw-tag v-if="item.display_status === 'DISABLED'" type="error">已停用</zw-tag>
                  <zw-tag v-else-if="item.in_withdrawal" type="warning" icon="hourglass_top">休药中</zw-tag>
                  <zw-tag v-else-if="item.display_status === 'EMPTY'">空栏</zw-tag>
                  <zw-tag v-else type="primary">在养</zw-tag>
                </template>
                <template #item.actions="{ item }">
                  <span class="zw-cell-actions">
                    <a @click="item.is_breeding_pen ? openBreeding() : openPen(item.pen_id)">{{ penActionText(item) }}</a>
                  </span>
                </template>
              </v-data-table>
              <div class="zw-table-foot l01-table-foot">
                <span class="zw-faint body-s">多个在栏批次时，死亡率为栏舍内各批次合计</span>
                <zw-pager :total="store.pageTotal" :page="store.page" :size="store.size" @update:page="store.setPage" @update:size="store.setSize" />
              </div>
            </template>
          </zw-card>

          <zw-card v-else-if="store.tab === 'category'" title="品类" :sub="`${store.filteredCategories.length} 个`" flush>
            <div v-if="noMatching">
              <zw-empty icon="search_off" title="没有找到匹配的栏舍或批次" desc="换一个关键词试试。">
                <v-btn variant="text" @click="store.clearKeyword()">清除搜索</v-btn>
              </zw-empty>
            </div>
            <zw-empty v-else-if="!store.filteredCategories.length" icon="category" title="还没有品类" desc="先新增栏舍并登记批次。" />
            <v-table v-else class="l01-category-table">
              <thead>
                <tr>
                  <th>品类</th>
                  <th>批次</th>
                  <th>所在栏舍</th>
                  <th class="text-end">存栏</th>
                  <th class="text-end">死亡率</th>
                  <th class="text-end" style="width: 100px">操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="category in store.filteredCategories" :key="category.category_id">
                  <tr>
                    <td>
                      <span v-if="!category.is_breeding" class="l01-expand zw-cell-main" @click="store.toggleCategory(category.category_id)">
                        <zw-icon :name="store.isCategoryOpen(category.category_id) ? 'expand_more' : 'chevron_right'" :size="18" />
                        {{ category.category_name }}
                      </span>
                      <span v-else class="l01-expand zw-cell-main">
                        <zw-icon name="chevron_right" :size="18" style="visibility: hidden" />
                        {{ category.category_name }}
                      </span>
                    </td>
                    <td>{{ category.is_breeding ? '种猪按耳号建档，在繁育中查看' : categoryBatchCount(category) }}</td>
                    <td>{{ categoryPens(category) }}</td>
                    <td class="text-end num">{{ formatNumber(category.stock || 0) }} {{ store.unit }}</td>
                    <td class="text-end num">{{ formatPercent(category.mortality_rate, 1) }}</td>
                    <td class="text-end">
                      <span class="zw-cell-actions">
                        <a v-if="category.is_breeding" @click="openBreeding">去繁育</a>
                        <a v-else @click="store.toggleCategory(category.category_id)">
                          {{ store.isCategoryOpen(category.category_id) ? '收起' : '展开' }}
                        </a>
                      </span>
                    </td>
                  </tr>
                  <template v-if="!category.is_breeding && store.isCategoryOpen(category.category_id)">
                    <tr v-for="batch in category.batches || []" :key="batch.batch_id" class="l01-sub">
                      <td class="l01-sub-cell"><span class="num">{{ batch.batch_no }}</span></td>
                      <td class="l01-sub-cell zw-muted">{{ batch.breed_name }} · {{ formatNumber(batch.day_age || 0) }} 日龄 · {{ batch.stage_name }}</td>
                      <td class="l01-sub-cell">{{ (batch.pens || []).map((pen) => pen.pen_name).join('、') }}</td>
                      <td class="l01-sub-cell text-end num">{{ formatNumber(batch.current_count || 0) }} {{ batch.unit_text || store.unit }}</td>
                      <td class="l01-sub-cell text-end num">{{ formatPercent(batch.mortality_rate, 1) }}</td>
                      <td class="l01-sub-cell text-end"><span class="zw-cell-actions"><a @click="openBatch(batch.batch_id)">档案</a></span></td>
                    </tr>
                  </template>
                </template>
              </tbody>
            </v-table>
          </zw-card>

          <zw-card v-else title="在养批次" :sub="`${store.filteredBatches.length} 个`" flush>
            <template #extra>
              <v-text-field
                v-model="store.keyword"
                placeholder="输入栏舍名称或批次号"
                prepend-inner-icon="search"
                class="zw-w-240"
                clearable
                @update:model-value="store.setKeyword(String($event || ''))"
              />
              <v-select v-model="store.batchSort" :items="batchSortItems" class="zw-w-140" />
            </template>
            <div v-if="noMatching">
              <zw-empty icon="search_off" title="没有找到匹配的栏舍或批次" desc="换一个关键词试试。">
                <v-btn variant="text" @click="store.clearKeyword()">清除搜索</v-btn>
              </zw-empty>
            </div>
            <zw-empty v-else-if="batchEmpty" icon="folder_open" title="还没有在养批次" desc="点“批次入栏”登记第一个批次。">
              <v-btn v-if="canAddPen" color="primary" @click="openEntry">批次入栏</v-btn>
            </zw-empty>
            <template v-else>
              <v-data-table :headers="batchHeaders" :items="store.pagedBatches" item-value="batch_id" hide-default-footer>
                <template #item.batch_no="{ item }">
                  <span class="batch-link" @click="openBatch(item.batch_id)">
                    <span class="zw-link num">{{ item.batch_no }}</span>
                    <zw-tag v-if="item.purpose === 'BREEDING'" size="small">种用</zw-tag>
                  </span>
                </template>
                <template #item.breed="{ item }">
                  <div>{{ item.breed_name }}</div>
                  <div class="zw-cell-sub">{{ item.category_name }} · {{ item.source === 'SELF_BRED' ? '自繁' : '外购' }}</div>
                </template>
                <template #item.pen="{ item }">{{ (item.pens || []).map((pen) => pen.pen_name).join('、') || '—' }}</template>
                <template #item.day_age="{ item }">
                  <div class="num">{{ formatNumber(item.day_age || 0) }} 日龄</div>
                  <div class="zw-cell-sub">{{ item.stage_name }}</div>
                </template>
                <template #item.current_count="{ item }"><span class="num">{{ formatNumber(item.current_count || 0) }} {{ item.unit_text || store.unit }}</span></template>
                <template #item.mortality="{ item }"><span class="num">{{ formatPercent(item.mortality_rate, 1) }}</span></template>
                <template #item.status="{ item }">
                  <zw-tag v-if="item.in_withdrawal" type="warning" icon="hourglass_top">休药中</zw-tag>
                  <zw-tag v-else type="primary">在养</zw-tag>
                </template>
                <template #item.actions="{ item }"><span class="zw-cell-actions"><a @click="openBatch(item.batch_id)">档案</a></span></template>
              </v-data-table>
              <div class="zw-table-foot l01-table-foot">
                <span class="zw-faint body-s">已结束的批次在“批次列表与查询”中查看</span>
                <zw-pager :total="store.pageTotal" :page="store.page" :size="store.size" @update:page="store.setPage" @update:size="store.setSize" />
              </div>
            </template>
          </zw-card>
        </div>

        <div class="l01-aside">
          <zw-card v-if="store.reminders.length" title="生产提醒" :sub="`${store.reminderTotal} 条`">
            <div
              v-for="(item, index) in store.reminders"
              :key="`${item.reminder_type}-${index}`"
              class="l01-warn"
              :class="{ 'l01-warn--click': reminderClickable(item) }"
              @click="openReminder(item)"
            >
              <span class="zw-ico-tile zw-ico-tile--warning"><zw-icon :name="reminderIcon(item.reminder_type)" :size="18" /></span>
              <div class="l01-warn-main">
                <div class="l01-warn-t">
                  <span class="zw-cell-main">{{ item.title }}</span>
                  <zw-tag v-if="item.tag_text" type="warning" size="small">{{ item.tag_text }}</zw-tag>
                </div>
                <div class="zw-cell-sub">{{ item.subtitle }}</div>
              </div>
            </div>
            <button v-if="store.reminderTotal > store.reminders.length" class="l01-more" type="button" @click="router.push({ name: 'WU-T01' })">
              查看全部 {{ store.reminderTotal }} 条提醒
            </button>
          </zw-card>

          <zw-card title="存栏构成">
            <zw-donut
              :size="120"
              :thickness="16"
              :value="formatNumber(store.summary?.total_stock || 0)"
              title="总存栏（头）"
              :items="(store.summary?.categories || []).map((item) => ({
                label: item.category_name || '品类',
                value: Number(item.stock || 0),
                text: `${formatNumber(item.stock || 0)} ${store.unit}`
              }))"
            />
          </zw-card>
        </div>
      </div>
    </template>
  </zw-shell>
</template>

<style scoped>
.l01-loading { display: flex; flex-direction: column; }
.l01-main { display: flex; align-items: flex-start; gap: 16px; }
.l01-main-left { flex: 1; min-width: 0; }
.l01-aside { width: 320px; flex-shrink: 0; display: flex; flex-direction: column; gap: 16px; }
.l01-occ { display: flex; align-items: center; gap: 8px; }
.l01-occ :deep(.v-progress-linear) { width: 72px; flex: 0 0 72px; }
.l01-tbl :deep(td:not(:nth-child(2))) { white-space: nowrap; }
.l01-category-table :deep(td) { height: 48px; }
.l01-expand { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; }
.l01-sub-cell { background: var(--fill-1); }
.l01-table-foot { padding: 12px 16px !important; border-top: 1px solid var(--line-1); }
.l01-warn { display: flex; gap: 12px; padding: 12px 0; }
.l01-warn + .l01-warn { border-top: 1px dashed var(--line-1); }
.l01-warn--click { cursor: pointer; }
.l01-warn--click:hover .zw-cell-main { color: var(--primary); }
.l01-warn:first-of-type { padding-top: 0; }
.l01-warn:last-of-type { padding-bottom: 0; }
.l01-warn-main { flex: 1; min-width: 0; }
.l01-warn-t { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.l01-more { width: 100%; margin-top: 12px; padding: 10px 0 0; border: 0; border-top: 1px dashed var(--line-1); background: transparent; color: var(--primary); text-align: left; font: inherit; font-size: 13px; cursor: pointer; }
.batch-link { display: inline-flex; align-items: center; gap: 6px; }
</style>
