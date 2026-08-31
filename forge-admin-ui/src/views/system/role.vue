<template>
  <div class="system-role-page">
    <MasterDetailWorkspace
      class="role-workspace"
      :aside-width="260"
      main-width="minmax(0, 1fr)"
    >
      <template #aside>
        <div class="role-list-panel">
          <div class="role-selector-header">
            <div class="role-selector-title">
              <span class="role-selector-icon">
                <i class="i-material-symbols:shield-outline-rounded" />
              </span>
              <span class="role-selector-copy">
                <strong>角色管理</strong>
                <small>{{ roleList.length }} 个角色</small>
              </span>
            </div>
            <n-button size="small" type="primary" circle title="新增角色" @click="handleAddRole">
              <template #icon>
                <i class="i-material-symbols:add-rounded" />
              </template>
            </n-button>
          </div>

          <div class="role-selector-tools">
            <n-input
              v-model:value="roleKeyword"
              class="role-search"
              clearable
              size="small"
              placeholder="搜索角色名称..."
              @clear="handleRoleSearch"
              @keyup.enter="handleRoleSearch"
            >
              <template #prefix>
                <i class="i-material-symbols:search-rounded" />
              </template>
            </n-input>
            <div class="role-tabs">
              <button
                v-for="tab in roleTypeTabs"
                :key="tab.value"
                type="button"
                :class="{ 'is-active': String(activeRoleType) === String(tab.value) }"
                @click="handleRoleTypeChange(tab.value)"
              >
                {{ tab.label }}
              </button>
            </div>
          </div>

          <n-spin :show="roleListLoading" class="role-list-spin">
            <div class="role-list">
              <div
                v-for="role in roleList"
                :key="role.id"
                class="role-list-item"
                :class="{ 'is-selected': currentRole.id === role.id }"
                role="button"
                tabindex="0"
                @click="handleSelectRole(role)"
                @keydown.enter.prevent="handleSelectRole(role)"
                @keydown.space.prevent="handleSelectRole(role)"
              >
                <span class="role-list-main">
                  <span class="role-list-title">
                    <strong :title="role.roleName">{{ role.roleName }}</strong>
                    <span v-if="isRoleDisabled(role)" class="role-disabled-badge">
                      {{ resolveRoleStatusLabel(role) }}
                    </span>
                  </span>
                  <span class="role-card-meta">
                    <span>{{ resolveRoleDataScopeLabel(role) }}</span>
                  </span>
                </span>
                <span class="role-list-side" @click.stop>
                  <n-dropdown
                    trigger="click"
                    placement="bottom-end"
                    :menu-props="getRoleDropdownMenuProps"
                    :options="getRoleActionOptions(role)"
                    @select="key => handleRoleCardAction(key, role)"
                  >
                    <button type="button" class="role-card-menu" title="角色操作" aria-label="角色操作" @click.stop>
                      <i class="i-material-symbols:more-vert" />
                    </button>
                  </n-dropdown>
                </span>
              </div>
              <n-empty v-if="!roleListLoading && roleList.length === 0" description="暂无角色" size="small" />
            </div>
          </n-spin>
        </div>
      </template>

      <section class="role-user-panel">
        <header class="role-user-header">
          <div class="role-user-title">
            <div class="role-user-heading">
              <h2>{{ currentRole.roleName || '请选择角色' }}</h2>
              <small v-if="currentRole.roleKey">{{ currentRole.roleKey }}</small>
            </div>
            <div v-if="currentRole.id" class="role-user-badges">
              <NTag size="small" type="info" :bordered="false">
                {{ currentRoleDataScopeLabel }}
              </NTag>
              <NTag size="small" :type="currentRoleScopeTagType" :bordered="false">
                {{ currentRoleScopeLabel }}
              </NTag>
              <NTag size="small" :bordered="false">
                共 {{ roleUserTotal }} 名成员
              </NTag>
            </div>
          </div>
          <n-space size="small">
            <n-button
              size="small"
              type="primary"
              :disabled="!canAddUserToCurrentRole"
              @click="handleAddUser"
            >
              <template #icon>
                <i class="i-material-symbols:person-add-rounded" />
              </template>
              {{ addUserButtonText }}
            </n-button>
            <n-button
              size="small"
              quaternary
              circle
              title="刷新"
              :disabled="!currentRole.id"
              @click="refreshRoleUsers"
            >
              <template #icon>
                <i class="i-material-symbols:refresh-rounded" />
              </template>
            </n-button>
          </n-space>
        </header>

        <div class="role-user-search">
          <n-input
            v-model:value="roleUserKeyword"
            clearable
            size="small"
            placeholder="搜索账号"
            @clear="handleUserSearch"
            @keyup.enter="handleUserSearch"
          >
            <template #prefix>
              <i class="i-material-symbols:search-rounded" />
            </template>
          </n-input>
          <n-tree-select
            v-model:value="roleUserOrgId"
            placeholder="选择授权组织"
            clearable
            filterable
            size="small"
            :disabled="!currentRole.id || roleUserOrgOptions.length === 0"
            :options="roleUserOrgTreeOptions"
            key-field="value"
            label-field="label"
            children-field="children"
            @update:value="handleRoleUserOrgChange"
          />
          <n-select
            v-model:value="userSearchParams.userStatus"
            placeholder="状态"
            clearable
            size="small"
            :options="userStatusOptions"
            @update:value="handleUserSearch"
          />
          <div class="role-user-search-actions">
            <n-button class="role-user-search-action" size="small" type="primary" :disabled="!currentRole.id" @click="handleUserSearch">
              查询
            </n-button>
            <n-button class="role-user-search-action" size="small" :disabled="!currentRole.id" @click="handleUserSearchReset">
              重置
            </n-button>
          </div>
        </div>

        <div class="member-body">
          <AiCrudPage
            v-if="currentRole.id"
            ref="roleUserCrudRef"
            api="/system/role"
            :api-config="roleUserApiConfig"
            :search-schema="[]"
            :columns="roleUserTableColumns"
            :before-load-list="beforeLoadRoleUserList"
            row-key="id"
            :hide-add="true"
            :hide-batch-delete="true"
            :hide-selection="true"
            :show-search="false"
            :show-render-mode-switch="false"
            :page-size="10"
            :scroll-x="760"
            :table-props="{
              dragScroll: false,
              showToolbar: false,
              showRefresh: false,
              showDensity: false,
              showColumnFilter: false,
            }"
            @load-list-success="handleRoleUserLoadSuccess"
          />
        </div>
      </section>
    </MasterDetailWorkspace>

    <div class="crud-driver" aria-hidden="true">
      <AiCrudPage
        ref="crudRef"
        api="/system/role"
        :api-config="{
          list: 'get@/system/role/page',
          detail: 'post@/system/role/getById',
          add: 'post@/system/role/add',
          update: 'post@/system/role/edit',
          delete: 'post@/system/role/removeBatch',
        }"
        :search-schema="searchSchema"
        :columns="tableColumns"
        :edit-schema="editSchema"
        :before-submit="beforeSubmit"
        row-key="id"
        :edit-grid-cols="2"
        edit-label-align="left"
        modal-width="800px"
        add-button-text="新增角色"
        :show-search="false"
        :show-pagination="false"
        :hide-toolbar="true"
        :hide-selection="true"
        :hide-batch-delete="true"
        @submit-success="handleRoleMutationSuccess"
      />
    </div>

    <!-- 角色权限配置弹窗 -->
    <n-modal
      v-model:show="authModalVisible"
      preset="card"
      class="role-permission-modal"
      style="width: 100vw; max-width: 100vw"
      :mask-closable="false"
    >
      <div class="auth-modal-content">
        <header class="auth-workspace-header">
          <div class="auth-header-main">
            <div class="auth-breadcrumb">
              <span>角色综合授权</span>
              <span class="auth-breadcrumb-divider">/</span>
              <span class="auth-role-badge">
                <i class="i-material-symbols:business-center" />
                {{ currentRole.roleName || '-' }}
              </span>
            </div>
            <div class="auth-role-key">
              {{ currentRole.roleKey || '-' }}
            </div>
          </div>

          <div class="auth-header-actions">
            <span class="auth-client-badge">{{ currentAuthClientName }}</span>
            <n-button @click="authModalVisible = false">
              取消
            </n-button>
            <n-button
              type="primary"
              :loading="authSubmitLoading"
              :disabled="authLoading || dataScopeLoading || authLoadFailed || dataScopeLoadFailed"
              @click="handleSubmitAuth"
            >
              保存配置
            </n-button>
          </div>
        </header>

        <div v-if="authClientTabs.length > 1" class="auth-client-tabs">
          <n-tabs
            type="segment"
            size="small"
            :value="currentAuthClientCode"
            @update:value="handleAuthClientChange"
          >
            <n-tab-pane
              v-for="client in authClientTabs"
              :key="client.clientCode"
              :name="client.clientCode"
              :tab="client.clientName"
            />
          </n-tabs>
        </div>

        <n-alert v-if="authLoadFailed || dataScopeLoadFailed" type="error" :show-icon="false" class="auth-load-alert">
          权限配置加载不完整，请关闭弹窗后重试
        </n-alert>

        <RolePermissionSettings
          v-model:checked-keys="checkedResourceKeys"
          v-model:data-scope-settings="dataScopeSettings"
          :resource-tree="resourceTreeData"
          :loading="authLoading"
          :data-scope-loading="dataScopeLoading"
          :data-scope-options="manageableDataScopeOptions"
        />

        <div class="auth-floating-actions">
          <n-button @click="authModalVisible = false">
            取消
          </n-button>
          <n-button
            type="primary"
            :loading="authSubmitLoading"
            :disabled="authLoading || dataScopeLoading || authLoadFailed || dataScopeLoadFailed"
            @click="handleSubmitAuth"
          >
            保存配置
          </n-button>
        </div>
      </div>
    </n-modal>

    <!-- 添加用户弹窗 -->
    <UserSelectPanel
      :show="addUserModalVisible"
      :title="`添加用户到角色 - ${currentRole.roleName || ''}`"
      :confirm-loading="addUserLoading"
      :assigned-user-ids="assignedUserIds"
      :tenant-id="currentRole.tenantId"
      :initial-org-id="roleUserOrgId"
      :locked-org-id="roleUserOrgId"
      :direct-org-only="true"
      @update:show="val => addUserModalVisible = val"
      @confirm="handleConfirmAddUsers"
    />

    <!-- 角色适用组织弹窗 -->
    <n-modal
      v-model:show="roleOrgModalVisible"
      :title="`适用组织 - ${currentRole.roleName || ''}`"
      preset="card"
      style="width: 720px"
      :mask-closable="false"
    >
      <div class="role-org-modal-content">
        <div class="role-org-toolbar">
          <div class="role-scope-mode">
            <n-radio-group
              :value="roleScopeMode"
              size="small"
              @update:value="handleRoleScopeModeChange"
            >
              <n-radio-button value="global">
                租户全局
              </n-radio-button>
              <n-radio-button value="custom">
                指定组织
              </n-radio-button>
            </n-radio-group>
            <NTag size="small" :type="roleOrgScopeTagType" :bordered="false">
              {{ roleOrgScopeSummary }}
            </NTag>
          </div>
          <n-space size="small" align="center">
            <n-button size="small" :disabled="roleOrgLoading" @click="toggleRoleOrgExpandAll">
              <template #icon>
                <i :class="roleOrgTreeExpandAll ? 'i-material-symbols:unfold-less' : 'i-material-symbols:unfold-more'" />
              </template>
              {{ roleOrgTreeExpandAll ? '折叠全部' : '展开全部' }}
            </n-button>
          </n-space>
        </div>
        <div class="auth-tree-container">
          <n-spin :show="roleOrgLoading">
            <PremiumTree
              v-if="roleOrgTreeData.length > 0"
              :data="roleOrgTreeData"
              :checkable="roleScopeMode === 'custom'"
              :cascade="false"
              :expanded-keys="roleOrgExpandedKeys"
              :checked-keys="roleScopeMode === 'global' ? allRoleOrgIds : checkedRoleOrgKeys"
              key-field="id"
              label-field="orgName"
              children-field="children"
              :get-node-icon="getOrgNodeIcon"
              :get-node-tone="getOrgNodeTone"
              @update:expanded-keys="handleRoleOrgExpandedKeysChange"
              @update:checked-keys="handleRoleOrgCheckedKeysChange"
            />
            <n-empty v-else description="暂无组织数据" />
          </n-spin>
        </div>
      </div>

      <template #footer>
        <n-space justify="end">
          <n-button @click="roleOrgModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="roleOrgSubmitLoading" @click="handleSubmitRoleOrgs">
            确定
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, nextTick, onMounted, ref, watch } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import MasterDetailWorkspace from '@/components/common/MasterDetailWorkspace.vue'
import PremiumTree from '@/components/common/PremiumTree.vue'
import SystemTableCell from '@/components/common/SystemTableCell.vue'
import DictTag from '@/components/DictTag.vue'
import UserSelectPanel from '@/components/UserSelectPanel.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store'
import { request } from '@/utils'
import RolePermissionSettings from './components/RolePermissionSettings.vue'

