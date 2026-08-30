const VIEW_MODULES = import.meta.glob('@/views/**/*.vue')
const modulePromiseCache = new Map()

export async function loadFlowBusinessFormFieldCatalog(formUrl) {
  const mod = await loadFlowBusinessFormModule(formUrl)
  if (!mod)
    return []
  const component = mod.default || mod
  const catalog = mod.flowFieldCatalog
    || component?.flowFieldCatalog
    || component?.__vccOpts?.flowFieldCatalog
  return normalizeFlowFieldCatalog(catalog)
}

export function normalizeFlowFieldCatalog(catalog) {
  if (!Array.isArray(catalog))
    return []

  const fields = new Map()
  catalog.forEach((item) => {
    const field = String(item?.field || item?.fieldCode || item?.name || item?.key || '').trim()
    if (!field || fields.has(field))
      return
    fields.set(field, {
      field,
      label: String(item.label || item.title || item.fieldName || field).trim(),
      group: String(item.group || '').trim(),
      componentType: String(item.componentType || item.type || '').trim(),
      dataType: String(item.dataType || 'string').trim(),
      required: item.required === true || item.sourceRequired === true,
      source: item.source || 'external',
    })
  })
  return [...fields.values()]
}

function loadFlowBusinessFormModule(formUrl) {
  const normalizedUrl = normalizeFormUrl(formUrl)
  if (!normalizedUrl)
    return Promise.resolve(null)
  if (modulePromiseCache.has(normalizedUrl))
    return modulePromiseCache.get(normalizedUrl)

  const loader = resolveLoader(normalizedUrl)
  if (!loader)
    return Promise.resolve(null)

  const modulePromise = loader()
    .catch((error) => {
      modulePromiseCache.delete(normalizedUrl)
      throw error
    })
  modulePromiseCache.set(normalizedUrl, modulePromise)
  return modulePromise
}

function resolveLoader(normalizedUrl) {
  const targetPath = `${normalizedUrl}.vue`
  const expectedKey = `/src/views${targetPath}`
  const exactLoader = VIEW_MODULES[expectedKey]
  if (exactLoader)
    return exactLoader

  const lowerTarget = targetPath.toLowerCase()
  return Object.entries(VIEW_MODULES).find(([key]) =>
    key.toLowerCase().endsWith(lowerTarget),
  )?.[1] || null
}

function normalizeFormUrl(formUrl) {
  if (!formUrl)
    return ''
  const cleanUrl = String(formUrl).split('?')[0].trim()
  if (!cleanUrl)
    return ''
  return cleanUrl.startsWith('/') ? cleanUrl : `/${cleanUrl}`
}
