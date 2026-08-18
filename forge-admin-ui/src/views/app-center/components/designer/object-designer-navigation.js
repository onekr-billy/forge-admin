export const standaloneObjectDesignerSections = [
  { key: 'basic', label: '基本信息' },
  { key: 'fields', label: '字段设计' },
  { key: 'data-model', label: '数据关系' },
]

const DATA_MODEL_PANELS = new Set(['data-model', 'relations', 'tree-model', 'permission'])

export function resolveStandaloneObjectDesignerSection(value) {
  const panel = String(value || '').trim()
  if (DATA_MODEL_PANELS.has(panel))
    return 'data-model'
  if (standaloneObjectDesignerSections.some(item => item.key === panel))
    return panel
  return 'fields'
}

export function resolveDataModelTab(value) {
  const panel = String(value || '').trim()
  if (['tree-model', 'permission'].includes(panel))
    return 'tree-model'
  return panel === 'relations' ? panel : 'relations'
}