defineOptions({ name: 'SystemRole' })

const USER_STATUS_DICT = 'sys_user_status'
const ROLE_DATA_SCOPE_DICT = 'sys_role_data_scope'
const ROLE_TYPE_DICT = 'sys_role_type'
const NORMAL_DISABLE_DICT = 'sys_normal_disable'
const YES_NO_DICT = 'sys_yes_no'

const crudRef = ref(null)
const roleUserCrudRef = ref(null)
const userStore = useUserStore()
const roleList = ref([])
const roleListLoading = ref(false)
const roleKeyword = ref('')
const activeRoleType = ref(null)
const ROLE_ORG_SCOPE_GLOBAL = 1
const ROLE_ORG_SCOPE_CUSTOM = 2

// 授权相关
const authModalVisible = ref(false)
const authLoading = ref(false)
const authLoadFailed = ref(false)
const authSubmitLoading = ref(false)
const resourceTreeData = ref([])
const checkedResourceKeys = ref([])
const dataScopeLoading = ref(false)
const dataScopeLoadFailed = ref(false)
const dataScopeSettings = ref({ defaultDataScope: 5, modules: [] })
const clientList = ref([])
const currentAuthClientCode = ref('pc')

// 用户列表相关
const currentRole = ref({})
const addUserModalVisible = ref(false)
const addUserLoading = ref(false)
const assignedUserIds = ref([]) // 当前角色已授权的用户ID列表
const roleUserOrgId = ref(null)
const roleUserKeyword = ref('')
const roleApplicableOrgIds = ref([])
const roleOrgTreeData = ref([])
const roleUserTotal = ref(0)
const roleUserCountMap = ref({})
const userSearchParams = ref({
  userStatus: null,
})

// 角色适用组织
const roleOrgModalVisible = ref(false)
const roleOrgLoading = ref(false)
const roleOrgSubmitLoading = ref(false)
const roleScopeMode = ref('global')
const checkedRoleOrgKeys = ref([])
const roleOrgExpandedKeys = ref([])
const roleOrgTreeExpandAll = ref(true)

const { dict } = useDict(USER_STATUS_DICT, ROLE_DATA_SCOPE_DICT, ROLE_TYPE_DICT, NORMAL_DISABLE_DICT, YES_NO_DICT)

