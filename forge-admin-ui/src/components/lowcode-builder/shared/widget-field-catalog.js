import { isChildListField } from '@/components/lowcode-builder/page/page-schema'

/**
 * 把页面/表单字段目录转成打印字段选择器同款结构：
 * { path, label, type }，子表以 COLLECTION + 子字段呈现。
 */
export function buildWidgetFieldCatalog(fields = [], options = {}) {
  const catalog = []
  const seen = new Set()
  const collections = new Map()

  const push = (entry) => {
    const path = String(entry?.path || '').trim()
    if (!path || seen.has(path))
      return
    seen.add(path)
    catalog.push({
      path,
      label: String(entry.label || path).trim() || path,
      type: entry.type || 'STRING',
    })
  }

  ;(Array.isArray(fields) ? fields : []).forEach((field) => {
    if (!field || field.nodeType === 'widget')
      return
    const code = String(field.fieldCode || field.field || field.key || '').trim()
    if (!code)
      return
    const label = String(field.label || field.fieldName || field.rawLabel || code).trim() || code

    if (isChildListField(field)) {
      const collectionPath = resolveChildCollectionPath(field)
      const collectionLabel = String(field.modelName || field.sourceLabel || collectionPath).trim() || collectionPath
      if (!collections.has(collectionPath)) {
        collections.set(collectionPath, collectionLabel)
        push({ path: collectionPath, label: collectionLabel, type: 'COLLECTION' })
      }
      const childCode = String(field.sourceField || code.split('__').at(-1) || code).trim()
      push({
        path: `${collectionPath}.${childCode}`,
        label: `${collectionLabel} · ${label}`,
        type: mapWidgetFieldType(field),
      })
      return
    }

    push({ path: code, label, type: mapWidgetFieldType(field) })
  })

  // 表单设计器里的子表（props.columns），补齐主子表目录
  walkFormSchemaSubTables(options.formDesignerSchema, (subTable) => {
    const collectionPath = String(subTable.relationKey || subTable.fieldCode || subTable.id || '').trim()
    if (!collectionPath)
      return
    const collectionLabel = String(subTable.header || subTable.label || collectionPath).trim() || collectionPath
    if (!collections.has(collectionPath)) {
      collections.set(collectionPath, collectionLabel)
      push({ path: collectionPath, label: collectionLabel, type: 'COLLECTION' })
    }
    ;(Array.isArray(subTable.columns) ? subTable.columns : []).forEach((column) => {
      const childCode = String(column.fieldCode || column.field || column.key || column.value || '').trim()
      if (!childCode)
        return
      push({
        path: `${collectionPath}.${childCode}`,
        label: `${collectionLabel} · ${column.label || column.title || childCode}`,
        type: mapWidgetFieldType(column),
      })
    })
  })

  return catalog
}

export function resolveChildCollectionPath(field = {}) {
  const modelCode = String(field.modelCode || '').trim()
  if (modelCode)
    return modelCode
  const key = String(field.field || field.fieldCode || '')
  if (key.includes('__'))
    return key.split('__')[0] || 'children'
  return String(field.sourceLabel || 'children').trim() || 'children'
}

export function mapWidgetFieldType(field = {}) {
  const raw = String(field.dataType || field.fieldType || field.type || field.componentKey || '').toUpperCase()
  if (['IMAGE', 'FILE', 'NUMBER', 'DATE', 'DATETIME', 'BOOLEAN', 'COLLECTION'].includes(raw))
    return raw
  if (/IMAGE|UPLOAD|AVATAR/.test(raw))
    return 'IMAGE'
  if (/FILE|ATTACHMENT/.test(raw))
    return 'FILE'
  if (/NUMBER|INTEGER|DECIMAL|MONEY|AMOUNT/.test(raw))
    return 'NUMBER'
  if (/DATE|TIME/.test(raw))
    return /TIME/.test(raw) ? 'DATETIME' : 'DATE'
  if (/SWITCH|BOOLEAN|CHECKBOX/.test(raw))
    return 'BOOLEAN'
  return 'STRING'
}

function walkFormSchemaSubTables(schema, visit) {
  if (!schema || typeof visit !== 'function')
    return
  const walk = (components = []) => {
    ;(Array.isArray(components) ? components : []).forEach((component) => {
      if (!component)
        return
      if (component.componentKey === 'subTable' || component.type === 'subTable') {
        visit({
          id: component.id,
          label: component.label,
          fieldCode: component.fieldBinding?.fieldCode || component.field,
          relationKey: component.props?.relationKey,
          header: component.props?.header,
          columns: component.props?.columns,
        })
      }
      walk(component.children || [])
    })
  }
  walk(schema.components || schema.list || [])
}

/**
 * 组合显示：按字段列表或模板从当前数据对象拼出展示文案。
 */
export function resolveComposeDisplayValue(data, binding = {}, getNestedValue = defaultGetNestedValue) {
  if (data === null || data === undefined)
    return ''
  if (typeof data !== 'object' || Array.isArray(data))
    return data == null ? '' : String(data)

  const template = String(binding.displayTemplate || '').trim()
  if (template) {
    return template.replace(/\{([^{}]+)\}/g, (_, token) => {
      const value = getNestedValue(data, String(token).trim())
      return value == null ? '' : String(value)
    })
  }

  const fields = Array.isArray(binding.displayFields)
    ? binding.displayFields.map(item => String(item || '').trim()).filter(Boolean)
    : []
  if (!fields.length)
    return ''
  const separator = binding.displaySeparator == null ? ' ' : String(binding.displaySeparator)
  return fields
    .map(path => getNestedValue(data, path))
    .filter(value => value !== null && value !== undefined && value !== '')
    .map(value => (typeof value === 'object' ? JSON.stringify(value) : String(value)))
    .join(separator)
}

export function isComposeDisplayEnabled(binding = {}) {
  return binding.displayMode === 'compose'
    || (Array.isArray(binding.displayFields) && binding.displayFields.length > 0)
    || Boolean(String(binding.displayTemplate || '').trim())
}

function defaultGetNestedValue(source, path) {
  if (!path)
    return source
  return String(path).split('.').reduce((current, key) => {
    if (current === null || current === undefined)
      return undefined
    return current[key]
  }, source)
}
