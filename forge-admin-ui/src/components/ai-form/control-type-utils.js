/**
 * 运行态控件类型归一：与后端 BusinessFlowService#normalizeTaskFormFieldType 对齐。
 * 审批 uiDocument 常见 type=input + componentKey=真实控件，必须按强类型解析。
 */

const WEAK_CONTROL_TYPES = new Set([
  '',
  'input',
  'text',
  'string',
  'varchar',
])

const NUMBER_CONTROL_TYPES = new Set([
  'number',
  'inputnumber',
  'input-number',
  'integer',
  'money',
  'decimal',
])

const USER_SELECT_TYPES = new Set([
  'userselect',
  'userpicker',
  'user',
  'username',
  'sysuserselect',
  'forgeuserselect',
])

const ORG_SELECT_TYPES = new Set([
  'orgtreeselect',
  'orgselect',
  'organizationselect',
  'departmentselect',
  'departmenttreeselect',
  'deptselect',
  'depttreeselect',
  'eltreeselect',
  'orgname',
  'deptname',
  'forgeorgtreeselect',
])

const DICT_SELECT_TYPES = new Set([
  'dictselect',
  'forgedictselect',
])

const UPLOAD_TYPES = new Set([
  'upload',
  'fileupload',
])

/**
 * @param {unknown} type
 * @returns {boolean}
 */
export function isWeakControlType(type) {
  return WEAK_CONTROL_TYPES.has(String(type || '').trim().toLowerCase())
}

/**
 * 去掉 forgeXxx 前缀（forgeUserSelect → userSelect）。
 * @param {unknown} type
 * @returns {string}
 */
export function stripForgeComponentPrefix(type) {
  const value = String(type || '').trim()
  if (value.startsWith('forge') && value.length > 5 && value.charAt(5) === value.charAt(5).toUpperCase())
    return value.charAt(5).toLowerCase() + value.slice(6)
  return value
}

/**
 * 将设计器 / 发布态别名归一为 AiFormItem 可识别的标准 type。
 * @param {unknown} componentType
 * @returns {string}
 */
export function normalizeRuntimeControlType(componentType) {
  const raw = stripForgeComponentPrefix(componentType)
  const lower = raw.toLowerCase()
  if (!raw)
    return 'input'
  if (NUMBER_CONTROL_TYPES.has(lower))
    return 'number'
  if (DICT_SELECT_TYPES.has(lower))
    return 'dictSelect'
  if (USER_SELECT_TYPES.has(lower))
    return 'userSelect'
  if (ORG_SELECT_TYPES.has(lower))
    return 'orgTreeSelect'
  if (UPLOAD_TYPES.has(lower))
    return 'fileUpload'
  if (lower === 'imageupload')
    return 'imageUpload'
  if (lower === 'radiobutton')
    return 'radioButton'
  if (lower === 'objectreference')
    return 'objectReference'
  if (lower === 'recordselector')
    return 'recordSelector'
  if (lower === 'regiontreeselect')
    return 'regionTreeSelect'
  if (lower === 'treeselect')
    return 'treeSelect'
  if (lower === 'customselect')
    return 'customSelect'
  // 保留常见驼峰标准名
  const passthrough = new Set([
    'textarea', 'select', 'radio', 'checkbox', 'switch',
    'date', 'datetime', 'daterange', 'datetimerange',
    'month', 'year', 'time', 'timerange',
    'slider', 'rate', 'color', 'cascader', 'transfer',
    'text', 'slot', 'array', 'barcodeScanner',
  ])
  if (passthrough.has(raw))
    return raw
  if (passthrough.has(lower))
    return lower
  return isWeakControlType(raw) ? 'input' : raw
}

/**
 * 从 field 的 type / componentType / componentKey 中取最强控件身份并归一。
 * @param {object} field
 * @returns {string}
 */
export function resolveFieldControlType(field = {}) {
  const candidates = [
    field?.componentKey,
    field?.componentType,
    field?.type,
    field?.props?.type,
  ]
  let weakFallback = ''
  for (const candidate of candidates) {
    const raw = String(candidate || '').trim()
    if (!raw)
      continue
    if (isWeakControlType(raw)) {
      if (!weakFallback)
        weakFallback = 'input'
      continue
    }
    return normalizeRuntimeControlType(raw)
  }
  return weakFallback || 'input'
}

/**
 * uiDocument 节点：type 弱、componentKey 强时优选取 componentKey。
 * @param {object} node
 * @returns {string}
 */
export function resolveDocumentNodeControlType(node = {}) {
  return resolveFieldControlType({
    type: node.type,
    componentType: node.componentType,
    componentKey: node.componentKey,
  })
}