const userStatusOptions = computed(() => toNumberOptions(dict.value[USER_STATUS_DICT]))
const dataScopeOptions = computed(() => toNumberOptions(dict.value[ROLE_DATA_SCOPE_DICT]))
const manageableDataScopeOptions = computed(() => {
  if (userStore.isAdmin)
    return dataScopeOptions.value
  const deniedScopes = Number(userStore.userType) === 2 ? [1, 2] : [1]
  return dataScopeOptions.value.filter(item => !deniedScopes.includes(Number(item.value)))
})
const roleTypeOptions = computed(() => toNumberOptions(dict.value[ROLE_TYPE_DICT]))
const roleStatusOptions = computed(() => toNumberOptions(dict.value[NORMAL_DISABLE_DICT]))
const yesNoOptions = computed(() => toNumberOptions(dict.value[YES_NO_DICT]))
const roleTypeTabs = computed(() => {
  const options = roleTypeOptions.value || []
  if (options.length > 0)
    return options.map(item => ({ label: resolveRoleTypeShortLabel(item.label), value: item.value }))
  return [{ label: '角色', value: null }]
})
const isCurrentRoleGlobalScope = computed(() =>
  Number(currentRole.value?.orgScopeType ?? ROLE_ORG_SCOPE_GLOBAL) === ROLE_ORG_SCOPE_GLOBAL,
)
const roleUserOrgOptions = computed(() => {
  const scopedOrgIds = new Set(normalizeNumberList(roleApplicableOrgIds.value))
  return flattenOrgNodes(roleOrgTreeData.value)
    .filter(item => isCurrentRoleGlobalScope.value || scopedOrgIds.has(normalizeSingleNumber(item.id)))
    .map(item => ({
      label: item.orgName,
      value: normalizeSingleNumber(item.id),
    }))
    .filter(item => item.value !== null)
})
const roleUserOrgTreeOptions = computed(() => {
  const scopedOrgIds = new Set(normalizeNumberList(roleApplicableOrgIds.value))
  return buildRoleUserOrgTreeOptions(roleOrgTreeData.value, scopedOrgIds, isCurrentRoleGlobalScope.value)
})
const allRoleOrgIds = computed(() => flattenOrgNodes(roleOrgTreeData.value)
  .map(item => normalizeSingleNumber(item.id))
  .filter(item => item !== null))
const currentRoleScopeLabel = computed(() => {
  if (!currentRole.value?.id)
    return ''
  if (isCurrentRoleGlobalScope.value)
    return '租户全局'
  if (roleApplicableOrgIds.value.length > 0)
    return `${roleApplicableOrgIds.value.length} 个组织`
  return '未设置范围'
})
const currentRoleScopeTagType = computed(() => {
  if (isCurrentRoleGlobalScope.value)
    return 'success'
  return roleApplicableOrgIds.value.length > 0 ? 'info' : 'warning'
})
const currentRoleDataScopeLabel = computed(() => {
  if (!currentRole.value?.id)
    return ''
  return resolveRoleDataScopeLabel(currentRole.value)
})
const canAddUserToCurrentRole = computed(() => {
  if (!currentRole.value?.id || roleUserOrgOptions.value.length === 0)
    return false
  return !!roleUserOrgId.value
})
const addUserButtonText = computed(() => {
  if (!currentRole.value?.id)
    return '添加用户'
  if (roleUserOrgOptions.value.length === 0)
    return '无授权组织'
  return roleUserOrgId.value ? '添加用户' : '先选组织'
})
const roleUserApiConfig = computed(() => ({
  list: currentRole.value?.id
    ? `get@/system/role/${currentRole.value.id}/users`
    : '',
  detail: 'post@/system/user/getById',
}))
const roleUserTableColumns = computed(() => [
  {
    prop: 'username',
    label: '成员信息',
    minWidth: 190,
    render: row => h(SystemTableCell, {
      title: resolveUserDisplayName(row),
      subtitle: resolveUserAccountLabel(row),
      interactive: true,
      avatar: true,
      tooltip: `查看用户详情：${row.username || '-'}`,
      onActivate: () => roleUserCrudRef.value?.showDetail?.(row),
    }),
  },
  {
    prop: 'orgName',
    label: '所属组织',
    width: 180,
    render: row => h('span', { class: 'role-member-plain', title: resolveUserOrgLabel(row) }, resolveUserOrgLabel(row)),
  },
  {
    prop: 'phone',
    label: '联系方式',
    width: 180,
    render: row => h('span', { class: 'role-member-plain role-member-phone', title: row.phone || '' }, row.phone || '未填写手机号'),
  },
  {
    prop: 'userStatus',
    label: '状态',
    width: 110,
    render: row => h('span', {
      class: ['role-member-status', { 'is-disabled': !isUserEnabled(row) }],
    }, [
      h('span', { class: 'role-member-status-dot' }),
      h('span', { class: 'role-member-status-text' }, resolveUserStatusLabel(row)),
    ]),
  },
  {
    prop: 'actions',
    label: '操作',
    width: 90,
    fixed: 'right',
    actions: [
      {
        label: '移除',
        key: 'remove',
        type: 'primary',
        onClick: row => handleRemoveUserRole(row),
      },
    ],
  },
])
function getRoleActionOptions(role = {}) {
  const options = [
    {
      label: '编辑角色',
      key: 'edit',
      icon: () => h('i', { class: 'i-material-symbols:edit-outline-rounded' }),
    },
    {
      label: '适用组织',
      key: 'org-scope',
      icon: () => h('i', { class: 'i-material-symbols:account-tree-rounded' }),
    },
    {
      label: '权限授权',
      key: 'auth',
      icon: () => h('i', { class: 'i-material-symbols:admin-panel-settings-outline-rounded' }),
    },
  ]
  if (role?.id && Number(role.id) !== 1) {
    options.push(
      { type: 'divider', key: 'delete-divider' },
      {
        label: () => h('span', { class: 'role-action-danger' }, '删除角色'),
        key: 'delete',
        icon: () => h('i', { class: 'i-material-symbols:delete-outline-rounded role-action-danger' }),
      },
    )
  }
  return options
}

function getRoleDropdownMenuProps() {
  return {
    class: 'role-action-dropdown-menu',
  }
}

async function handleRoleCardAction(key, role) {
  if (!role?.id)
    return
  if (key === 'edit') {
    handleEdit(role)
    return
  }
  if (key === 'org-scope') {
    await handleRoleOrgScope(role)
    return
  }
  if (key === 'auth') {
    await handleAuth(role)
    return
  }
  if (key === 'delete') {
    handleDelete(role)
  }
}

const roleOrgScopeSummary = computed(() => {
  if (allRoleOrgIds.value.length === 0)
    return '暂无组织'
  if (roleScopeMode.value === 'global')
    return `${allRoleOrgIds.value.length} 个组织`
  if (checkedRoleOrgKeys.value.length === 0)
    return '未设置'
  return `${checkedRoleOrgKeys.value.length} 个组织`
})
const roleOrgScopeTagType = computed(() => {
  if (allRoleOrgIds.value.length === 0 || (roleScopeMode.value === 'custom' && checkedRoleOrgKeys.value.length === 0))
    return 'warning'
  return roleScopeMode.value === 'global' ? 'success' : 'info'
})

const authClientTabs = computed(() => {
  if (clientList.value.length > 0)
    return clientList.value
  return [{ clientCode: 'pc', clientName: 'PC端' }]
})
const currentAuthClientName = computed(() => {
  const client = authClientTabs.value.find(item => item.clientCode === currentAuthClientCode.value)
  return client?.clientName || currentAuthClientCode.value || '-'
})

