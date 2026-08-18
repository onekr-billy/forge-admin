const PAGE_MODES = ['create', 'edit', 'detail']
const BOTTOM_ACTION_TYPES = ['save', 'navigate', 'process', 'action', 'reset', 'cancel']
const DISPLAY_CONDITION_PATTERN = /^([\w.]+)\s*(==|!=|=)\s*(\S(?:.*\S)?)$/

export function createPageSection(sectionType = 'card', sections = []) {
  const type = sectionType === 'child_table' ? 'child_table' : 'card'
  const index = (Array.isArray(sections) ? sections.length : 0) + 1
  const base = {
    sectionId: reserveId(type === 'card' ? 'content_section' : 'child_section', sections, item => item?.sectionId),
    sectionType: type,
    title: type === 'card' ? `内容分区 ${index}` : `子表分区 ${index}`,
    visibleInModes: [...PAGE_MODES],
  }
  return type === 'card'
    ? { ...base, fields: [], fieldOverrides: {}, collapsible: false, collapsedByDefault: false }
    : {
        ...base,
        relationKey: '',
        displayMode: 'inline_grid',
        inlineCreateEnabled: true,
        inlineEditEnabled: true,
        selectorEnabled: false,
        selectorObjectCode: '',
        selectorButtonText: '选择记录',
        selectorTitle: '',
        selectorDisplayFields: [],
        selectorKeywordFields: [],
        selectorMappings: [],
        selectorFilters: [],
      }
}

export function createBottomAction(type = 'save', actions = []) {
  const normalizedType = BOTTOM_ACTION_TYPES.includes(type) ? type : 'save'
  const presets = {
    save: { label: '保存', variant: 'primary' },
    navigate: { label: '跳转', variant: 'secondary', actionCode: '' },
    process: { label: '发起流程', variant: 'primary', actionCode: '' },
    reset: { label: '重置', variant: 'secondary' },
    action: { label: '执行动作', variant: 'primary', actionCode: '' },
    cancel: { label: '取消', variant: 'secondary' },
  }
  return {
    actionId: reserveId('bottom_action', actions, item => item?.actionId),
    type: normalizedType,
    ...presets[normalizedType],
    visibleInModes: [...PAGE_MODES],
  }
}

