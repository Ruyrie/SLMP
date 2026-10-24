<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatNumber, formatPercent } from '@zmt/shared'
import type { DataTableHeader } from 'vuetify'
import { usePenListStore } from '@/stores/herd/penList'
import { useFarmReload } from '@/composables/useFarmReload'
import { useRole } from '@/composables/useRole'
import { useFarmStore } from '@/stores/farm'
import { occupancyPercent, overCapacityText, penStatusText } from '@/stores/herd/shared'

const store = usePenListStore()
const farm = useFarmStore()
const router = useRouter()
const route = useRoute()
const { isStaff } = useRole()

const headers: DataTableHeader[] = [
  { title: '编号', key: 'pen_code', sortable: false, width: 96 },
  { title: '栏舍名称', key: 'pen_name', sortable: false },
  { title: '类型', key: 'pen_type_text', sortable: false, width: 96 },
  { title: '适用品类', key: 'category_name', sortable: false, width: 104 },
  { title: '容量', key: 'capacity', sortable: false, align: 'end', width: 92 },
  { title: '在栏', key: 'stock_count', sortable: false, align: 'end', width: 108 },
  { title: '占用率', key: 'occupancy', sortable: false, width: 150 },
  { title: '负责员工', key: 'staff', sortable: false, width: 108 },
  { title: '状态', key: 'status', sortable: false, width: 88 },
  { title: '操作', key: 'actions', sortable: false, align: 'end', width: 146 }
]

const canManage = computed(() => !isStaff.value)
const desc = computed(() => {
  const name = farm.farmName || '当前养殖场'
  return `${name} · 栏舍编号本场内唯一，保存后不能修改；负责员工在成员管理中分配`
})
const summaryText = computed(() => {
  const value = store.summary
  return `在用 ${value.inUse} 个 · 空栏 ${value.empty} 个${value.disabled ? ` · 已停用 ${value.disabled} 个` : ''} · 总容量 ${formatNumber(value.capacity)} 头`
})
const detailSub = computed(() => {
  const detail = store.detail
  if (!detail) return ''
  return `${detail.pen_code || ''} · ${detail.category_name || ''} · ${detail.display_status_text || penStatusText({ display_status: detail.display_status })}`
})
const editingStock = computed(() => {
  if (store.formMode !== 'edit') return 0
  return Number(store.details[store.form.penId]?.stock_count || 0)
})
const capacityWarning = computed(() => {
  if (!editingStock.value) return ''
  const capacity = Number(store.form.capacity || 0)
  const over = editingStock.value - capacity
  if (over <= 0) return ''
  return `当前在栏 ${formatNumber(editingStock.value)} 头，超过新容量 ${formatNumber(over)} 头`
})
const editBreedingPen = computed(() => !!store.details[store.form.penId]?.is_breeding_pen)
const canDisableDetail = computed(() => {
  const detail = store.detail
  if (!detail || detail.is_breeding_pen) return false
  return detail.display_status === 'EMPTY' && !!detail.can_disable
})
const canEnableDetail = computed(() => store.detail?.display_status === 'DISABLED')
const typeChoices = computed(() => (store.formOptions?.pen_types || []).map((item) => ({
  label: item.label || String(item.value),
  value: String(item.value)
})))

const formError = (field: string): string => store.formErrors[field] || ''

function onFilterChange(): void {
  store.applyFilter()
}

function resetFilters(): void {
  store.resetFilters()
}

function openEdit(penId?: number): void {
  if (penId) void store.openEdit(penId)
}

function openDetail(penId?: number): void {
  if (penId) void store.openDetail(penId)
}

function openBatch(batchId?: number): void {
  if (!batchId) return
  store.detailDrawer = false
  void router.push({ name: 'WU-L04', params: { batchId: String(batchId) } })
}

function goMembers(): void {
  if (!farm.currentFarmId) return
  void router.push({ name: 'WU-F04', params: { farmId: String(farm.currentFarmId) } })
}

async function handleQuery(): Promise<void> {
  if (route.query.action === 'create' && canManage.value) {
    await store.openCreate()
  }
  const penId = Number(route.query.penId || 0)
  if (penId) await store.openDetail(penId)
}

