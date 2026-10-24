/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE?: string
  readonly VITE_API_BASE?: string
  readonly VITE_CLIENT?: string
  readonly VITE_POLL_INTERVAL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
