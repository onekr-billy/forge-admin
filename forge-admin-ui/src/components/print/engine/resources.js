import { resolveBinding } from '../protocol/binding'
import { formatValue } from '../protocol/formatters'
import { PRINT_LIMITS, PrintError } from '../protocol/types'
import { isSafeImageReference } from '../protocol/validate'
import { encodePrintCode } from '../renderers/codes'
import { requireLocalFont } from './fonts'

export function abortable(promise, signal) {
  return new Promise((resolve, reject) => {
    const cancel = () => reject(signal.reason || new PrintError('RESOURCE_CANCELLED', '打印准备已取消'))
    if (signal.aborted) {
      cancel()
      return
    }
    signal.addEventListener('abort', cancel, { once: true })
    Promise.resolve(promise).then(resolve, reject).finally(() => signal.removeEventListener('abort', cancel))
  })
}

async function decodeImage(src, signal) {
  const image = new Image()
  image.src = src
  try {
    await abortable(image.decode(), signal)
    if (!image.naturalWidth || !image.naturalHeight) {
      throw new Error('empty image')
    }
  }
  finally {
    image.removeAttribute('src')
  }
}

export async function preparePrintResources(document, context, options = {}) {
  const controller = new AbortController()
  const { signal } = controller
  const cancel = () => controller.abort(new PrintError('RESOURCE_CANCELLED', '打印准备已取消'))
  options.signal?.addEventListener('abort', cancel, { once: true })
  if (options.signal?.aborted) {
    cancel()
  }
  const timer = setTimeout(() => controller.abort(new PrintError('RESOURCE_TIMEOUT', '打印资源准备超时')), options.timeoutMs ?? PRINT_LIMITS.resourceTimeoutMs)
  const images = new Map()
  const files = new Map()
  const urls = new Set()
  const revoke = options.revokeObjectURL || (url => URL.revokeObjectURL(url))
  const dispose = () => {
    urls.forEach(url => revoke(url))
    urls.clear()
    images.clear()
  }
  let location = 'fonts'
  try {
    const fonts = options.fonts === undefined ? globalThis.document?.fonts : options.fonts
    if (fonts) {
      await abortable(fonts.ready, signal)
      const styles = new Set()
      const families = new Set(['Arial, sans-serif'])
      JSON.stringify(document, (key, value) => {
        if (key === 'style' && value) {
          families.add(value.fontFamily || 'Arial, sans-serif')
          styles.add(`${value.fontStyle || 'normal'} ${value.fontWeight || 400} ${value.fontSizePt || 10}pt ${value.fontFamily || 'Arial, sans-serif'}`)
        }
        return value
      })
      styles.add('normal 400 10pt Arial, sans-serif')
      for (const family of families) {
        await abortable((options.requireLocalFont || requireLocalFont)(family), signal)
      }
      for (const style of styles) {
        await abortable(fonts.load(style, '打印中文Aa012345'), signal)
        if (!fonts.check(style, '打印中文Aa012345')) {
          throw new PrintError('FONT_UNAVAILABLE', '打印字体未就绪', 'fonts')
        }
      }
    }
    const elements = [...document.header.elements, ...document.body.flatMap(section => section.elements || []), ...document.footer.elements]
    const aliases = new Map(document.resources.map(resource => [resource.id, resource.fileId]))
    for (const element of elements) {
      location = element.id
      let src
      if (element.type === 'IMAGE') {
        const reference = resolveBinding(element.binding, context)
        if (reference === null || reference === '') {
          continue
        }
        if (!isSafeImageReference(reference)) {
          throw new PrintError('INVALID_RESOURCE', '图片引用无效', location)
        }
        const fileId = aliases.get(reference) || reference
        src = files.get(fileId)
        if (!src) {
          if (reference.startsWith('data:')) {
            src = reference
          }
          else {
            if (!options.resolveFile) {
              throw new PrintError('RESOURCE_RESOLVER_REQUIRED', '缺少鉴权文件读取能力', location)
            }
            const blob = await abortable(options.resolveFile(fileId, { signal }), signal)
            if (!(blob instanceof Blob) || !['image/png', 'image/jpeg', 'image/webp'].includes(blob.type) || blob.size > 10 * 1024 * 1024) {
              throw new PrintError('INVALID_RESOURCE', '文件不是受支持的图片或体积超过限制', location)
            }
            src = (options.createObjectURL || (value => URL.createObjectURL(value)))(blob)
            urls.add(src)
          }
          await abortable((options.decodeImage || decodeImage)(src, signal), signal)
          files.set(fileId, src)
        }
      }
      else if (['BARCODE', 'QRCODE'].includes(element.type)) {
        const text = formatValue(resolveBinding(element.binding, context), element.format)
        src = await abortable((options.encodeCode || encodePrintCode)({ ...element, text }, signal), signal)
        await abortable((options.decodeImage || decodeImage)(src, signal), signal)
      }
      if (src) {
        images.set(element.id, src)
      }
    }
    if (signal.aborted) {
      throw signal.reason
    }
    return { images, dispose }
  }
  catch (error) {
    dispose()
    throw error instanceof PrintError ? error : new PrintError('RESOURCE_FAILED', '打印资源加载失败', location)
  }
  finally {
    clearTimeout(timer)
    options.signal?.removeEventListener('abort', cancel)
  }
}
