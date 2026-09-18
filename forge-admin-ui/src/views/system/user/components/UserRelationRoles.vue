<template>
  <!-- 角色授权 -->
  <div v-show="relationActiveTab === 'auth'" class="relation-section auth-modal-content">
    <n-alert type="info" :bordered="false" class="batch-action-alert">
      角色授权按组织生效。先选授权组织，再查看该组织可用角色和当前已拥有角色。
    </n-alert>

    <div v-if="authOrgOptions.length === 0" class="relation-empty-action">
      <i class="i-material-symbols:account-tree-rounded" />
      <strong>还没有可授权组织</strong>
      <span>角色需要挂在具体组织下。先在当前弹窗维护组织关系，再回来设置角色。</span>
      <n-button size="small" type="primary" secondary @click="handleRelationTabChange('org')">
        先维护组织关系
      </n-button>
    </div>

    <template v-else>
      <n-form label-placement="left" label-width="90" class="batch-action-form">
        <n-form-item label="授权组织">
          <n-select
            v-model:value="authOrgId"
            :options="authOrgOptions"
            placeholder="请选择授权组织"
            filterable
          />
        </n-form-item>
      </n-form>

      <UserRoleSelector />
    </template>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useUserManagementStore } from '@/stores/system/userManagementStore'

import UserRoleSelector from './UserRoleSelector.vue'

const store = useUserManagementStore()
const { relationActiveTab, authOrgId, authOrgOptions } = storeToRefs(store)
const { handleRelationTabChange } = store
</script>
