import { normalizeMobileComponentType } from '../components/lowcode/mobile-component-registry.js'

/**
 * PC 低代码设计器字段属性的移动端兼容清单。
 *
 * native: 移动端控件直接支持；adapted: 按触屏交互做等价适配；
 * retained: 协议保留，但只在对应平台/展示态生效。
 * 契约测试会读取 PC spec，保证 PC 后续新增属性时这里必须同步声明。
 */
export const MOBILE_FIELD_PROPERTY_SUPPORT = Object.freeze({
  input: support({
    native: ['placeholder', 'clearable', 'maxLength', 'showCount', 'disabled', 'readonly', 'autofocus', 'prefix', 'suffix'],
    adapted: ['size', 'status', 'round', 'pair', 'passivelyTriggered', 'loading', 'inputProps'],
  }),
  barcodeScanner: support({ native: ['placeholder', 'disabled', 'clearable'], adapted: ['scanFormat', 'continuousScan'] }),
  textarea: support({
    native: ['placeholder', 'rows', 'maxLength', 'autosize', 'showCount', 'disabled', 'readonly', 'autofocus'],
    adapted: ['status', 'round', 'passivelyTriggered'],
  }),
  number: support({
    native: ['placeholder', 'min', 'max', 'step', 'precision', 'disabled', 'readonly', 'clearable', 'autofocus', 'prefix', 'suffix'],
    adapted: ['size', 'showButton', 'buttonPlacement', 'status', 'loading', 'keyboard'],
  }),
  money: support({
    native: ['precision', 'currencySymbol', 'placeholder', 'disabled', 'clearable'],
    adapted: ['showChinese', 'size', 'status', 'loading'],
  }),
  slider: support({
    native: ['min', 'max', 'step', 'range', 'disabled', 'reverse'],
    adapted: ['vertical', 'showTooltip', 'marks', 'tooltip', 'keyboard'],
  }),
  rate: support({
    native: ['count', 'allowHalf', 'clearable', 'disabled', 'readonly', 'color'],
    adapted: ['size', 'texts'],
  }),
  color: support({
    native: ['clearable', 'disabled', 'showPreview'],
    adapted: ['modes', 'showAlpha', 'size', 'swatches', 'renderable'],
  }),
  select: selectionSupport(['options', 'clearable', 'filterable', 'multiple', 'placeholder', 'size', 'status', 'disabled', 'maxTagCount', 'tag', 'virtualScroll', 'loading', 'clearFilterAfterSelect']),
  dictSelect: selectionSupport(['dictType', 'clearable', 'filterable', 'multiple', 'placeholder', 'size', 'status', 'disabled', 'maxTagCount', 'tag', 'virtualScroll', 'loading']),
  radio: selectionSupport(['options', 'size', 'direction', 'disabled', 'name']),
  radioButton: selectionSupport(['options', 'size', 'direction', 'disabled', 'name']),
  checkbox: selectionSupport(['options', 'min', 'max', 'size', 'direction', 'disabled', 'maxTagCount']),
  transfer: selectionSupport(['sourceTitle', 'targetTitle', 'filterable', 'showSelected', 'size', 'disabled', 'virtualScroll', 'selectAllText', 'clearText', 'clearable']),
  cascader: selectionSupport(['options', 'checkStrategy', 'filterable', 'clearable', 'placeholder', 'multiple', 'size', 'status', 'disabled', 'separator', 'cascade', 'leafOnly', 'maxTagCount', 'virtualScroll', 'loading', 'expandTrigger']),
  treeSelect: selectionSupport(['options', 'multiple', 'checkStrategy', 'filterable', 'clearable', 'placeholder', 'size', 'status', 'disabled', 'defaultExpandAll', 'cascade', 'leafOnly', 'maxTagCount', 'virtualScroll', 'loading', 'checkable', 'remote']),
  customSelect: selectionSupport(['api', 'method', 'labelField', 'valueField', 'clearable', 'filterable', 'multiple', 'placeholder', 'size', 'status', 'disabled', 'paramsText', 'cache', 'maxTagCount', 'virtualScroll', 'loading']),
  date: dateSupport(['format', 'placeholder', 'clearable', 'size', 'status', 'disabled', 'shortcuts', 'firstDayOfWeek', 'actions', 'defaultTime']),
  datetime: dateSupport(['format', 'placeholder', 'clearable', 'size', 'status', 'disabled', 'shortcuts', 'defaultTime', 'timePickerFormat', 'actions']),
  daterange: dateSupport(['format', 'startPlaceholder', 'endPlaceholder', 'clearable', 'size', 'status', 'disabled', 'shortcuts', 'firstDayOfWeek', 'actions']),
  datetimerange: dateSupport(['format', 'startPlaceholder', 'endPlaceholder', 'clearable', 'size', 'status', 'disabled', 'shortcuts', 'defaultTime', 'actions']),
  month: dateSupport(['format', 'placeholder', 'clearable', 'size', 'status', 'disabled', 'actions']),
  year: dateSupport(['format', 'placeholder', 'clearable', 'size', 'status', 'disabled']),
  timerange: dateSupport(['format', 'startPlaceholder', 'endPlaceholder', 'clearable', 'size', 'status', 'disabled', 'use12Hours']),
  switch: support({
    native: ['checkedText', 'uncheckedText', 'defaultValue', 'checkedValue', 'uncheckedValue', 'disabled'],
    adapted: ['size', 'round', 'loading', 'rubberBand'],
  }),
  userSelect: selectionSupport(['multiple', 'clearable', 'placeholder', 'filterable', 'size', 'status', 'disabled', 'deptFilter', 'maxTagCount']),
  orgTreeSelect: selectionSupport(['multiple', 'clearable', 'placeholder', 'filterable', 'size', 'status', 'disabled', 'maxLevel', 'cascade', 'checkStrategy']),
  regionTreeSelect: selectionSupport(['level', 'filterable', 'clearable', 'placeholder', 'multiple', 'size', 'status', 'disabled', 'cascade', 'leafOnly']),
  objectReference: selectionSupport(['targetObject', 'displayField', 'multiple', 'clearable', 'placeholder', 'size', 'disabled', 'modalColumns']),
  recordSelector: selectionSupport(['targetObject', 'multiple', 'clearable', 'placeholder', 'filterable', 'size', 'disabled', 'modalColumns', 'maxTagCount']),
  fileUpload: uploadSupport(['maxCount', 'maxSize', 'accept', 'multiple', 'listType', 'disabled', 'showRemoveButton', 'storageType', 'showDownloadButton']),
  imageUpload: uploadSupport(['maxCount', 'maxSize', 'accept', 'multiple', 'listType', 'disabled', 'showRemoveButton', 'crop', 'watermark', 'showDownloadButton']),
  text: support({
    native: ['template', 'prefix', 'suffix', 'ellipsis', 'strong', 'italic', 'underline', 'delete', 'code', 'type'],
    adapted: ['tooltip', 'copyable'],
  }),
})

