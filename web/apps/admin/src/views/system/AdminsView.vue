<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { routeQueryId, routeQueryText } from '@/utils/routeQuery'
import { formatDateTime } from '@zmt/shared'
import { adminStatus } from '@/views/system/shared'
import { useAdminsStore } from '@/stores/system/admins'
import { useUiStore } from '@/stores/ui'
import type { AdminRow, PermissionCell, PermissionRow } from '@/api/system'

const store = useAdminsStore()
const ui = useUiStore()
const route = useRoute()

const headers = [
  { title: '管理员', key: 'name', width: 160, sortable: false },
  { title: '登录账号', key: 'username', width: 130, sortable: false },
  { title: '角色', key: 'role_name', width: 130, sortable: false },
  { title: '手机号', key: 'phone', width: 130, sortable: false },
  { title: '创建时间', key: 'created_at', width: 120, sortable: false },
  { title: '最近登录', key: 'last_login_at', width: 140, sortable: false },
  { title: '状态', key: 'status', width: 90, sortable: false },
  { title: '操作', key: 'actions', width: 210, sortable: false }
]

const groups = computed(() => {
  const rows = store.permission?.rows || []
  const map = new Map<string, { code: string; name: string; rows: PermissionRow[] }>()
  for (const row of rows) {
    if (!map.has(row.module_code)) map.set(row.module_code, { code: row.module_code, name: row.module_name, rows: [] })
    map.get(row.module_code)!.rows.push(row)
  }
  return [...map.values()]
})

function roleTone(code?: string): string {
  if (code === 'SUPER_ADMIN') return 'primary'
  if (code === 'AUDITOR') return 'warning'
  return 'ai'
}

function roleIcon(code?: string): string {
  if (code === 'SUPER_ADMIN') return 'verified_user'
  if (code === 'AUDITOR') return 'fact_check'
  return 'campaign'
}

function isChecked(cell: PermissionCell): boolean {
  return store.draft.includes(cell.perm_code)
}

function toggle(cell: PermissionCell, value: unknown): void {
  store.togglePermission(cell, !!value)
}

async function copyTemp(): Promise<void> {
  try {
    await navigator.clipboard.writeText(store.tempPassword)
    ui.toast('临时密码已复制')
  } catch {
    ui.toast('复制失败，请手动选择复制', 'error')
  }
}

function changePage(page: number): void {
  store.query.page = page
  void store.fetchAdmins()
}

function changeSize(size: number): void {
  store.query.size = size
  store.query.page = 1
  void store.fetchAdmins()
}

onMounted(async () => {
  await Promise.all([store.fetchAdmins(), store.fetchRoles()])
  // 从操作日志带参数进入：tab=roles&role_id= 打开角色权限；id= 打开该管理员的编辑抽屉
  const roleId = routeQueryId(route, 'role_id')
  if (routeQueryText(route, 'tab') === 'roles' || roleId) {
    store.tab = 'roles'
    if (roleId && store.roles.some((role) => role.role_id === roleId)) await store.selectRole(roleId)
    return
  }
  const id = routeQueryId(route)
  const row = id ? store.list.find((item) => item.admin_id === id) : undefined
  if (row) store.openEdit(row)
  else if (id) ui.toast('该管理员不在当前列表中，请翻页查找', 'info')
})
</script>

