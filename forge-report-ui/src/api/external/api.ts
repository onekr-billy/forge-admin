import { get } from '@/api/http'

export interface ExternalApiVO {
  id: number
  systemId: number
  systemName?: string
  apiName: string
  apiPath: string
  method: string
  adapterType?: string
  status: string
}

export const getExternalApiListApi = () => {
  return get('/forge-report-api/external/api/list') as unknown as Promise<{ code: number; data: ExternalApiVO[]; msg: string }>
}