watch(roleUserOrgOptions, (options) => {
  if (roleUserOrgId.value && options.some(item => item.value === roleUserOrgId.value))
    return
  if (options.length === 1) {
    roleUserOrgId.value = options[0].value
    return
  }
  roleUserOrgId.value = null
})

watch(roleTypeTabs, (tabs) => {
  if (activeRoleType.value !== null || tabs.length === 0)
    return
  activeRoleType.value = tabs[0].value
  loadRoleList()
}, { immediate: true })

// 搜索表单配置
const searchSchema = computed(() => [
  {
    field: 'roleName',
    label: '角色名称',
    type: 'input',
    props: {
      placeholder: '请输入角色名称',
    },
  },
  {
    field: 'roleKey',
    label: '权限字符',
    type: 'input',
    props: {
      placeholder: '请输入权限字符',
    },
  },
  {
    field: 'roleType',
    label: '角色类型',
    type: 'select',
    props: {
      placeholder: '请选择角色类型',
      options: roleTypeOptions.value,
    },
  },
  {
    field: 'roleStatus',
    label: '状态',
    type: 'select',
    props: {
      placeholder: '请选择状态',
      options: roleStatusOptions.value,
    },
  },
])

// 表格列配置
const tableColumns = computed(() => [
  {
    prop: 'roleName',
    label: '角色名称',
    width: 150,
  },
  {
    prop: 'roleKey',
    label: '权限字符',
    width: 150,
  },
  {
    prop: 'roleType',
    label: '角色类型',
    width: 120,
    render: (row) => {
      return h(DictTag, { dictType: ROLE_TYPE_DICT, value: row.roleType, size: 'small', forceTag: true })
    },
  },
  {
    prop: 'dataScope',
    label: '数据范围',
    width: 150,
    render: (row) => {
      return h(DictTag, { dictType: ROLE_DATA_SCOPE_DICT, value: row.dataScope, size: 'small', forceTag: true })
    },
  },
  {
    prop: 'sort',
    label: '排序',
    width: 80,
  },
  {
    prop: 'roleStatus',
    label: '状态',
    width: 80,
    render: (row) => {
      return h(DictTag, { dictType: NORMAL_DISABLE_DICT, value: row.roleStatus, size: 'small', forceTag: true })
    },
  },
  {
    prop: 'isSystem',
    label: '系统角色',
    width: 100,
    render: (row) => {
      return h(DictTag, { dictType: YES_NO_DICT, value: row.isSystem, size: 'small', forceTag: true })
    },
  },
  {
    prop: 'remark',
    label: '备注',
    minWidth: 150,
  },
  {
    prop: 'action',
    label: '操作',
    width: 260,
    fixed: 'right',
    actions: [
      { label: '编辑', key: 'edit', onClick: handleEdit },
      { label: '适用组织', key: 'orgScope', type: 'info', onClick: handleRoleOrgScope },
      { label: '查看用户', key: 'viewUsers', onClick: handleViewUsers },
      { label: '添加用户', key: 'addUsers', type: 'success', onClick: handleAddUserFromList },
      { label: '授权', key: 'auth', onClick: handleAuth },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete, visible: row => row.id !== 1 },
    ],
  },
])

// 编辑表单配置
const editSchema = computed(() => [
  {
    field: 'roleName',
    label: '角色名称',
    type: 'input',
    rules: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
    props: {
      placeholder: '请输入角色名称',
    },
  },
  {
    field: 'roleKey',
    label: '权限字符',
    type: 'input',
    rules: [{ required: true, message: '请输入权限字符', trigger: 'blur' }],
    props: {
      placeholder: '请输入权限字符，如：admin',
    },
  },
  {
    field: 'roleType',
    label: '角色类型',
    type: 'select',
    defaultValue: 1,
    rules: [{ required: true, type: 'number', message: '请选择角色类型', trigger: 'change' }],
    props: {
      placeholder: '请选择角色类型',
      options: roleTypeOptions.value,
    },
  },
  {
    field: 'dataScope',
    label: '数据范围',
    type: 'select',
    defaultValue: 2,
    rules: [{ required: true, type: 'number', message: '请选择数据范围', trigger: 'change' }],
    props: {
      placeholder: '请选择数据范围',
      options: manageableDataScopeOptions.value,
    },
  },
  {
    field: 'sort',
    label: '排序',
    type: 'number',
    defaultValue: 0,
    props: {
      placeholder: '排序值',
      min: 0,
    },
  },
  {
    type: 'divider',
    label: '状态配置',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
  },
  {
    field: 'roleStatus',
    label: '角色状态',
    type: 'radio',
    defaultValue: 1,
    props: {
      options: roleStatusOptions.value,
    },
  },
  {
    field: 'isSystem',
    label: '系统角色',
    type: 'radio',
    defaultValue: 0,
    props: {
      options: yesNoOptions.value,
    },
  },
  {
    field: 'remark',
    label: '备注',
    type: 'textarea',
    span: 2,
    props: {
      placeholder: '请输入备注',
      rows: 3,
    },
  },
])

function toNumberOptions(options = []) {
  return options.map(item => ({
    ...item,
    value: Number(item.value),
  }))
}

function normalizeSingleNumber(value, fallback = null) {
  if (Array.isArray(value)) {
    const first = value.find(item => item !== null && item !== undefined && item !== '')
    return normalizeSingleNumber(first, fallback)
  }
  if (value === null || value === undefined || value === '')
    return fallback
  const numberValue = Number(value)
  return Number.isNaN(numberValue) ? fallback : numberValue
}

function normalizeNumberList(value) {
  const list = Array.isArray(value) ? value : (value === null || value === undefined || value === '' ? [] : [value])
  return Array.from(new Set(list
    .map(item => normalizeSingleNumber(item))
    .filter(item => item !== null)))
}

function resolveOptionLabel(options = [], value, fallback = '') {
  const matched = options.find(option => String(option?.value) === String(value))
  return matched?.label || fallback
}

function resolveRoleTypeShortLabel(label = '') {
  const text = String(label || '').trim()
  return text.replace(/角色/g, '') || text || '角色'
}

function resolveRoleDictValue(row = {}, field) {
  const aliasMap = {
    roleType: ['roleType', 'role_type', 'type'],
    dataScope: ['dataScope', 'data_scope'],
    roleStatus: ['roleStatus', 'role_status', 'status'],
  }
  const keys = aliasMap[field] || [field]
  const value = keys.map(key => row[key]).find(item => item !== null && item !== undefined && item !== '')
  return normalizeSingleNumber(value, value ?? '')
}

function resolveRoleDataScopeLabel(row = {}) {
  const value = resolveRoleDictValue(row, 'dataScope')
  return resolveOptionLabel(dataScopeOptions.value, value, row.dataScope || '-')
}

function resolveRoleStatusLabel(row = {}) {
  const value = resolveRoleDictValue(row, 'roleStatus')
  return Number(value) === 1 ? '正常' : '停用'
}

function isRoleDisabled(row = {}) {
  return Number(resolveRoleDictValue(row, 'roleStatus')) !== 1
}

function resolveRoleMemberCount(row = {}) {
  const count = row.userCount ?? roleUserCountMap.value[row.id] ?? row.memberCount ?? row.users
  if (count === null || count === undefined || count === '')
    return null
  const numericCount = Number(count)
  return Number.isNaN(numericCount) ? null : numericCount
}

function resolveUserDisplayName(row = {}) {
  return row.realName || row.name || row.nickname || row.username || `用户${row.id}`
}

function resolveUserAccountLabel(row = {}) {
  const username = String(row.username || '').trim()
  return username && username !== resolveUserDisplayName(row) ? `@${username}` : ''
}

function resolveUserOrgLabel(row = {}) {
  return row.orgName || row.org || row.deptName || row.departmentName || '-'
}

function resolveUserStatusLabel(row = {}) {
  return Number(row.userStatus) === 1 ? '正常' : '停用'
}

