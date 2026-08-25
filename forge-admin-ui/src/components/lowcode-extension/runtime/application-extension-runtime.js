const SAFE_EFFECT_TYPES = new Set(['SET_FIELD', 'SHOW_MESSAGE', 'TRIGGER_ACTION'])
const CLIENT_EXTENSION_TYPES = new Set(['VISUAL_RULE', 'CLIENT_JS', 'SCOPED_CSS'])
const SENSITIVE_FIELD_PATTERN = /token|secret|password|cookie|authorization|api[_-]?key|session/i

export function selectRuntimeExtensions(extensions = [], hookCode, context = {}) {
  const hook = normalizeUpper(hookCode)
  return (Array.isArray(extensions) ? extensions : [])
    .filter(item => normalizeUpper(item?.status) === 'ENABLED')
    .filter(item => normalizeUpper(item?.hookCode) === hook)
    .filter(item => extensionApplies(item, context))
    .sort((left, right) => Number(left?.sortOrder || 0) - Number(right?.sortOrder || 0)
      || String(left?.extensionCode || '').localeCompare(String(right?.extensionCode || '')))
}

export function selectScopedCssExtensions(extensions = [], context = {}) {
  return selectRuntimeExtensions(extensions, 'PAGE_INIT', context)
    .filter(item => normalizeUpper(item?.extensionType) === 'SCOPED_CSS')
    .filter(item => String(item?.processedContent || '').trim())
}

export function extensionApplies(extension = {}, context = {}) {
  const scopeType = normalizeUpper(extension.scopeType || 'APPLICATION')
  if (scopeType === 'PAGE') {
    return !String(extension.scopeKey || '').trim()
      || String(extension.scopeKey) === 'default'
      || sameId(extension.scopeKey, context.pageId)
  }
  if (scopeType === 'OBJECT')
    return sameId(extension.objectId, context.objectId)
  if (scopeType === 'ENTRY')
    return sameId(extension.entryId, context.entryId)
  return scopeType === 'APPLICATION'
}

export function materializeRuntimeScopedCss(extension = {}, context = {}) {
  const css = String(extension.processedContent || '')
  const applicationCode = String(context.applicationCode || '').trim()
  const pageId = String(context.pageId || '').trim()
  if (!css || !applicationCode || !pageId)
    return ''
  const sourceApplication = escapeRegExp(applicationCode)
  const sourcePage = escapeRegExp(String(extension.scopeKey || 'default'))
  return css.replace(
    new RegExp(`\\[data-forge-app="${sourceApplication}"\\]\\[data-forge-page="${sourcePage}"\\]`, 'g'),
    `[data-forge-app="${applicationCode}"][data-forge-page="${pageId}"]`,
  )
}

export async function runRuntimeExtensions(options = {}) {
  const {
    extensions = [],
    hookCode,
    context = {},
    record = {},
    fieldCatalog = [],
    sandboxExecute,
    serverExecute,
    notify = defaultNotify,
    triggerAction,
  } = options
  let nextRecord = cloneRecord(record)
  const appliedEffects = []
  const selected = selectRuntimeExtensions(extensions, hookCode, context)
    .filter(item => normalizeUpper(item?.extensionType) !== 'SCOPED_CSS')

  for (const extension of selected) {
    try {
      const effects = await executeExtension(extension, {
        context,
        record: nextRecord,
        fieldCatalog,
        sandboxExecute,
        serverExecute,
      })
      assertGovernedFieldEffects(effects, fieldCatalog)
      nextRecord = await applyRuntimeExtensionEffects(effects, {
        record: nextRecord,
        notify,
        triggerAction,
      })
      appliedEffects.push(...effects)
    }
    catch (error) {
      const message = `${extension.extensionName || extension.extensionCode || '业务增强'}执行失败：${safeError(error)}`
      const policy = normalizeUpper(extension.failurePolicy || 'BLOCK')
      if (policy === 'BLOCK')
        throw new Error(message)
      if (policy === 'WARN')
        notify('warning', message)
      console.warn('[business-extension-runtime]', message)
    }
  }
  return { record: nextRecord, effects: appliedEffects }
}

