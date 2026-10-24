<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dayjs } from '@zmt/shared'
import { farmApi } from '@/api/farm'
import { useFarmCreateStore, type CreateMode, type FarmLocationPayload, type QualItem } from '@/stores/farm/create'
import { useFarmStore } from '@/stores/farm'
import { useUiStore } from '@/stores/ui'
import { farmStatusMeta, formatPhoneInput } from '@/stores/farm/shared'
import FarmLocationDialog from './components/FarmLocationDialog.vue'

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const farmStore = useFarmStore()
const store = useFarmCreateStore()

const fileInput = ref<HTMLInputElement | null>(null)
const dragOver = ref(false)
const locationOpen = ref(false)
const deleteOpen = ref(false)
const previewOpen = ref(false)
const previewUrl = ref('')

const mode = computed<CreateMode>(() => {
  const value = String(route.query.mode || '')
  return value === 'change' || value === 'registration' ? value : 'new'
})
const farmId = computed(() => Number(route.query.farmId || 0))
const isRejected = computed(() => store.mode === 'registration' && store.audit?.status === 'REJECTED')
const isPendingRegistration = computed(() => store.mode === 'registration' && store.audit?.status === 'PENDING')
const locked = computed(() => store.isReadonly || store.submitting)

const statusTag = computed(() => {
  if (store.mode === 'registration') {
    if (isRejected.value) return farmStatusMeta('REJECTED')
    if (isPendingRegistration.value) return farmStatusMeta('PENDING')
    return null
  }
  if (store.mode === 'change') {
    if (store.latestChange?.status === 'PENDING') return farmStatusMeta('PENDING')
    if (store.latestChange?.status === 'REJECTED') return farmStatusMeta('REJECTED')
    return farmStatusMeta('NORMAL')
  }
  return null
})

const pageDesc = computed(() => {
  if (store.mode === 'new') return '提交后由平台审核，结果发到消息中心。审核通过前只能修改注册信息。'
  if (store.mode === 'registration') {
    const audit = store.audit
    if (!audit) return '修改注册信息后重新提交审核'
    if (audit.status === 'REJECTED') {
      const submitted = audit.first_submitted_at ? dayjs(audit.first_submitted_at).format('M月D日') : '—'
      const audited = audit.audited_at ? dayjs(audit.audited_at).format('M月D日 HH:mm') : '—'
      return `${audit.name} · ${submitted} 提交 · ${audited} 驳回`
    }
    const submitted = audit.submitted_at ? dayjs(audit.submitted_at).format('M月D日 HH:mm') : '—'
    return `${audit.name} · ${submitted} 提交 · 审核结果会发到消息中心`
  }
  return `${store.detail?.name || ''} · 你是${store.detail?.role_text || '所有者'} · 当前生效的信息见右侧对比`
})

const categoryOptions = computed(() =>
  store.categories.map((category) => ({
    title: category.name,
    value: category.id,
    unit: category.count_unit_text || '头'
  }))
)

const markers = computed(() => {
  const lng = Number(store.form.longitude)
  const lat = Number(store.form.latitude)
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || !store.form.address) return []
  return [{ x: 50, y: 50, label: store.form.name || '养殖场位置' }]
})

const originalQualCount = computed(() => store.original?.qualIds.length ?? 0)
const addedCount = computed(() => {
  const original = store.original?.qualIds || []
  return store.qualFiles.filter((file) => {
    if (!file.fileId) return true
    return !original.includes(file.fileId)
  }).length
})
const removedCount = computed(() => {
  const original = store.original?.qualIds || []
  const current = store.qualIds
  return original.filter((id) => !current.includes(id)).length
})

const qualHint = computed(() => {
  if (store.qualError) return store.qualError
  if (store.isReadonly) return '资料变更审核中，审核期间不能再次提交。'
  if (store.mode === 'registration' && isRejected.value) {
    const count = store.audit?.reshoot_count || store.qualFiles.filter((file) => file.reshoot).length
    if (count > 0) {
      return `${count} 张需重拍：删除后上传原件的清晰照片。至少 1 张，最多 9 张；JPG 或 PNG，单张不超过 5MB`
    }
  }
  if (store.mode === 'change') {
    if (addedCount.value && removedCount.value) {
      return `新增 ${addedCount.value} 张、删除 ${removedCount.value} 张，共 ${store.qualFiles.length} 张；至少 1 张，最多 9 张，JPG 或 PNG，单张不超过 5MB`
    }
    if (addedCount.value) {
      return `新增 ${addedCount.value} 张，共 ${store.qualFiles.length} 张；至少 1 张，最多 9 张，JPG 或 PNG，单张不超过 5MB`
    }
    if (removedCount.value) {
      return `删除 ${removedCount.value} 张，共 ${store.qualFiles.length} 张；至少 1 张，最多 9 张，JPG 或 PNG，单张不超过 5MB`
    }
  }
  return '至少 1 张，最多 9 张；JPG 或 PNG，单张不超过 5MB。可拖拽图片到上传框'
})

