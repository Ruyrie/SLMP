<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { LayerGroup, Map as LeafletMap, Marker, TileLayer } from 'leaflet'
import type { FarmMapCluster } from '@/api/system'

type LeafletApi = typeof import('leaflet')

const props = withDefaults(
  defineProps<{
    clusters: FarmMapCluster[]
    legend?: Array<{ type: string; label: string }>
    selectedCode?: string
    height?: number | string
  }>(),
  { legend: () => [], selectedCode: '', height: 420 }
)

const emit = defineEmits<{
  (event: 'select', cluster: FarmMapCluster): void
  (event: 'map-click'): void
  (event: 'ready'): void
}>()

/**
 * 高德栅格底图坐标为 GCJ-02，与 App 高德 SDK 上报的经纬度一致；
 * 高德不可达时退回 OpenStreetMap（WGS-84，省级尺度误差可忽略）。
 */
interface TileSource {
  name: string
  url: string
  options: { maxZoom: number; subdomains?: string; attribution: string }
}

const TILE_SOURCES: TileSource[] = [
  {
    name: '高德地图',
    url: 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    options: { maxZoom: 18, subdomains: '1234', attribution: '&copy; 高德地图' }
  },
  {
    name: 'OpenStreetMap',
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    options: { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }
  }
]

const TRANSPARENT_TILE = 'data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs='
const TILE_WATCHDOG_MS = 6000

const host = ref<HTMLElement | null>(null)
const loading = ref(true)
const tileNote = ref('')
const sourceName = ref('')

let disposed = false
let L: LeafletApi | null = null
let map: LeafletMap | null = null
let markerLayer: LayerGroup | null = null
let tileLayer: TileLayer | null = null
let resizeObserver: ResizeObserver | null = null
let watchdog: number | null = null
let sourceTimer: number | null = null
let tilesLoaded = 0
let usingFallback = false

const hasClusters = computed(() =>
  props.clusters.some((cluster) => Number.isFinite(coord(cluster.lat)) && Number.isFinite(coord(cluster.lng)))
)

/** 后端没有坐标时可能是 null，Number(null) 会变成 0，不能直接转。 */
function coord(value?: number | null): number {
  if (value === null || value === undefined) return Number.NaN
  return Number(value)
}

function escapeHtml(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char] || char))
}

function shortRegion(name?: string): string {
  if (!name) return ''
  return name.replace(/(壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市)$/g, '')
}

function clusterTone(cluster: FarmMapCluster): 'primary' | 'warning' | 'error' {
  if (cluster.frozen > 0) return 'error'
  if (cluster.pending > 0) return 'warning'
  return 'primary'
}

function clusterTitle(cluster: FarmMapCluster): string {
  return `${cluster.region_name}：已通过 ${cluster.approved}、待审核 ${cluster.pending}、已冻结 ${cluster.frozen}，共 ${cluster.total} 个养殖场`
}

function buildMarker(api: LeafletApi, cluster: FarmMapCluster): ReturnType<LeafletApi['divIcon']> {
  const tone = clusterTone(cluster)
  const label = escapeHtml(shortRegion(cluster.region_name) || cluster.region_name)
  const total = Math.max(0, Number(cluster.total) || 0)
  const approved = Math.max(0, Number(cluster.approved) || 0)
  const pending = Math.max(0, Number(cluster.pending) || 0)
  const frozen = Math.max(0, Number(cluster.frozen) || 0)
  const sum = approved + pending + frozen
  const pct = (value: number): number => (sum > 0 ? Math.round((value / sum) * 100) : 0)
  const bar =
    sum > 0
      ? `<span class="afm-pin-bar" aria-hidden="true">` +
        `<i class="ok" style="width:${pct(approved)}%"></i>` +
        `<i class="wait" style="width:${pct(pending)}%"></i>` +
        `<i class="stop" style="width:${pct(frozen)}%"></i>` +
        `</span>`
      : ''
  return api.divIcon({
    className: 'afm-icon',
    html:
      `<div class="afm-pin afm-pin--${tone}${props.selectedCode && cluster.region_code.startsWith(props.selectedCode) ? ' on' : ''}">` +
      `<span class="afm-pin-label">${label}</span>` +
      `<span class="afm-pin-count">${total}</span>` +
      bar +
      `</div>`,
    iconSize: [48, 58],
    iconAnchor: [24, 50]
  })
}

