<template>
  <!-- 高级关系维护弹窗（仅处理复杂授权/组织/岗位/租户关系） -->
  <n-modal
    v-model:show="relationModalVisible"
    title=""
    preset="card"
    class="user-relation-modal"
    :style="{ width: 'min(1120px, calc(100vw - 32px))' }"
    :content-style="{ padding: '0' }"
    :mask-closable="false"
    :closable="false"
  >
    <div class="relation-workbench">
      <aside class="relation-sidebar">
        <div class="relation-sidebar-header">
          <div class="relation-user-avatar">
            {{ (currentUser.username || currentUser.realName || '用').slice(0, 1).toUpperCase() }}
          </div>
          <div class="relation-sidebar-copy">
            <span>高级关系维护</span>
            <small>仅用于复杂场景：{{ currentUser.username || '-' }}</small>
          </div>
        </div>

        <div class="relation-nav-list">
          <button
            v-for="tab in relationTabItems"
            :key="tab.key"
            type="button"
            class="relation-nav-item"
            :class="{ 'is-active': relationActiveTab === tab.key }"
            @click="handleRelationTabChange(tab.key)"
          >
            <i v-if="tab.key === 'overview'" class="i-material-symbols:manage-accounts-outline-rounded" aria-hidden="true" />
            <i v-else-if="tab.key === 'org'" class="i-material-symbols:account-tree-rounded" aria-hidden="true" />
            <i v-else-if="tab.key === 'auth'" class="i-material-symbols:verified-user-outline-rounded" aria-hidden="true" />
            <i v-else-if="tab.key === 'post'" class="ai-icon:briefcase" aria-hidden="true" />
            <i v-else class="i-material-symbols:business-center" aria-hidden="true" />
            <div class="relation-nav-copy">
              <strong>{{ tab.label }}</strong>
              <small>{{ tab.desc }}</small>
            </div>
          </button>
        </div>
      </aside>

      <section class="relation-content">
        <button
          type="button"
          class="relation-close-button"
          title="关闭"
          @click="relationModalVisible = false"
        >
          <i class="i-material-symbols:close-rounded" />
        </button>

        <div class="relation-content-header">
          <div class="relation-content-title">
            <strong>{{ currentRelationTabMeta?.label || '高级关系维护' }}</strong>
            <span>{{ currentRelationTabMeta?.desc || '只处理复杂组织、角色、岗位和租户关系' }}</span>
          </div>
        </div>

        <div class="relation-content-body">
          <n-alert type="info" :bordered="false" class="batch-action-alert">
            普通新增/编辑已经可以一次性设置组织、角色和岗位。这里仅用于多组织、多租户、历史数据修正等高级维护场景。
          </n-alert>

          <UserRelationOverview />
          <UserRelationRoles />
          <UserRelationOrganizations />
          <UserRelationPosts />
          <UserRelationTenants />
        </div>

        <div class="relation-footer">
          <n-button @click="relationModalVisible = false">
            {{ relationActiveTab === 'overview' ? '关闭' : '取消' }}
          </n-button>
          <n-button
            v-if="relationActiveTab !== 'overview'"
            type="primary"
            :loading="relationSubmitLoading"
            @click="handleRelationSubmit"
          >
            保存更改
          </n-button>
        </div>
      </section>
    </div>
  </n-modal>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useUserManagementStore } from '@/stores/system/userManagementStore'

import UserRelationOrganizations from './UserRelationOrganizations.vue'
import UserRelationOverview from './UserRelationOverview.vue'
import UserRelationPosts from './UserRelationPosts.vue'
import UserRelationRoles from './UserRelationRoles.vue'
import UserRelationTenants from './UserRelationTenants.vue'

const store = useUserManagementStore()
const { relationModalVisible, relationActiveTab, relationSubmitLoading, currentUser, relationTabItems, currentRelationTabMeta } = storeToRefs(store)
const { handleRelationTabChange, handleRelationSubmit } = store
</script>
