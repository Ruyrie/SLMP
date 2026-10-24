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
      window.setTimeout(() => {
        if (this.snackText === text) this.snackVisible = false
      }, 3000)
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
