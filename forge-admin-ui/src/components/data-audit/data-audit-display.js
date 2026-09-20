export const DEFAULT_AUDIT_DIFF_LIMIT = 4

export function isMainAuditField(field = {}) {
  return !field.relationKey
    && field.fieldType !== 'CHILD_SUMMARY'
    && field.fieldCode !== '__childRows'
}

export function sortAuditFields(fields = []) {
  return [...fields].sort((left, right) => {
    const mainOrder = Number(isMainAuditField(right)) - Number(isMainAuditField(left))
    if (mainOrder)
      return mainOrder
    return Number(left.sortOrder ?? Number.MAX_SAFE_INTEGER) - Number(right.sortOrder ?? Number.MAX_SAFE_INTEGER)
  })
}

export function visibleAuditFields(fields = [], expanded = false, limit = DEFAULT_AUDIT_DIFF_LIMIT) {
  const sorted = sortAuditFields(fields)
  return expanded ? sorted : sorted.slice(0, limit)
}

export function groupAuditFields(fields = [], expanded = false, limit = DEFAULT_AUDIT_DIFF_LIMIT) {
  const sorted = sortAuditFields(fields)
  const displayed = expanded ? sorted : sorted.slice(0, limit)
  const mainFields = sorted.filter(isMainAuditField)
  const relatedFields = sorted.filter(field => !isMainAuditField(field))
  return [
    {
      key: 'main',
      label: '主表字段变化',
      total: mainFields.length,
      fields: displayed.filter(isMainAuditField),
    },
    {
      key: 'related',
      label: '子表变化',
      total: relatedFields.length,
      fields: displayed.filter(field => !isMainAuditField(field)),
    },
  ].filter(group => group.fields.length > 0)
}

export function auditFieldSummary(fields = [], fallbackCount = 0) {
  if (!fields.length)
    return `${fallbackCount || 0} 项变化`
  const mainCount = fields.filter(isMainAuditField).length
  const relatedCount = fields.length - mainCount
  return [
    mainCount ? `主表 ${mainCount} 项` : '',
    relatedCount ? `子表 ${relatedCount} 项` : '',
  ].filter(Boolean).join('，')
}

export function auditDiffHeadings(eventType) {
  if (eventType === 'CREATE')
    return { before: '新增前', after: '新增值' }
  if (eventType === 'DELETE')
    return { before: '删除前值', after: '删除后' }
  return { before: '修改前', after: '修改后' }
}
