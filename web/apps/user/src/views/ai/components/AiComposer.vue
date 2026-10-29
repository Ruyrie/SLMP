<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { FileVO } from '@zmt/shared'
import { recognizeSpeech, uploadAiImage } from '@/api/ai'
import { useAiAssistantStore } from '@/stores/ai/assistant'
import { errorTextOf, sseErrorMessage } from '@/stores/ai/shared'
import { useUiStore } from '@/stores/ui'

interface Attachment {
  key: string
  file: File
  url: string
  status: 'uploading' | 'ready' | 'error'
  progress: number
  fileId?: number
  error?: string
}

const store = useAiAssistantStore()
const ui = useUiStore()
const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const attachments = ref<Attachment[]>([])
const uploading = ref(false)
const sending = ref(false)
const dragover = ref(false)
const focused = ref(false)
const recording = ref(false)
const recognizing = ref(false)
const recordSeconds = ref(0)
const waveBars = ref<number[]>(new Array(28).fill(3))
let recorder: MediaRecorder | null = null
let recordStream: MediaStream | null = null
let recordChunks: Blob[] = []
let recordStartedAt = 0
let recordTimer = 0
let cancelled = false
let analyser: AnalyserNode | null = null
let analyserCtx: AudioContext | null = null
let analyserTimer = 0
let waveformData: Uint8Array | null = null

const streaming = computed(() => store.isStreaming)
const hasText = computed(() => !!store.draft.trim())
const readyAttachments = computed(() => attachments.value.filter((item) => item.status === 'ready'))
const hasFailed = computed(() => attachments.value.some((item) => item.status === 'error'))
const isUploading = computed(() => attachments.value.some((item) => item.status === 'uploading'))
const canSend = computed(
  () => !streaming.value && !sending.value && !isUploading.value && !hasFailed.value && (hasText.value || readyAttachments.value.length > 0)
)
const countText = computed(() => (attachments.value.length ? `已附 ${attachments.value.length} 张 · 最多 9 张` : ''))
const charCount = computed(() => [...store.draft].length)
const placeholder = computed(() => {
  if (attachments.value.length) return '描述这些照片里的问题…'
  // 新建的空会话已有 session_id，但仍算新对话：没有问答时按新对话提示
  const hasConversation = !!store.currentSessionId && (store.messagesStatus !== 'empty' || store.messages.length > 0)
  return hasConversation ? '继续追问…' : '输入你的养殖问题'
})
const uploadHint = computed(() => {
  if (hasFailed.value) {
    const failed = attachments.value.filter((item) => item.status === 'error').length
    return `${failed} 张图片上传失败，点击缩略图重试`
  }
  return '可拖入或粘贴图片，JPG / PNG 单张不超过 5MB'
})
const recordTime = computed(() => {
  const total = recordSeconds.value
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
})
const recordHint = computed(() => {
  if (recognizing.value) return '识别中…'
  if (recordSeconds.value >= 50) return `还能说 ${Math.max(60 - recordSeconds.value, 0)} 秒`
  return '说完点“完成”，识别出的文字会填入输入框'
})

