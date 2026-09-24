/**
 * 远程数据源探测：从接口返回 JSON 抽取可选字段路径，供 WidgetDataBindingEditor 选字段。
 */

export function getNestedJsonValue(source, path = '') {
  const text = String(path || '').trim()
  if (!text)
    return source
  return text.split('.').reduce((acc, key) => {
    if (acc == null || typeof acc !== 'object')
      return undefined
    return acc[key]
  }, source)
}

/**
 * @returns {{ path: string, type: string, label: string, sample?: any }[]}
 */
export function extractJsonFieldPaths(root, { maxDepth = 5, includeContainers = true } = {}) {
  const results = []
  const seen = new Set()

  function push(entry) {
    const key = `${entry.path}|${entry.type}`
    if (!entry.path || seen.has(key))
      return
    seen.add(key)
    results.push(entry)
  }

  function walk(node, path, depth) {
    if (depth > maxDepth || node === undefined)
      return
    if (node === null) {
      if (path)
        push({ path, type: 'null', label: path, sample: null })
      return
    }
    if (Array.isArray(node)) {
      if (includeContainers && path)
        push({ path, type: 'array', label: `${path}[]`, sample: `array(${node.length})` })
      if (node.length)
        walk(node[0], path, depth)
      return
    }
    if (typeof node === 'object') {
      if (includeContainers && path)
        push({ path, type: 'object', label: path, sample: 'object' })
      Object.keys(node).forEach((key) => {
        const next = path ? `${path}.${key}` : key
        walk(node[key], next, depth + 1)
      })
      return
    }
    if (path)
      push({ path, type: typeof node, label: path, sample: node })
  }

  walk(root, '', 0)
  return results
}

/** 取 dataPath 指向节点后，供槽位映射的「相对字段」（列表则取首条） */
export function resolveProbeSlotSource(response, dataPath = '') {
  const nested = getNestedJsonValue(response, dataPath)
  if (Array.isArray(nested))
    return nested[0] && typeof nested[0] === 'object' ? nested[0] : null
  if (nested && typeof nested === 'object')
    return nested
  return null
}

export function buildRemoteFieldSelectOptions(paths = [], { onlyLeaves = false } = {}) {
  return (paths || [])
    .filter(item => item?.path)
    .filter(item => !onlyLeaves || !['array', 'object'].includes(item.type))
    .map(item => ({
      label: item.type === 'array'
        ? `${item.path}（数组）`
        : item.sample != null && item.sample !== 'object' && !String(item.sample).startsWith('array(')
          ? `${item.path}  ·  ${formatSample(item.sample)}`
          : item.path,
      value: item.path,
    }))
}

function formatSample(value) {
  if (typeof value === 'string')
    return value.length > 24 ? `${value.slice(0, 24)}…` : value
  if (typeof value === 'number' || typeof value === 'boolean')
    return String(value)
  return ''
}

export function normalizeRemoteProbeParams(value = '{}') {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value || '{}') : value
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  }
  catch {
    return {}
  }
}
