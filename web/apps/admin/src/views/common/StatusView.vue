<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const type = computed(() => (route.name === 'not-found' ? 'not-found' : String(route.params.type || 'not-found')))
const reason = computed(() => String(route.query.reason || ''))
const view = computed(() => {
  if (type.value === 'forbidden') {
    return { icon: 'lock', title: '无权查看', desc: reason.value || '你没有该页面的操作权限，如需处理请联系超级管理员。', action: '返回平台概览' }
  }
  return { icon: 'search_off', title: '页面不存在', desc: '你访问的页面可能已被删除，或地址有误。', action: '返回平台概览' }
})
</script>

<template>
  <zw-shell side="admin" active="WA-U02" :crumbs="[view.title]">
    <zw-card>
      <zw-empty :icon="view.icon" :title="view.title" :desc="view.desc">
        <v-btn color="primary" variant="outlined" @click="router.push({ name: 'WA-S02' })">{{ view.action }}</v-btn>
        <v-btn variant="text" @click="router.back()">返回上一页</v-btn>
      </zw-empty>
    </zw-card>
  </zw-shell>
</template>
