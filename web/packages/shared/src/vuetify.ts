import type { App } from 'vue'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'

/**
 * Vuetify 的 `vuetify` 入口只提供 createVuetify，不注册组件；
 * 正式工程统一在此注册全部组件与指令（原型使用完整版 vuetify.min.js）。
 */
export function installVuetify(app: App): void {
  for (const [name, component] of Object.entries(components)) {
    if (component && typeof component === 'object') {
      app.component(name, component as never)
    }
  }
  for (const [name, directive] of Object.entries(directives)) {
    if (directive) app.directive(name, directive as never)
  }
}
