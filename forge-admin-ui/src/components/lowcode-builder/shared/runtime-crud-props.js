/**
 * 将已发布的 CRUD 配置转换为 AiCrudPage 的运行参数。
 *
 * 列表设计器预览和应用页面都使用这份轻量桥接：接口、表单和列表字段始终
 * 来自同一个 configKey，应用设计器只允许覆盖外观与局部行为，不另存一套接口。
 */
export function buildRuntimeCrudProps(config = {}, { designPreview = false } = {}) {
  const options = config.options || {}
  const formOpenMode = resolveFormOpenMode(options, config)
  const configKey = String(config.configKey || '').trim()
  const apiConfig = normalizeApiConfig(config.apiConfig, configKey, designPreview)
  return {
    searchSchema: normalizeFields(config.searchSchema),
    columns: normalizeColumns(config.columnsSchema, config.transConfig),
    editSchema: normalizeFields(config.editSchema),
    fieldCatalog: buildRuntimeFieldCatalog(config),
    childrenConfig: options.masterDetailConfig?.children || [],
    expandConfig: options.expandConfig || config.expandConfig || {},
    detailPanels: options.detailPanels || config.detailPanels || [],
    apiConfig,
    configKey,
    designPreview,
    options,
    rowKey: config.rowKey || 'id',
    formOpenMode,
    tabWorkspace: options.tabWorkspace || config.tabWorkspace || {},
    modalType: resolveModalType(formOpenMode, options, config),
    modalWidth: options.modalWidth || config.modalWidth || '800px',
    editGridCols: options.editGridCols || config.editGridCols || 1,
    editLabelWidth: options.editLabelWidth || config.editLabelWidth || 'auto',
    editLabelPlacement: options.editLabelPlacement || config.editLabelPlacement || 'left',
    editLabelAlign: options.editLabelAlign || config.editLabelAlign || 'right',
    editSize: options.editSize || config.editSize || 'medium',
    editShowFeedback: options.editShowFeedback ?? config.editShowFeedback ?? true,
    editFormClass: options.editFormClass || config.editFormClass || '',
    editFormStyle: options.editFormStyle || config.editFormStyle,
    formAssets: options.formAssets || config.formAssets || [],
    editXGap: numberOption(options.editXGap ?? config.editXGap, 12),
    editYGap: numberOption(options.editYGap ?? config.editYGap, 8),
    tableRowGap: normalizeTableRowGap(options.tableRowGap ?? config.tableRowGap, 8),
    loadDetailOnEdit: options.loadDetailOnEdit ?? config.loadDetailOnEdit ?? true,
    searchGridCols: options.searchGridCols || config.searchGridCols || 4,
    showSearch: options.showSearch ?? config.showSearch ?? true,
    showPagination: options.showPagination ?? config.showPagination ?? true,
    hideAdd: options.hideAdd ?? config.hideAdd ?? false,
    hideBatchDelete: options.hideBatchDelete ?? config.hideBatchDelete ?? false,
    showImport: options.showImport ?? config.showImport ?? false,
    showExport: options.showExport ?? config.showExport ?? false,
    enableCustomQuery: options.enableCustomQuery ?? config.enableCustomQuery ?? true,
    customQueryConfigKey: config.configKey || '',
    publicParams: { ...(options.publicParams || config.publicParams || {}) },
    publicQuery: { ...(options.publicQuery || config.publicQuery || {}) },
    formDefaultValues: { ...(options.formDefaultValues || config.formDefaultValues || {}) },
    submitDefaultParams: { ...(options.submitDefaultParams || config.submitDefaultParams || {}) },
    toolbarActions: Array.isArray(options.toolbarActions) ? options.toolbarActions : [],
    runtimeActions: Array.isArray(options.runtimeActions) ? options.runtimeActions : [],
    businessObjectCode: config.objectCode || options.businessObjectCode || '',
  }
}

function normalizeApiConfig(apiConfig, configKey, designPreview) {
  return Object.fromEntries(Object.entries(apiConfig || {}).map(([key, value]) => {
    const resolved = resolveCurrentConfigPlaceholder(value, configKey)
    return [key, designPreview ? appendDesignPreviewToApiValue(resolved) : resolved]
  }))
}

export function appendDesignPreviewToApiValue(value) {
  const text = String(value || '').trim()
  if (!text || text.includes('designPreview='))
    return text
  return `${text}${text.includes('?') ? '&' : '?'}designPreview=1`
}

