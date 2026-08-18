import { BarcodeFormat, BrowserMultiFormatReader } from '@zxing/browser'

const DEFAULT_TIMEOUT = 30000

/**
 * 使用浏览器后置摄像头扫描条形码/二维码。
 * 该适配器只负责设备能力和结果采集，业务查询仍由 SCAN_COMPLETE 字段事件完成。
 */
export function scanWithH5Camera({ timeoutMs = DEFAULT_TIMEOUT, signal, formats = [] } = {}) {
  if (typeof document === 'undefined' || typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia)
    return Promise.reject(createH5ScanError('SCAN_UNSUPPORTED', '当前浏览器没有摄像头能力'))

  return new Promise((resolve, reject) => {
    const shell = document.createElement('div')
    shell.setAttribute('data-forge-barcode-scanner', 'true')
    Object.assign(shell.style, {
      position: 'fixed',
      inset: '0',
      zIndex: '2147483000',
      display: 'grid',
      placeItems: 'center',
      padding: '24px',
      background: 'rgba(15, 23, 42, .78)',
    })
    const panel = document.createElement('div')
    Object.assign(panel.style, {
      position: 'relative',
      width: 'min(460px, 100%)',
      overflow: 'hidden',
      borderRadius: '14px',
      background: '#0f172a',
      boxShadow: '0 18px 50px rgba(0,0,0,.35)',
    })
    const video = document.createElement('video')
    video.setAttribute('autoplay', 'true')
    video.setAttribute('muted', 'true')
    video.setAttribute('playsinline', 'true')
    Object.assign(video.style, {
      display: 'block',
      width: '100%',
      minHeight: '260px',
      maxHeight: '70vh',
      objectFit: 'cover',
      background: '#020617',
    })
    const footer = document.createElement('div')
    Object.assign(footer.style, {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: '12px',
      padding: '12px 14px',
      color: '#e2e8f0',
      fontSize: '13px',
    })
    const hint = document.createElement('span')
    hint.textContent = '请将条形码放入取景框'
    const cancel = document.createElement('button')
    cancel.type = 'button'
    cancel.textContent = '取消'
    Object.assign(cancel.style, {
      border: '1px solid rgba(226,232,240,.35)',
      borderRadius: '6px',
      padding: '6px 12px',
      color: '#e2e8f0',
      background: 'transparent',
      cursor: 'pointer',
    })
    footer.append(hint, cancel)
    panel.append(video, footer)
    shell.append(panel)
    document.body?.append(shell)

    let settled = false
    let controls = null
    let timer = null
    let stream = null
    let abort = () => {}
    const cleanup = () => {
      clearTimeout(timer)
      controls?.stop?.()
      stream?.getTracks?.().forEach(track => track.stop())
      if (video.srcObject) {
        video.srcObject = null
      }
      shell.remove()
      signal?.removeEventListener?.('abort', abort)
    }
    const finish = (handler, value) => {
      if (settled)
        return
      settled = true
      cleanup()
      handler(value)
    }
    abort = () => finish(reject, createH5ScanError('SCAN_CANCELLED', '已取消扫码'))
    cancel.addEventListener('click', abort, { once: true })
    if (signal?.aborted) {
      abort()
      return
    }
    signal?.addEventListener?.('abort', abort, { once: true })
    timer = setTimeout(() => finish(reject, createH5ScanError('SCAN_TIMEOUT', '扫码超时，请重试')), normalizeTimeout(timeoutMs))

    Promise.resolve().then(async () => {
      const reader = new BrowserMultiFormatReader()
      if (Array.isArray(formats) && formats.length) {
        const possibleFormats = formats.map(format => BarcodeFormat[String(format).toUpperCase()]).filter(Boolean)
        if (possibleFormats.length)
          reader.possibleFormats = possibleFormats
      }
      const callback = (result, error, scannerControls) => {
        controls = scannerControls || controls
        if (!result)
          return
        const text = result.getText?.() || result.text || ''
        if (String(text).trim())
          finish(resolve, { value: String(text).trim(), type: result.getBarcodeFormat?.()?.toString?.() || 'UNKNOWN' })
      }
      try {
        controls = await reader.decodeFromConstraints({ video: { facingMode: { ideal: 'environment' } } }, video, callback)
        stream = video.srcObject
      }
      catch (error) {
        const code = error?.name === 'NotAllowedError' || error?.name === 'SecurityError'
          ? 'SCAN_PERMISSION_DENIED'
          : 'SCAN_FAILED'
        finish(reject, createH5ScanError(code, code === 'SCAN_PERMISSION_DENIED' ? '摄像头权限被拒绝' : '扫码失败，请重试', error))
      }
    })
  })
}

function normalizeTimeout(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.min(60000, Math.max(1000, Math.trunc(number))) : DEFAULT_TIMEOUT
}

function createH5ScanError(code, message, cause) {
  const error = new Error(message || code)
  error.code = code
  if (cause)
    error.cause = cause
  return error
}