<template>
  <zw-shell side="admin" active="WA-S05" :crumbs="['用户与系统', '管理员与权限']">
    <zw-page-head title="管理员与权限" desc="管理端账号由超级管理员创建，按角色分配权限。新增、停用管理员和修改权限都要填写原因，记入操作日志。">
      <template #actions>
        <v-btn v-if="store.tab === 'admins'" color="primary" prepend-icon="person_add" @click="store.openCreate()">新增管理员</v-btn>
      </template>
    </zw-page-head>

    <v-tabs v-model="store.tab" class="zw-tabs-line" style="margin-bottom: 16px">
      <v-tab value="admins">管理员账号 {{ store.total }}</v-tab>
      <v-tab value="roles">角色与权限 {{ store.roles.length }}</v-tab>
    </v-tabs>

    <template v-if="store.tab === 'admins'">
      <zw-card flush>
        <v-skeleton-loader v-if="store.status === 'loading'" type="table-row@6" />
        <zw-empty v-else-if="store.status === 'error'" icon="cloud_off" title="加载失败" :desc="store.error" tone="error">
          <v-btn variant="outlined" @click="store.fetchAdmins()">重新加载</v-btn>
        </zw-empty>
        <zw-empty v-else-if="store.status === 'empty'" icon="manage_accounts" title="还没有管理员" desc="新增管理员后，把页面上显示的临时密码线下告知对方，对方凭临时密码登录管理端。" />
        <template v-else>
          <v-data-table :headers="headers" :items="store.list" item-key="admin_id" hide-default-footer density="compact">
            <template #item.name="{ item }">
              <div class="zw-row">
                <zw-avatar :name="item.name" :size="26" />
                <span>{{ item.name }}</span>
                <zw-tag v-if="item.is_self" type="primary" size="small">你</zw-tag>
              </div>
            </template>
            <template #item.username="{ item }">
              <span class="num">{{ item.username }}</span>
            </template>
            <template #item.role_name="{ item }">
              <zw-tag :type="roleTone(item.role_code)" size="small">{{ item.role_name }}</zw-tag>
            </template>
            <template #item.phone="{ item }">
              <span class="num">{{ item.phone || '—' }}</span>
            </template>
            <template #item.created_at="{ item }">
              <span class="num">{{ item.created_at ? formatDateTime(item.created_at) : '—' }}</span>
            </template>
            <template #item.last_login_at="{ item }">
              <span class="num">{{ item.last_login_at ? formatDateTime(item.last_login_at) : '—' }}</span>
            </template>
            <template #item.status="{ item }">
              <zw-tag :type="adminStatus(item).tone" size="small">{{ adminStatus(item).text }}</zw-tag>
            </template>
            <template #item.actions="{ item }">
              <div class="zw-cell-actions">
                <a class="zw-link" @click="store.openEdit(item as AdminRow)">编辑</a>
                <a class="zw-link" @click="store.openReset(item as AdminRow)">重置密码</a>
                <a v-if="item.status === 'NORMAL' && !item.is_self" class="zw-link danger" @click="store.openStatus(item as AdminRow, 'disable')">停用</a>
                <a v-if="item.status !== 'NORMAL'" class="zw-link" @click="store.openStatus(item as AdminRow, 'enable')">启用</a>
              </div>
            </template>
          </v-data-table>
          <div class="zw-table-foot">
            <span class="zw-muted">不能停用自己的账号；平台至少保留 1 个正常的超级管理员</span>
            <zw-pager :total="store.total" :page="store.query.page" :size="store.query.size" @update:page="changePage" @update:size="changeSize" />
          </div>
        </template>
      </zw-card>

      <zw-notice class="admin-tip" type="info" icon="info">
        管理员账号只能登录管理端，不能登录 App 和用户端网页。新增或重置密码后，临时密码只在操作完成时显示一次，请线下告知对方；临时密码 24 小时内有效，首次登录必须修改。
      </zw-notice>
    </template>

    <template v-else>
      <div class="role-layout">
        <zw-card class="role-list">
          <div class="role-list-title">角色</div>
          <button
            v-for="role in store.roles"
            :key="role.role_id"
            type="button"
            class="role-item"
            :class="{ on: role.role_id === store.selectedRoleId }"
            @click="store.selectRole(role.role_id)"
          >
            <span class="role-ico" :class="`role-ico--${roleTone(role.role_code)}`"><zw-icon :name="roleIcon(role.role_code)" :size="18" /></span>
            <span class="role-text">
              <span class="role-name">{{ role.role_name }} <span class="zw-muted">{{ role.member_count || 0 }} 人</span></span>
              <span class="role-desc">{{ role.description || '' }}</span>
            </span>
          </button>
        </zw-card>

        <zw-card class="role-perm" :title="store.permission ? `${store.permission.role.role_name}的权限` : '角色权限'">
          <template #extra>
            <div class="perm-head">
              <span class="zw-muted">{{ store.permission?.members.length || 0 }} 人{{ store.membersText ? ` · ${store.membersText}` : '' }}</span>
              <v-btn variant="outlined" size="small" :disabled="!store.permission?.editable" @click="store.resetPermissions()">恢复默认</v-btn>
              <v-btn color="primary" size="small" :loading="store.savingPermissions" :disabled="!store.permission?.editable" @click="store.savePermissions()">保存修改</v-btn>
            </div>
          </template>

          <v-skeleton-loader v-if="store.permissionLoading" type="paragraph@6" />
          <template v-else-if="store.permission">
            <table class="perm-table">
              <thead>
                <tr><th>功能块</th><th>页面</th><th class="center">查看</th><th class="center">处理</th><th>处理指</th></tr>
              </thead>
              <tbody v-for="group in groups" :key="group.code">
                <tr v-for="(row, index) in group.rows" :key="row.page_code">
                  <td v-if="index === 0" :rowspan="group.rows.length" class="perm-module">{{ group.name }}</td>
                  <td>{{ row.page_name }}</td>
                  <td class="center">
                    <v-checkbox-btn
                      :model-value="isChecked(row.view)"
                      :disabled="row.view.locked || !store.permission.editable"
                      density="compact"
                      color="primary"
                      @update:model-value="toggle(row.view, $event)"
                    />
                  </td>
                  <td class="center">
                    <v-checkbox-btn
                      v-if="row.handle"
                      :model-value="isChecked(row.handle)"
                      :disabled="row.handle.locked || !store.permission.editable"
                      density="compact"
                      color="primary"
                      @update:model-value="toggle(row.handle, $event)"
                    />
                    <span v-else class="zw-faint">—</span>
                  </td>
                  <td class="zw-muted">{{ row.handle_desc || '—' }}</td>
                </tr>
              </tbody>
            </table>
            <div class="perm-foot">
              <v-text-field v-model="store.permissionReason" class="perm-reason" placeholder="填写修改原因（必填）" maxlength="100" density="compact" hide-details />
              <span class="zw-muted">保存时对该角色的全部管理员立即生效；超级管理员权限不能修改。</span>
            </div>
          </template>
        </zw-card>
      </div>
    </template>

    <zw-drawer v-model="store.drawer" :title="store.isCreate ? '新增管理员' : '编辑管理员'" width="560">
      <div class="zw-form">
        <zw-form-item label="姓名" required :error="store.formErrors.name">
          <v-text-field v-model="store.form.name" placeholder="请输入姓名" maxlength="20" :error="!!store.formErrors.name" />
        </zw-form-item>
        <zw-form-item label="登录账号" required :error="store.formErrors.username" :hint="store.isCreate ? '4–20 位字母和数字，创建后不可修改' : '账号不可修改'">
          <v-text-field
            v-model="store.form.username"
            placeholder="请输入登录账号"
            maxlength="20"
            :disabled="!store.isCreate"
            :loading="store.usernameChecking"
            :error="!!store.formErrors.username"
            @blur="store.checkUsername()"
            @update:model-value="store.formErrors.username = ''"
          />
        </zw-form-item>
        <zw-form-item
          label="手机号"
          :required="store.isCreate"
          :optional="!store.isCreate"
          :error="store.formErrors.phone"
          :hint="store.isCreate ? '用于联系管理员本人；临时密码不会以短信发送' : `当前 ${store.editing?.phone || '—'}；不改手机号请留空`"
        >
          <v-text-field
            v-model="store.form.phone"
            :placeholder="store.isCreate ? '请输入 11 位手机号' : '不改请留空，改号请输入新的 11 位手机号'"
            maxlength="11"
            :error="!!store.formErrors.phone"
          />
        </zw-form-item>
        <zw-form-item label="角色" required :error="store.formErrors.role_id">
          <v-select v-model="store.form.role_id" :items="store.roleOptions" placeholder="请选择角色" :error="!!store.formErrors.role_id" />
        </zw-form-item>
        <zw-form-item label="原因" required :error="store.formErrors.reason" hint="记入操作日志，管理员本人不可见">
          <v-textarea v-model="store.form.reason" placeholder="请说明新增 / 修改的原因" :rows="3" maxlength="200" counter :error="!!store.formErrors.reason" />
        </zw-form-item>
      </div>
      <template #foot>
        <v-btn variant="outlined" @click="store.drawer = false">取消</v-btn>
        <v-btn color="primary" :loading="store.submitting" @click="store.submit()">保存</v-btn>
      </template>
    </zw-drawer>

    <zw-dialog v-model="store.resetOpen" title="重置密码" width="440" icon="lock" tone="warning">
      <p class="dialog-tip">重置后该管理员的全部会话失效并解除锁定。新的临时密码只在本页面显示一次，请线下告知 {{ store.resetTarget?.name || '对方' }}，首次登录必须修改。</p>
      <zw-form-item label="修改原因" required>
        <v-textarea v-model="store.resetReason" placeholder="请填写重置密码的原因" :rows="3" maxlength="100" counter />
      </zw-form-item>
      <template #actions>
        <v-btn variant="outlined" @click="store.resetOpen = false">取消</v-btn>
        <v-btn color="primary" :loading="store.resetting" @click="store.confirmReset()">确认重置</v-btn>
      </template>
    </zw-dialog>

    <zw-dialog v-model="store.statusOpen" :title="store.statusAction === 'disable' ? '停用管理员' : '启用管理员'" width="440" :icon="store.statusAction === 'disable' ? 'block' : 'check_circle'" :tone="store.statusAction === 'disable' ? 'error' : 'primary'">
      <p class="dialog-tip">{{ store.statusAction === 'disable' ? `停用后 ${store.statusTarget?.name || '该管理员'} 立即无法登录管理端。` : `启用后 ${store.statusTarget?.name || '该管理员'} 可以重新登录管理端。` }}</p>
      <zw-form-item label="原因" required>
        <v-textarea v-model="store.statusReason" placeholder="请填写原因，记入操作日志" :rows="3" maxlength="100" counter />
      </zw-form-item>
      <template #actions>
        <v-btn variant="outlined" @click="store.statusOpen = false">取消</v-btn>
        <v-btn :color="store.statusAction === 'disable' ? 'error' : 'primary'" :loading="store.statusSaving" @click="store.confirmStatus()">
          {{ store.statusAction === 'disable' ? '停用' : '启用' }}
        </v-btn>
      </template>
    </zw-dialog>

    <zw-dialog :model-value="store.tempOpen" :title="store.tempTitle || '临时密码'" width="440" icon="key" tone="primary" persistent @update:model-value="!$event && store.closeTempPassword()">
      <p class="dialog-tip">{{ store.tempTarget }}的临时密码如下。请线下告知对方（当面或电话），<b>关闭后无法再查看</b>；系统不会发送短信。</p>
      <div class="temp-pwd"><span class="num">{{ store.tempPassword || '—' }}</span><v-btn variant="outlined" size="small" @click="copyTemp">复制</v-btn></div>
      <p class="dialog-tip temp-expire">{{ store.tempExpireAt ? `${formatDateTime(store.tempExpireAt)} 前有效` : '24 小时内有效' }}，首次登录必须修改密码。</p>
      <template #actions>
        <v-btn color="primary" @click="store.closeTempPassword()">我已告知对方，关闭</v-btn>
      </template>
    </zw-dialog>
  </zw-shell>