function autoGrow(): void {
  const el = textarea.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 110)}px`
}

watch(
  () => store.draft,
  () => {
    void nextTick(autoGrow)
  }
)

function focusInput(): void {
  void nextTick(() => {
    const el = textarea.value
    if (!el) return
    el.focus()
    el.setSelectionRange(el.value.length, el.value.length)
  })
}

function openFilePicker(): void {
  if (attachments.value.length >= 9) {
    ui.toast('最多附 9 张图片', 'error')
    return
  }
  fileInput.value?.click()
}

function addFiles(files: File[]): boolean {
  if (!files.length) return false
  let accepted = 0
  for (const file of files) {
    if (attachments.value.length >= 9) {
      ui.toast('最多附 9 张图片', 'error')
      break
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      ui.toast('只支持 JPG 和 PNG 图片，单张不超过 5MB', 'error')
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      ui.toast('只支持 JPG 和 PNG 图片，单张不超过 5MB', 'error')
      continue
    }
    const attachment: Attachment = {
      key: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      file,
      url: URL.createObjectURL(file),
      status: 'uploading',
      progress: 0
    }
    attachments.value.push(attachment)
    accepted += 1
    // 上传进度、状态要改响应式数组里的那一份；改原始对象界面收不到，发送按钮会一直灰
    void upload(attachments.value[attachments.value.length - 1])
  }
  return accepted > 0
}

async function upload(attachment: Attachment): Promise<void> {
  uploading.value = true
  attachment.status = 'uploading'
  attachment.progress = 0
  attachment.error = ''
  try {
    const file = await uploadAiImage(attachment.file, (percent) => {
      attachment.progress = percent
    })
    attachment.fileId = Number(file.file_id)
    attachment.status = 'ready'
    attachment.progress = 100
  } catch (error) {
    attachment.status = 'error'
    attachment.error = errorTextOf(error, '上传失败')
  } finally {
    uploading.value = attachments.value.some((item) => item.status === 'uploading')
  }
}

function retryUpload(attachment: Attachment): void {
  if (attachment.status === 'error') void upload(attachment)
}

function removeAttachment(attachment: Attachment): void {
  URL.revokeObjectURL(attachment.url)
  attachments.value = attachments.value.filter((item) => item.key !== attachment.key)
}

function clearAttachments(): void {
  attachments.value.forEach((item) => URL.revokeObjectURL(item.url))
  attachments.value = []
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files || []))
  input.value = ''
}

function onDrop(event: DragEvent): void {
  dragover.value = false
  const files = Array.from(event.dataTransfer?.files || [])
  if (!files.length) return
  addFiles(files)
}

function onPaste(event: ClipboardEvent): void {
  const files = Array.from(event.clipboardData?.files || [])
  if (!files.length) return
  const images = files.filter((file) => file.type.startsWith('image/'))
  if (!images.length) return
  if (addFiles(images)) event.preventDefault()
}

function onDragOver(event: DragEvent): void {
  if (recording.value) return
  event.preventDefault()
  dragover.value = true
}

function onDragLeave(event: DragEvent): void {
  if (event.currentTarget === event.target) dragover.value = false
}

async function send(): Promise<void> {
  if (streaming.value) {
    store.stopStream()
    return
  }
  if (!canSend.value) return
  const text = store.draft.trim()
  const images = readyAttachments.value
    .filter((item) => item.fileId)
    .map((item) => ({ file_id: item.fileId, url: item.url } as FileVO))
  sending.value = true
  try {
    const result = await store.ask({
      text,
      images,
      contextCourseId: store.contextCourse?.id
    })
    if (result.ok) {
      store.draft = ''
      clearAttachments()
      void nextTick(autoGrow)
    } else if (result.error) {
      ui.toast(result.error, 'error')
    }
  } catch (error) {
    ui.toast(sseErrorMessage(error), 'error')
  } finally {
    sending.value = false
    focusInput()
  }
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  if (!streaming.value) void send()
}

/* ---------------- 语音输入 ---------------- */

function stopAnalyser(): void {
  if (analyserTimer) {
    window.clearInterval(analyserTimer)
    analyserTimer = 0
  }
  analyser = null
  waveformData = null
  if (analyserCtx) {
    void analyserCtx.close().catch(() => undefined)
    analyserCtx = null
  }
  waveBars.value = new Array(28).fill(3)
}

function startAnalyser(stream: MediaStream): void {
  const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!Ctx) return
  try {
    analyserCtx = new Ctx()
    const source = analyserCtx.createMediaStreamSource(stream)
    analyser = analyserCtx.createAnalyser()
    analyser.fftSize = 64
    source.connect(analyser)
    waveformData = new Uint8Array(analyser.frequencyBinCount)
    analyserTimer = window.setInterval(() => {
      if (!analyser || !waveformData) return
      analyser.getByteFrequencyData(waveformData as Uint8Array<ArrayBuffer>)
      const bars: number[] = []
      const step = Math.max(1, Math.floor(waveformData.length / 28))
      for (let i = 0; i < 28; i += 1) {
        const value = waveformData[i * step] || 0
        bars.push(Math.max(3, Math.round((value / 255) * 30)))
      }
      waveBars.value = bars
    }, 80)
  } catch {
    stopAnalyser()
  }
}

async function startRecording(): Promise<void> {
  if (streaming.value || recognizing.value) return
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    ui.toast('当前浏览器不支持录音，请改用键盘输入', 'error')
    return
  }
  try {
    recordStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch (error) {
    const name = (error as DOMException)?.name
    if (name === 'NotAllowedError' || name === 'SecurityError') {
      ui.toast('需要麦克风权限才能语音输入，请在浏览器地址栏左侧的网站设置中允许使用麦克风', 'error')
    } else if (name === 'NotFoundError') {
      ui.toast('没有检测到麦克风，请连接后再试，或改用键盘输入', 'error')
    } else {
      ui.toast('麦克风暂时无法使用，请稍后再试，或改用键盘输入', 'error')
    }
    return
  }
  cancelled = false
  recordChunks = []
  recordSeconds.value = 0
  recordStartedAt = Date.now()
  try {
    recorder = new MediaRecorder(recordStream)
  } catch {
    stopRecordStream()
    ui.toast('麦克风暂时无法使用，请改用键盘输入', 'error')
    return
  }
  recorder.ondataavailable = (event) => {
    if (event.data.size) recordChunks.push(event.data)
  }
  recorder.onstop = () => {
    const duration = Date.now() - recordStartedAt
    stopRecordStream()
    recording.value = false
    if (cancelled) {
      recordChunks = []
      return
    }
    if (duration < 1000) {
      ui.toast('说话时间太短', 'error')
      recordChunks = []
      return
    }
    void recognize(duration)
  }
  recorder.start()
  recording.value = true
  startAnalyser(recordStream)
  recordTimer = window.setInterval(() => {
    recordSeconds.value += 1
    if (recordSeconds.value >= 60) finishRecording()
  }, 1000)
  window.addEventListener('keydown', onRecordKeydown)
}

function stopRecordStream(): void {
  if (recordTimer) {
    window.clearInterval(recordTimer)
    recordTimer = 0
  }
  stopAnalyser()
  recordStream?.getTracks().forEach((track) => track.stop())
  recordStream = null
  window.removeEventListener('keydown', onRecordKeydown)
}

function finishRecording(): void {
  if (!recorder || recorder.state === 'inactive') return
  recorder.stop()
}

function cancelRecording(): void {
  if (!recorder || recorder.state === 'inactive') return
  cancelled = true
  recorder.stop()
}

function onRecordKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    cancelRecording()
  }
}

function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeText = (offset: number, text: string): void => {
    for (let i = 0; i < text.length; i += 1) view.setUint8(offset + i, text.charCodeAt(i))
  }
  writeText(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeText(8, 'WAVE')
  writeText(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeText(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  let offset = 44
  for (let i = 0; i < samples.length; i += 1) {
    const sample = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
    offset += 2
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

async function toWav(blob: Blob): Promise<Blob> {
  const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!Ctx) throw new Error('当前浏览器不支持音频处理')
  const ctx = new Ctx()
  try {
    const buffer = await blob.arrayBuffer()
    const audio = await ctx.decodeAudioData(buffer.slice(0))
    const input = audio.getChannelData(0)
    const targetRate = 16000
    if (audio.sampleRate === targetRate) return encodeWav(input, targetRate)
    const ratio = audio.sampleRate / targetRate
    const length = Math.max(1, Math.floor(input.length / ratio))
    const output = new Float32Array(length)
    for (let i = 0; i < length; i += 1) {
      const start = Math.floor(i * ratio)
      const end = Math.min(Math.floor((i + 1) * ratio), input.length)
      let sum = 0
      for (let j = start; j < end; j += 1) sum += input[j]
      output[i] = sum / Math.max(1, end - start)
    }
    return encodeWav(output, targetRate)
  } finally {
    await ctx.close().catch(() => undefined)
  }
}

async function recognize(durationMs: number): Promise<void> {
  recognizing.value = true
  try {
    const blob = new Blob(recordChunks, { type: recorder?.mimeType || 'audio/webm' })
    recordChunks = []
    if (!blob.size) {
      ui.toast('没有听清，请再说一遍，或改用键盘输入', 'error')
      return
    }
    const wav = await toWav(blob)
    const result = await recognizeSpeech(wav, durationMs)
    if (result.recognized && result.text) {
      const prefix = store.draft && !/\s$/.test(store.draft) ? ' ' : ''
      store.draft = `${store.draft}${prefix}${result.text}`
      focusInput()
      void nextTick(autoGrow)
    } else {
      ui.toast('没有听清，请再说一遍，或改用键盘输入', 'error')
    }
  } catch (error) {
    const message = errorTextOf(error, '语音识别暂不可用，请稍后再试')
    ui.toast(`${message}，可改用键盘输入`, 'error')
  } finally {
    recognizing.value = false
    recorder = null
  }
}

onBeforeUnmount(() => {
  if (recorder && recorder.state !== 'inactive') {
    cancelled = true
    recorder.stop()
  }
  stopRecordStream()
  clearAttachments()
})

defineExpose({
  focusInput
})
</script>

<template>
  <div class="k01-compose-wrap">
    <div
      class="k01-compose"
      :class="{ focus: focused || hasText || attachments.length, dragover }"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <div v-if="dragover" class="k01-drop">松开即可添加图片</div>
      <template v-if="recording || recognizing">
        <div class="k01-rec">
          <span v-if="!recognizing" class="k01-rec-dot" />
          <v-progress-circular v-else indeterminate size="20" width="2" color="primary" />
          <span class="k01-rec-label">{{ recognizing ? '识别中…' : '正在录音' }}</span>
          <span class="num k01-rec-time">{{ recordTime }}</span>
          <span v-if="!recognizing" class="k01-wave">
            <i v-for="(height, index) in waveBars" :key="index" :style="{ height: `${height}px` }" />
          </span>
        </div>
        <div class="k01-tools">
          <button class="zw-icon-btn" type="button" aria-label="上传图片" disabled style="opacity: .38">
            <zw-icon name="add_photo_alternate" :size="20" />
          </button>
          <button class="zw-icon-btn k01-mic-on" type="button" aria-label="结束并识别" :disabled="recognizing" @click="finishRecording">
            <zw-icon name="mic" :size="20" fill />
          </button>
          <span class="k01-tools-hint">{{ recordHint }}</span>
          <span class="k01-grow" />
          <v-btn variant="outlined" :disabled="recognizing" @click="cancelRecording">取消</v-btn>
          <v-btn color="primary" prepend-icon="check" :disabled="recognizing" @click="finishRecording">完成</v-btn>
        </div>
      </template>

      <template v-else>
        <div v-if="attachments.length" class="k01-att">
          <div v-for="attachment in attachments" :key="attachment.key" class="k01-att-item">
            <img :src="attachment.url" :alt="attachment.file.name" />
            <div v-if="attachment.status === 'uploading'" class="k01-att-mask">
              <v-progress-circular :model-value="attachment.progress" :size="28" :width="3" color="white" />
              <span class="num">{{ attachment.progress }}%</span>
            </div>
            <button
              v-else-if="attachment.status === 'error'"
              type="button"
              class="k01-att-mask k01-att-mask--error"
              @click="retryUpload(attachment)"
            >
              <zw-icon name="refresh" :size="18" />
              <span>上传失败</span>
              <span>点击重试</span>
            </button>
            <button
              class="k01-att-del"
              type="button"
              aria-label="移除图片"
              @click.stop="removeAttachment(attachment)"
            >
              <zw-icon name="close" :size="14" />
            </button>
          </div>
          <span class="k01-att-count">{{ countText }}</span>
        </div>

        <textarea
          ref="textarea"
          v-model="store.draft"
          class="k01-input"
          rows="1"
          maxlength="500"
          :placeholder="placeholder"
          aria-label="输入问题"
          @focus="focused = true"
          @blur="focused = false"
          @input="autoGrow"
          @keydown="onKeydown"
          @paste="onPaste"
        />

        <div class="k01-tools">
          <v-tooltip text="上传图片（最多 9 张）">
            <template #activator="{ props: tooltipProps }">
              <button
                v-bind="tooltipProps"
                class="zw-icon-btn"
                type="button"
                aria-label="上传图片"
                :disabled="attachments.length >= 9 || recording"
                @click="openFilePicker"
              >
                <zw-icon name="add_photo_alternate" :size="20" />
              </button>
            </template>
          </v-tooltip>
          <v-tooltip :text="streaming ? '回答生成中，暂不能语音输入' : '语音输入：点麦克风开始录音，说完点完成'">
            <template #activator="{ props: tooltipProps }">
              <button
                v-bind="tooltipProps"
                class="zw-icon-btn"
                type="button"
                aria-label="语音输入"
                :disabled="streaming || recognizing"
                @click="startRecording"
              >
                <zw-icon name="mic" :size="20" />
              </button>
            </template>
          </v-tooltip>
          <span class="k01-tools-hint" :class="{ 'k01-tools-hint--error': hasFailed }">{{ uploadHint }}</span>
          <span class="k01-grow" />
          <span v-if="charCount >= 480" class="k01-char num">{{ charCount }} / 500</span>
          <v-btn v-if="streaming" color="primary" prepend-icon="stop" @click="store.stopStream()">停止</v-btn>
          <v-btn v-else color="primary" prepend-icon="send" :disabled="!canSend" :loading="sending" @click="send">发送</v-btn>
        </div>
      </template>
    </div>
    <div class="k01-foot">
      <span><zw-icon name="info" :size="14" />由 AI 生成，仅供参考</span>
      <span>Enter 发送 · Shift + Enter 换行</span>
    </div>
    <input ref="fileInput" type="file" accept="image/jpeg,image/png" multiple hidden @change="onFileChange" />
  </div>
</template>

<style scoped>
.k01-compose-wrap {
  flex-shrink: 0;
  padding: 0 24px 12px;
}
.k01-compose {
  position: relative;
  max-width: 672px;
  margin: 0 auto;
  border: 1px solid var(--control-border);
  border-radius: var(--radius-md);
  background: var(--bg-card);
}
.k01-compose.focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px #0b57d01f;
}
.k01-compose.dragover {
  border: 2px dashed var(--primary);
  background: var(--primary-subtle);
}
.k01-drop {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  border-radius: var(--radius-md);
  background: var(--primary-subtle);
  color: var(--primary);
  font-size: 14px;
  pointer-events: none;
}
.k01-att {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px 0;
  overflow-x: auto;
}
.k01-att-item {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--fill-3);
}
.k01-att-item img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.k01-att-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border: 0;
  background: #00000080;
  color: #ffffff;
  font-size: 11px;
  line-height: 14px;
  cursor: default;
}
.k01-att-mask--error {
  background: #b3261ed9;
  cursor: pointer;
  font-family: inherit;
}
.k01-att-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #000000a6;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.k01-att-count {
  margin-left: 4px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
}
.k01-input {
  display: block;
  width: 100%;
  min-height: 48px;
  max-height: 110px;
  padding: 10px 14px 0;
  border: 0;
  outline: 0;
  resize: none;
  font: inherit;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-1);
  background: transparent;
  box-sizing: border-box;
}
.k01-input::placeholder {
  color: var(--text-3);
}
.k01-tools {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px 8px;
}
.k01-tools-hint {
  margin-left: 4px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.k01-tools-hint--error {
  color: var(--error);
}
.k01-char {
  font-size: 12px;
  color: var(--text-3);
  margin-right: 4px;
}
.k01-grow {
  flex: 1;
}
.k01-foot {
  max-width: 672px;
  margin: 6px auto 0;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-3);
}
.k01-foot span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.k01-rec {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 58px;
  padding: 0 14px;
}
.k01-rec-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--error);
  box-shadow: 0 0 0 4px #b3261e26;
  flex-shrink: 0;
}
.k01-rec-label {
  font-size: 14px;
  font-weight: 500;
}
.k01-rec-time {
  font-size: 14px;
  color: var(--text-2);
}
.k01-wave {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
  height: 32px;
  overflow: hidden;
}
.k01-wave i {
  display: block;
  width: 3px;
  border-radius: 2px;
  background: var(--primary);
  flex-shrink: 0;
  transition: height 80ms linear;
}
.k01-mic-on {
  background: var(--error-container) !important;
  color: var(--on-error-container) !important;
}
.k01-mic-on :deep(.zw-mso) {
  color: var(--on-error-container);
}
</style>
