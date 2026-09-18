import { computed, watch } from 'vue'
import { request } from '@/utils'
import { normalizeNumberList, normalizeSingleNumber } from './utils'

// 共享状态由 Pinia 创建；跨领域调用通过同一工作台实例协调。
export function createUserMembership(state, runtime, workspace) {
  const { relationModalVisible, currentUser, postLoading, postSubmitLoading, postList, checkedPostKeys, mainPostId, tenantLoading, tenantSubmitLoading, tenantOptions, checkedTenantKeys, defaultTenantId, batchTenantModalVisible, batchTenantSubmitLoading, batchTenantForm } = state
  const { userStore, crudRef } = runtime
  const tenantSelectOptions = computed(() => tenantOptions.value.map(item => ({
    label: item.tenantName,
    value: item.id,
  })))

  const tenantMemberTypeOptions = computed(() => workspace.userTypeOptions.value.filter(item => Number(item.value) !== 0))

  const selectedTenantOptions = computed(() => tenantOptions.value
    .filter(item => checkedTenantKeys.value.includes(item.id))
    .map(item => ({
      label: item.tenantName,
      value: item.id,
    })))

  const selectedPostOptions = computed(() => postList.value
    .filter(item => checkedPostKeys.value.includes(item.id))
    .map(item => ({
      label: item.postName,
      value: item.id,
    })))

  async function loadTenantOptions() {
    try {
      const res = await request.get('/system/tenant/assignable/options')
      if (res.code === 200) {
        tenantOptions.value = (res.data || [])
          .map(item => ({
            ...item,
            id: normalizeSingleNumber(item.id),
          }))
          .filter(item => item.id !== null)
      }
    }
    catch (error) {
      console.error('加载租户选项失败:', error)
    }
  }

  async function loadPostList(tenantId = workspace.resolveOperationTenantId()) {
    try {
      postLoading.value = true
      const res = await request.get('/system/post/list', {
        params: workspace.buildTenantParams(tenantId),
      })
      if (res.code === 200) {
        postList.value = res.data || []
      }
    }
    catch (error) {
      console.error('加载岗位列表失败:', error)
      window.$message.error('加载岗位列表失败')
    }
    finally {
      postLoading.value = false
    }
  }

  async function loadUserPosts(userId) {
    try {
      postLoading.value = true
      const res = await request.get(`/system/user/${userId}/posts`, {
        params: workspace.buildTenantParams(workspace.resolveOperationTenantId()),
      })
      if (res.code === 200) {
        checkedPostKeys.value = normalizeNumberList(res.data || [])
        mainPostId.value = checkedPostKeys.value.length > 0 ? checkedPostKeys.value[0] : null
      }
    }
    catch (error) {
      console.error('加载用户岗位失败:', error)
      window.$message.error('加载用户岗位失败')
    }
    finally {
      postLoading.value = false
    }
  }

  async function handleSubmitPost() {
    if (checkedPostKeys.value.length === 0) {
      window.$message.warning('请至少选择一个岗位')
      return
    }

    try {
      postSubmitLoading.value = true
      const res = await request.post(
        `/system/user/${currentUser.value.id}/posts`,
        {
          postIds: checkedPostKeys.value,
          mainPostId: mainPostId.value,
        },
        { params: workspace.buildTenantParams(workspace.resolveOperationTenantId()) },
      )
      if (res.code === 200) {
        window.$message.success('岗位绑定成功')
        relationModalVisible.value = false
        crudRef.value?.refresh()
      }
    }
    catch (error) {
      console.error('岗位绑定失败:', error)
      window.$message.error('岗位绑定失败')
    }
    finally {
      postSubmitLoading.value = false
    }
  }

  async function handleOpenBatchTenant() {
    if (!userStore.isAdmin) {
      window.$message.warning('只有超级管理员可以批量加入租户')
      return
    }
    const userIds = workspace.getSelectedBatchUserIds()
    if (userIds.length === 0) {
      return
    }

    if (tenantOptions.value.length === 0) {
      await loadTenantOptions()
    }

    batchTenantForm.value = {
      tenantId: workspace.resolveDefaultBatchTenantId(),
      memberType: 2,
    }
    batchTenantModalVisible.value = true
  }

  async function loadUserTenants(userId) {
    try {
      tenantLoading.value = true
      const res = await request.get(`/system/user/${userId}/tenants`)
      if (res.code === 200) {
        const list = res.data || []
        checkedTenantKeys.value = list
          .filter(item => item.status !== 0)
          .map(item => normalizeSingleNumber(item.tenantId))
          .filter(item => item !== null)
        const defaultTenant = list.find(item => item.isDefault === 1)
        defaultTenantId.value = normalizeSingleNumber(defaultTenant?.tenantId) || checkedTenantKeys.value[0] || null
      }
    }
    catch (error) {
      console.error('加载用户租户失败:', error)
      window.$message.error('加载用户租户失败')
    }
    finally {
      tenantLoading.value = false
    }
  }

  async function handleSubmitTenant() {
    if (checkedTenantKeys.value.length === 0) {
      window.$message.warning('请至少选择一个租户')
      return
    }
    if (!defaultTenantId.value || !checkedTenantKeys.value.includes(defaultTenantId.value)) {
      defaultTenantId.value = checkedTenantKeys.value[0]
    }

    try {
      tenantSubmitLoading.value = true
      const res = await request.post(
        `/system/user/${currentUser.value.id}/tenants`,
        {
          tenantIds: checkedTenantKeys.value,
          defaultTenantId: defaultTenantId.value,
          memberType: normalizeSingleNumber(currentUser.value.userType, 2) === 1 ? 1 : 2,
        },
      )
      if (res.code === 200) {
        window.$message.success('租户绑定成功')
        relationModalVisible.value = false
        crudRef.value?.refresh()
      }
    }
    catch (error) {
      console.error('租户绑定失败:', error)
      window.$message.error('租户绑定失败')
    }
    finally {
      tenantSubmitLoading.value = false
    }
  }

  async function handleSubmitBatchTenant() {
    const userIds = workspace.getSelectedBatchUserIds()
    if (userIds.length === 0) {
      return
    }

    const tenantId = normalizeSingleNumber(batchTenantForm.value.tenantId)
    if (tenantId === null) {
      window.$message.warning('请选择目标租户')
      return
    }

    try {
      batchTenantSubmitLoading.value = true
      const res = await request.post('/system/user/batch/tenants', {
        userIds,
        tenantId,
        memberType: normalizeSingleNumber(batchTenantForm.value.memberType, 2),
      })
      if (res.code === 200) {
        window.$message.success('批量加入租户成功')
        batchTenantModalVisible.value = false
        workspace.clearBatchSelection()
        crudRef.value?.refresh()
      }
    }
    catch (error) {
      console.error('批量加入租户失败:', error)
      window.$message.error('批量加入租户失败')
    }
    finally {
      batchTenantSubmitLoading.value = false
    }
  }

  watch(checkedTenantKeys, (keys) => {
    if (defaultTenantId.value && !keys.includes(defaultTenantId.value)) {
      defaultTenantId.value = keys[0] || null
    }
  })

  watch(checkedPostKeys, (keys) => {
    if (mainPostId.value && !keys.includes(mainPostId.value)) {
      mainPostId.value = null
    }
  })

  return { tenantSelectOptions, tenantMemberTypeOptions, selectedTenantOptions, selectedPostOptions, loadTenantOptions, loadPostList, loadUserPosts, handleSubmitPost, handleOpenBatchTenant, loadUserTenants, handleSubmitTenant, handleSubmitBatchTenant }
}
