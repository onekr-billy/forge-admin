import { ChartFrameEnum, CreateComponentType } from '@/packages/index.d'
import type { RequestConfigType } from '@/store/modules/chartEditStore/chartEditStore.d'
import { normalizeDatasetForChart, unwrapResponseData } from '@/utils/utils'

export type DatasetMappingMode = 'auto' | 'echartsDataset' | 'arrayRows' | 'objectRows' | 'nameValue' | 'singleValue'

export interface DatasetMappingConfig {
  mode?: DatasetMappingMode
  fieldMap?: Record<string, string>
  outputFields?: string[]
  syncHeader?: boolean
}

export interface DatasetAdapterResult {
  dataset: any
  optionPatch?: Record<string, any>
}

type DatasetFieldMeta = {
  fieldName?: string
  fieldLabel?: string
  dataType?: string
  fieldRole?: string
}

type RuntimeDataset = {
  dimensions: string[]
  source: any[]
  fields: DatasetFieldMeta[]
}

const isRecord = (value: any): value is Record<string, any> => {
  return Object.prototype.toString.call(value) === '[object Object]'
}

const getFieldValue = (row: any, field: string, index: number) => {
  if (Array.isArray(row)) return row[index]
  if (isRecord(row)) return row[field]
  return index === 0 ? row : undefined
}

const uniqueFields = (fields: string[]) => {
  return Array.from(new Set(fields.filter(Boolean)))
}

const inferDimensions = (source: any[]) => {
  const firstRow = source.find(row => row !== undefined && row !== null)
  if (Array.isArray(firstRow)) {
    return firstRow.map((_, index) => `field${index + 1}`)
  }
  if (isRecord(firstRow)) {
    return Object.keys(firstRow)
  }
  return []
}

const normalizeRuntimeDataset = (data: any): RuntimeDataset => {
  const unwrappedData = unwrapResponseData(data)

  if (isRecord(unwrappedData)) {
    const source = Array.isArray(unwrappedData.source)
      ? unwrappedData.source
      : Array.isArray(unwrappedData.records)
        ? unwrappedData.records
        : Array.isArray(unwrappedData.data)
          ? unwrappedData.data
          : []

    return {
      dimensions: Array.isArray(unwrappedData.dimensions) ? unwrappedData.dimensions : inferDimensions(source),
      source,
      fields: Array.isArray(unwrappedData.fields) ? unwrappedData.fields : []
    }
  }

  if (Array.isArray(unwrappedData)) {
    return {
      dimensions: inferDimensions(unwrappedData),
      source: unwrappedData,
      fields: []
    }
  }

  return {
    dimensions: ['value'],
    source: [{ value: unwrappedData }],
    fields: []
  }
}

const getOutputFields = (runtime: RuntimeDataset, request?: RequestConfigType) => {
  return uniqueFields(
    request?.datasetMapping?.outputFields?.length
      ? request.datasetMapping.outputFields
      : request?.datasetFields?.length
        ? request.datasetFields
        : runtime.dimensions
  )
}

const getFieldLabel = (fieldName: string, fields: DatasetFieldMeta[]) => {
  const field = fields.find(item => item.fieldName === fieldName)
  return field?.fieldLabel || fieldName
}

const toArrayRows = (runtime: RuntimeDataset, fields: string[]) => {
  return runtime.source.map(row => fields.map((field, index) => getFieldValue(row, field, index) ?? ''))
}

const toObjectRows = (runtime: RuntimeDataset, fields: string[]) => {
  return runtime.source.map((row, rowIndex) => {
    const nextRow = fields.reduce((acc, field, index) => {
      acc[field] = getFieldValue(row, field, index)
      return acc
    }, {} as Record<string, any>)
    if (nextRow.key === undefined) nextRow.key = rowIndex
    return nextRow
  })
}

const findFieldByRole = (runtime: RuntimeDataset, fields: string[], role: string) => {
  return runtime.fields.find(item => item.fieldRole === role && item.fieldName && fields.includes(item.fieldName))?.fieldName
}

const findFieldByType = (runtime: RuntimeDataset, fields: string[], typeList: string[]) => {
  return runtime.fields.find(item => item.fieldName && fields.includes(item.fieldName) && typeList.includes(item.dataType || ''))
    ?.fieldName
}

