export const standaloneObjectDesignerSections = [
  { key: 'basic', label: '基本信息' },
  { key: 'fields', label: '字段设计' },
  { key: 'data-model', label: '数据关系' },
  { key: 'tree-model', label: '树形模型' },
]

const DATA_MODEL_PANELS = new Set(['data-model', 'relations'])

export function resolveStandaloneObjectDesignerSection(value) {
  const panel = String(value || '').trim()
  if (panel === 'tree-model' || panel === 'permission')
    return 'tree-model'
  if (DATA_MODEL_PANELS.has(panel))
    return 'data-model'
  if (standaloneObjectDesignerSections.some(item => item.key === panel))
    return panel
  return 'fields'
}

/** @deprecated 树形模型已提升为一级入口；保留兼容旧 modelTab 深链 */
export function resolveDataModelTab(value) {
  const panel = String(value || '').trim()
  if (['tree-model', 'permission'].includes(panel))
    return 'tree-model'
  return panel === 'relations' ? panel : 'relations'
}

export function pickBusinessObjectIdentity({ queryObjectId, objectByCode } = {}) {
  const rawId = Array.isArray(queryObjectId) ? queryObjectId[0] : queryObjectId
  if (rawId != null && String(rawId).trim() !== '')
    return { id: String(rawId) }
  return objectByCode || null
}
