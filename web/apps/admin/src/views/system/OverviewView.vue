<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useOverviewStore } from '@/stores/system/overview'
import type { FarmMapCluster, OverviewPendingItem } from '@/api/system'
import { dateRange, fmtDate, fmtDateTime, fmtShortDate, fmtTime, fmtValue, hoursText, mapPoint, pct, shortRegion } from '@/views/system/shared'

const router = useRouter()
const store = useOverviewStore()

const data = computed(() => store.data)
const farmStat = computed(() => data.value?.farm_stat || null)
const userStat = computed(() => data.value?.user_stat || null)
const activeStat = computed(() => data.value?.active_stat || null)
const activeUsers = computed(() => activeStat.value?.users_7d ?? activeStat.value?.users7d ?? 0)
const activeUsersRatio = computed(() => activeStat.value?.users_7d_ratio ?? activeStat.value?.users7d_ratio)
const activeUsersToday = computed(() => activeStat.value?.users_today ?? 0)
const activeFarms = computed(() => activeStat.value?.farms_7d ?? activeStat.value?.farms7d ?? 0)
const activeFarmsRatio = computed(() => activeStat.value?.farms_7d_ratio ?? activeStat.value?.farms7d_ratio)
const pendingItems = computed(() => data.value?.pending?.items || [])
const mapClusters = computed(() => store.map?.clusters || [])
const region = computed(() => store.region)

const asOfText = computed(() => (data.value?.as_of ? `${fmtDate(data.value.as_of)} ${fmtTime(data.value.as_of)}` : '—'))
const mapFootLeft = computed(
  () => `点聚合标记查看该省的养殖场；已驳回待重新提交的 ${farmStat.value?.rejected ?? 0} 个不在地图上显示`
)

const mapLegend = computed(() => [
  { type: 'primary', label: `已通过 ${store.map?.legend?.approved || 0}` },
  { type: 'warning', label: `待审核 ${store.map?.legend?.pending || 0}` },
  { type: 'error', label: `已冻结 ${store.map?.legend?.frozen || 0}` }
])

const todoIcon: Record<string, string> = {
  FARM_AUDIT: 'domain_add',
  FARM_CHANGE: 'edit_location_alt',
  CHANGE_REQUEST: 'edit_note',
  ACCOUNT_RECOVERY: 'manage_accounts'
}

const categoryItems = computed(() =>
  (data.value?.category_dist || []).map((item) => ({
    label: item.species_text,
    value: item.count,
    text: `${item.count} 个 · ${pct(item.ratio)}`
  }))
)

const categoryTotal = computed(() => farmStat.value?.approved || 0)
const monthItems = computed(() => data.value?.month_audit?.items || [])
const monthRange = computed(() => dateRange(data.value?.month_audit?.range_start, data.value?.month_audit?.range_end))

const trendMetrics = [
  { label: '活跃账号', value: 'ACTIVE_USER' },
  { label: '新注册账号', value: 'NEW_USER' },
  { label: '生产登记次数', value: 'PRODUCTION_RECORD' }
]

const trendLabels = computed(() => (store.trend?.points || []).map((point) => fmtShortDate(point.date)))
const trendValues = computed(() => (store.trend?.points || []).map((point) => point.value))
const trendAvg = computed(() => (store.trend?.points || []).map((point) => (point.avg7 === undefined || point.avg7 === null ? null : Number(point.avg7))))
const trendHasData = computed(() => (store.trend?.summary?.max_value || 0) > 0)
const trendSeries = computed(() => [
  { name: store.trend?.metric_text || '日活跃账号', data: trendValues.value, area: true },
  { name: '7 日平均', data: trendAvg.value, color: 'chart-ref', dashed: true }
])

const failedLabels: Record<string, string> = {
  pending: '待处理',
  farm_stat: '养殖场统计',
  user_stat: '用户统计',
  active_stat: '活跃度',
  category_dist: '品类分布',
  month_audit: '本月审核',
  recent_ops: '最近操作'
}

const failedText = computed(() => (data.value?.failed_blocks || []).map((block) => failedLabels[block] || block).join('、'))

