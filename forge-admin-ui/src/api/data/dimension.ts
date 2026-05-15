import { request } from '@/utils'

export interface DataDimension {
  id?: number
  dimensionCode: string
  dimensionName: string
  sourceType: 'MANUAL' | 'SQL'
  connectionId?: number | null
  connectionName?: string
  sqlText?: string
  valueColumn?: string
  labelColumn?: string
  status?: number
  lastSyncTime?: string
  description?: string
  itemCount?: number
  createTime?: string
  updateTime?: string
}

export interface DataDimensionItem {
  id?: number
  dimensionId?: number
  itemValue: string
  itemLabel: string
  sort?: number
  status?: number
  extraJson?: string
}

export function getDataDimensionPage(params: {
  pageNum: number
  pageSize: number
  dimensionName?: string
  sourceType?: string
  status?: number
}) {
  return request.get('/data/dimension/page', { params })
}

export function getDataDimensionList() {
  return request.get('/data/dimension/list')
}

export function getDataDimensionById(id: number) {
  return request.get(`/data/dimension/${id}`)
}

export function createDataDimension(data: DataDimension) {
  return request.post('/data/dimension', data)
}

export function updateDataDimension(data: DataDimension) {
  return request.put('/data/dimension', data)
}

export function deleteDataDimension(id: number) {
  return request.delete(`/data/dimension/${id}`)
}

export function getDataDimensionItems(id: number) {
  return request.get(`/data/dimension/${id}/items`)
}

export function saveDataDimensionItems(id: number, items: DataDimensionItem[]) {
  return request.put(`/data/dimension/${id}/items`, items)
}

export function syncDataDimensionItems(id: number) {
  return request.post(`/data/dimension/${id}/sync`)
}
