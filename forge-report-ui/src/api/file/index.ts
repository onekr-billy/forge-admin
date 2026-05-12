import type { AxiosRequestConfig } from 'axios'
import axiosInstance from '@/api/axios'

export interface FileUploadResult {
  fileId: string
  fileName: string
  fileSize: number
  contentType: string
  businessType: string
  businessId?: string
  accessUrl: string
  storageType: string
  createTime?: string
}

export const uploadFileApi = async (file: File, businessType = 'project_screenshot', businessId?: string) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('businessType', businessType)
  if (businessId) {
    formData.append('businessId', businessId)
  }

  const config: AxiosRequestConfig & { encrypt?: boolean } = {
    url: '/forge-report-api/api/file/upload',
    method: 'post',
    data: formData,
    encrypt: false
  }

  return axiosInstance(config) as unknown as Promise<{ code: number; data: FileUploadResult; msg: string }>
}

export const getFileUrlApi = (fileId: string, expires = 3600) => {
  return `/forge-report-api/api/file/url/${fileId}?expires=${expires}`
}
