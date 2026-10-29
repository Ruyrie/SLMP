<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminMenus, filterMenus, isGroup, quickItems, userMenus, type MenuItem, type MenuNode } from '../menus'
import { shellActions, shellState, toggleSider, missingRouteParams, menuRouteParams, type ShellSide } from './shell'

const props = withDefaults(
  defineProps<{
    side?: ShellSide
    role?: 'owner' | 'manager' | 'staff'
    active?: string
    crumbs?: string[]
    farm?: string
    allFarms?: boolean
    unread?: number
    pad?: boolean
  }>(),
  // allFarms 不传时要保持 undefined（布尔 prop 缺省会被转成 false），才能回落到全局 shellState.allFarms
  { pad: true, allFarms: undefined, unread: undefined }
)

const router = useRouter()
const route = useRoute()

const side = computed<ShellSide>(() => props.side || shellState.side)
const role = computed(() => props.role || shellState.role)
const menu = computed<MenuNode[]>(() =>
  side.value === 'admin' ? filterMenus(adminMenus, shellState.perms) : userMenus(role.value)
)
const activeKey = computed(() => props.active || String(route.name || ''))
const farmName = computed(() => props.farm || shellState.farmName || '养殖场')
const roleLabel = computed(() => shellState.farmRoleLabel || (role.value === 'staff' ? '员工' : role.value === 'manager' ? '场长' : '所有者'))
const unreadN = computed(() => props.unread ?? shellState.unread)
const crumbs = computed(() => props.crumbs || [])
const isAllFarms = computed(() => props.allFarms ?? shellState.allFarms)
const quick = computed(() => quickItems(role.value))
const searchText = ref('')
const farmMenu = ref(false)
const userMenu = ref(false)
const quickMenu = ref(false)

const isActive = (item: MenuItem): boolean => item.key === activeKey.value || !!item.match?.includes(activeKey.value)

const openGroups = reactive<Record<string, boolean>>({})
function syncGroups(): void {
  for (const node of menu.value) {
    if (!isGroup(node)) continue
    if (node.children.some((child) => isActive(child))) openGroups[node.group] = true
  }
}
watch([menu, activeKey], syncGroups, { immediate: true })

function toggleGroup(group: string): void {
  openGroups[group] = !openGroups[group]
}

function go(name: string): void {
  if (name === String(route.name || '')) return
  const record = router.getRoutes().find((item) => String(item.name || '') === name)
  const params = menuRouteParams(record?.path, shellState.farmId)
  if (missingRouteParams(record?.path, params).length) {
    void router.push({ name: side.value === 'admin' ? 'WA-S02' : 'WU-F01' })
    return
  }
  void router.push({ name, params: Object.keys(params).length ? params : undefined }).catch(() => {
    /* 导航被守卫取消时保持当前页面，不产生未处理的 Promise 异常 */
  })
}

function goQuick(item: { key: string; action?: string; query?: Record<string, string> }): void {
  quickMenu.value = false
  const query: Record<string, string> = { ...(item.query || {}) }
  if (item.action) query.action = item.action
  router.push({ name: item.key, query: Object.keys(query).length ? query : undefined })
}

function onSearch(): void {
  const keyword = searchText.value.trim()
  if (!keyword) return
  if (shellActions.searchBatch) shellActions.searchBatch(keyword)
  else router.push({ name: side.value === 'admin' ? 'WA-S04' : 'WU-L03', query: { keyword } })
}

function onFarm(): void {
  router.push({ name: 'WU-F01' })
}

function onMessages(): void {
  if (side.value === 'admin') shellActions.openMessages()
  else router.push({ name: 'WU-T04' })
}

function onAccount(): void {
  userMenu.value = false
  if (side.value === 'admin') {
    router.push({ name: 'WA-S05' })
  } else {
    router.push({ name: 'WU-A04' })
  }
}

function onLogout(): void {
  userMenu.value = false
  shellActions.logout()
}

function badgeOf(item: MenuItem): string {
  const value = item.badgeKey ? shellState.badges[item.badgeKey] ?? 0 : item.badge ?? 0
  if (!value) return ''
  return value > 99 ? '99+' : String(value)
}

function hasBadge(node: MenuNode): boolean {
  return isGroup(node) && node.children.some((child) => !!badgeOf(child))
}

function farmBadge(farm: { status: string }): string {
  return farm.status === 'PENDING' ? '待审核' : farm.status === 'DISABLED' ? '已停用' : ''
}
</script>

