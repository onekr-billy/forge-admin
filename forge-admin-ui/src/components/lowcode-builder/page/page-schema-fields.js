import { toCanvasComponentCatalog as _toCanvasComponentCatalog } from '@/components/lowcode-builder/designer-core'
import { createPageWidgetDefaultProps, createWidgetDataBinding, pageWidgetCatalog } from '@/components/lowcode-builder/shared/page-widget-schema'
import { clonePlain, isNumericId, safeKey } from './page-schema-utils.js'
import {
  createDefaultListGridLayout,
  syncGridLayoutWithModel,
  bootstrapGridLayoutFromZones,
  applyGridLayoutToZones,
  normalizeZoneCanvas,
  resolveFieldRefsFromCanvas,
  resolveFieldRefsFromFormCreateRules,
} from './page-schema-layout.js'

export const pageZoneCatalog = [
  {
    zoneKey: 'search',
    componentKey: 'search-form',
    title: '查询页',
    desc: '查询集、重置、自定义筛选条件',
  },
  {
    zoneKey: 'table',
    componentKey: 'data-table',
    title: '列表页',
    desc: '列表列、导入导出、批量操作',
  },
  {
    zoneKey: 'edit',
    componentKey: 'edit-form',
    title: '表单与详情页',
    desc: '新增、编辑、详情展示共用字段',
  },
  {
    zoneKey: 'detail',
    componentKey: 'detail-panel',
    title: '详情页兼容区',
    desc: '历史协议保留，新配置使用表单与详情页',
  },
]