function isUserEnabled(row = {}) {
  return Number(row.userStatus) === 1
}

function flattenOrgNodes(list = []) {
  return (list || []).flatMap((item) => {
    const current = [item]
    const children = flattenOrgNodes(item.children || [])
    return [...current, ...children]
  })
}

function buildRoleUserOrgTreeOptions(list = [], scopedOrgIds = new Set(), globalScope = false) {
  return (list || [])
    .map((item) => {
      const value = normalizeSingleNumber(item.id)
      const children = buildRoleUserOrgTreeOptions(item.children || [], scopedOrgIds, globalScope)
      const selectable = value !== null && (globalScope || scopedOrgIds.has(value))
      if (!selectable && children.length === 0)
        return null
      return {
        label: item.orgName || item.label || '-',
        value,
        disabled: !selectable,
        children,
      }
    })
    .filter(Boolean)
}

function getOrgNodeIcon(node = {}) {
  return node.children?.length
    ? 'i-material-symbols:account-tree-rounded'
    : 'i-material-symbols:domain-rounded'
}

function getOrgNodeTone(node = {}) {
  if (!node.parentId || Number(node.parentId) === 0)
    return 'folder'
  return node.children?.length ? 'folder' : 'menu'
}

function buildRoleTenantParams() {
  const resolvedTenantId = userStore.userInfo?.tenantId
  return resolvedTenantId ? { tenantId: resolvedTenantId } : {}
}

async function loadRoleOrgTree(tenantId = currentRole.value?.tenantId) {
  const res = await request.get('/system/org/tree', {
    params: buildRoleTenantParams(tenantId),
  })
  if (res.code === 200) {
    roleOrgTreeData.value = res.data || []
    if (roleOrgTreeExpandAll.value)
      roleOrgExpandedKeys.value = getAllKeys(roleOrgTreeData.value)
  }
}

async function loadRoleApplicableOrgIds(roleId = currentRole.value?.id) {
  if (!roleId) {
    roleApplicableOrgIds.value = []
    return []
  }
  const res = await request.get(`/system/role/${roleId}/orgs`)
  if (res.code === 200) {
    roleApplicableOrgIds.value = normalizeNumberList(res.data || [])
    return roleApplicableOrgIds.value
  }
  roleApplicableOrgIds.value = []
  return []
}

// 表单提交前处理
function beforeSubmit(formData) {
  if (!formData.id && formData.orgScopeType == null)
    formData.orgScopeType = ROLE_ORG_SCOPE_GLOBAL
  formData.tenantId = userStore.userInfo?.tenantId
  if (!userStore.isAdmin) {
    if (Number(userStore.userType) === 2 && [1, 2].includes(Number(formData.dataScope)))
      formData.dataScope = 5
    else if (Number(formData.dataScope) === 1)
      formData.dataScope = 2
  }
  return formData
}

async function loadRoleList() {
  try {
    roleListLoading.value = true
    const params = {
      pageNum: 1,
      pageSize: 200,
      roleName: roleKeyword.value || undefined,
      roleType: activeRoleType.value === null ? undefined : activeRoleType.value,
    }
    const res = await request.get('/system/role/page', { params })
    if (res.code === 200) {
      roleList.value = res.data?.records || res.data?.list || []
      if (!roleList.value.some(item => item.id === currentRole.value?.id)) {
        const firstRole = roleList.value[0]
        if (firstRole) {
          await handleSelectRole(firstRole)
        }
        else {
          currentRole.value = {}
          roleUserTotal.value = 0
        }
      }
    }
  }
  catch (error) {
    console.error('加载角色列表失败:', error)
    window.$message.error('加载角色列表失败')
  }
  finally {
    roleListLoading.value = false
  }
}

function handleRoleTypeChange(value) {
  activeRoleType.value = value
  roleKeyword.value = ''
  loadRoleList()
}

function handleRoleSearch() {
  loadRoleList()
}

function handleAddRole() {
  crudRef.value?.showAdd()
}

async function handleSelectRole(row) {
  if (!row?.id)
    return
  currentRole.value = row
  roleUserKeyword.value = ''
  userSearchParams.value = {
    userStatus: null,
  }
  roleUserOrgId.value = null
  roleUserTotal.value = resolveRoleMemberCount(row) ?? 0
  try {
    await Promise.all([
      loadRoleOrgTree(row.tenantId),
      loadRoleApplicableOrgIds(row.id),
    ])
    await searchRoleUsers()
  }
  catch (error) {
    console.error('加载角色用户上下文失败:', error)
    window.$message.error('加载角色用户失败')
  }
}

async function handleRoleMutationSuccess() {
  await loadRoleList()
  crudRef.value?.refresh()
}

// 编辑
function handleEdit(row) {
  crudRef.value?.showEdit(row)
}

