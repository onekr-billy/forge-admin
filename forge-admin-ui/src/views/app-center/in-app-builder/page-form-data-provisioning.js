import { buildBusinessObjectDesignerPayloadFromFormAsset } from './page-form-object-promotion'

const MANAGED_BY_PAGE_FORM = 'PAGE_FORM'

export function collectFormDataProvisionTargets(builder = {}, objects = []) {
  const formAssets = Array.isArray(builder.formAssets) ? builder.formAssets : []
  const singleFormAssetId = formAssets.length === 1 ? formAssets[0].id : ''
  const usageByFormAssetId = new Map()
  const pageNodeById = new Map((Array.isArray(builder.nodes) ? builder.nodes : [])
    .map(node => [String(node?.id || ''), node]))

  Object.entries(builder.pages || {}).forEach(([pageId, page]) => {
    const items = page?.layout?.gridLayout?.items || page?.layout?.items || []
    const pageObjectRef = page?.objectRef || pageNodeById.get(String(pageId))?.objectRef
    visitBlocks(items, (block) => {
      if (block?.blockType !== 'AiCrudPage')
        return
      const formAssetId = String(block.props?.formAssetId || singleFormAssetId || '').trim()
      if (!formAssetId)
        return
      const usage = usageByFormAssetId.get(formAssetId) || { crudCount: 0, unboundCrudCount: 0 }
      usage.crudCount += 1
      if (!hasUsableObjectRef(block, pageObjectRef))
        usage.unboundCrudCount += 1
      usageByFormAssetId.set(formAssetId, usage)
    })
  })

  const managedFormAssetIds = resolveManagedFormAssetIds(objects)
  return formAssets.flatMap((asset) => {
    const usage = usageByFormAssetId.get(asset.id) || { crudCount: 0, unboundCrudCount: 0 }
    const alreadyManaged = managedFormAssetIds.has(String(asset.id))
    if (!alreadyManaged && usage.unboundCrudCount === 0)
      return []
    const designer = buildBusinessObjectDesignerPayloadFromFormAsset(asset)
    if (!designer.fields.length)
      return []
    const objectRef = resolveFormAssetObjectRef(builder, asset.id)
    return [{
      formAssetId: String(asset.id),
      formName: asset.name || designer.formDesignerSchema?.formName || '未命名表单',
      crudCount: usage.crudCount,
      unboundCrudCount: usage.unboundCrudCount,
      alreadyManaged,
      request: {
        formAssetId: String(asset.id),
        formName: asset.name || designer.formDesignerSchema?.formName || '未命名表单',
        fields: designer.fields,
        formDesignerSchema: designer.formDesignerSchema,
        runtimeDatasourceId: objectRef?.runtimeDatasourceId || null,
        createMode: objectRef?.createMode || '',
        importDatasourceId: objectRef?.importDatasourceId || objectRef?.runtimeDatasourceId || null,
        importTableName: objectRef?.importTableName || '',
      },
    }]
  })
}

export function bindProvisionedFormData(builder = {}, formAssetId, provisioned = {}) {
  const next = clone(builder)
  const assets = Array.isArray(next.formAssets) ? next.formAssets : []
  const singleFormAssetId = assets.length === 1 ? assets[0].id : ''
  const pageNodeById = new Map((Array.isArray(next.nodes) ? next.nodes : [])
    .map(node => [String(node?.id || ''), node]))
  let changed = false

  Object.entries(next.pages || {}).forEach(([pageId, page]) => {
    const layout = page?.layout
    if (!layout)
      return
    const pageObjectRef = page?.objectRef || pageNodeById.get(String(pageId))?.objectRef
    if (Array.isArray(layout.gridLayout?.items))
      layout.gridLayout.items = mapBlocks(layout.gridLayout.items, block => bindBlock(block, pageObjectRef))
    else if (Array.isArray(layout.items))
      layout.items = mapBlocks(layout.items, block => bindBlock(block, pageObjectRef))
  })

  return { schema: next, changed }

  function bindBlock(block, pageObjectRef) {
    if (block?.blockType !== 'AiCrudPage')
      return block
    const blockFormAssetId = String(block.props?.formAssetId || singleFormAssetId || '').trim()
    if (blockFormAssetId !== String(formAssetId || '').trim())
      return block
    const currentObjectRef = resolveObjectRef(block, pageObjectRef)
    if (hasUsableObjectRef(block, pageObjectRef)
      && !matchesProvisionedObject(currentObjectRef, provisioned)) {
      return block
    }
    if (block.props?.managedPreviewInitialized === true
      && matchesProvisionedObject(currentObjectRef, provisioned)) {
      return block
    }
    changed = true
    return {
      ...block,
      props: {
        ...(block.props || {}),
        ...buildCrudApiProps(provisioned.configKey),
        previewLiveData: true,
        previewMode: 'realList',
        managedPreviewInitialized: true,
        objectRef: {
          objectId: String(provisioned.objectId ?? provisioned.id ?? ''),
          objectCode: provisioned.objectCode || '',
          objectName: provisioned.objectName || '',
          configKey: provisioned.configKey || '',
        },
      },
    }
  }
}

/**
 * 合并页面表单和异步运行字段目录。
 *
 * 页面表单是编辑期间的稳定基线，运行字段只补充数据库类型、字典和系统字段；
 * 即使运行目录加载期间暂时只有 ID，也不能把表单字段从页面配置中清掉。
 */