export const canvasComponentCatalog = [
  ..._toCanvasComponentCatalog(),
  // 页面设计器专用字段组件（zone 画布上的字段渲染器，非注册表组件）
  { group: 'field', componentKey: 'field-input', title: '单行输入', desc: '文本字段', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-textarea', title: '多行文本', desc: '长文本字段', zones: ['edit', 'detail'], defaultWidth: 580, defaultHeight: 98 },
  { group: 'field', componentKey: 'field-number', title: '数字输入', desc: '整数、小数、金额', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-select', title: '下拉选择', desc: '系统字典或枚举', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-dict-select', title: '字典选择器', desc: '系统字典组件', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-tree-select', title: '树形选择', desc: '组织、分类、区域等树形字段', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-org-tree-select', title: '组织树选择', desc: '当前系统组织树', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-user-select', title: '用户选择', desc: '当前系统用户列表', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-region-tree-select', title: '区划树选择', desc: '行政区划树组件', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-cascader', title: '级联选择', desc: '多级分类或行政区划', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-date', title: '日期', desc: '日期选择', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-datetime', title: '日期时间', desc: '日期时间选择', zones: ['search', 'edit', 'detail'], defaultWidth: 280, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-switch', title: '开关', desc: '启用、禁用类字段', zones: ['edit', 'detail'], defaultWidth: 220, defaultHeight: 64 },
  { group: 'field', componentKey: 'field-upload', title: '文件上传', desc: '附件上传', zones: ['edit', 'detail'], defaultWidth: 300, defaultHeight: 72 },
  { group: 'field', componentKey: 'field-image-upload', title: '图片上传', desc: '图片上传', zones: ['edit', 'detail'], defaultWidth: 300, defaultHeight: 88 },
]

const hiddenPageFieldNames = new Set(['tenantId', 'delFlag'])
const hiddenPageColumnNames = new Set(['tenant_id', 'del_flag'])
const readonlySystemFieldNames = new Set([
  'id',
  'tenantId',
  'createBy',
  'createTime',
  'createDept',
  'updateBy',
  'updateTime',
  'delFlag',
])
const readonlySystemColumnNames = new Set([
  'id',
  'tenant_id',
  'create_by',
  'create_time',
  'create_dept',
  'update_by',
  'update_time',
  'del_flag',
])

export function isHiddenPageField(field = {}) {
  const fieldName = field.sourceField || field.field
  return hiddenPageFieldNames.has(fieldName)
    || hiddenPageFieldNames.has(field.field)
    || hiddenPageColumnNames.has(field.columnName)
}

export function isInactivePageField(field = {}) {
  const status = String(field.fieldStatus || '').toUpperCase()
  return status === 'DISABLED' || status === 'HIDDEN'
}

export function isReadonlySystemField(field = {}) {
  const fieldName = field.sourceField || field.field
  return Boolean(field.systemField)
    || Boolean(field.readonly)
    || readonlySystemFieldNames.has(fieldName)
    || readonlySystemFieldNames.has(field.field)
    || readonlySystemColumnNames.has(field.columnName)
}

/** 查询区默认排除的审计字段（不含 id，允许按主键筛选） */
const auditSystemFieldNames = new Set([
  'tenantId',
  'createBy',
  'createTime',
  'createDept',
  'updateBy',
  'updateTime',
  'delFlag',
])
const auditSystemColumnNames = new Set([
  'tenant_id',
  'create_by',
  'create_time',
  'create_dept',
  'update_by',
  'update_time',
  'del_flag',
])

export function isAuditSystemField(field = {}) {
  if (!field || isHiddenPageField(field) || isInactivePageField(field))
    return true
  const fieldName = field.sourceField || field.field
  return auditSystemFieldNames.has(fieldName)
    || auditSystemFieldNames.has(field.field)
    || auditSystemColumnNames.has(field.columnName)
}

export function isChildListField(field = {}) {
  if (field?.fieldScope === 'child' || field?.scope === 'child')
    return true
  const key = String(field?.field || field?.fieldCode || '')
  const source = String(field?.sourceField || '')
  return Boolean(source) && key.includes('__') && key !== source
}

/**
 * 列表列标题只表达字段本身。子表归属在字段选择面板中分组展示，
 * 不重复写入正式表头；同时兼容已保存过“子表名 · 字段”的旧标题。
 */
export function resolveListFieldTitle(field = {}, setting = {}, fieldCode = '') {
  const label = String(
    setting?.title
    || setting?.label
    || field.rawLabel
    || field.label
    || field.fieldName
    || fieldCode
    || field.field
    || '',
  ).trim()
  if (!label || !isChildListField(field))
    return label
  const modelName = String(field.modelName || field.sourceLabel || '').trim()
  if (!modelName)
    return label
  const prefixes = [
    `${modelName} · `,
    `${modelName}·`,
    `${modelName}.`,
    `${modelName}。`,
    `${modelName}:`,
    `${modelName}：`,
  ]
  const prefix = prefixes.find(item => label.startsWith(item))
  return prefix ? label.slice(prefix.length).trim() : label
}

export function resolveChildListDisplayMode(value) {
  return value === 'expand' ? 'expand' : 'aggregate'
}

export function resolveChildListDisplayHint(value) {
  if (resolveChildListDisplayMode(value) === 'expand') {
    return '每个子表行单独占一行，主表字段会重复出现。分页按展开后的行数计算，同一条主记录可能被拆到前后两页。编辑和删除仍按整条主表记录处理，勾选同一条主记录拆出来的多行只会操作一次。字典、人员这类字段按单个值显示。'
  }
  return '一条主表记录只占一行。同一条主记录下的多个子表值用「、」拼在同一个单元格里。分页按主表记录数计算，适合看单据列表。拼在一起后，字典标签不能再逐个上色。'
}

export function isPageFieldVisible(field = {}, zoneKey = 'table') {
  if (!field || isHiddenPageField(field) || isInactivePageField(field))
    return false
  if (zoneKey === 'edit')
    return !isReadonlySystemField(field) && field.formVisible !== false
  if (zoneKey === 'detail')
    return field.formVisible !== false
  if (zoneKey === 'search')
    // 查询条件只排除隐藏/停用与内置审计字段；业务字段的 form「只读」不应阻断勾选为查询条件
    return !isAuditSystemField(field)
  if (zoneKey === 'table')
    return field.listVisible !== false
  return true
}

/** 列表可选字段。子表列即使没勾默认列表显示，也要能被选中并保留。 */
export function isListFieldSelectable(field = {}, zoneKey = 'table') {
  if (!field || isHiddenPageField(field) || isInactivePageField(field))
    return false
  if (zoneKey === 'table' && isChildListField(field))
    return true
  return isPageFieldVisible(field, zoneKey)
}

export function filterPageFields(fields = [], zoneKey = 'table') {
  return (fields || []).filter(field => isPageFieldVisible(field, zoneKey))
}

// 列表默认列只保留业务字段，审计类系统字段（创建人/时间、更新人/时间、创建部门）
// 默认不进列表，避免一进来就有 20+ 列把操作列挤到看不见的位置。用户仍可在
// 列表设计器里手动添加这些字段——它们没有从 listVisible 白名单里移除。
const defaultListHiddenFieldNames = new Set([
  'createBy',
  'createTime',
  'createDept',
  'updateBy',
  'updateTime',
])
const defaultListHiddenColumnNames = new Set([
  'create_by',
  'create_time',
  'create_dept',
  'update_by',
  'update_time',
])

export function isDefaultListColumnField(field = {}) {
  if (!isPageFieldVisible(field, 'table'))
    return false
  const fieldName = field.sourceField || field.field
  return !defaultListHiddenFieldNames.has(fieldName)
    && !defaultListHiddenFieldNames.has(field.field)
    && !defaultListHiddenColumnNames.has(field.columnName)
}

export function filterDefaultListFields(fields = []) {
  return (fields || []).filter(isDefaultListColumnField)
}

export function isChildPageModelField(field = {}) {
  const sourceField = field.sourceField || field.field
  return Boolean(field.modelCode) && field.field !== sourceField
}

export function mergePageFieldRefs(...groups) {
  return Array.from(new Set(groups.flat().filter(Boolean)))
}

export function createDefaultPageSchema(modelSchema) {
  const fields = modelSchema?.fields || []
  const isTree = modelSchema?.appType === 'TREE'
  const isMasterDetail = modelSchema?.appType === 'MASTER_DETAIL'
  const layoutType = isTree ? 'tree-crud' : isMasterDetail ? 'master-detail-crud' : 'simple-crud'
  const treeConfig = isTree ? resolveDefaultTreeConfig(modelSchema) : null
  const schema = {
    layoutType,
    listLayoutMode: 'grid',
    listGridLayout: createDefaultListGridLayout(modelSchema, { layoutType }),
    zones: [
      {
        zoneKey: 'search',
        componentKey: 'search-form',
        enabled: true,
        fieldRefs: filterPageFields(fields, 'search').map(field => field.field),
        props: {},
      },
      {
        zoneKey: 'table',
        componentKey: 'data-table',
        enabled: true,
        fieldRefs: filterDefaultListFields(fields).map(field => field.field),
        props: {
          showImport: true,
          showExport: true,
          hideBatchDelete: false,
          enableCustomQuery: true,
          customActions: [],
          defaultSortField: 'id',
          defaultSortOrder: 'desc',
          ...(isTree
            ? {
                treeConfig,
              }
            : {}),
        },
      },
      {
        zoneKey: 'edit',
        componentKey: 'edit-form',
        enabled: true,
        fieldRefs: filterPageFields(fields, 'edit').map(field => field.field),
        props: {
          editGridCols: 1,
        },
      },
      {
        zoneKey: 'detail',
        componentKey: 'detail-panel',
        enabled: false,
        fieldRefs: filterPageFields(fields, 'detail').map(field => field.field),
        props: {},
      },
    ],
  }
  return syncPageSchemaWithModel(schema, modelSchema)
}

export function syncPageSchemaWithModel(pageSchema, modelSchema) {
  const current = pageSchema || createDefaultPageSchema(modelSchema)
  const fields = modelSchema?.fields || []
  // layoutType 以页面为准：嵌入式树表也会标 appType=TREE，不能因此改成左树右表。
  const layoutType = current.layoutType
    || (modelSchema?.appType === 'MASTER_DETAIL' ? 'master-detail-crud' : 'simple-crud')
  const zones = (current.zones || []).map((zone) => {
    const zoneFields = filterPageFields(fields, zone.zoneKey)
    const zoneFieldSet = new Set(zoneFields.map(field => field.field))
    const childEditRefs = zone.zoneKey === 'edit' && layoutType === 'master-detail-crud'
      ? zoneFields.filter(field => isChildPageModelField(field)).map(field => field.field)
      : []
    const childEditSet = new Set(childEditRefs)
    const props = zone.props || {}
    const normalizedZone = {
      ...zone,
      fieldRefs: (zone.fieldRefs || []).filter(field => zoneFieldSet.has(field)),
      props,
    }
    const canvas = normalizeZoneCanvas(normalizedZone, modelSchema)
    const canvasRefs = resolveFieldRefsFromCanvas(canvas).filter(field => zoneFieldSet.has(field))
    const formCreateRefs = normalizedZone.zoneKey === 'edit' && !canvasRefs.length
      ? resolveFieldRefsFromFormCreateRules(props.formCreateRule, zoneFieldSet)
      : []
    const explicitRefs = (normalizedZone.fieldRefs || []).filter(field => zoneFieldSet.has(field))
    const explicitChildRefs = explicitRefs.filter(ref => childEditSet.has(ref))
    const customChildSelection = normalizedZone.zoneKey === 'edit'
      && layoutType === 'master-detail-crud'
      && (String(props.relationFieldSelectionMode || '').toUpperCase() === 'CUSTOM'
        || props.relationFieldSelectionTouched === true)
    const mergedChildRefs = customChildSelection
      ? explicitChildRefs
      : explicitChildRefs.length ? explicitChildRefs : childEditRefs
    const preferCanvasRefs = ['edit', 'detail'].includes(normalizedZone.zoneKey) && canvasRefs.length
    const fieldRefs = preferCanvasRefs
      ? mergePageFieldRefs(canvasRefs, mergedChildRefs)
      : formCreateRefs.length
        ? mergePageFieldRefs(formCreateRefs, mergedChildRefs)
        : explicitRefs.length
          ? mergePageFieldRefs(explicitRefs, mergedChildRefs)
          : mergePageFieldRefs(canvasRefs, mergedChildRefs)
    return {
      ...normalizedZone,
      fieldRefs,
      props: {
        ...normalizedZone.props,
        canvas,
      },
    }
  })

  for (const catalog of pageZoneCatalog) {
    if (!zones.some(zone => zone.zoneKey === catalog.zoneKey)) {
      const zone = {
        zoneKey: catalog.zoneKey,
        componentKey: catalog.componentKey,
        enabled: catalog.zoneKey !== 'detail',
        fieldRefs: [],
        props: {},
      }
      const canvas = normalizeZoneCanvas(zone, modelSchema)
      const zoneFields = filterPageFields(fields, zone.zoneKey)
      const zoneFieldSet = new Set(zoneFields.map(field => field.field))
      const childEditRefs = zone.zoneKey === 'edit' && layoutType === 'master-detail-crud'
        ? zoneFields.filter(field => isChildPageModelField(field)).map(field => field.field)
        : []
      const formCreateRefs = zone.zoneKey === 'edit'
        ? resolveFieldRefsFromFormCreateRules(zone.props?.formCreateRule, zoneFieldSet)
        : []
      zones.push({
        ...zone,
        fieldRefs: formCreateRefs.length
          ? mergePageFieldRefs(formCreateRefs, childEditRefs)
          : resolveFieldRefsFromCanvas(canvas).filter(field => zoneFieldSet.has(field)),
        props: {
          canvas,
        },
      })
    }
  }

  const listLayoutMode = current.listLayoutMode || 'grid'
  const listPageGridLayout = Array.isArray(current.pages)
    ? current.pages.find(page => page?.pageKey === 'list')?.gridLayout
    : null
  let listGridLayout = listPageGridLayout || current.listGridLayout
  if (listLayoutMode === 'grid') {
    if (!listGridLayout) {
      listGridLayout = bootstrapGridLayoutFromZones(zones, modelSchema, { layoutType })
    }
    listGridLayout = syncGridLayoutWithModel(listGridLayout, modelSchema, { layoutType })
  }
  const finalZones = listLayoutMode === 'grid' && listGridLayout
    ? applyGridLayoutToZones(zones, listGridLayout, modelSchema)
    : zones

  return {
    ...current,
    layoutType,
    listLayoutMode,
    listGridLayout,
    zones: finalZones,
  }
}

export function createPageModelRef(model = {}, options = {}) {
  const schema = model.modelSchema || model
  const modelCode = model.modelCode || schema?.object?.code || options.modelCode || ''
  const modelName = model.modelName || schema?.object?.name || schema?.businessName || modelCode
  const primary = Boolean(options.primary)

  return {
    modelId: isNumericId(model.id) ? Number(model.id) : null,
    modelCode,
    modelName,
    tableName: schema?.tableName || model.tableName || '',
    relations: Array.isArray(schema?.relations) ? clonePlain(schema.relations) : [],
    primary,
    fields: (schema?.fields || [])
      .filter(field => !isHiddenPageField(field) && !isInactivePageField(field))
      .map(field => ({
        ...field,
        sourceField: field.field,
        fieldRef: resolveModelFieldRef(modelCode, field.field, primary),
        modelCode,
        modelName,
      })),
  }
}

export function buildPageDesignModelSchema(modelSchema, modelRefs = []) {
  const refs = Array.isArray(modelRefs) && modelRefs.length
    ? modelRefs
    : [createPageModelRef({ modelSchema }, { primary: true })]
  const fields = refs.flatMap((modelRef) => {
    const modelCode = modelRef.modelCode || ''
    const modelName = modelRef.modelName || modelCode || '数据模型'
    return (modelRef.fields || []).filter(field => !isHiddenPageField(field) && !isInactivePageField(field)).map((field) => {
      const sourceField = field.sourceField || field.field
      const fieldRef = field.fieldRef || resolveModelFieldRef(modelCode, sourceField, modelRef.primary)
      return {
        ...field,
        field: fieldRef,
        sourceField,
        rawLabel: field.rawLabel || field.label || sourceField,
        modelId: modelRef.modelId || null,
        modelCode,
        modelName,
        fieldScope: modelRef.primary ? 'main' : 'child',
        label: field.label || sourceField,
        sourceLabel: modelRef.primary ? '主表' : (modelName || '子表'),
      }
    })
  })

  return {
    ...(modelSchema || {}),
    fields,
    pageModelRefs: refs,
  }
}

export function resolveModelFieldRef(modelCode, fieldName, primary = false) {
  if (primary)
    return fieldName || ''
  return `${safeKey(modelCode || 'model')}__${fieldName || 'field'}`
}

