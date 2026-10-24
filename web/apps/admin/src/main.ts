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
import { useAdminStore } from './stores/admin'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const admin = useAdminStore(pinia)

setupHttp({
  baseURL: appConfig.apiBase,
  client: appConfig.client,
  onAuthExpired: () => {
    if (router.currentRoute.value.name === 'WA-S01') return
    admin.reset()
    void router.push({ name: 'WA-S01', query: { reason: '登录已超时，请重新登录' } })
  }
})

app.use(router)
app.use(createZwVuetify())
installVuetify(app)
installZw(app)
app.component('zw-shell', ZwShell)
app.mount('#app')
