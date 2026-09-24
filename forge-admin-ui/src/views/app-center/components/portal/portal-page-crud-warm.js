import { crudConfigRender } from '@/api/ai'
import { buildRuntimeCrudProps } from '@/components/lowcode-builder/shared/runtime-crud-props'
import { isDataFieldBlockType } from '@/components/lowcode-builder/page/page-schema'
import { resolvePortalPageBlocks } from './portal-page-runtime-layout'

/**
 * 收集门户页需要预热的业务对象 CRUD 目标（按对象去重）。
 * 用于首屏骨架阶段并行拉取 render，避免壳出来后内容区再白几秒。
 */
export function collectPortalPageCrudTargets({
  page,
  node,
  objects = [],
  entries = [],
} = {}) {
  const objectList = Array.isArray(objects) ? objects : []
  const entryList = Array.isArray(entries) ? entries : []
  const targets = new Map()

  function enrichObjectRef(objectRef) {
    const targetId = String(objectRef?.objectId ?? objectRef?.id ?? '')
    const targetCode = String(objectRef?.objectCode || '')
    const object = objectList.find(item => (
      (targetId && String(item.objectId ?? item.id ?? '') === targetId)
      || (targetCode && String(item.objectCode || '') === targetCode)
    ))
    return {
      ...(object || {}),
      ...(objectRef || {}),
      objectId: objectRef?.objectId ?? objectRef?.id ?? object?.objectId ?? object?.id,
      objectCode: objectRef?.objectCode || object?.objectCode || '',
      configKey: objectRef?.configKey || object?.configKey || '',
    }
  }

  function resolveObjectRef(source = {}) {
    const raw = source.objectRef || source.props?.objectRef || source.props?.runtimeObjectRef
    if (raw && typeof raw === 'object')
      return enrichObjectRef(raw)
    if (source === node && node?.objectRef)
      return enrichObjectRef(node.objectRef)
    return null
  }

  function resolveObjectKey(objectRef) {
    return String(objectRef?.objectId ?? objectRef?.id ?? objectRef?.objectCode ?? objectRef?.configKey ?? '')
  }

  function resolveRuntimeEntryId(configKey) {
    const normalized = String(configKey || '').trim()
    if (!normalized)
      return null
    const entry = entryList.find(item => String(item?.configKey || '').trim() === normalized)
    return entry?.id ?? entry?.entryId ?? null
  }

  function visit(blocks = []) {
    ;(blocks || []).forEach((block) => {
      if (isDataFieldBlockType(block?.blockType)) {
        const objectRef = resolveObjectRef(block) || resolveObjectRef(node || {})
        const key = resolveObjectKey(objectRef)
        const configKey = String(objectRef?.configKey || '').trim()
        if (key && configKey && !targets.has(key)) {
          targets.set(key, {
            key,
            configKey,
            objectRef,
            entryId: resolveRuntimeEntryId(configKey),
          })
        }
      }
      ;[block?.children, block?.props?.children, block?.props?.items]
        .filter(Array.isArray)
        .forEach(visit)
    })
  }

  const blocks = resolvePortalPageBlocks({
    page,
    node,
    resolveObjectRef,
  })
  visit(blocks)
  return [...targets.values()]
}

async function fetchOneCrudConfig(target, {
  designPreview = false,
  applicationId = '',
  pageId = '',
} = {}) {
  const renderOptions = {
    needTip: false,
    appId: target.entryId,
    applicationId,
    pageId: String(pageId || '').trim(),
  }
  let preview = Boolean(designPreview)
  let config = null
  try {
    config = (await crudConfigRender(target.configKey, preview, renderOptions)).data
  }
  catch (error) {
    if (!preview)
      throw error
    preview = false
    config = (await crudConfigRender(target.configKey, false, renderOptions)).data
  }
  if (!config || typeof config !== 'object')
    throw new Error('业务对象运行配置为空')
  return {
    key: target.key,
    props: {
      ...buildRuntimeCrudProps(config, { designPreview: preview }),
      title: config.title || target.objectRef?.objectName || '',
    },
  }
}

/**
 * 骨架阶段预热首屏 CRUD render。超时不阻塞出壳，已完成的结果仍作为 seed。
 */
export async function warmPortalPageCrudProps(targets = [], options = {}) {
  const list = Array.isArray(targets) ? targets.filter(item => item?.configKey && item?.key) : []
  if (!list.length)
    return {}

  const timeoutMs = Number(options.timeoutMs)
  const waitMs = Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 6000
  const result = {}

  const work = Promise.all(list.map(async (target) => {
    try {
      const item = await fetchOneCrudConfig(target, options)
      result[item.key] = item.props
    }
    catch (error) {
      console.warn('[application-portal] 首屏预热 CRUD 失败', target.configKey, error?.message || error)
    }
  }))

  await Promise.race([
    work,
    new Promise(resolve => setTimeout(resolve, waitMs)),
  ])
  return result
}