function clearMap(): void {
  if (watchdog !== null) {
    window.clearTimeout(watchdog)
    watchdog = null
  }
  clearSourceTimer()
  markerLayer?.remove()
  markerLayer = null
  if (tileLayer) {
    tileLayer.off()
    tileLayer.remove()
    tileLayer = null
  }
  if (map) {
    try {
      map.off()
      map.remove()
    } catch {
      /* 地图销毁失败不影响页面其他部分 */
    }
    map = null
  }
  tilesLoaded = 0
}

function fitView(): void {
  if (!map || !L) return
  const points = props.clusters
    .filter((cluster) => Number.isFinite(coord(cluster.lat)) && Number.isFinite(coord(cluster.lng)))
    .map((cluster) => [coord(cluster.lat), coord(cluster.lng)] as [number, number])
  if (!points.length) {
    map.setView([35.8, 104.2], 4, { animate: false })
    return
  }
  if (points.length === 1) {
    map.setView(points[0], 7, { animate: false })
    return
  }
  map.fitBounds(points, { padding: [48, 48], maxZoom: 9, animate: false })
}

function renderMarkers(): void {
  if (!map || !L) return
  markerLayer?.remove()
  markerLayer = L.layerGroup().addTo(map)
  for (const cluster of props.clusters) {
    const lat = coord(cluster.lat)
    const lng = coord(cluster.lng)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue
    const marker: Marker = L.marker([lat, lng], {
      icon: buildMarker(L, cluster),
      keyboard: true,
      riseOnHover: true,
      title: clusterTitle(cluster),
      alt: clusterTitle(cluster)
    })
    marker.on('click', (event) => {
      if (event.originalEvent) {
        const original = event.originalEvent as MouseEvent
        original.preventDefault()
        original.stopPropagation()
      }
      emit('select', cluster)
    })
    marker.addTo(markerLayer)
  }
}

function clearSourceTimer(): void {
  if (sourceTimer !== null) {
    window.clearTimeout(sourceTimer)
    sourceTimer = null
  }
}

function fallbackToNextSource(sourceIndex: number): void {
  const source = TILE_SOURCES[sourceIndex]
  if (usingFallback || sourceIndex + 1 >= TILE_SOURCES.length) {
    tileNote.value = '地图底图暂时不可用，聚合点仍可点击查看地区明细'
    return
  }
  usingFallback = true
  clearSourceTimer()
  tileLayer?.off()
  tileLayer?.remove()
  tileLayer = null
  tilesLoaded = 0
  const fallbackIndex = sourceIndex + 1
  addTileLayer(fallbackIndex)
  tileNote.value = `${source.name}底图加载失败，已切换为 ${TILE_SOURCES[fallbackIndex].name}`
}

function addTileLayer(sourceIndex: number): void {
  if (!map || !L) return
  const source = TILE_SOURCES[sourceIndex]
  sourceName.value = source.name
  clearSourceTimer()
  tileLayer = L.tileLayer(source.url, {
    ...source.options,
    minZoom: 3,
    errorTileUrl: TRANSPARENT_TILE
  })
  // errorTileUrl 会让失败瓦片再触发一次 tileload，用 WeakSet 把这些占位图排除掉，
  // 否则会误判成“底图已加载”，永远不切备用源。
  const placeholderTiles = new WeakSet<HTMLElement>()
  tileLayer.on('tileload', (event) => {
    const tile = (event as { tile?: HTMLElement }).tile
    if (tile && placeholderTiles.has(tile)) return
    tilesLoaded += 1
    if (tilesLoaded === 1) {
      clearSourceTimer()
      if (watchdog !== null) {
        window.clearTimeout(watchdog)
        watchdog = null
      }
      loading.value = false
      tileNote.value = ''
      emit('ready')
    }
  })
  let errors = 0
  tileLayer.on('tileerror', (event) => {
    const tile = (event as { tile?: HTMLElement }).tile
    if (tile) placeholderTiles.add(tile)
    errors += 1
    // 底图请求持续失败时提前换下一个瓦片源。
    if (tilesLoaded === 0 && errors >= 3) fallbackToNextSource(sourceIndex)
  })
  tileLayer.addTo(map)
  // Leaflet 在部分网络环境下不会触发 tileerror（请求被浏览器直接中止），
  // 用超时兜底：单个源超过 2.5s 还没有任何瓦片加载成功就切下一个。
  sourceTimer = window.setTimeout(() => {
    sourceTimer = null
    if (tilesLoaded === 0) fallbackToNextSource(sourceIndex)
  }, sourceIndex === 0 ? 2500 : 3500)
  if (watchdog !== null) window.clearTimeout(watchdog)
  watchdog = window.setTimeout(() => {
    watchdog = null
    loading.value = false
    if (tilesLoaded === 0) {
      tileNote.value = '地图底图加载较慢，聚合点仍可点击查看地区明细'
      emit('ready')
    }
  }, TILE_WATCHDOG_MS)
}

