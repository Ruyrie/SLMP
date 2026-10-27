<script setup lang="ts">
import { onMounted, watch, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { setShellActions, shellState } from '@zmt/shared/layouts/shell'
import { useAdminStore } from '@/stores/admin'
import { useUiStore } from '@/stores/ui'
import { useAdminMessagesStore } from '@/stores/messages'
import AdminMessagesDrawer from '@/components/AdminMessagesDrawer.vue'

const admin = useAdminStore()
const ui = useUiStore()
const messages = useAdminMessagesStore()
const router = useRouter()

watch(() => admin.admin?.admin_id, () => messages.reset(), { flush: 'sync' })

watchEffect(() => {
  shellState.side = 'admin'
  shellState.userName = admin.displayName
  shellState.avatarUrl = admin.admin?.avatar_url || ''
  shellState.adminRoleName = admin.roleName
  shellState.perms = admin.perms
  shellState.unread = admin.unread
  shellState.badges = {
    farmAudit: admin.badges.farm_audit || 0,
    farmChange: admin.badges.farm_change || 0,
    changeRequest: admin.badges.change_request || 0,
    recovery: admin.badges.account_recovery || 0,
    unread: admin.unread
  }
})

setShellActions({
  async logout() {
    await admin.logout()
    await router.push({ name: 'WA-S01' })
  },
  searchBatch(keyword: string) {
    router.push({ name: 'WA-S04', query: { keyword } })
  },
  openAccount() {
    router.push({ name: 'WA-S05' })
  },
  openMessages() {
    void messages.show()
  },
  switchFarm: () => undefined,
  toggleAllFarms: () => undefined
})

onMounted(() => {
  if (admin.loaded && !admin.mustChangePassword) admin.startPolling()
})

watch(
  () => [admin.loaded, admin.mustChangePassword],
  ([loaded, mustChange]) => {
    if (loaded && !mustChange) admin.startPolling()
    else admin.stopPolling()
  }
)
</script>

<template>
  <v-app class="zw-app zw-app--admin">
    <router-view />
    <AdminMessagesDrawer v-if="admin.loaded && !admin.mustChangePassword && messages.open" />
    <zw-snack v-model="ui.snackVisible" :text="ui.snackText" :type="ui.snackType === 'info' ? '' : ui.snackType" />
  </v-app>
</template>
