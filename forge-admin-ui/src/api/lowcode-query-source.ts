import type { AxiosRequestConfig } from 'axios'
import { request } from '@/utils'

export type LowcodeQuerySourceType = 'EXTERNAL_API' | 'DATASET'

export interface LowcodeQuerySourceRef {
  sourceType: LowcodeQuerySourceType
  sourceKey: string
}

export interface LowcodeQuerySourceCatalogItem extends LowcodeQuerySourceRef {
  sourceId: number
  sourceName: string
  sourceGroup?: string
  description?: string
}

export interface LowcodeQuerySourceField {
  field: string
  label: string
  type?: string
  path: string
  sensitive?: boolean
}

export interface LowcodeQuerySourceMetadata extends LowcodeQuerySourceCatalogItem {
  inputSchemaJson?: string
  fields: LowcodeQuerySourceField[]
}

export interface LowcodeQuerySourceExecuteRequest extends LowcodeQuerySourceRef {
  params?: Record<string, unknown>
  fields?: string[]
  pageNum?: number
  pageSize?: number
  maxRows?: number
}

export interface LowcodeQuerySourceResult extends LowcodeQuerySourceRef {
  sourceId: number
  data: unknown
  total?: number
  pageNum?: number
  pageSize?: number
  fields: LowcodeQuerySourceField[]
}

export function getLowcodeQuerySourceCatalog(keyword?: string) {
  return request.get<LowcodeQuerySourceCatalogItem[]>('/ai/lowcode/query-source/catalog', {
    params: keyword ? { keyword } : undefined,
  })
}

export function getLowcodeQuerySourceMetadata(data: LowcodeQuerySourceRef) {
  return request.post<LowcodeQuerySourceMetadata>('/ai/lowcode/query-source/metadata', data)
}

export function executeLowcodeQuerySource(data: LowcodeQuerySourceExecuteRequest, config?: AxiosRequestConfig) {
  return request.post<LowcodeQuerySourceResult>('/ai/lowcode/query-source/execute', data, config)
}
