import { createGridBlock } from '@/components/lowcode-builder/page/page-schema'
import { createDefaultFormDesignerSchema } from '@/views/app-center/components/designer/form-first/formDesignerSchema'
import { normalizeObjectCode } from '@/views/app-center/components/designer/form-first/namingUtils'
import { createInAppFormAsset, createNavigationNode } from './in-app-builder-schema'

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
    label: '自定义页面',
    description: '自由搭建内容，不强制绑定数据对象',
  },
])

const PAGE_SHAPE_VALUES = new Set(PAGE_SHAPE_TYPES.map(item => item.value))

export function normalizePageShapeSelection(selection = {}) {
  const pageName = String(selection.pageName || '').trim() || '未命名页面'
  const pageType = PAGE_SHAPE_VALUES.has(selection.pageType) ? selection.pageType : 'form'
  const objectName = String(selection.objectName || '').trim() || pageName
  return {
    pageName,
    pageType,
    objectName,
    objectCode: pageType === 'custom'
      ? ''
      : normalizeObjectCode(selection.objectCode, objectName),
    parentId: selection.parentId || null,
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
    pageTemplate: customPage ? 'blank' : normalized.pageType,
    objectRef: customPage
      ? null
      : {
          objectId: selection.objectId || null,
          objectCode: normalized.objectCode,
          objectName: normalized.objectName,
          pageKey: pageMode === 'form' ? 'form' : 'list',
          pageMode,
          configKey: selection.configKey || '',
          valid: true,
        },
  })
  const pageId = nodeResult.nodes.at(-1)?.id || ''
  if (customPage)
    return { schema: nodeResult, pageId, formAssetId: '', selection: normalized }

  const assetResult = createInAppFormAsset(nodeResult, {
    name: normalized.pageName,
    formKey: `${normalized.objectCode}_form`,
    formDesignerSchema: createDefaultFormDesignerSchema({
      objectCode: normalized.objectCode,
      objectName: normalized.objectName,
      formName: normalized.pageName,
      formOpenMode: normalized.pageType === 'list-form' ? 'flat' : 'modal',
    }),
  })
  const crudBlock = createGridBlock('AiCrudPage', { fields: [] }, { gridX: 0, gridY: 0 })
  const objectRef = {
    objectId: selection.objectId || null,
    objectCode: normalized.objectCode,
    objectName: normalized.objectName,
    pageKey: pageMode === 'form' ? 'form' : 'list',
    pageMode,
    configKey: selection.configKey || '',
    valid: true,
  }
  const block = {
    ...crudBlock,
    label: normalized.pageName,
    props: {
      ...(crudBlock.props || {}),
      title: normalized.pageName,
      formAssetId: assetResult.formAssetId,
      formAssetFieldsInitialized: false,
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

function resolvePageMode(pageType) {
  if (pageType === 'form')
    return 'form'
  if (pageType === 'list')
    return 'list'
  return 'crud'
}
