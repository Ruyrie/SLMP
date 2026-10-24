<script setup lang="ts">
import { onMounted, watch, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { setShellActions, shellState } from '@zmt/shared/layouts/shell'
import type { FarmBrief } from '@zmt/shared/layouts/shell'
import { useAuthStore } from '@/stores/auth'
import { useFarmStore } from '@/stores/farm'
import { useNoticeStore } from '@/stores/notice'
import { useUiStore } from '@/stores/ui'

const auth = useAuthStore()
const farm = useFarmStore()
const notice = useNoticeStore()
const ui = useUiStore()
const router = useRouter()

watchEffect(() => {
  shellState.side = 'user'
  shellState.role = farm.role
  shellState.userName = auth.user?.name || ''
  shellState.phone = auth.phone
  shellState.userId = Number(auth.user?.user_id || 0)
  shellState.farmId = farm.currentFarmId
  shellState.farmName = farm.farmName
  shellState.farmRoleLabel = farm.current?.role_text || farm.roleText
  shellState.farms = farm.farms
  shellState.allFarms = farm.viewAll
  shellState.unread = notice.unread
})

setShellActions({
  async switchFarm(farmId: number) {
    const target = farm.farms.find((item: FarmBrief) => item.farm_id === farmId)
    try {
      await farm.switchTo(farmId)
      await notice.load()
      ui.toast(`已切换到${target?.name || '新的养殖场'}`)
      const roles = router.currentRoute.value.meta.roles as string[] | undefined
      if (roles && !roles.includes(farm.role)) await router.push({ name: 'WU-T01' })
    } catch {
      ui.toast('切换养殖场失败，请稍后重试', 'error')
    }
  },
  toggleAllFarms(value: boolean) {
    if (value && !router.currentRoute.value.meta.allFarms) {
      ui.toast('当前页面不支持全部养殖场视角', 'info')
      return
    }
    farm.setViewAll(value)
    ui.toast(value ? '已切换到全部养殖场' : '已切换到单个养殖场')
  },
  async logout() {
    await auth.logout()
    farm.reset()
    notice.stop()
    notice.reset()
    await router.push({ name: 'WU-A01' })
  },
  searchBatch(keyword: string) {
    router.push({ name: 'WU-L03', query: { keyword } })
  },
  openAccount() {
    router.push({ name: 'WU-A04' })
  },
  openMessages() {
    router.push({ name: 'WU-T04' })
  }
})

onMounted(() => {
  if (auth.isLoggedIn) notice.start()
})

watch(
  () => auth.isLoggedIn,
  (value) => {
    if (value) notice.start()
    else notice.stop()
  }
)
</script>

<template>
  <v-app class="zw-app">
    <router-view />
    <zw-snack v-model="ui.snackVisible" :text="ui.snackText" :type="ui.snackType === 'info' ? '' : ui.snackType" />
  </v-app>
</template>
