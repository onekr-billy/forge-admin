import { resolveFieldComponentDefaults } from './fieldComponentCatalog'
import {
  camelToSnake,
  isFieldComponent,
  normalizeFormDesignerSchema,
} from './formDesignerSchema'

export function buildAutoFieldAssets(schema = {}, existingFields = []) {
  const normalized = normalizeFormDesignerSchema(schema)
  const existing = cloneFields(existingFields)
  const existingByCode = new Map(existing
    .map(field => [String(field.fieldCode || field.field || '').trim(), field])
    .filter(([code]) => code))
  const createdFields = []

  walkComponents(normalized.components, (component, index) => {
    if (!isBoundFieldComponent(component))
      return
    const fieldCode = String(component.fieldBinding?.fieldCode || '').trim()
    const current = existingByCode.get(fieldCode)
    if (!current) {
      const field = createFieldFromComponent(component, index)
      existing.push(field)
      existingByCode.set(fieldCode, field)
      createdFields.push(field)
      return
    }
    // 已存在字段也要把表单组件上的选项源/控件类型回写进 basicProps，
    // 否则明细页下拉正常、被其它页当子表引用时丢失 optionSource。
    syncExistingFieldFromComponent(current, component)
  })

  return {
    fields: existing,
    createdFields,
  }
}

export function createFieldFromComponent(component = {}, index = 0) {
  const binding = component.fieldBinding || {}
  const fieldCode = binding.fieldCode || ''
  const defaults = resolveFieldComponentDefaults(component.componentKey)
  const props = component.props || {}
  const basicProps = {
    ...props,
    fieldBinding: {
      mode: 'field',
      fieldCode,
      columnName: binding.columnName || camelToSnake(fieldCode),
      createIfMissing: true,
      source: 'designer',
      locked: false,
      ...(binding || {}),
    },
  }
  if (props.placeholder)
    basicProps.placeholder = props.placeholder

  return {
    fieldName: component.label || fieldCode || '字段',
    fieldCode,
    columnName: binding.columnName || camelToSnake(fieldCode),
    fieldType: defaults.fieldType,
    dataType: defaults.dataType,
    length: defaults.length,
    precision: defaults.precision,
    required: Boolean(component.validation?.required),
    defaultValue: props.defaultValue ?? null,
    searchable: false,
    listVisible: true,
    formVisible: component.visibility?.hidden !== true,
    importable: true,
    exportable: true,
    componentType: defaults.componentType,
    queryType: defaults.queryType,
    dictType: props.dictType || '',
    sensitiveType: '',
    encryptAlgorithm: '',
    sortable: false,
    systemField: false,
    readonly: Boolean(component.visibility?.readonly),
    fieldStatus: 'ENABLED',
    referenceObjectCode: props.referenceObjectCode || '',
    referenceDisplayField: props.referenceDisplayField || '',
    placeholder: props.placeholder || '',
    remark: component.label || '',
    sortOrder: Number(component.props?.sortOrder ?? component.layout?.order ?? index + 1),
    fieldBinding: basicProps.fieldBinding,
    formulaConfig: props.formulaConfig ?? component.advancedProps?.formulaConfig ?? null,
    basicProps,
    advancedProps: {
      ...(component.advancedProps || {}),
    },
  }
}

function isBoundFieldComponent(component = {}) {
  if (!isFieldComponent(component))
    return false
  const binding = component.fieldBinding || {}
  return binding.mode !== 'virtual' && Boolean(binding.fieldCode)
}

/** 把表单设计器组件上的运行态关键配置镜像到字段注册表 */
function syncExistingFieldFromComponent(field = {}, component = {}) {
  const props = component.props && typeof component.props === 'object' ? component.props : {}
  const defaults = resolveFieldComponentDefaults(component.componentKey)
  const basicProps = {
    ...(field.basicProps && typeof field.basicProps === 'object' ? field.basicProps : {}),
  }
  const syncKeys = [
    'optionSource',
    'options',
    'dictType',
    'labelValueField',
    'fieldMappings',
    'mappings',
    'cascade',
    'cascadeConfig',
    'referenceObjectCode',
    'referenceDisplayField',
    'referenceValueField',
    'recordSelector',
    'checkedValue',
    'uncheckedValue',
    'runtimeRules',
    'multiple',
    'clearable',
    'filterable',
    'placeholder',
    'defaultValue',
  ]
  for (const key of syncKeys) {
    if (props[key] !== undefined)
      basicProps[key] = props[key]
  }
  if (component.advancedProps && typeof component.advancedProps === 'object') {
    field.advancedProps = {
      ...(field.advancedProps || {}),
      ...component.advancedProps,
    }
  }
  if (defaults?.componentType)
    field.componentType = defaults.componentType
  else if (component.componentKey)
    field.componentType = component.componentKey
  if (props.dictType !== undefined)
    field.dictType = props.dictType || ''
  if (props.referenceObjectCode !== undefined)
    field.referenceObjectCode = props.referenceObjectCode || ''
  if (props.referenceDisplayField !== undefined)
    field.referenceDisplayField = props.referenceDisplayField || ''
  if (props.defaultValue !== undefined)
    field.defaultValue = props.defaultValue
  if (props.placeholder !== undefined)
    field.placeholder = props.placeholder || ''
  if (component.label)
    field.fieldName = component.label
  if (component.validation?.required !== undefined)
    field.required = Boolean(component.validation.required)
  if (component.visibility?.readonly !== undefined)
    field.readonly = Boolean(component.visibility.readonly)
  if (component.visibility?.hidden !== undefined)
    field.formVisible = component.visibility.hidden !== true
  if (props.formulaConfig !== undefined || component.advancedProps?.formulaConfig !== undefined) {
    field.formulaConfig = props.formulaConfig ?? component.advancedProps?.formulaConfig ?? null
  }
  field.basicProps = basicProps
}

function walkComponents(components = [], visitor) {
  ;(Array.isArray(components) ? components : []).forEach((component, index) => {
    if (!component || typeof component !== 'object')
      return
    visitor(component, index)
    if (Array.isArray(component.children))
      walkComponents(component.children, visitor)
  })
}

function cloneFields(fields = []) {
  return JSON.parse(JSON.stringify(Array.isArray(fields) ? fields : []))
}
