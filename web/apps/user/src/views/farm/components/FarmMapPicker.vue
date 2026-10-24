<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { DivIcon, LeafletMouseEvent, Map as LeafletMap, Marker as LeafletMarker, TileLayer } from 'leaflet'

interface MapPick {
  longitude: string
  latitude: string
}

type LeafletApi = typeof import('leaflet')

const props = withDefaults(
  defineProps<{
    longitude?: string
    latitude?: string
    fallbackLongitude?: string
    fallbackLatitude?: string
    label?: string
  }>(),
  { longitude: '', latitude: '', fallbackLongitude: '', fallbackLatitude: '', label: '养殖场位置' }
)

const emit = defineEmits<{
  (event: 'pick', value: MapPick): void
  (event: 'unavailable', reason: string): void
  (event: 'ready'): void
}>()

/** 预检 + 建图最多占用 1.5s，超时或失败就交给父组件降级为示意地图。 */
const MAP_INIT_BUDGET_MS = 1500
const TILE_LOAD_BUDGET_MS = 1500
const TILE_ERROR_GRACE_MS = 700

/**
 * 两张都是 OpenStreetMap 瓦片，均无需 Key。
 * 先试 FOSSGIS 提供的 OSM 瓦片（国内网络通常更稳定），失败后再试 OSM 官方瓦片；
 * 两者都不可用时由父组件降级到 zw-map 示意图。
 */
interface TileSource {
  url: string
  attribution: string
}

const OSM_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'

const TILE_SOURCES: TileSource[] = [
  {
    url: 'https://tile.openstreetmap.de/{z}/{x}/{y}.png',
    attribution: `${OSM_ATTRIBUTION} · Tiles: FOSSGIS`
  },
  {
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: OSM_ATTRIBUTION
  }
]

/** 瓦片请求失败时用透明像素占位，避免出现碎图标。 */
const TRANSPARENT_TILE = 'data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs='

const container = ref<HTMLElement | null>(null)
const loading = ref(true)

let disposed = false
let failed = false
let L: LeafletApi | null = null
let map: LeafletMap | null = null
let marker: LeafletMarker | null = null
let tileLayer: TileLayer | null = null
let resizeObserver: ResizeObserver | null = null
let tilesLoaded = 0
let startupTimer: number | null = null
let tileErrorTimer: number | null = null
let syncTimer: number | null = null
let lastPicked: { lng: number; lat: number } | null = null

function parseCoord(value?: string): number | null {
  if (value === undefined || value === null) return null
  const text = String(value).trim()
  if (!text) return null
  const parsed = Number(text)
  return Number.isFinite(parsed) ? parsed : null
}

function readPoint(longitude?: string, latitude?: string): { lng: number; lat: number } | null {
  const lng = parseCoord(longitude)
  const lat = parseCoord(latitude)
  if (lng === null || lat === null) return null
  if (lng < -180 || lng > 180 || lat < -85 || lat > 85) return null
  return { lng, lat }
}

function preflightTile(urlTemplate: string, timeoutMs: number): Promise<boolean> {
  return new Promise((resolve) => {
    let settled = false
    let timer = 0
    const image = new Image()
    const finish = (ok: boolean): void => {
      if (settled) return
      settled = true
      window.clearTimeout(timer)
      image.onload = null
      image.onerror = null
      resolve(ok)
    }
    timer = window.setTimeout(() => finish(false), Math.max(80, timeoutMs))
    image.onload = () => finish(true)
    image.onerror = () => finish(false)
    image.src = urlTemplate.replace('{z}', '0').replace('{x}', '0').replace('{y}', '0')
  })
}

async function selectTileSource(deadline: number): Promise<number> {
  for (let index = 0; index < TILE_SOURCES.length; index += 1) {
    const remaining = deadline - Date.now()
    if (remaining < 120) return -1
    const ok = await preflightTile(TILE_SOURCES[index].url, remaining)
    if (ok) return index
  }
  return -1
}

function buildPin(api: LeafletApi): DivIcon {
  return api.divIcon({
    className: 'floc-pin',
    html:
      '<svg width="24" height="34" viewBox="0 0 24 34" aria-hidden="true">' +
      '<path d="M12 32.2C9.4 27.6 2.4 21.1 2.4 13.2a9.6 9.6 0 1 1 19.2 0c0 7.9-7 14.4-9.6 19Z" fill="var(--primary)" stroke="#FFFFFF" stroke-width="2.2"/>' +
      '<circle cx="12" cy="13" r="3.8" fill="#FFFFFF"/>' +
      '</svg>',
    iconSize: [24, 34],
    iconAnchor: [12, 33]
  })
}