async function bootstrap(): Promise<void> {
  if (disposed) return
  const hostEl = host.value
  if (!hostEl) return
  try {
    const runtime = await import('./leaflet-runtime')
    if (disposed) return
    L = runtime.default
    map = L.map(hostEl, {
      center: [35.8, 104.2],
      zoom: 4,
      minZoom: 3,
      maxZoom: 18,
      zoomControl: false,
      attributionControl: true,
      preferCanvas: true,
      worldCopyJump: true,
      zoomAnimation: false
    })
    L.control.zoom({ position: 'bottomright' }).addTo(map)
    L.control.scale({ position: 'bottomleft', imperial: false, maxWidth: 120 }).addTo(map)
    map.on('click', () => emit('map-click'))
    // 直接挂高德瓦片，tileerror 或 2.5s 内没有瓦片就换 OSM；不做预检，避免把网络慢误判成地图不可用。
    addTileLayer(0)
    renderMarkers()
    fitView()
    loading.value = false
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => map?.invalidateSize({ animate: false }))
      resizeObserver.observe(hostEl)
    }
    window.setTimeout(() => map?.invalidateSize({ animate: false }), 120)
  } catch {
    loading.value = false
    tileNote.value = '地图初始化失败，聚合点仍可点击查看地区明细'
  }
}

watch(
  () => props.clusters,
  () => {
    if (!map) return
    renderMarkers()
    // 切换品类后聚合点会移动，自动回到筛选后的数据范围，避免标记跑出视野。
    fitView()
  }
)

watch(
  () => props.selectedCode,
  () => {
    if (!map) return
    renderMarkers()
  }
)

onMounted(() => {
  void bootstrap()
})

onBeforeUnmount(() => {
  disposed = true
  resizeObserver?.disconnect()
  resizeObserver = null
  clearMap()
})
</script>

<template>
  <div class="afm" :style="{ height: typeof height === 'number' ? `${height}px` : height }">
    <div ref="host" class="afm-canvas" role="application" aria-label="养殖场分布地图">
      <slot />
    </div>
    <div v-if="loading" class="afm-loading"><v-progress-circular indeterminate size="20" width="2" color="primary" /><span>正在加载地图…</span></div>

    <div v-if="legend.length" class="afm-legend">
      <span v-for="item in legend" :key="item.label"><i :class="`afm-dot afm-dot--${item.type}`" />{{ item.label }}</span>
    </div>

    <button type="button" class="afm-reset" title="回到全部聚合点" aria-label="回到全部聚合点" @click="fitView">
      <zw-icon name="zoom_out_map" :size="17" />
    </button>

    <div v-if="!hasClusters" class="afm-empty">
      <zw-icon name="map" :size="24" />
      <span>当前筛选下还没有已通过或待审核的养殖场</span>
    </div>
    <div v-if="tileNote" class="afm-note">{{ tileNote }}<template v-if="sourceName"> · {{ sourceName }}</template></div>
  </div>
</template>

