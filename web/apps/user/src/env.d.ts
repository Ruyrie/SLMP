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
  readonly VITE_AMAP_KEY?: string
  /** 仅开发环境使用；生产环境由 Nginx /_AMapService 代理追加，不进前端包 */
  readonly VITE_AMAP_SECURITY_CODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'qrcode' {
  interface QRCodeToDataURLOptions {
    margin?: number
    width?: number
    errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H'
  }
  const QRCode: {
    toDataURL(text: string, options?: QRCodeToDataURLOptions): Promise<string>
  }
  export default QRCode
}
