/**
 * 下拉选项来源：子表明细 / 业务对象 候选项组装。
 * 纯函数模块，供属性面板与单测复用。
 */

const CHILD_RELATION_TYPES = new Set(['DETAIL', 'CHILD_LIST', 'ONE_TO_MANY'])

export function parseRelationConfig(value) {
  if (!value)
    return {}
  if (typeof value === 'object' && !Array.isArray(value))
    return value
  try {
    const parsed = JSON.parse(String(value))
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  }
  catch {
    return {}
  }
}

export function resolveChildRelationKey(relation = {}) {
  const parsed = parseRelationConfig(relation.relationConfig)
  return String(
    relation.relationKey
    || parsed.relationKey
    || parsed.collectionKey
    || relation.targetObjectCode
    || '',
  ).trim()
}

export function resolveChildRelationTargetCode(relation = {}) {
  const parsed = parseRelationConfig(relation.relationConfig)
  return String(parsed.targetObjectCode || relation.targetObjectCode || '').trim()
}

export function isChildLikeRelation(relation = {}) {
  const type = String(relation.relationType || '').toUpperCase()
  return CHILD_RELATION_TYPES.has(type)
}

function walkSubTableComponents(components = [], result = []) {
  ;(Array.isArray(components) ? components : []).forEach((component) => {
    if (!component || typeof component !== 'object')
      return
    if (component.componentKey === 'subTable')
      result.push(component)
    if (Array.isArray(component.children))
      walkSubTableComponents(component.children, result)
  })
  return result
}

/**
 * 汇总可选子表关系：当前画布 + 其它表单资产中的子表 + 对象级明细/子列表关系。
 * 同源 relationKey 去重，优先保留画布已配置项（有 columns，便于字段下拉）。
 */
export function collectChildTableRelationOptions({
  subTableComponents = [],
  formAssets = [],
  relations = [],
} = {}) {
  const options = []
  const seen = new Set()

  const push = (value, label, meta = {}) => {
    const key = String(value || '').trim()
    if (!key || seen.has(key))
      return
    seen.add(key)
    options.push({
      label: label || key,
      value: key,
      ...meta,
    })
  }

  ;(Array.isArray(subTableComponents) ? subTableComponents : []).forEach((comp) => {
    const relationKey = String(comp?.props?.relationKey || '').trim()
    if (!relationKey)
      return
    const header = comp.props?.header || comp.label || relationKey
    push(relationKey, `${header}（${relationKey}）`, {
      source: 'canvas',
      targetObjectCode: String(comp.props?.targetObjectCode || '').trim(),
      columns: Array.isArray(comp.props?.columns) ? comp.props.columns : [],
    })
  })

  ;(Array.isArray(formAssets) ? formAssets : []).forEach((asset) => {
    const formName = asset?.formName || asset?.formKey || '其它表单'
    walkSubTableComponents(asset?.schema?.components).forEach((comp) => {
      const relationKey = String(comp?.props?.relationKey || '').trim()
      if (!relationKey)
        return
      const header = comp.props?.header || comp.label || relationKey
      push(relationKey, `${formName} · ${header}（${relationKey}）`, {
        source: 'formAsset',
        formKey: asset.formKey,
        targetObjectCode: String(comp.props?.targetObjectCode || '').trim(),
        columns: Array.isArray(comp.props?.columns) ? comp.props.columns : [],
      })
    })
  })

  ;(Array.isArray(relations) ? relations : []).forEach((relation) => {
    if (!isChildLikeRelation(relation) || relation.status === 0)
      return
    const relationKey = resolveChildRelationKey(relation)
    if (!relationKey)
      return
    const name = relation.relationName
      || relation.targetObjectName
      || resolveChildRelationTargetCode(relation)
      || relationKey
    push(relationKey, `对象关系 · ${name}（${relationKey}）`, {
      source: 'relation',
      targetObjectCode: resolveChildRelationTargetCode(relation),
      columns: [],
    })
  })

  return options
}

/**
 * 按 relationKey 解析值字段/显示字段候选项。
 * 优先子表 columns；无 columns 时回落 targetFieldOptions（目标对象字段）。
 */
export function collectChildTableFieldOptions({
  relationKey = '',
  relationOptions = [],
  targetFieldOptions = [],
} = {}) {
  const key = String(relationKey || '').trim()
  if (!key)
    return []

  const matched = (Array.isArray(relationOptions) ? relationOptions : [])
    .find(item => String(item?.value || '').trim() === key)

  const fromColumns = (Array.isArray(matched?.columns) ? matched.columns : [])
    .map((column) => {
      if (typeof column === 'string') {
        const value = column.trim()
        return value ? { label: value, value } : null
      }
      const value = String(column?.fieldCode || column?.field || column?.value || '').trim()
      if (!value)
        return null
      const label = column.fieldLabel || column.label || value
      return { label: `${label}（${value}）`, value }
    })
    .filter(Boolean)

  if (fromColumns.length)
    return fromColumns

  return (Array.isArray(targetFieldOptions) ? targetFieldOptions : [])
    .map((field) => {
      const value = String(field?.value || field?.fieldCode || field?.field || '').trim()
      if (!value)
        return null
      const label = field.label || field.fieldName || value
      return {
        label: label.includes(`（${value}）`) ? label : `${label}（${value}）`,
        value,
      }
    })
    .filter(Boolean)
}

export function resolveChildRelationTargetObjectCode(relationKey = '', relationOptions = []) {
  const key = String(relationKey || '').trim()
  if (!key)
    return ''
  const matched = (Array.isArray(relationOptions) ? relationOptions : [])
    .find(item => String(item?.value || '').trim() === key)
  return String(matched?.targetObjectCode || '').trim()
}