function assertGovernedFieldEffects(effects = [], fieldCatalog = []) {
  const writable = new Set(fieldCatalog.filter(isWritableField).map(fieldCode))
  for (const effect of effects) {
    if (effect.type === 'SET_FIELD' && !writable.has(effect.field))
      throw new Error(`字段 ${effect.field} 不存在或不可写`)
  }
}

export function executeVisualRule(source, record = {}) {
  const rule = typeof source === 'string' ? JSON.parse(source) : source || {}
  const conditions = Array.isArray(rule.conditions) ? rule.conditions : []
  const matches = conditions.length === 0
    || (normalizeUpper(rule.match) === 'ANY'
      ? conditions.some(condition => matchesCondition(condition, record))
      : conditions.every(condition => matchesCondition(condition, record)))
  return matches ? normalizeEffects(rule.actions) : []
}

export async function applyRuntimeExtensionEffects(effects = [], options = {}) {
  const next = cloneRecord(options.record)
  for (const effect of normalizeEffects(effects)) {
    if (effect.type === 'SET_FIELD') {
      next[effect.field] = effect.value
      continue
    }
    if (effect.type === 'SHOW_MESSAGE') {
      options.notify?.(normalizeMessageLevel(effect.level), String(effect.message || '').slice(0, 500))
      continue
    }
    if (effect.type === 'TRIGGER_ACTION')
      await options.triggerAction?.(effect.actionCode, effect.payload || {})
  }
  return next
}

async function executeExtension(extension, runtime) {
  const type = normalizeUpper(extension.extensionType)
  if (type === 'VISUAL_RULE')
    return executeVisualRule(extension.content, runtime.record)
  if (type === 'CLIENT_JS') {
    if (typeof runtime.sandboxExecute !== 'function')
      throw new Error('客户端扩展沙箱未初始化')
    const bindings = detectClientBindings(extension.content)
    const readable = readableFieldCodes(runtime.fieldCatalog, runtime.record, bindings.fields)
    const writable = writableFieldCodes(runtime.fieldCatalog, bindings.fields)
    const result = await runtime.sandboxExecute(
      extension.content,
      { record: runtime.record, allowedActions: bindings.actions },
      readable,
      writable,
    )
    return normalizeEffects(result?.effects)
  }
  if (type === 'SERVER_BINDING') {
    if (typeof runtime.serverExecute !== 'function')
      throw new Error('服务端扩展执行器未初始化')
    const result = await runtime.serverExecute(extension, runtime)
    if (result?.success === false)
      throw new Error(result.message || result.code || '服务端处理器返回失败')
    return normalizeEffects(result?.output?.effects)
  }
  if (CLIENT_EXTENSION_TYPES.has(type))
    return []
  throw new Error(`不支持的扩展类型 ${type || 'UNKNOWN'}`)
}

function matchesCondition(condition = {}, record = {}) {
  const actual = record?.[condition.field]
  const expected = condition.value
  switch (normalizeUpper(condition.operator)) {
    case 'EQ': return equalValue(actual, expected)
    case 'NE': return !equalValue(actual, expected)
    case 'GT': return compareValue(actual, expected) > 0
    case 'GE': return compareValue(actual, expected) >= 0
    case 'LT': return compareValue(actual, expected) < 0
    case 'LE': return compareValue(actual, expected) <= 0
    case 'CONTAINS': return Array.isArray(actual)
      ? actual.some(item => equalValue(item, expected))
      : String(actual ?? '').includes(String(expected ?? ''))
    case 'EMPTY': return isEmpty(actual)
    case 'NOT_EMPTY': return !isEmpty(actual)
    default: return false
  }
}