const recentItems = computed(() =>
  (data.value?.recent_ops || []).map((op) => {
    const tone =
      op.op_action_text.includes('通过') || op.op_action_text.includes('启用')
        ? 'success'
        : op.op_action_text.includes('驳回') || op.op_action_text.includes('停用') || op.op_action_text.includes('冻结')
          ? 'error'
          : op.op_action_text.includes('发布') || op.op_action_text.includes('发送')
            ? 'primary'
            : 'neutral'
    const icon =
      tone === 'success' ? 'check' : tone === 'error' ? 'block' : op.op_action_text.includes('登录') ? 'login' : 'history'
    return {
      title: `${op.operator_name} ${op.op_action_text}`,
      time: fmtTime(op.created_at),
      desc: [op.target_name, op.op_type_text].filter(Boolean).join(' · '),
      tone,
      icon
    }
  })
)

function clusterPoint(cluster: FarmMapCluster): { x: number; y: number } {
  return mapPoint(cluster.lng, cluster.lat)
}

function clusterTone(cluster: FarmMapCluster): string {
  if (cluster.frozen > 0) return 'error'
  if (cluster.pending > 0) return 'warning'
  return 'primary'
}

function goPending(item: OverviewPendingItem): void {
  if (!item.page_code) return
  void router.push({ name: item.page_code })
}

function todoFoot(item: OverviewPendingItem): string {
  if (!item.count) return '当前没有待处理'
  if (item.type === 'ACCOUNT_RECOVERY') return '恢复码均已核对正确，待核实身份'
  const parts: string[] = []
  if (item.earliest_at) parts.push(`最早 ${fmtDateTime(item.earliest_at)} 提交`)
  if (item.overdue_count) parts.push(`${item.overdue_count} 项超过 48 小时`)
  if (item.type === 'CHANGE_REQUEST' && item.sellable_date_count) parts.push(`其中影响可出栏日期 ${item.sellable_date_count} 项`)
  return parts.join(' · ')
}

function openRegion(cluster: FarmMapCluster): void {
  void store.openRegion(cluster)
}

function goFarms(): void {
  const code = region.value?.region_code
  if (code) void router.push({ name: 'WA-S04', query: { province_code: code, region_name: region.value?.region_name || '' } })
  else void router.push({ name: 'WA-S04' })
}

function goAudit(): void {
  void router.push({ name: 'WA-R01' })
}

function refreshAll(): void {
  void store.refresh()
}

onMounted(() => {
  void store.load()
})
</script>

