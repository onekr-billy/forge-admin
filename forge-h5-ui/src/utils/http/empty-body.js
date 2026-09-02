/**
 * axios.post(url, null) 在部分版本会带上
 * Content-Type: application/x-www-form-urlencoded;charset=utf-8。
 * 收成无请求体，避免空 POST 被当成表单。
 */
export function stripNullRequestBody(config = {}) {
  if (config.data !== null)
    return config

  config.data = undefined
  const headers = config.headers
  if (!headers)
    return config

  if (typeof headers.setContentType === 'function') {
    headers.setContentType(false, false)
    return config
  }

  delete headers['Content-Type']
  delete headers['content-type']
  return config
}
