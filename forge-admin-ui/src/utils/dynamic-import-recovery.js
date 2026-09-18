const RELOAD_MARK_KEY = 'forge:dynamic-import-reload-at'
const RELOAD_COOLDOWN_MS = 60_000

const DYNAMIC_IMPORT_ERROR_PATTERNS = [
  /Failed to fetch dynamically imported module/i,
  /Importing a module script failed/i,
  /error loading dynamically imported module/i,
  /Loading chunk [\w-]+ failed/i,
  /ChunkLoadError/i,
]

function readErrorMessage(error) {
  if (typeof error === 'string')
    return error
  return String(error?.message || error?.reason?.message || '')
}

export function isDynamicImportError(error) {
  const message = readErrorMessage(error)
  return DYNAMIC_IMPORT_ERROR_PATTERNS.some(pattern => pattern.test(message))
}

function canReload(storage, now) {
  if (!storage)
    return false

  try {
    const lastReloadAt = Number(storage?.getItem(RELOAD_MARK_KEY) || 0)
    if (lastReloadAt > 0 && now - lastReloadAt < RELOAD_COOLDOWN_MS)
      return false
    storage?.setItem(RELOAD_MARK_KEY, String(now))
    return true
  }
  catch {
    // 无法持久化冷却标记时不刷新，避免浏览器策略导致无限刷新。
    return false
  }
}

function getSessionStorage() {
  try {
    return globalThis.sessionStorage
  }
  catch {
    return null
  }
}

export function recoverFromDynamicImportError(error, options = {}) {
  if (!isDynamicImportError(error))
    return false

  const storage = Object.hasOwn(options, 'storage') ? options.storage : getSessionStorage()
  const now = options.now?.() ?? Date.now()
  if (!canReload(storage, now))
    return false

  const reload = options.reload || (() => globalThis.location.reload())
  reload()
  return true
}

export function setupDynamicImportRecovery(eventTarget = globalThis.window) {
  eventTarget?.addEventListener('vite:preloadError', (event) => {
    if (!recoverFromDynamicImportError(event.payload))
      return

    // Vite 默认会继续抛出原异常；已经触发恢复刷新时阻止该行为。
    event.preventDefault()
  })
}
