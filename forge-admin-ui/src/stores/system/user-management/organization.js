import { computed, watch } from 'vue'
import { request } from '@/utils'
import { convertRegionToTreeSelect, countTreeNodes, findOrgNode, flattenOrgNodes, getAllKeys, isSameKey, normalizeNumberList, normalizeOrgTreeNodes, normalizeSingleNumber } from './utils'

// 共享状态由 Pinia 创建；跨领域调用通过同一工作台实例协调。
export function createUserOrganization(state, runtime, workspace) {
  const { leftOrgTreeData, leftOrgTreeLoading, leftOrgExpandAll, leftOrgExpandedKeys, leftOrgPanelCollapsed, selectedOrgKeys, selectedOrgNode, isShowAllUsers, relationModalVisible, currentUser, orgLoading, orgSubmitLoading, orgTreeData, mainOrgId, checkedOrgKeys, orgTreeExpandAll, orgTreeExpandedKeys, searchRegionOptions, editRegionOptions } = state
  const { crudRef } = runtime
  const orgTreeSummaryText = computed(() => {
    const total = countTreeNodes(leftOrgTreeData.value)
    if (!total) {
      return '未加载组织'
    }
    return `${total} 个组织节点`
  })

  const mainOrgName = computed(() => {
    if (!mainOrgId.value) {
      return ''
    }
    const node = findOrgNode(orgTreeData.value, mainOrgId.value)
    return node?.orgName || ''
  })

  const selectedOrgOptions = computed(() => flattenOrgNodes(orgTreeData.value)
    .filter(item => checkedOrgKeys.value.includes(normalizeSingleNumber(item.id)))
    .map(item => ({
      label: item.orgName,
      value: normalizeSingleNumber(item.id),
    })))

  function getUserOrgNodeMeta(node = {}) {
    if (isSameKey(node.id, mainOrgId.value)) {
      return { value: '主组织' }
    }
    if (checkedOrgKeys.value.includes(normalizeSingleNumber(node.id))) {
      return { value: '已选' }
    }
    return null
  }

  async function fetchOrgRootNodes(tenantId) {
    const res = await request.get('/system/org/lazyTree', {
      params: {
        parentId: 0,
        ...workspace.buildTenantParams(tenantId),
      },
    })
    if (res.code !== 200) {
      return []
    }
    return normalizeOrgTreeNodes(res.data || [])
  }

  async function fetchOrgChildrenNodes(parentId, tenantId) {
    const res = await request.get(`/system/org/children/${parentId}`, {
      params: workspace.buildTenantParams(tenantId),
    })
    if (res.code !== 200) {
      return []
    }
    return normalizeOrgTreeNodes(res.data || [])
  }

  async function hydrateOrgTreeNodes(nodes, tenantId) {
    await Promise.all((nodes || []).map(async (node) => {
      if (!node?.hasChildren) {
        node.children = Array.isArray(node.children) ? node.children : []
        return
      }
      const children = await fetchOrgChildrenNodes(node.id, tenantId)
      node.children = children
      if (children.length > 0) {
        await hydrateOrgTreeNodes(children, tenantId)
      }
    }))
  }

  async function loadCompleteOrgTree(tenantId) {
    const roots = await fetchOrgRootNodes(tenantId)
    await hydrateOrgTreeNodes(roots, tenantId)
    return roots
  }

  async function loadLeftOrgTree(tenantId) {
    try {
      leftOrgTreeLoading.value = true
      leftOrgTreeData.value = await loadCompleteOrgTree(tenantId)
      if (leftOrgExpandAll.value) {
        leftOrgExpandedKeys.value = getAllKeys(leftOrgTreeData.value)
      }

      // 同时加载行政区划选项
      await loadRegionOptions()
    }
    catch (error) {
      console.error('加载组织树失败:', error)
      window.$message.error('加载组织树失败')
    }
    finally {
      leftOrgTreeLoading.value = false
    }
  }

  async function loadRegionOptions() {
    try {
      const res = await request.get('/system/region/treeAll', { params: { dataRight: true } })
      if (res.code === 200) {
        const data = res.data || []
        // 搜索场景：虚拟节点可选（代表"该区域下所有"）
        searchRegionOptions.value = convertRegionToTreeSelect(data, false)
        // 编辑场景：虚拟节点不可选（避免存入ALL后缀编码）
        editRegionOptions.value = convertRegionToTreeSelect(data, true)
      }
    }
    catch (error) {
      console.error('加载行政区划选项失败:', error)
    }
  }

  function handleOrgNodeSelect(keys) {
    selectedOrgKeys.value = keys

    if (keys.length > 0) {
      isShowAllUsers.value = false
      const orgId = keys[0]
      selectedOrgNode.value = findOrgNode(leftOrgTreeData.value, orgId)
      crudRef.value?.refresh()
    }
    else {
      selectedOrgNode.value = null
      crudRef.value?.refresh()
    }
  }

  function handleLeftOrgExpandedKeysChange(keys) {
    leftOrgExpandedKeys.value = keys
  }

  function toggleOrgExpandAll() {
    leftOrgExpandAll.value = !leftOrgExpandAll.value

    if (leftOrgExpandAll.value) {
      leftOrgExpandedKeys.value = getAllKeys(leftOrgTreeData.value)
    }
    else {
      leftOrgExpandedKeys.value = []
    }
  }

  function toggleUserOrgExpandAll() {
    orgTreeExpandAll.value = !orgTreeExpandAll.value

    if (orgTreeExpandAll.value) {
      orgTreeExpandedKeys.value = getAllKeys(orgTreeData.value)
    }
    else {
      orgTreeExpandedKeys.value = []
    }
  }

  function toggleLeftOrgPanel() {
    leftOrgPanelCollapsed.value = !leftOrgPanelCollapsed.value
  }

  function handleClearOrgFilter() {
    selectedOrgKeys.value = []
    selectedOrgNode.value = null
    isShowAllUsers.value = true
    crudRef.value?.refresh()
  }

  function handleSelectAllUsers() {
    isShowAllUsers.value = true
    selectedOrgKeys.value = []
    selectedOrgNode.value = null
    crudRef.value?.refresh()
  }

  function beforeLoadList(params) {
    Object.assign(params, workspace.buildTenantParams())
    if (selectedOrgNode.value && !isShowAllUsers.value) {
      params.orgId = selectedOrgNode.value.id
    }
    return params
  }

  async function beforeSearch(params) {
    selectedOrgKeys.value = []
    selectedOrgNode.value = null
    isShowAllUsers.value = true
    await loadLeftOrgTree()
    return params
  }

  async function loadOrgTree(tenantId = workspace.resolveOperationTenantId()) {
    try {
      orgLoading.value = true
      orgTreeData.value = await loadCompleteOrgTree(tenantId)
      if (orgTreeExpandAll.value) {
        orgTreeExpandedKeys.value = getAllKeys(orgTreeData.value)
      }
    }
    catch (error) {
      console.error('加载组织列表失败:', error)
      window.$message.error('加载组织列表失败')
    }
    finally {
      orgLoading.value = false
    }
  }

  async function loadUserOrgs(userId) {
    try {
      orgLoading.value = true
      const res = await request.get(`/system/user/${userId}/org-bindings`, {
        params: workspace.buildTenantParams(workspace.resolveOperationTenantId()),
      })
      if (res.code === 200) {
        const bindings = res.data || []
        checkedOrgKeys.value = normalizeNumberList(bindings.map(item => item.orgId))
        const mainBinding = bindings.find(item => Number(item.isMain) === 1)
        mainOrgId.value = normalizeSingleNumber(mainBinding?.orgId) || checkedOrgKeys.value[0] || null
      }
    }
    catch (error) {
      console.error('加载用户组织失败:', error)
      window.$message.error('加载用户组织失败')
    }
    finally {
      orgLoading.value = false
    }
  }

  function handleOrgExpandedKeysChange(keys) {
    orgTreeExpandedKeys.value = keys
  }

  function handleOrgCheckedKeysChange(keys) {
    checkedOrgKeys.value = normalizeNumberList(keys || [])
    if (mainOrgId.value && !checkedOrgKeys.value.includes(mainOrgId.value)) {
      mainOrgId.value = checkedOrgKeys.value[0] || null
    }
    else if (!mainOrgId.value && checkedOrgKeys.value.length > 0) {
      mainOrgId.value = checkedOrgKeys.value[0]
    }
  }

  async function handleSubmitOrg() {
    if (checkedOrgKeys.value.length === 0) {
      window.$message.warning('请至少选择一个组织')
      return
    }
    if (!mainOrgId.value || !checkedOrgKeys.value.includes(mainOrgId.value)) {
      window.$message.warning('请选择主组织')
      return
    }

    try {
      orgSubmitLoading.value = true
      const res = await request.post(
        `/system/user/${currentUser.value.id}/orgs`,
        {
          orgIds: checkedOrgKeys.value,
          mainOrgId: mainOrgId.value,
        },
        { params: workspace.buildTenantParams(workspace.resolveOperationTenantId()) },
      )
      if (res.code === 200) {
        window.$message.success('组织绑定成功')
        relationModalVisible.value = false
        crudRef.value?.refresh()
      }
    }
    catch (error) {
      console.error('组织绑定失败:', error)
      window.$message.error('组织绑定失败')
    }
    finally {
      orgSubmitLoading.value = false
    }
  }

  watch(checkedOrgKeys, (keys) => {
    if (mainOrgId.value && !keys.includes(mainOrgId.value)) {
      mainOrgId.value = keys[0] || null
    }
  })

  return { orgTreeSummaryText, mainOrgName, selectedOrgOptions, getUserOrgNodeMeta, fetchOrgRootNodes, fetchOrgChildrenNodes, hydrateOrgTreeNodes, loadCompleteOrgTree, loadLeftOrgTree, loadRegionOptions, handleOrgNodeSelect, handleLeftOrgExpandedKeysChange, toggleOrgExpandAll, toggleUserOrgExpandAll, toggleLeftOrgPanel, handleClearOrgFilter, handleSelectAllUsers, beforeLoadList, beforeSearch, loadOrgTree, loadUserOrgs, handleOrgExpandedKeysChange, handleOrgCheckedKeysChange, handleSubmitOrg }
}
