const REFRESH_INTERVAL_MS = 10 * 60 * 1000

let lastRefreshAt = null
let refreshPromise = null

function isRefreshSkipped(config = {}) {
  if (config.skipAuthRefresh) {
    return true
  }
  const url = String(config.url || '')
  return url.includes('/auth/refreshToken')
    || url.includes('/auth/logout')
    || url.includes('/auth/login')
}

export function resetSessionKeepalive() {
  lastRefreshAt = null
  refreshPromise = null
}

export function maybeRefreshSession(axiosInstance, accessToken, config = {}) {
  if (!axiosInstance || !accessToken || isRefreshSkipped(config)) {
    return
  }

  const now = Date.now()
  if (lastRefreshAt == null) {
    lastRefreshAt = now
    return
  }
  if (now - lastRefreshAt < REFRESH_INTERVAL_MS || refreshPromise) {
    return
  }

  lastRefreshAt = now
  refreshPromise = axiosInstance.post('/auth/refreshToken', {}, {
    needTip: false,
    skipAuthRefresh: true,
  })
    .catch(() => {
      lastRefreshAt = null
    })
    .finally(() => {
      refreshPromise = null
    })
}