const nameHint = computed(() => {
  if (store.nameChecking) return '正在检查名称是否重复…'
  if (store.mode === 'change') {
    if (store.changedFields.includes('NAME')) return `已修改 · 当前：${store.original?.name || '—'}`
    return '未修改'
  }
  return '同一账号下的养殖场名称不能重复'
})

const addressHint = computed(() => {
  if (store.mode === 'change') {
    if (store.changedFields.includes('ADDRESS')) return `已修改 · 当前：${store.original?.address || '—'}`
    return '未修改'
  }
  return store.form.address ? '已在地图上选点，点按可重新选择' : '请在地图上选择养殖场位置'
})

const auditTimeline = computed(() => {
  const records = store.audit?.records || []
  return records.map((record) => ({
    title: record.type_text || record.type,
    time: record.time ? dayjs(record.time).format('M月D日 HH:mm') : '',
    tag: record.status_tag || '',
    tagType:
      record.type === 'REJECT'
        ? ('error' as const)
        : record.type === 'APPROVE'
          ? ('success' as const)
          : record.type === 'RESUBMIT'
            ? ('warning' as const)
            : ('neutral' as const),
    tone:
      record.type === 'REJECT'
        ? 'error'
        : record.type === 'APPROVE'
          ? 'success'
          : record.type === 'RESUBMIT'
            ? 'warning'
            : 'neutral',
    icon:
      record.type === 'REJECT'
        ? 'block'
        : record.type === 'APPROVE'
          ? 'check'
          : record.type === 'RESUBMIT'
            ? 'schedule'
            : 'send',
    desc: record.desc || ''
  }))
})

const compareRows = computed(() => {
  if (store.mode !== 'change' || !store.original) return []
  const beforeName = store.original.name
  const beforeAddress = store.original.address
  const beforeQual = `${originalQualCount.value} 张`
  let afterQual = `${store.qualFiles.length} 张`
  if (addedCount.value && removedCount.value) afterQual += `（新增 ${addedCount.value} 张，删除 ${removedCount.value} 张）`
  else if (addedCount.value) afterQual += `（新增 ${addedCount.value} 张）`
  else if (removedCount.value) afterQual += `（删除 ${removedCount.value} 张）`
  return [
    { label: '名称', before: beforeName, after: store.form.name },
    { label: '地址', before: beforeAddress, after: store.form.address },
    { label: '资质图', before: beforeQual, after: afterQual }
  ]
})

const submitText = computed(() => {
  if (store.mode === 'new') return '提交审核'
  if (store.mode === 'registration') return '重新提交审核'
  return '提交重新审核'
})

const canDelete = computed(() => isRejected.value || isPendingRegistration.value)

function onPhoneChange(value: string): void {
  store.form.contactPhone = formatPhoneInput(value)
}

function openLocation(): void {
  if (locked.value) return
  locationOpen.value = true
}

function onLocationConfirm(value: FarmLocationPayload): void {
  store.setLocation(value)
}

function triggerFile(): void {
  if (locked.value) return
  fileInput.value?.click()
}

function onPick(event: Event): void {
  const target = event.target as HTMLInputElement
  if (target.files?.length) void store.addFiles(target.files)
  target.value = ''
}

function onDrop(event: DragEvent): void {
  dragOver.value = false
  if (locked.value) return
  if (event.dataTransfer?.files?.length) void store.addFiles(event.dataTransfer.files)
}

function removeFile(item: QualItem): void {
  if (locked.value) return
  store.removeFile(item)
  ui.toast('已删除 1 张资质图', 'info')
}

function retryFile(item: QualItem): void {
  store.retryUpload(item)
}

function previewFile(item: QualItem): void {
  if (!item.url && !item.thumbUrl) return
  previewUrl.value = item.url || item.thumbUrl || ''
  previewOpen.value = true
}

