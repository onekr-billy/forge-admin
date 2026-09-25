const NUMBER_FIELD_TYPES = new Set([
  'number',
  'inputnumber',
  'input-number',
  'integer',
  'money',
  'decimal',
])

/**
 * 判断 AiForm 字段是否属于数字输入类型。
 *
 * `number` 是页面 Schema 的标准写法；其余值用于兼容历史配置、业务字段和外部 Schema。
 *
 * @param {unknown} type 字段类型
 * @returns {boolean} 是否为数字输入
 */
export function isNumberFieldType(type) {
  return NUMBER_FIELD_TYPES.has(String(type || '').trim().toLowerCase())
}

/**
 * 从 field / componentType / componentKey 中解析数字控件身份。
 * 审批 uiDocument 偶发 type=input 但 componentKey=money，不能只看 type。
 */
export function resolveNumberFieldType(field = {}) {
  const candidates = [
    field?.type,
    field?.componentType,
    field?.componentKey,
    field?.props?.type,
  ]
  for (const candidate of candidates) {
    if (isNumberFieldType(candidate))
      return String(candidate || '').trim().toLowerCase()
  }
  return ''
}

export function isNumberLikeField(field = {}) {
  return Boolean(resolveNumberFieldType(field))
}

/**
 * Naive n-input-number 只接受 number | null；接口 BigDecimal / 分转元后偶发字符串，
 * 直接绑定会空白，审批详情表现为「金额没渲染」。
 */
export function coerceNumberFieldValue(value) {
  if (value === null || value === undefined || value === '')
    return null
  if (typeof value === 'number')
    return Number.isFinite(value) ? value : null
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed)
      return null
    const parsed = Number(trimmed)
    return Number.isFinite(parsed) ? parsed : null
  }
  if (typeof value === 'bigint')
    return Number(value)
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 运行态统一数字控件 type，避免 money/inputNumber 落到文本输入 */
export function normalizeNumberFieldType(type) {
  return isNumberFieldType(type) ? 'number' : String(type || '').trim()
}

/**
 * 判断字段是否使用“请输入”语义。
 *
 * @param {unknown} type 字段类型
 * @returns {boolean} 是否为文本或数字输入
 */
export function isInputLikeFieldType(type) {
  const normalizedType = String(type || '').trim().toLowerCase()
  return normalizedType === 'input'
    || normalizedType === 'textarea'
    || isNumberFieldType(type)
}
