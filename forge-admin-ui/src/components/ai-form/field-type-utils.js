const NUMBER_FIELD_TYPES = new Set([
  'number',
  'inputnumber',
  'input-number',
  'integer',
  'money',
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
