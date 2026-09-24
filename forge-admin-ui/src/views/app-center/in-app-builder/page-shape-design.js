import { createGridBlock } from '@/components/lowcode-builder/page/page-schema'
import { createDefaultFormDesignerSchema } from '@/views/app-center/components/designer/form-first/formDesignerSchema'
import { normalizeObjectCode } from '@/views/app-center/components/designer/form-first/namingUtils'
import { createInAppFormAsset, createNavigationNode } from './in-app-builder-schema'
import {
  PAGE_DATA_SOURCE_CREATE,
  PAGE_DATA_SOURCE_EXISTING_TABLE,
  PAGE_OBJECT_CREATE_BLANK,
  PAGE_OBJECT_CREATE_DB_IMPORT,
  resolvePageCreateMode,
} from './page-table-import'

export const PAGE_SHAPE_TYPES = Object.freeze([
  {
    value: 'form',
    label: '表单页',
    description: '适合登记、申请和单条数据维护',
  },
  {
    value: 'list',
    label: '列表页',
    description: '适合查询、筛选和批量管理数据',
  },
  {
    value: 'list-form',
    label: '列表 + 表单',
    description: '在同一工作区浏览列表并编辑数据',
  },
  {
    value: 'custom',
    label: '自由布局',
    description: '空白画布，自由拖入组件搭建页面，不强制绑定数据对象',
  },
])

const PAGE_SHAPE_VALUES = new Set(PAGE_SHAPE_TYPES.map(item => item.value))

export function normalizePageShapeSelection(selection = {}) {
  const pageName = String(selection.pageName || '').trim() || '未命名页面'
  const pageType = PAGE_SHAPE_VALUES.has(selection.pageType) ? selection.pageType : 'form'
  const objectName = String(selection.objectName || '').trim() || pageName
  const customPage = pageType === 'custom'
  const dataSourceMode = selection.dataSourceMode === PAGE_DATA_SOURCE_EXISTING_TABLE
    ? PAGE_DATA_SOURCE_EXISTING_TABLE
    : PAGE_DATA_SOURCE_CREATE
  const createMode = customPage ? '' : resolvePageCreateMode({ ...selection, dataSourceMode })
  const runtimeDatasourceId = customPage ? null : normalizeId(selection.runtimeDatasourceId)
  const importTableName = !customPage && createMode === PAGE_OBJECT_CREATE_DB_IMPORT
    ? String(selection.importTableName || '').trim()
    : ''
  return {
    pageName,
    pageType,
    objectName,
    objectCode: customPage
      ? ''
      : normalizeObjectCode(selection.objectCode, objectName),
    parentId: selection.parentId || null,
    dataSourceMode: customPage ? '' : dataSourceMode,
    createMode,
    runtimeDatasourceId,
    importDatasourceId: importTableName
      ? normalizeId(selection.importDatasourceId || selection.runtimeDatasourceId)
      : null,
    importTableName,
    fields: Array.isArray(selection.fields) ? selection.fields : [],
  }
}

export function createPageShapeBuilder(schema, selection = {}) {
  const normalized = normalizePageShapeSelection(selection)
  const customPage = normalized.pageType === 'custom'
  const pageMode = resolvePageMode(normalized.pageType)
  const nodeResult = createNavigationNode(schema, {
    type: 'page',
    title: normalized.pageName,
    parentId: normalized.parentId,
    pageType: customPage ? 'content' : 'object',
    pageTemplate: customPage ? 'custom' : normalized.pageType,
    pageShape: customPage ? 'custom' : normalized.pageType,
    objectRef: customPage
      ? null
      : buildPageObjectRef(selection, normalized, pageMode),
  })
  const pageId = nodeResult.nodes.at(-1)?.id || ''
  if (customPage)
    return { schema: nodeResult, pageId, formAssetId: '', selection: normalized }

  const importedFields = normalized.fields.filter(field => field && (field.fieldCode || field.field))
  const assetResult = createInAppFormAsset(nodeResult, {
    name: normalized.pageName,
    formKey: `${normalized.objectCode}_form`,
    formDesignerSchema: createDefaultFormDesignerSchema({
      objectCode: normalized.objectCode,
      objectName: normalized.objectName,
      formName: normalized.pageName,
      formOpenMode: normalized.pageType === 'list-form' ? 'flat' : 'modal',
      fields: importedFields,
    }),
  })
  const crudBlock = createGridBlock('AiCrudPage', { fields: [] }, { gridX: 0, gridY: 0 })
  const objectRef = buildPageObjectRef(selection, normalized, pageMode)
  const importedFieldCodes = importedFields.map(field => field.fieldCode || field.field).filter(Boolean)
  const block = {
    ...crudBlock,
    label: normalized.pageName,
    props: {
      ...(crudBlock.props || {}),
      title: normalized.pageName,
      formAssetId: assetResult.formAssetId,
      formAssetFieldsInitialized: importedFieldCodes.length > 0,
      ...(importedFieldCodes.length ? { fieldRefs: importedFieldCodes } : {}),
      objectRef,
      ...(normalized.pageType === 'form'
        ? { formOnly: true, hideToolbar: true, hideBatchDelete: true, showSearch: false }
        : {}),
      ...(normalized.pageType === 'list-form'
        ? { formOpenMode: 'flat', modalType: 'flat' }
        : {}),
    },
  }
  const page = assetResult.schema.pages[pageId]
  const nextSchema = {
    ...assetResult.schema,
    pages: {
      ...assetResult.schema.pages,
      [pageId]: {
        ...page,
        layout: {
          ...page.layout,
          gridLayout: {
            cols: 12,
            rowHeight: 32,
            gap: 8,
            designWidth: 1366,
            layoutType: normalized.pageType,
            items: [block],
          },
        },
      },
    },
  }
  return {
    schema: nextSchema,
    pageId,
    formAssetId: assetResult.formAssetId,
    selection: normalized,
  }
}