// 删除
function handleDelete(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定要删除角色"${row.roleName}"吗？删除后将无法恢复！`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await request.post('/system/role/remove', null, { params: { id: row.id } })
        if (res.code === 200) {
          window.$message.success('删除成功')
          await handleRoleMutationSuccess()
        }
      }
      catch {
        window.$message.error('删除失败')
      }
    },
  })
}

async function handleRoleOrgScope(row) {
  if (!row?.id)
    return
  if (currentRole.value?.id !== row.id)
    await handleSelectRole(row)
  roleOrgModalVisible.value = true
  checkedRoleOrgKeys.value = []
  roleOrgExpandedKeys.value = []
  try {
    roleOrgLoading.value = true
    checkedRoleOrgKeys.value = normalizeNumberList(roleApplicableOrgIds.value)
    roleScopeMode.value = isCurrentRoleGlobalScope.value ? 'global' : 'custom'
  }
  catch (error) {
    console.error('加载角色适用组织失败:', error)
    window.$message.error('加载角色适用组织失败')
  }
  finally {
    roleOrgLoading.value = false
  }
}

function toggleRoleOrgExpandAll() {
  roleOrgTreeExpandAll.value = !roleOrgTreeExpandAll.value
  roleOrgExpandedKeys.value = roleOrgTreeExpandAll.value ? getAllKeys(roleOrgTreeData.value) : []
}

function handleRoleOrgExpandedKeysChange(keys) {
  roleOrgExpandedKeys.value = keys
}

function handleRoleOrgCheckedKeysChange(keys) {
  checkedRoleOrgKeys.value = normalizeNumberList(keys)
}

async function handleSubmitRoleOrgs() {
  const nextOrgIds = roleScopeMode.value === 'global'
    ? []
    : normalizeNumberList(checkedRoleOrgKeys.value)
  if (roleScopeMode.value === 'custom' && nextOrgIds.length === 0) {
    window.$message.warning('请至少选择一个适用组织')
    return
  }
  try {
    roleOrgSubmitLoading.value = true
    const res = await request.post(`/system/role/${currentRole.value.id}/orgs`, nextOrgIds)
    if (res.code === 200) {
      window.$message.success('适用组织保存成功')
      roleOrgModalVisible.value = false
      roleApplicableOrgIds.value = nextOrgIds
      currentRole.value.orgScopeType = roleScopeMode.value === 'global'
        ? ROLE_ORG_SCOPE_GLOBAL
        : ROLE_ORG_SCOPE_CUSTOM
      const listRole = roleList.value.find(item => Number(item.id) === Number(currentRole.value.id))
      if (listRole)
        listRole.orgScopeType = currentRole.value.orgScopeType
      if (!isCurrentRoleGlobalScope.value && roleUserOrgId.value && !roleApplicableOrgIds.value.includes(roleUserOrgId.value)) {
        roleUserOrgId.value = null
      }
      await searchRoleUsers()
    }
  }
  catch (error) {
    console.error('保存适用组织失败:', error)
    window.$message.error('保存适用组织失败')
  }
  finally {
    roleOrgSubmitLoading.value = false
  }
}

function handleRoleScopeModeChange(value) {
  roleScopeMode.value = value
  if (value === 'global')
    checkedRoleOrgKeys.value = normalizeNumberList(allRoleOrgIds.value)
}

// 查看角色用户
async function handleViewUsers(row) {
  await handleSelectRole(row)
}

function beforeLoadRoleUserList(params = {}) {
  const keyword = String(roleUserKeyword.value || '').trim()
  const nextParams = {
    ...params,
    username: keyword || undefined,
    userStatus: userSearchParams.value.userStatus,
    orgId: roleUserOrgId.value || undefined,
  }
  Object.keys(nextParams).forEach((key) => {
    if (nextParams[key] === '' || nextParams[key] === null || nextParams[key] === undefined)
      delete nextParams[key]
  })
  return nextParams
}

function handleRoleUserLoadSuccess({ total = 0 } = {}) {
  roleUserTotal.value = total
  if (currentRole.value?.id) {
    roleUserCountMap.value = {
      ...roleUserCountMap.value,
      [currentRole.value.id]: total,
    }
    currentRole.value.userCount = total
    const listRole = roleList.value.find(item => Number(item.id) === Number(currentRole.value.id))
    if (listRole)
      listRole.userCount = total
  }
}

async function refreshRoleUsers() {
  if (!currentRole.value?.id) {
    roleUserTotal.value = 0
    return
  }
  await nextTick()
  roleUserCrudRef.value?.refresh?.()
}

async function searchRoleUsers() {
  if (!currentRole.value?.id) {
    roleUserTotal.value = 0
    return
  }
  await nextTick()
  roleUserCrudRef.value?.search?.({})
}

// 用户搜索
function handleUserSearch() {
  searchRoleUsers()
}

function handleRoleUserOrgChange() {
  searchRoleUsers()
}

// 用户搜索重置
function handleUserSearchReset() {
  roleUserKeyword.value = ''
  userSearchParams.value = {
    userStatus: null,
  }
  searchRoleUsers()
}

// 移除角色用户
async function handleRemoveUserRole(user) {
  const orgId = normalizeSingleNumber(roleUserOrgId.value)
  window.$dialog.warning({
    title: '确认移除',
    content: orgId === null
      ? `确定移除用户“${user.username}”在全部授权组织下的角色“${currentRole.value.roleName}”吗？`
      : `确定移除用户“${user.username}”在当前授权组织下的角色“${currentRole.value.roleName}”吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        if (orgId === null) {
          const res = await request.post('/system/role/removeUserRole', null, {
            params: {
              roleId: currentRole.value.id,
              userId: user.id,
            },
          })
          if (res.code === 200) {
            window.$message.success('移除成功')
            await refreshRoleUsers()
          }
          return
        }
        const currentRes = await request.get(`/system/user/${user.id}/org-roles`, {
          params: {
            tenantId: currentRole.value.tenantId,
            orgId,
          },
        })
        const nextRoleIds = normalizeNumberList(currentRes.code === 200 ? currentRes.data : [])
          .filter(roleId => Number(roleId) !== Number(currentRole.value.id))
        const res = await request.post(`/system/user/${user.id}/org-roles`, {
          tenantId: currentRole.value.tenantId,
          orgId,
          roleIds: nextRoleIds,
        })
        if (res.code === 200) {
          window.$message.success('移除成功')
          await refreshRoleUsers()
        }
      }
      catch (error) {
        console.error('移除用户失败:', error)
        window.$message.error('移除用户失败')
      }
    },
  })
}

// 加载角色已授权用户ID列表
async function loadAssignedUserIds() {
  const orgId = normalizeSingleNumber(roleUserOrgId.value)
  if (orgId === null) {
    assignedUserIds.value = []
    return
  }
  try {
    const res = await request.get(`/system/role/${currentRole.value.id}/users`, {
      params: { pageNum: 1, pageSize: 9999, orgId },
    })
    if (res.code === 200 && res.data) {
      assignedUserIds.value = (res.data.records || []).map(u => u.id)
    }
  }
  catch {
    assignedUserIds.value = []
  }
}

// 打开添加用户弹窗
async function handleAddUser() {
  if (!canAddUserToCurrentRole.value) {
    window.$message.warning('请先选择一个授权组织')
    return
  }
  await loadAssignedUserIds()
  addUserModalVisible.value = true
}

// 从角色列表直接添加用户
async function handleAddUserFromList(row) {
  await handleSelectRole(row)
  if (!canAddUserToCurrentRole.value) {
    window.$message.warning('请先选择一个授权组织')
    return
  }
  await loadAssignedUserIds()
  addUserModalVisible.value = true
}

// 确认添加用户到角色
async function handleConfirmAddUsers(userIds) {
  if (!userIds || userIds.length === 0)
    return
  const orgId = normalizeSingleNumber(roleUserOrgId.value)
  if (orgId === null) {
    window.$message.warning('请选择授权组织')
    return
  }
  try {
    addUserLoading.value = true
    for (const userId of userIds) {
      const currentRes = await request.get(`/system/user/${userId}/org-roles`, {
        params: {
          tenantId: currentRole.value.tenantId,
          orgId,
        },
      })
      const roleIds = Array.from(new Set([
        ...normalizeNumberList(currentRes.code === 200 ? currentRes.data : []),
        normalizeSingleNumber(currentRole.value.id),
      ]))
      const res = await request.post(`/system/user/${userId}/org-roles`, {
        tenantId: currentRole.value.tenantId,
        orgId,
        roleIds,
      })
      if (res.code !== 200) {
        throw new Error(`用户 ${userId} 添加失败`)
      }
    }
    window.$message.success(`成功添加 ${userIds.length} 个用户`)
    addUserModalVisible.value = false
    await refreshRoleUsers()
  }
  catch (error) {
    console.error('添加用户失败:', error)
    window.$message.error('添加用户失败')
  }
  finally {
    addUserLoading.value = false
  }
}

// 授权
async function handleAuth(row) {
  if (!row?.id)
    return
  if (currentRole.value?.id !== row.id)
    await handleSelectRole(row)
  authModalVisible.value = true
  authLoadFailed.value = false
  dataScopeLoadFailed.value = false
  dataScopeSettings.value = createFallbackDataScopeSettings()

  await Promise.all([
    loadClientList(),
    loadRoleDataScopes(),
  ])
  if (!authClientTabs.value.some(item => item.clientCode === currentAuthClientCode.value)) {
    currentAuthClientCode.value = authClientTabs.value[0]?.clientCode || 'pc'
  }
  await loadAuthClientResources()
}

// 获取所有节点的 key（用于展开/收起）
function getAllKeys(list, keys = []) {
  list.forEach((item) => {
    keys.push(item.id)
    if (item.children && item.children.length > 0) {
      getAllKeys(item.children, keys)
    }
  })
  return keys
}

// 加载资源树
async function loadResourceTree() {
  try {
    const res = await request.get('/system/resource/assignable-tree', {
      params: { clientCode: currentAuthClientCode.value },
    })
    if (res.code === 200) {
      resourceTreeData.value = res.data || []
      return
    }
    throw new Error(res.message || '资源树响应异常')
  }
  catch (error) {
    authLoadFailed.value = true
    console.error('加载资源树失败:', error)
    window.$message.error('加载资源树失败')
  }
}

// 加载角色已有的资源
async function loadRoleResources(roleId) {
  try {
    const res = await request.get(`/system/role/${roleId}/resources`, {
      params: { clientCode: currentAuthClientCode.value, includeParents: true },
    })
    if (res.code === 200) {
      checkedResourceKeys.value = res.data || []
      return
    }
    throw new Error(res.message || '角色资源响应异常')
  }
  catch (error) {
    authLoadFailed.value = true
    console.error('加载角色资源失败:', error)
    window.$message.error('加载角色资源失败')
  }
}

