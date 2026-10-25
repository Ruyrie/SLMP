<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { LayerGroup, Map as LeafletMap, TileLayer } from 'leaflet'
import { locationMarker, type AuditLocationMarker } from './location'

const props = withDefaults(defineProps<{ markers: AuditLocationMarker[]; height?: number }>(), { height: 220 })
const points = computed(() => props.markers
  .map((marker) => locationMarker(marker, marker.label, marker.tone))
  .filter((marker): marker is AuditLocationMarker => marker !== null))
const host = ref<HTMLElement | null>(null)
const loading = ref(false)
const error = ref('')
let L: typeof import('leaflet') | null = null
let map: LeafletMap | null = null
let markers: LayerGroup | null = null
let tiles: TileLayer | null = null
let observer: ResizeObserver | null = null
let timer: number | null = null
let disposed = false
let generation = 0

function clearTimer(): void {
  if (timer !== null) window.clearTimeout(timer)
  timer = null
}

function destroy(): void {
  generation += 1
  clearTimer()
  loading.value = false
  error.value = ''
  observer?.disconnect()
  observer = null
  tiles?.off()
  tiles = null
  markers = null
  map?.remove()
  map = null
}

function fitView(): void {
  if (!map || !points.value.length) return
  const coordinates = points.value.map((point) => [point.latitude, point.longitude] as [number, number])
  map.invalidateSize({ animate: false })
  if (coordinates.length === 1) map.setView(coordinates[0], 15, { animate: false })
  else map.fitBounds(coordinates, { padding: [40, 45], maxZoom: 16, animate: false })
}

function renderMarkers(): void {
  if (!map || !L) return
  markers?.remove()
  markers = L.layerGroup().addTo(map)
  for (const point of points.value) {
    // 使用文本节点，养殖场名称不能作为 tooltip HTML 插入。
    const label = document.createElement('span')
    label.textContent = point.label
    L.marker([point.latitude, point.longitude], {
      icon: L.divIcon({
        className: `alm-marker alm-marker--${point.tone}`,
        html: '<span></span>',
        iconSize: [20, 20],
        iconAnchor: [10, 10]
      }),
      title: point.label,
      alt: point.label,
      keyboard: true
    }).bindTooltip(label, { permanent: true, direction: 'top', offset: [0, -10] }).addTo(markers)
  }
  fitView()
}

async function initialize(): Promise<void> {
  destroy()
  if (disposed || !host.value || !points.value.length) return
  const current = generation
  loading.value = true
  error.value = ''
  try {
    // 复用管理端已有的 Leaflet 运行时与样式，按需加载。
    const runtime = await import('@/views/system/components/leaflet-runtime')
    if (disposed || current !== generation || !host.value) return
    L = runtime.default
    map = L.map(host.value, {
      center: [points.value[0].latitude, points.value[0].longitude],
      zoom: 15,
      minZoom: 3,
      maxZoom: 18,
      zoomControl: false,
      zoomAnimation: false
    })
    L.control.zoom({ position: 'bottomright', zoomInTitle: '放大', zoomOutTitle: '缩小' }).addTo(map)
    L.control.scale({ imperial: false, maxWidth: 80 }).addTo(map)
    // App 上报 GCJ-02，使用同坐标系高德底图，避免审核位置在切源后发生偏移。
    tiles = L.tileLayer(
      'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
      { subdomains: '1234', maxZoom: 18, attribution: '&copy; 高德地图' }
    )
    let loaded = 0
    let failures = 0
    const unavailable = (): void => {
      loading.value = false
      error.value = '地图底图加载失败，请检查网络后重试'
      clearTimer()
    }
    tiles.on('tileload', () => {
      loaded += 1
      clearTimer()
      loading.value = false
      error.value = ''
    })
    tiles.on('tileerror', () => {
      failures += 1
      if (!loaded && failures >= 3) unavailable()
    })
    timer = window.setTimeout(() => { if (!loaded) unavailable() }, 8000)
    renderMarkers()
    tiles.addTo(map)
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(() => {
        // 抽屉动画结束后容器尺寸才稳定，重新居中以免标记被裁切。
        if (map && host.value?.clientWidth) fitView()
      })
      observer.observe(host.value)
    }
  } catch {
    if (disposed || current !== generation) return
    destroy()
    loading.value = false
    error.value = '地图初始化失败，请重试'
  }
}

