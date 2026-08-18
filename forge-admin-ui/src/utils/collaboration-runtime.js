import { scanWithH5Camera } from './h5-barcode-scanner'

const PLATFORM_UA_PATTERNS = [
  ['WECHAT_ENTERPRISE', /wxwork/i],
  ['DINGTALK', /dingtalk/i],
  ['FEISHU', /(?:lark|feishu)/i],
]

const MOBILE_UA_PATTERN = /android|iphone|ipad|ipod|mobile/i

const SCAN_ERROR_CODES = new Set([
  'SCAN_CANCELLED',
  'CANCELLED',
  'CANCEL',
  'USER_CANCEL',
  'USER_CANCELLED',
])
const MAX_SCAN_VALUE_LENGTH = 2048

export function detectCollaborationPlatform(userAgent, globals) {
  const ua = String(userAgent ?? getUserAgent()).trim()
  for (const [platform, pattern] of PLATFORM_UA_PATTERNS) {
    if (pattern.test(ua))
      return platform
  }
  if (MOBILE_UA_PATTERN.test(ua))
    return 'H5'
  // globals is intentionally not used to infer identity. It is accepted so callers
  // can keep platform detection deterministic when running in a host shell.
  void globals
  return 'BROWSER'
}

export function normalizeScanResult(result, platform = 'BROWSER') {
  const value = extractScanValue(result)
  if (typeof value !== 'string')
    throw createScanError('SCAN_INVALID_RESULT', '扫码结果无效', platform)
  const normalizedValue = value.trim()
  if (!normalizedValue || normalizedValue.length > MAX_SCAN_VALUE_LENGTH)
    throw createScanError('SCAN_INVALID_RESULT', '扫码结果无效', platform)

  const type = extractScanType(result) || 'UNKNOWN'
  return {
    value: normalizedValue,
    type: type.slice(0, 64),
    platform,
  }
}

export function normalizeScanContext(scan, platform = 'BROWSER') {
  if (!scan || typeof scan !== 'object')
    return null
  try {
    const normalized = normalizeScanResult(scan, scan.platform || platform)
    return {
      value: normalized.value,
      type: normalized.type,
      platform: normalized.platform,
    }
  }
  catch {
    return null
  }
}

export function createScanError(code, message, platform = 'BROWSER', cause) {
  const error = new Error(message || code)
  error.code = code
  error.platform = platform
  if (cause && typeof cause === 'object' && cause.name)
    error.cause = cause
  return error
}

export function scan(options = {}) {
  const platform = String(options.platform || detectCollaborationPlatform(options.userAgent, options.globals)).trim() || 'BROWSER'
  const timeoutMs = normalizeTimeout(options.timeoutMs)
  const scanner = resolveScanner(options, platform)
  if (!scanner)
    return Promise.reject(createScanError('SCAN_UNSUPPORTED', '当前环境不支持扫码', platform))

  return new Promise((resolve, reject) => {
    let settled = false
    let timer
    let cleanupAbort = () => {}

    const settle = (handler, value) => {
      if (settled)
        return
      settled = true
      clearTimeout(timer)
      cleanupAbort()
      handler(value)
    }
    const succeed = (raw) => {
      try {
        settle(resolve, normalizeScanResult(raw, platform))
      }
      catch (error) {
        settle(reject, normalizeScanFailure(error, platform))
      }
    }
    const fail = error => settle(reject, normalizeScanFailure(error, platform))
    const abort = () => fail(createScanError('SCAN_CANCELLED', '已取消扫码', platform))

    if (options.signal) {
      if (options.signal.aborted) {
        abort()
        return
      }
      options.signal.addEventListener?.('abort', abort, { once: true })
      cleanupAbort = () => options.signal.removeEventListener?.('abort', abort)
    }
    timer = setTimeout(() => fail(createScanError('SCAN_TIMEOUT', '扫码超时，请重试', platform)), timeoutMs)

    try {
      const result = scanner({
        platform,
        timeoutMs,
        signal: options.signal,
        field: options.field,
      })
      Promise.resolve(result).then(succeed, fail)
    }
    catch (error) {
      fail(error)
    }
  })
}

