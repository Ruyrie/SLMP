<script setup lang="ts">
/** K04 / K02 通用的 HLS 课程播放器：倍速、清晰度、画中画、全屏与进度事件。 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Hls from 'hls.js'
import { formatDuration } from '@zmt/shared'
import type { VideoQuality, VideoSource } from '@/api/ai'
import CourseCover from './CourseCover.vue'

const props = withDefaults(
  defineProps<{
    sources?: VideoSource[]
    resumePosition?: number
    autoplay?: boolean
    simple?: boolean
    hasNext?: boolean
    posterKind?: string
    posterUrl?: string
    posterAlt?: string
    durationHint?: number
  }>(),
  {
    sources: () => [],
    resumePosition: 0,
    autoplay: false,
    simple: false,
    hasNext: false,
    posterKind: 'course',
    posterUrl: '',
    posterAlt: '课程封面',
    durationHint: 0
  }
)

const emit = defineEmits<{
  (event: 'ready', payload: { duration: number; resume: number; quality: VideoQuality }): void
  (event: 'tick', payload: { position: number; duration: number; playing: boolean; quality: VideoQuality }): void
  (event: 'started'): void
  (event: 'paused', payload: { position: number; duration: number }): void
  (event: 'seeked', payload: { position: number; duration: number }): void
  (event: 'ended', payload: { position: number; duration: number }): void
  (event: 'next'): void
  (event: 'quality-change', quality: VideoQuality): void
  (event: 'error', message: string): void
}>()

const containerEl = ref<HTMLElement | null>(null)
const videoEl = ref<HTMLVideoElement | null>(null)
const quality = ref<VideoQuality>('P720')
const playing = ref(false)
const ended = ref(false)
const buffering = ref(true)
const failed = ref(false)
const errorText = ref('')
const controlsVisible = ref(true)
const hasStarted = ref(false)
const speedMenu = ref(false)
const qualityMenu = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const buffered = ref(0)
const volume = ref(1)
const muted = ref(false)
const isFullscreen = ref(false)
const speed = ref(1)
const showControls = computed(() => controlsVisible.value || !playing.value || speedMenu.value || qualityMenu.value || failed.value)
const displayDuration = computed(() => duration.value > 0 ? duration.value : Math.max(0, props.durationHint || 0))
const displayPosition = computed(() => Math.min(currentTime.value, displayDuration.value || currentTime.value))
const progressPercent = computed(() =>
  displayDuration.value ? Math.min(100, (displayPosition.value / displayDuration.value) * 100) : 0
)
const speedLabel = computed(() => (speed.value === 1 ? '倍速' : `${speed.value.toFixed(2).replace(/0$/, '')}x`))
const qualityLabel = computed(() => (quality.value === 'P480' ? '480P' : '720P'))
const menuSpeedItems = [2, 1.5, 1.25, 1, 0.75]

let hls: Hls | null = null
let hideTimer = 0
let resumeApplied = false
let retried = false
let readyEmitted = false
let lastEmit = 0
let pendingStartPosition = 0

function sourceOf(target: VideoQuality): VideoSource | undefined {
  return (props.sources || []).find((item) => item.quality === target)
}

function breakHls(): void {
  if (hls) {
    hls.destroy()
    hls = null
  }
}

function resetVideo(): void {
  const video = videoEl.value
  if (video) {
    video.pause()
    video.removeAttribute('src')
    video.load()
  }
  breakHls()
}

function setupSource(target?: VideoQuality, keepTime: number | null = null, autoPlay = false): void {
  const video = videoEl.value
  if (!video) return
  const list = props.sources || []
  if (!list.length) {
    failed.value = true
    errorText.value = '播放地址不可用'
    buffering.value = false
    return
  }
  const nextQuality = target || quality.value
  const source = sourceOf(nextQuality) || list[0]
  quality.value = (source.quality === 'P480' ? 'P480' : 'P720') as VideoQuality
  hasStarted.value = false
  emit('quality-change', quality.value)
  const url = source.hls_url || ''
  resetVideo()
  pendingStartPosition = Math.max(0, keepTime ?? props.resumePosition)
  duration.value = 0
  currentTime.value = 0
  buffered.value = 0
  failed.value = false
  errorText.value = ''
  buffering.value = true
  ended.value = false
  resumeApplied = false
  retried = false
  readyEmitted = false
  if (!url) {
    failed.value = true
    buffering.value = false
    errorText.value = '播放地址不可用'
    return
  }
  if (Hls.isSupported()) {
    hls = new Hls({ enableWorker: true, lowLatencyMode: false })
    hls.loadSource(url)
    hls.attachMedia(video)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      applyStartPosition()
      if (autoPlay) void startPlayback()
    })
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return
      if (!retried && data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        retried = true
        try {
          hls?.startLoad()
          return
        } catch {
          /* 继续走失败分支 */
        }
      }
      if (!retried && data.type === Hls.ErrorTypes.MEDIA_ERROR) {
        retried = true
        try {
          hls?.recoverMediaError()
          return
        } catch {
          /* 继续走失败分支 */
        }
      }
      failed.value = true
      buffering.value = false
      errorText.value = '播放失败，点击重试'
      emit('error', errorText.value)
    })
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = url
    video.addEventListener(
      'loadedmetadata',
      () => {
        applyStartPosition()
        if (autoPlay) void startPlayback()
      },
      { once: true }
    )
  } else {
    failed.value = true
    buffering.value = false
    errorText.value = '当前浏览器不支持 HLS 播放'
    emit('error', errorText.value)
  }
}

