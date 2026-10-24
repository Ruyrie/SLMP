<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useUiStore } from '@/stores/ui'
import type { FarmLocationPayload } from '@/stores/farm/create'
import FarmMapPicker from './FarmMapPicker.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    farmName?: string
    address?: string
    province?: string
    city?: string
    district?: string
    township?: string
    adcode?: string
    longitude?: string
    latitude?: string
    locAccuracy?: number | null
    changeMode?: boolean
  }>(),
  { farmName: '', address: '', province: '', city: '', district: '', township: '', adcode: '', longitude: '', latitude: '', locAccuracy: null }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', value: FarmLocationPayload): void
}>()

const ui = useUiStore()
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const form = reactive({
  address: '',
  province: '',
  city: '',
  district: '',
  township: '',
  adcode: '',
  longitude: '',
  latitude: '',
  locAccuracy: ''
})

const errors = reactive<Record<string, string>>({})
const locating = ref(false)
const mapMode = ref<'idle' | 'live' | 'fallback'>('idle')
const mapReady = ref(false)

const hasCoordinates = computed(() => {
  if (!form.longitude.trim() || !form.latitude.trim()) return false
  const lng = Number(form.longitude)
  const lat = Number(form.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat)
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      mapMode.value = 'idle'
      mapReady.value = false
      return
    }
    form.address = props.address
    form.province = props.province
    form.city = props.city
    form.district = props.district
    form.township = props.township
    form.adcode = props.adcode
    form.longitude = props.longitude
    form.latitude = props.latitude
    form.locAccuracy = props.locAccuracy === null || props.locAccuracy === undefined ? '' : String(props.locAccuracy)
    for (const key of Object.keys(errors)) delete errors[key]
    mapReady.value = false
    mapMode.value = 'live'
  },
  { immediate: true }
)

const marker = computed(() => {
  const lng = Number(form.longitude)
  const lat = Number(form.latitude)
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return []
  return [{ x: 50, y: 50, label: props.farmName || '养殖场位置' }]
})

function clearError(key: string): void {
  delete errors[key]
}

function onMapPick(value: { longitude: string; latitude: string }): void {
  form.longitude = value.longitude
  form.latitude = value.latitude
  delete errors.longitude
  delete errors.latitude
}

function onMapUnavailable(): void {
  mapReady.value = false
  mapMode.value = 'fallback'
}

function useCurrentLocation(): void {
  if (!navigator.geolocation) {
    ui.toast('当前浏览器不支持定位，请手动填写经纬度', 'error')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      form.longitude = position.coords.longitude.toFixed(6)
      form.latitude = position.coords.latitude.toFixed(6)
      form.locAccuracy = String(Math.round(position.coords.accuracy || 0))
      locating.value = false
      ui.toast('已填入当前位置经纬度，请补充地址和行政区划')
    },
    () => {
      locating.value = false
      ui.toast('定位失败，你可以手动填写地址和经纬度', 'error')
    },
    { enableHighAccuracy: true, timeout: 8000 }
  )
}

function confirm(): void {
  for (const key of Object.keys(errors)) delete errors[key]
  const address = form.address.trim()
  if (!address) errors.address = '请填写详细地址'
  if (!form.province.trim()) errors.province = '请填写省 / 自治区'
  if (!form.district.trim()) errors.district = '请填写区 / 县'
  if (!/^\d{6}$/.test(form.adcode.trim())) errors.adcode = '请填写 6 位行政区划代码'
  const lng = Number(form.longitude)
  const lat = Number(form.latitude)
  if (!Number.isFinite(lng) || lng < 73 || lng > 136) errors.longitude = '经度需在 73–136 之间'
  if (!Number.isFinite(lat) || lat < 3 || lat > 54) errors.latitude = '纬度需在 3–54 之间'
  if (Object.keys(errors).length) {
    ui.toast('请检查位置信息中标出的字段', 'error')
    return
  }
  emit('confirm', {
    address,
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    township: form.township.trim(),
    adcode: form.adcode.trim(),
    longitude: form.longitude.trim(),
    latitude: form.latitude.trim(),
    locAccuracy: form.locAccuracy ? Number(form.locAccuracy) : null
  })
  visible.value = false
}
</script>