export function isScanError(error, code) {
  return error?.code === code
}

function resolveScanner(options, platform) {
  if (typeof options.scanner === 'function')
    return options.scanner

  if (platform === 'WECHAT_ENTERPRISE') {
    const wx = options.globals?.wx || getGlobalObject()?.wx
    if (typeof wx?.scanQRCode === 'function')
      return ({ signal }) => invokeWeComScanner(wx, signal)
  }

  const injected = options.globals?.collaborationScanner || getGlobalObject()?.collaborationScanner
  if (typeof injected?.scan === 'function') {
    return ({ platform: currentPlatform, timeoutMs, signal }) => injected.scan({
      platform: currentPlatform,
      timeoutMs,
      signal,
    })
  }
  if (['H5', 'BROWSER'].includes(platform)
    && (options.globals?.navigator?.mediaDevices?.getUserMedia || getUserAgentMediaDevices())) {
    return ({ timeoutMs, signal }) => scanWithH5Camera({ timeoutMs, signal, formats: options.formats })
  }
  return null
}

function invokeWeComScanner(wx, signal) {
  return new Promise((resolve, reject) => {
    let settled = false
    let cleanup = () => {}
    const finish = (handler, value) => {
      if (settled)
        return
      settled = true
      cleanup()
      handler(value)
    }
    const abort = () => finish(reject, createScanError('SCAN_CANCELLED', '已取消扫码', 'WECHAT_ENTERPRISE'))
    cleanup = () => signal?.removeEventListener?.('abort', abort)

    if (signal?.aborted) {
      abort()
      return
    }
    signal?.addEventListener?.('abort', abort, { once: true })
    try {
      wx.scanQRCode({
        needResult: 1,
        scanType: ['qrCode', 'barCode'],
        success: result => finish(resolve, result),
        fail: error => finish(reject, error),
      })
    }
    catch (error) {
      finish(reject, error)
    }
  })
}

function normalizeScanFailure(error, platform) {
  if (error?.code && String(error.code).startsWith('SCAN_'))
    return createScanError(error.code, safeMessage(error.message, error.code), platform, error)
  if (isCancellation(error))
    return createScanError('SCAN_CANCELLED', '已取消扫码', platform, error)
  return createScanError('SCAN_FAILED', '扫码失败，请重试', platform, error)
}

function getUserAgentMediaDevices() {
  return typeof navigator !== 'undefined' && navigator.mediaDevices?.getUserMedia
}

function extractScanValue(result) {
  if (typeof result === 'string')
    return result
  if (!result || typeof result !== 'object')
    return undefined
  for (const key of ['resultStr', 'codeString', 'value']) {
    if (typeof result[key] === 'string')
      return result[key]
  }
  return undefined
}

function extractScanType(result) {
  if (!result || typeof result !== 'object')
    return ''
  for (const key of ['type', 'scanType', 'codeType']) {
    if (typeof result[key] === 'string' && result[key].trim())
      return result[key].trim()
  }
  return ''
}

function isCancellation(error) {
  if (!error)
    return false
  if (error.cancelled === true || error.canceled === true)
    return true
  const code = String(error.code || error.errCode || '').trim().toUpperCase()
  const message = String(error.message || error.errMsg || '').trim().toLowerCase()
  return SCAN_ERROR_CODES.has(code)
    || code === '1'
    || message.includes('cancel')
    || message.includes('取消')
}

function normalizeTimeout(value) {
  const number = Number(value)
  if (!Number.isFinite(number))
    return 30000
  return Math.min(60000, Math.max(1000, Math.trunc(number)))
}

function safeMessage(message, fallback) {
  const text = String(message || '').trim()
  return text && text.length <= 200 ? text : fallback
}

function getUserAgent() {
  return typeof navigator === 'undefined' ? '' : navigator.userAgent || ''
}

function getGlobalObject() {
  return typeof globalThis === 'undefined' ? undefined : globalThis
}
