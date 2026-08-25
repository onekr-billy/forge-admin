const INPUT_TYPE_MAP = Object.freeze({
  text: 'input',
  number: 'input-number',
  integer: 'input-number',
  money: 'input-number',
  boolean: 'switch',
  date: 'date',
  datetime: 'datetime',
  select: 'select',
})

const SAFE_INPUT_NAME = /^[a-z]\w{0,63}$/i
const SAFE_IDEMPOTENCY_KEY = /^[\w.:-]{8,128}$/

export function shouldShowDetailFlowHistory({
  isDetailMode = false,
  runtime = null,
  timelineVisible = true,
  diagramVisible = true,
} = {}) {
  return isDetailMode === true
    && Boolean(String(runtime?.processInstanceId || '').trim())
    && (timelineVisible === true || diagramVisible === true)
}

export function buildBusinessActionInputFormSchema(inputSchema) {
  if (!Array.isArray(inputSchema))
    return []
  const names = new Set()
  return inputSchema.map((definition) => {
    if (!definition || typeof definition !== 'object')
      return null
    const field = String(definition.name || '').trim()
    const inputType = String(definition.type || 'text').trim().toLowerCase()
    const type = INPUT_TYPE_MAP[inputType]
    if (!SAFE_INPUT_NAME.test(field) || names.has(field) || !type)
      return null
    names.add(field)
    const item = {
      field,
      label: String(definition.label || field),
      type,
      required: definition.required === true,
      defaultValue: definition.defaultValue,
      placeholder: definition.placeholder || defaultPlaceholder(type, definition.label || field),
    }
    if (definition.min !== undefined && definition.min !== null)
      item.min = Number(definition.min)
    if (definition.max !== undefined && definition.max !== null)
      item.max = Number(definition.max)
    if (inputType === 'integer') {
      item.precision = 0
      item.step = 1
    }
    if (inputType === 'money') {
      const scale = Math.min(6, Math.max(0, Number(definition.scale ?? 2)))
      item.precision = scale
      item.step = 10 ** -scale
      item.min = definition.min === undefined || definition.min === null ? 0 : Number(definition.min)
    }
    if (inputType === 'text' && definition.maxLength)
      item.maxlength = Number(definition.maxLength)
    if (inputType === 'select')
      item.options = normalizeInputOptions(definition.options)
    return item
  }).filter(Boolean)
}

export function buildBusinessActionInitialData(config = {}, row = {}) {
  const hasInputSchema = Array.isArray(config.inputSchema)
  const inputSchema = Array.isArray(config.inputSchema) ? config.inputSchema : []
  const declaredFields = new Set(inputSchema.map(item => String(item?.name || '').trim()).filter(Boolean))
  const defaults = {}
  inputSchema.forEach((definition) => {
    const name = String(definition?.name || '').trim()
    if (name && definition && Object.prototype.hasOwnProperty.call(definition, 'defaultValue'))
      defaults[name] = definition.defaultValue
  })
  const defaultValues = config.defaultValues && typeof config.defaultValues === 'object'
    ? config.defaultValues
    : {}
  Object.entries(defaultValues).forEach(([key, value]) => {
    if (hasInputSchema && !declaredFields.has(key))
      return
    defaults[key] = typeof value === 'string' && value.startsWith('row.')
      ? readPath(row, value.slice(4))
      : value
  })
  return defaults
}

export function createBusinessActionIdempotencyKey(cryptoLike = globalThis.crypto) {
  if (typeof cryptoLike?.randomUUID === 'function')
    return `ui:${cryptoLike.randomUUID()}`
  if (typeof cryptoLike?.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    cryptoLike.getRandomValues(bytes)
    return `ui:${Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')}`
  }
  return `ui:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 14)}`
}

