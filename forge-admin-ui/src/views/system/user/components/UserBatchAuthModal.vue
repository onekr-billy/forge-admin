<template>
  <!-- 批量授权弹窗 -->
  <n-modal
    v-model:show="batchAuthModalVisible"
    title="批量用户授权"
    class="user-batch-auth-modal"
    preset="card"
    style="width: 860px"
    :mask-closable="false"
  >
    <div class="auth-modal-content">
      <n-alert type="info" :bordered="false" class="batch-action-alert">
        已选择 {{ selectedUserIds.length }} 个用户，提交后会给这些用户追加所选角色。
      </n-alert>
      <n-form label-placement="left" label-width="90" class="batch-action-form">
        <n-form-item label="授权租户">
          <n-select
            v-model:value="batchAuthTenantId"
            :options="tenantSelectOptions"
            placeholder="请选择授权租户"
            filterable
          />
        </n-form-item>
        <n-form-item label="授权组织">
          <n-tree-select
            v-model:value="batchAuthOrgId"
            :options="batchAuthOrgTreeOptions"
            placeholder="请选择授权组织"
            filterable
            clearable
            key-field="value"
            label-field="label"
            children-field="children"
          />
        </n-form-item>
      </n-form>

      <UserRoleSelector />
    </div>

    <template #footer>
      <n-space justify="end">
        <n-button @click="batchAuthModalVisible = false">
          取消
        </n-button>
        <n-button
          type="primary"
          :loading="batchAuthSubmitLoading"
          @click="handleSubmitBatchAuth"
        >
          确定
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useUserManagementStore } from '@/stores/system/userManagementStore'

import UserRoleSelector from './UserRoleSelector.vue'

const store = useUserManagementStore()
const { selectedUserIds, batchAuthModalVisible, batchAuthSubmitLoading, batchAuthTenantId, batchAuthOrgId, batchAuthOrgTreeOptions, tenantSelectOptions } = storeToRefs(store)
const { handleSubmitBatchAuth } = store
</script>
