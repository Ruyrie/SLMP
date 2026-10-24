import type { PageVO, Schema, Schemas } from '@zmt/shared'
import { http } from '@/api/client'

/**
 * 模块05 繁育管理接口。
 * 服务端响应是 snake_case；页面层沿用 camelCase 字段。这里在 API 边界统一转换，
 * 类型由生成类型（snake_case）通过 Camelize 映射得到，保证字段名一致。
 */
type CamelCase<S extends string> = S extends `${infer Head}_${infer Tail}`
  ? `${Head}${Capitalize<CamelCase<Tail>>}`
  : S

export type Camelize<T> = T extends (infer U)[]
  ? Camelize<U>[]
  : T extends object
    ? { [K in keyof T as CamelCase<K & string>]: Camelize<T[K]> }
    : T

type S<K extends keyof Schemas> = Schema<K>
export interface BreedingCounts {
  all?: number
  empty?: number
  mated?: number
  pregnant?: number
  lactating?: number
  exited?: number
}

export type BreedingOverview = Omit<Camelize<S<'BreedingOverviewVO'>>, 'counts'> & {
  counts?: BreedingCounts
}
export type DueSoonItem = NonNullable<BreedingOverview['dueSoon']>[number]
export type SwitchFarm = NonNullable<BreedingOverview['switchFarms']>[number]
export type BreedingAnimalItem = Camelize<S<'BreedingAnimalItemVO'>>
export type BreedingAnimalOption = Camelize<S<'BreedingAnimalOptionVO'>>
export type BreedingAnimalDetail = Camelize<S<'BreedingAnimalDetailVO'>>
export type BreedingStats = NonNullable<BreedingAnimalDetail['stats']>
export type BreedingCycle = NonNullable<BreedingAnimalDetail['currentCycle']>
export type BreedingRecord = Camelize<S<'BreedingRecordVO'>>
export type RegisterContext = Camelize<S<'RegisterContextVO'>>
export type RegisterResult = Camelize<S<'RegisterResultVO'>>
export type PendingCheck = Camelize<S<'PendingCheckVO'>>
export type FarrowDueItem = Camelize<S<'FarrowDueItemVO'>>
export type HatchDueItem = Camelize<S<'HatchDueItemVO'>>
export type FarrowingDetail = Camelize<S<'FarrowingDetailVO'>>
export type IncubationDetail = Camelize<S<'IncubationDetailVO'>>
export type IncubationTimeline = NonNullable<IncubationDetail['timeline']>[number]
export type AnimalRecordLog = Camelize<S<'RecordLogVO'>>

export type AnimalFilter = 'ALL' | 'EMPTY' | 'MATED' | 'PREGNANT' | 'LACTATING' | 'EXITED'
export type RegisterPurpose = 'MATING' | 'FARROWING' | 'WEANING'
export type RegisterType = RegisterPurpose | 'PREG_CHECK'
export type MatingMethod = 'ARTIFICIAL' | 'NATURAL'
export type PregnancyResult = 'POSITIVE' | 'NEGATIVE'
export type ExitType = 'CULL' | 'DEATH'
export type AnimalSource = 'PURCHASED' | 'SELF_BRED'

/** 入孵时可选的种用禽类批次（GET /batches?purpose=BREEDING&species=POULTRY）。 */
export interface PoultryBatchOption {
  batch_id?: number
  batch_no?: string
  breed_id?: number
  breed_name?: string
  current_count?: number
  unit_text?: string
  pens?: Array<{ pen_id?: number; pen_name?: string; stock_count?: number }>
}

export interface AnimalListParams {
  page?: number
  size?: number
  status?: AnimalFilter | string
  keyword?: string
  penId?: number
}

export interface OptionListParams {
  purpose?: RegisterPurpose | string
  keyword?: string
  page?: number
  size?: number
}

export interface PageParams {
  page?: number
  size?: number
  keyword?: string
}

export interface AnimalSavePayload {
  ear_tag: string
  breed_id: number
  birth_date?: string
  source: AnimalSource
  pen_id: number
  initial_parity: number
  version?: number
}

export interface AnimalExitPayload {
  exit_type: ExitType
  exit_date: string
  reason: string
  version: number
}

export interface MatingPayload {
  animal_id: number
  animal_version: number
  mating_date: string
  mating_method: MatingMethod
  boar_info?: string
}

export interface PregnancyCheckPayload {
  animal_id: number
  animal_version: number
  cycle_id: number
  check_date: string
  result: PregnancyResult
  check_method?: string
}

export interface FarrowingPayload {
  animal_id: number
  animal_version: number
  farrow_date: string
  total_born: number
  live_born: number
  stillborn: number
  transfer_to_batch: boolean
  target_pen_id?: number
  offspring_breed_id?: number
  capacity_confirmed?: boolean
}

export interface WeaningPayload {
  animal_id: number
  animal_version: number
  wean_date: string
  weaned_count: number
  litter_weight?: number | null
}

export interface IncubationCreatePayload {
  breeding_batch_id: number
  set_date: string
  set_count: number
}

export interface CandlingPayload {
  candle_date: string
  fertile_count: number
  version: number
}

