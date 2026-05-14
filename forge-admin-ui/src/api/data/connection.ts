import { request } from '@/utils'

export interface DataConnection {
  id?: number
  connectionCode: string
  connectionName: string
  dbType: string
  driverClassName: string
  jdbcUrl: string
  username: string
  password?: string
  schemaName?: string
  testSql?: string
  poolConfigJson?: string
  status?: number
  description?: string
  createTime?: string
  updateTime?: string
}

export interface DataConnectionDetail {
  id: number
  connectionCode: string
  connectionName: string
  dbType: string
  driverClassName: string
  jdbcUrl: string
  username: string
  hasPassword: boolean
  schemaName?: string
  testSql: string
  poolConfigJson?: string
  status: number
  description?: string
  createTime: string
  updateTime: string
}

export interface DataConnectionTable {
  tableName: string
  tableType: string
  tableComment?: string
}

export interface DataConnectionField {
  columnName: string
  columnType: string
  columnComment?: string
  nullable: boolean
  primaryKey: boolean
}

export function getDataConnectionPage(params: { pageNum: number; pageSize: number; connectionName?: string; dbType?: string; status?: number }) {
  return request.get('/data/connection/page', { params })
}

export function getDataConnectionList() {
  return request.get('/data/connection/list')
}

export function getDataConnectionById(id: number) {
  return request.get(`/data/connection/${id}`)
}

export function createDataConnection(data: DataConnection) {
  return request.post('/data/connection', data)
}

export function updateDataConnection(data: DataConnection) {
  return request.put('/data/connection', data)
}

export function deleteDataConnection(id: number) {
  return request.delete(`/data/connection/${id}`)
}

export function testDataConnection(id: number) {
  return request.post(`/data/connection/${id}/test`)
}

export function testDataConnectionTemp(data: { dbType: string; driverClassName: string; jdbcUrl: string; username: string; password: string; testSql?: string }) {
  return request.post('/data/connection/test', data)
}

export function getDataConnectionTables(id: number, keyword?: string) {
  return request.get(`/data/connection/${id}/tables`, { params: { keyword } })
}

export function getDataConnectionFields(id: number, tableName: string) {
  return request.get(`/data/connection/${id}/tables/${encodeURIComponent(tableName)}/fields`)
}