function updateMarker(lat: number, lng: number): void {
  if (!map || !L) return
  if (!marker) {
    marker = L.marker([lat, lng], {
      icon: buildPin(L),
      draggable: true,
      keyboard: true,
      title: props.label,
      alt: props.label,
      riseOnHover: true
    }).addTo(map)
    marker.on('dragend', handleMarkerDrag)
    return
  }
  marker.setLatLng([lat, lng])
}

function removeMarker(): void {
  if (!marker) return
  marker.off()
  marker.remove()
  marker = null
}

function emitPick(lat: number, lng: number): void {
  const longitude = lng.toFixed(6)
  const latitude = lat.toFixed(6)
  lastPicked = { lng: Number(longitude), lat: Number(latitude) }
  emit('pick', { longitude, latitude })
}

function handleMarkerDrag(): void {
  if (!marker) return
  const point = marker.getLatLng()
  emitPick(point.lat, point.lng)
}

function handleMapClick(event: LeafletMouseEvent): void {
  updateMarker(event.latlng.lat, event.latlng.lng)
  emitPick(event.latlng.lat, event.latlng.lng)
}

function clearStartupTimers(): void {
  if (startupTimer !== null) {
    window.clearTimeout(startupTimer)
    startupTimer = null
  }
  if (tileErrorTimer !== null) {
    window.clearTimeout(tileErrorTimer)
    tileErrorTimer = null
  }
}

function handleTileLoad(): void {
  tilesLoaded += 1
  clearStartupTimers()
}

function handleTileError(): void {
  if (tilesLoaded > 0 || failed) return
  if (tileErrorTimer !== null) return
  tileErrorTimer = window.setTimeout(() => {
    tileErrorTimer = null
    if (tilesLoaded === 0) markUnavailable('tiles')
  }, TILE_ERROR_GRACE_MS)
}

function startTileWatchdog(): void {
  startupTimer = window.setTimeout(() => {
    startupTimer = null
    if (tilesLoaded === 0) markUnavailable('tiles')
  }, TILE_LOAD_BUDGET_MS)
}

function destroyMap(): void {
  if (marker) {
    marker.off()
    marker = null
  }
  if (map) {
    try {
      map.off()
      map.remove()
    } catch {
      /* 销毁失败不影响降级展示 */
    }
    map = null
  }
  tileLayer = null
  tilesLoaded = 0
}

function markUnavailable(reason: string): void {
  if (failed || disposed) return
  failed = true
  loading.value = false
  clearStartupTimers()
  destroyMap()
  // 只允许出现一条 warning，避免瓦片失败刷屏；生产构建保持静默。
  if (import.meta.env.DEV) {
    console.warn(`[FarmMap] 地图不可用（${reason}），已切换为示意地图`)
  }
  emit('unavailable', reason)
}

function handleOffline(): void {
  markUnavailable('offline')
}

function syncFromForm(): void {
  syncTimer = null
  if (!map || !L) return
  const point = readPoint(props.longitude, props.latitude)
  if (!point) {
    const empty = !String(props.longitude ?? '').trim() && !String(props.latitude ?? '').trim()
    if (empty) {
      lastPicked = null
      removeMarker()
    }
    return
  }
  // 地图点击 / 拖动 marker 也会回写表单，这种变化不需要再把地图拉回中心。
  if (lastPicked && Math.abs(lastPicked.lng - point.lng) < 1e-7 && Math.abs(lastPicked.lat - point.lat) < 1e-7) {
    return
  }
  updateMarker(point.lat, point.lng)
  const center = map.getCenter()
  if (Math.abs(center.lat - point.lat) < 1e-7 && Math.abs(center.lng - point.lng) < 1e-7) return
  map.setView([point.lat, point.lng], map.getZoom() < 10 ? 15 : map.getZoom(), { animate: false })
}