/**
 * 解析页面形态（纯函数，便于单测）。
 * custom → 自由布局；form / list / list-form → 对象页。
 *
 * @param {object|null} node 页面节点
 * @param {{ designTab?: string }} [options]
 */
export function resolvePageShapeFromNode(node = null, options = {}) {
  const designTab = String(Array.isArray(options.designTab) ? options.designTab[0] : (options.designTab ?? '')).trim()
  // URL/入口明确要求自由布局时，不再回落到表单/列表（否则中间空白）
  if (designTab === 'page')
    return 'custom'

  if (!node || node.type !== 'page')
    return 'custom'

  const explicit = String(node.pageShape || '').trim().toLowerCase()
  if (explicit === 'list_form')
    return 'list-form'
  if (['custom', 'form', 'list', 'list-form'].includes(explicit))
    return explicit

  const template = String(node.pageTemplate || '').trim().toLowerCase()
  const pageType = String(node.pageType || '').trim().toLowerCase()
  const pageMode = String(node.objectRef?.pageMode || '').trim().toLowerCase()

  if (pageType === 'content' || pageType === 'home' || pageType === 'intro'
    || template === 'blank' || template === 'intro' || template === 'custom'
    || template === 'home'
    || !node.objectRef)
    return 'custom'

  if (template === 'form')
    return 'form'
  if (template === 'list')
    return 'list'
  if (template === 'list-form' || template === 'list_form')
    return 'list-form'

  if (pageType === 'object') {
    if (pageMode === 'form')
      return 'form'
    if (pageMode === 'list')
      return 'list'
    return 'list-form'
  }
  return 'custom'
}

/** 将误判的自由布局节点写回明确的 pageShape，避免下次再丢 Tab。 */
export function ensureFreeLayoutPageNode(node = null) {
  if (!node || node.type !== 'page')
    return node
  // 已绑定数据对象 / 明确是表单列表形态的页面，绝不能改成自由布局
  if (node.objectRef)
    return node
  const shape = String(node.pageShape || node.pageTemplate || '').trim().toLowerCase()
  if (['form', 'list', 'list-form', 'list_form'].includes(shape))
    return node
  if (String(node.pageType || '').trim().toLowerCase() === 'object')
    return node
  if (String(node.pageShape || '').trim().toLowerCase() === 'custom'
    && ['content', 'home', 'intro'].includes(String(node.pageType || '').trim().toLowerCase()))
    return node
  return {
    ...node,
    pageShape: 'custom',
    pageTemplate: String(node.pageTemplate || '').trim() || 'custom',
    pageType: ['content', 'home', 'intro'].includes(String(node.pageType || '').trim().toLowerCase())
      ? node.pageType
      : 'content',
  }
}

/**
 * 对象页（表单/列表/列表+表单）布局是否被自由布局组件污染，或丢失了 AiCrudPage。
 */
export function isObjectBoundPageLayoutPolluted(node = null, page = null) {
  if (!node || node.type !== 'page')
    return false
  const shape = resolvePageShapeFromNode(node)
  const objectBound = Boolean(node.objectRef)
    || shape === 'form'
    || shape === 'list'
    || shape === 'list-form'
    || String(node.pageType || '').trim().toLowerCase() === 'object'
  if (!objectBound)
    return false

  const items = Array.isArray(page?.layout?.gridLayout?.items)
    ? page.layout.gridLayout.items
    : []
  const contentItems = items.filter(item => item && item.blockType !== 'page-title')
  const hasCrud = contentItems.some(item => item.blockType === 'AiCrudPage')
  const hasForeign = contentItems.some(item => item.blockType && item.blockType !== 'AiCrudPage')
  // 误写成自由布局元数据，但还挂着 objectRef
  const shapeCorrupted = Boolean(node.objectRef)
    && (String(node.pageType || '').trim().toLowerCase() === 'content'
      || String(node.pageShape || '').trim().toLowerCase() === 'custom')
  if (shapeCorrupted)
    return true
  if (!hasCrud)
    return true
  return hasForeign
}

