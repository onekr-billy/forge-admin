import { computed, h } from 'vue'
import { request } from '@/utils'
import { normalizeNumberList } from './utils'

// 共享状态由 Pinia 创建；跨领域调用通过同一工作台实例协调。
export function createUserActions(state, runtime, workspace) {
  const { selectedUserIds, resetPwdModalVisible, resetPwdLoading, resetPwdForm } = state
  const { userStore, crudRef } = runtime
  const userBatchActionOptions = computed(() => [
    {
      label: '批量授权',
      key: 'auth',
      icon: () => h('i', { class: 'i-material-symbols:verified-user-outline-rounded' }),
    },
    ...(userStore.isAdmin
      ? [{
          label: '加入租户',
          key: 'tenant',
          icon: () => h('i', { class: 'i-material-symbols:group-add' }),
        }]
      : []),
  ])

  function handleUserSelectionChange(payload = {}) {
    const keys = Array.isArray(payload) ? payload : payload.keys
    selectedUserIds.value = normalizeNumberList(keys || [])
  }

  function handleUserBatchActionSelect(key) {
    if (key === 'auth') {
      workspace.handleOpenBatchAuth()
      return
    }
    if (key === 'tenant') {
      workspace.handleOpenBatchTenant()
    }
  }

  function clearBatchSelection() {
    selectedUserIds.value = []
    crudRef.value?.clearSelection?.()
  }

  async function handleConfirmResetPwd(formRef) {
    formRef?.validate(async (errors) => {
      if (!errors) {
        try {
          resetPwdLoading.value = true
          const res = await request.post('/system/user/resetPwd', null, {
            params: {
              id: resetPwdForm.value.id,
              password: resetPwdForm.value.password,
            },
          })
          if (res.code === 200) {
            window.$message.success('重置成功')
            resetPwdModalVisible.value = false
          }
        }
        catch {
          window.$message.error('重置失败')
        }
        finally {
          resetPwdLoading.value = false
        }
      }
    })
  }

  async function handleUpdateStatus(row, status) {
    const actionText = status === 1 ? '启用' : '禁用'
    window.$dialog.warning({
      title: '确认操作',
      content: `确定要${actionText}用户"${row.username}"吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await request.post('/system/user/updateStatus', null, {
            params: { id: row.id, status },
          })
          if (res.code === 200) {
            window.$message.success(`${actionText}成功`)
            crudRef.value?.refresh()
          }
        }
        catch {
          window.$message.error(`${actionText}失败`)
        }
      },
    })
  }

  async function handleUntieDisable(row) {
    window.$dialog.warning({
      title: '确认操作',
      content: `确定要启用并解封用户"${row.username}"吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await request.post('/system/user/doUntieDisable', null, {
            params: { id: row.id },
          })
          if (res.code === 200) {
            window.$message.success(`用户已启用`)
            crudRef.value?.refresh()
          }
        }
        catch {
          window.$message.error(`启用失败`)
        }
      },
    })
  }

  function handleEdit(row) {
    crudRef.value?.showEdit(row)
  }

  function handleDelete(row) {
    window.$dialog.warning({
      title: '确认删除',
      content: `确定要删除用户"${row.username}"吗？删除后将无法恢复！`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await request.post('/system/user/remove', null, { params: { id: row.id } })
          if (res.code === 200) {
            window.$message.success('删除成功')
            crudRef.value?.refresh()
          }
        }
        catch {
          window.$message.error('删除失败')
        }
      },
    })
  }

  return { userBatchActionOptions, handleUserSelectionChange, handleUserBatchActionSelect, clearBatchSelection, handleConfirmResetPwd, handleUpdateStatus, handleUntieDisable, handleEdit, handleDelete }
}
