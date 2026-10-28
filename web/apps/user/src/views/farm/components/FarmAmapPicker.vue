<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatCoord, loadAmap, regeoToFields, type AMapNamespace, type RegeoFields } from './amap'

/**
 * 高德 JS API 2.0 选点：点地图 / 拖动标记 / 搜索地点 / 定位，选中后逆地理编码回填省市区与 adcode。
 * 坐标系统一为 GCJ-02（与 App 高德 SDK 一致）。加载失败或超时由父组件降级为手填。
 */
const props = withDefaults(
  defineProps<{
    longitude?: string
    latitude?: string
    label?: string
  }>(),
  { longitude: '', latitude: '', label: '养殖场位置' }
)

const emit = defineEmits<{
  (event: 'pick', value: { longitude: string; latitude: string; accuracy?: number | null }): void
  (event: 'address', value: RegeoFields): void
  (event: 'geocode-error', reason: string): void
  (event: 'ready'): void
  (event: 'unavailable', reason: string): void
}>()

const LOAD_BUDGET_MS = 10000
const DEFAULT_CENTER: [number, number] = [108.94, 34.34]

interface SearchTip {
  id?: string
  name?: string
  district?: string
  address?: string
  adcode?: string
  location?: { getLng(): number; getLat(): number } | null
}

const container = ref<HTMLElement | null>(null)
const keyword = ref('')
const tips = ref<SearchTip[]>([])
const searching = ref(false)
const searchOpen = ref(false)

let AMap: AMapNamespace = null
let map: AMapNamespace = null
let marker: AMapNamespace = null
let geocoder: AMapNamespace = null
let autoComplete: AMapNamespace = null
let placeSearch: AMapNamespace = null
let disposed = false
let searchTimer = 0
let geocodeSeq = 0
let lastPoint = ''

function currentPoint(): [number, number] | null {
  const lng = Number(props.longitude)
  const lat = Number(props.latitude)
  if (!props.longitude || !props.latitude || !Number.isFinite(lng) || !Number.isFinite(lat)) return null
  return [lng, lat]
}

function placeMarker(position: [number, number]): void {
  if (!map || !AMap) return
  if (!marker) {
    marker = new AMap.Marker({ position, draggable: true, cursor: 'move', title: props.label, anchor: 'bottom-center' })
    marker.on('dragend', (event: { lnglat: { getLng(): number; getLat(): number } }) => {
      setPoint(event.lnglat.getLng(), event.lnglat.getLat(), { geocode: true })
    })
    map.add(marker)
  } else {
    marker.setPosition(position)
  }
}

/** 选中一个点：更新标记、通知父组件，按需逆地理编码。 */
function setPoint(lng: number, lat: number, options: { geocode?: boolean; center?: boolean; zoom?: number; accuracy?: number | null } = {}): void {
  const position: [number, number] = [Number(formatCoord(lng)), Number(formatCoord(lat))]
  lastPoint = `${formatCoord(lng)},${formatCoord(lat)}`
  placeMarker(position)
  if (options.center && map) {
    if (options.zoom) map.setZoomAndCenter(options.zoom, position)
    else map.setCenter(position)
  }
  emit('pick', { longitude: formatCoord(lng), latitude: formatCoord(lat), accuracy: options.accuracy ?? null })
  if (options.geocode) reverseGeocode(position)
}

function reverseGeocode(position: [number, number]): void {
  if (!geocoder) return
  const seq = ++geocodeSeq
  geocoder.getAddress(position, (status: string, result: { info?: string; regeocode?: Parameters<typeof regeoToFields>[0] }) => {
    if (disposed || seq !== geocodeSeq) return
    const fields = status === 'complete' ? regeoToFields(result?.regeocode) : null
    if (fields) emit('address', fields)
    else emit('geocode-error', status === 'no_data' ? '这个位置没有行政区划信息' : String(result?.info || status || '地址解析失败'))
  })
}

/* ---------------- 地点搜索（AutoComplete 补全 + PlaceSearch 兜底） ---------------- */
function onKeyword(value: string | null): void {
  keyword.value = value || ''
  window.clearTimeout(searchTimer)
  const text = keyword.value.trim()
  if (!text || !autoComplete) {
    tips.value = []
    searchOpen.value = false
    return
  }
  searchTimer = window.setTimeout(() => {
    searching.value = true
    autoComplete.search(text, (status: string, result: { tips?: SearchTip[] }) => {
      if (disposed) return
      searching.value = false
      tips.value = status === 'complete' ? (result?.tips || []).filter((tip) => tip.name).slice(0, 8) : []
      searchOpen.value = true
    })
  }, 300)
}

function selectTip(tip: SearchTip): void {
  searchOpen.value = false
  keyword.value = tip.name || ''
  if (tip.location && typeof tip.location.getLng === 'function') {
    setPoint(tip.location.getLng(), tip.location.getLat(), { geocode: true, center: true, zoom: 15 })
    return
  }
  // 行政区类提示没有坐标，用地点搜索取第一个结果
  if (!placeSearch) return
  placeSearch.search(`${tip.district || ''}${tip.name || ''}`, (status: string, result: { poiList?: { pois?: SearchTip[] } }) => {
    if (disposed) return
    const poi = status === 'complete' ? result?.poiList?.pois?.[0] : null
    if (poi?.location) setPoint(poi.location.getLng(), poi.location.getLat(), { geocode: true, center: true, zoom: 15 })
    else emit('geocode-error', '没有找到这个地点的坐标，请在地图上点选')
  })
}