function equalValue(actual, expected) {
  if (actual == null || expected == null)
    return actual == null && expected == null
  if (typeof actual === 'boolean')
    return actual === (expected === true || String(expected).toLowerCase() === 'true')
  if (typeof actual === 'number' && Number.isFinite(Number(expected)))
    return actual === Number(expected)
  return String(actual) === String(expected)
}

function compareValue(actual, expected) {
  const actualNumber = Number(actual)
  const expectedNumber = Number(expected)
  if (Number.isFinite(actualNumber) && Number.isFinite(expectedNumber))
    return actualNumber - expectedNumber
  return String(actual ?? '').localeCompare(String(expected ?? ''))
}

function detectClientBindings(source = '') {
  const fields = new Set()
  const actions = new Set()
  const script = String(source || '')
  for (const match of script.matchAll(/\b(?:readField|setField)\s*\(\s*(['"])([a-z]\w{0,63})\1/g))
    fields.add(match[2])
  for (const match of script.matchAll(/\btriggerAction\s*\(\s*(['"])([a-z]\w{0,63})\1/g))
    actions.add(match[2])
  return { fields: [...fields], actions: [...actions] }
}

function readableFieldCodes(catalog = [], record = {}, detected = []) {
  return normalizeCodes([
    ...detected,
    ...Object.keys(record || {}),
    ...catalog.map(fieldCode),
  ])
}

function writableFieldCodes(catalog = [], detected = []) {
  const known = catalog.filter(isWritableField).map(fieldCode)
  return normalizeCodes(known.length ? known : detected)
}

function isWritableField(field = {}) {
  const status = normalizeUpper(field.fieldStatus)
  return Boolean(fieldCode(field)) && field.systemField !== true && field.readonly !== true
    && !['DISABLED', 'HIDDEN'].includes(status)
}

function fieldCode(field = {}) {
  return String(field.fieldCode || field.field || field.code || '').trim()
}

function normalizeEffects(effects) {
  return (Array.isArray(effects) ? effects : [])
    .filter(effect => effect && SAFE_EFFECT_TYPES.has(normalizeUpper(effect.type || effect.actionType)))
    .map((effect) => {
      const type = normalizeUpper(effect.type || effect.actionType)
      if (type === 'SET_FIELD')
        return { type, field: normalizeCode(effect.field), value: safeClone(effect.value) }
      if (type === 'SHOW_MESSAGE')
        return { type, message: String(effect.message || '').slice(0, 500), level: normalizeMessageLevel(effect.level) }
      return { type, actionCode: normalizeCode(effect.actionCode), payload: safeClone(effect.payload || {}) }
    })
    .filter(effect => effect.type === 'SHOW_MESSAGE' ? effect.message : effect.field || effect.actionCode)
}

function normalizeCodes(values = []) {
  return [...new Set(values.map(normalizeCode).filter(code => code && !SENSITIVE_FIELD_PATTERN.test(code)))]
}

function normalizeCode(value) {
  const code = String(value || '').trim()
  return /^[a-z]\w{0,63}$/i.test(code) && !['__proto__', 'prototype', 'constructor'].includes(code) ? code : ''
}

function normalizeMessageLevel(level) {
  const value = String(level || 'info').toLowerCase()
  return ['success', 'warning', 'error'].includes(value) ? value : 'info'
}

function normalizeUpper(value) {
  return String(value || '').trim().toUpperCase()
}

function sameId(left, right) {
  return left != null && right != null && String(left) === String(right)
}

function isEmpty(value) {
  return value == null || value === '' || (Array.isArray(value) && value.length === 0)
}

function cloneRecord(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? safeClone(value) : {}
}

function safeClone(value) {
  if (value === undefined)
    return null
  return JSON.parse(JSON.stringify(value))
}

function safeError(error) {
  return String(error?.message || error || '未知错误')
    .replace(/(?:token|secret|password|cookie|authorization)\S*/gi, '[REDACTED]')
    .slice(0, 420)
}

function defaultNotify(level, message) {
  window.$message?.[level]?.(message)
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
