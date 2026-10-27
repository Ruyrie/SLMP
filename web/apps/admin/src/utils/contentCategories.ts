import type { CategoryNode } from '@/api/base'

/** 内容适用范围引用 t_category；品种 ID 属于另一张表，不能提交到 applicable_category_ids。 */
export function applicableCategoryOptions(categories: CategoryNode[]): { title: string; value: number }[] {
  return categories.filter(category => category.status === 'ENABLED')
    .map(category => ({ title: category.name, value: category.id }))
}
