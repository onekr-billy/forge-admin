const FLOW_PERMISSION_VERSION = 3

export function normalizeFlowFormPermissions(source) {
  const parsed = parseSource(source)
  const rawFields = Array.isArray(parsed)
    ? parsed
    : Array.isArray(parsed?.fields) ? parsed.fields : []
  const rawChildren = Array.isArray(parsed?.children) ? parsed.children : []
  const rawArrays = Array.isArray(parsed?.arrays) ? parsed.arrays : []
  const fields = []
  const seen = new Set()

  rawFields.forEach((item) => {
    const normalized = normalizeFlowFieldPermission(item)
    if (!normalized.field || seen.has(normalized.permissionKey))
      return
    seen.add(normalized.permissionKey)
    fields.push(normalized)
  })

  const children = []
  const childSeen = new Set()
  rawChildren.forEach((item) => {
    const normalized = normalizeFlowChildPermission(item)
    if (!normalized.childKey || childSeen.has(normalized.childKey))
      return
    childSeen.add(normalized.childKey)
    children.push(normalized)
  })

  const arrays = []
  const arraySeen = new Set()
  rawArrays.forEach((item) => {
    const normalized = normalizeFlowArrayPermission(item)
    if (!normalized.arrayKey || arraySeen.has(normalized.arrayKey))
      return
    arraySeen.add(normalized.arrayKey)
    arrays.push(normalized)
  })

  return {
    version: parsed && !Array.isArray(parsed) && Number(parsed.version) > 0
      ? Number(parsed.version)
      : 1,
    fields,
    children,
    arrays,
  }
}

export function serializeFlowFormPermissions(fields = [], children = [], arrays = []) {
  const normalizedFields = (Array.isArray(fields) ? fields : [])
    .map(normalizeFlowFieldPermission)
    .filter(item => item.field)
  const normalizedChildren = (Array.isArray(children) ? children : [])
    .map(normalizeFlowChildPermission)
    .filter(item => item.childKey)
  const normalizedArrays = (Array.isArray(arrays) ? arrays : [])
    .map(normalizeFlowArrayPermission)
    .filter(item => item.arrayKey)

  if (!normalizedChildren.length && !normalizedArrays.length && normalizedFields.every(item => item.scope === 'main'))
    return normalizedFields.map(stripPermissionKey)

  return {
    version: normalizedArrays.length || normalizedFields.some(item => item.scope === 'array')
      ? FLOW_PERMISSION_VERSION
      : 2,
    fields: normalizedFields.map(stripPermissionKey),
    children: normalizedChildren,
    ...(normalizedArrays.length ? { arrays: normalizedArrays } : {}),
  }
}

export function normalizeFlowFieldCatalog(catalog = []) {
  const result = []
  const seen = new Set()
  const visit = (items, parentChild = null) => {
    if (!Array.isArray(items))
      return
    items.forEach((item) => {
      if (!item || typeof item !== 'object')
        return
      const childKey = String(item.childKey || item.relationKey || '').trim()
      const nestedFields = Array.isArray(item.fields)
        ? item.fields
        : Array.isArray(item.fieldCatalog) ? item.fieldCatalog : null
      if (nestedFields && (childKey || item.scope === 'child')) {
        const child = childKey || String(item.key || item.modelCode || item.tableName || '').trim()
        visit(nestedFields, child || parentChild)
        return
      }
      const field = String(item.field || item.fieldCode || item.childField || item.fieldName || item.name || item.key || '').trim()
      if (!field)
        return
      const arrayKey = String(item.arrayKey || '').trim()
      const effectiveChildKey = childKey || parentChild?.childKey || ''
      const effectiveArrayKey = arrayKey || parentChild?.arrayKey || ''
      const effectiveScope = effectiveArrayKey || item.scope === 'array'
        ? 'array'
        : (effectiveChildKey || item.scope === 'child' ? 'child' : 'main')
      const childField = String(item.childField || (effectiveScope === 'child' ? field : '') || '').trim()
      const itemField = String(item.itemField || (effectiveScope === 'array' ? field : '') || '').trim()
      const permissionKey = effectiveScope === 'child'
        ? `child:${effectiveChildKey}:${childField || field}`
        : effectiveScope === 'array'
          ? `array:${effectiveArrayKey}:${itemField || field}`
          : `main:${field}`
      if (!effectiveChildKey && effectiveScope === 'child')
        return
      if (!effectiveArrayKey && effectiveScope === 'array')
        return
      if (seen.has(permissionKey))
        return
      seen.add(permissionKey)
      result.push({
        ...item,
        field: effectiveScope === 'child' ? childField || field : (effectiveScope === 'array' ? itemField || field : field),
        fieldCode: effectiveScope === 'child' ? childField || field : (effectiveScope === 'array' ? itemField || field : field),
        label: String(item.label || item.title || item.fieldName || field).trim(),
        scope: effectiveScope,
        ...(effectiveScope === 'child' ? { childKey: effectiveChildKey, childField: childField || field } : {}),
        ...(effectiveScope === 'array' ? { arrayKey: effectiveArrayKey, itemField: itemField || field } : {}),
        permissionKey,
      })
    })
  }
  visit(catalog)
  return collapseAliasedChildFields(result)
}

