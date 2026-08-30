/**
 * 发起人自选审批人：单选（不会签）是字符串，会签是数组。
 */
export function normalizeInitiatorSelectIds(value) {
  if (Array.isArray(value)) {
    return [...new Set(value
      .map(item => String(item ?? '').trim())
      .filter(Boolean))]
  }
  if (value == null || value === '')
    return []
  const text = String(value).trim()
  return text ? [text] : []
}

export function collectInitiatorSelectSelections(nodes = [], selections = {}) {
  const result = {}
  for (const node of nodes) {
    const nodeKey = String(node?.nodeKey || '').trim()
    if (!nodeKey)
      continue
    const ids = normalizeInitiatorSelectIds(selections?.[nodeKey])
    if (!ids.length) {
      const name = node.nodeName || nodeKey
      throw new Error(`请选择「${name}」的审批人`)
    }
    result[nodeKey] = node.multiple === false ? [ids[0]] : ids
  }
  return result
}
