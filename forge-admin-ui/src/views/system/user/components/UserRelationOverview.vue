<template>
  <!-- 授权概览 -->
  <div v-show="relationActiveTab === 'overview'" class="relation-section relation-overview-section">
    <div class="relation-overview-user">
      <div class="relation-overview-avatar">
        {{ (currentUser.realName || currentUser.username || '用').slice(0, 1).toUpperCase() }}
      </div>
      <div class="relation-overview-copy">
        <strong>{{ currentUser.realName || currentUser.username || '-' }}</strong>
        <span>@{{ currentUser.username || '-' }} · {{ resolveOptionLabel(userTypeOptions, normalizeSingleNumber(currentUser.userType)) || '用户' }}</span>
      </div>
    </div>

    <div class="relation-summary-grid">
      <section class="relation-summary-card">
        <div class="relation-summary-heading">
          <i class="i-material-symbols:account-tree-rounded" />
          <strong>组织关系</strong>
          <button type="button" @click="handleRelationTabChange('org')">
            维护
          </button>
        </div>
        <div class="relation-summary-body">
          <template v-if="relationOrgSummaryItems.length > 0">
            <div
              v-for="item in relationOrgSummaryItems"
              :key="item.key"
              class="relation-summary-line"
            >
              <span>{{ item.label }}</span>
              <small v-if="item.meta">{{ item.meta }}</small>
            </div>
          </template>
          <span v-else class="relation-summary-empty">未绑定组织</span>
        </div>
      </section>

      <section class="relation-summary-card">
        <div class="relation-summary-heading">
          <i class="i-material-symbols:verified-user-outline-rounded" />
          <strong>角色授权</strong>
          <button type="button" @click="handleRelationTabChange('auth')">
            维护
          </button>
        </div>
        <div class="relation-summary-body">
          <template v-if="relationRoleSummaryItems.length > 0">
            <div
              v-for="item in relationRoleSummaryItems"
              :key="item.key"
              class="relation-summary-line"
            >
              <span>{{ item.label }}</span>
              <small>{{ item.meta }}</small>
            </div>
          </template>
          <span v-else class="relation-summary-empty">未授权角色</span>
        </div>
      </section>

      <section class="relation-summary-card">
        <div class="relation-summary-heading">
          <i class="ai-icon:briefcase" />
          <strong>岗位关系</strong>
          <button type="button" @click="handleRelationTabChange('post')">
            维护
          </button>
        </div>
        <div class="relation-summary-body">
          <template v-if="relationPostSummaryItems.length > 0">
            <div
              v-for="item in relationPostSummaryItems"
              :key="item.key"
              class="relation-summary-line"
            >
              <span>{{ item.label }}</span>
              <small v-if="item.meta">{{ item.meta }}</small>
            </div>
          </template>
          <span v-else class="relation-summary-empty">未设置岗位</span>
        </div>
      </section>

      <section v-if="userStore.isAdmin" class="relation-summary-card">
        <div class="relation-summary-heading">
          <i class="i-material-symbols:business-center" />
          <strong>租户关系</strong>
          <button type="button" @click="handleRelationTabChange('tenant')">
            维护
          </button>
        </div>
        <div class="relation-summary-body">
          <template v-if="relationTenantSummaryItems.length > 0">
            <div
              v-for="item in relationTenantSummaryItems"
              :key="item.key"
              class="relation-summary-line"
            >
              <span>{{ item.label }}</span>
              <small v-if="item.meta">{{ item.meta }}</small>
            </div>
          </template>
          <span v-else class="relation-summary-empty">未加入租户</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store'

import { normalizeSingleNumber, resolveOptionLabel } from '@/stores/system/user-management/utils'
import { useUserManagementStore } from '@/stores/system/userManagementStore'

const userStore = useUserStore()
const store = useUserManagementStore()
const { relationActiveTab, currentUser, userTypeOptions, relationOrgSummaryItems, relationRoleSummaryItems, relationPostSummaryItems, relationTenantSummaryItems } = storeToRefs(store)
const { handleRelationTabChange } = store
</script>