/**
 * 同一张子表会同时带上关系键和带应用前缀的对象编码，例如 detail_ujpc 与 cgou_detail_ujpc。
 * 权限面板和审批回放都要当成一张表，否则配置写在一把键上，运行时用另一把键，看起来就不生效。
 */
export function childTableKeysAlias(left, right) {
  const a = String(left || '').trim()
  const b = String(right || '').trim()
  if (!a || !b)
    return false
  if (a === b)
    return true
  const shorter = a.length <= b.length ? a : b
  const longer = a.length <= b.length ? b : a
  return longer.endsWith(`_${shorter}`)
}

export function collapseAliasedChildFields(fields = []) {
  const mains = []
  const groups = []
  ;(Array.isArray(fields) ? fields : []).forEach((field) => {
    if (!field || field.scope !== 'child' || !field.childKey) {
      mains.push(field)
      return
    }
    const group = groups.find(item => childTableKeysAlias(item.childKey, field.childKey))
    if (!group) {
      groups.push({
        childKey: field.childKey,
        childLabel: readableChildLabel('', field.childLabel || field.relationName),
        fields: [field],
      })
      return
    }
    group.childKey = String(group.childKey).length >= String(field.childKey).length
      ? group.childKey
      : field.childKey
    group.childLabel = readableChildLabel(group.childLabel, field.childLabel || field.relationName)
    group.fields.push(field)
  })
  const children = []
  groups.forEach((group) => {
    const seen = new Set()
    group.fields.forEach((field) => {
      const childField = String(field.childField || field.field || '').trim()
      if (!childField || seen.has(childField))
        return
      seen.add(childField)
      children.push({
        ...field,
        field: childField,
        fieldCode: childField,
        childField,
        childKey: group.childKey,
        childLabel: group.childLabel || field.childLabel || group.childKey,
        relationName: field.relationName || group.childLabel || '',
        permissionKey: `child:${group.childKey}:${childField}`,
      })
    })
  })
  return [...mains, ...children]
}

function readableChildLabel(current, incoming) {
  const candidates = [incoming, current].map(value => String(value || '').trim()).filter(Boolean)
  return candidates.find(value => /[^\u0000-\u007F]/.test(value)) || candidates[0] || ''
}

export function applyChildTableFieldPermissions(children = [], permissions = []) {
  const list = collectPermissionFields(permissions)
  return (Array.isArray(children) ? children : []).map((child) => {
    const childKey = child?.modelCode || child?.relationKey || child?.key || child?.tableName || ''
    const fields = (Array.isArray(child?.fields) ? child.fields : []).map((field) => {
      const fieldName = String(field?.field || field?.fieldCode || field?.sourceField || '').trim()
      const permission = list.find((item) => {
        if (item?.scope && item.scope !== 'child' && !item.childKey)
          return false
        const itemField = String(item?.childField || item?.field || '').trim()
        if (!sameFlowFieldName(fieldName, itemField))
          return false
        const itemKey = String(item?.childKey || item?.relationKey || '').trim()
        if (!itemKey)
          return item?.scope === 'child'
        return childTableKeysAlias(itemKey, childKey)
      })
      if (permission)
        return permission.writable === true ? enableChildField(field) : lockChildField(field)
      if (child?.allowUpdate === true)
        return enableChildField(field)
      return field
    })
    return {
      ...child,
      fields,
      allowUpdate: child?.allowUpdate === true || fields.some(item => item?.writable === true),
    }
  })
}

function collectPermissionFields(permissions) {
  const byKey = new Map()
  permissionSources(permissions).forEach((source) => {
    normalizeFlowFormPermissions(source).fields.forEach((item) => {
      const key = item.permissionKey || `${item.scope || 'main'}:${item.childKey || ''}:${item.field || ''}`
      const existing = byKey.get(key)
      if (!existing || (item.writable === true && existing.writable !== true))
        byKey.set(key, item)
    })
  })
  return Array.from(byKey.values())
}