async function loadClientList() {
  try {
    const res = await request.get('/system/client/list')
    if (res.code === 200) {
      clientList.value = res.data || []
    }
  }
  catch (error) {
    console.error('加载客户端列表失败:', error)
  }
}

async function loadAuthClientResources() {
  authLoading.value = true
  authLoadFailed.value = false
  checkedResourceKeys.value = []
  resourceTreeData.value = []
  try {
    await Promise.all([
      loadResourceTree(),
      loadRoleResources(currentRole.value.id),
    ])
  }
  finally {
    authLoading.value = false
  }
}

async function loadRoleDataScopes() {
  dataScopeLoading.value = true
  dataScopeLoadFailed.value = false
  try {
    const res = await request.get(`/system/role/${currentRole.value.id}/dataScopes`)
    if (res.code === 200) {
      applyRoleDataScopeSettings(res.data)
      return
    }
    dataScopeLoadFailed.value = true
    dataScopeSettings.value = createFallbackDataScopeSettings()
  }
  catch (error) {
    dataScopeLoadFailed.value = true
    dataScopeSettings.value = createFallbackDataScopeSettings()
    console.warn('当前后端未提供角色数据权限明细接口，已按角色数据范围降级展示:', error)
  }
  finally {
    dataScopeLoading.value = false
  }
}

async function handleAuthClientChange(clientCode) {
  currentAuthClientCode.value = clientCode
  await loadAuthClientResources()
}

function createFallbackDataScopeSettings() {
  return {
    defaultDataScope: Number(currentRole.value?.dataScope) || 5,
    modules: [],
  }
}

function normalizeRoleDataScopeSettings(settings = {}) {
  const defaultDataScope = Number(settings.defaultDataScope) || Number(currentRole.value?.dataScope) || 5
  return {
    defaultDataScope,
    modules: (settings.modules || []).map(module => ({
      ...module,
      dataScope: module.dataScope == null ? null : Number(module.dataScope),
      effectiveDataScope: Number(module.effectiveDataScope ?? defaultDataScope) || defaultDataScope,
    })),
  }
}

function applyRoleDataScopeSettings(settings = {}) {
  const normalized = normalizeRoleDataScopeSettings(settings)
  dataScopeSettings.value = normalized
  if (currentRole.value)
    currentRole.value.dataScope = normalized.defaultDataScope
}

// 提交授权
async function handleSubmitAuth() {
  if (authLoading.value || dataScopeLoading.value || authLoadFailed.value || dataScopeLoadFailed.value) {
    window.$message.error('权限配置尚未完整加载，请关闭弹窗后重试')
    return
  }

  try {
    authSubmitLoading.value = true
    const res = await request.post(
      `/system/role/${currentRole.value.id}/resources`,
      checkedResourceKeys.value,
      { params: { clientCode: currentAuthClientCode.value } },
    )
    if (res.code === 200) {
      await saveRoleDataScopesIfSupported()
      window.$message.success('角色权限配置已保存')
      authModalVisible.value = false
      crudRef.value?.refresh()
    }
    else {
      throw new Error(res.message || '功能权限保存失败')
    }
  }
  catch (error) {
    console.error('角色权限配置保存失败:', error)
    window.$message.error(error?.message || '保存失败')
  }
  finally {
    authSubmitLoading.value = false
  }
}

async function saveRoleDataScopesIfSupported() {
  const dataScopeRes = await request.post(`/system/role/${currentRole.value.id}/dataScopes`, {
    defaultDataScope: dataScopeSettings.value.defaultDataScope,
    moduleScopes: dataScopeSettings.value.modules.map(module => ({
      moduleCode: module.moduleCode,
      dataScope: module.dataScope,
    })),
  })
  if (dataScopeRes.code !== 200)
    throw new Error(dataScopeRes.message || '数据权限保存失败')
  applyRoleDataScopeSettings(dataScopeRes.data)
}

onMounted(() => {
  loadRoleList()
})
</script>

<style scoped>
.system-role-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
}

.role-workspace {
  flex: 1;
  height: 100%;
  min-height: 0;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.05);
}

.role-list-panel {
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fbfcff;
}

.role-selector-header {
  min-height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px;
  border-bottom: 0;
}

.role-selector-title {
  min-width: 120px;
  display: flex;
  align-items: center;
  gap: 9px;
}

.role-selector-icon {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: #2563eb;
  font-size: 17px;
}

.role-selector-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-selector-copy strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.2;
}

.role-selector-copy small {
  color: #64748b;
  font-size: 11px;
  line-height: 1.2;
}

.role-selector-tools {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 0 12px 8px;
  border-bottom: 1px solid #f1f5f9;
  background: #fff;
}

.role-tabs {
  display: flex;
  align-items: center;
  gap: 3px;
  width: 100%;
  min-width: 0;
  min-height: 30px;
  padding: 2px;
  border: 0;
  border-radius: 6px;
  background: #f1f5f9;
}

.role-tabs button {
  min-width: 0;
  flex: 1 1 0;
  height: 26px;
  padding: 0 7px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition:
    background 0.16s ease,
    color 0.16s ease,
    box-shadow 0.16s ease;
}

.role-tabs button:hover,
.role-tabs button.is-active {
  color: #2563eb;
}

.role-tabs button.is-active {
  background: #fff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
}

.role-search {
  width: 100%;
  min-width: 0;
}

