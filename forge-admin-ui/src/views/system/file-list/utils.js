export function isImageFile(file) {
  return !!file?.mimeType?.startsWith('image/')
}

export function isPdfFile(file) {
  return file?.mimeType === 'application/pdf' || file?.extension?.replace(/^\./, '').toLowerCase() === 'pdf'
}

export function canPreviewFile(file) {
  return isImageFile(file) || isPdfFile(file)
}

export function formatFileSize(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes <= 0)
    return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${Number((bytes / 1024 ** index).toFixed(index ? 1 : 0))} ${units[index]}`
}

export function formatFileDate(value, short = false) {
  if (!value)
    return '—'
  const normalized = String(value).replace('T', ' ')
  return normalized.slice(0, short ? 10 : 16)
}