export const MOBILE_LAYOUT_PROPERTY_SUPPORT = Object.freeze({
  grid: layoutSupport(['columns', 'gutter', 'rowGap', 'cellMinHeight', 'alignItems', 'justifyItems', 'showCellBorder', 'cellBackground']),
  table: layoutSupport(['rows', 'cols', 'bordered', 'cellPadding', 'striped', 'size']),
  card: layoutSupport(['title', 'size', 'bordered', 'embedded', 'hoverable', 'collapsible', 'segmented']),
  tabs: layoutSupport(['type', 'placement', 'size', 'trigger', 'animated', 'closable', 'addable', 'tabsPadding', 'justifyContent']),
  collapse: layoutSupport(['accordion', 'defaultExpandedNames', 'arrowPlacement', 'displayDirective']),
  box: layoutSupport(['direction', 'justifyContent', 'alignItems', 'gap', 'wrap']),
  divider: layoutSupport(['direction', 'dashed', 'titlePlacement', 'vertical']),
  spacer: layoutSupport(['height', 'backgroundColor']),
  space: layoutSupport(['direction', 'size', 'align', 'justify', 'wrap']),
  groupTitle: layoutSupport(['title', 'description', 'badge', 'size', 'type', 'depth']),
  formSectionTitle: layoutSupport(['title', 'description', 'depth']),
  tabPane: layoutSupport(['label', 'name', 'disabled', 'closable', 'displayDirective']),
  collapseItem: layoutSupport(['title', 'name', 'disabled', 'displayDirective']),
  col: layoutSupport(['span', 'offset', 'push', 'pull']),
  tableCell: layoutSupport(['span', 'rowspan', 'bordered']),
})

function support(groups = {}) {
  return Object.freeze(Object.entries(groups).reduce((result, [mode, names]) => {
    names.forEach(name => { result[name] = mode })
    return result
  }, {}))
}

function selectionSupport(names) {
  const native = new Set(['options', 'dictType', 'clearable', 'filterable', 'multiple', 'placeholder', 'disabled', 'min', 'max', 'direction', 'name', 'api', 'method', 'labelField', 'valueField', 'targetObject'])
  return support({
    native: names.filter(name => native.has(name)),
    adapted: names.filter(name => !native.has(name)),
  })
}

function dateSupport(names) {
  const native = new Set(['format', 'placeholder', 'startPlaceholder', 'endPlaceholder', 'clearable', 'disabled', 'defaultTime'])
  return support({ native: names.filter(name => native.has(name)), adapted: names.filter(name => !native.has(name)) })
}