<template>
  <div class="zw-shell" :class="[`zw-shell--${side}`, { 'zw-shell--collapsed': shellState.collapsed }]">
    <v-navigation-drawer permanent :width="shellState.collapsed ? 64 : 220" class="zw-sider" floating>
      <div class="zw-logo" role="button" tabindex="0" @click="go(side === 'admin' ? 'WA-S02' : 'WU-T01')" @keydown.enter="go(side === 'admin' ? 'WA-S02' : 'WU-T01')">
        <span class="zw-logo-name">智牧通</span>
        <span v-if="side === 'admin'" class="zw-logo-tag">管理端</span>
      </div>
      <nav class="zw-menu" aria-label="主菜单">
        <template v-for="(node, index) in menu" :key="index">
          <button v-if="isGroup(node)" type="button" class="zw-menu-item zw-menu-group-head" :class="{ 'has-on': node.children.some(isActive) }" @click="toggleGroup(node.group)">
            <zw-icon :name="node.icon || 'folder'" :size="18" />
            <span class="zw-menu-label">{{ node.group }}</span>
            <span v-if="!openGroups[node.group] && hasBadge(node)" class="zw-menu-dot" />
            <zw-icon :name="openGroups[node.group] ? 'expand_less' : 'expand_more'" :size="18" class="zw-menu-caret" />
          </button>
          <a v-else href="javascript:;" class="zw-menu-item" :class="{ on: isActive(node) }" @click="go(node.key)">
            <zw-icon :name="node.icon || 'circle'" :size="18" :fill="isActive(node)" />
            <span class="zw-menu-label">{{ node.label }}</span>
            <span v-if="badgeOf(node)" class="zw-menu-badge num">{{ badgeOf(node) }}</span>
          </a>
          <div v-if="isGroup(node) && openGroups[node.group]" class="zw-menu-sub">
            <a v-for="child in node.children" :key="child.key" href="javascript:;" class="zw-menu-item zw-menu-subitem" :class="{ on: isActive(child) }" @click="go(child.key)">
              <span class="zw-menu-label">{{ child.label }}</span>
              <span v-if="badgeOf(child)" class="zw-menu-badge num">{{ badgeOf(child) }}</span>
            </a>
          </div>
        </template>
      </nav>
      <div class="zw-sider-foot" role="button" tabindex="0" @click="toggleSider" @keydown.enter="toggleSider">
        <zw-icon :name="shellState.collapsed ? 'menu' : 'menu_open'" :size="18" />
        <span v-if="!shellState.collapsed">收起菜单</span>
      </div>
    </v-navigation-drawer>

    <v-app-bar flat height="56" class="zw-header">
      <div class="zw-crumbs">
        <template v-for="(crumb, index) in crumbs" :key="index">
          <span v-if="index" class="zw-crumb-sep">/</span>
          <span :class="{ cur: index === crumbs.length - 1 }">{{ crumb }}</span>
        </template>
      </div>
      <div class="zw-header-r">
        <template v-if="side === 'user'">
          <v-menu v-model="farmMenu" location="bottom end" :close-on-content-click="false">
            <template #activator="{ props: activatorProps }">
              <button v-bind="activatorProps" class="zw-farm" type="button">
                <zw-icon name="domain" :size="18" />
                <span>{{ isAllFarms ? '全部养殖场' : farmName }}</span>
                <span v-if="!isAllFarms" class="zw-farm-role">{{ roleLabel }}</span>
                <zw-icon name="keyboard_arrow_down" :size="18" />
              </button>
            </template>
            <div class="zw-pop zw-farm-pop">
              <div class="zw-pop-title">切换养殖场</div>
              <div class="zw-farm-all" :class="{ dis: isAllFarms }" @click="shellActions.toggleAllFarms(!isAllFarms)">
                <zw-icon name="apps" :size="18" />
                <div>
                  <div>全部养殖场</div>
                  <div class="zw-farm-sub">只在养殖日历、消息中心、收支记账、经营报表中提供</div>
                </div>
              </div>
              <div class="zw-pop-div" />
              <div
                v-for="farm in shellState.farms"
                :key="farm.farm_id"
                class="zw-farm-item"
                :class="{ on: farm.farm_id === shellState.farmId && !isAllFarms, muted: farm.status !== 'NORMAL' }"
                @click="farm.can_switch !== false && shellActions.switchFarm(farm.farm_id)"
              >
                <zw-icon :name="farm.farm_id === shellState.farmId && !isAllFarms ? 'radio_button_checked' : 'radio_button_unchecked'" :size="18" class="zw-farm-radio" />
                <div class="zw-farm-txt">
                  <div class="zw-farm-name">{{ farm.name }}</div>
                  <div class="zw-farm-sub">{{ farm.role_text }} · {{ farm.main_category_name || farm.species_text || '' }}<template v-if="farm.current_stock !== undefined"> · 存栏 {{ farm.current_stock }} {{ farm.count_unit_text || '' }}</template></div>
                </div>
                <zw-tag v-if="farmBadge(farm)" :type="farm.status === 'PENDING' ? 'warning' : 'neutral'" size="small">{{ farmBadge(farm) }}</zw-tag>
                <zw-tag v-else-if="farm.farm_id === shellState.farmId && !isAllFarms" type="primary" size="small">当前</zw-tag>
              </div>
              <div class="zw-pop-div" />
              <div class="zw-pop-foot">
                <v-btn variant="text" color="primary" prepend-icon="add" size="small" @click="farmMenu = false; router.push({ name: 'WU-F02' })">新建养殖场</v-btn>
                <v-btn variant="text" size="small" @click="farmMenu = false; onFarm()">管理我的养殖场</v-btn>
              </div>
            </div>
          </v-menu>

          <label class="zw-search">
            <zw-icon name="search" :size="18" />
            <input v-model="searchText" placeholder="输入批次号查档案" aria-label="输入批次号查档案" @keydown.enter="onSearch" />
          </label>

          <v-menu v-model="quickMenu" location="bottom end">
            <template #activator="{ props: activatorProps }">
              <button v-bind="activatorProps" class="zw-register" type="button">
                <zw-icon name="add" :size="18" />
                登记
              </button>
            </template>
            <div class="zw-pop zw-quick">
              <div class="zw-pop-title">快捷登记<span class="zw-pop-sub">登记到{{ farmName }}，选一项直接进入登记页</span></div>
              <div class="zw-quick-label">作业</div>
              <div class="zw-quick-grid">
                <a v-for="item in quick.work" :key="item.key" class="zw-quick-item" @click="goQuick(item)">
                  <zw-icon :name="item.icon" :size="20" />
                  <span>{{ item.label }}</span>
                </a>
              </div>
              <template v-if="quick.farmer.length">
                <div class="zw-quick-label">养殖户</div>
                <div class="zw-quick-grid">
                  <a v-for="item in quick.farmer" :key="item.key" class="zw-quick-item" @click="goQuick(item)">
                    <zw-icon :name="item.icon" :size="20" />
                    <span>{{ item.label }}</span>
                  </a>
                </div>
              </template>
            </div>
          </v-menu>
        </template>

        <template v-else>
          <label class="zw-search zw-search--wide">
            <zw-icon name="search" :size="18" />
            <input v-model="searchText" placeholder="搜索养殖场、账号、批次号" aria-label="搜索养殖场、账号、批次号" @keydown.enter="onSearch" />
          </label>
        </template>

        <button class="zw-icon-btn zw-bell" type="button" aria-label="消息" @click="onMessages">
          <zw-icon name="notifications" :size="20" />
          <span v-if="unreadN" class="zw-bell-badge num">{{ unreadN > 99 ? '99+' : unreadN }}</span>
        </button>

        <v-menu v-model="userMenu" location="bottom end">
          <template #activator="{ props: activatorProps }">
            <button v-bind="activatorProps" class="zw-user" type="button">
              <zw-avatar :name="shellState.userName" :src="shellState.avatarUrl" :size="28" />
              <span>{{ shellState.userName }}</span>
              <zw-icon name="keyboard_arrow_down" :size="16" />
            </button>
          </template>
          <div class="zw-pop zw-user-pop">
            <div class="zw-user-head">
              <zw-avatar :name="shellState.userName" :src="shellState.avatarUrl" :size="36" />
              <div>
                <div class="title-s">{{ shellState.userName }}</div>
                <div class="zw-farm-sub">{{ side === 'admin' ? shellState.adminRoleName || '管理员' : roleLabel }}</div>
              </div>
            </div>
            <div class="zw-pop-div" />
            <a class="zw-pop-item" @click="onAccount"><zw-icon name="person" :size="18" />{{ side === 'admin' ? '管理员信息' : '个人中心' }}</a>
            <a v-if="side === 'user'" class="zw-pop-item" @click="userMenu = false; router.push({ name: 'WU-A04' })"><zw-icon name="lock" :size="18" />修改密码</a>
            <div class="zw-pop-div" />
            <a class="zw-pop-item" @click="onLogout"><zw-icon name="logout" :size="18" />退出登录</a>
          </div>
        </v-menu>
      </div>
    </v-app-bar>

    <v-main class="zw-main">
      <div class="zw-content" :class="{ 'zw-content--flush': !pad }">
        <slot />
      </div>
    </v-main>
  </div>
</template>

<style scoped>
.zw-shell {
  height: 100vh;
}
.zw-shell--collapsed :deep(.zw-logo) {
  justify-content: center;
  padding: 0;
}
.zw-shell--collapsed :deep(.zw-logo-name),
.zw-shell--collapsed :deep(.zw-logo-tag),
.zw-shell--collapsed :deep(.zw-menu-label),
.zw-shell--collapsed :deep(.zw-menu-badge),
.zw-shell--collapsed :deep(.zw-menu-caret),
.zw-shell--collapsed :deep(.zw-menu-dot),
.zw-shell--collapsed :deep(.zw-menu-sub) {
  display: none;
}
.zw-shell--collapsed :deep(.zw-menu-item) {
  justify-content: center;
  padding: 0;
  margin-left: 10px;
  margin-right: 10px;
}
.zw-shell--collapsed :deep(.zw-sider-foot) {
  justify-content: center;
  padding: 0;
}
</style>
