<template>
  <!-- 租户绑定（仅管理员可见） -->
  <div v-show="relationActiveTab === 'tenant' && userStore.isAdmin" class="relation-section tenant-modal-content">
    <n-spin :show="tenantLoading">
      <div class="relation-primary-setting">
        <div class="relation-primary-copy">
          <strong>默认工作区</strong>
          <span>用户登录后默认进入的租户空间。</span>
        </div>
        <div class="relation-primary-control">
          <n-select
            v-model:value="defaultTenantId"
            :options="selectedTenantOptions"
            placeholder="请选择默认租户"
          />
        </div>
      </div>

      <div class="relation-option-section">
        <div class="relation-option-heading">
          <div>
            <strong>可访问租户</strong>
            <span>控制用户可切换和访问的工作区。</span>
          </div>
          <span class="relation-option-count">
            已选 {{ checkedTenantKeys.length }} 个
          </span>
        </div>

        <div class="relation-option-list">
          <n-checkbox-group v-model:value="checkedTenantKeys">
            <div class="relation-tenant-list">
              <label
                v-for="tenant in tenantOptions"
                :key="tenant.id"
                class="relation-option-card is-wide"
                :class="{ 'is-selected': checkedTenantKeys.includes(tenant.id) }"
              >
                <n-checkbox :value="tenant.id" />
                <span class="relation-option-main">
                  <strong>{{ tenant.tenantName }}</strong>
                  <small>{{ tenant.tenantCode || tenant.contactName || '租户工作区' }}</small>
                </span>
                <i v-if="checkedTenantKeys.includes(tenant.id)" class="i-material-symbols:check-circle-rounded relation-option-check" />
              </label>
            </div>
          </n-checkbox-group>
          <n-empty v-if="!tenantLoading && tenantOptions.length === 0" description="暂无租户数据" size="small" />
        </div>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store'

import { useUserManagementStore } from '@/stores/system/userManagementStore'

const userStore = useUserStore()
const store = useUserManagementStore()
const { relationActiveTab, tenantLoading, tenantOptions, checkedTenantKeys, defaultTenantId, selectedTenantOptions } = storeToRefs(store)
</script>