function createMap(api: LeafletApi, sourceIndex: number): void {
  const host = container.value
  if (!host || map) return
  const formPoint = readPoint(props.longitude, props.latitude)
  const farmPoint = readPoint(props.fallbackLongitude, props.fallbackLatitude)
  const anchor = formPoint ?? farmPoint
  const center = anchor ?? { lng: 104.2, lat: 35.8 }
  try {
    map = api.map(host, {
      center: [center.lat, center.lng],
      zoom: anchor ? 15 : 4,
      minZoom: 3,
      maxZoom: 19,
      zoomControl: true,
      attributionControl: true,
      preferCanvas: true,
      worldCopyJump: true
    })

    const source = TILE_SOURCES[sourceIndex]
    tileLayer = api.tileLayer(source.url, {
      minZoom: 3,
      maxZoom: 19,
      attribution: source.attribution,
      errorTileUrl: TRANSPARENT_TILE
    })
    tileLayer.on('tileload', handleTileLoad)
    tileLayer.on('tileerror', handleTileError)
    tileLayer.addTo(map)
    map.on('click', handleMapClick)

    if (anchor) updateMarker(anchor.lat, anchor.lng)
    startTileWatchdog()

    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => {
        map?.invalidateSize({ animate: false })
      })
      resizeObserver.observe(host)
    }

    loading.value = false
    emit('ready')
    window.setTimeout(() => map?.invalidateSize({ animate: false }), 120)
  } catch {
    markUnavailable('init')
  }
}

async function bootstrap(): Promise<void> {
  if (disposed) return
  if (navigator.onLine === false) {
    markUnavailable('offline')
    return
  }
  const deadline = Date.now() + MAP_INIT_BUDGET_MS
  try {
    // 动态 import 与瓦片预检并行，最迟 1.5s 内决定“真地图”还是“示意地图”。
    const [runtime, sourceIndex] = await Promise.all([import('./leaflet-runtime'), selectTileSource(deadline)])
    if (disposed) return
    if (sourceIndex < 0) {
      markUnavailable('tiles')
      return
    }
    L = runtime.default
    createMap(L, sourceIndex)
  } catch {
    if (!disposed) markUnavailable('init')
  }
}

onMounted(() => {
  window.addEventListener('offline', handleOffline)
  void bootstrap()
})

onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('offline', handleOffline)
  clearStartupTimers()
  if (syncTimer !== null) {
    window.clearTimeout(syncTimer)
    syncTimer = null
  }
  resizeObserver?.disconnect()
  resizeObserver = null
  destroyMap()
})

watch(
  () => [props.longitude, props.latitude],
  () => {
    if (syncTimer !== null) window.clearTimeout(syncTimer)
    syncTimer = window.setTimeout(syncFromForm, 220)
  }
)
</script>

<template>
  <div class="floc-leaflet">
    <div ref="container" class="floc-leaflet-canvas" role="application" aria-label="养殖场位置选择地图" />
    <div v-if="loading" class="floc-leaflet-loading">
      <v-progress-circular indeterminate size="18" width="2" color="primary" />
      <span>正在加载地图…</span>
    </div>
  </div>
</template>

<style scoped>
.floc-leaflet {
  position: relative;
  width: 100%;
  height: 100%;
  background: var(--map-land);
}
.floc-leaflet-canvas {
  width: 100%;
  height: 100%;
  position: relative;
  background: var(--map-land);
  color: var(--text-1);
  font-family: var(--font-sans);
  font-size: 13px;
}
.floc-leaflet-loading {
  position: absolute;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--map-land);
  color: var(--text-3);
  font-size: 13px;
}
:deep(.leaflet-control-zoom) {
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-popover);
  overflow: hidden;
}
:deep(.leaflet-control-zoom a) {
  width: 30px;
  height: 30px;
  line-height: 30px;
  background: var(--bg-card);
  color: var(--text-1);
  border-bottom-color: var(--line-1);
  font-family: var(--font-sans);
  font-size: 17px;
}
:deep(.leaflet-control-zoom a:hover) {
  background: var(--fill-1);
  color: var(--primary);
}
:deep(.leaflet-control-attribution) {
  padding: 0 6px;
  background: #FFFFFFCC;
  color: var(--text-3);
  font-family: var(--font-sans);
  font-size: 11px;
  line-height: 16px;
}
:deep(.leaflet-control-attribution a) {
  color: var(--text-2);
}
:deep(.floc-pin) {
  background: transparent;
  border: 0;
}
:deep(.floc-pin svg) {
  display: block;
  filter: drop-shadow(0 2px 3px #00000033);
}
</style>