export function isDesignPreviewCrudProps(runtimeCrudProps = {}) {
  return runtimeCrudProps.designPreview === true || runtimeCrudProps.draftOnly === true
}

export function resolveCurrentConfigPlaceholder(value, configKey) {
  const text = String(value || '').trim()
  if (!text)
    return ''
  if (!text.includes('/ai/crud/当前配置'))
    return text
  // 没有真实 configKey 时不要把占位字符串传到运行时，避免请求当前页面 URL。
  if (!configKey)
    return ''
  return text.replaceAll('/ai/crud/当前配置', `/ai/crud/${configKey}`)
}

export function resolveRuntimeBlockApi(value, configKey, designPreview = false) {
  const resolved = resolveCurrentConfigPlaceholder(value, configKey)
  return designPreview ? appendDesignPreviewToApiValue(resolved) : resolved
}

/** 将页面区块的列布局覆盖到已编译的运行列上。 */
export function applyTableColumnLayout(columns = [], blockProps = {}) {
  const globalAlign = normalizeAlign(blockProps.globalAlign)
  const settings = blockProps.fieldSettings && typeof blockProps.fieldSettings === 'object'
    ? blockProps.fieldSettings
    : {}
  return (Array.isArray(columns) ? columns : []).map((column) => {
    const key = column?.prop || column?.key || column?.dataIndex || ''
    const fieldAlign = normalizeAlign(settings[key]?.align)
    const align = fieldAlign || globalAlign
    return align
      ? { ...column, align, titleAlign: align }
      : { ...column }
  })
}

export function normalizeTableRowGap(value, fallback = 8) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.min(32, number)) : fallback
}

/**
 * 解析 CRUD 区块自己的查询字段目录。
 *
 * 新协议以 props.searchFieldRefs 为准；只有旧区块没有该属性时，才兼容使用
 * 列表 fieldRefs，避免列表列调整后把查询条件错误地一起改掉。
 */
export function filterCrudItemsByFieldRefs(items = [], fieldRefs = []) {
  if (!Array.isArray(items) || !items.length)
    return Array.isArray(items) ? items : []
  if (!Array.isArray(fieldRefs) || !fieldRefs.length)
    return items
  const allow = new Set(fieldRefs.filter(Boolean).map(String))
  return items.filter((item) => {
    const key = String(item?.prop || item?.field || item?.key || item?.dataIndex || '').trim()
    if (!key || item?.type === 'action' || item?.fixed === 'right' || key === 'action')
      return true
    return allow.has(key)
  })
}

export function resolveCrudSearchFieldCatalog(fields = [], block = {}) {
  const fieldMap = new Map((Array.isArray(fields) ? fields : []).flatMap((field) => {
    const fieldCode = field?.field || field?.fieldCode || field?.prop || field?.key
    return fieldCode ? [[fieldCode, { ...field, field: fieldCode, fieldCode }]] : []
  }))
  const hasSearchFieldRefs = Object.prototype.hasOwnProperty.call(block.props || {}, 'searchFieldRefs')
  const refs = hasSearchFieldRefs ? block.props?.searchFieldRefs : block.fieldRefs
  return (Array.isArray(refs) ? refs : [])
    .map((fieldCode) => {
      const sourceField = fieldMap.get(fieldCode)
      if (!sourceField)
        return null
      const setting = block.props?.searchFieldSettings?.[fieldCode] || {}
      const requestedQueryField = String(setting.queryField || '').trim()
      const queryField = fieldMap.get(requestedQueryField) || sourceField
      const queryFieldCode = queryField.field
      return {
        ...sourceField,
        ...queryField,
        ...setting,
        field: queryFieldCode,
        fieldCode: queryFieldCode,
        sourceField: fieldCode,
        label: setting.label || sourceField.label || sourceField.fieldName || fieldCode,
        componentType: setting.componentType || queryField.componentType || sourceField.componentType || '',
        queryType: setting.queryType || queryField.queryType || sourceField.queryType || 'eq',
      }
    })
    .filter(Boolean)
}

const SUPPORTED_SEARCH_TYPES = new Set([
  'eq',
  'ne',
  'like',
  'left_like',
  'right_like',
  'gt',
  'ge',
  'gte',
  'lt',
  'le',
  'lte',
  'in',
  'between',
  'is_null',
  'is_not_null',
])

