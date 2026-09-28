/** 为流程写操作生成可重试的幂等凭证。 */
export async function createFlowActionCredentials(action, taskId, payload = {}) {
  const normalizedAction = String(action || 'action').trim() || 'action'
  const normalizedTaskId = String(taskId || '').trim()
  const raw = `${normalizedAction}|${normalizedTaskId}|${canonicalJson(payload)}`
  if (!globalThis.crypto?.subtle || !globalThis.TextEncoder)
    throw new Error('当前浏览器不支持 SHA-256，无法生成流程动作幂等凭证')

  const buffer = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw))
  const digest = Array.from(new Uint8Array(buffer), byte => byte.toString(16).padStart(2, '0')).join('')
  return {
    // 同一任务、动作和规范载荷在用户重试时必须复用同一远端命令身份。
    idempotencyKey: `flow:${digest}`.slice(0, 128),
    requestDigest: digest.slice(0, 71),
  }
}

function canonicalJson(value) {
  return JSON.stringify(value, (_key, current) => {
    if (!current || typeof current !== 'object' || Array.isArray(current))
      return current
    return Object.keys(current).sort().reduce((ordered, key) => {
      ordered[key] = current[key]
      return ordered
    }, {})
  })
}