function uploadSupport(names) {
  const native = new Set(['maxCount', 'maxSize', 'accept', 'multiple', 'disabled', 'showRemoveButton', 'crop'])
  return support({ native: names.filter(name => native.has(name)), adapted: names.filter(name => !native.has(name)) })
}

function layoutSupport(names) {
  return support({ adapted: names })
}

export function normalizeMobileFieldContract(field = {}) {
  const rawProps = isObject(field.props) ? field.props : {}
  const type = resolveContractType(field)
  const validation = {
    ...(isObject(rawProps.validation) ? rawProps.validation : {}),
    ...(isObject(field.validation) ? field.validation : {}),
  }
  const normalizedProps = compactUndefined({
    ...rawProps,
    placeholder: firstDefined(field.placeholder, rawProps.placeholder),
    clearable: firstDefined(field.clearable, rawProps.clearable),
    maxLength: firstDefined(field.maxLength, field.maxlength, rawProps.maxLength, rawProps.maxlength),
    showCount: firstDefined(field.showCount, rawProps.showCount, rawProps.showWordLimit),
    rows: firstDefined(field.rows, rawProps.rows),
    autosize: firstDefined(field.autosize, rawProps.autosize, rawProps.autoHeight),
    min: firstDefined(field.min, rawProps.min),
    max: firstDefined(field.max, rawProps.max),
    step: firstDefined(field.step, rawProps.step),
    precision: firstDefined(field.precision, rawProps.precision),
    multiple: firstDefined(field.multiple, rawProps.multiple),
    filterable: firstDefined(field.filterable, rawProps.filterable),
    disabled: firstDefined(field.disabled, rawProps.disabled),
    readonly: firstDefined(field.readonly, rawProps.readonly),
    format: firstDefined(field.format, rawProps.format),
    valueFormat: firstDefined(field.valueFormat, rawProps.valueFormat, rawProps.format),
    startPlaceholder: firstDefined(field.startPlaceholder, rawProps.startPlaceholder),
    endPlaceholder: firstDefined(field.endPlaceholder, rawProps.endPlaceholder),
    checkedValue: type === 'switch' ? firstDefined(field.checkedValue, rawProps.checkedValue, true) : firstDefined(field.checkedValue, rawProps.checkedValue),
    uncheckedValue: type === 'switch' ? firstDefined(field.uncheckedValue, rawProps.uncheckedValue, false) : firstDefined(field.uncheckedValue, rawProps.uncheckedValue),
    maxCount: firstDefined(field.maxCount, field.limit, rawProps.maxCount, rawProps.limit),
    maxSize: firstDefined(field.maxSize, rawProps.maxSize),
    accept: firstDefined(field.accept, rawProps.accept),
  })
  return {
    ...field,
    type,
    props: normalizedProps,
    validation,
    rules: collectFieldRules(field, validation),
    required: field.required === true || validation.required === true,
    requiredMessage: field.requiredMessage || validation.requiredMessage,
    readonly: field.readonly === true || rawProps.readonly === true,
    disabled: field.disabled === true || rawProps.disabled === true,
    defaultValue: firstDefined(field.defaultValue, rawProps.defaultValue),
  }
}

function resolveContractType(field) {
  const candidates = [field.componentKey, field.type, field.componentType]
    .map(normalizeMobileComponentType)
    .filter(Boolean)
  const first = candidates[0]
  const weak = new Set(['input', 'text', 'string'])
  if (weak.has(first)) return candidates.find(type => !weak.has(type)) || first || 'input'
  if (first === 'date') {
    const subtype = candidates.find(type => ['datetime', 'daterange', 'datetimerange', 'month', 'year', 'time', 'timerange'].includes(type))
    if (subtype) return subtype
  }
  if (first === 'number' && candidates.includes('money')) return 'money'
  return first || 'input'
}

