export function buildWorkspaceModules(tree, dataScopeModules = []) {
  const modules = []
  const assignedIds = new Set()
  const dataScopeCodes = dataScopeModules.map(module => module.moduleCode).filter(Boolean)

  function ensureModule(rawModule) {
    const key = rawModule?.key || 'module:default'
    let module = modules.find(item => item.key === key)
    if (!module) {
      module = {
        key,
        name: rawModule?.name || '业务模块',
        path: rawModule?.path || '',
        parentKey: rawModule?.parentKey || '',
        resourceId: rawModule?.resourceId ?? null,
        pages: [],
      }
      modules.push(module)
    }
    return module
  }

  function walk(nodes, breadcrumbs = [], currentModule = null) {
    for (const node of nodes || []) {
      const type = Number(node.resourceType)
      const nextBreadcrumbs = [1, 2].includes(type)
        ? [...breadcrumbs, node.resourceName].filter(Boolean)
        : breadcrumbs
      let nextModule = currentModule

      if (type === 1) {
        nextModule = ensureModule({
          key: `module:${node.id}`,
          name: node.resourceName || '未命名目录',
          path: breadcrumbs.join(' / '),
          parentKey: currentModule?.key || '',
          resourceId: node.id,
        })
      }

      if (type === 2) {
        const ownedActions = collectOwnedActions(node)
        const childMenus = (node.children || []).some(child => Number(child.resourceType) === 2)
        if (ownedActions.length > 0 || !childMenus) {
          const items = groupPermissionItems(ownedActions, node.resourceName)
          const accessItem = {
            key: `access:${node.id}`,
            label: '页面入口',
            resourceIds: [node.id],
            permission: String(node.perms || '').trim(),
          }
          const actionResourceIds = uniqueIds(items.flatMap(item => item.resourceIds))
          const resourceIds = uniqueIds([...accessItem.resourceIds, ...actionResourceIds])
          resourceIds.forEach(id => assignedIds.add(String(id)))
          const module = ensureModule(nextModule || {
            key: `module:single:${node.id}`,
            name: node.resourceName || '业务模块',
            path: breadcrumbs.join(' / '),
            parentKey: '',
            resourceId: null,
          })
          const moduleCode = resolveDataScopeModuleCode([node, ...ownedActions], dataScopeCodes)
          module.pages.push({
            key: `page:${node.id}`,
            name: node.resourceName || '未命名页面',
            path: nextBreadcrumbs.slice(0, -1).join(' / '),
            accessItem,
            actionItems: items,
            resourceIds,
            moduleCode,
            dataScopeModule: moduleCode ? dataScopeModules.find(item => item.moduleCode === moduleCode) || null : null,
            navigationHidden: Number(node.visible) === 0 || Number(node.menuStatus) === 0,
          })
        }
      }

      const structuralChildren = (node.children || []).filter(child => [1, 2].includes(Number(child.resourceType)))
      walk(structuralChildren, nextBreadcrumbs, nextModule)
    }
  }

  walk(tree)

  const unassignedActions = collectAllActions(tree)
    .filter(node => !assignedIds.has(String(node.id)))
  if (unassignedActions.length > 0) {
    const items = groupPermissionItems(unassignedActions, '')
    const moduleCode = resolveDataScopeModuleCode(unassignedActions, dataScopeCodes)
    ensureModule({
      key: 'module:other',
      name: '其他业务权限',
      path: '',
      parentKey: '',
      resourceId: null,
    }).pages.push({
      key: 'page:other',
      name: '未归类权限',
      path: '',
      accessItem: null,
      actionItems: items,
      resourceIds: uniqueIds(items.flatMap(item => item.resourceIds)),
      moduleCode,
      dataScopeModule: moduleCode ? dataScopeModules.find(item => item.moduleCode === moduleCode) || null : null,
      navigationHidden: false,
    })
  }

  return modules
}

export function normalizePermissionModules(modules, dataScopeModules = []) {
  const dataScopeModuleMap = new Map(dataScopeModules.map(module => [module.moduleCode, module]))
  return (modules || []).map((module, moduleIndex) => ({
    ...module,
    key: module.key || `module:prepared:${moduleIndex}`,
    name: module.name || '业务模块',
    path: module.path || '',
    parentKey: module.parentKey || '',
    pages: (module.pages || []).map((page, pageIndex) => {
      const actionItems = (page.actionItems || []).map((item, itemIndex) => ({
        ...item,
        key: item.key || `permission:${moduleIndex}:${pageIndex}:${itemIndex}`,
        label: item.label || '使用',
        resourceIds: uniqueIds(item.resourceIds || []),
        permissions: uniqueIds(item.permissions || []),
        sources: Array.isArray(item.sources) ? item.sources : [],
        disabled: Boolean(item.disabled),
      }))
      const accessItem = page.accessItem
        ? {
            ...page.accessItem,
            resourceIds: uniqueIds(page.accessItem.resourceIds || []),
          }
        : null
      const moduleCode = page.moduleCode || page.dataScopeModule?.moduleCode || ''
      const showDataScopePanel = page.showDataScopePanel === undefined
        ? true
        : Boolean(page.showDataScopePanel)
      const showFunctionPanel = page.showFunctionPanel === undefined
        ? true
        : Boolean(page.showFunctionPanel)

      return {
        ...page,
        key: page.key || `page:prepared:${moduleIndex}:${pageIndex}`,
        name: page.name || '未命名页面',
        path: page.path || '',
        accessItem,
        actionItems,
        moduleCode,
        dataScopeModule: moduleCode ? dataScopeModuleMap.get(moduleCode) || null : null,
        resourceIds: uniqueIds([
          ...(accessItem?.resourceIds || []),
          ...actionItems.flatMap(item => item.resourceIds),
        ]),
        navigationHidden: Boolean(page.navigationHidden),
        showDataScopePanel,
        showFunctionPanel,
        hasDetails: showDataScopePanel || showFunctionPanel,
      }
    }),
  }))
}

