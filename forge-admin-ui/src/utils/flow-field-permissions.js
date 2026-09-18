const FLOW_PERMISSION_VERSION = 2

export function normalizeFlowFormPermissions(source) {
  const parsed = parseSource(source)
  const rawFields = Array.isArray(parsed)
    ? parsed
    : Array.isArray(parsed?.fields) ? parsed.fields : []
  const rawChildren = Array.isArray(parsed?.children) ? parsed.children : []
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

  return {
    version: parsed && !Array.isArray(parsed) && Number(parsed.version) > 0
      ? Number(parsed.version)
      : 1,
    fields,
    children,
  }
}

export function serializeFlowFormPermissions(fields = [], children = []) {
  const normalizedFields = (Array.isArray(fields) ? fields : [])
    .map(normalizeFlowFieldPermission)
    .filter(item => item.field)
  const normalizedChildren = (Array.isArray(children) ? children : [])
    .map(normalizeFlowChildPermission)
    .filter(item => item.childKey)

  if (!normalizedChildren.length && normalizedFields.every(item => item.scope === 'main'))
    return normalizedFields.map(stripPermissionKey)

  return {
    version: FLOW_PERMISSION_VERSION,
    fields: normalizedFields.map(stripPermissionKey),
    children: normalizedChildren,
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
      const field = String(item.field || item.fieldCode || item.fieldName || item.name || item.key || '').trim()
      if (!field)
        return
      const effectiveChildKey = childKey || parentChild?.childKey || ''
      const effectiveScope = effectiveChildKey || item.scope === 'child' ? 'child' : 'main'
      const childField = String(item.childField || (effectiveScope === 'child' ? field : '') || '').trim()
      const permissionKey = effectiveScope === 'child'
        ? `child:${effectiveChildKey}:${childField || field}`
        : `main:${field}`
      if (!effectiveChildKey && effectiveScope === 'child')
        return
      if (seen.has(permissionKey))
        return
      seen.add(permissionKey)
      result.push({
        ...item,
        field: effectiveScope === 'child' ? childField || field : field,
        fieldCode: effectiveScope === 'child' ? childField || field : field,
        label: String(item.label || item.title || item.fieldName || field).trim(),
        scope: effectiveScope,
        ...(effectiveScope === 'child' ? { childKey: effectiveChildKey, childField: childField || field } : {}),
        permissionKey,
      })
    })
  }
  visit(catalog)
  return result
}

export function normalizeFlowFieldPermission(item = {}) {
  const scope = String(item.scope || (item.childKey ? 'child' : 'main')).trim().toLowerCase() === 'child'
    ? 'child'
    : 'main'
  const childKey = String(item.childKey || item.relationKey || '').trim()
  const childField = String(item.childField || (scope === 'child' ? item.field : '') || '').trim()
  const field = String(item.field || item.fieldCode || item.code || childField).trim()
  const normalizedField = scope === 'child' ? childField || field : field
  const readable = readBoolean(item.readable, readBoolean(item.visible, true))
  const writable = readable && readBoolean(item.writable, readBoolean(item.editable, scope === 'main'))
  const permissionKey = scope === 'child'
    ? `child:${childKey}:${normalizedField}`
    : `main:${normalizedField}`
  return {
    ...item,
    field: normalizedField,
    fieldCode: normalizedField,
    label: String(item.label || normalizedField || '').trim(),
    scope,
    ...(scope === 'child' ? { childKey, childField: normalizedField } : {}),
    visible: readable,
    editable: writable,
    readable,
    writable,
    required: writable && readBoolean(item.required, false),
    permissionKey,
  }
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