export function parseDisplayCondition(condition = '') {
  const match = String(condition || '').trim().match(DISPLAY_CONDITION_PATTERN)
  if (!match)
    return { field: '', operator: '==', value: '' }
  return {
    field: match[1],
    operator: match[2] === '=' ? '==' : match[2],
    value: match[3].replace(/^["']|["']$/g, ''),
  }
}

export function isSupportedDisplayCondition(condition = '') {
  const value = String(condition || '').trim()
  return !value || DISPLAY_CONDITION_PATTERN.test(value)
}

export function serializeDisplayCondition(condition = {}) {
  const field = String(condition.field || '').trim()
  const value = String(condition.value ?? '').trim()
  if (!field || !value)
    return ''
  const operator = condition.operator === '!=' ? '!=' : '=='
  const encodedValue = /\s/.test(value) ? JSON.stringify(value) : value
  return `${field} ${operator} ${encodedValue}`
}

export function resolveVisibleModes(item = {}) {
  const configured = Array.isArray(item?.visibleInModes)
    ? item.visibleInModes.filter(mode => PAGE_MODES.includes(mode))
    : []
  return configured.length ? [...new Set(configured)] : [...PAGE_MODES]
}

export function updateVisibleModes(item = {}, nextModes = []) {
  const normalized = Array.isArray(nextModes)
    ? [...new Set(nextModes.filter(mode => PAGE_MODES.includes(mode)))]
    : []
  return normalized.length ? normalized : resolveVisibleModes(item)
}

export function collectPageSectionWarnings({ pageSections = [], bottomBar = {}, fields = [], relations = [], actions = [] } = {}) {
  const fieldCodes = collectFieldCodes(fields)
  const relationKeys = collectRelationKeys(relations)
  const actionCodes = collectActionCodes(actions)
  const warnings = []

  ;(Array.isArray(pageSections) ? pageSections : []).forEach((section, sectionIndex) => {
    const title = section?.title || `分区 ${sectionIndex + 1}`
    if (section?.sectionType === 'child_table') {
      const relationKey = String(section.relationKey || '').trim()
      if (!relationKey)
        warnings.push({ key: `section:${sectionIndex}:relation`, message: `“${title}”尚未选择子表关系` })
      else if (!relationKeys.has(relationKey))
        warnings.push({ key: `section:${sectionIndex}:relation:${relationKey}`, message: `“${title}”引用的子表关系 ${relationKey} 已失效` })
      if (section.selectorEnabled === true) {
        if (!String(section.selectorObjectCode || '').trim()) {
          warnings.push({
            key: `section:${sectionIndex}:selector:object`,
            message: `“${title}”已启用子表选择器，但尚未配置候选对象`,
          })
        }
        ;(Array.isArray(section.selectorMappings) ? section.selectorMappings : []).forEach((mapping, mappingIndex) => {
          if (!String(mapping?.sourceField || '').trim() || !String(mapping?.targetField || '').trim()) {
            warnings.push({
              key: `section:${sectionIndex}:selector:mapping:${mappingIndex}`,
              message: `“${title}”的第 ${mappingIndex + 1} 条字段映射不完整`,
            })
          }
        })
        ;(Array.isArray(section.selectorFilters) ? section.selectorFilters : []).forEach((filter, filterIndex) => {
          if (!String(filter?.sourceField || '').trim() || !String(filter?.targetParam || '').trim()) {
            warnings.push({
              key: `section:${sectionIndex}:selector:filter:${filterIndex}`,
              message: `“${title}”的第 ${filterIndex + 1} 条筛选条件不完整`,
            })
          }
        })
      }
      return
    }
    const sectionFields = Array.isArray(section?.fields) ? section.fields : []
    if (!sectionFields.length)
      warnings.push({ key: `section:${sectionIndex}:empty`, message: `“${title}”尚未选择字段` })
    sectionFields.forEach((fieldCode) => {
      if (!fieldCodes.has(String(fieldCode)))
        warnings.push({ key: `section:${sectionIndex}:field:${fieldCode}`, message: `“${title}”引用的字段 ${fieldCode} 已失效` })
    })
  })

  ;(Array.isArray(bottomBar?.actions) ? bottomBar.actions : []).forEach((action, actionIndex) => {
    const processBackedAction = action?.type === 'process'
      || action?.actionType === 'START_PROCESS'
      || action?.actionType === 'BUSINESS_PROCESS_ACTION'
    if (processBackedAction) {
      const actionCode = String(action.actionCode || '').trim()
      if (!actionCode)
        warnings.push({ key: `action:${actionIndex}:process`, message: `底部按钮“${action.label || actionIndex + 1}”尚未选择业务流程` })
    }
    else if (action?.type === 'action') {
      const actionCode = String(action.actionCode || '').trim()
      if (!actionCode)
        warnings.push({ key: `action:${actionIndex}:empty`, message: `底部按钮“${action.label || actionIndex + 1}”尚未绑定业务动作` })
      else if (!actionCodes.has(actionCode))
        warnings.push({ key: `action:${actionIndex}:${actionCode}`, message: `底部按钮“${action.label || actionCode}”引用的业务动作 ${actionCode} 已失效` })
    }
    const displayCondition = String(action?.displayCondition || '').trim()
    if (displayCondition && !isSupportedDisplayCondition(displayCondition)) {
      warnings.push({
        key: `action:${actionIndex}:condition:unsupported`,
        message: `底部按钮“${action?.label || actionIndex + 1}”使用了不支持的显示条件：${displayCondition}`,
      })
    }
    else {
      const condition = parseDisplayCondition(displayCondition)
      if (condition.field && !fieldCodes.has(condition.field))
        warnings.push({ key: `action:${actionIndex}:condition:${condition.field}`, message: `底部按钮“${action?.label || actionIndex + 1}”的显示字段 ${condition.field} 已失效` })
    }
  })

  return warnings
}

function collectFieldCodes(fields = []) {
  return new Set((Array.isArray(fields) ? fields : [])
    .flatMap(field => [field?.fieldCode, field?.field, field?.sourceField])
    .map(value => String(value || '').trim())
    .filter(Boolean))
}

function collectRelationKeys(relations = []) {
  const keys = new Set()
  ;(Array.isArray(relations) ? relations : []).forEach((relation) => {
    const config = relation?.relationConfig || relation?.config || relation?.props || {}
    ;[
      relation?.relationKey,
      relation?.key,
      relation?.collectionKey,
      relation?.relationName,
      relation?.targetObjectCode,
      relation?.modelCode,
      config?.relationKey,
      config?.collectionKey,
    ].forEach((value) => {
      const key = String(value || '').trim()
      if (key)
        keys.add(key)
    })
  })
  return keys
}

function collectActionCodes(actions = []) {
  return new Set((Array.isArray(actions) ? actions : [])
    .flatMap(action => [action?.actionCode, action?.key])
    .map(value => String(value || '').trim())
    .filter(Boolean))
}

function reserveId(prefix, items, resolver) {
  const used = new Set((Array.isArray(items) ? items : []).map(resolver).filter(Boolean).map(String))
  if (!used.has(prefix))
    return prefix
  for (let index = 2; index < 1000; index += 1) {
    const candidate = `${prefix}_${index}`
    if (!used.has(candidate))
      return candidate
  }
  return `${prefix}_${Date.now()}`
}

/**
 * 关系选项归一化：relationKey 优先从 relationConfig 中提取，
 * 供子表分区（页面分区/关联子表容器）统一展示与取值。
 */
export function normalizeRelationOption(relation = {}) {
  const config = relation.relationConfig || relation.config || relation.props || {}
  const value = String(relation.relationKey || relation.key || relation.collectionKey || config.relationKey || relation.relationName || '').trim()
  if (!value)
    return null
  const label = relation.detailTabTitle || relation.modelName || relation.targetObjectName || relation.label || relation.relationName || value
  return { label: `${label}（${value}）`, value }
}

/**
 * 选项补齐：已配置但不在候选列表中的值以“（已失效/已有配置）”禁用项展示，
 * 供分区字段、子表关系、底栏条件字段等下拉统一复用。
 */
export function appendMissingOptions(options = [], values = [], suffix = '已失效') {
  const next = [...options]
  const known = new Set(next.map(option => option.value))
  ;(Array.isArray(values) ? values : []).filter(Boolean).forEach((value) => {
    if (!known.has(value)) {
      next.push({ label: `${value}（${suffix}）`, value, disabled: true })
      known.add(value)
    }
  })
  return next
}
