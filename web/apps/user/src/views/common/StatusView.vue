<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const type = computed(() => {
  if (route.name === 'not-found') return 'not-found'
  return String(route.params.type || 'not-found')
})
const reason = computed(() => String(route.query.reason || ''))

const view = computed(() => {
  if (type.value === 'forbidden') {
    return {
      icon: 'lock',
      title: '无权查看',
      desc: reason.value || '你没有该页面的查看权限，如需处理请联系养殖场所有者或场长。',
      tone: 'warning' as const,
      action: '返回工作台'
    }
  }
  if (type.value === 'loading') {
    return { icon: 'hourglass_top', title: '加载中', desc: '正在获取数据，请稍候。', tone: undefined, action: '' }
  }
  if (type.value === 'error') {
    return {
      icon: 'cloud_off',
      title: '加载失败',
      desc: '网络连接失败或服务暂时不可用，请稍后重新加载。',
      tone: 'error' as const,
      action: '重新加载'
    }
  }
  return {
    icon: 'search_off',
    title: '页面不存在',
    desc: '你访问的页面可能已被删除，或地址有误。',
    tone: undefined,
    action: '返回工作台'
  }
})

function act(): void {
  if (type.value === 'error') router.go(0)
  else router.push({ name: 'WU-T01' })
}
</script>

<template>
  <zw-shell side="user" active="WU-U02" :crumbs="[view.title]">
    <zw-card>
      <zw-empty :icon="view.icon" :title="view.title" :desc="view.desc" :tone="view.tone">
        <v-btn v-if="view.action" color="primary" variant="outlined" @click="act">{{ view.action }}</v-btn>
        <v-btn variant="text" @click="router.back()">返回上一页</v-btn>
      </zw-empty>
    </zw-card>
  </zw-shell>
</template>