export function buildPermissionNavigationTree(modules) {
  const moduleMap = new Map((modules || []).map(module => [module.key, module]))
  const nodeMap = new Map()
  const roots = []

  for (const module of modules || []) {
    const node = {
      key: `directory:${module.key}`,
      label: module.name,
      nodeType: 'directory',
      moduleKey: module.key,
      path: module.path || '',
      resourceIds: uniqueIds(module.pages.flatMap(page => page.resourceIds)),
      children: [],
    }
    nodeMap.set(module.key, node)
  }

  for (const module of modules || []) {
    const node = nodeMap.get(module.key)
    const parent = module.parentKey ? nodeMap.get(module.parentKey) : null
    if (parent)
      parent.children.push(node)
    else
      roots.push(node)

    for (const page of module.pages || []) {
      node.children.push({
        key: `page-navigation:${page.key}`,
        label: page.name,
        nodeType: 'page',
        moduleKey: module.key,
        pageKey: page.key,
        path: page.path || module.path || '',
        resourceIds: uniqueIds(page.resourceIds),
        navigationHidden: Boolean(page.navigationHidden),
      })
    }
  }

  function collectNodeResourceIds(node) {
    node.resourceIds = uniqueIds([
      ...(node.resourceIds || []),
      ...(node.children || []).flatMap(child => collectNodeResourceIds(child)),
    ])
    return node.resourceIds
  }

  roots.forEach(collectNodeResourceIds)
  return roots
    .filter(node => moduleMap.has(node.moduleKey))
    .map((node) => {
      const module = moduleMap.get(node.moduleKey)
      const isStandalonePage = module?.key.startsWith('module:single:')
        && !module.resourceId
        && node.children.length === 1
        && node.children[0].nodeType === 'page'
      return isStandalonePage ? node.children[0] : node
    })
}

export function filterPermissionNavigationTree(tree, keyword) {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  if (!normalizedKeyword)
    return tree || []

  return (tree || []).flatMap((node) => {
    const children = filterPermissionNavigationTree(node.children, normalizedKeyword)
    const matched = [node.label, node.path].some(value => String(value || '').toLowerCase().includes(normalizedKeyword))
    if (!matched && children.length === 0)
      return []
    return [{ ...node, children: matched ? node.children : children }]
  })
}

export function findPermissionNavigationNode(tree, key) {
  for (const node of tree || []) {
    if (String(node.key) === String(key))
      return node
    const match = findPermissionNavigationNode(node.children, key)
    if (match)
      return match
  }
  return null
}

export function findPermissionNavigationMatch(tree, keyword) {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  if (!normalizedKeyword)
    return null
  for (const node of tree || []) {
    const matched = [node.label, node.path].some(value => String(value || '').toLowerCase().includes(normalizedKeyword))
    if (matched)
      return node
    const childMatch = findPermissionNavigationMatch(node.children, normalizedKeyword)
    if (childMatch)
      return childMatch
  }
  return null
}

export function collectPermissionNavigationPages(node, modulesByKey) {
  if (!node)
    return []
  if (node.nodeType === 'page') {
    const page = modulesByKey.get(node.moduleKey)?.pages.find(item => item.key === node.pageKey)
    return page ? [page] : []
  }
  return (node.children || []).flatMap(child => collectPermissionNavigationPages(child, modulesByKey))
}

export function collectPermissionNavigationExpandableKeys(tree) {
  return (tree || []).flatMap(node => [
    ...(node.children?.length ? [node.key] : []),
    ...collectPermissionNavigationExpandableKeys(node.children),
  ])
}

export function findPermissionNavigationPath(tree, key, path = []) {
  for (const node of tree || []) {
    const nextPath = [...path, node.key]
    if (String(node.key) === String(key))
      return nextPath
    const result = findPermissionNavigationPath(node.children, key, nextPath)
    if (result.length)
      return result
  }
  return []
}

