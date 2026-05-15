import { del, get, post, put } from '@/api/http'
import { extractPageStorage, isMultiPageStorage } from '@/utils/reportPages'
import type { ChartEditStorage, ReportMultiPageStorage } from '@/store/modules/chartEditStore/chartEditStore.d'

export interface ForgeProject {
  id: number | string
  directoryId?: number | string
  projectName: string
  remark?: string
  indexImg?: string
  status?: string
  canvasWidth?: number
  canvasHeight?: number
  backgroundColor?: string
  componentData?: string
  publishStatus?: string
  publishUrl?: string
  publishTime?: string
  createTime?: string
}

export interface ReportDirectory {
  id: number | string
  parentId?: number | string
  ancestors?: string
  directoryName: string
  sort?: number
  remark?: string
  createTime?: string
  updateTime?: string
  children?: ReportDirectory[]
}

export interface ProjectPageResponse {
  records: ForgeProject[]
  total: number
  size: number
  current: number
}

export const getProjectPageApi = (params?: { pageNum?: number; pageSize?: number; projectName?: string; directoryId?: number | string }) => {
  return get('/forge-report-api/report/project/page', params) as unknown as Promise<{ code: number; data: ProjectPageResponse; msg: string }>
}

export const getProjectDetailApi = (id: string | number) => {
  return get(`/forge-report-api/report/project/${id}`) as unknown as Promise<{ code: number; data: ForgeProject; msg: string }>
}

export const createProjectApi = (data: Partial<ForgeProject>) => {
  return post('/forge-report-api/report/project', data) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const updateProjectApi = (data: Partial<ForgeProject>) => {
  return put('/forge-report-api/report/project', data) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const deleteProjectApi = (id: string | number) => {
  return del(`/forge-report-api/report/project/${id}`) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const publishProjectApi = (id: string | number, publishUrl: string) => {
  return post(`/forge-report-api/report/project/publish/${id}?publishUrl=${encodeURIComponent(publishUrl)}`) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const getProjectDirectoryTreeApi = () => {
  return get('/forge-report-api/report/directory/tree') as unknown as Promise<{ code: number; data: ReportDirectory[]; msg: string }>
}

export const createProjectDirectoryApi = (data: Partial<ReportDirectory>) => {
  return post('/forge-report-api/report/directory', data) as unknown as Promise<{ code: number; data: ReportDirectory; msg: string }>
}

export const updateProjectDirectoryApi = (data: Partial<ReportDirectory>) => {
  return put('/forge-report-api/report/directory', data) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const moveProjectDirectoryApi = (data: { id: string | number; targetParentId?: string | number | null }) => {
  return put('/forge-report-api/report/directory/move', data) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const deleteProjectDirectoryApi = (id: string | number) => {
  return del(`/forge-report-api/report/directory/${id}`) as unknown as Promise<{ code: number; data?: any; msg: string }>
}

export const buildProjectPayload = (
  rawId: string | string[] | number,
  storageInfo: ChartEditStorage | ReportMultiPageStorage,
  indexImg?: string
) => {
  const id = Array.isArray(rawId) ? rawId[0] : rawId
  const activeStorage = isMultiPageStorage(storageInfo)
    ? extractPageStorage(storageInfo, storageInfo.activePageId)
    : storageInfo
  const payload: Partial<ForgeProject> = {
    id,
    projectName: activeStorage.editCanvasConfig?.projectName || '新项目',
    canvasWidth: activeStorage.editCanvasConfig?.width,
    canvasHeight: activeStorage.editCanvasConfig?.height,
    backgroundColor: activeStorage.editCanvasConfig?.background || '',
    componentData: JSON.stringify(storageInfo),
    status: '0',
  }
  if (indexImg) {
    payload.indexImg = indexImg
  }
  return payload
}
