export function parseOptions(value) {
  if (value && typeof value === 'object')
    return value
  try {
    const parsed = JSON.parse(value || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  }
  catch { return {} }
}

/** 从发布快照提取页面绑定，保留字符串 ID；纯展示页面不能冒充数据表单。 */
export function publishedPageSources(runtime = {}) {
  runtime = runtime || {}
  const builder = parseOptions(runtime.application?.options).inAppBuilder || {}
  const objects = runtime.objects || []
  const results = []
  const add = (id, name, ref) => {
    if (!ref || ref.valid === false)
      return
    const refId = ref.objectId ?? ref.id
    const object = objects.find(item => refId
      ? String(item.objectId) === String(refId)
      : ref.objectCode ? item.objectCode === ref.objectCode : ref.configKey && item.configKey === ref.configKey)
    if (object && !results.some(item => item.value === id)) {
      results.push({ value: id, label: name, objectId: object.objectId, objectCode: object.objectCode, suiteCode: object.suiteCode })
    }
  }
  for (const node of builder.nodes || []) {
    if (node.type !== 'page')
      continue
    const page = builder.pages?.[node.id] || {}
    const fallback = page.objectRef || node.objectRef
    const pageStart = results.length
    const visit = (blocks) => {
      for (const block of blocks || []) {
        if (['AiCrudPage', 'AiForm'].includes(block.blockType)) {
          add(`${node.id}/${block.id}`, `${node.title || node.name || '页面'} · ${block.title || block.props?.title || '数据表单'}`, block.props?.objectRef || block.props?.businessObjectRef || block.objectRef || fallback)
        }
        visit(block.children)
        for (const tab of block.props?.tabs || [])
          visit(tab.children)
        for (const cell of block.props?.cells || [])
          visit(cell.children)
      }
    }
    visit(page.layout?.gridLayout?.items || page.layout?.items)
    if (results.length === pageStart)
      add(String(node.id), node.title || node.name || '未命名页面', fallback)
  }
  for (const entry of runtime.entries || [])
    add(`entry/${entry.id}`, entry.appName || entry.objectName, entry)
  return results
}

export function registrationScenario(sourceType, sourceKey = '') {
  if (sourceKey === 'system.rest.invoke')
    return 'rest'
  if (sourceType === 'BUSINESS_ACTION' || sourceKey === 'lowcode.form.create')
    return 'application'
  return 'flow'
}
