import { computed } from 'vue'
import { useFarmStore } from '@/stores/farm'

/** 角色判断：员工不渲染金额、成本、行情、报表相关菜单/列/字段。 */
export function useRole() {
  const farm = useFarmStore()
  return {
    role: computed(() => farm.role),
    isManager: computed(() => farm.role !== 'staff'),
    isStaff: computed(() => farm.role === 'staff'),
    isOwner: computed(() => farm.role === 'owner')
  }
}
