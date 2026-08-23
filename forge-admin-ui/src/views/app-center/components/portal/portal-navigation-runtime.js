import { createPageManagementSystemNavigationNodes } from '../../in-app-builder/page-management'

/**
 * Select the application page tree for the client that is rendering it.
 *
 * A published application snapshot can be shared by the management portal
 * and the H5 runtime.  The snapshot therefore contains both client trees;
 * filtering must happen before the navigation component builds its tree.
 */
export function filterNavigationNodesByClient(nodes, clientCode = 'pc') {
  const targetClient = normalizeClientCode(clientCode)
  const source = Array.isArray(nodes) ? nodes : []
  const normalized = source
    .map((node, index) => normalizeRuntimeNode(node, index))
    .filter(Boolean)

  const byId = new Map(normalized.map(node => [node.id, node]))
  const visiblePageIds = new Set(
    normalized
      .filter(node => node.type === 'page' && resolveNodeClientCodes(node).includes(targetClient))
      .map(node => node.id),
  )
  const included = new Set(visiblePageIds)

  // Keep every ancestor of a visible page, even when the group itself was
  // left at the legacy ADMIN default.  This makes a MOBILE page nested below
  // an old/default page group render as a proper parent in H5.
  visiblePageIds.forEach((pageId) => {
    const visited = new Set([pageId])
    let parentId = byId.get(pageId)?.parentId || null
    while (parentId && !visited.has(parentId)) {
      visited.add(parentId)
      const parent = byId.get(parentId)
      if (!parent)
        break
      included.add(parent.id)
      parentId = parent.parentId
    }
  })

  return normalized
    .filter(node => included.has(node.id))
    .map(({ _index, ...node }) => node)
}

/**
 * Build the navigation used by the published application portal.
 *
 * Workflow workspace pages belong to the desktop application shell rather
 * than the user's published page tree, so they are projected at runtime and
 * kept before all user-managed pages. H5 continues to render only pages that
 * were explicitly mounted to the mobile client.
 */
export function buildApplicationPortalNavigationNodes(nodes, clientCode = 'pc') {
  const targetClient = normalizeClientCode(clientCode)
  const clientNodes = filterNavigationNodesByClient(nodes, targetClient)
  if (targetClient !== 'pc')
    return clientNodes

  const systemNodes = createPageManagementSystemNavigationNodes()
  const systemPageIds = new Set(systemNodes.map(node => node.id))
  return [
    ...systemNodes,
    ...clientNodes.filter(node => !systemPageIds.has(node.id)),
  ]
}

export function resolveNodeClientCodes(node = {}) {
  const target = String(
    node.mountTarget
    ?? node.settings?.mountTarget
    ?? 'ADMIN',
  ).trim().toUpperCase()

  if (target === 'MOBILE' || target === 'H5')
    return ['h5']
  if (target === 'BOTH' || target === 'ALL')
    return ['pc', 'h5']
  if (target === 'API' || target === 'NONE')
    return []
  return ['pc']
}

function normalizeClientCode(value) {
  const normalized = String(value || '').trim().toLowerCase()
  return normalized === 'h5' || normalized === 'mobile' ? 'h5' : 'pc'
}

function normalizeRuntimeNode(node, index) {
  if (!node || typeof node !== 'object')
    return null
  const id = String(node.id || '').trim()
  if (!id)
    return null
  const rawType = String(node.type || node.nodeType || node.kind || '').trim().toLowerCase()
  const type = ['group', 'page-group', 'page_group', 'pagegroup', 'menu-group', 'menu_group', 'directory', 'folder'].includes(rawType)
    ? 'group'
    : 'page'
  const parentValue = node.parentId ?? node.parentNodeId ?? node.parentID ?? node.settings?.parentId
  return {
    ...node,
    id,
    type,
    parentId: parentValue == null || parentValue === '' ? null : String(parentValue),
    _index: index,
  }
}
