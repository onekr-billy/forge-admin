import { buildAutoFieldAssets } from '@/views/app-center/components/designer/form-first/autoFieldRegistry'
import { normalizeFormDesignerSchema } from '@/views/app-center/components/designer/form-first/formDesignerSchema'

export function buildBusinessObjectDesignerPayloadFromFormAsset(asset = {}, existingFields = []) {
  const formDesignerSchema = normalizeFormDesignerSchema(asset.formDesignerSchema || asset.schema || {})
  const fields = buildAutoFieldAssets(formDesignerSchema, existingFields).fields.map(toBusinessFieldPayload)
  return {
    fields,
    formDesignerSchema,
  }
}

export function syncFormBoundFieldRefs({ formFieldCodes = [], searchFieldRefs = [] } = {}) {
  const fieldRefs = uniqueFieldCodes(formFieldCodes)
  const keptSearch = uniqueFieldCodes(searchFieldRefs).filter(ref => fieldRefs.includes(ref))
  return {
    fieldRefs,
    searchFieldRefs: (keptSearch.length ? keptSearch : fieldRefs).slice(0, 8),
  }
}

function uniqueFieldCodes(values = []) {
  return [...new Set((Array.isArray(values) ? values : [])
    .map(value => String(value || '').trim())
    .filter(Boolean))]
}

export function normalizeObjectDesignerFieldCatalog(fields = []) {
  return (Array.isArray(fields) ? fields : [])
    .map((field, index) => {
      const fieldCode = field?.field || field?.fieldCode || field?.fieldBinding?.fieldCode || ''
      if (!fieldCode)
        return null
      const fieldName = field.fieldName || field.label || field.comment || fieldCode || `字段 ${index + 1}`
      return {
        ...field,
        field: fieldCode,
        fieldCode,
        sourceField: field.sourceField || fieldCode,
        fieldName,
        label: fieldName,
        listVisible: field.listVisible !== false,
        formVisible: field.formVisible !== false,
        fieldStatus: field.fieldStatus || 'ENABLED',
        systemField: Boolean(field.systemField),
      }
    })
    .filter(Boolean)
}

function toBusinessFieldPayload(field = {}) {
  return {
    fieldName: field.fieldName || field.label || field.fieldCode || field.field,
    fieldCode: field.fieldCode || field.field,
    columnName: field.columnName,
    fieldType: field.fieldType || field.businessFieldType || 'TEXT',
    dataType: field.dataType,
    length: field.length,
    precision: field.precision,
    required: Boolean(field.required),
    defaultValue: field.defaultValue,
    searchable: Boolean(field.searchable),
    listVisible: field.listVisible !== false,
    formVisible: field.formVisible !== false,
    importable: field.importable !== false,
    exportable: field.exportable !== false,
    componentType: field.componentType,
    queryType: field.queryType,
    dictType: field.dictType,
    sensitiveType: field.sensitiveType,
    encryptAlgorithm: field.encryptAlgorithm,
    sortable: Boolean(field.sortable),
    systemField: false,
    readonly: Boolean(field.readonly),
    fieldStatus: field.fieldStatus || 'ENABLED',
    referenceObjectCode: field.referenceObjectCode,
    referenceDisplayField: field.referenceDisplayField,
    placeholder: field.placeholder || field.basicProps?.placeholder || '',
    remark: field.remark || field.fieldName || field.label || '',
    sortOrder: field.sortOrder,
    formulaConfig: field.formulaConfig ?? null,
    fieldBinding: { ...(field.fieldBinding || field.basicProps?.fieldBinding || {}) },
    basicProps: { ...(field.basicProps || {}) },
    advancedProps: { ...(field.advancedProps || {}) },
  }
}