function permissionSources(permissions) {
  if (permissions == null)
    return []
  if (typeof permissions === 'string')
    return [permissions]
  if (Array.isArray(permissions)) {
    const fieldList = permissions.some(item => item && !Array.isArray(item) && (item.field || item.childField || item.childKey || item.scope))
    return fieldList ? [permissions] : permissions.filter(item => item != null)
  }
  return [permissions]
}

function sameFlowFieldName(left, right) {
  const a = String(left || '').trim()
  const b = String(right || '').trim()
  if (!a || !b)
    return false
  return a === b || camelFlowField(a).toLowerCase() === camelFlowField(b).toLowerCase()
}

function camelFlowField(value) {
  return String(value || '').replace(/_([a-z0-9])/gi, (_, ch) => ch.toUpperCase())
}

function enableChildField(field = {}) {
  const props = { ...(field.props || {}) }
  delete props.readonly
  delete props.disabled
  return {
    ...field,
    writable: true,
    editable: true,
    readonly: false,
    disabled: false,
    props,
  }
}

function lockChildField(field = {}) {
  return {
    ...field,
    writable: false,
    editable: false,
    readonly: true,
    disabled: true,
  }
}

export function normalizeFlowFieldPermission(item = {}) {
  const rawScope = String(item.scope || (item.arrayKey ? 'array' : (item.childKey ? 'child' : 'main'))).trim().toLowerCase()
  const scope = rawScope === 'child' ? 'child' : (rawScope === 'array' ? 'array' : 'main')
  const childKey = String(item.childKey || item.relationKey || '').trim()
  const childField = String(item.childField || (scope === 'child' ? item.field : '') || '').trim()
  const arrayKey = String(item.arrayKey || '').trim()
  const itemField = String(item.itemField || (scope === 'array' ? item.field : '') || '').trim()
  const field = String(item.field || item.fieldCode || item.code || childField || itemField).trim()
  const normalizedField = scope === 'child' ? childField || field : (scope === 'array' ? itemField || field : field)
  const readable = readBoolean(item.readable, readBoolean(item.visible, true))
  const writable = readable && readBoolean(item.writable, readBoolean(item.editable, scope !== 'child'))
  const permissionKey = scope === 'child'
    ? `child:${childKey}:${normalizedField}`
    : scope === 'array'
      ? `array:${arrayKey}:${normalizedField}`
      : `main:${normalizedField}`
  return {
    ...item,
    field: normalizedField,
    fieldCode: normalizedField,
    label: String(item.label || normalizedField || '').trim(),
    scope,
    ...(scope === 'child' ? { childKey, childField: normalizedField } : {}),
    ...(scope === 'array' ? { arrayKey, itemField: normalizedField } : {}),
    visible: readable,
    editable: writable,
    readable,
    writable,
    required: writable && readBoolean(item.required, false),
    permissionKey,
  }
}

export function normalizeFlowArrayPermission(item = {}) {
  const arrayKey = String(item.arrayKey || item.field || item.key || '').trim()
  const readable = readBoolean(item.readable, true)
  return {
    ...item,
    arrayKey,
    label: String(item.label || item.title || arrayKey).trim(),
    readable,
    allowCreate: readable && readBoolean(item.allowCreate, false),
    allowUpdate: readable && readBoolean(item.allowUpdate, true),
    allowDelete: readable && readBoolean(item.allowDelete, false),
  }
}

/**
 * 流程模型表单权限读的是表单资产字段目录。子表列存在明细表组件 props.columns 里，
 * 不会作为主表 fieldBinding 出现，这里补成 scope=child，权限面板才能按子表字段控制。
 */
