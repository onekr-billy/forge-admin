import type { DrillParamBinding } from '@/store/modules/chartEditStore/chartEditStore.d'

const isPlainObject = (value: unknown): value is Record<string, unknown> => {
  return Object.prototype.toString.call(value) === '[object Object]'
}

const getByPath = (source: unknown, path?: string): unknown => {
  if (!path) return undefined
  return path.split('.').reduce<unknown>((current, key) => {
    if (current === undefined || current === null) return undefined
    if (isPlainObject(current)) return current[key]
    if (Array.isArray(current) && /^\d+$/.test(key)) return current[Number(key)]
    return undefined
  }, source)
}

export function resolveDrillParams(
  bindings: DrillParamBinding[] = [],
  eventPayload: any = {},
  pageContext: Record<string, any> = {}
): Record<string, any> {
  const result: Record<string, any> = {}

  bindings.forEach(binding => {
    if (!binding.targetKey) return

    let value: unknown
    if (binding.source === 'static') {
      value = binding.value
    } else if (binding.source === 'pageContext') {
      value = getByPath(pageContext, binding.sourceKey)
    } else if (binding.source === 'componentField') {
      value = getByPath(eventPayload?.component, binding.sourceKey || 'option.dataset')
    } else {
      value = undefined
    }

    if (value === undefined || value === null || value === '') {
      value = binding.fallbackValue
    }
    if (value !== undefined && value !== null && value !== '') {
      result[binding.targetKey] = value
    }
  })

  return result
}
