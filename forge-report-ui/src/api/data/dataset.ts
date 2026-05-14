import { get, post } from '../http'

export interface DataConnectionOption {
  id: number
  connectionName: string
  connectionCode?: string
  status?: number
}

export interface DataDatasetOption {
  id: number
  datasetName: string
  datasetCode?: string
  connectionId?: number
  datasetType?: string
  status?: number
}

export interface DataDatasetQueryDTO {
  datasetId: number
  params?: Record<string, any>
  fields?: string[]
  pageNum?: number
  pageSize?: number
  maxRows?: number
  outputMode?: string
}

export interface DataDatasetMetadata {
  datasetId: number
  datasetCode: string
  datasetName: string
  datasetType: string
  fields: DataDatasetField[]
  paramSchemaJson?: string
}

export interface DataDatasetField {
  id?: number
  fieldName: string
  fieldLabel?: string
  dataType: string
  fieldRole: string
  queryEnabled?: number
  displayEnabled?: number
}

export interface DataDatasetQueryResult {
  dimensions: string[]
  source: Record<string, any>[]
  total: number
  pageNum?: number
  pageSize?: number
  fields: DataDatasetField[]
}

export function queryDataDataset(dto: DataDatasetQueryDTO): Promise<{ data: DataDatasetQueryResult }> {
  return post('/forge-report-api/data/dataset/runtime/query', dto)
}

export function getDataConnectionList(): Promise<{ data: DataConnectionOption[] }> {
  return get('/forge-report-api/data/connection/list')
}

export function getDataDatasetList(connectionId?: number): Promise<{ data: DataDatasetOption[] }> {
  return get('/forge-report-api/data/dataset/list', { connectionId })
}

export function getDataDatasetMetadata(id: number): Promise<{ data: DataDatasetMetadata }> {
  return get(`/forge-report-api/data/dataset/runtime/${id}/metadata`)
}