const getNameValueFields = (runtime: RuntimeDataset, fields: string[], request?: RequestConfigType) => {
  const fieldMap = request?.datasetMapping?.fieldMap || {}
  const nameField =
    fieldMap.name ||
    findFieldByRole(runtime, fields, 'DIMENSION') ||
    findFieldByType(runtime, fields, ['STRING', 'DATE', 'DATETIME']) ||
    fields[0]
  const valueField =
    fieldMap.value ||
    findFieldByRole(runtime, fields, 'MEASURE') ||
    findFieldByType(runtime, fields, ['NUMBER', 'INTEGER', 'DECIMAL']) ||
    fields.find(field => field !== nameField) ||
    fields[1] ||
    nameField

  return { nameField, valueField }
}

const toNameValueRows = (runtime: RuntimeDataset, fields: string[], request?: RequestConfigType) => {
  const { nameField, valueField } = getNameValueFields(runtime, fields, request)
  const nameIndex = fields.indexOf(nameField)
  const valueIndex = fields.indexOf(valueField)

  return runtime.source.map(row => ({
    name: getFieldValue(row, nameField, nameIndex < 0 ? 0 : nameIndex) ?? '',
    value: Number(getFieldValue(row, valueField, valueIndex < 0 ? 1 : valueIndex) ?? 0)
  }))
}

const toBasicTableDataset = (runtime: RuntimeDataset, fields: string[]) => {
  return {
    dimensions: fields.map(field => ({
      title: getFieldLabel(field, runtime.fields),
      key: field
    })),
    source: toObjectRows(runtime, fields)
  }
}

const toEchartsDataset = (runtime: RuntimeDataset, fields: string[]) => {
  return normalizeDatasetForChart(
    {
      dimensions: fields,
      source: toObjectRows(runtime, fields)
    },
    ChartFrameEnum.ECHARTS
  )
}

const adaptByExistingShape = (runtime: RuntimeDataset, fields: string[], currentDataset: any) => {
  if (Array.isArray(currentDataset)) {
    const firstItem = currentDataset[0]
    if (Array.isArray(firstItem)) return toArrayRows(runtime, fields)
    if (isRecord(firstItem) && ('name' in firstItem || 'value' in firstItem)) return toNameValueRows(runtime, fields)
    if (isRecord(firstItem)) return toObjectRows(runtime, fields)
    return runtime.source.map(row => getFieldValue(row, fields[0], 0))
  }

  if (typeof currentDataset === 'number') {
    const firstValue = getFieldValue(runtime.source[0], fields[0], 0)
    return Number(firstValue ?? 0)
  }

  if (typeof currentDataset === 'string') {
    const firstValue = getFieldValue(runtime.source[0], fields[0], 0)
    return firstValue === undefined || firstValue === null ? '' : String(firstValue)
  }

  if (isRecord(currentDataset) && Array.isArray(currentDataset.dimensions) && Array.isArray(currentDataset.source)) {
    return toEchartsDataset(runtime, fields)
  }

  return {
    dimensions: fields,
    source: toObjectRows(runtime, fields)
  }
}

export const adaptDatasetForComponent = (data: any, targetComponent: CreateComponentType): DatasetAdapterResult => {
  const runtime = normalizeRuntimeDataset(data)
  const request = targetComponent.request
  const fields = getOutputFields(runtime, request)
  const chartFrame = targetComponent.chartConfig?.chartFrame
  const mappingMode = request?.datasetMapping?.mode || 'auto'

  if (chartFrame === ChartFrameEnum.ECHARTS || mappingMode === 'echartsDataset') {
    return { dataset: toEchartsDataset(runtime, fields) }
  }

  if (mappingMode === 'arrayRows' || targetComponent.key === 'TableScrollBoard') {
    const optionPatch =
      targetComponent.key === 'TableScrollBoard' && request?.datasetMapping?.syncHeader !== false
        ? { header: fields.map(field => getFieldLabel(field, runtime.fields)) }
        : undefined
    return {
      dataset: toArrayRows(runtime, fields),
      optionPatch
    }
  }

  if (mappingMode === 'nameValue' || targetComponent.key === 'TableList') {
    return { dataset: toNameValueRows(runtime, fields, request) }
  }

  if (targetComponent.key === 'TablesBasic') {
    return { dataset: toBasicTableDataset(runtime, fields) }
  }

  if (mappingMode === 'objectRows') {
    return { dataset: toObjectRows(runtime, fields) }
  }

  if (mappingMode === 'singleValue') {
    return { dataset: getFieldValue(runtime.source[0], fields[0], 0) }
  }

  return {
    dataset: adaptByExistingShape(runtime, fields, targetComponent.option?.dataset)
  }
}

export const applyDatasetAdapterResult = (targetComponent: CreateComponentType, result: DatasetAdapterResult) => {
  if (result.optionPatch) {
    Object.assign(targetComponent.option, result.optionPatch)
  }
  targetComponent.option.dataset = result.dataset
}