export function resolveBusinessActionAttempt(state = {}, formData = {}, keyFactory = createBusinessActionIdempotencyKey) {
  const payloadDigest = stableSerialize(formData && typeof formData === 'object' ? formData : {})
  const currentKey = SAFE_IDEMPOTENCY_KEY.test(String(state.idempotencyKey || ''))
    ? state.idempotencyKey
    : ''
  const shouldRotate = state.lastPayloadDigest && state.lastPayloadDigest !== payloadDigest
  return {
    idempotencyKey: !currentKey || shouldRotate ? keyFactory() : currentKey,
    lastPayloadDigest: payloadDigest,
  }
}

export function buildBusinessActionExecutePayload({
  action = {},
  config = {},
  objectCode = '',
  recordId = '',
  formData = {},
  routeQuery = {},
  idempotencyKey = '',
  parentRecordId = '',
  childRecordId = '',
  relationKey = '',
} = {}) {
  const payload = {
    suiteCode: action.suiteCode || config.suiteCode || '',
    objectCode,
    recordId: recordId === undefined || recordId === null ? '' : String(recordId),
    actionCode: action.actionCode || action.key || '',
    formData: formData && typeof formData === 'object' ? { ...formData } : {},
    context: {
      routeQuery: routeQuery && typeof routeQuery === 'object' ? { ...routeQuery } : {},
    },
    idempotencyKey,
  }
  if (isPresentIdentifier(parentRecordId))
    payload.parentRecordId = String(parentRecordId)
  if (isPresentIdentifier(childRecordId))
    payload.childRecordId = String(childRecordId)
  if (String(relationKey || '').trim())
    payload.relationKey = String(relationKey).trim()
  return payload
}

export function unwrapBusinessActionResult(response, fallbackMessage = '动作执行失败') {
  const result = response?.data && typeof response.data === 'object'
    ? response.data
    : (response && typeof response === 'object' ? response : {})
  if (String(result.executeStatus || '').toUpperCase() === 'FAILED')
    throw new Error(result.message || fallbackMessage)
  return result
}

export function buildChildRowActionContext({ child = {}, parentRecord = {}, childRecord = {} } = {}) {
  const parentRecordId = readIdentifier(parentRecord)
  const childRecordId = readIdentifier(childRecord)
  const relationKey = String(child.relationKey || child.key || child.modelCode || '').trim()
  return {
    parentRecordId,
    childRecordId,
    relationKey,
    persisted: isPresentIdentifier(parentRecordId)
      && isPresentIdentifier(childRecordId)
      && Boolean(relationKey),
  }
}

/**
 * 判断流程动作是否属于指定运行位置。历史动作没有 position 时按列表行动作兼容，
 * 避免详情/表单动作再次混入列表操作列。
 */
export function isRuntimeActionForPosition(action = {}, position = 'row') {
  const actual = String(action?.position || '').trim().toLowerCase()
  const expected = String(position || 'row').trim().toLowerCase()
  if (expected === 'row')
    return !actual || actual === 'row'
  return actual === expected
}

/** 当前记录已有同一个业务流程运行实例时，隐藏其启动动作。 */
export function shouldHideProcessStartAction(action = {}, runtime = {}) {
  const type = String(action.actionType || action.key || '').toUpperCase()
  const key = String(action.key || '').toUpperCase()
  const isApplicationProcess = type === 'START_PROCESS' || key.startsWith('STARTPROCESS:')
  const isDocumentFlow = ['START_FLOW', 'START_APPROVAL'].includes(type) || key === 'START_FLOW'
  if (!isApplicationProcess && !isDocumentFlow)
    return false

  if (isApplicationProcess) {
    const processCode = String(action.processCode || '').trim()
    const activeCodes = Array.isArray(runtime?.activeProcessCodes)
      ? runtime.activeProcessCodes.map(item => String(item).trim())
      : []
    // 应用级业务流程必须按 processCode 精确判断，不能因为同一单据的另一个
    // 主流程正在运行，就把所有可并行流程的启动按钮一起隐藏。
    return Boolean(processCode) && activeCodes.includes(processCode)
  }

  const status = String(runtime?.flowStatus || '').toUpperCase()
  return runtime?.nextAction === 'VIEW_FLOW' || ['STARTED', 'RUNNING', 'IN_PROCESS'].includes(status)
}