function applyStartPosition(): void {
  const video = videoEl.value
  if (!video || !Number.isFinite(video.duration) || video.duration <= 0) return
  // 旧素材的位置超过新素材末尾时从头播放，不把未实际播放的旧位置上报给服务端。
  const target = pendingStartPosition < Math.max(video.duration - 0.5, 0) ? pendingStartPosition : 0
  try {
    video.currentTime = target
  } catch {
    return
  }
  resumeApplied = true
  currentTime.value = video.currentTime
  duration.value = video.duration
  if (!readyEmitted) {
    readyEmitted = true
    emit('ready', { duration: duration.value, resume: video.currentTime, quality: quality.value })
  }
  updateBuffered()
}

async function startPlayback(): Promise<void> {
  const video = videoEl.value
  if (!video) return
  try {
    await video.play()
  } catch {
    playing.value = false
    buffering.value = false
    controlsVisible.value = true
  }
}

function togglePlay(): void {
  const video = videoEl.value
  if (!video) return
  if (video.paused) {
    if (ended.value) {
      ended.value = false
      video.currentTime = 0
    }
    void startPlayback()
  } else {
    video.pause()
  }
}

function seekBy(delta: number): void {
  const video = videoEl.value
  if (!video || !Number.isFinite(video.duration)) return
  video.currentTime = Math.max(0, Math.min(video.duration, video.currentTime + delta))
  currentTime.value = video.currentTime
  emit('seeked', {
    position: Math.min(video.currentTime, displayDuration.value || video.currentTime),
    duration: displayDuration.value
  })
}

function onProgressInput(event: Event): void {
  const input = event.target as HTMLInputElement
  const value = Math.max(0, Math.min(Number(input.value || 0), displayDuration.value))
  currentTime.value = value
}

function onProgressChange(event: Event): void {
  const video = videoEl.value
  if (!video) return
  const input = event.target as HTMLInputElement
  if (!Number.isFinite(video.duration) || video.duration <= 0) return
  const value = Math.max(0, Math.min(Number(input.value || 0), video.duration))
  video.currentTime = value
  ended.value = false
  emit('seeked', {
    position: Math.min(value, displayDuration.value || value),
    duration: displayDuration.value
  })
}

function onLoadedMetadata(): void {
  const video = videoEl.value
  if (!video) return
  duration.value = Number.isFinite(video.duration) ? video.duration : 0
  if (!resumeApplied) applyStartPosition()
  buffering.value = false
}

function syncMediaState(): void {
  const video = videoEl.value
  if (!video) return
  if (Number.isFinite(video.duration) && video.duration > 0) duration.value = video.duration
  if (!resumeApplied) applyStartPosition()
  if (video.readyState >= 2 || (!video.paused && video.readyState >= 1)) buffering.value = false
  currentTime.value = video.currentTime
}

function onTimeUpdate(): void {
  const video = videoEl.value
  if (!video) return
  currentTime.value = video.currentTime
  if (Number.isFinite(video.duration)) duration.value = video.duration
  updateBuffered()
  const now = Date.now()
  if (now - lastEmit >= 500) {
    lastEmit = now
    emit('tick', {
      position: Math.min(video.currentTime, displayDuration.value || video.currentTime),
      duration: displayDuration.value,
      playing: !video.paused && !video.ended,
      quality: quality.value
    })
  }
}

