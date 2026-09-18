import { PrintError } from '../protocol/types'

const GENERIC_FAMILIES = new Set(['serif', 'sans-serif', 'monospace', 'system-ui'])

/** Check an explicitly selected local face instead of silently substituting a missing font. */
export async function requireLocalFont(family, FontFaceType = globalThis.FontFace) {
  const primary = family.split(',')[0].trim()
  if (GENERIC_FAMILIES.has(primary.toLowerCase())) {
    return
  }
  if (!FontFaceType) {
    throw new PrintError('FONT_CHECK_UNAVAILABLE', '浏览器不支持打印字体校验', 'fonts')
  }
  try {
    const face = new FontFaceType('ForgePrintLocalFontCheck', `local("${primary}")`)
    await face.load()
  }
  catch {
    throw new PrintError('FONT_UNAVAILABLE', `打印字体未安装：${primary}`, 'fonts')
  }
}
