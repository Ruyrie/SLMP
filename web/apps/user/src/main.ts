import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vuetify/styles'
import '@zmt/shared/theme/fonts/mso.css'
import '@zmt/shared/theme/tokens.css'
import '@zmt/shared/theme/zw.css'
import App from './App.vue'
import router from './router'
import { createZwVuetify, installZw, appConfig } from '@zmt/shared'
import { installVuetify } from '@zmt/shared/vuetify'
import ZwShell from '@zmt/shared/layouts/ZwShell.vue'
import { setupHttp } from './api/client'
import { useAuthStore } from './stores/auth'
import { useFarmStore } from './stores/farm'
import { useNoticeStore } from './stores/notice'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const auth = useAuthStore(pinia)
const farm = useFarmStore(pinia)
const notice = useNoticeStore(pinia)

setupHttp({
  baseURL: appConfig.apiBase,
  client: appConfig.client,
  getToken: () => auth.accessToken,
  getFarmId: () => farm.currentFarmId,
  isAllFarms: () => farm.viewAll,
  refresh: () => auth.refresh(),
  onAuthExpired: (reason) => {
    if (router.currentRoute.value.name === 'WU-A01') return
    auth.clear()
    farm.reset()
    notice.stop()
    void router.push({ name: 'WU-A01', query: reason ? { reason } : undefined })
  }
})

app.use(router)
app.use(createZwVuetify())
installVuetify(app)
installZw(app)
app.component('zw-shell', ZwShell)
app.mount('#app')
