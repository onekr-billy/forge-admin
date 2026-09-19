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
    label: '自定义页面',
    description: '自由搭建内容，不强制绑定数据对象',
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
    pageTemplate: customPage ? 'blank' : normalized.pageType,
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
