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