/** 计算流程/业务按钮的结构化或已编译显示条件。 */
export function matchesRuntimeDisplayCondition(expression = '', row = {}) {
  if (expression && typeof expression === 'object' && !Array.isArray(expression)) {
    const rules = Array.isArray(expression.rules) ? expression.rules : []
    const groups = Array.isArray(expression.groups) ? expression.groups : []
    const mode = String(expression.logic || expression.operator || 'AND').toUpperCase() === 'OR' ? 'any' : 'all'
    const matches = groups.map((group) => {
      const groupRules = Array.isArray(group?.rules)
        ? group.rules
        : Array.isArray(group?.conditions) ? group.conditions : []
      const groupMode = String(group?.logic || group?.operator || 'AND').toUpperCase() === 'OR' ? 'any' : 'all'
      const results = groupRules.map(rule => matchesDisplayRule(rule, row))
      return results.length ? (groupMode === 'any' ? results.some(Boolean) : results.every(Boolean)) : true
    })
    const results = groups.length ? matches : rules.map(rule => matchesDisplayRule(rule, row))
    return results.length ? (mode === 'any' ? results.some(Boolean) : results.every(Boolean)) : true
  }

  const text = String(expression || '').trim().replace(/\s+/g, ' ')
  if (!text || text === '[object Object]')
    return true
  const orParts = splitRuntimeExpression(text, 'OR')
  if (orParts.length > 1)
    return orParts.some(item => matchesRuntimeDisplayCondition(item, row))
  const andParts = splitRuntimeExpression(text, 'AND')
  if (andParts.length > 1)
    return andParts.every(item => matchesRuntimeDisplayCondition(item, row))

  const inExpression = parseRuntimeInExpression(text)
  if (inExpression) {
    const actual = readPath(row, inExpression.field)
    const expectedValues = inExpression.value.split(',').map(item => stripRuntimeQuote(item)).filter(Boolean)
    const matched = expectedValues.some(item => runtimeValuesEqual(actual, item))
    return inExpression.operator === 'NOT IN' ? !matched : matched
  }

  const comparison = parseRuntimeComparison(text)
  if (!comparison)
    return true
  const actual = readPath(row, comparison.field)
  const operator = comparison.operator
  const expected = stripRuntimeQuote(comparison.value)
  if (['>', '>=', '<', '<='].includes(operator)) {
    const left = Number(actual)
    const right = Number(expected)
    if (!Number.isFinite(left) || !Number.isFinite(right))
      return false
    if (operator === '>')
      return left > right
    if (operator === '>=')
      return left >= right
    if (operator === '<')
      return left < right
    return left <= right
  }
  const matched = runtimeValuesEqual(actual, expected)
  return operator === '!=' ? !matched : matched
}

function matchesDisplayRule(rule = {}, row = {}) {
  const field = String(rule.field || rule.path || '').trim()
  if (!field)
    return true
  const actual = readPath(row, field)
  const operator = String(rule.operator || rule.op || 'EQ').replace(/[-\s]/g, '_').toUpperCase()
  if (operator === 'NOT_EMPTY')
    return actual !== undefined && actual !== null && String(actual).trim() !== ''
  if (operator === 'EMPTY')
    return actual === undefined || actual === null || String(actual).trim() === ''
  if (operator === 'IN' || operator === 'NOT_IN') {
    const values = Array.isArray(rule.value)
      ? rule.value
      : String(rule.value ?? '').split(',').map(item => item.trim()).filter(Boolean)
    const matched = values.some(item => runtimeValuesEqual(actual, item))
    return operator === 'NOT_IN' ? !matched : matched
  }
  if (['GT', 'GTE', 'GE', 'LT', 'LTE', 'LE'].includes(operator)) {
    const left = Number(actual)
    const right = Number(rule.value)
    if (!Number.isFinite(left) || !Number.isFinite(right))
      return false
    if (operator === 'GT')
      return left > right
    if (operator === 'GTE' || operator === 'GE')
      return left >= right
    if (operator === 'LT')
      return left < right
    return left <= right
  }
  if (operator === 'CONTAINS' || operator === 'NOT_CONTAINS') {
    const matched = String(actual ?? '').includes(String(rule.value ?? ''))
    return operator === 'NOT_CONTAINS' ? !matched : matched
  }
  const matched = runtimeValuesEqual(actual, rule.value)
  return operator === 'NE' || operator === 'NOT_EQUAL' ? !matched : matched
}

