import { computed } from 'vue'
import { formatTenantNameList, isSameKey, normalizeSingleNumber, splitTableCellValues } from './utils'

// 工作台编排仅协调各关系领域，不直接承载接口实现。
export function createUserRelations(state, runtime, workspace) {
  const { relationModalVisible, relationActiveTab, relationSubmitLoading, currentUser, roleSearchKeyword, checkedRoleKeys, authOrgId, currentUserOrgBindings, rolePagination, mainOrgId, checkedOrgKeys, postList, checkedPostKeys, mainPostId, tenantOptions, checkedTenantKeys, defaultTenantId } = state
  const { userStore } = runtime
  const relationTabItems = computed(() => [
    {
      key: 'overview',
      label: '授权概览',
      desc: '查看当前用户关系',
    },
    {
      key: 'org',
      label: '组织关系',
      desc: '维护主组织与成员组织',
    },
    {
      key: 'auth',
      label: '按组织授权',
      desc: '维护组织内的角色权限',
    },
    {
      key: 'post',
      label: '岗位关系',
      desc: '维护主岗位与兼职岗位',
    },
    ...(userStore.isAdmin
      ? [{
          key: 'tenant',
          label: '租户关系',
          desc: '维护可访问工作区',
        }]
      : []),
  ])

  const currentRelationTabMeta = computed(() =>
    relationTabItems.value.find(item => item.key === relationActiveTab.value) || relationTabItems.value[0])

  const relationOrgSummaryItems = computed(() => {
    const bindings = currentUserOrgBindings.value || []
    if (bindings.length > 0) {
      return bindings.map((item, index) => ({
        key: `${item.orgId || index}`,
        label: item.orgName || String(item.orgId || '-'),
        meta: Number(item.isMain) === 1 ? '主组织' : '',
      }))
    }
    return splitTableCellValues(currentUser.value?.orgName || currentUser.value?.orgNames).map((label, index) => ({
      key: `row-org-${index}`,
      label,
      meta: index === 0 ? '主组织' : '',
    }))
  })

  const relationRoleSummaryItems = computed(() => {
    const items = []
    currentUserOrgBindings.value.forEach((binding, index) => {
      const roleNames = Array.isArray(binding.roleNames) ? binding.roleNames.filter(Boolean) : []
      roleNames.forEach((roleName, roleIndex) => {
        items.push({
          key: `${binding.orgId || index}-${roleName}-${roleIndex}`,
          label: roleName,
          meta: binding.orgName || '组织内授权',
        })
      })
    })
    return items
  })

  const relationPostSummaryItems = computed(() => {
    const selectedNames = postList.value
      .filter(item => checkedPostKeys.value.includes(normalizeSingleNumber(item.id)))
      .map((item, index) => ({
        key: `post-${item.id || index}`,
        label: item.postName || String(item.id || '-'),
        meta: index === 0 ? '主岗位' : '',
      }))
    if (selectedNames.length > 0) {
      return selectedNames
    }
    return splitTableCellValues(currentUser.value?.postName || currentUser.value?.postNames).map((label, index) => ({
      key: `row-post-${index}`,
      label,
      meta: index === 0 ? '主岗位' : '',
    }))
  })

  const relationTenantSummaryItems = computed(() => {
    const selectedNames = tenantOptions.value
      .filter(item => checkedTenantKeys.value.includes(normalizeSingleNumber(item.id)))
      .map(item => ({
        key: `tenant-${item.id}`,
        label: item.tenantName || String(item.id || '-'),
        meta: isSameKey(item.id, defaultTenantId.value) ? '默认租户' : '',
      }))
    if (selectedNames.length > 0) {
      return selectedNames
    }
    return formatTenantNameList(currentUser.value).map((label, index) => ({
      key: `row-tenant-${index}`,
      label,
      meta: index === 0 ? '默认租户' : '',
    }))
  })

  async function handleRelation(row) {
    currentUser.value = row
    relationModalVisible.value = true
    relationActiveTab.value = 'overview'
    await loadRelationTab('overview', row)
  }

  async function loadRelationTab(tab, row) {
    const user = row || currentUser.value
    if (!user?.id) {
      return
    }
    if (tab === 'overview') {
      await loadRelationOverview(user)
    }
    else if (tab === 'auth') {
      roleSearchKeyword.value = ''
      rolePagination.value.page = 1
      checkedRoleKeys.value = []
      authOrgId.value = null
      await workspace.loadUserOrgBindings(user.id, workspace.resolveOperationTenantId(user))
      authOrgId.value = workspace.resolveDefaultAuthOrgId()
      if (authOrgId.value) {
        await workspace.loadRoleList(workspace.resolveOperationTenantId(user), authOrgId.value)
        await workspace.loadUserRoles(user.id, authOrgId.value)
      }
    }
    else if (tab === 'org') {
      mainOrgId.value = null
      checkedOrgKeys.value = []
      await workspace.loadOrgTree(workspace.resolveOperationTenantId(user))
      await workspace.loadUserOrgs(user.id)
    }
    else if (tab === 'post') {
      checkedPostKeys.value = []
      mainPostId.value = null
      await workspace.loadPostList(workspace.resolveOperationTenantId(user))
      await workspace.loadUserPosts(user.id)
    }
    else if (tab === 'tenant') {
      checkedTenantKeys.value = []
      defaultTenantId.value = null
      if (tenantOptions.value.length === 0) {
        await workspace.loadTenantOptions()
      }
      await workspace.loadUserTenants(user.id)
    }
  }

  async function loadRelationOverview(user) {
    currentUserOrgBindings.value = []
    checkedPostKeys.value = []
    checkedTenantKeys.value = []
    defaultTenantId.value = null

    await workspace.loadUserOrgBindings(user.id, workspace.resolveOperationTenantId(user))
    await workspace.loadPostList(workspace.resolveOperationTenantId(user))
    await workspace.loadUserPosts(user.id)
    if (userStore.isAdmin) {
      if (tenantOptions.value.length === 0) {
        await workspace.loadTenantOptions()
      }
      await workspace.loadUserTenants(user.id)
    }
  }

  async function handleRelationTabChange(tab) {
    if (relationActiveTab.value !== tab) {
      relationActiveTab.value = tab
    }
    await loadRelationTab(tab, currentUser.value)
  }

  async function handleRelationSubmit() {
    const tab = relationActiveTab.value
    relationSubmitLoading.value = true
    try {
      if (tab === 'auth') {
        await workspace.handleSubmitAuth()
      }
      else if (tab === 'org') {
        await workspace.handleSubmitOrg()
      }
      else if (tab === 'post') {
        await workspace.handleSubmitPost()
      }
      else if (tab === 'tenant') {
        await workspace.handleSubmitTenant()
      }
    }
    finally {
      relationSubmitLoading.value = false
    }
  }

  return { relationTabItems, currentRelationTabMeta, relationOrgSummaryItems, relationRoleSummaryItems, relationPostSummaryItems, relationTenantSummaryItems, handleRelation, loadRelationTab, loadRelationOverview, handleRelationTabChange, handleRelationSubmit }
}