/**
 * 把被自由布局污染的对象页恢复成单一 AiCrudPage（保留原表单资产与 objectRef）。
 */
export function restoreObjectBoundPageLayout(schema, pageId, options = {}) {
  const next = {
    ...schema,
    nodes: Array.isArray(schema?.nodes) ? [...schema.nodes] : [],
    pages: { ...(schema?.pages || {}) },
    formAssets: Array.isArray(schema?.formAssets) ? schema.formAssets : [],
  }
  const index = next.nodes.findIndex(item => String(item?.id) === String(pageId || ''))
  if (index < 0)
    throw new Error('页面不存在')
  const node = next.nodes[index]
  const page = next.pages[pageId] || { title: node.title, layout: {} }
  const existingItems = Array.isArray(page?.layout?.gridLayout?.items)
    ? page.layout.gridLayout.items
    : []
  const existingCrud = existingItems.find(item => item?.blockType === 'AiCrudPage')
  const shape = resolveRestorePageShape(node)
  const formAssetId = String(
    options.formAssetId
    || existingCrud?.props?.formAssetId
    || findFormAssetIdInBlocks(existingItems)
    || next.formAssets[0]?.id
    || '',
  ).trim()

  const objectRef = node.objectRef && typeof node.objectRef === 'object'
    ? {
        ...node.objectRef,
        pageMode: shape === 'form' ? 'form' : shape === 'list' ? 'list' : 'crud',
        pageKey: shape === 'form' ? 'form' : 'list',
        valid: node.objectRef.valid !== false,
      }
    : null

  const crudBlock = {
    ...createGridBlock('AiCrudPage', { fields: [] }, { gridX: 0, gridY: 0 }),
    ...(existingCrud?.id ? { id: existingCrud.id } : {}),
    label: node.title || existingCrud?.label || '数据列表',
    props: {
      ...(existingCrud?.props || {}),
      title: node.title || existingCrud?.props?.title || '数据列表',
      formAssetId,
      formAssetFieldsInitialized: existingCrud?.props?.formAssetFieldsInitialized === true,
      ...(objectRef ? { objectRef } : {}),
      ...(shape === 'form'
        ? { formOnly: true, hideToolbar: true, hideBatchDelete: true, showSearch: false }
        : {}),
      ...(shape === 'list-form'
        ? { formOpenMode: 'flat', modalType: 'flat' }
        : {}),
      style: {
        widthMode: 'full',
        heightMode: 'full',
        pageFlowHeight: 640,
        ...(existingCrud?.props?.style || {}),
      },
    },
  }

  next.nodes[index] = {
    ...node,
    pageType: 'object',
    pageTemplate: shape,
    pageShape: shape,
    objectRef,
  }
  next.pages[pageId] = {
    ...page,
    title: page.title || node.title,
    layout: {
      ...(page.layout || {}),
      items: [],
      gridLayout: {
        cols: 12,
        rowHeight: 32,
        gap: 8,
        designWidth: 1366,
        layoutType: shape,
        items: [crudBlock],
      },
      pageTitleComponentInitialized: true,
    },
  }
  return next
}

function resolveRestorePageShape(node = {}) {
  const explicit = String(node.pageShape || node.pageTemplate || '').trim().toLowerCase()
  if (explicit === 'list_form')
    return 'list-form'
  if (['form', 'list', 'list-form'].includes(explicit))
    return explicit
  const mode = String(node.objectRef?.pageMode || '').trim().toLowerCase()
  if (mode === 'form')
    return 'form'
  if (mode === 'list')
    return 'list'
  return 'list-form'
}

function findFormAssetIdInBlocks(blocks = []) {
  for (const block of blocks) {
    const id = String(block?.props?.formAssetId || '').trim()
    if (id)
      return id
    const nested = block?.props?.tabs || block?.props?.items || block?.children
    if (Array.isArray(nested)) {
      const found = findFormAssetIdInBlocks(nested)
      if (found)
        return found
    }
  }
  return ''
}

function resolvePageMode(pageType) {
  if (pageType === 'form')
    return 'form'
  if (pageType === 'list')
    return 'list'
  return 'crud'
}

function buildPageObjectRef(selection = {}, normalized = {}, pageMode = 'crud') {
  return {
    objectId: selection.objectId || null,
    objectCode: normalized.objectCode,
    objectName: normalized.objectName,
    pageKey: pageMode === 'form' ? 'form' : 'list',
    pageMode,
    configKey: selection.configKey || '',
    runtimeDatasourceId: normalized.runtimeDatasourceId,
    createMode: normalized.createMode || PAGE_OBJECT_CREATE_BLANK,
    importDatasourceId: normalized.importDatasourceId,
    importTableName: normalized.importTableName || '',
    valid: true,
  }
}

function normalizeId(value) {
  if (value === null || value === undefined || value === '')
    return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : value
}
