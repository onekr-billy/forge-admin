export function getPublicPath() {
  const rawPath = import.meta.env.VITE_PUBLIC_PATH || import.meta.env.BASE_URL || '/'
  const value = String(rawPath || '/').trim()
  if (!value || value === '/') {
    return '/'
  }
  if (value === './' || value === '.') {
    return './'
  }
  return `/${value.replace(/^\/+|\/+$/g, '')}/`
}

/**
 * 解析静态资源地址。
 *
 * 子路径部署（如 /forge-h5/）时返回相对路径 `./static/...`：
 * - uni-app `<image>` 不会再把 Vite base 拼到已带前缀的绝对路径上（避免 /forge-h5/forge-h5/...）
 * - 内联 CSS mask/background 相对文档根解析，结果仍是 /forge-h5/static/...
 */
export function resolveStaticUrl(path) {
  const value = String(path || '').trim()
  if (!value) {
    return ''
  }
  if (isExternalAssetUrl(value)) {
    return value
  }

  const publicPath = getPublicPath()
  let normalizedPath = value.replace(/^\/+/, '')
  const normalizedPublicPath = publicPath.replace(/^\/+|\/+$/g, '')

  // 已带 publicPath 时剥掉，避免二次拼接
  if (normalizedPublicPath && normalizedPath.startsWith(`${normalizedPublicPath}/`)) {
    normalizedPath = normalizedPath.slice(normalizedPublicPath.length + 1)
  }

  if (publicPath === './') {
    return `./${normalizedPath}`
  }
  if (!normalizedPublicPath) {
    return `/${normalizedPath}`
  }
  return `./${normalizedPath}`
}

export function resolveIconUrl(icon) {
  const value = String(icon || '').trim()
  if (!value) {
    return ''
  }
  if (value.includes('/') || /\.(?:svg|png|jpe?g|webp|gif|avif)(?:\?.*)?$/i.test(value)) {
    return resolveStaticUrl(value)
  }
  return resolveStaticUrl(`/static/icons/ai-icon/${value}.svg`)
}

function isExternalAssetUrl(value) {
  return value.startsWith('http://')
    || value.startsWith('https://')
    || value.startsWith('data:')
    || value.startsWith('blob:')
}