function searchFirst(): void {
  if (tips.value.length) selectTip(tips.value[0])
}

/* ---------------- 定位（AMap.Geolocation，结果已是 GCJ-02） ---------------- */
function locate(): Promise<{ ok: boolean; message?: string }> {
  return new Promise((resolve) => {
    if (!AMap || !map) {
      resolve({ ok: false, message: 'MAP_NOT_READY' })
      return
    }
    const geolocation = new AMap.Geolocation({
      enableHighAccuracy: true,
      timeout: 10000,
      needAddress: false,
      showButton: false,
      showMarker: false,
      showCircle: false,
      zoomToAccuracy: false
    })
    geolocation.getCurrentPosition((status: string, result: { position?: { getLng(): number; getLat(): number }; accuracy?: number; message?: string }) => {
      if (disposed) return
      if (status === 'complete' && result?.position) {
        const accuracy = Number(result.accuracy)
        setPoint(result.position.getLng(), result.position.getLat(), {
          geocode: true,
          center: true,
          zoom: 15,
          accuracy: Number.isFinite(accuracy) && accuracy > 0 ? Math.round(accuracy) : null
        })
        resolve({ ok: true })
      } else {
        resolve({ ok: false, message: result?.message || '定位失败' })
      }
    })
  })
}

defineExpose({ locate })

async function init(): Promise<void> {
  let timer = 0
  try {
    const loaded = await Promise.race([
      loadAmap(),
      new Promise<never>((_, reject) => {
        timer = window.setTimeout(() => reject(new Error('TIMEOUT')), LOAD_BUDGET_MS)
      })
    ])
    window.clearTimeout(timer)
    if (disposed || !container.value) return
    AMap = loaded
    const point = currentPoint()
    map = new AMap.Map(container.value, {
      zoom: point ? 15 : 4,
      center: point || DEFAULT_CENTER,
      viewMode: '2D',
      resizeEnable: true
    })
    map.addControl(new AMap.Scale())
    map.addControl(new AMap.ToolBar({ position: 'RT' }))
    geocoder = new AMap.Geocoder({ extensions: 'base' })
    autoComplete = new AMap.AutoComplete({ city: '全国', citylimit: false })
    placeSearch = new AMap.PlaceSearch({ pageSize: 1, extensions: 'base' })
    if (point) {
      lastPoint = `${formatCoord(point[0])},${formatCoord(point[1])}`
      placeMarker(point)
    }
    map.on('click', (event: { lnglat: { getLng(): number; getLat(): number } }) => {
      setPoint(event.lnglat.getLng(), event.lnglat.getLat(), { geocode: true })
    })
    emit('ready')
  } catch (error) {
    window.clearTimeout(timer)
    if (disposed) return
    const reason = (error as Error)?.message || String(error || 'LOAD_FAILED')
    emit('unavailable', reason)
  }
}

/** 父组件手填经纬度后同步标记位置（不触发逆地理，避免覆盖手填的地址）。 */
watch(
  () => [props.longitude, props.latitude],
  () => {
    const point = currentPoint()
    if (!point || !map) return
    const key = `${formatCoord(point[0])},${formatCoord(point[1])}`
    if (key === lastPoint) return
    lastPoint = key
    placeMarker(point)
    map.setCenter(point)
  }
)

onMounted(() => {
  void init()
})

onBeforeUnmount(() => {
  disposed = true
  window.clearTimeout(searchTimer)
  try {
    map?.destroy()
  } catch {
    /* 地图已销毁 */
  }
  map = null
  marker = null
})
</script>

<template>
  <div class="famap">
    <div ref="container" class="famap-map" />
    <div class="famap-search">
      <v-text-field
        :model-value="keyword"
        density="compact"
        hide-details
        clearable
        prepend-inner-icon="search"
        placeholder="搜索地点，例如 赤峰市松山区太平地镇"
        :loading="searching"
        @update:model-value="onKeyword"
        @keydown.enter.prevent="searchFirst"
        @focus="searchOpen = tips.length > 0"
      />
      <div v-if="searchOpen" class="famap-tips" role="listbox">
        <div v-if="!tips.length" class="famap-tip famap-tip--empty">没有找到匹配的地点</div>
        <button v-for="(tip, index) in tips" :key="tip.id || `${tip.name}-${index}`" type="button" class="famap-tip" @click="selectTip(tip)">
          <span class="famap-tip-name">{{ tip.name }}</span>
          <span class="famap-tip-sub">{{ tip.district }}{{ typeof tip.address === 'string' ? ` ${tip.address}` : '' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.famap {
  position: relative;
  width: 100%;
  height: 100%;
}
.famap-map {
  width: 100%;
  height: 100%;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.famap-search {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 10;
  width: min(360px, calc(100% - 90px));
  background: var(--bg-1, #fff);
  border-radius: var(--radius-sm);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.famap-tips {
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px solid var(--line-1);
}
.famap-tip {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 6px 12px;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.famap-tip:hover {
  background: var(--fill-1);
}
.famap-tip--empty {
  cursor: default;
  color: var(--text-3);
  font-size: 13px;
}
.famap-tip-name {
  font-size: 14px;
  color: var(--text-1);
}
.famap-tip-sub {
  font-size: 12px;
  color: var(--text-3);
}
</style>
