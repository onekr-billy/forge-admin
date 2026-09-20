import { resolveCrudFormOnly } from '@/components/lowcode-builder/shared/runtime-crud-page-mode'

export function isRuntimeAutoHeightBlock(block = {}) {
  if (block?.blockType === 'AiForm')
    return true
  if (block?.blockType !== 'AiCrudPage')
    return false
  return resolveCrudFormOnly(block.props)
}

export function shouldUseContentSizedFlow(blocks = [], options = {}) {
  if (options.fillHost)
    return false
  const items = Array.isArray(blocks) ? blocks.filter(Boolean) : []
  return items.length > 0 && items.every(isRuntimeAutoHeightBlock)
}