</template>

<style scoped>
.admin-tip {
  margin-top: 16px;
}
.role-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.role-list {
  padding: 8px;
}
.role-list-title {
  padding: 8px 12px;
  font-weight: 600;
}
.role-item {
  width: 100%;
  display: flex;
  gap: 10px;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  font: inherit;
}
.role-item:hover {
  background: var(--fill-1);
}
.role-item.on {
  border-color: var(--primary);
  background: var(--primary-subtle);
}
.role-ico {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.role-ico--primary { background: var(--primary-container); color: var(--primary); }
.role-ico--warning { background: var(--warning-container); color: var(--warning); }
.role-ico--ai { background: var(--tertiary-container); color: var(--tertiary); }
.role-text { flex: 1; min-width: 0; }
.role-name { display: block; font-weight: 500; }
.role-desc { display: block; margin-top: 2px; font-size: 12px; line-height: 18px; color: var(--text-3); }
.perm-head { display: flex; align-items: center; gap: 8px; }
.perm-table { width: 100%; border-collapse: collapse; }
.perm-table th { height: 36px; padding: 0 10px; text-align: left; font-weight: 400; font-size: 12px; color: var(--text-2); background: var(--fill-1); border-bottom: 1px solid var(--line-1); }
.perm-table td { height: 40px; padding: 4px 10px; border-bottom: 1px solid var(--line-1); vertical-align: middle; }
.perm-table .center { text-align: center; }
.perm-module { width: 110px; font-weight: 500; background: var(--bg-card); }
.perm-foot { display: flex; align-items: center; gap: 12px; padding-top: 12px; }
.perm-reason { max-width: 320px; }
.dialog-tip { margin: 0 0 14px; color: var(--text-2); font-size: 13px; line-height: 20px; }
.temp-expire { margin: 12px 0 0; }
.temp-pwd { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; border-radius: var(--radius-sm); background: var(--fill-1); font-size: 20px; font-weight: 600; letter-spacing: 2px; }
</style>