function updateBuffered(): void {
  const video = videoEl.value
  if (!video || !video.buffered.length) return
  try {
    buffered.value = video.buffered.end(video.buffered.length - 1) / (video.duration || 1)
  } catch {
    buffered.value = 0
  }
}

function onPlay(): void {
  playing.value = true
  hasStarted.value = true
  ended.value = false
  buffering.value = false
  scheduleHide()
  emit('started')
}

function onPause(): void {
  playing.value = false
  controlsVisible.value = true
  const video = videoEl.value
  if (video && !ended.value) {
    emit('paused', {
      position: Math.min(video.currentTime, displayDuration.value || video.currentTime),
      duration: displayDuration.value
    })
  }
}

function onWaiting(): void {
  buffering.value = true
}

function onPlaying(): void {
  buffering.value = false
  playing.value = true
}

function onEnded(): void {
  ended.value = true
  playing.value = false
  controlsVisible.value = true
  const video = videoEl.value
  if (video) {
    if (Number.isFinite(video.duration) && video.duration > 0) duration.value = video.duration
    currentTime.value = video.currentTime
    emit('tick', {
      position: displayPosition.value,
      duration: duration.value,
      playing: false,
      quality: quality.value
    })
  }
  emit('ended', { position: displayPosition.value, duration: duration.value })
}

function onVolume(): void {
  const video = videoEl.value
  if (!video) return
  video.muted = !video.muted
  muted.value = video.muted
}

function setSpeed(value: number): void {
  const video = videoEl.value
  speed.value = value
  speedMenu.value = false
  if (video) video.playbackRate = value
}

function chooseQuality(value: VideoQuality): void {
  if (value === quality.value) {
    qualityMenu.value = false
    return
  }
  const video = videoEl.value
  const position = video?.currentTime || 0
  const wasPlaying = !!video && !video.paused
  qualityMenu.value = false
  setupSource(value, position, wasPlaying)
}

async function toggleFullscreen(): Promise<void> {
  const container = containerEl.value
  if (!container) return
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await container.requestFullscreen()
  } catch {
    /* 浏览器拒绝全屏时忽略 */
  }
}

async function togglePip(): Promise<void> {
  const video = videoEl.value
  if (!video) return
  try {
    if (document.pictureInPictureElement) await document.exitPictureInPicture()
    else await video.requestPictureInPicture()
  } catch {
    emit('error', '当前浏览器不支持画中画')
  }
}

function onFullscreenChange(): void {
  isFullscreen.value = !!document.fullscreenElement
}

function scheduleHide(): void {
  if (hideTimer) window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => {
    if (playing.value && !speedMenu.value && !qualityMenu.value) controlsVisible.value = false
  }, 3000)
}

function onMouseMove(): void {
  controlsVisible.value = true
  if (playing.value) scheduleHide()
}

function retry(): void {
  failed.value = false
  setupSource(quality.value, currentTime.value, playing.value)
}

function replay(): void {
  const video = videoEl.value
  if (!video) return
  ended.value = false
  video.currentTime = 0
  void startPlayback()
}

function onKeydown(event: KeyboardEvent): void {
  const target = event.target as HTMLElement | null
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT' || target.isContentEditable)) return
  if (event.key === ' ' || event.code === 'Space') {
    event.preventDefault()
    togglePlay()
  } else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    seekBy(-10)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    seekBy(10)
  } else if (event.key === 'f' || event.key === 'F') {
    void toggleFullscreen()
  } else if (event.key === 'm' || event.key === 'M') {
    onVolume()
  }
}

function onCustomError(message: string): void {
  failed.value = true
  buffering.value = false
  errorText.value = message
}

watch(
  () => props.sources,
  () => {
    if (!videoEl.value) return
    setupSource(quality.value, currentTime.value, false)
  },
  { deep: true }
)

onMounted(async () => {
  await nextTick()
  setupSource(quality.value, null, props.autoplay)
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  if (hideTimer) window.clearTimeout(hideTimer)
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  resetVideo()
})

