import { get } from '@/api/http'

export interface ExternalSystemVO {
  id: number
  systemName: string
  systemCode?: string
  systemStatus?: number
}

export interface ExternalApiVO {
  id: number
  systemId: number
  systemName?: string
  systemCode?: string
  apiName: string
  apiCode?: string
  apiPath: string
  apiMethod: string
  apiStatus: number
  requestContentType?: string
  responseDataPath?: string
  responseTotalPath?: string
}

export interface ExternalApiPageParams {
  pageNum: number
  pageSize: number
  systemId: number
  apiName?: string
  apiCode?: string
  apiStatus?: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const getExternalSystemListApi = () => {
  return get('/forge-report-api/external/system/list') as unknown as Promise<{
    code: number
    data: ExternalSystemVO[]
    message: string
  }>
}

export const getExternalApiPageApi = (params: ExternalApiPageParams) => {
  return get('/forge-report-api/external/api/page', params) as unknown as Promise<{
    code: number
    data: PageResponse<ExternalApiVO>
    message: string
  }>
}

export const getExternalApiDetailApi = (id: number) => {
  return get(`/forge-report-api/external/api/${id}`) as unknown as Promise<{
    code: number
    data: ExternalApiVO
    message: string
  }>
}