export function validateMobileFieldValue(field = {}, value) {
  const normalized = normalizeMobileFieldContract(field)
  const rules = normalized.rules.length ? normalized.rules : [{}]
  if (normalized.required && !rules.some(rule => rule.required === true))
    rules.unshift({ required: true, message: normalized.requiredMessage })

  for (const rule of rules) {
    if (!rule || rule.enabled === false) continue
    const empty = isEmptyValue(value)
    if (rule.required === true && empty)
      return rule.message || normalized.requiredMessage || requiredMessage(normalized)
    if (empty) continue

    const type = String(rule.type || normalized.type || '').toLowerCase()
    const length = Array.isArray(value) ? value.length : String(value).length
    const number = Number(value)
    if (rule.len != null && length !== Number(rule.len))
      return rule.message || `${normalized.label || '该字段'}长度必须为 ${rule.len}`
    if (rule.minLength != null && length < Number(rule.minLength))
      return rule.message || `${normalized.label || '该字段'}长度不能少于 ${rule.minLength}`
    if (rule.maxLength != null && length > Number(rule.maxLength))
      return rule.message || `${normalized.label || '该字段'}长度不能超过 ${rule.maxLength}`
    if (rule.min != null) {
      const actual = isNumericType(type, normalized.type) ? number : length
      if (Number.isFinite(actual) && actual < Number(rule.min))
        return rule.message || `${normalized.label || '该字段'}不能小于 ${rule.min}`
    }
    if (rule.max != null) {
      const actual = isNumericType(type, normalized.type) ? number : length
      if (Number.isFinite(actual) && actual > Number(rule.max))
        return rule.message || `${normalized.label || '该字段'}不能大于 ${rule.max}`
    }
    if (rule.pattern) {
      const pattern = toRegExp(rule.pattern)
      if (pattern && !pattern.test(String(value)))
        return rule.message || `${normalized.label || '该字段'}格式不正确`
    }
    if (Array.isArray(rule.enum) && !rule.enum.map(String).includes(String(value)))
      return rule.message || `${normalized.label || '该字段'}不在允许范围内`
  }
  return ''
}

export function formatMobileFieldValue(field = {}, value, options = []) {
  const normalized = normalizeMobileFieldContract(field)
  const props = normalized.props
  if (isEmptyValue(value)) return '-'
  const values = Array.isArray(value) ? value : String(value).split(',').map(item => item.trim()).filter(Boolean)
  if (isSelectionType(normalized.type) && values.length) {
    const labels = values.map(item => options.find(option => String(option?.value) === String(item))?.label || item)
    return labels.join(props.separator || '、')
  }
  if (normalized.type === 'switch')
    return valuesEqual(value, props.checkedValue) ? (props.checkedText || '是') : (props.uncheckedText || '否')
  if (['money', 'number'].includes(normalized.type) && Number.isFinite(Number(value))) {
    const precision = numberOrUndefined(props.precision)
    const formatted = precision == null ? String(value) : Number(value).toFixed(precision)
    return `${props.prefix ?? (normalized.type === 'money' ? props.currencySymbol || '' : '')}${formatted}${props.suffix || ''}`
  }
  if (normalized.type === 'text') {
    const base = props.template ? String(props.template).replace(/\{(?:field|value)\}/g, String(value)) : String(value)
    return `${props.prefix || ''}${base}${props.suffix || ''}`
  }
  if (Array.isArray(value)) return value.join(' 至 ')
  if (typeof value === 'object') {
    try { return JSON.stringify(value) }
    catch { return '[复杂数据]' }
  }
  return `${props.prefix || ''}${String(value)}${props.suffix || ''}`
}

export function mobileFieldValueEquals(left, right) {
  return valuesEqual(left, right)
}

function collectFieldRules(field, validation) {
  const rules = []
  const add = source => {
    if (Array.isArray(source)) rules.push(...source.filter(isObject))
    else if (isObject(source)) rules.push(source)
  }
  add(field.rules)
  add(validation.rules)
  const inline = {}
  for (const key of ['required', 'message', 'type', 'len', 'min', 'max', 'minLength', 'maxLength', 'pattern', 'enum', 'trigger']) {
    if (validation[key] !== undefined) inline[key] = validation[key]
  }
  if (Object.keys(inline).length) rules.push(inline)
  return rules
}

function requiredMessage(field) {
  const inputTypes = ['input', 'textarea', 'number', 'money', 'barcodeScanner']
  return `请${inputTypes.includes(field.type) ? '输入' : '选择'}${field.label || '该字段'}`
}

function toRegExp(value) {
  if (value instanceof RegExp) return value
  const text = String(value || '')
  try {
    const match = text.match(/^\/(.*)\/([dgimsuvy]*)$/)
    return match ? new RegExp(match[1], match[2]) : new RegExp(text)
  }
  catch { return null }
}

function isNumericType(ruleType, fieldType) {
  return ['number', 'integer', 'float'].includes(ruleType) || ['number', 'money', 'slider', 'rate'].includes(fieldType)
}

function isSelectionType(type) {
  return ['select', 'dictSelect', 'radio', 'radioButton', 'checkbox', 'transfer', 'cascader', 'treeSelect', 'customSelect', 'userSelect', 'orgTreeSelect', 'regionTreeSelect', 'objectReference', 'recordSelector'].includes(type)
}

function isEmptyValue(value) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

function valuesEqual(left, right) {
  return left === right || String(left) === String(right)
}

function numberOrUndefined(value) {
  const result = Number(value)
  return Number.isFinite(result) ? result : undefined
}

function firstDefined(...values) {
  return values.find(value => value !== undefined && value !== null)
}

function isObject(value) {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value))
}

function compactUndefined(source) {
  return Object.fromEntries(Object.entries(source).filter(([_key, value]) => value !== undefined))
}
