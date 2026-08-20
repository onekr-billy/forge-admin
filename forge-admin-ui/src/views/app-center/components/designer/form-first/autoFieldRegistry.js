import { resolveFieldComponentDefaults } from './fieldComponentCatalog'
import {
  camelToSnake,
  isFieldComponent,
  normalizeFormDesignerSchema,
} from './formDesignerSchema'

export function buildAutoFieldAssets(schema = {}, existingFields = []) {
  const normalized = normalizeFormDesignerSchema(schema)
  const existing = cloneFields(existingFields)
  const existingCodes = new Set(existing.map(field => field.fieldCode || field.field).filter(Boolean))
  const createdFields = []

  walkComponents(normalized.components, (component, index) => {
    if (!shouldCreateField(component, existingCodes))
      return
    const field = createFieldFromComponent(component, index)
    existing.push(field)
    existingCodes.add(field.fieldCode)
    createdFields.push(field)
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

function shouldCreateField(component = {}, existingCodes) {
  if (!isFieldComponent(component))
    return false
  const binding = component.fieldBinding || {}
  if (binding.mode === 'virtual' || !binding.fieldCode)
    return false
  return binding.createIfMissing !== false && !existingCodes.has(binding.fieldCode)
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
