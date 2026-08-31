export function isRuntimeAutoHeightBlock(block = {}) {
  if (block?.blockType === 'AiForm')
    return true
  if (block?.blockType !== 'AiCrudPage')
    return false
  const props = block.props || {}
  const objectRef = props.objectRef || {}
  return props.formOnly === true
    || objectRef.pageMode === 'form'
    || objectRef.pageKey === 'form'
}

export function shouldUseContentSizedFlow(blocks = [], options = {}) {
  if (options.fillHost)
    return false
  const items = Array.isArray(blocks) ? blocks.filter(Boolean) : []
  return items.length > 0 && items.every(isRuntimeAutoHeightBlock)
}
