<template>
  <!-- 重置密码弹窗 -->
  <n-modal
    v-model:show="resetPwdModalVisible"
    title="重置密码"
    preset="card"
    style="width: 400px"
  >
    <n-form
      ref="resetPwdFormRef"
      :model="resetPwdForm"
      :rules="resetPwdRules"
      label-placement="left"
      label-width="80"
    >
      <n-form-item label="新密码" path="password">
        <n-input
          v-model:value="resetPwdForm.password"
          type="password"
          show-password-on="click"
          placeholder="请输入新密码"
        />
      </n-form-item>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="resetPwdModalVisible = false">
          取消
        </n-button>
        <n-button type="primary" :loading="resetPwdLoading" @click="handleConfirmResetPwd(resetPwdFormRef)">
          确定
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { ref } from 'vue'

import { resetPwdRules } from '@/stores/system/user-management/utils'
import { useUserManagementStore } from '@/stores/system/userManagementStore'

const resetPwdFormRef = ref(null)
const store = useUserManagementStore()
const { resetPwdModalVisible, resetPwdLoading, resetPwdForm } = storeToRefs(store)
const { handleConfirmResetPwd } = store
</script>