function runtimeValuesEqual(actual, expected) {
  const left = actual === undefined || actual === null ? '' : String(actual).trim()
  const right = expected === undefined || expected === null ? '' : String(expected).trim()
  if (left === right)
    return true
  const leftNumber = Number(left)
  const rightNumber = Number(right)
  return left !== '' && right !== '' && Number.isFinite(leftNumber) && Number.isFinite(rightNumber) && leftNumber === rightNumber
}

function stripRuntimeQuote(value = '') {
  return String(value || '').trim().replace(/^['"]|['"]$/g, '')
}

function splitRuntimeExpression(value, keyword) {
  const token = ` ${keyword} `
  const normalized = String(value || '')
  const upperValue = normalized.toUpperCase()
  const parts = []
  let offset = 0
  let index = upperValue.indexOf(token, offset)
  while (index >= 0) {
    parts.push(normalized.slice(offset, index).trim())
    offset = index + token.length
    index = upperValue.indexOf(token, offset)
  }
  parts.push(normalized.slice(offset).trim())
  return parts.filter(Boolean)
}

function parseRuntimeInExpression(value) {
  const text = String(value || '')
  const upperText = text.toUpperCase()
  for (const operator of ['NOT IN', 'IN']) {
    const token = ` ${operator} `
    const index = upperText.indexOf(token)
    if (index > 0) {
      const field = text.slice(0, index).trim()
      const expected = text.slice(index + token.length).trim()
      if (field && expected)
        return { field, operator, value: expected }
    }
  }
  return null
}

function parseRuntimeComparison(value) {
  const text = String(value || '')
  let matched = null
  for (const operator of ['>=', '<=', '!=', '==', '=', '>', '<']) {
    const index = text.indexOf(operator)
    if (index <= 0 || (matched && index >= matched.index))
      continue
    matched = { index, operator }
  }
  if (!matched)
    return null
  const field = text.slice(0, matched.index).trim()
  if (!field)
    return null
  return {
    field,
    operator: matched.operator,
    value: text.slice(matched.index + matched.operator.length).trim(),
  }
}

function normalizeInputOptions(options) {
  if (!Array.isArray(options))
    return []
  return options.map((option) => {
    if (option && typeof option === 'object') {
      const value = Object.prototype.hasOwnProperty.call(option, 'value') ? option.value : option.label
      return { label: String(option.label ?? value ?? ''), value }
    }
    return { label: String(option ?? ''), value: option }
  })
}

function defaultPlaceholder(type, label) {
  return `${['select', 'date', 'datetime'].includes(type) ? '请选择' : '请输入'}${label}`
}

function readPath(source, path) {
  return String(path || '').split('.').filter(Boolean).reduce((value, key) => value?.[key], source)
}

function readIdentifier(record = {}) {
  return record?.id ?? record?.ID ?? ''
}

function isPresentIdentifier(value) {
  return value !== null && value !== undefined && String(value).trim() !== ''
}

function stableSerialize(value) {
  if (Array.isArray(value))
    return `[${value.map(stableSerialize).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}
