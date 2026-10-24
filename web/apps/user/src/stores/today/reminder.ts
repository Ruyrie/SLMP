import { defineStore } from 'pinia'
import { ApiError } from '@zmt/shared'
import { fetchReminderSettings, patchReminderSettings } from '@/api/today'
import type { ReminderPatch, ReminderSettingVO } from '@/api/today'
import { useUiStore } from '@/stores/ui'

export const useReminderStore = defineStore('todayReminder', {
  state: () => ({
    setting: null as ReminderSettingVO | null,
    status: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    error: '',
    saving: '' as string
  }),
  actions: {
    async load(force = false): Promise<void> {
      if (this.setting && !force) return
      this.status = 'loading'
      this.error = ''
      try {
        this.setting = await fetchReminderSettings()
        this.status = 'ready'
      } catch (error) {
        this.status = 'error'
        this.error = error instanceof ApiError ? error.message : '设置加载失败，请稍后重试'
      }
    },
    /** 每次只提交变化的一项；失败恢复原值并提示。 */
    async save(patch: ReminderPatch, field: string): Promise<void> {
      if (!this.setting) return
      const before = { ...this.setting }
      this.saving = field
      this.setting = { ...this.setting, ...patch }
      try {
        this.setting = await patchReminderSettings(patch)
      } catch (error) {
        this.setting = before
        useUiStore().toast(
          error instanceof ApiError && error.message ? error.message : '设置没有保存，请检查网络后重试',
          'error'
        )
      } finally {
        this.saving = ''
      }
    }
  }
})