function onCategory(value: unknown): void {
  store.setCategory(value === null || value === undefined ? null : Number(value))
}

function onHeadClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (target?.closest('.zw-back')) cancel()
}

function goBack(): void {
  if (store.mode === 'change' && store.farmId) void router.push({ name: 'WU-F03', params: { farmId: store.farmId } })
  else void router.push({ name: 'WU-F01' })
}

function cancel(): void {
  if (store.isDirty) {
    store.leaveConfirm = true
    return
  }
  goBack()
}

function discard(): void {
  store.leaveConfirm = false
  goBack()
}

async function scrollToFirstError(): Promise<void> {
  await nextTick()
  const target = document.querySelector('.zw-form-item--error, .f02-qual-error')
  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function submit(): Promise<void> {
  const id = await store.submit()
  if (!id) {
    await scrollToFirstError()
    return
  }
  if (store.mode === 'change') {
    await router.push({ name: 'WU-F03', params: { farmId: store.farmId } })
  } else {
    await router.push({ name: 'WU-F01', query: { audit: String(id) } })
  }
}

async function removeFarm(): Promise<void> {
  if (!store.farmId) return
  try {
    await farmApi.deleteFarm(store.farmId)
    await farmStore.load(true)
    if (!farmStore.farms.length) farmStore.setCurrent(0)
    deleteOpen.value = false
    ui.toast(`已删除${store.audit?.name || '养殖场'}`)
    await router.push({ name: 'WU-F01' })
  } catch (error) {
    const value = error as { message?: string }
    ui.toast(value?.message || '删除养殖场失败，请稍后重试', 'error')
  }
}

onMounted(() => {
  void store.init(mode.value, farmId.value)
})
</script>

<template>
  <zw-shell side="user" active="WU-F02" :crumbs="store.mode === 'change' ? ['设置', '养殖场信息', '资料变更'] : ['设置', '我的养殖场', store.mode === 'new' ? '新建养殖场' : '修改注册信息']">
    <zw-page-head :title="store.title" back :desc="pageDesc" @click="onHeadClick">
      <template v-if="statusTag" #tags>
        <zw-tag :type="statusTag.tagType" :icon="statusTag.icon">{{ statusTag.label }}</zw-tag>
      </template>
    </zw-page-head>

    <v-skeleton-loader v-if="store.status === 'loading'" type="article, actions" />

    <zw-card v-else-if="store.status === 'error'">
      <zw-empty icon="cloud_off" title="页面加载失败" :desc="store.error" tone="error">
        <v-btn variant="outlined" @click="goBack">返回</v-btn>
        <v-btn color="primary" @click="store.init(mode, farmId)">重新加载</v-btn>
      </zw-empty>
    </zw-card>

    <zw-card v-else-if="store.status === 'forbidden'">
      <zw-empty icon="lock" title="无权查看" :desc="store.error || '该操作只对养殖场所有者开放。'" tone="warning">
        <v-btn variant="outlined" @click="goBack">返回</v-btn>
      </zw-empty>
    </zw-card>

    <div v-else class="f02-cols">
      <div class="f02-main zw-stack">
        <zw-notice v-if="isRejected && store.audit?.reject_reason" type="error" icon="rule">
          <div class="f02-reason-t">驳回原因</div>
          {{ store.audit.reject_reason }}
        </zw-notice>

        <zw-notice v-if="store.mode === 'change' && store.isReadonly" type="warning" icon="lock" title="资料变更审核中">
          审核期间不能再次提交，当前显示的是已提交的内容；审核结果会发到消息中心。
        </zw-notice>
        <zw-notice v-else-if="store.mode === 'change' && store.latestChange?.status === 'REJECTED'" type="error" icon="rule" title="资料变更已驳回">
          {{ store.latestChange.reject_reason || '请按审核意见修改后重新提交。' }}
        </zw-notice>
        <zw-notice v-else-if="store.mode === 'change'" icon="info">
          名称、地址和资质图的修改需要重新审核，只有所有者可以提交。<b>审核期间旧信息照常有效</b>，结果发到消息中心。
        </zw-notice>

        <zw-card title="注册信息" :sub="store.mode === 'change' ? '与当前生效信息对照，已修改的字段标出' : ''">
          <div class="zw-form-3">
            <zw-form-item
              label="养殖场名称"
              required
              :error="store.formErrors.name"
              :hint="nameHint"
              :class="{ 'f02-mod': store.changedFields.includes('NAME') }"
              :style="store.mode === 'change' ? 'grid-column: 1 / -1' : undefined"
            >
              <template v-if="store.mode === 'change' && store.changedFields.includes('NAME')" #label-extra>
                <zw-tag type="primary" size="small">已修改</zw-tag>
              </template>
              <v-text-field
                v-model="store.form.name"
                maxlength="20"
                :readonly="locked"
                @blur="store.checkName()"
              />
            </zw-form-item>
            <template v-if="store.mode !== 'change'">
              <zw-form-item label="主要品类" required :error="store.formErrors.main_category_id">
                <v-select
                  :model-value="store.form.mainCategoryId"
                  :items="categoryOptions"
                  item-title="title"
                  item-value="value"
                  :disabled="locked"
                  @update:model-value="onCategory"
                />
              </zw-form-item>
              <zw-form-item label="规模" required :error="store.formErrors.scale" hint="单位随主要品类显示“头”或“只”">
                <v-text-field v-model="store.form.scale" type="number" min="1" max="999999" :suffix="store.unitText" :readonly="locked" />
              </zw-form-item>
              <zw-form-item label="联系人" required :error="store.formErrors.contact_name">
                <v-text-field v-model="store.form.contactName" maxlength="20" :readonly="locked" />
              </zw-form-item>
              <zw-form-item label="联系电话" required :error="store.formErrors.contact_phone">
                <v-text-field
                  :model-value="store.form.contactPhone"
                  placeholder="138 0000 6620"
                  :readonly="locked"
                  @update:model-value="onPhoneChange"
                />
              </zw-form-item>
              <div class="f02-fill" />
            </template>

            <zw-form-item
              label="地址"
              required
              :error="store.formErrors.address"
              :hint="addressHint"
              :class="{ 'f02-mod': store.changedFields.includes('ADDRESS') }"
              style="grid-column: 1 / -1"
            >
              <template v-if="store.mode === 'change' && store.changedFields.includes('ADDRESS')" #label-extra>
                <zw-tag type="primary" size="small">已修改</zw-tag>
              </template>
              <div class="f02-addr-row">
                <v-text-field
                  :model-value="store.form.address"
                  placeholder="在地图上选择位置"
                  prepend-inner-icon="location_on"
                  readonly
                  @click="openLocation"
                />
                <v-btn variant="outlined" prepend-icon="map" :disabled="locked" @click="openLocation">在地图上选择</v-btn>
              </div>
            </zw-form-item>

            <div class="zw-form-item" style="grid-column: 1 / -1">
              <div class="zw-form-label">
                <span class="zw-req">*</span>资质图
                <zw-tag v-if="store.changedFields.includes('QUALIFICATION')" type="primary" size="small">已修改</zw-tag>
                <span class="f02-count num">{{ store.qualFiles.length }} / 9</span>
              </div>
              <div
                class="f02-grid"
                :class="{ 'f02-grid--over': dragOver }"
                @dragenter.prevent="dragOver = true"
                @dragover.prevent="dragOver = true"
                @dragleave.prevent="dragOver = false"
                @drop.prevent="onDrop"
              >
                <div
                  v-for="item in store.qualFiles"
                  :key="item.key"
                  class="f02-thumb"
                  :class="{ 'f02-thumb--reshoot': item.reshoot, 'f02-thumb--error': item.failed }"
                >
                  <img
                    v-if="item.thumbUrl || item.url"
                    :src="item.thumbUrl || item.url"
                    alt="资质图"
                    @click="previewFile(item)"
                  />
                  <div v-else class="f02-thumb-doc" @click="previewFile(item)">
                    <zw-thumb kind="doc" :w="120" :h="90" />
                  </div>
                  <span v-if="item.reshoot" class="f02-flag f02-flag--error">需重拍</span>
                  <span v-else-if="store.mode === 'change' && item.isNew" class="f02-flag">新增</span>
                  <button v-if="!locked" type="button" class="zw-thumb-del" aria-label="删除资质图" @click.stop="removeFile(item)">
                    <zw-icon name="close" :size="14" />
                  </button>
                  <span v-if="item.uploading" class="f02-thumb-mask">
                    <v-progress-circular :model-value="item.progress" :size="32" :width="3" color="white" />
                    <span class="num">{{ item.progress }}%</span>
                  </span>
                  <button v-else-if="item.failed" type="button" class="f02-thumb-mask f02-thumb-mask--error" @click.stop="retryFile(item)">
                    <zw-icon name="refresh" :size="20" />
                    <span>上传失败</span>
                    <span>点击重试</span>
                  </button>
                </div>

                <div v-if="!locked && store.qualFiles.length < 9" class="f02-add" role="button" tabindex="0" @click="triggerFile" @keydown.enter="triggerFile">
                  <zw-icon name="add" :size="24" />
                  <span>上传</span>
                </div>
              </div>
              <input ref="fileInput" type="file" accept="image/jpeg,image/png" multiple class="f02-file" @change="onPick" />
              <div class="f02-hint" :class="{ 'f02-qual-error': store.qualError, 'f02-hint--err': store.qualError }">{{ qualHint }}</div>
            </div>

            <zw-form-item v-if="store.mode === 'change'" label="变更说明" optional :error="store.formErrors.remark" style="grid-column: 1 / -1">
              <template #label-extra><span class="f02-count num">{{ store.form.remark.length }} / 100</span></template>
              <v-textarea v-model="store.form.remark" rows="2" maxlength="100" :readonly="locked" />
            </zw-form-item>
          </div>

          <template #foot>
            <div class="f02-foot">
              <v-btn v-if="canDelete && store.mode === 'registration'" variant="text" color="error" prepend-icon="delete" @click="deleteOpen = true">删除养殖场</v-btn>
              <span v-else-if="store.mode === 'change'" class="zw-faint body-s">没有修改任何内容时不能提交</span>
              <span v-else class="zw-faint body-s">带 * 为必填</span>
              <span style="flex: 1"></span>
              <v-btn variant="outlined" @click="cancel">取消</v-btn>
              <v-btn v-if="!store.isReadonly" color="primary" :loading="store.submitting" :disabled="!store.canSubmit" @click="submit">{{ submitText }}</v-btn>
            </div>
          </template>
        </zw-card>
      </div>

      <div class="f02-side zw-stack">
        <zw-card title="位置" flush>
          <zw-map :height="180" :controls="false" :scale="false" :labels="[]" :markers="markers" />
          <div class="f02-loc">
            <div class="f02-loc-t">{{ store.form.address || '还没有选择位置' }}</div>
            <div class="f02-loc-d">
              {{ store.form.longitude && store.form.latitude ? `经度 ${store.form.longitude} · 纬度 ${store.form.latitude}` : '未配置地图 Key，请手动填写地址与经纬度' }}
            </div>
          </div>
        </zw-card>

        <zw-card v-if="store.mode === 'change'" title="修改前后对比" flush>
          <zw-compare :rows="compareRows" before-title="当前生效" after-title="提交后" />
          <div class="f02-cmp-note">审核通过后新信息生效；驳回时在养殖场信息中显示原因，可修改后重新提交。</div>
        </zw-card>

        <zw-card v-else :title="isRejected ? '审核记录' : '提交后'">
          <zw-timeline v-if="isRejected && auditTimeline.length" :items="auditTimeline" />
          <zw-timeline
            v-else
            :items="[
              { title: '提交注册信息', desc: '名称、地址、规模、主要品类、联系人和资质图', tone: 'primary', icon: 'edit_note' },
              { title: '平台审核', desc: '审核结果发到消息中心；审核通过前只能修改注册信息', tone: 'neutral', icon: 'fact_check' },
              { title: '开始使用', desc: '审核通过后新增栏舍、邀请成员', tone: 'neutral', icon: 'fence' }
            ]"
          />
        </zw-card>
      </div>
    </div>

    <FarmLocationDialog
      v-model="locationOpen"
      :farm-name="store.form.name"
      :address="store.form.address"
      :province="store.form.province"
      :city="store.form.city"
      :district="store.form.district"
      :township="store.form.township"
      :adcode="store.form.adcode"
      :longitude="store.form.longitude"
      :latitude="store.form.latitude"
      :loc-accuracy="store.form.locAccuracy"
      :change-mode="store.mode === 'change'"
      @confirm="onLocationConfirm"
    />

    <zw-dialog v-model="store.leaveConfirm" title="放弃填写？" icon="warning" tone="warning" :width="420">
      <p class="f02-dialog-p">
        {{ store.mode === 'new' ? '已填写的内容和上传的资质图不会保存。' : '已修改的内容和上传的资质图不会保存。' }}
      </p>
      <template #actions>
        <v-btn variant="outlined" @click="store.leaveConfirm = false">继续填写</v-btn>
        <v-btn color="error" @click="discard">放弃</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="deleteOpen" :title="`删除${store.audit?.name || '养殖场'}？`" icon="delete" tone="error" :width="420">
      <p class="f02-dialog-p">{{ store.audit?.name || '该养殖场' }}还没有通过审核。删除后注册信息和资质图一并删除，不能恢复。</p>
      <template #actions>
        <v-btn variant="outlined" @click="deleteOpen = false">取消</v-btn>
        <v-btn color="error" @click="removeFarm">删除</v-btn>
      </template>
    </zw-dialog>

    <v-dialog v-model="previewOpen" width="760" scrim="#000000A6">
      <div class="f02-preview">
        <img :src="previewUrl" alt="资质图大图" />
        <button type="button" class="f02-preview-close" aria-label="关闭" @click="previewOpen = false">
          <zw-icon name="close" :size="20" />
        </button>
      </div>
    </v-dialog>
  </zw-shell>
