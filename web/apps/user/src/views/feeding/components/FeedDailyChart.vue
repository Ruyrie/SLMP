<script setup lang="ts">
import { computed } from 'vue'
import type { FeedExecDaily } from '@/api/feeding'
import { shortDay } from '@/stores/feeding/shared'

const props = withDefaults(defineProps<{
  daily: FeedExecDaily[]
  planQty?: number
  lineType?: string
}>(), {
  planQty: 0,
  lineType: 'FLAT'
})

const emit = defineEmits<{ select: [date: string] }>()

const W = 720
const H = 280
const L = 48
const R = 16
const T = 20
const B = 32

const chart = computed(() => {
  const data = props.daily || []
  const values = data.flatMap((item) => [Number(item.actual_qty || 0), Number(item.plan_qty || 0)])
  const peak = Math.max(props.planQty || 0, ...values, 1)
  const step = peak > 800 ? 200 : peak > 400 ? 100 : peak > 200 ? 50 : 20
  const top = Math.ceil(peak / step) * step
  const pw = W - L - R
  const ph = H - T - B
  const band = pw / Math.max(data.length, 1)
  const y = (value: number): number => T + ph - (value / top) * ph
  const bars = data.map((item, index) => {
    const value = Number(item.actual_qty || 0)
    const x = L + band * index + band / 2
    return {
      index,
      date: item.date || '',
      label: shortDay(item.date),
      x: x - Math.min(28, band * 0.55) / 2,
      w: Math.min(28, band * 0.55),
      y: y(value),
      h: Math.max(0, T + ph - y(value)),
      value,
      plan: Number(item.plan_qty || 0),
      over: !!item.over_threshold,
      done: Number(item.done_meals || 0),
      meals: Number(item.planned_meals || 0)
    }
  })
  const ticks = Array.from({ length: 5 }, (_, index) => {
    const value = (top / 4) * index
    return { value, y: y(value) }
  })
  const planY = props.lineType === 'FLAT' && props.planQty ? y(Number(props.planQty)) : null
  const planPoints = data
    .map((item, index) => ({ x: L + band * index + band / 2, y: y(Number(item.plan_qty || 0)), v: Number(item.plan_qty || 0) }))
    .filter((point) => point.v > 0)
  const planPath = planPoints.map((point, index) => `${index ? 'L' : 'M'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join('')
  return { data, bars, ticks, top, planY, planPath, band, pw, ph }
})

function tickLabel(value: number): string {
  return value.toLocaleString('en-US')
}

function onBar(date: string): void {
  if (date) emit('select', date)
}
</script>

<template>
  <div class="fd-chart">
    <svg :viewBox="`0 0 ${W} ${H}`" class="fd-svg" role="img" aria-label="每日投喂量柱状图">
      <g class="fd-grid">
        <line v-for="tick in chart.ticks" :key="tick.value" :x1="L" :x2="W - R" :y1="tick.y" :y2="tick.y" />
      </g>
      <g class="fd-axis">
        <text v-for="tick in chart.ticks" :key="`t-${tick.value}`" :x="L - 8" :y="tick.y + 4" text-anchor="end">{{ tickLabel(tick.value) }}</text>
        <template v-for="(bar, index) in chart.bars" :key="`x-${index}`">
          <text v-if="index % Math.max(1, Math.ceil(chart.data.length / 7)) === 0 || index === chart.data.length - 1" :x="bar.x + bar.w / 2" :y="H - 8" text-anchor="middle">{{ bar.label }}</text>
        </template>
      </g>
      <line v-if="chart.planY !== null" :x1="L" :x2="W - R" :y1="chart.planY" :y2="chart.planY" class="fd-plan" />
      <path v-else-if="chart.planPath" :d="chart.planPath" class="fd-plan" />
      <g>
        <rect
          v-for="bar in chart.bars"
          :key="`b-${bar.index}`"
          :x="bar.x"
          :y="bar.y"
          :width="bar.w"
          :height="bar.h"
          rx="2"
          class="fd-bar"
          @click="onBar(bar.date)"
        >
          <title>{{ bar.label }} · 实投 {{ bar.value.toLocaleString('en-US') }} 千克 · 计划 {{ bar.plan.toLocaleString('en-US') }} 千克 · 完成 {{ bar.done }} / {{ bar.meals }} 餐</title>
        </rect>
        <template v-for="bar in chart.bars" :key="`v-${bar.index}`">
          <text v-if="bar.over && chart.data.length <= 7" :x="bar.x + bar.w / 2" :y="bar.y - 6" text-anchor="middle" class="fd-value">{{ bar.value.toLocaleString('en-US') }}</text>
        </template>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.fd-chart { width: 100%; }
.fd-svg { width: 100%; height: auto; display: block; }
.fd-grid line { stroke: var(--line-1); stroke-width: 1; }
.fd-axis text { font-size: 12px; fill: var(--text-3); }
.fd-bar { fill: var(--chart-1); cursor: pointer; }
.fd-bar:hover { fill: var(--primary-hover, var(--primary)); }
.fd-plan { stroke: var(--chart-ref); stroke-width: 2; stroke-dasharray: 6 4; fill: none; }
.fd-value { font-size: 12px; fill: var(--text-2); }
</style>