defineExpose({
  seekTo(seconds: number): void {
    const video = videoEl.value
    if (!video) return
    video.currentTime = Math.max(0, Math.min(seconds, Number.isFinite(video.duration) ? video.duration : 0))
    ended.value = false
  },
  play(): void {
    void startPlayback()
  },
  pause(): void {
    videoEl.value?.pause()
  },
  replay(): void {
    replay()
  },
  getPosition(): number {
    return videoEl.value?.currentTime || 0
  },
  getDuration(): number {
    return duration.value
  },
  getQuality(): VideoQuality {
    return quality.value
  },
  onCustomError
})
</script>

<template>
  <div
    ref="containerEl"
    class="k04-player"
    @mousemove="onMouseMove"
    @mouseleave="playing && (controlsVisible = false)"
  >
    <video
      ref="videoEl"
      class="k04-video"
      playsinline
      preload="metadata"
      :poster="posterUrl || undefined"
      @loadedmetadata="onLoadedMetadata"
      @loadeddata="syncMediaState"
      @canplay="syncMediaState"
      @durationchange="syncMediaState"
      @timeupdate="onTimeUpdate"
      @progress="updateBuffered"
      @play="onPlay"
      @pause="onPause"
      @waiting="onWaiting"
      @playing="onPlaying"
      @ended="onEnded"
      @click="togglePlay"
    />

    <div v-if="!hasStarted && !failed && !ended" class="k04-poster">
      <CourseCover :src="posterUrl" :alt="posterAlt" :kind="posterKind" class="k04-poster-thumb" />
    </div>

    <div v-if="buffering && !failed && !ended" class="k04-buffer">
      <v-progress-circular indeterminate size="48" width="3" color="white" />
    </div>

    <div v-if="failed" class="k04-fail">
      <zw-icon name="refresh" :size="32" />
      <span>{{ errorText || '播放失败，点击重试' }}</span>
      <v-btn variant="outlined" color="white" @click.stop="retry">重新加载</v-btn>
    </div>

    <div v-if="!playing && !ended && !failed && !buffering" class="k04-big-play" @click.stop="togglePlay">
      <zw-icon name="play_arrow" :size="40" fill />
    </div>

    <div v-if="ended" class="k04-ended">
      <slot name="ended">
        <div class="k04-ended-t">本节已播放完</div>
        <div class="k04-ended-btns">
          <v-btn variant="outlined" prepend-icon="replay" @click.stop="replay">重播</v-btn>
        </div>
      </slot>
    </div>

    <div v-if="showControls && !failed" class="k04-ctrl" @click.stop>
      <div class="k04-bar">
        <div class="k04-buf" :style="{ width: `${buffered * 100}%` }" />
        <div class="k04-fill" :style="{ width: `${progressPercent}%` }" />
        <input
          class="k04-range"
          type="range"
          min="0"
          :max="displayDuration || 0"
          step="0.1"
          :value="displayPosition"
          aria-label="播放进度"
          @input="onProgressInput"
          @change="onProgressChange"
        />
      </div>
      <div class="k04-row">
        <button type="button" class="k04-ibtn" :aria-label="playing ? '暂停' : '播放'" @click="togglePlay">
          <zw-icon :name="playing ? 'pause' : 'play_arrow'" :size="24" fill />
        </button>
        <button type="button" class="k04-ibtn" aria-label="后退 10 秒" @click="seekBy(-10)">
          <zw-icon name="replay_10" :size="22" />
        </button>
        <button type="button" class="k04-ibtn" aria-label="前进 10 秒" @click="seekBy(10)">
          <zw-icon name="forward_10" :size="22" />
        </button>
        <button v-if="!simple && hasNext" type="button" class="k04-ibtn" aria-label="下一节" @click="emit('next')">
          <zw-icon name="skip_next" :size="24" fill />
        </button>
        <span class="k04-time num">{{ formatDuration(Math.floor(displayPosition)) }} / {{ formatDuration(Math.floor(displayDuration)) }}</span>
        <button type="button" class="k04-ibtn" :aria-label="muted ? '取消静音' : '静音'" @click="onVolume">
          <zw-icon :name="muted ? 'volume_off' : 'volume_up'" :size="22" />
        </button>
        <span class="k04-grow" />
        <button type="button" class="k04-text" :class="{ on: speedMenu }" @click="speedMenu = !speedMenu; qualityMenu = false">
          {{ speedLabel }}
        </button>
        <button type="button" class="k04-text" :class="{ on: qualityMenu }" @click="qualityMenu = !qualityMenu; speedMenu = false">
          {{ qualityLabel }}
        </button>
        <button v-if="!simple" type="button" class="k04-ibtn" aria-label="画中画" @click="togglePip">
          <zw-icon name="picture_in_picture_alt" :size="22" />
        </button>
        <button type="button" class="k04-ibtn" :aria-label="isFullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen">
          <zw-icon :name="isFullscreen ? 'fullscreen_exit' : 'fullscreen'" :size="24" />
        </button>
      </div>

      <div v-if="speedMenu" class="k04-menu k04-menu--speed" @click.stop>
        <button
          v-for="item in menuSpeedItems"
          :key="item"
          type="button"
          :class="{ on: speed === item }"
          @click="setSpeed(item)"
        >{{ item.toFixed(2).replace(/0$/, '') }}x</button>
      </div>
      <div v-if="qualityMenu" class="k04-menu k04-menu--quality" @click.stop>
        <button type="button" :class="{ on: quality === 'P720' }" @click="chooseQuality('P720')">720P 高清</button>
        <button type="button" :class="{ on: quality === 'P480' }" @click="chooseQuality('P480')">480P 流畅</button>
      </div>
    </div>

    <div v-if="(speedMenu || qualityMenu) && showControls && !failed" class="k04-menu-mask" @click="speedMenu = false; qualityMenu = false" />
  </div>
