import { defineStore } from 'pinia'
import { shallowRef, watch } from 'vue'
import { useUserStore } from '@/store'
import { createUserActions } from './user-management/actions'
import { createUserCore } from './user-management/core'
import { createUserForm } from './user-management/form'
import { createUserMembership } from './user-management/membership'
import { createUserOrganization } from './user-management/organization'
import { createUserRelations } from './user-management/relations'
import { createUserRoles } from './user-management/roles'
import { createUserManagementState } from './user-management/state'

export const useUserManagementStore = defineStore('system-user-management', () => {
  const state = createUserManagementState()
  // 组件实例不进入 Pinia 状态，避免深层代理和序列化；卸载时释放。
  const crudRef = shallowRef(null)
  const runtime = { userStore: useUserStore(), crudRef }
  const workspace = {}
  Object.assign(workspace, createUserCore(state, runtime, workspace))
  Object.assign(workspace, createUserForm(state, runtime, workspace))
  Object.assign(workspace, createUserOrganization(state, runtime, workspace))
  Object.assign(workspace, createUserRoles(state, runtime, workspace))
  Object.assign(workspace, createUserMembership(state, runtime, workspace))
  Object.assign(workspace, createUserRelations(state, runtime, workspace))
  Object.assign(workspace, createUserActions(state, runtime, workspace))

  function attachList(instance) {
    crudRef.value = instance
  }

  function setDictionaries(value) {
    state.dict.value = value
  }

  function resetWorkspace() {
    crudRef.value = null
    const defaults = createUserManagementState()
    for (const key of Object.keys(state)) {
      state[key].value = defaults[key].value
    }
  }

  watch(state.resetPwdModalVisible, (visible) => {
    if (!visible) {
      state.resetPwdForm.value = { id: null, password: '' }
    }
  }, { flush: 'sync' })

  return { ...state, ...workspace, attachList, setDictionaries, resetWorkspace }
})
