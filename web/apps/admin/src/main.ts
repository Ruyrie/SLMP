import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vuetify/styles'
import '@zmt/shared/theme/fonts/mso.css'
import '@zmt/shared/theme/tokens.css'
import '@zmt/shared/theme/zw.css'
import App from './App.vue'
import router from './router'
import { createZwVuetify, installZw } from '@zmt/shared'
import { adminConfig } from './config'
import { installVuetify } from '@zmt/shared/vuetify'
import ZwShell from '@zmt/shared/layouts/ZwShell.vue'
import { setupHttp } from './api/client'
import { useAdminStore } from './stores/admin'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

const admin = useAdminStore(pinia)

setupHttp({
  baseURL: adminConfig.apiBase,
  client: adminConfig.client,
  onAuthExpired: () => {
    // A first-visit /auth/me probe has no active session to expire.
    if (!admin.loaded) return
    admin.reset()
    if (router.currentRoute.value.name === 'WA-S01') return
    void router.push({ name: 'WA-S01', query: { reason: '登录已超时，请重新登录' } })
  }
})

app.use(router)
app.use(createZwVuetify())
installVuetify(app)
installZw(app)
app.component('zw-shell', ZwShell)
app.mount('#app')
