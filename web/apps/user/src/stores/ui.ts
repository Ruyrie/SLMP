import { defineStore } from 'pinia'

export type SnackType = 'success' | 'error' | 'info'

export const useUiStore = defineStore('ui', {
  state: () => ({
    snackVisible: false,
    snackText: '',
    snackType: 'success' as SnackType,
    snackAction: '',
    snackHandler: null as null | (() => void),
    overlayCount: 0
  }),
  actions: {
    toast(text: string, type: SnackType = 'success', action = '', handler?: () => void): void {
      this.snackText = text
      this.snackType = type
      this.snackAction = action
      this.snackHandler = handler || null
      this.snackVisible = true
      // 带操作按钮（撤销、去登记）的提示多停留一会儿，方便点击
      window.setTimeout(() => {
        if (this.snackText === text) this.closeSnack()
      }, action ? 6000 : 3000)
    },
    closeSnack(): void {
      this.snackVisible = false
      this.snackAction = ''
      this.snackHandler = null
    },
    runSnackAction(): void {
      this.snackHandler?.()
      this.closeSnack()
    }
  }
})
