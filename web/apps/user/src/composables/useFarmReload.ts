import { watch } from 'vue'
import { useFarmStore } from '@/stores/farm'

/** 切换养殖场后重新加载页面数据：在页面 setup 中调用，传入加载函数。 */
export function useFarmReload(reload: () => void | Promise<void>): void {
  const farm = useFarmStore()
  watch(
    () => farm.revision,
    () => void reload()
  )
}