<style scoped>
.afm {
  position: relative;
  width: 100%;
  min-height: 330px;
  overflow: hidden;
  border-radius: var(--radius-sm);
  background: var(--map-land);
  isolation: isolate;
}
.afm-canvas {
  width: 100%;
  height: 100%;
  background: var(--map-land);
  color: var(--text-1);
  font-family: var(--font-sans);
}
.afm-loading {
  position: absolute;
  inset: 0;
  z-index: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #FFFFFFB8;
  background: color-mix(in srgb, var(--bg-card) 72%, transparent);
  color: var(--text-3);
  font-size: 13px;
}
.afm-legend {
  position: absolute;
  left: 12px;
  top: 12px;
  z-index: 650;
  display: flex;
  gap: 12px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  box-shadow: var(--shadow-popover);
  font-size: 12px;
  line-height: 18px;
  color: var(--text-1);
  pointer-events: none;
}
.afm-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.afm-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
}
.afm-dot--warning {
  background: var(--warning);
}
.afm-dot--error {
  background: var(--error);
}
.afm-reset {
  position: absolute;
  right: 12px;
  bottom: 82px;
  z-index: 650;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  box-shadow: var(--shadow-popover);
  cursor: pointer;
}
.afm-reset:hover {
  color: var(--primary);
  background: var(--fill-1);
}
.afm-empty {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 640;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  max-width: calc(100% - 32px);
  padding: 12px 18px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-popover);
  color: var(--text-3);
  font-size: 13px;
  line-height: 20px;
  text-align: center;
  pointer-events: none;
}
.afm-note {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 650;
  max-width: calc(100% - 120px);
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  background: #FFFFFFE6;
  color: var(--text-2);
  font-size: 12px;
  line-height: 18px;
}

/* Leaflet 控件沿用页面主题 */
.afm :deep(.leaflet-container) {
  width: 100%;
  height: 100%;
  font-family: var(--font-sans);
  font-size: 12px;
  background: var(--map-land);
}
.afm :deep(.leaflet-control-zoom) {
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-popover);
  overflow: hidden;
}
.afm :deep(.leaflet-control-zoom a) {
  width: 32px;
  height: 32px;
  line-height: 32px;
  background: var(--bg-card);
  color: var(--text-1);
  border-bottom-color: var(--line-1);
  font-family: var(--font-sans);
  font-size: 18px;
}
.afm :deep(.leaflet-control-zoom a:hover) {
  background: var(--fill-1);
  color: var(--primary);
}
.afm :deep(.leaflet-control-attribution) {
  padding: 0 6px;
  background: #FFFFFFCC;
  color: var(--text-3);
  font-size: 11px;
  line-height: 16px;
}
.afm :deep(.leaflet-control-attribution a) {
  color: var(--text-2);
}
.afm :deep(.leaflet-control-scale-line) {
  border-color: var(--text-3);
  color: var(--text-3);
  background: #FFFFFFB3;
  font-size: 11px;
}
.afm :deep(.afm-icon) {
  background: transparent;
  border: 0;
}
.afm :deep(.afm-pin) {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 48px;
  filter: drop-shadow(0 2px 4px #00000033);
  cursor: pointer;
  transform: translate(-50%, -100%);
}
.afm :deep(.afm-pin-label) {
  order: -1;
  max-width: 120px;
  margin-bottom: 3px;
  padding: 1px 7px;
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-1);
  font-size: 12px;
  line-height: 18px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: var(--shadow-popover);
}
.afm :deep(.afm-pin-count) {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 34px;
  padding: 0 7px;
  border-radius: 17px;
  background: var(--primary);
  color: #FFFFFF;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 0 0 4px #FFFFFFB3;
  font-variant-numeric: tabular-nums;
}
.afm :deep(.afm-pin--warning .afm-pin-count) {
  background: var(--warning);
}
.afm :deep(.afm-pin--error .afm-pin-count) {
  background: var(--error);
}
.afm :deep(.afm-pin.on .afm-pin-count) {
  outline: 2px solid var(--text-1);
  outline-offset: 2px;
}
.afm :deep(.afm-pin-bar) {
  display: flex;
  width: 34px;
  height: 3px;
  margin-top: 3px;
  border-radius: 2px;
  overflow: hidden;
  background: #FFFFFF99;
}
.afm :deep(.afm-pin-bar i) {
  display: block;
  height: 100%;
}
.afm :deep(.afm-pin-bar .ok) {
  background: var(--primary);
}
.afm :deep(.afm-pin-bar .wait) {
  background: var(--warning);
}
.afm :deep(.afm-pin-bar .stop) {
  background: var(--error);
}
</style>
