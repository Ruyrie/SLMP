<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const page = computed(() => String(route.meta.page || ''))
const title = computed(() => String(route.meta.title || '页面'))
const crumbs = computed(() => (route.meta.crumbs as string[]) || [title.value])
</script>

<template>
  <zw-shell side="user" :active="page" :crumbs="crumbs">
    <zw-page-head :title="title" :desc="`页面 ${page} 正在实现中`" />
    <zw-card>
      <zw-empty icon="construction" title="页面建设中" :desc="`${page} 由对应模块代理完成，接口与交互正在接入。`" />
    </zw-card>
  </zw-shell>
</template>
