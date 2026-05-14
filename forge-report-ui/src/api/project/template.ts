import { del, get, post, put } from '@/api/http'
import type { ChartEditStorage } from '@/store/modules/chartEditStore/chartEditStore.d'

export interface ForgeTemplate {
  id: number | string
  sourceProjectId?: number | string
  projectId?: number | string
  templateName: string
  remark?: string
  indexImg?: string
  status?: string
  publishStatus?: string
  templateScope?: string
  publishUrl?: string
  publishTime?: string
  componentData?: string
  directoryId?: number | string
  createTime?: string
  updateTime?: string
  ownerId?: number | string
  ownerName?: string
  copiedCount?: number
}

export interface TemplatePageResponse {
  records: ForgeTemplate[]
  total: number
  size: number
  current: number
}

export interface TemplatePageQuery {
  pageNum?: number
  pageSize?: number
  templateName?: string
  publishStatus?: string
}

export interface TemplateCopyPayload {
  templateId: number | string
  projectName: string
}

export interface TemplatePublishPayload {
  id: number | string
  publishUrl?: string
}

export const getMyTemplatePageApi = (params?: TemplatePageQuery) => {
  return get('/forge-report-api/report/template/page', params) as unknown as Promise<{ code: number; data: TemplatePageResponse; msg: string }>
}

export const getTemplateMarketPageApi = (params?: TemplatePageQuery) => {
  return get('/forge-report-api/report/template/market/page', params) as unknown as Promise<{ code: number; data: TemplatePageResponse; msg: string }>
}

export const getTemplateDetailApi = (id: string | number) => {
  return get(`/forge-report-api/report/template/${id}`) as unknown as Promise<{ code: number; data: ForgeTemplate; msg: string }>
}

export const createTemplateFromProjectApi = (data: Partial<ForgeTemplate>) => {
  return post('/forge-report-api/report/template/from-project', data) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const updateTemplateApi = (data: Partial<ForgeTemplate>) => {
  return put('/forge-report-api/report/template', data) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const deleteTemplateApi = (id: string | number) => {
  return del(`/forge-report-api/report/template/${id}`) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const publishTemplateToMarketApi = (id: string | number, publishUrl?: string) => {
  return post(`/forge-report-api/report/template/publish/${id}${publishUrl ? `?publishUrl=${encodeURIComponent(publishUrl)}` : ''}`) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const copyTemplateToProjectApi = (data: TemplateCopyPayload) => {
  return post('/forge-report-api/report/template/copy-to-project', data) as unknown as Promise<{ code: number; data?: { projectId?: number | string }; msg: string }>
}

export const buildTemplatePayload = (rawId: string | string[] | number, storageInfo: ChartEditStorage, indexImg?: string) => {
  const id = Array.isArray(rawId) ? rawId[0] : rawId
  const payload: Partial<ForgeTemplate> = {
    id,
    templateName: storageInfo.editCanvasConfig?.projectName || '新模板',
    componentData: JSON.stringify(storageInfo),
    status: '0',
  }
  if (indexImg) {
    payload.indexImg = indexImg
  }
  return payload
}
