function parseSchema(source) {
  if (Array.isArray(source))
    return source
  if (source && typeof source === 'object')
    return source.rule || source.rules || source.schema || source.children || source
  if (typeof source === 'string' && source.trim()) {
    try {
      return parseSchema(JSON.parse(source))
    }
    catch {
      return []
    }
  }
  return []
}

export function formCreateToAiSchema(source) {
  const result = []
  const seen = new Set()
  collectFields(parseSchema(source), result, seen)
  return result
}

function collectFields(node, result, seen) {
  if (!node)
    return
  if (Array.isArray(node)) {
    node.forEach(item => collectFields(item, result, seen))
    return
  }
  if (typeof node !== 'object')
    return

  const field = resolveField(node)
  if (field && !seen.has(field) && !isLayoutRule(node)) {
    seen.add(field)
    result.push(toAiField(node, field))
    if (isArrayRule(node))
      return
  }

  const children = Array.isArray(node.children) ? node.children : []
  children.forEach(item => collectFields(item, result, seen))
  ;['rule', 'rules', 'columns'].forEach((key) => {
    if (Array.isArray(node[key]))
      node[key].forEach(item => collectFields(item, result, seen))
  })
}

function toAiField(rule, field) {
  const type = normalizeFieldType(rule)
  const props = normalizeProps(rule.props || {}, type)
  const options = normalizeOptions(rule.options || rule.props?.options)
  delete props.options
  const required = resolveRequired(rule)
  const rules = normalizeRules(rule, required)
  const arraySchema = type === 'array' ? normalizeArraySchema(rule) : null
  return {
    field,
    code: field,
    prop: field,
    label: resolveLabel(rule, field),
    type,
    required,
    span: normalizeSpan(rule),
    props,
    ...normalizeFieldAttrs(rule),
    ...(options.length ? { options } : {}),
    ...(rules.length ? { rules } : {}),
    ...(arraySchema || {}),
  }
}

function resolveField(rule = {}) {
  const field = text(rule.field)
    || text(rule.fieldCode)
    || text(rule.name)
    || text(rule.props?.field)
    || text(rule.props?.fieldCode)
    || text(rule.fieldBinding?.fieldCode)
    || text(rule._forge?.fieldBinding?.fieldCode)
  if (!field || field.startsWith('ref_'))
    return ''
  return field
}

function resolveLabel(rule = {}, fallback) {
  return text(rule.title)
    || text(rule.label)
    || text(rule.fieldName)
    || text(rule.props?.title)
    || text(rule.props?.label)
    || fallback
}

function normalizeFieldType(rule = {}) {
  const rawType = normalizeTypeName(rule.type || rule.component || rule.componentKey)
  const propType = normalizeTypeName(rule.props?.type)
  const fieldType = normalizeTypeName(rule.fieldType || rule.componentType)
  if (['group', 'tableform', 'subform', 'array'].includes(rawType))
    return 'array'
  if (['textarea'].includes(propType) || rawType === 'textarea')
    return 'textarea'
  if (['inputnumber', 'number', 'elinputnumber', 'ninputnumber'].includes(rawType) || fieldType === 'number')
    return 'number'
  if (['select', 'elselect', 'nselect', 'fcselect'].includes(rawType))
    return 'select'
  if (['radio', 'elradio', 'nradio'].includes(rawType))
    return 'radio'
  if (['checkbox', 'elcheckbox', 'ncheckbox', 'checkboxgroup'].includes(rawType))
    return 'checkbox'
  if (['switch', 'elswitch', 'nswitch'].includes(rawType))
    return 'switch'
  if (['datepicker', 'eldatepicker', 'ndatepicker', 'date'].includes(rawType)) {
    if (['datetime', 'datetimerange', 'daterange', 'month', 'year', 'time', 'timerange'].includes(propType))
      return propType
    return 'date'
  }
  if (['timepicker', 'eltimepicker', 'ntimepicker', 'time'].includes(rawType))
    return propType === 'timerange' ? 'timerange' : 'time'
  if (['upload', 'elupload', 'nupload'].includes(rawType))
    return 'upload'
  if (['fileupload'].includes(rawType))
    return 'fileUpload'
  if (['imageupload'].includes(rawType))
    return 'imageUpload'
  if (['cascader', 'elcascader', 'ncascader'].includes(rawType))
    return 'cascader'
  if (['treeselect', 'eltreeselect', 'ntreeselect'].includes(rawType))
    return 'treeSelect'
  if (['slider', 'elslider', 'nslider'].includes(rawType))
    return 'slider'
  if (['rate', 'elrate', 'nrate'].includes(rawType))
    return 'rate'
  if (['colorpicker', 'elcolorpicker', 'ncolorpicker'].includes(rawType))
    return 'color'
  return 'input'
}

function isArrayRule(rule = {}) {
  return normalizeFieldType(rule) === 'array'
}