export function navigationNodeStatus(node, checkedKeys) {
  const ids = uniqueIds(node?.resourceIds || [])
  if (ids.length === 0)
    return 'none'
  const checked = new Set((checkedKeys || []).map(String))
  const selectedCount = ids.filter(id => checked.has(String(id))).length
  if (selectedCount === 0)
    return 'none'
  if (selectedCount === ids.length)
    return 'all'
  return 'partial'
}

export function permissionSections(page) {
  const sections = [
    { key: 'button', label: '页面按钮', items: [] },
    { key: 'service', label: '后台服务', items: [] },
    { key: 'resource', label: '其他资源', items: [] },
  ]
  const sectionMap = new Map(sections.map(section => [section.key, section]))
  for (const item of page.actionItems || []) {
    const key = item.sectionKey || permissionSectionKeyByType(item.sources?.[0]?.type)
    const section = sectionMap.get(key) || sectionMap.get('resource')
    section.items.push(item)
  }
  return sections.filter(section => section.items.length > 0)
}

function collectOwnedActions(menuNode) {
  const result = []
  function walk(nodes) {
    for (const node of nodes || []) {
      const type = Number(node.resourceType)
      if (type === 2)
        continue
      if ([3, 4].includes(type))
        result.push(node)
      walk(node.children)
    }
  }
  walk(menuNode.children)
  return result
}

function collectAllActions(nodes, result = []) {
  for (const node of nodes || []) {
    if ([3, 4].includes(Number(node.resourceType)))
      result.push(node)
    collectAllActions(node.children, result)
  }
  return result
}

function groupPermissionItems(nodes, pageName) {
  const groups = new Map()
  for (const node of nodes) {
    const type = Number(node.resourceType)
    const actionKey = permissionActionKey(node)
    const key = `${permissionSectionKeyByType(type)}:${actionKey}`
    const current = groups.get(key) || {
      key,
      actionKey,
      sectionKey: permissionSectionKeyByType(type),
      label: '',
      resourceIds: [],
      permissions: [],
      resourceTypes: new Set(),
      hasBusinessLabel: false,
    }
    current.resourceIds.push(node.id)
    if (node.perms)
      current.permissions.push(String(node.perms).trim())
    current.resourceTypes.add(type)
    const label = normalizePermissionLabel(node.resourceName, pageName, actionKey)
    const isBusinessLabel = type === 3
    if (!current.label || (isBusinessLabel && !current.hasBusinessLabel)) {
      current.label = label
      current.hasBusinessLabel = isBusinessLabel
    }
    groups.set(key, current)
  }
  return [...groups.values()].map(({ hasBusinessLabel, resourceTypes, ...item }) => ({
    ...item,
    resourceIds: uniqueIds(item.resourceIds),
    permissions: uniqueIds(item.permissions),
    sources: permissionSourceMetas(resourceTypes),
  }))
}

function permissionSectionKeyByType(type) {
  if (Number(type) === 3)
    return 'button'
  if (Number(type) === 4)
    return 'service'
  return 'resource'
}

function permissionSourceMetas(resourceTypes) {
  return [...resourceTypes].sort((left, right) => left - right).map((type) => {
    if (type === 3)
      return { type, kind: 'button', label: '按钮' }
    if (type === 4)
      return { type, kind: 'service', label: '服务' }
    return { type, kind: 'resource', label: '资源' }
  })
}

function permissionActionKey(node) {
  const permission = String(node.perms || '')
  const segments = permission.split(':').filter(segment => segment && segment !== 'api')
  const rawAction = segments[segments.length - 1] || String(node.resourceName || node.id)
  const normalized = rawAction.toLowerCase().replace(/_/g, '-')
  const aliases = {
    page: 'view',
    list: 'view',
    query: 'view',
    get: 'detail',
    getbyid: 'detail',
    add: 'create',
    save: 'create',
    update: 'edit',
    remove: 'delete',
  }
  return aliases[normalized] || normalized
}

function normalizePermissionLabel(resourceName, pageName, actionKey) {
  let label = String(resourceName || '')
    .replace(/(?:按钮权限|按钮|接口权限|接口|API)$/i, '')
    .trim()
  if (pageName && label.startsWith(pageName))
    label = label.slice(pageName.length).trim()
  const fallbackLabels = {
    view: '查看',
    detail: '查看详情',
    create: '新增',
    edit: '编辑',
    delete: '删除',
    export: '导出',
    import: '导入',
  }
  return label || fallbackLabels[actionKey] || '使用'
}

function resolveDataScopeModuleCode(nodes, availableCodes) {
  const permissions = nodes.map(node => String(node.perms || '').trim()).filter(Boolean)
  return [...availableCodes]
    .sort((left, right) => right.length - left.length)
    .find(code => permissions.some(permission => permission === code || permission.startsWith(`${code}:`)))
}

export function uniqueIds(ids) {
  const seen = new Set()
  return (ids || []).filter((id) => {
    if (id == null || seen.has(String(id)))
      return false
    seen.add(String(id))
    return true
  })
}
