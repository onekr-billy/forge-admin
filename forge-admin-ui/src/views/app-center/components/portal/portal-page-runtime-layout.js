import { resolveCrudFormOnly } from '@/components/lowcode-builder/shared/runtime-crud-page-mode'
import { isDataFieldBlockType } from '@/components/lowcode-builder/page/page-schema'

/** 介绍/模板残留的未改文案占位；对象数据页上不应盖住 CRUD。 */
export const DEFAULT_INFO_PANEL_PLACEHOLDER = Object.freeze({
  title: '提示信息',
  content: '在这里展示当前页面的说明、风险提醒或操作结果。',
})

export const DEFAULT_EMPTY_STATE_PLACEHOLDER = Object.freeze({
  title: '暂无数据',
  description: '当前条件下没有可展示的数据',
})

export function isRuntimeAutoHeightBlock(block = {}) {
  if (block?.blockType === 'AiForm')
    return true
  if (block?.blockType !== 'AiCrudPage')
    return false
  return resolveCrudFormOnly(block.props)
}

export function shouldUseContentSizedFlow(blocks = [], options = {}) {
  if (options.fillHost)
    return false
  const items = Array.isArray(blocks) ? blocks.filter(Boolean) : []
  return items.length > 0 && items.every(isRuntimeAutoHeightBlock)
}

export function isDefaultInfoPanelPlaceholder(block = {}) {
  if (block?.blockType !== 'info-panel')
    return false
  const title = String(block?.props?.title ?? DEFAULT_INFO_PANEL_PLACEHOLDER.title).trim()
    || DEFAULT_INFO_PANEL_PLACEHOLDER.title
  const content = String(block?.props?.content || '').trim()
  return title === DEFAULT_INFO_PANEL_PLACEHOLDER.title
    && content === DEFAULT_INFO_PANEL_PLACEHOLDER.content
}

export function isDefaultEmptyStatePlaceholder(block = {}) {
  if (block?.blockType !== 'empty-state')
    return false
  const title = String(block?.props?.title ?? DEFAULT_EMPTY_STATE_PLACEHOLDER.title).trim()
    || DEFAULT_EMPTY_STATE_PLACEHOLDER.title
  const description = String(block?.props?.description || '').trim()
  return title === DEFAULT_EMPTY_STATE_PLACEHOLDER.title
    && description === DEFAULT_EMPTY_STATE_PLACEHOLDER.description
}

/** 对象页上未定制过的装饰占位（提示面板 / 空状态），会叠在 CRUD 上层误导用户。 */
export function isUntouchedDecorativePlaceholder(block = {}) {
  return isDefaultInfoPanelPlaceholder(block) || isDefaultEmptyStatePlaceholder(block)
}

/**
 * 解析门户页应渲染的区块。
 * - 优先 gridLayout.items；否则回退旧 layout.items
 * - 对象页若只有装饰块或没有数据块，补一个 AiCrudPage
 * - 对象页过滤未改过的提示面板 / 空状态占位，避免盖住列表
 */
export function resolvePortalPageBlocks({
  page,
  node,
  resolveObjectRef,
  normalizeLegacyBlock,
} = {}) {
  const layout = page?.layout || {}
  const gridItems = Array.isArray(layout.gridLayout?.items) ? layout.gridLayout.items : null
  const legacyItems = Array.isArray(layout.items) && typeof normalizeLegacyBlock === 'function'
    ? layout.items.map((item, index) => normalizeLegacyBlock(item, index))
    : (Array.isArray(layout.items) ? layout.items : [])
  const rawItems = (gridItems && gridItems.length > 0) ? gridItems : legacyItems
  let items = (Array.isArray(rawItems) ? rawItems : []).filter(item => item?.blockType !== 'page-title')

  const objectRef = typeof resolveObjectRef === 'function' ? resolveObjectRef(node || {}) : null
  const isObjectPage = node?.pageType === 'object' && Boolean(objectRef)
  if (isObjectPage) {
    items = items.filter(item => !isUntouchedDecorativePlaceholder(item))
    const hasDataBlock = items.some(item => isDataFieldBlockType(item?.blockType))
    if (!hasDataBlock) {
      items = [{
        id: `portal-object-${node.id}`,
        blockType: 'AiCrudPage',
        props: {
          objectRef,
          style: { widthMode: 'full', heightMode: 'full', pageFlowHeight: 640 },
        },
      }, ...items]
    }
  }
  return items
}
