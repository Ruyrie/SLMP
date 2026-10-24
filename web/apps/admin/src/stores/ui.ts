import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', {
  state: () => ({
    snackVisible: false,
    snackText: '',
    snackType: 'success' as 'success' | 'error' | 'info'
  }),
  actions: {
    toast(text: string, type: 'success' | 'error' | 'info' = 'success'): void {
      this.snackText = text
      this.snackType = type
      this.snackVisible = true
      window.setTimeout(() => {
        if (this.snackText === text) this.snackVisible = false
      }, 3000)
    },
    close(): void {
      this.snackVisible = false
    }
  }
})