</template>

<style scoped>
.f02-cols {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.f02-main {
  flex: 1 1 0;
  min-width: 0;
}
.f02-side {
  width: 360px;
  flex-shrink: 0;
}
.f02-fill {
  min-height: 1px;
}
.f02-reason-t {
  font-weight: 600;
}
.f02-addr-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.f02-addr-row > .v-input {
  flex: 1;
}
.f02-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-3);
}
.f02-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px;
  margin: -4px;
  border-radius: var(--radius-sm);
}
.f02-grid--over {
  outline: 1px dashed var(--primary);
  background: var(--primary-subtle);
}
.f02-thumb {
  position: relative;
  width: 120px;
  height: 90px;
  border: 1px solid var(--line-1);
  border-radius: var(--radius-sm);
  background: var(--fill-3);
  overflow: hidden;
  flex-shrink: 0;
}
.f02-thumb img,
.f02-thumb-doc,
.f02-thumb-doc :deep(.zw-thumb) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  cursor: zoom-in;
}
.f02-thumb--reshoot {
  outline: 2px solid var(--error);
  outline-offset: -2px;
}
.f02-thumb--error {
  outline: 2px solid var(--error);
  outline-offset: -2px;
}
.f02-flag {
  position: absolute;
  left: 6px;
  bottom: 6px;
  padding: 0 4px;
  border-radius: 2px;
  background: var(--primary);
  color: var(--on-primary);
  font-size: 11px;
  line-height: 16px;
}
.f02-flag--error {
  background: var(--error);
}
.f02-thumb-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: #00000080;
  color: #FFFFFF;
  font-size: 12px;
  line-height: 16px;
}
.f02-thumb-mask--error {
  border: 0;
  background: #B3261ED9;
  cursor: pointer;
}
.f02-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 120px;
  height: 90px;
  border: 1px dashed var(--control-border);
  border-radius: var(--radius-sm);
  background: var(--fill-1);
  color: var(--text-2);
  font-size: 12px;
  cursor: pointer;
  flex-shrink: 0;
}
.f02-add:hover {
  border-color: var(--primary);
  background: var(--primary-subtle);
  color: var(--primary);
}
.f02-add .zw-mso {
  color: var(--primary);
}
.f02-file {
  display: none;
}
.f02-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
.f02-hint--err {
  color: var(--error);
}
.f02-foot {
  display: flex;
  align-items: center;
  gap: 8px;
}
.f02-loc {
  padding: 12px 20px 16px;
}
.f02-loc-t {
  color: var(--text-1);
}
.f02-loc-d {
  margin-top: 2px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f02-cmp-note {
  padding: 0 16px 16px;
  margin-top: 12px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.f02-dialog-p {
  margin: 0;
  color: var(--text-2);
}
.f02-preview {
  position: relative;
  background: var(--media-backdrop);
  padding: 16px;
  border-radius: var(--radius-md);
  text-align: center;
}
.f02-preview img {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}
.f02-preview-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: #FFFFFF1F;
  color: #FFFFFF;
  cursor: pointer;
}
.f02-mod :deep(.v-field__outline) {
  color: var(--primary) !important;
  --v-field-border-width: 2px !important;
}
.f02-mod :deep(.zw-form-hint) {
  color: var(--primary);
}
</style>
