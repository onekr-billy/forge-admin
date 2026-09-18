<template>
  <!-- 批量加入租户弹窗 -->
  <n-modal
    v-model:show="batchTenantModalVisible"
    title="批量加入租户"
    class="user-batch-tenant-modal"
    preset="card"
    style="width: 520px"
    :mask-closable="false"
  >
    <n-alert type="info" :bordered="false" class="batch-action-alert">
      已选择 {{ selectedUserIds.length }} 个用户，提交后会将这些用户加入目标租户。
    </n-alert>
    <n-form label-placement="left" label-width="90" class="batch-action-form">
      <n-form-item label="目标租户">
        <n-select
          v-model:value="batchTenantForm.tenantId"
          :options="tenantSelectOptions"
          placeholder="请选择目标租户"
          filterable
        />
      </n-form-item>
      <n-form-item label="成员类型">
        <n-select
          v-model:value="batchTenantForm.memberType"
          :options="tenantMemberTypeOptions"
          placeholder="请选择成员类型"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="batchTenantModalVisible = false">
          取消
        </n-button>
        <n-button
          type="primary"
          :loading="batchTenantSubmitLoading"
          @click="handleSubmitBatchTenant"
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

const store = useUserManagementStore()
const { selectedUserIds, batchTenantModalVisible, batchTenantSubmitLoading, batchTenantForm, tenantSelectOptions, tenantMemberTypeOptions } = storeToRefs(store)
const { handleSubmitBatchTenant } = store
</script>
