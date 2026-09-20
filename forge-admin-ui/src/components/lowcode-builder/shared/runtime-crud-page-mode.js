/** 页面形态属于区块，不属于共享的数据对象运行配置。 */
export function resolveCrudFormOnly(blockProps = {}, runtimeProps = {}) {
  blockProps ||= {}
  runtimeProps ||= {}
  if (typeof blockProps.formOnly === 'boolean') {
    return blockProps.formOnly
  }
  const objectRef = blockProps.objectRef || blockProps.businessObjectRef || blockProps.runtimeObjectRef || {}
  const pageMode = String(objectRef.pageMode || '').trim().toLowerCase()
  if (pageMode === 'form') {
    return true
  }
  if (['list', 'crud', 'list-form'].includes(pageMode)) {
    return false
  }
  if (objectRef.pageKey === 'form') {
    return true
  }
  return runtimeProps.formOnly === true
}

export function resolveCrudPagePresentation(blockProps = {}, runtimeProps = {}) {
  blockProps ||= {}
  runtimeProps ||= {}
  const presentation = { formOnly: resolveCrudFormOnly(blockProps, runtimeProps) }
  for (const key of ['formOnlyTitle', 'formOnlySubmitText', 'formOnlySuccessTitle', 'formOnlySuccessDescription']) {
    const value = blockProps[key] ?? runtimeProps[key]
    if (value !== undefined) {
      presentation[key] = value
    }
  }
  return presentation
}
