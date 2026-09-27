import { useAuthStore } from '@/store'
import { resolveStaticUrl } from '@/utils/assets'

const blobUrlCache = new Map()
const fileAccessUrlCache = new Map()
export const DEFAULT_AVATAR_URL = resolveStaticUrl('/static/icons/ai-icon/user.svg')

export function getFileDownloadUrl(fileId) {
  const value = String(fileId || '').trim()
  if (!value) {
    return ''
  }
  if (isDirectFileUrl(value)) {
    return value
  }
  const prefix = import.meta.env.VITE_REQUEST_PREFIX || ''
  return `${prefix}/api/file/download/${encodeURIComponent(value)}`
}

export function normalizeFileAccessUrl(url) {
  const value = String(url || '').trim()
  if (!value) {
    return ''
  }
  if (value.startsWith('http://')
    || value.startsWith('https://')
    || value.startsWith('data:')
    || value.startsWith('blob:')) {
    return value
  }

  const prefix = import.meta.env.VITE_REQUEST_PREFIX || ''
  if (value.startsWith('/api/file/') && prefix) {
    return `${prefix}${value}`
  }
  return value
}

export function resolveAvatarUrl(value, fallback = '') {
  const rawValue = String(value || '').trim()
  if (!rawValue) {
    return fallback
  }
  return getFileDownloadUrl(rawValue)
}

export async function resolveRenderableFileUrl(fileData, options = {}) {
  const url = await resolveFileAccessUrl(fileData, options)
  if (!url) {
    return ''
  }

  if (url.startsWith('data:') || url.startsWith('blob:') || !isInternalFileUrl(url)) {
    return url
  }

  const cacheKey = getFileCacheKey(fileData)
  if (!options.forceRefresh && cacheKey && blobUrlCache.has(cacheKey)) {
    return blobUrlCache.get(cacheKey)
  }

  if (options.forceRefresh && cacheKey) {
    revokeCachedFileBlobUrl(cacheKey)
  }

  const response = await fetch(url, {
    headers: getAuthHeaders(),
  })
  if (!response.ok) {
    throw new Error('文件加载失败')
  }
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const result = await response.json().catch(() => null)
    throw new Error(result?.message || result?.msg || '文件加载失败')
  }

  const blobUrl = URL.createObjectURL(await response.blob())
  if (cacheKey) {
    blobUrlCache.set(cacheKey, blobUrl)
  }
  return blobUrl
}

export async function resolveFileAccessUrl(fileData, options = {}) {
  const value = typeof fileData === 'object'
    ? fileData?.accessUrl || fileData?.fileId || fileData?.filePath || fileData?.url
    : fileData
  const rawValue = String(value || '').trim()
  if (!rawValue) {
    return ''
  }

  if (isDirectFileUrl(rawValue)) {
    return normalizeFileAccessUrl(rawValue)
  }

  if (!options.forceRefresh && fileAccessUrlCache.has(rawValue)) {
    return fileAccessUrlCache.get(rawValue)
  }

  if (options.forceRefresh) {
    fileAccessUrlCache.delete(rawValue)
  }

  const prefix = import.meta.env.VITE_REQUEST_PREFIX || ''
  const response = await fetch(`${prefix}/api/file/url/${encodeURIComponent(rawValue)}`, {
    headers: getAuthHeaders(),
  })
  if (!response.ok) {
    throw new Error('文件访问地址获取失败')
  }

  const result = await response.json()
  if (!(result?.code === 200 || result?.respCode === '0000') || !result?.data) {
    throw new Error(result?.message || result?.msg || '文件访问地址获取失败')
  }

  const accessUrl = normalizeFileAccessUrl(result.data)
  fileAccessUrlCache.set(rawValue, accessUrl)
  return accessUrl
}

export function revokeCachedFileBlobUrl(fileData) {
  const value = typeof fileData === 'object'
    ? fileData?.accessUrl || fileData?.fileId || fileData?.filePath || fileData?.url
    : fileData
  const rawValue = String(value || '').trim()
  const blobUrl = blobUrlCache.get(rawValue)
  if (blobUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(blobUrl)
  }
  blobUrlCache.delete(rawValue)
}

function getAuthHeaders() {
  try {
    const authStore = useAuthStore()
    const headers = {
      'X-Timestamp': Date.now().toString(),
      'X-Nonce': generateNonce(),
    }
    if (authStore.accessToken) {
      headers.Authorization = `${authStore.tokenType || 'Bearer'} ${authStore.accessToken}`
    }
    return headers
  }
  catch {}
  return {
    'X-Timestamp': Date.now().toString(),
    'X-Nonce': generateNonce(),
  }
}

function getFileCacheKey(fileData) {
  const value = typeof fileData === 'object'
    ? fileData?.accessUrl || fileData?.fileId || fileData?.filePath || fileData?.url
    : fileData
  return String(value || '').trim()
}

function generateNonce() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0
    const value = char === 'x' ? random : (random & 0x3 | 0x8)
    return value.toString(16)
  })
}

function isInternalFileUrl(url) {
  const prefix = import.meta.env.VITE_REQUEST_PREFIX || ''
  return url.startsWith('/api/file/')
    || url.includes('/api/file/')
    || (prefix && url.startsWith(`${prefix}/api/file/`))
}

function isDirectFileUrl(value) {
  return value.startsWith('http://')
    || value.startsWith('https://')
    || value.startsWith('data:')
    || value.startsWith('blob:')
    || value.startsWith('/api/file/')
    || /\.(?:png|jpe?g|webp|gif|svg|avif)(?:\?.*)?$/i.test(value)
}