.role-list-spin {
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.role-list-spin :deep(.n-spin-container),
.role-list-spin :deep(.n-spin-content) {
  height: 100%;
  min-height: 0;
}

.role-list {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 8px;
}

.role-list-item {
  width: 100%;
  min-width: 0;
  min-height: 54px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  position: relative;
  padding: 8px 8px 8px 10px;
  margin-bottom: 0;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #0f172a;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    box-shadow 0.16s ease;
}

.role-list-item:hover {
  border-color: #e2e8f0;
  background: #fff;
}

.role-list-item.is-selected {
  border-color: transparent;
  background: rgba(239, 246, 255, 0.6);
  box-shadow: none;
}

.role-list-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.role-list-title {
  max-width: 100%;
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.role-list-main strong {
  max-width: 100%;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-list-item.is-selected .role-list-main strong {
  color: #1d4ed8;
}

.role-list-item.is-selected .role-card-meta {
  color: rgba(59, 130, 246, 0.7);
}

.role-list-item.is-selected .role-card-meta > * + *::before {
  color: rgba(191, 219, 254, 0.95);
}

.role-disabled-badge {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  height: 16px;
  padding: 0 4px;
  border: 1px solid #fecaca;
  border-radius: 4px;
  background: #fef2f2;
  color: #ef4444;
  font-size: 10px;
  line-height: 14px;
}

.role-card-meta {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
}

.role-card-meta small,
.role-card-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-card-meta > * + *::before {
  content: '·';
  margin-right: 6px;
  color: #cbd5e1;
}

.role-list-side {
  display: inline-flex;
  align-items: flex-start;
  flex: 0 0 auto;
  gap: 4px;
}

.role-card-menu {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  opacity: 0;
  transition:
    opacity 0.16s ease,
    background 0.16s ease,
    color 0.16s ease;
}

.role-list-item:hover .role-card-menu,
.role-list-item.is-selected .role-card-menu {
  opacity: 1;
}

.role-card-menu:hover {
  background: #e2e8f0;
  color: #334155;
}

.role-card-menu i {
  font-size: 16px;
}

:global(.role-action-dropdown-menu) {
  min-width: 136px;
  padding: 6px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.14);
}

:global(.role-action-dropdown-menu .n-dropdown-option) {
  border-radius: 6px;
}

:global(.role-action-danger) {
  color: #dc2626;
}

.role-user-panel {
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.role-user-header {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-height: 54px;
  padding: 11px 16px;
  border-bottom: 1px solid #e6ebf2;
  background: #fff;
}

.role-user-header h2 {
  overflow: hidden;
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-user-title {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.role-user-heading {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.role-user-heading small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-user-badges {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.role-user-search {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: minmax(200px, 280px) minmax(180px, 240px) minmax(120px, 160px) auto;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border-bottom: 1px solid #edf1f7;
  background: #fff;
}

.role-user-search-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-self: end;
}

.role-user-search-action {
  width: 72px;
}

.member-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 8px 10px 10px;
  background: #fff;
}

.member-body :deep(.ai-crud-page) {
  height: 100%;
  min-height: 0;
}

.member-body :deep(.ai-crud-layout),
.member-body :deep(.ai-crud-content),
.member-body :deep(.ai-crud-table),
.member-body :deep(.ai-table-wrapper),
.member-body :deep(.n-data-table) {
  min-height: 0;
}

.member-body :deep(.ai-crud-table) {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.member-body :deep(.ai-table-wrapper) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.member-body :deep(.n-data-table-wrapper),
.member-body :deep(.n-data-table-base-table),
.member-body :deep(.n-data-table-base-table-body) {
  min-height: 0;
}

:global(.role-member-plain) {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.role-member-phone) {
  font-family: var(--font-family-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace);
  font-variant-numeric: tabular-nums;
}

:global(.role-member-status) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #059669;
  font-size: 12px;
  font-weight: 500;
}

:global(.role-member-status.is-disabled) {
  color: #64748b;
}

:global(.role-member-status-dot) {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #10b981;
}

:global(.role-member-status.is-disabled .role-member-status-dot) {
  background: #94a3b8;
}

.crud-driver {
  position: fixed;
  top: 0;
  left: -9999px;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.crud-driver :deep(.n-modal-container),
.crud-driver :deep(.n-drawer-container) {
  opacity: 1;
}

/* 授权弹窗样式 */
.auth-modal-content {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  color: #0f172a;
  background: #f8fafc;
}

.auth-workspace-header {
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 16px;
  min-height: 56px;
  padding: 10px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.auth-header-main {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.auth-breadcrumb {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.auth-breadcrumb-divider {
  color: #cbd5e1;
  font-weight: 500;
}

.auth-role-badge,
.auth-client-badge {
  display: inline-flex;
  align-items: center;
  max-width: 260px;
  min-width: 0;
  gap: 6px;
  padding: 3px 9px;
  border: 1px solid #e0e7ff;
  border-radius: 6px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
}

.auth-role-badge {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-role-key {
  max-width: 260px;
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.auth-header-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
}

.auth-client-tabs {
  flex: 0 0 auto;
  padding: 8px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.auth-client-tabs :deep(.n-tabs-nav) {
  max-width: 100%;
}

.auth-load-alert {
  flex: 0 0 auto;
  margin: 12px 16px 0;
}

.auth-floating-actions {
  position: fixed;
  right: 24px;
  bottom: 22px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(10px);
}

:global(.role-permission-modal.n-card) {
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  max-width: 100vw;
  max-height: 100vh;
  border-radius: 0;
}

:global(.role-permission-modal .n-card-header) {
  display: none;
}

:global(.role-permission-modal .n-card-content) {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

:global(.role-permission-modal .n-card__content) {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

.role-org-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.role-scope-mode {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 弹窗底部按钮样式 */
:deep(.n-card__footer) {
  padding: 14px 24px;
  border-top: 1px solid #e5e7eb;
  background-color: #f8fafc;
}

/* 深色模式 */
.dark .system-role-page {
  background: #020617;
}

.dark .role-list-panel,
.dark .role-user-cards,
.dark .member-body,
.dark .role-user-header,
.dark .role-user-search {
  background: #0f172a;
}

.dark .role-tabs,
.dark .role-selector-header,
.dark .role-selector-tools,
.dark .role-user-header,
.dark .role-user-search {
  border-color: #334155;
  background: #0f172a;
}

.dark .role-tabs {
  background: #111827;
}

.dark .role-tabs button.is-active {
  background: #1e293b;
  box-shadow: none;
}

.dark .role-selector-icon {
  color: #bfdbfe;
}

.dark .role-selector-copy strong {
  color: #f1f5f9;
}

.dark .role-selector-copy small,
.dark .role-user-heading small,
.dark .role-card-meta,
.dark .role-member-main small,
.dark .role-member-plain {
  color: #94a3b8;
}

.dark .role-tabs button {
  color: #cbd5e1;
}

.dark .role-tabs button:hover,
.dark .role-tabs button.is-active {
  color: #93c5fd;
}

.dark .role-list-item {
  border-color: transparent;
  background: transparent;
  color: #e2e8f0;
}

.dark .role-list-item:hover {
  border-color: #334155;
  background: #162033;
}

.dark .role-list-item.is-selected {
  border-color: transparent;
  background: rgba(37, 99, 235, 0.18);
  box-shadow: none;
}

.dark .role-list-main strong,
.dark .role-member-main strong,
.dark .role-user-header h2 {
  color: #f1f5f9;
}

.dark .role-list-item.is-selected .role-list-main strong,
.dark .role-list-item.is-selected .role-card-meta {
  color: #bfdbfe;
}

.dark .role-disabled-badge {
  border-color: rgba(248, 113, 113, 0.28);
  background: rgba(127, 29, 29, 0.28);
  color: #fca5a5;
}

.dark .role-card-menu {
  color: #94a3b8;
}

.dark .role-card-menu:hover {
  background: rgba(30, 41, 59, 0.86);
  color: #bfdbfe;
}

:global(.dark .role-action-dropdown-menu) {
  border-color: #334155;
  box-shadow: 0 16px 34px rgba(0, 0, 0, 0.34);
}

.dark :global(.role-member-plain) {
  color: #94a3b8;
}

.dark :global(.role-member-status) {
  color: #34d399;
}

.dark :global(.role-member-status.is-disabled) {
  color: #94a3b8;
}

.dark :global(.role-member-status-dot) {
  background: #34d399;
}

.dark :global(.role-member-status.is-disabled .role-member-status-dot) {
  background: #64748b;
}

.dark .auth-modal-content {
  color: #e5e7eb;
  background: #0f172a;
}

.dark .auth-workspace-header,
.dark .auth-client-tabs {
  background: #111827;
  border-color: #334155;
}

.dark .auth-breadcrumb {
  color: #f8fafc;
}

.dark .auth-role-badge,
.dark .auth-client-badge {
  background: #1e3a8a;
  border-color: #1d4ed8;
  color: #bfdbfe;
}

.dark .auth-role-key,
.dark .auth-breadcrumb-divider {
  color: #94a3b8;
}

.dark .auth-floating-actions {
  border-color: rgba(51, 65, 85, 0.92);
  background: rgba(15, 23, 42, 0.92);
  box-shadow: 0 18px 36px rgba(0, 0, 0, 0.34);
}

.dark .role-org-toolbar {
  background: #111827;
  border-color: #334155;
}

@media (max-width: 760px) {
  .auth-workspace-header {
    align-items: stretch;
    flex-direction: column;
  }

  .auth-header-main,
  .auth-header-actions {
    width: 100%;
  }

  .auth-header-main {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .auth-header-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .auth-floating-actions {
    right: 12px;
    bottom: 12px;
    left: 12px;
    justify-content: flex-end;
  }
}

@media (max-width: 860px) {
  .role-list-panel {
    min-height: 0;
  }

  .role-user-search {
    grid-template-columns: 1fr;
  }

  .role-user-search-actions {
    justify-self: start;
  }
}
</style>