watch(points, () => {
  if (!points.value.length) destroy()
  else if (map) renderMarkers()
  else void initialize()
}, { flush: 'post' })
onMounted(() => { void initialize() })
onBeforeUnmount(() => { disposed = true; destroy() })
</script>

<template>
  <div class="audit-location-map" :style="{ height: `${height}px` }">
    <div v-if="points.length" ref="host" class="alm-canvas" role="region" aria-label="养殖场位置地图" />
    <div v-else class="alm-empty">未提供有效经纬度，无法显示地图</div>
    <div v-if="points.length" class="alm-legend">
      <span v-for="(point, index) in points" :key="index"><i :class="`alm-dot--${point.tone}`" />{{ point.label }}</span>
    </div>
    <button v-if="points.length" class="alm-reset" type="button" aria-label="回到养殖场位置" title="回到养殖场位置" @click="fitView">
      <zw-icon name="my_location" :size="18" />
    </button>
    <div v-if="loading" class="alm-loading" role="status">正在加载地图…</div>
    <div v-if="error && points.length" class="alm-error" role="alert">
      <span>{{ error }}</span><button type="button" @click="initialize">重新加载</button>
    </div>
  </div>
</template>

<style scoped>
.audit-location-map { position: relative; overflow: hidden; isolation: isolate; border: 1px solid var(--line-1); border-radius: var(--radius-sm); background: var(--fill-1); }
.alm-canvas { width: 100%; height: 100%; font-family: var(--font-sans); }
.alm-legend { position: absolute; top: 10px; left: 10px; z-index: 650; display: flex; flex-wrap: wrap; gap: 10px; max-width: calc(100% - 20px); padding: 5px 9px; background: var(--bg-card); border-radius: var(--radius-sm); box-shadow: var(--shadow-popover); font-size: 12px; pointer-events: none; }
.alm-legend span { display: inline-flex; align-items: center; gap: 5px; }
.alm-legend i { width: 8px; height: 8px; border-radius: 50%; background: var(--primary); }
.alm-legend .alm-dot--neutral { background: var(--text-3); }
.alm-reset { position: absolute; right: 10px; bottom: 90px; z-index: 650; display: grid; place-items: center; width: 34px; height: 34px; background: var(--bg-card); color: var(--primary); border: 1px solid var(--line-1); border-radius: var(--radius-sm); cursor: pointer; }
.alm-empty, .alm-loading { position: absolute; inset: 0; display: grid; place-items: center; color: var(--text-3); font-size: 12px; }
.alm-loading { z-index: 700; background: #FFFFFFB8; pointer-events: none; }
.alm-error { position: absolute; inset: auto 0 0; z-index: 700; display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; padding: 8px; background: var(--bg-card); color: var(--text-2); font-size: 12px; }
.alm-error button { color: var(--primary); text-decoration: underline; cursor: pointer; }
.audit-location-map :deep(.alm-marker) { display: grid; place-items: center; background: transparent; border: 0; }
.audit-location-map :deep(.alm-marker span) { width: 18px; height: 18px; border: 3px solid white; border-radius: 50%; background: var(--primary); box-shadow: 0 1px 5px #00000055; }
.audit-location-map :deep(.alm-marker--neutral span) { background: var(--text-3); }
.audit-location-map :deep(.leaflet-tooltip) { max-width: 220px; overflow: hidden; text-overflow: ellipsis; font-family: var(--font-sans); }
</style>
