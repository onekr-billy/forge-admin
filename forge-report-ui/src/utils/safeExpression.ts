const MAX_SELECTOR_LENGTH = 512
const MAX_TEMPLATE_LENGTH = 2048
const DANGEROUS_KEYS = new Set(['__proto__', 'prototype', 'constructor'])
const SELECTOR_PATTERN = /^(data|res)((?:\.[A-Za-z_$][\w$]*|\[(?:0|[1-9]\d*|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')\])*)$/
const SEGMENT_PATTERN = /\.([A-Za-z_$][\w$]*)|\[(0|[1-9]\d*|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')\]/g
const TEMPLATE_TOKEN_PATTERN = /\$\{([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\}/g

const assertSafeKey = (key: string) => {
  if (DANGEROUS_KEYS.has(key)) {
    throw new Error(`不允许访问属性 ${key}`)
  }
}

const readPath = (source: unknown, keys: Array<string | number>) => {
  return keys.reduce<unknown>((current, key) => {
    if (current === null || current === undefined) return undefined
    if (typeof key === 'string') assertSafeKey(key)
    if (typeof current !== 'object') return undefined
    return (current as Record<string | number, unknown>)[key]
  }, source)
}

const decodeSingleQuotedKey = (raw: string) => {
  let decoded = ''
  for (let index = 1; index < raw.length - 1; index += 1) {
    const current = raw[index]
    if (current !== '\\') {
      decoded += current
      continue
    }
    const escaped = raw[++index]
    if (escaped === undefined) throw new Error('字段路径转义不完整')
    if (escaped === 'u') {
      const hex = raw.slice(index + 1, index + 5)
      if (!/^[0-9a-fA-F]{4}$/.test(hex)) throw new Error('字段路径 Unicode 转义无效')
      decoded += String.fromCharCode(Number.parseInt(hex, 16))
      index += 4
      continue
    }
    const escapes: Record<string, string> = {
      "'": "'", '"': '"', '\\': '\\', b: '\b', f: '\f', n: '\n', r: '\r', t: '\t'
    }
    if (!(escaped in escapes)) throw new Error('字段路径包含不支持的转义')
    decoded += escapes[escaped]
  }
  return decoded
}

const parseSelector = (script: string) => {
  if (!script || script.length > MAX_SELECTOR_LENGTH) {
    throw new Error('过滤表达式为空或过长')
  }
  const expression = script.trim().replace(/^return\s+/, '').replace(/;$/, '').trim()
  const match = SELECTOR_PATTERN.exec(expression)
  if (!match) {
    throw new Error('仅支持 return data、return res 及其字段路径；JavaScript 已禁用')
  }
  const keys: Array<string | number> = []
  for (const segment of match[2].matchAll(SEGMENT_PATTERN)) {
    const raw = segment[1] ?? segment[2]
    if (/^(0|[1-9]\d*)$/.test(raw)) {
      keys.push(Number(raw))
      continue
    }
    const key = raw.startsWith('"')
      ? JSON.parse(raw)
      : (raw.startsWith("'") ? decodeSingleQuotedKey(raw) : raw)
    assertSafeKey(key)
    keys.push(key)
  }
  return { root: match[1] as 'data' | 'res', keys }
}

export const assertRestrictedDataSelector = (script?: string) => {
  if (script) parseSelector(script)
}

export const evaluateRestrictedDataSelector = (script: string, data: unknown, res: unknown) => {
  const selector = parseSelector(script)
  return readPath(selector.root === 'data' ? data : res, selector.keys)
}

export const resolveRestrictedUrlTemplate = (
  template: string,
  values: Record<string, unknown> = {}
) => {
  if (!template || template.length > MAX_TEMPLATE_LENGTH) {
    throw new Error('URL 模板为空或过长')
  }
  const resolved = template.replace(TEMPLATE_TOKEN_PATTERN, (_token, path: string) => {
    const keys = path.split('.')
    keys.forEach(assertSafeKey)
    const value = readPath(values, keys)
    if (value === undefined || value === null) {
      throw new Error(`URL 模板参数 ${path} 缺失`)
    }
    if (!['string', 'number', 'boolean'].includes(typeof value)) {
      throw new Error(`URL 模板参数 ${path} 必须是标量`)
    }
    return encodeURIComponent(String(value))
  })
  if (resolved.includes('${') || /^(?:javascript|data|file):/i.test(resolved.trim())) {
    throw new Error('URL 模板包含不受支持的表达式或协议')
  }
  return resolved
}