<template>
  <zw-shell side="admin" active="WA-S02" :crumbs="['平台概览']">
    <zw-page-head
      title="平台概览"
      :desc="`数据截至 ${asOfText}。管理端只显示汇总数据，不显示养殖户的单笔账目和凭证。`"
    >
      <template #actions>
        <v-btn variant="outlined" prepend-icon="refresh" :loading="store.refreshing" @click="refreshAll">刷新</v-btn>
      </template>
    </zw-page-head>

    <div v-if="store.status === 'loading'" class="ov-loading">
      <div class="zw-grid-4">
        <v-skeleton-loader v-for="i in 4" :key="i" type="card@1" />
      </div>
      <div class="ov-row">
        <v-skeleton-loader type="image@1" height="420" />
        <v-skeleton-loader type="card@2" height="420" />
      </div>
    </div>

    <zw-empty v-else-if="store.status === 'forbidden'" icon="lock" title="你没有平台概览的查看权限" :desc="store.error">
      <v-btn variant="outlined" @click="refreshAll">重新加载</v-btn>
    </zw-empty>

    <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="平台概览加载失败" :desc="store.error" tone="error">
      <v-btn variant="outlined" @click="refreshAll">重新加载</v-btn>
    </zw-empty>

    <template v-else-if="data">
      <zw-notice v-if="failedText" type="warning" icon="warning" class="ov-notice">
        部分数据加载失败：{{ failedText }}。其余卡片已按最新数据展示，可稍后刷新重试。
      </zw-notice>

      <zw-card title="待处理" :sub="`共 ${store.pendingTotal} 项`">
        <template #extra>
          <zw-tag v-if="store.overdueTotal" type="warning" icon="schedule">{{ store.overdueTotal }} 项已等待超过 48 小时</zw-tag>
          <span v-else class="zw-faint body-s">没有超时事项</span>
        </template>
        <div v-if="pendingItems.length" class="ov-todo">
          <div
            v-for="item in pendingItems"
            :key="item.type"
            class="ov-todo-item"
            :class="{ empty: !item.count }"
            @click="goPending(item)"
          >
            <span class="zw-ico-tile zw-ico-tile--lg zw-ico-tile--warning"><zw-icon :name="todoIcon[item.type] || 'assignment'" :size="22" /></span>
            <div class="ov-todo-main">
              <div class="ov-todo-label">
                <span>{{ item.type_text }}</span>
                <a v-if="item.can_handle !== false" class="zw-link body-s" @click.stop="goPending(item)">去处理</a>
                <span v-else class="zw-faint body-s">无处理权限</span>
              </div>
              <div class="ov-todo-num num">{{ item.count }}<small>项</small></div>
              <div class="ov-todo-sub" :class="{ late: !!item.overdue_count }">{{ todoFoot(item) }}</div>
            </div>
          </div>
        </div>
        <div v-else class="ov-nodata">暂无数据</div>
      </zw-card>

      <div class="zw-grid-4 ov-stats">
        <zw-stat
          label="养殖场（已通过）"
          icon="domain"
          :value="fmtValue(farmStat?.approved)"
          unit="个"
          :foot="`正常 ${farmStat?.normal ?? 0} · 已停用 ${farmStat?.disabled ?? 0} · 已冻结 ${farmStat?.frozen ?? 0}`"
          :info="`已通过审核的养殖场。另有待审核 ${farmStat?.pending ?? 0} 个、已驳回待重新提交 ${farmStat?.rejected ?? 0} 个，平台共 ${farmStat?.total ?? 0} 个`"
        />
        <zw-stat
          label="用户账号"
          icon="group"
          :value="fmtValue(userStat?.total)"
          unit="个"
          :foot="`正常 ${userStat?.normal ?? 0} · 已停用 ${userStat?.disabled ?? 0} · 本月新注册 ${userStat?.new_this_month ?? 0}`"
        />
        <zw-stat
          label="近 7 天活跃账号"
          icon="person_check"
          :value="fmtValue(activeUsers)"
          unit="个"
          :foot="`占正常账号 ${pct(activeUsersRatio)} · 今天已活跃 ${activeUsersToday}`"
          :info="`${dateRange(activeStat?.range_start, activeStat?.range_end)} 在 App 或网页版登录并有操作的账号数（去重）`"
        />
        <zw-stat
          label="近 7 天活跃养殖场"
          icon="monitoring"
          :value="fmtValue(activeFarms)"
          unit="个"
          :foot="`占正常使用的 ${farmStat?.normal ?? 0} 个养殖场 ${pct(activeFarmsRatio)}`"
          :info="`${dateRange(activeStat?.range_start, activeStat?.range_end)} 有生产登记的养殖场数`"
        />
      </div>

      <div class="ov-row">
        <zw-card title="养殖场分布" sub="按省份聚合 · 按审核状态着色" flush class="ov-map-card">
          <template #extra>
            <v-select
              :model-value="store.categoryGroup"
              :items="[
                { title: '全部品类', value: '' },
                { title: '猪', value: 'PIG' },
                { title: '禽', value: 'POULTRY' },
                { title: '牛', value: 'CATTLE' },
                { title: '羊', value: 'SHEEP' }
              ]"
              class="zw-w-120"
              density="compact"
              hide-details
              @update:model-value="store.setCategory(String($event))"
            />
          </template>
          <div v-if="store.mapLoading" class="map-loading"><v-skeleton-loader type="image@1" /></div>
          <zw-empty v-else-if="store.mapError" icon="cloud_off" title="地图没有加载出来" :desc="store.mapError" tone="error">
            <v-btn variant="text" color="primary" @click="store.fetchMap()">重新加载</v-btn>
          </zw-empty>
          <div v-else-if="!mapClusters.length" class="ov-map-empty">
            <zw-icon name="map" :size="36" />
            <div class="ov-nodata-title">暂无数据</div>
            <div class="zw-faint">当前筛选下还没有已通过或待审核的养殖场</div>
          </div>
          <zw-map v-else :height="330" regions :legend="mapLegend">
            <button
              v-for="cluster in mapClusters"
              :key="cluster.region_code"
              type="button"
              class="ov-cluster"
              :class="[`ov-cluster--${clusterTone(cluster)}`, { on: region?.region_code && cluster.region_code.startsWith(region.region_code) }]"
              :style="{ left: `${clusterPoint(cluster).x}%`, top: `${clusterPoint(cluster).y}%` }"
              :aria-label="`${cluster.region_name} ${cluster.total} 个养殖场`"
              @click="openRegion(cluster)"
            >
              <span class="ov-cluster-label">{{ shortRegion(cluster.region_name) }}</span>
              <span class="ov-cluster-count num">{{ cluster.total }}</span>
            </button>

            <div v-if="store.regionOpen" class="ov-pop" @click.stop>
              <div class="ov-pop-head">
                <span class="ov-pop-title">{{ region?.region_name || '地区明细' }}<template v-if="region"> · {{ region.total }} 个养殖场</template></span>
                <button class="zw-icon-btn" type="button" aria-label="关闭" @click="store.closeRegion()">
                  <zw-icon name="close" :size="16" />
                </button>
              </div>
              <v-skeleton-loader v-if="store.regionLoading" type="paragraph@3" />
              <div v-else-if="store.regionError" class="ov-pop-error">{{ store.regionError }}</div>
              <template v-else-if="region">
                <div class="ov-pop-nums">
                  <span><i class="zw-dot zw-dot--primary" />已通过 {{ region.approved }}</span>
                  <span><i class="zw-dot zw-dot--warning" />待审核 {{ region.pending }}</span>
                  <span><i class="zw-dot zw-dot--error" />已冻结 {{ region.frozen }}</span>
                </div>
                <div class="ov-pop-k">待审核</div>
                <div v-for="farm in region.pending_farms" :key="farm.farm_id" class="ov-pop-row">
                  <span>{{ farm.farm_name }}<span class="zw-faint"> · {{ farm.category_name || '—' }}</span></span>
                  <span :class="farm.overdue ? 'zw-warning-text' : 'zw-muted'">{{ farm.wait_text || '—' }}</span>
                </div>
                <div v-if="!region.pending_farms.length" class="zw-faint ov-pop-empty">这个地区没有待审核的养殖场</div>
                <div class="ov-pop-foot">
                  <v-btn size="small" variant="outlined" @click="goFarms">查看养殖场列表</v-btn>
                  <v-btn v-if="region.pending" size="small" color="primary" @click="goAudit">去审核</v-btn>
                </div>
              </template>
            </div>
          </zw-map>
          <div class="ov-map-foot">
            <span>{{ mapFootLeft }}</span>
            <span>已停用的 {{ farmStat?.disabled ?? 0 }} 个计入“已通过”</span>
          </div>
        </zw-card>

        <div class="ov-side">
          <zw-card title="品类分布" :sub="`已通过 ${categoryTotal} 个`">
            <zw-donut
              v-if="categoryItems.length"
              :items="categoryItems"
              :value="String(categoryTotal)"
              title="养殖场（个）"
              :size="148"
              :thickness="20"
            />
            <div v-else class="ov-nodata">暂无数据</div>
          </zw-card>
          <zw-card title="本月审核" :sub="monthRange">
            <table v-if="monthItems.length" class="ov-month">
              <thead>
                <tr><th>类别</th><th>通过</th><th>驳回</th><th>平均用时</th></tr>
              </thead>
              <tbody>
                <tr v-for="item in monthItems" :key="item.type">
                  <td>{{ item.type_text }}</td>
                  <td class="num">{{ item.approved }}</td>
                  <td class="num">{{ item.rejected }}</td>
                  <td class="num">{{ hoursText(item.avg_handle_hours) }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="ov-nodata">暂无数据</div>
          </zw-card>
        </div>
      </div>

      <div class="ov-row ov-row--bottom">
        <zw-card title="近 30 天趋势" :sub="dateRange(store.trend?.range_start, store.trend?.range_end)">
          <template #extra>
            <zw-seg
              size="small"
              :model-value="store.trendMetric"
              :items="trendMetrics"
              @update:model-value="store.fetchTrend(String($event))"
            />
          </template>
          <div v-if="store.trendLoading" class="trend-loading"><v-skeleton-loader type="image@1" /></div>
          <zw-empty v-else-if="store.trendError" icon="cloud_off" title="趋势数据加载失败" :desc="store.trendError" tone="error">
            <v-btn variant="text" color="primary" @click="store.fetchTrend(store.trendMetric)">重新加载</v-btn>
          </zw-empty>
          <div v-else-if="!trendHasData" class="ov-trend-empty">
            <zw-icon name="show_chart" :size="36" />
            <div class="ov-nodata-title">暂无数据</div>
            <div class="zw-faint">近 30 天没有{{ store.trend?.metric_text || '该指标' }}记录</div>
          </div>
          <template v-else>
            <zw-line-chart :labels="trendLabels" :series="trendSeries" :height="180" :unit="store.trend?.unit || '个'" :x-every="5" />
            <div class="ov-trend-sum">
              <span>日均 <b class="num">{{ store.trend?.summary?.daily_avg ?? '—' }}</b></span>
              <span v-if="store.trend?.summary?.max_date">最高 <b>{{ fmtShortDate(store.trend.summary.max_date) }} {{ store.trend.summary.max_value }}</b></span>
              <span v-if="store.trend?.summary?.holiday_text">{{ store.trend.summary.holiday_text }}日均 <b class="num">{{ store.trend.summary.holiday_avg ?? '—' }}</b></span>
            </div>
          </template>
        </zw-card>

        <zw-card title="最近操作">
          <template #extra><a class="zw-link" @click="$router.push({ name: 'WA-S06' })">操作日志</a></template>
          <zw-timeline v-if="recentItems.length" :items="recentItems" />
          <div v-else class="ov-nodata">暂无数据</div>
        </zw-card>
      </div>
    </template>
  </zw-shell>
</template>

<style scoped>
.ov-loading {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ov-notice {
  margin-bottom: 16px;
}
.ov-todo {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.ov-todo-item {
  display: flex;
  gap: 12px;
  padding: 4px 20px;
  cursor: pointer;
}
.ov-todo-item + .ov-todo-item {
  border-left: 1px solid var(--line-1);
}
.ov-todo-item:first-child {
  padding-left: 0;
}
.ov-todo-item.empty {
  cursor: default;
}
.ov-todo-main {
  flex: 1;
  min-width: 0;
}
.ov-todo-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2);
}
.ov-todo-num {
  margin-top: 4px;
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
  color: var(--warning);
}
.ov-todo-num small {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-2);
}
.ov-todo-sub {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.ov-todo-sub.late {
  color: var(--warning);
}
.ov-stats {
  margin-top: 16px;
}
.ov-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
  align-items: stretch;
}
.ov-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ov-map-card {
  display: flex;
  flex-direction: column;
}
.ov-map-card :deep(.zw-card-body--flush) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.ov-map-card :deep(.zw-map) {
  flex: 1;
  height: auto !important;
  min-height: 330px;
  border: 0;
  border-radius: 0;
}
.map-loading,
.trend-loading {
  height: 330px;
  padding: 16px;
}
.ov-map-empty,
.ov-trend-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 300px;
  color: var(--text-3);
}
.ov-nodata-title {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: var(--text-1);
}
.ov-nodata {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: var(--text-3);
  font-size: 13px;
}
.ov-cluster {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  z-index: 2;
}
.ov-cluster-count {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 34px;
  padding: 0 6px;
  border-radius: 17px;
  background: var(--primary);
  color: #ffffff;
  font-size: 13px;
  box-shadow: 0 0 0 4px #ffffffb3;
}
.ov-cluster--warning .ov-cluster-count {
  background: var(--warning);
}
.ov-cluster--error .ov-cluster-count {
  background: var(--error);
}
.ov-cluster.on .ov-cluster-count {
  outline: 2px solid var(--text-1);
  outline-offset: 2px;
}
.ov-cluster-label {
  order: -1;
  margin-bottom: 4px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
  box-shadow: var(--shadow-popover);
}
.ov-pop {
  position: absolute;
  left: 24px;
  top: 48px;
  z-index: 6;
  width: 320px;
  padding: 14px 16px 12px;
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-popover);
  font-size: 13px;
  line-height: 20px;
}
.ov-pop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ov-pop-title {
  font-size: 14px;
  font-weight: 600;
}
.ov-pop-nums {
  display: flex;
  gap: 12px;
  margin: 6px 0 10px;
  color: var(--text-2);
}
.ov-pop-k {
  margin: 8px 0 4px;
  font-size: 12px;
  color: var(--text-3);
}
.ov-pop-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 3px 0;
}
.ov-pop-row .zw-faint {
  font-size: 12px;
}
.ov-pop-empty {
  padding: 4px 0;
}
.ov-pop-error {
  padding: 8px 0;
  color: var(--error);
  font-size: 13px;
}
.ov-pop-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line-1);
}
.ov-map-foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 20px;
  border-top: 1px solid var(--line-1);
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.ov-month {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.ov-month th {
  padding: 0 0 8px;
  text-align: right;
  font-weight: 400;
  font-size: 12px;
  color: var(--text-3);
}
.ov-month th:first-child,
.ov-month td:first-child {
  text-align: left;
}
.ov-month td {
  padding: 7px 0;
  border-top: 1px solid var(--line-1);
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.ov-trend-sum {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.ov-trend-sum b {
  color: var(--text-1);
  font-weight: 500;
}
.ov-row--bottom {
  margin-bottom: 16px;
}
</style>