</template>

<style scoped>
.k04-player {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--media-backdrop);
  color: var(--on-media);
  user-select: none;
}
.k04-player:fullscreen {
  width: 100vw;
  height: 100vh;
  aspect-ratio: auto;
  border-radius: 0;
}
.k04-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: var(--media-backdrop);
  cursor: pointer;
}
.k04-poster {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.k04-poster-thumb {
  position: absolute !important;
  inset: 0;
  width: 100% !important;
  height: 100% !important;
  opacity: 0.55;
}
.k04-buffer {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.k04-fail {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #000000a6;
  text-align: center;
  font-size: 14px;
  z-index: 3;
}
.k04-big-play {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 64px;
  height: 64px;
  margin: -32px 0 0 -32px;
  border-radius: 50%;
  background: #00000080;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.k04-ended {
  position: absolute;
  inset: 0 0 70px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: #000000a6;
  z-index: 2;
}
.k04-ended-t {
  font-size: 16px;
  line-height: 24px;
  font-weight: 500;
}
.k04-ended-btns {
  display: flex;
  gap: 12px;
}
.k04-ctrl {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24px 16px 8px;
  background: linear-gradient(180deg, #00000000 0%, #000000b3 100%);
  z-index: 4;
}
.k04-bar {
  position: relative;
  height: 4px;
  border-radius: 2px;
  background: #ffffff4d;
  margin-bottom: 8px;
}
.k04-buf {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: #ffffff73;
  border-radius: 2px;
}
.k04-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: var(--primary);
  border-radius: 2px;
}
.k04-range {
  position: absolute;
  left: -4px;
  right: -4px;
  top: -6px;
  width: calc(100% + 8px);
  height: 16px;
  margin: 0;
  opacity: 0;
  cursor: pointer;
}
.k04-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.k04-ibtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--on-media);
  cursor: pointer;
}
.k04-ibtn:hover {
  background: #ffffff1f;
}
.k04-time {
  font-size: 13px;
  margin-left: 2px;
  white-space: nowrap;
}
.k04-grow {
  flex: 1;
}
.k04-text {
  height: 32px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--on-media);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}
.k04-text.on,
.k04-text:hover {
  background: #ffffff26;
}
.k04-menu {
  position: absolute;
  bottom: 78px;
  min-width: 96px;
  padding: 4px 0;
  border-radius: var(--radius-sm);
  background: #1d2129f2;
  z-index: 6;
}
.k04-menu--speed {
  right: 86px;
}
.k04-menu--quality {
  right: 56px;
}
.k04-menu button {
  display: block;
  width: 100%;
  padding: 6px 16px;
  border: 0;
  background: transparent;
  color: var(--on-media);
  font: inherit;
  font-size: 14px;
  text-align: center;
  white-space: nowrap;
  cursor: pointer;
}
.k04-menu button:hover {
  background: #ffffff1a;
}
.k04-menu button.on {
  color: var(--inverse-primary);
  font-weight: 500;
}
/* 菜单在 .k04-ctrl（z-index 4）的层叠上下文里，遮罩必须低于它，否则会盖住菜单项吃掉点击。 */
.k04-menu-mask {
  position: absolute;
  inset: 0;
  z-index: 3;
}
</style>
