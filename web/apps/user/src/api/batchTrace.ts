import { http } from './client'

export interface BatchTrace {
  batch_no: string
  farm_name: string
  category_name: string
  breed_name: string
  source: string
  status: string
  entry_date: string
  end_date?: string
  entry_age_days?: number
  entry_avg_weight?: number
  updated_at?: string
  events: Array<{ type: string; date: string; name: string; quantity?: number; avg_weight?: number; withdrawal_end_date?: string }>
}

export const batchTraceApi = {
  read: (token: string) => http().get<BatchTrace>(`/public/batch-traces/${encodeURIComponent(token)}`),
  settings: (id: number, enabled: boolean, rotate: boolean, version: number) =>
    http().put(`/batches/${id}/trace-settings`, { enabled, rotate, version })
}