onMounted(async () => {
  await store.load()
  if (!canManage.value) return
  await handleQuery()
})
useFarmReload(async () => {
  store.reset()
  await store.load()
})
watch(() => [route.query.action, route.query.penId], () => {
  if (!canManage.value || store.status !== 'ready') return
  void handleQuery()
})
</script>

<template>
  <zw-shell side="user" active="WU-L02" :crumbs="['生产管理', '栏舍管理']">
    <zw-page-head title="栏舍管理" :desc="desc">
      <template #actions>
        <v-btn v-if="canManage" color="primary" prepend-icon="add" :loading="store.loadingOptions" @click="store.openCreate()">新增栏舍</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'">
      <v-skeleton-loader type="article, table-row@7" />
    </div>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" tone="warning" title="无权查看" :desc="store.error || '当前角色无权管理栏舍。'">
        <v-btn variant="outlined" @click="router.push({ name: 'WU-T01' })">返回工作台</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" tone="error" title="栏舍加载失败" :desc="store.error">
        <v-btn color="primary" variant="outlined" @click="store.load(true)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <template v-else>
      <div class="zw-toolbar">
        <div class="zw-toolbar-l">
          <v-text-field
            v-model="store.query.keyword"
            placeholder="搜索栏舍名称或编号"
            prepend-inner-icon="search"
            class="zw-w-240"
            clearable
            @keydown.enter="onFilterChange"
            @click:clear="onFilterChange"
          />
          <v-select
            v-model="store.query.penType"
            :items="store.typeItems"
            class="zw-w-140"
            @update:model-value="onFilterChange"
          />
          <v-select
            v-model="store.query.displayStatus"
            :items="store.statusItems"
            class="zw-w-140"
            @update:model-value="onFilterChange"
          />
          <v-btn variant="text" color="primary" @click="resetFilters">重置</v-btn>
        </div>
        <div class="zw-toolbar-r"><span class="zw-faint body-s">{{ summaryText }}</span></div>
      </div>

      <zw-card flush>
        <div v-if="store.status === 'empty' || (store.status === 'ready' && store.pens.length === 0)">
          <zw-empty icon="fence" title="还没有栏舍" desc="先新增栏舍，再登记批次入栏。">
            <v-btn v-if="canManage" color="primary" @click="store.openCreate()">新增栏舍</v-btn>
          </zw-empty>
        </div>
        <div v-else-if="store.filteredEmpty">
          <zw-empty icon="search_off" title="没有找到匹配的栏舍" desc="换一个关键词或清除筛选。">
            <v-btn variant="text" @click="resetFilters">清除筛选</v-btn>
          </zw-empty>
        </div>
        <template v-else>
          <v-data-table :headers="headers" :items="store.pagedPens" item-value="pen_id" hide-default-footer class="l02-table">
            <template #item.pen_code="{ item }"><span class="num">{{ item.pen_code }}</span></template>
            <template #item.pen_name="{ item }"><span class="zw-cell-main">{{ item.pen_name }}</span></template>
            <template #item.pen_type_text="{ item }">{{ item.pen_type_text || '—' }}</template>
            <template #item.category_name="{ item }">{{ item.category_name || '—' }}</template>
            <template #item.capacity="{ item }"><span class="num">{{ item.capacity ? `${formatNumber(item.capacity)} 头` : '' }}</span></template>
            <template #item.stock_count="{ item }">
              <span v-if="!item.is_breeding_pen" class="num" :class="{ 'zw-faint': !item.stock_count }">{{ formatNumber(item.stock_count || 0) }} 头</span>
              <span v-else class="zw-faint body-s">在繁育中查看</span>
            </template>
            <template #item.occupancy="{ item }">
              <div v-if="!item.is_breeding_pen" class="l02-occ">
                <v-progress-linear :model-value="occupancyPercent(item.occupancy_rate)" height="6" rounded />
                <span class="num zw-muted">{{ formatPercent(item.occupancy_rate, 1) }}</span>
              </div>
              <span v-else class="zw-faint">—</span>
            </template>
            <template #item.staff="{ item }">
              <span v-if="store.staffOf(item.pen_id)">{{ store.staffOf(item.pen_id) }}</span>
              <span v-else class="zw-faint">{{ store.details[Number(item.pen_id)] ? '未分配' : '—' }}</span>
            </template>
            <template #item.status="{ item }">
              <zw-tag v-if="item.display_status === 'DISABLED'" type="error">已停用</zw-tag>
              <zw-tag v-else-if="item.display_status === 'EMPTY'">空栏</zw-tag>
              <zw-tag v-else type="primary">在用</zw-tag>
            </template>
            <template #item.actions="{ item }">
              <span class="zw-cell-actions">
                <a @click="openDetail(item.pen_id)">详情</a>
                <a v-if="canManage" @click="openEdit(item.pen_id)">编辑</a>
                <a v-if="canManage && store.isEnableVisible(item)" @click="store.enablePen(item)">启用</a>
                <a v-else-if="canManage && store.isDisableVisible(item)" class="danger" @click="store.askDisable(item)">停用</a>
              </span>
            </template>
          </v-data-table>
          <div class="zw-table-foot zw-table-foot--in">
            <span class="zw-faint body-s">有在栏批次的栏舍不能停用，空栏才显示“停用”</span>
            <zw-pager
              :total="store.total"
              :page="store.query.page"
              :size="store.query.size"
              @update:page="store.setPage"
              @update:size="store.setSize"
            />
          </div>
        </template>
      </zw-card>
    </template>

    <!-- 栏舍详情 -->
    <zw-drawer v-model="store.detailDrawer" :title="store.detail?.pen_name || '栏舍详情'" :sub="detailSub" width="560">
      <div v-if="store.detailLoading" class="l02-detail-loading">
        <v-skeleton-loader type="image, article, table-row@4" />
      </div>
      <zw-empty v-else-if="store.detailError" icon="cloud_off" tone="error" title="栏舍详情加载失败" :desc="store.detailError">
        <v-btn variant="outlined" @click="store.detailDrawer = false">关闭</v-btn>
      </zw-empty>
      <template v-else-if="store.detail">
        <div class="l02-cap" :class="{ 'l02-cap--over': Number(store.detail.over_capacity_count || 0) > 0 }">
          <div class="zw-between">
            <span class="zw-muted">容量占用</span>
            <span class="l02-cap-right">
              <zw-tag v-if="Number(store.detail.over_capacity_count || 0) > 0" type="warning" size="small">超出容量</zw-tag>
              <span class="num">{{ formatPercent(store.detail.occupancy_rate, 1) }}</span>
            </span>
          </div>
          <div class="l02-cap-v num">{{ formatNumber(store.detail.stock_count || 0) }}<small>/ {{ formatNumber(store.detail.capacity || 0) }} {{ store.detail.unit_text || '头' }}</small></div>
          <v-progress-linear
            :model-value="occupancyPercent(store.detail.occupancy_rate)"
            :color="Number(store.detail.over_capacity_count || 0) > 0 ? 'warning' : 'primary'"
            height="8"
            rounded
            class="mt-3"
          />
          <div v-if="store.detail.display_status !== 'DISABLED'" class="zw-faint body-s mt-2">
            <template v-if="Number(store.detail.over_capacity_count || 0) > 0">已超出容量 {{ formatNumber(store.detail.over_capacity_count) }} 头</template>
            <template v-else>还可转入 {{ formatNumber(store.detail.remaining_capacity || 0) }} 头，超过容量时需要确认后才能保存</template>
          </div>
        </div>

        <section class="l02-sec">
          <div class="zw-section-title">栏舍信息</div>
          <zw-desc :cols="2">
            <zw-desc-item label="编号"><span class="num">{{ store.detail.pen_code }}</span></zw-desc-item>
            <zw-desc-item label="类型">{{ store.detail.pen_type_text || '—' }}</zw-desc-item>
            <zw-desc-item label="适用品类">{{ store.detail.category_name || '—' }}</zw-desc-item>
            <zw-desc-item label="容量">{{ formatNumber(store.detail.capacity || 0) }} {{ store.detail.unit_text || '头' }}</zw-desc-item>
          </zw-desc>
        </section>

        <section class="l02-sec">
          <div class="zw-section-title">负责员工</div>
          <template v-if="store.detail.staff?.length">
            <div v-for="member in store.detail.staff" :key="member.user_id" class="l02-row">
              <zw-avatar :name="member.name || '员'" :size="32" />
              <div class="l02-row-main">
                <div class="zw-cell-main">{{ member.name }}</div>
                <div class="zw-cell-sub">{{ member.role_text || '员工' }} · 在成员管理中分配</div>
              </div>
              <a v-if="canManage" class="zw-link" @click="goMembers">去调整</a>
            </div>
          </template>
          <div v-else class="l02-row">
            <span class="zw-faint">还没有负责员工</span>
            <span class="l02-row-main" />
            <a v-if="canManage" class="zw-link" @click="goMembers">去分配</a>
          </div>
        </section>

        <section class="l02-sec">
          <div class="zw-section-title">在栏批次 · {{ store.detail.batches?.length || 0 }} 个</div>
          <template v-if="store.detail.batches?.length">
            <div v-for="batch in store.detail.batches" :key="batch.batch_id" class="l02-row l02-batch-row" @click="openBatch(batch.batch_id)">
              <span class="zw-ico-tile zw-ico-tile--primary"><zw-icon name="folder_open" :size="18" /></span>
              <div class="l02-row-main">
                <div class="zw-cell-main num">{{ batch.batch_no }}</div>
                <div class="zw-cell-sub">{{ batch.breed_title }} · {{ formatNumber(batch.day_age || 0) }} 日龄 · {{ formatNumber(batch.qty_in_pen || 0) }} {{ store.detail?.unit_text || '头' }}</div>
              </div>
              <zw-tag v-if="batch.in_withdrawal" type="warning" icon="hourglass_top">休药中</zw-tag>
              <zw-icon name="chevron_right" :size="20" color="text-3" />
            </div>
          </template>
          <div v-else class="l02-row"><span class="zw-faint">这个栏舍现在是空栏</span></div>
        </section>

        <section class="l02-sec">
          <div class="zw-section-title">修改记录</div>
          <v-skeleton-loader v-if="store.logsLoading" type="list-item-two-line@2" />
          <zw-timeline v-else-if="store.logItems.length" :items="store.logItems" />
          <div v-else class="l02-row"><span class="zw-faint">还没有修改记录</span></div>
        </section>
      </template>
      <template #foot>
        <span v-if="store.detail && canDisableDetail === false && store.detail.display_status !== 'DISABLED'" class="zw-foot-note">
          {{ store.detail.disable_block_reason || '空栏后才能停用栏舍' }}
        </span>
        <span v-else-if="store.detail?.display_status === 'DISABLED'" class="zw-foot-note">停用后历史记录照常保留</span>
        <v-btn
          v-if="canManage && canEnableDetail"
          variant="outlined"
          color="primary"
          prepend-icon="restart_alt"
          :loading="store.statusBusy"
          @click="store.detail && store.enablePen({ pen_id: store.detail.pen_id, pen_name: store.detail.pen_name })"
        >启用栏舍</v-btn>
        <v-btn
          v-else-if="canManage && canDisableDetail"
          variant="outlined"
          color="error"
          prepend-icon="block"
          :loading="store.statusBusy"
          @click="store.detail && store.askDisable({ pen_id: store.detail.pen_id, pen_name: store.detail.pen_name })"
        >停用栏舍</v-btn>
        <v-btn v-if="canManage && store.detail" color="primary" prepend-icon="edit" @click="store.detail && Number(store.detail.pen_id) && store.openEdit(Number(store.detail.pen_id))">编辑栏舍</v-btn>
      </template>
    </zw-drawer>

    <!-- 新增 / 编辑栏舍 -->
    <zw-drawer v-model="store.formDrawer" :title="store.formTitle" :sub="farm.farmName" width="560">
      <div v-if="store.loadingOptions" class="l02-detail-loading">
        <v-skeleton-loader type="article, table-row@4" />
      </div>
      <template v-else>
        <div class="zw-form-2">
          <zw-form-item label="栏舍编号" required :error="formError('pen_code')" :hint="store.formMode === 'edit' ? '编号不能修改' : '本场内唯一，保存后不能修改'">
            <v-text-field
              v-model="store.form.penCode"
              :readonly="store.formMode === 'edit'"
              :class="{ 'zw-readonly': store.formMode === 'edit' }"
              placeholder="例如 YF-06"
              maxlength="10"
              :error="!!formError('pen_code')"
              @update:model-value="store.form.penCode = String($event || '').toUpperCase()"
            />
          </zw-form-item>
          <zw-form-item label="栏舍名称" required :error="formError('pen_name')" hint="改名后，历史记录仍按编号关联">
            <v-text-field v-model="store.form.penName" placeholder="最多 20 个字" maxlength="20" :error="!!formError('pen_name')" />
          </zw-form-item>
          <zw-form-item label="类型" required :error="formError('pen_type')" style="grid-column: 1 / -1">
            <zw-seg v-model="store.form.penType" :items="typeChoices" />
          </zw-form-item>
          <zw-form-item label="适用品类" required :error="formError('category_id')" hint="从平台品类品种字典中选择">
            <v-select
              v-model="store.form.categoryId"
              :items="store.categoryItems"
              item-title="title"
              item-value="value"
              :error="!!formError('category_id')"
              placeholder="请选择适用品类"
            />
          </zw-form-item>
          <zw-form-item
            label="容量"
            required
            :error="formError('capacity')"
            :hint="capacityWarning || '转入后超过容量时，需要确认后才能保存'"
          >
            <v-text-field v-model.number="store.form.capacity" type="number" min="1" :suffix="store.formUnit" :error="!!formError('capacity')" />
          </zw-form-item>
        </div>
        <div v-if="capacityWarning" class="l02-warning">
          <zw-icon name="warning" :size="16" /> {{ capacityWarning }}
        </div>
        <zw-notice icon="badge" class="mt-6">
          负责员工在成员管理中分配。保存后，可以到成员管理为员工分配这个栏舍。
        </zw-notice>
        <div v-if="editBreedingPen" class="l02-warning">
          <zw-icon name="info" :size="16" /> 这个栏舍用于种猪繁育，容量可按实际产栏调整。
        </div>
      </template>
      <template #foot>
        <span class="zw-foot-note">带 * 为必填</span>
        <v-btn variant="outlined" @click="store.formDrawer = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" :disabled="store.submitting || store.loadingOptions" @click="store.saveForm()">保存</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog v-model="store.disableDialog" :title="store.disableText" icon="block" tone="error" width="460">
      <p>停用后不能再向这个栏舍入栏或转入，历史记录照常保留。</p>
      <template #actions>
        <v-btn variant="outlined" @click="store.disableDialog = false">取消</v-btn>
        <v-btn color="error" :loading="store.statusBusy" @click="store.confirmDisable()">停用</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.l02-occ { display: flex; align-items: center; gap: 8px; }
.l02-occ :deep(.v-progress-linear) { width: 80px; flex: 0 0 80px; }
.l02-cap { padding: 16px 20px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.l02-cap--over { border-color: var(--warning-container); background: #FFFCF7; }
.l02-cap-right { display: inline-flex; align-items: center; gap: 8px; }
.l02-cap-v { font-size: 28px; line-height: 36px; font-weight: 600; color: var(--text-1); }
.l02-cap-v small { font-size: 14px; font-weight: 400; color: var(--text-2); margin-left: 4px; }
.l02-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid var(--line-1); border-radius: var(--radius-sm); }
.l02-row + .l02-row { margin-top: 8px; }
.l02-row-main { flex: 1; min-width: 0; }
.l02-batch-row { cursor: pointer; }
.l02-batch-row:hover { background: var(--fill-1); }
.l02-sec { margin-top: 24px; }
.l02-warning { margin-top: 8px; display: flex; align-items: center; gap: 6px; color: var(--warning); font-size: 12px; }
.l02-detail-loading { padding: 4px 0; }
.l02-table :deep(td) { white-space: nowrap; }
</style>
