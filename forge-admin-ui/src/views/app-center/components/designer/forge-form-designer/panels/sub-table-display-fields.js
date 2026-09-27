/**
 * 关联子表「显示字段」：识别对象必填字段，设计态必须勾选，否则运行时校验会失败。
 */

export function resolveFieldCode(field = {}) {
  return String(field?.fieldCode || field?.sourceField || field?.field || field?.value || '').trim()
}

export function resolveFieldLabel(field = {}) {
  return String(field?.fieldName || field?.rawLabel || field?.label || resolveFieldCode(field) || '').trim()
}

export function isBusinessFieldRequired(field = {}) {
  if (field?.required === true || field?.required === 1 || field?.required === '1')
    return true
  if (field?.validation?.required === true)
    return true
  return false
}

/**
 * 组装字段勾选列表：必填排前，并带 required 标记。
 */
export function buildSubTableDisplayFieldOptions(fields = []) {
  const options = (Array.isArray(fields) ? fields : [])
    .map((field) => {
      const value = resolveFieldCode(field)
      if (!value)
        return null
      return {
        label: resolveFieldLabel(field) || value,
        value,
        required: isBusinessFieldRequired(field),
      }
    })
    .filter(Boolean)

  return options.sort((a, b) => Number(Boolean(b.required)) - Number(Boolean(a.required)))
}

export function collectRequiredFieldCodes(options = []) {
  return (Array.isArray(options) ? options : [])
    .filter(item => item?.required && item?.value)
    .map(item => String(item.value))
}

/** 已选字段始终并入必填项（去重）；已有顺序保持不变，缺失的必填字段补到前面。 */
export function ensureRequiredDisplayFieldCodes(selectedCodes = [], options = []) {
  const required = collectRequiredFieldCodes(options)
  const selected = [...new Set((Array.isArray(selectedCodes) ? selectedCodes : [])
    .map(code => String(code || '').trim())
    .filter(Boolean))]
  const selectedSet = new Set(selected)
  const missingRequired = required.filter(code => !selectedSet.has(code))
  return [...missingRequired, ...selected]
}

export function toSubTableColumnDefs(codes = [], fields = []) {
  return (Array.isArray(codes) ? codes : []).map((code) => {
    const value = String(code || '').trim()
    const field = (Array.isArray(fields) ? fields : []).find(item => resolveFieldCode(item) === value)
    return {
      fieldCode: value,
      fieldLabel: field ? resolveFieldLabel(field) : value,
      required: field ? isBusinessFieldRequired(field) : false,
    }
  }).filter(col => col.fieldCode)
}
