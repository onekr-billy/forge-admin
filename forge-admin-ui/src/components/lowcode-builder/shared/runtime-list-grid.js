/**
 * 列表自由布局（listGridLayout / pages[].gridLayout）在运行态的解析与判定。
 *
 * 标准列表入口历史上只渲 AiCrudPage，把 listGridLayout 当设计元数据；
 * 当画布上还有树面板、标题、提示等伴生块时，门户改走 RuntimeListGridFlow（纵向文档流），
 * 禁止嵌套完整 ListPageGridDesigner，避免设计态画布样式泄漏。
 */

const CRUD_ONLY_BLOCK_TYPES = new Set(['AiCrudPage', 'AiTable', 'data-table'])

export function resolveListGridLayoutFromPageSchema(pageSchema = {}, { pageKey = 'list' } = {}) {
  const key = String(pageKey || 'list').trim() || 'list'
  const pages = Array.isArray(pageSchema?.pages) ? pageSchema.pages : []
  const page = pages.find(item => String(item?.pageKey || '').trim() === key) || null
  if (page?.gridLayout && Array.isArray(page.gridLayout.items))
    return page.gridLayout
  if (key !== 'list')
    return null
  const layout = pageSchema?.listGridLayout
  if (!layout || !Array.isArray(layout.items))
    return null
  return layout
}

export function normalizeRuntimeListGridLayout(layout = null, pageSchema = {}, fallbackLayoutType = 'simple-crud') {
  if (!layout || !Array.isArray(layout.items) || !layout.items.length)
    return null
  const items = layout.items.map(item => normalizeFlowBlockItem(item))
  return {
    ...layout,
    items,
    layoutType: layout.layoutType
      || pageSchema?.layoutType
      || fallbackLayoutType
      || 'simple-crud',
  }
}

/** 去掉设计态绝对定位尺寸，改为运行态通栏文档流可用的块配置 */
export function normalizeFlowBlockItem(item = {}) {
  if (!item || typeof item !== 'object')
    return item
  const style = { ...(item.props?.style || {}) }
  const isCrud = item.blockType === 'AiCrudPage'
  // heightMode=full / 设计态固定 px 高会在门户里裁切下方组件
  style.heightMode = 'auto'
  style.widthMode = 'full'
  delete style.width
  delete style.height
  delete style.minWidth
  delete style.maxWidth
  const nextProps = {
    ...(item.props || {}),
    style,
  }
  if (isCrud) {
    nextProps.previewLiveData = true
    nextProps.previewMode = 'realList'
  }
  return {
    ...item,
    props: nextProps,
  }
}

export function buildRuntimeListFlowBlocks(layout = null) {
  const items = Array.isArray(layout?.items) ? layout.items.filter(Boolean) : []
  return [...items]
    .map(normalizeFlowBlockItem)
    .sort((left, right) => {
      const leftY = Number(left?.props?.style?.pageFlowY)
      const rightY = Number(right?.props?.style?.pageFlowY)
      const safeLeft = Number.isFinite(leftY) ? leftY : 0
      const safeRight = Number.isFinite(rightY) ? rightY : 0
      if (safeLeft !== safeRight)
        return safeLeft - safeRight
      return String(left?.id || '').localeCompare(String(right?.id || ''))
    })
}

/** 画布上是否有 CRUD 以外的可见伴生块（树、标题、提示、统计等） */
export function isRichListGridLayout(layout = null) {
  const items = Array.isArray(layout?.items) ? layout.items.filter(Boolean) : []
  if (items.length <= 1)
    return false
  return items.some(item => item?.blockType && !CRUD_ONLY_BLOCK_TYPES.has(item.blockType))
}

export function shouldRenderRuntimeListGrid({
  pageSchema = null,
  modelSchema = null,
  layoutType = '',
  formOnly = false,
  pageKey = 'list',
} = {}) {
  if (formOnly || !pageSchema)
    return false
  const raw = resolveListGridLayoutFromPageSchema(pageSchema, { pageKey })
  const normalized = normalizeRuntimeListGridLayout(
    raw,
    pageSchema,
    layoutType || pageSchema?.layoutType || 'simple-crud',
  )
  if (!normalized)
    return false
  // 非 list 页（详情/自定义页）有 grid 就渲；list 页仅在有伴生块时改走自由布局
  const key = String(pageKey || 'list').trim() || 'list'
  if (key !== 'list')
    return true
  return isRichListGridLayout(normalized)
}

export function resolveRuntimeListGridModel(pageSchema = null, {
  modelSchema = null,
  layoutType = '',
  pageKey = 'list',
} = {}) {
  const raw = resolveListGridLayoutFromPageSchema(pageSchema || {}, { pageKey })
  const layout = normalizeRuntimeListGridLayout(
    raw,
    pageSchema || {},
    layoutType || pageSchema?.layoutType || 'simple-crud',
  )
  if (!layout)
    return null
  const fields = Array.isArray(modelSchema?.fields)
    ? modelSchema.fields.map(field => ({
        ...field,
        field: field.field || field.fieldCode || field.columnName || '',
        label: field.label || field.fieldName || field.field || field.fieldCode || field.columnName || '',
        componentType: field.componentType || field.componentKey || field.dataType || 'input',
      })).filter(field => field.field)
    : []
  return {
    layout: {
      ...layout,
      items: buildRuntimeListFlowBlocks(layout),
    },
    fields,
    layoutType: layout.layoutType || layoutType || pageSchema?.layoutType || 'simple-crud',
    modelSchema: modelSchema || {},
  }
}