/**
 * 页面查询方式属于动态 CRUD 控制信息，和用户输入值分开传输。
 */
export function buildCrudSearchTypeRequestParams(searchSchema = []) {
  const searchTypes = {}
  ;(Array.isArray(searchSchema) ? searchSchema : []).forEach((field) => {
    const fieldCode = String(field?.field || '').trim()
    const queryType = String(field?.queryType || field?.searchType || '').trim().toLowerCase()
    if (fieldCode && SUPPORTED_SEARCH_TYPES.has(queryType))
      searchTypes[fieldCode] = queryType
  })
  return Object.keys(searchTypes).length
    ? { _searchTypes: JSON.stringify(searchTypes) }
    : {}
}

/**
 * 构造真实 CRUD 预览的稳定请求身份。
 *
 * 预览结果状态会写回 block.props，但它不属于请求条件，不能因此再次加载列表。
 */
export function resolveCrudPreviewReloadKey(block = {}, runtimeCrudProps = {}) {
  const sourceBlock = block || {}
  const runtimeProps = runtimeCrudProps || {}
  const blockProps = sourceBlock.props || {}
  const previewLiveData = blockProps.previewLiveData === true
  return JSON.stringify({
    enabled: previewLiveData,
    mode: blockProps.previewMode || (previewLiveData ? 'realList' : 'mock'),
    recordId: String(blockProps.previewRecordId ?? ''),
    listApi: blockProps.listApi
      || blockProps.api
      || runtimeProps.apiConfig?.list
      || runtimeProps.api
      || '',
  })
}

function normalizeFields(fields) {
  return (Array.isArray(fields) ? fields : []).map(field => ({ ...field }))
}

/** 供应用页属性面板使用的统一字段目录，和实际 CRUD 列表/弹窗同源。 */
function buildRuntimeFieldCatalog(config = {}) {
  const fields = new Map()
  const append = (source = [], patch = {}) => {
    ;(Array.isArray(source) ? source : []).forEach((item) => {
      const field = item?.field || item?.fieldCode || item?.prop || item?.key || item?.dataIndex
      if (!field || ['action', 'actions', 'operation', 'operations'].includes(field))
        return
      const current = fields.get(field) || {}
      fields.set(field, {
        ...current,
        ...item,
        ...patch,
        field,
        fieldCode: field,
        sourceField: item.sourceField || current.sourceField || field,
        fieldName: item.fieldName || item.label || item.title || current.fieldName || field,
        label: item.label || item.title || item.fieldName || current.label || field,
        listVisible: patch.listVisible ?? item.listVisible ?? current.listVisible ?? false,
        formVisible: patch.formVisible ?? item.formVisible ?? current.formVisible ?? false,
        fieldStatus: item.fieldStatus || current.fieldStatus || 'ENABLED',
      })
    })
  }
  append(config.modelSchema?.fields || [])
  append(config.editSchema, { formVisible: true })
  append(config.columnsSchema, { listVisible: true })
  append(config.searchSchema, { searchable: true })
  return [...fields.values()]
}

function normalizeColumns(columns, transConfig = {}) {
  return (Array.isArray(columns) ? columns : []).map((column) => {
    const key = column.prop || column.key || column.dataIndex || ''
    const next = { ...column, key, prop: key }
    const transform = transConfig?.[key]
    if (transform?.targetField && !next.render)
      next.renderConfig = { ...(next.renderConfig || {}), targetField: transform.targetField, type: transform.type }
    return next
  })
}

function resolveFormOpenMode(options = {}, config = {}) {
  const value = String(options.formOpenMode || config.formOpenMode || options.modalType || config.modalType || 'modal').trim()
  return value.toLowerCase() === 'tabworkspace' ? 'tabWorkspace' : (['modal', 'drawer', 'flat'].includes(value.toLowerCase()) ? value.toLowerCase() : 'modal')
}

function resolveModalType(formOpenMode, options = {}, config = {}) {
  if (['modal', 'drawer'].includes(formOpenMode))
    return formOpenMode
  const modalType = String(options.modalType || config.modalType || '').trim().toLowerCase()
  return ['modal', 'drawer'].includes(modalType) ? modalType : 'modal'
}

function numberOption(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : fallback
}

function normalizeAlign(value) {
  const align = String(value || '').trim().toLowerCase()
  return ['left', 'center', 'right'].includes(align) ? align : ''
}
