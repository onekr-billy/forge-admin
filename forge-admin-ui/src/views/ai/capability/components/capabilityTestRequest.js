const unsafe = new Set(['__proto__', 'prototype', 'constructor'])
export function requestObject(text) {
  let value
  try {
    value = JSON.parse(text)
  }
  catch { throw new Error('JSON 格式不正确，请检查引号、逗号和括号') }
  if (!value || Array.isArray(value) || typeof value !== 'object')
    throw new Error('请求参数必须是 JSON 对象')
  return value
}

function pathParts(field) {
  const parts = field.path?.startsWith('$.') ? field.path.slice(2).split('.') : []
  return parts.length && parts.every(part => /^[\w-]+$/.test(part) && !unsafe.has(part)) && parts.at(-1) === field.fieldCode ? parts : null
}

export function editableRequestFields(guide) {
  const fields = guide?.requestFields || []
  if (!fields.length || fields.some(field => !pathParts(field) || !/^(?:string|integer|number|boolean|object)(?:\(.*\))?$/.test(field.type)))
    return null
  return fields.filter(field => !field.type.startsWith('object')).map(field => ({ ...field, parts: pathParts(field), baseType: field.type.split('(')[0] }))
}

export function requestFieldValue(body, field) {
  return field.parts.reduce((value, key) => value?.[key], body)
}

export function updateRequestField(text, field, value) {
  const body = requestObject(text)
  const parts = pathParts(field)
  if (!parts)
    throw new Error('此字段路径请在 JSON 模式中填写')
  let current = body
  parts.slice(0, -1).forEach((key) => {
    if (current[key] == null)
      current[key] = {}
    if (typeof current[key] !== 'object' || Array.isArray(current[key]))
      throw new Error('当前父字段不是对象，请先在 JSON 中修正')
    current = current[key]
  })
  if (value === null || value === undefined)
    delete current[parts.at(-1)]
  else
    current[parts.at(-1)] = value
  return JSON.stringify(body, null, 2)
}

export function requestValidationError(guide, text) {
  try {
    const body = requestObject(text)
    for (const field of guide?.requestFields || []) {
      const parts = pathParts(field)
      if (!parts)
        continue // Unknown schemas are checked by the server; never guess a path.
      const parent = parts.slice(0, -1).reduce((value, key) => value?.[key], body)
      const value = parent?.[parts.at(-1)]
      if (parent == null)
        continue // Optional object not submitted.
      const label = field.fieldLabel || field.fieldCode
      if (field.required && (value === undefined || value === null || value === ''))
        return `请填写「${label}」`
      if (value === undefined || value === null)
        continue
      const type = field.type?.split('(')[0]
      if (type === 'integer' && (!Number.isSafeInteger(value)))
        return `「${label}」必须是安全范围内的整数；长 ID 请按字符串契约传入`
      if (type === 'number' && (typeof value !== 'number' || !Number.isFinite(value)))
        return `「${label}」必须是数字`
      if ((type === 'string' && typeof value !== 'string') || (type === 'boolean' && typeof value !== 'boolean'))
        return `「${label}」类型应为 ${type}`
      if (type === 'object' && (typeof value !== 'object' || Array.isArray(value)))
        return `「${label}」必须是对象`
      if (type === 'array' && !Array.isArray(value))
        return `「${label}」必须是数组`
    }
    return ''
  }
  catch (error) { return error.message }
}
