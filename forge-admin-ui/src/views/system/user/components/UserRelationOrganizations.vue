<template>
  <!-- 组织绑定 -->
  <div v-show="relationActiveTab === 'org'" class="relation-section org-modal-content">
    <div class="relation-org-layout">
      <section class="relation-org-tree-panel">
        <div class="org-toolbar">
          <n-button size="small" @click="toggleUserOrgExpandAll">
            <template #icon>
              <i :class="orgTreeExpandAll ? 'i-material-symbols:unfold-less' : 'i-material-symbols:unfold-more'" />
            </template>
            {{ orgTreeExpandAll ? '折叠全部' : '展开全部' }}
          </n-button>
        </div>

        <div class="org-tree-container">
          <n-spin :show="orgLoading">
            <PremiumTree
              v-if="orgTreeData.length > 0"
              :data="orgTreeData"
              checkable
              :cascade="false"
              :selected-keys="mainOrgId ? [mainOrgId] : []"
              :checked-keys="checkedOrgKeys"
              :expanded-keys="orgTreeExpandedKeys"
              key-field="id"
              label-field="orgName"
              children-field="children"
              :get-node-icon="getLeftOrgNodeIcon"
              :get-node-meta="getUserOrgNodeMeta"
              :get-node-tone="getLeftOrgNodeTone"
              show-meta
              @update:expanded-keys="handleOrgExpandedKeysChange"
              @update:checked-keys="handleOrgCheckedKeysChange"
            />
            <n-empty v-else description="暂无组织数据" />
          </n-spin>
        </div>
      </section>

      <aside class="relation-org-side">
        <div class="relation-primary-setting is-stack">
          <div class="relation-primary-copy">
            <strong>设为主组织</strong>
            <span>决定默认数据汇报与组织归属。</span>
          </div>
          <n-select
            v-model:value="mainOrgId"
            :options="selectedOrgOptions"
            placeholder="请选择主组织"
            filterable
            :disabled="checkedOrgKeys.length === 0"
          />
        </div>

        <div class="relation-org-stat">
          <span>已选组织数</span>
          <strong>{{ checkedOrgKeys.length }}</strong>
        </div>

        <div class="relation-org-current">
          <span>当前主组织</span>
          <strong>{{ mainOrgName || '未设置' }}</strong>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import PremiumTree from '@/components/common/PremiumTree.vue'
import { getLeftOrgNodeIcon, getLeftOrgNodeTone } from '@/stores/system/user-management/utils'

import { useUserManagementStore } from '@/stores/system/userManagementStore'

const store = useUserManagementStore()
const { relationActiveTab, orgLoading, orgTreeData, mainOrgId, checkedOrgKeys, orgTreeExpandAll, orgTreeExpandedKeys, mainOrgName, selectedOrgOptions } = storeToRefs(store)
const { getUserOrgNodeMeta, toggleUserOrgExpandAll, handleOrgExpandedKeysChange, handleOrgCheckedKeysChange } = store
</script>
