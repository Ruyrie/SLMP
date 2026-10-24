import { appConfig } from '@zmt/shared'

/** Keep the admin API/client correct even when no local .env file is present. */
export const adminConfig = {
  ...appConfig,
  apiBase: import.meta.env.VITE_API_BASE || '/admin/v1',
  client: import.meta.env.VITE_CLIENT || 'ADMIN_WEB'
}