<template>
  <zw-dialog v-model="visible" class="floc-dialog" title="选择养殖场位置" width="800">
    <zw-notice v-if="mapMode === 'fallback'" type="warning" icon="map" title="地图瓦片不可用">
      地图瓦片不可用，可手动填写经纬度；也可以点“使用当前位置”填入经纬度后补充地址与行政区划。资料变更中确认位置后需重新审核。
    </zw-notice>

    <div class="floc-map" :data-map-mode="mapMode" :data-map-ready="mapReady">
      <FarmMapPicker
        v-if="mapMode === 'live'"
        :longitude="form.longitude"
        :latitude="form.latitude"
        :fallback-longitude="longitude"
        :fallback-latitude="latitude"
        :label="farmName || '养殖场位置'"
        @pick="onMapPick"
        @ready="mapReady = true"
        @unavailable="onMapUnavailable"
      />
      <zw-map v-else-if="mapMode === 'fallback'" height="100%" pick :controls="false" :scale="false" :labels="[]" :markers="marker" />
      <span v-if="mapMode === 'live' && mapReady" class="floc-map-tip">
        <zw-icon name="info" :size="16" />
        {{ hasCoordinates ? '点击地图或拖动标记调整位置' : '点击地图选择养殖场位置' }}
      </span>
      <span v-else-if="mapMode === 'fallback'" class="floc-map-tip floc-map-tip--warn">
        <zw-icon name="info" :size="16" />
        地图瓦片不可用，可手动填写经纬度
      </span>
    </div>

    <div class="floc-form">
      <zw-form-item label="详细地址" required :error="errors.address" style="grid-column: 1 / -1">
        <v-text-field
          v-model="form.address"
          placeholder="例如：内蒙古自治区赤峰市松山区太平地镇 英金河北岸"
          prepend-inner-icon="location_on"
          maxlength="200"
          @update:model-value="clearError('address')"
        />
      </zw-form-item>
      <zw-form-item label="省 / 自治区" required :error="errors.province">
        <v-text-field v-model="form.province" placeholder="例如：内蒙古自治区" maxlength="30" @update:model-value="clearError('province')" />
      </zw-form-item>
      <zw-form-item label="市" :error="errors.city">
        <v-text-field v-model="form.city" placeholder="例如：赤峰市" maxlength="30" @update:model-value="clearError('city')" />
      </zw-form-item>
      <zw-form-item label="区 / 县" required :error="errors.district">
        <v-text-field v-model="form.district" placeholder="例如：松山区" maxlength="30" @update:model-value="clearError('district')" />
      </zw-form-item>
      <zw-form-item label="乡镇 / 街道" :error="errors.township">
        <v-text-field v-model="form.township" placeholder="例如：太平地镇" maxlength="30" @update:model-value="clearError('township')" />
      </zw-form-item>
      <zw-form-item label="行政区划代码" required :error="errors.adcode" hint="6 位数字，例如 150404">
        <v-text-field v-model="form.adcode" placeholder="150404" maxlength="6" @update:model-value="clearError('adcode')" />
      </zw-form-item>
      <zw-form-item label="定位精度（米）">
        <v-text-field v-model="form.locAccuracy" type="number" placeholder="手动填写可留空" min="0" max="10000" />
      </zw-form-item>
      <zw-form-item label="经度" required :error="errors.longitude" hint="73–136，最多 7 位小数">
        <v-text-field v-model="form.longitude" placeholder="例如：118.851600" @update:model-value="clearError('longitude')" />
      </zw-form-item>
      <zw-form-item label="纬度" required :error="errors.latitude" hint="3–54，最多 7 位小数">
        <v-text-field v-model="form.latitude" placeholder="例如：42.302500" @update:model-value="clearError('latitude')" />
      </zw-form-item>
    </div>

    <div class="floc-card">
      <zw-icon name="location_on" :size="20" fill />
      <div class="floc-card-main">
        <div class="floc-card-name">{{ farmName || '养殖场位置' }}</div>
        <div class="floc-card-address">{{ form.address || '还没有填写详细地址' }}</div>
        <div class="floc-card-hint">
          {{ form.locAccuracy ? `定位精度约 ${form.locAccuracy} 米` : '经纬度按填写值保存，不进行地址解析' }}
          <template v-if="changeMode"> · 确认后需重新审核</template>
        </div>
      </div>
      <v-btn variant="outlined" size="small" prepend-icon="my_location" :loading="locating" @click="useCurrentLocation">使用当前位置</v-btn>
    </div>

    <template #actions>
      <v-btn variant="outlined" @click="visible = false">取消</v-btn>
      <v-btn color="primary" @click="confirm">确认位置</v-btn>
    </template>
  </zw-dialog>
</template>

<style scoped>
.floc-map {
  position: relative;
  height: 260px;
  margin-top: 12px;
  overflow: hidden;
  isolation: isolate;
  z-index: 0;
  border-radius: var(--radius-sm);
  background: var(--map-land);
}
.floc-map :deep(.floc-leaflet) {
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.floc-map :deep(.zw-map) {
  height: 100%;
  box-sizing: border-box;
}
.floc-map-tip {
  position: absolute;
  left: 50%;
  bottom: 26px;
  z-index: 900;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: max-content;
  max-width: calc(100% - 24px);
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  background: #FFFFFFE6;
  color: var(--text-2);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  pointer-events: none;
}
.floc-map-tip--warn {
  border: 1px solid var(--warning-container);
  background: #FFF7E6F2;
  color: var(--warning);
}
.floc-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 16px;
  margin-top: 12px;
}
.floc-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  color: var(--primary);
}
.floc-card-main {
  flex: 1;
  min-width: 0;
}
.floc-card-name {
  font-weight: 600;
  color: var(--text-1);
}
.floc-card-address {
  margin-top: 2px;
  color: var(--text-1);
}
.floc-card-hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
@media (max-width: 640px) {
  .floc-form {
    grid-template-columns: minmax(0, 1fr);
  }
  .floc-card {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

<style>
/* zw-dialog 的内容会 Teleport 到 body，scoped 选择器够不到，用 .floc-dialog 限定在本对话框。 */
.floc-dialog .zw-dialog-body {
  max-height: calc(100vh - 170px);
  overflow-y: auto;
}
@media (max-width: 860px) {
  .floc-dialog .v-overlay__content {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
  }
  .floc-dialog .zw-dialog {
    width: 100%;
    max-width: 100%;
  }
  .floc-dialog .zw-dialog-body {
    max-height: calc(100vh - 150px);
  }
}
</style>
