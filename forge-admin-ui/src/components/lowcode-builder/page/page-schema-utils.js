/** Shared helpers for page-schema modules (hoisted in original monolith). */
export function clonePlain(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

export function isNumericId(value) {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
}

export function safeKey(value) {
  return String(value || 'item').replace(/[^\w-]/g, '_')
}