export interface IncubationUpdatePayload {
  set_date?: string
  set_count?: number
  candle_date?: string
  fertile_count?: number
  version: number
}

export interface HatchPayload {
  hatch_date: string
  hatched_count: number
  healthy_count: number
  transfer_to_batch: boolean
  target_pen_id?: number
  offspring_breed_id?: number
  capacity_confirmed?: boolean
  version: number
}

export interface CamelPage<T> {
  list: T[]
  total: number
  page: number
  size: number
}

function camelKey(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, char: string) => char.toUpperCase())
}

function camelize<T>(value: unknown): T {
  if (Array.isArray(value)) return value.map((item) => camelize(item)) as T
  if (value && typeof value === 'object') {
    const output: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) {
      output[camelKey(key)] = camelize(item)
    }
    return output as T
  }
  return value as T
}

async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return camelize<T>(await http().get<unknown>(url, params))
}

async function post<T>(url: string, payload?: unknown): Promise<T> {
  return camelize<T>(await http().post<unknown>(url, payload))
}

async function put<T>(url: string, payload?: unknown): Promise<T> {
  return camelize<T>(await http().put<unknown>(url, payload))
}

export const breedingApi = {
  overview(): Promise<BreedingOverview> {
    return get<BreedingOverview>('/breeding/overview')
  },

  animals(params: AnimalListParams = {}): Promise<CamelPage<BreedingAnimalItem>> {
    return get<CamelPage<BreedingAnimalItem>>('/breeding-animals', { ...params })
  },

  animalOptions(params: OptionListParams = {}): Promise<CamelPage<BreedingAnimalOption>> {
    return get<CamelPage<BreedingAnimalOption>>('/breeding-animals/options', { ...params })
  },

  animalDetail(animalId: number): Promise<BreedingAnimalDetail> {
    return get<BreedingAnimalDetail>(`/breeding-animals/${animalId}`)
  },

  animalRecords(animalId: number, page = 1, size = 20): Promise<CamelPage<BreedingRecord>> {
    return get<CamelPage<BreedingRecord>>(`/breeding-animals/${animalId}/records`, { page, size })
  },

  registerContext(animalId: number, type: RegisterType): Promise<RegisterContext> {
    return get<RegisterContext>(`/breeding-animals/${animalId}/register-context`, { type })
  },

  createAnimal(payload: AnimalSavePayload): Promise<BreedingAnimalDetail> {
    return post<BreedingAnimalDetail>('/breeding-animals', payload)
  },

  updateAnimal(animalId: number, payload: AnimalSavePayload): Promise<BreedingAnimalDetail> {
    return put<BreedingAnimalDetail>(`/breeding-animals/${animalId}`, payload)
  },

  exitAnimal(animalId: number, payload: AnimalExitPayload): Promise<BreedingAnimalDetail> {
    return post<BreedingAnimalDetail>(`/breeding-animals/${animalId}/exit`, payload)
  },

  pendingChecks(params: PageParams = {}): Promise<CamelPage<PendingCheck>> {
    return get<CamelPage<PendingCheck>>('/breeding/pending-checks', { ...params })
  },

  farrowDue(params: PageParams = {}): Promise<CamelPage<FarrowDueItem>> {
    return get<CamelPage<FarrowDueItem>>('/breeding/farrow-due', { ...params })
  },

  hatchDue(params: PageParams = {}): Promise<CamelPage<HatchDueItem>> {
    return get<CamelPage<HatchDueItem>>('/breeding/hatch-due', { ...params })
  },

  createMating(payload: MatingPayload): Promise<RegisterResult> {
    return post<RegisterResult>('/matings', payload)
  },

  createPregnancyCheck(payload: PregnancyCheckPayload): Promise<RegisterResult> {
    return post<RegisterResult>('/pregnancy-checks', payload)
  },

  createFarrowing(payload: FarrowingPayload): Promise<RegisterResult> {
    return post<RegisterResult>('/farrowings', payload)
  },

  createWeaning(payload: WeaningPayload): Promise<RegisterResult> {
    return post<RegisterResult>('/weanings', payload)
  },

  incubationDetail(incubationId: number): Promise<IncubationDetail> {
    return get<IncubationDetail>(`/incubations/${incubationId}`)
  },

  createIncubation(payload: IncubationCreatePayload): Promise<RegisterResult> {
    return post<RegisterResult>('/incubations', payload)
  },

  updateIncubation(incubationId: number, payload: IncubationUpdatePayload): Promise<IncubationDetail> {
    return put<IncubationDetail>(`/incubations/${incubationId}`, payload)
  },

  candling(incubationId: number, payload: CandlingPayload): Promise<IncubationDetail> {
    return post<IncubationDetail>(`/incubations/${incubationId}/candling`, payload)
  },

  hatch(incubationId: number, payload: HatchPayload): Promise<RegisterResult> {
    return post<RegisterResult>(`/incubations/${incubationId}/hatch`, payload)
  },

  poultryBreedingBatches(): Promise<PageVO<PoultryBatchOption>> {
    return http().get<PageVO<PoultryBatchOption>>('/batches', {
      page: 1,
      size: 100,
      status: 'ACTIVE',
      purpose: 'BREEDING',
      species: 'POULTRY'
    })
  }
}