function normalizeArraySchema(rule = {}) {
  const rawType = normalizeTypeName(rule.type || rule.component || rule.componentKey)
  const props = rule.props || {}
  const tableMode = rawType === 'tableform'
  const itemSchema = tableMode
    ? collectTableFormFields(props.columns || rule.columns)
    : collectArrayItemFields(props.rule || rule.rule || rule.children)
  const allowCreate = props.addable !== undefined
    ? props.addable !== false
    : props.button !== false
  const allowDelete = props.deletable !== undefined
    ? props.deletable !== false
    : props.button !== false
  return {
    itemSchema,
    arrayConfig: {
      displayMode: tableMode ? 'table' : (props.type === 'card' ? 'card' : 'list'),
      allowCreate,
      allowUpdate: props.disabled !== true,
      allowDelete,
      sortable: props.sortBtn === true,
      min: normalizeArrayLimit(props.min, 0),
      max: normalizeArrayLimit(props.max, 0),
      itemTitle: text(props.title) || '第{index}项',
      addText: text(props.addText) || '新增明细',
    },
    defaultValue: Array.isArray(rule.value)
      ? rule.value
      : (Array.isArray(props.defaultValue) ? props.defaultValue : []),
  }
}

function collectArrayItemFields(source) {
  const result = []
  collectFields(parseSchema(source), result, new Set())
  return result
}

function collectTableFormFields(columns) {
  if (!Array.isArray(columns))
    return []
  const result = []
  const seen = new Set()
  columns.forEach((column) => {
    const rules = Array.isArray(column?.rule) ? column.rule : []
    const before = result.length
    collectFields(rules, result, seen)
    if (result.length === before)
      return
    const columnLabel = text(column?.label)
    if (columnLabel && result.length === before + 1 && result[before]?.label === result[before]?.field)
      result[before].label = columnLabel
    const width = text(column?.style?.width)
    if (width) {
      for (let index = before; index < result.length; index++)
        result[index].columnWidth = width
    }
  })
  return result
}

function normalizeArrayLimit(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : fallback
}

function normalizeTypeName(value) {
  return text(value)
    .replace(/^el[-_]?/i, 'el')
    .replace(/^n[-_]?/i, 'n')
    .replace(/^fc[-_]?/i, 'fc')
    .replace(/[-_\s]/g, '')
    .toLowerCase()
}

function resolveRequired(rule = {}) {
  if (rule.required === true)
    return true
  const validate = Array.isArray(rule.validate) ? rule.validate : []
  const rules = Array.isArray(rule.rules) ? rule.rules : []
  return [...validate, ...rules].some(item => item?.required === true)
}

function normalizeSpan(rule = {}) {
  const span = Number(rule.span || rule.col?.span || rule.props?.span)
  if (!Number.isFinite(span) || span <= 0)
    return 1
  if (span >= 24)
    return 2
  if (span >= 12)
    return 1
  return 1
}

function normalizeFieldAttrs(rule = {}) {
  const props = rule.props || {}
  const result = {}
  ;[
    'placeholder',
    'clearable',
    'multiple',
    'filterable',
    'disabled',
    'readonly',
    'min',
    'max',
    'step',
    'precision',
    'rows',
    'maxlength',
    'showCount',
    'format',
    'valueFormat',
    'startPlaceholder',
    'endPlaceholder',
    'checkedValue',
    'uncheckedValue',
    'checkedText',
    'uncheckedText',
    'action',
    'headers',
    'data',
    'accept',
    'limit',
    'fileSize',
    'fileType',
    'showFileList',
    'uploadText',
  ].forEach((key) => {
    if (rule[key] !== undefined)
      result[key] = rule[key]
    else if (props[key] !== undefined)
      result[key] = props[key]
  })
  if (rule.value !== undefined)
    result.defaultValue = rule.value
  else if (props.defaultValue !== undefined)
    result.defaultValue = props.defaultValue
  return result
}

function normalizeProps(props = {}, type) {
  const next = { ...props }
  if (['select', 'radio', 'checkbox'].includes(type))
    delete next.type
  if (['date', 'datetime', 'daterange', 'datetimerange', 'month', 'year', 'time', 'timerange'].includes(type)) {
    if (next.type && normalizeTypeName(next.type) === normalizeTypeName(type))
      delete next.type
  }
  delete next.options
  delete next.field
  delete next.fieldCode
  return next
}

function normalizeRules(rule = {}, required) {
  const validate = Array.isArray(rule.validate) ? rule.validate : []
  const rules = Array.isArray(rule.rules) ? rule.rules : []
  const result = [...validate, ...rules]
    .filter(item => item && typeof item === 'object')
    .map(item => ({
      ...item,
      key: item.key || resolveField(rule),
    }))
  if (required && !result.some(item => item.required === true || item.__required === true)) {
    result.unshift({
      required: true,
      message: `请填写${resolveLabel(rule, resolveField(rule) || '该字段')}`,
      trigger: ['blur', 'change'],
    })
  }
  return result
}

function normalizeOptions(options) {
  if (!Array.isArray(options))
    return []
  return options
    .map((item) => {
      if (!item || typeof item !== 'object')
        return null
      return {
        ...item,
        label: item.label ?? item.name ?? item.title ?? item.value,
        value: item.value ?? item.id ?? item.key ?? item.label,
      }
    })
    .filter(item => item && item.value !== undefined && item.value !== null)
}

function isLayoutRule(rule = {}) {
  const type = text(rule.type || rule.component || rule.componentKey).toLowerCase()
  return [
    'row',
    'col',
    'fcrow',
    'fccol',
    'elrow',
    'elcol',
    'grid',
    'tabs',
    'eltabs',
    'tabpane',
    'eltabpane',
    'collapse',
    'elcollapse',
    'collapseitem',
    'card',
    'elcard',
    'divider',
    'eldivider',
  ].includes(type)
}

function text(value) {
  if (value === undefined || value === null)
    return ''
  return String(value).trim()
}