export function appendChildTableCatalogFields(fields = [], schema = {}) {
  const result = Array.isArray(fields) ? [...fields] : []
  const seen = new Set()
  result.forEach((field) => {
    const childKey = String(field?.childKey || field?.relationKey || '').trim()
    const childField = String(field?.childField || (field?.scope === 'child' ? field?.field : '') || '').trim()
    if (childKey && childField)
      seen.add(`${childKey}:${childField}`)
  })
  const roots = []
  const pushRoot = (value) => {
    if (value && typeof value === 'object')
      roots.push(value)
  }
  pushRoot(schema)
  pushRoot(schema?.schema)
  pushRoot(schema?.formDesignerSchema)
  const visit = (components) => {
    if (!Array.isArray(components))
      return
    components.forEach((component) => {
      if (!component || typeof component !== 'object')
        return
      const componentKey = String(component.componentKey || component.type || '').trim()
      const props = component.props && typeof component.props === 'object' ? component.props : {}
      if (componentKey === 'subTable' || componentKey === 'childTable') {
        const childKey = String(props.modelCode || props.relationKey || component.modelCode || component.relationKey || '').trim()
        const childLabel = String(props.header || props.relationName || component.label || childKey).trim()
        const columns = Array.isArray(props.columns)
          ? props.columns
          : Array.isArray(props.fields) ? props.fields : []
        columns.forEach((column) => {
          const childField = String(
            typeof column === 'string'
              ? column
              : (column?.fieldCode || column?.field || column?.sourceField || ''),
          ).trim()
          const permissionKey = childKey && childField ? `${childKey}:${childField}` : ''
          if (!permissionKey || seen.has(permissionKey))
            return
          seen.add(permissionKey)
          const columnObject = column && typeof column === 'object' ? column : {}
          result.push({
            field: childField,
            fieldCode: childField,
            label: String(columnObject.fieldLabel || columnObject.label || columnObject.title || childField).trim(),
            scope: 'child',
            childKey,
            childField,
            childLabel,
            relationName: String(props.relationName || childLabel).trim(),
          })
        })
      }
      visit(component.children)
    })
  }
  roots.forEach(root => visit(root.components))
  return result
}

/**
 * 审批节点权限面板字段目录：主表/数组保留资产目录；子表优先用当前表单设计器 subTable，
 * 避免发布态 masterDetail / 旧 fieldCatalog 里已删除的子表继续出现。
 */
export function resolvePermissionFieldCatalog(fields = [], schema = {}) {
  const base = Array.isArray(fields) ? fields : []
  const nonChildFields = base.filter((field) => {
    const scope = String(field?.scope || '').trim().toLowerCase()
    const childKey = String(field?.childKey || field?.relationKey || '').trim()
    return scope !== 'child' && !childKey
  })
  const designerChildren = appendChildTableCatalogFields([], schema)
    .filter(field => String(field?.scope || '').toLowerCase() === 'child')
  // 只要资产带了表单设计器结构，子表就以设计器为准（含「已全部删除」）
  if (hasFormDesignerComponents(schema))
    return [...nonChildFields, ...designerChildren]
  return appendChildTableCatalogFields(base, schema)
}

function hasFormDesignerComponents(schema = {}) {
  const roots = [schema, schema?.schema, schema?.formDesignerSchema]
  return roots.some(root => Array.isArray(root?.components))
}

export function normalizeFlowChildPermission(item = {}) {
  const childKey = String(item.childKey || item.relationKey || item.key || '').trim()
  return {
    ...item,
    childKey,
    label: String(item.label || item.title || childKey).trim(),
    readable: readBoolean(item.readable, true),
    allowCreate: readBoolean(item.allowCreate, false),
    allowUpdate: readBoolean(item.allowUpdate, false),
    allowDelete: readBoolean(item.allowDelete, false),
  }
}

function stripPermissionKey(item) {
  const result = {
    field: item.field,
    fieldCode: item.fieldCode || item.field,
    label: item.label || item.field,
    visible: item.readable !== false,
    editable: item.writable === true,
    readable: item.readable !== false,
    writable: item.writable === true,
    required: item.required === true && item.writable === true,
  }
  if (item.scope === 'child') {
    result.scope = 'child'
    result.childKey = item.childKey
    result.childField = item.childField || item.field
  }
  else if (item.scope === 'array') {
    result.scope = 'array'
    result.arrayKey = item.arrayKey
    result.itemField = item.itemField || item.field
  }
  return result
}

function parseSource(source) {
  if (typeof source === 'string') {
    try {
      return parseSource(JSON.parse(source))
    }
    catch {
      return []
    }
  }
  if (Array.isArray(source) || (source && typeof source === 'object'))
    return source
  return []
}

function readBoolean(value, fallback) {
  if (value === undefined || value === null || value === '')
    return fallback
  if (typeof value === 'boolean')
    return value
  if (typeof value === 'number')
    return value !== 0
  const text = String(value).trim().toLowerCase()
  if (['true', '1', 'yes', 'y'].includes(text))
    return true
  if (['false', '0', 'no', 'n'].includes(text))
    return false
  return fallback
}