export function mergePageFieldCatalogs(formFields = [], runtimeFields = []) {
  const runtimeByField = new Map(normalizeFieldCatalog(runtimeFields)
    .map(field => [field.field, field]))
  const merged = []
  const used = new Set()

  normalizeFieldCatalog(formFields).forEach((field) => {
    const runtimeField = runtimeByField.get(field.field)
    merged.push(normalizeField({ ...field, ...(runtimeField || {}) }))
    used.add(field.field)
  })
  runtimeByField.forEach((field, fieldCode) => {
    if (!used.has(fieldCode))
      merged.push(field)
  })
  return merged
}

export function resolveManagedFormAssetIds(objects = []) {
  return new Set((Array.isArray(objects) ? objects : []).flatMap((object) => {
    const options = parseObject(object?.options)
    if (options.managedBy !== MANAGED_BY_PAGE_FORM || !options.sourceFormAssetId)
      return []
    return [String(options.sourceFormAssetId)]
  }))
}

function visitBlocks(blocks, visitor) {
  ;(Array.isArray(blocks) ? blocks : []).forEach((block) => {
    visitor(block)
    visitBlocks(resolveNestedBlocks(block), visitor)
  })
}

function mapBlocks(blocks, mapper) {
  return (Array.isArray(blocks) ? blocks : []).map((block) => {
    let next = mapper(block)
    const children = Array.isArray(next.children) ? mapBlocks(next.children, mapper) : next.children
    const tabs = Array.isArray(next.props?.tabs)
      ? next.props.tabs.map(tab => ({ ...tab, children: mapBlocks(tab.children, mapper) }))
      : next.props?.tabs
    const cells = Array.isArray(next.props?.cells)
      ? next.props.cells.map(cell => ({ ...cell, children: mapBlocks(cell.children, mapper) }))
      : next.props?.cells
    if (children !== next.children || tabs !== next.props?.tabs || cells !== next.props?.cells) {
      next = {
        ...next,
        ...(children !== next.children ? { children } : {}),
        props: {
          ...(next.props || {}),
          ...(tabs !== next.props?.tabs ? { tabs } : {}),
          ...(cells !== next.props?.cells ? { cells } : {}),
        },
      }
    }
    return next
  })
}

function resolveNestedBlocks(block = {}) {
  return [
    ...(Array.isArray(block.children) ? block.children : []),
    ...(Array.isArray(block.props?.tabs)
      ? block.props.tabs.flatMap(tab => Array.isArray(tab.children) ? tab.children : [])
      : []),
    ...(Array.isArray(block.props?.cells)
      ? block.props.cells.flatMap(cell => Array.isArray(cell.children) ? cell.children : [])
      : []),
  ]
}

function hasUsableObjectRef(block = {}, pageObjectRef) {
  const objectRef = resolveObjectRef(block, pageObjectRef)
  return objectRef?.valid !== false && Boolean(objectRef?.objectId ?? objectRef?.id ?? objectRef?.objectCode)
}

function resolveObjectRef(block = {}, pageObjectRef) {
  return block.props?.objectRef ?? block.props?.businessObjectRef ?? pageObjectRef
}

function resolveFormAssetObjectRef(builder = {}, formAssetId) {
  const pageNodeById = new Map((Array.isArray(builder.nodes) ? builder.nodes : [])
    .map(node => [String(node?.id || ''), node]))
  let matched = null
  Object.entries(builder.pages || {}).forEach(([pageId, page]) => {
    if (matched)
      return
    const pageObjectRef = page?.objectRef || pageNodeById.get(String(pageId))?.objectRef
    const items = page?.layout?.gridLayout?.items || page?.layout?.items || []
    visitBlocks(items, (block) => {
      if (matched || block?.blockType !== 'AiCrudPage')
        return
      const blockFormAssetId = String(block.props?.formAssetId || '').trim()
      if (blockFormAssetId && blockFormAssetId === String(formAssetId || '').trim())
        matched = resolveObjectRef(block, pageObjectRef)
    })
  })
  return matched
}

function matchesProvisionedObject(objectRef = {}, provisioned = {}) {
  if (!objectRef || objectRef.valid === false)
    return false
  const currentId = String(objectRef.objectId ?? objectRef.id ?? '').trim()
  const provisionedId = String(provisioned.objectId ?? provisioned.id ?? '').trim()
  if (currentId && provisionedId)
    return currentId === provisionedId
  const currentCode = String(objectRef.objectCode || '').trim()
  const provisionedCode = String(provisioned.objectCode || '').trim()
  return Boolean(currentCode && provisionedCode && currentCode === provisionedCode)
}

function normalizeFieldCatalog(fields = []) {
  return (Array.isArray(fields) ? fields : [])
    .map(normalizeField)
    .filter(field => field.field)
}

function normalizeField(field = {}) {
  const fieldCode = String(field.field || field.fieldCode || field.prop || field.key || '').trim()
  return {
    ...field,
    field: fieldCode,
    fieldCode,
    sourceField: field.sourceField || fieldCode,
  }
}

function buildCrudApiProps(configKey) {
  const normalizedConfigKey = String(configKey || '').trim()
  if (!normalizedConfigKey)
    return {}
  const apiPrefix = `/ai/crud/${normalizedConfigKey}`
  return {
    api: apiPrefix,
    listApi: `get@${apiPrefix}/page`,
    detailApi: `get@${apiPrefix}/:id`,
    createApi: `post@${apiPrefix}`,
    updateApi: `put@${apiPrefix}`,
    deleteApi: `delete@${apiPrefix}/:id`,
    importApi: `post@${apiPrefix}/import`,
    exportApi: `post@${apiPrefix}/export`,
  }
}

function parseObject(value) {
  if (!value)
    return {}
  if (typeof value === 'object')
    return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  }
  catch {
    return {}
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value || {}))
}
