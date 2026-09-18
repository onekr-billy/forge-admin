<template>
  <div class="collaboration-console-page">
    <div class="console-shell">
      <!-- 控制台页头：标题 + 配置指南入口 -->
      <div class="console-header">
        <div class="console-header__main">
          <h2 class="console-header__title">
            企业协同
          </h2>
          <p class="console-header__subtitle">
            对接企业微信、钉钉、飞书等平台，统一管理连接配置、通讯录同步与消息投递
          </p>
        </div>
        <n-button size="small" tertiary @click="guideVisible = true">
          <template #icon>
            <span class="console-guide-icon">?</span>
          </template>
          配置指南
        </n-button>
      </div>

      <!-- 全功能页签：连接配置 + 同步/投递/回调全链路运维，按权限控制可见性 -->
      <n-tabs v-model:value="activeTab" type="line" class="console-tabs" pane-class="console-tab-pane">
        <n-tab-pane name="connections" tab="连接管理" display-directive="show">
          <AiCrudPage
            ref="crudRef"
            api="/system/collaboration/connections"
            :api-config="{
              list: 'get@/system/collaboration/connections/page',
              add: 'post@/system/collaboration/connections',
              update: 'put@/system/collaboration/connections',
              delete: 'delete@/system/collaboration/connections/:id',
            }"
            :search-schema="searchSchema"
            :columns="tableColumns"
            :edit-schema="editSchema"
            row-key="id"
            add-button-text="新建连接"
            :edit-grid-cols="2"
            modal-width="900px"
            :hide-batch-delete="true"
            :hide-selection="true"
            :before-render-detail="handleBeforeRenderDetail"
            :before-submit="handleBeforeSubmit"
          />
        </n-tab-pane>
        <n-tab-pane v-if="canViewTab('system:collaboration:sync:view')" name="sync" tab="同步批次" display-directive="show:lazy">
          <SyncPanel />
        </n-tab-pane>
        <n-tab-pane v-if="canViewTab('system:collaboration:issue:view')" name="issues" tab="问题单" display-directive="show:lazy">
          <IssuesPanel />
        </n-tab-pane>
        <n-tab-pane v-if="canViewTab('system:collaboration:mapping:view')" name="mappings" tab="映射查询" display-directive="show:lazy">
          <MappingsPanel />
        </n-tab-pane>
        <n-tab-pane v-if="canViewTab('system:collaboration:delivery:view')" name="deliveries" tab="投递记录" display-directive="show:lazy">
          <DeliveriesPanel />
        </n-tab-pane>
        <n-tab-pane v-if="canViewTab('system:collaboration:callback:view')" name="callbackEvents" tab="回调事件" display-directive="show:lazy">
          <CallbackEventsPanel />
        </n-tab-pane>
      </n-tabs>
    </div>

    <!-- 配置指南：三步上手 + 各平台凭据获取路径 -->
    <n-modal
      v-model:show="guideVisible"
      title="配置指南"
      preset="card"
      style="width: 720px"
    >
      <div class="guide-content">
        <div class="guide-steps">
          <div class="guide-step">
            <div class="guide-step__index">
              1
            </div>
            <div>
              <div class="guide-step__title">
                新建连接
              </div>
              <div class="guide-step__desc">
                选择平台、填写外部企业ID等基础信息，连接编码可留空自动生成。每个字段旁的 ? 图标都有详细说明。
              </div>
            </div>
          </div>
          <div class="guide-step">
            <div class="guide-step__index">
              2
            </div>
            <div>
              <div class="guide-step__title">
                配置应用凭据
              </div>
              <div class="guide-step__desc">
                在连接的「应用管理」中新增应用，填入应用ID/Key 与 Secret。一个连接可配置多个应用，分别承担登录、消息等不同职责。
              </div>
            </div>
          </div>
          <div class="guide-step">
            <div class="guide-step__index">
              3
            </div>
            <div>
              <div class="guide-step__title">
                绑定能力并测试
              </div>
              <div class="guide-step__desc">
                将扫码登录、通讯录同步、消息推送等能力绑定到具体应用，然后用「测试」验证连通性，同步/投递结果在对应页签中查看。
              </div>
            </div>
          </div>
        </div>
        <n-divider style="margin: 16px 0 12px" />
        <div class="guide-section-title">
          各平台凭据获取路径
        </div>
        <n-table :bordered="true" size="small" class="guide-table">
          <thead>
            <tr>
              <th style="width: 90px">
                平台
              </th><th>企业ID</th><th>应用凭据（ID/Secret）</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>企业微信</td>
              <td>管理后台 → 我的企业 → 企业信息 → 企业ID</td>
              <td>管理后台 → 应用管理 → 自建应用 → AgentId / Secret</td>
            </tr>
            <tr>
              <td>钉钉</td>
              <td>开放平台首页右上角 CorpId</td>
              <td>开放平台 → 应用开发 → 企业内部应用 → AppKey / AppSecret</td>
            </tr>
            <tr>
              <td>飞书</td>
              <td>管理后台 → 设置 → 企业信息</td>
              <td>开放平台 → 开发者后台 → 应用 → App ID / App Secret</td>
            </tr>
            <tr>
              <td>其它（Gitee/GitHub 等）</td>
              <td>无需填写（仅 OAuth 登录）</td>
              <td>平台开发者设置 → OAuth 应用 → ClientID / ClientSecret</td>
            </tr>
          </tbody>
        </n-table>
      </div>
    </n-modal>

    <!-- 连接详情（只读查看）与应用管理（新增/编辑/绑定）共用弹窗，由 manageMode 区分 -->
    <n-modal
      v-model:show="detailVisible"
      :title="manageMode ? '应用与能力管理' : '连接详情'"
      preset="card"
      style="width: 1000px"
      :mask-closable="false"
    >
      <n-spin :show="detailLoading">
        <div v-if="detail" class="detail-content">
          <n-descriptions v-if="!manageMode" bordered :column="3" size="small">
            <n-descriptions-item label="平台">
              <DictTag dict-type="sys_collab_platform" :value="detail.connection?.platform" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="连接编码">
              {{ detail.connection?.connectionCode || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="连接名称">
              {{ detail.connection?.connectionName || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="企业ID">
              {{ detail.connection?.enterpriseId || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="身份匹配策略">
              <DictTag dict-type="sys_collab_identity_policy" :value="detail.connection?.identityPolicy" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="默认角色">
              {{ formatDefaultRoles(detail.connection?.defaultRoleIds) }}
            </n-descriptions-item>
            <n-descriptions-item label="目录权威来源">
              <DictTag dict-type="sys_collab_directory_authority" :value="detail.connection?.directoryAuthority" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="默认挂载组织">
              {{ detail.connection?.defaultOrgId || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="API基础地址">
              {{ detail.connection?.apiBaseUrl || '官方地址' }}
            </n-descriptions-item>
            <n-descriptions-item label="工作台免登">
              <NTag :type="detail.connection?.ssoWorkbenchEnabled === 1 ? 'success' : 'default'" size="small">
                {{ detail.connection?.ssoWorkbenchEnabled === 1 ? '已开启' : '未开启' }}
              </NTag>
            </n-descriptions-item>
            <n-descriptions-item label="待办卡片推送">
              <NTag :type="detail.connection?.todoPushEnabled === 1 ? 'success' : 'default'" size="small">
                {{ detail.connection?.todoPushEnabled === 1 ? '已开启' : '未开启' }}
              </NTag>
            </n-descriptions-item>
            <n-descriptions-item label="待办H5地址">
              {{ detail.connection?.todoPushH5Url || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="定时目录同步">
              <NTag :type="detail.connection?.syncScheduleEnabled === 1 ? 'success' : 'default'" size="small">
                {{ detail.connection?.syncScheduleEnabled === 1 ? '已开启' : '未开启' }}
              </NTag>
            </n-descriptions-item>
            <n-descriptions-item label="同步周期">
              {{ detail.connection?.syncCron || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="状态">
              <DictTag dict-type="sys_normal_disable" :value="String(detail.connection?.status ?? '')" size="small" />
            </n-descriptions-item>
            <n-descriptions-item label="更新时间">
              {{ detail.connection?.updateTime || '-' }}
            </n-descriptions-item>
          </n-descriptions>
          <n-alert v-if="manageMode" type="info" :show-icon="false" size="small">
            当前连接：{{ detail.connection?.connectionName || '-' }}（{{ detail.connection?.connectionCode || '-' }}）
          </n-alert>

          <n-divider title-placement="left">
            物理应用
            <n-button
              v-if="manageMode && canManageConnection"
              size="tiny"
              type="primary"
              class="ml-2"
              @click="handleAddApp"
            >
              新增应用
            </n-button>
          </n-divider>
          <n-data-table
            :columns="appColumns"
            :data="detail.apps || []"
            :bordered="true"
            size="small"
          />

          <n-divider title-placement="left">
            能力绑定
            <n-button
              v-if="manageMode && canManageConnection"
              size="tiny"
              type="primary"
              class="ml-2"
              @click="handleOpenBind"
            >
              绑定能力
            </n-button>
          </n-divider>
          <n-data-table
            :columns="bindingColumns"
            :data="detail.bindings || []"
            :bordered="true"
            size="small"
          />
        </div>
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="detailVisible = false">
            关闭
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 物理应用新增/编辑：Secret 编辑时留空表示保留现值 -->
    <n-modal
      v-model:show="appModalVisible"
      :title="appForm.id ? '编辑应用' : '新增应用'"
      preset="card"
      style="width: 720px"
      :mask-closable="false"
    >
      <n-form
        ref="appFormRef"
        :model="appForm"
        :rules="appFormRules"
        label-placement="left"
        label-width="130px"
      >
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="应用编码" path="appCode">
            <n-input v-model:value="appForm.appCode" placeholder="连接内唯一，如 main-app" :disabled="!!appForm.id" />
          </n-form-item-gi>
          <n-form-item-gi label="应用名称" path="appName">
            <n-input v-model:value="appForm.appName" placeholder="请输入应用名称" />
          </n-form-item-gi>
          <n-form-item-gi label="应用ID/Key" path="clientId">
            <n-input v-model:value="appForm.clientId" placeholder="企微填 CorpId，OAuth 平台填 ClientID" />
          </n-form-item-gi>
          <n-form-item-gi label="AgentId" path="agentId">
            <n-input v-model:value="appForm.agentId" placeholder="企微自建应用 AgentId，OAuth 平台可空" />
          </n-form-item-gi>
          <n-form-item-gi label="应用Secret" path="secret" :span="2">
            <n-input
              v-model:value="appForm.secret"
              type="password"
              show-password-on="click"
              :placeholder="appForm.id ? '留空表示保留现有Secret' : '企微填 Secret，OAuth 平台填 ClientSecret'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="回调Token" path="callbackToken">
            <n-input
              v-model:value="appForm.callbackToken"
              type="password"
              show-password-on="click"
              :placeholder="appForm.id ? '留空保留现值' : '仅企业平台接收事件回调时填写'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="EncodingAESKey" path="encodingAesKey">
            <n-input
              v-model:value="appForm.encodingAesKey"
              type="password"
              show-password-on="click"
              :placeholder="appForm.id ? '留空保留现值' : '仅企业平台接收事件回调时填写'"
            />
          </n-form-item-gi>
          <n-form-item-gi label="OAuth回调地址" path="redirectUri" :span="2">
            <n-input v-model:value="appForm.redirectUri" placeholder="填前端登录回调页完整地址，如 http://81.70.22.48:8084/forge/login/callback；须与 Gitee 等平台侧登记的回调地址完全一致" />
          </n-form-item-gi>
          <n-form-item-gi label="授权范围" path="scope">
            <n-input v-model:value="appForm.scope" placeholder="如 snsapi_base / user_info，多个用逗号分隔" />
          </n-form-item-gi>
          <n-form-item-gi label="状态" path="status">
            <n-select v-model:value="appForm.status" :options="appStatusOptions" />
          </n-form-item-gi>
          <n-form-item-gi label="备注" path="remark" :span="2">
            <n-input v-model:value="appForm.remark" type="textarea" :rows="2" placeholder="备注说明" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="appModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="appSubmitLoading" @click="handleSubmitApp">
            保存
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 能力绑定 -->
    <n-modal
      v-model:show="bindModalVisible"
      title="绑定能力"
      preset="card"
      style="width: 480px"
      :mask-closable="false"
    >
      <n-form label-placement="left" label-width="100px">
        <n-form-item label="业务能力">
          <n-select v-model:value="bindForm.capability" :options="capabilityOptions" placeholder="请选择能力" />
        </n-form-item>
        <n-form-item label="物理应用">
          <n-select v-model:value="bindForm.appConfigId" :options="appSelectOptions" placeholder="请选择应用" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="bindModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="bindSubmitLoading" @click="handleSubmitBind">
            绑定
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 连通测试 -->
    <n-modal
      v-model:show="testModalVisible"
      title="连通测试"
      preset="card"
      style="width: 420px"
      :mask-closable="false"
    >
      <n-form label-placement="left" label-width="100px">
        <n-form-item label="测试能力">
          <n-select v-model:value="testCapability" :options="capabilityOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="testModalVisible = false">
            取消
          </n-button>
          <n-button type="primary" :loading="testLoading" @click="handleSubmitTest">
            开始测试
          </n-button>
        </n-space>
      </template>
    </n-modal>
    <!-- 消息测试：显式指定接收人发送协同消息，返回逐人投递结果 -->
    <n-modal
      v-model:show="msgTestVisible"
      title="消息测试"
      preset="card"
      style="width: 560px"
      :mask-closable="false"
    >
      <n-form
        ref="msgTestFormRef"
        :model="msgTestForm"
        :rules="msgTestRules"
        label-placement="left"
        label-width="80px"
      >
        <n-form-item label="接收人" path="userIds">
          <UserSelectPicker
            v-model:model-value="msgTestForm.userIds"
            v-model:label-value="msgTestForm.userLabels"
            multiple
            placeholder="请选择测试接收人（最多10人）"
            title="选择测试接收人"
          />
        </n-form-item>
        <n-form-item label="标题" path="title">
          <n-input v-model:value="msgTestForm.title" placeholder="消息标题，可空" />
        </n-form-item>
        <n-form-item label="正文" path="content">
          <n-input v-model:value="msgTestForm.content" type="textarea" :rows="3" placeholder="请输入消息正文" />
        </n-form-item>
      </n-form>
      <n-alert type="info" :show-icon="false" size="small" class="mb-3">
        接收人必须已同步/绑定该连接的外部账号，且在企微应用可见范围内，否则会被平台标记无效。
      </n-alert>
      <n-data-table
        v-if="msgTestResult"
        :columns="msgTestResultColumns"
        :data="msgTestResult.deliveries || []"
        :bordered="true"
        size="small"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="msgTestVisible = false">
            关闭
          </n-button>
          <n-button type="primary" :loading="msgTestLoading" @click="handleSubmitMsgTest">
            发送
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  bindConnectionCapability,
  createConnectionApp,
  deleteConnectionApp,
  getConnectionDetail,
  sendTestMessage,
  testConnection,
  triggerConnectionSync,
  unbindConnectionCapability,
  updateConnection,
  updateConnectionApp,
} from '@/api/collaboration'
import { AiCrudPage } from '@/components/ai-form'
import AuthImage from '@/components/common/AuthImage.vue'
import UserSelectPicker from '@/components/common/UserSelectPicker.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store'
import { request } from '@/utils'
import CallbackEventsPanel from './callback-events.vue'
import DeliveriesPanel from './deliveries.vue'
import IssuesPanel from './issues.vue'
import MappingsPanel from './mappings.vue'
import SyncPanel from './sync.vue'

defineOptions({ name: 'CollaborationConnections' })

/**
 * 企业型平台：具备企业ID、通讯录同步与应用消息能力。
 * 其余平台（Gitee/GitHub/QQ 等）仅支持 OAuth 登录，不具备企业ID/通讯录/消息投递概念。
 */
const ENTERPRISE_PLATFORMS = new Set(['WECHAT_ENTERPRISE', 'DINGTALK', 'DINGTALK_ACCOUNT', 'FEISHU'])
const WECOM_API_BASE_URL = 'https://qyapi.weixin.qq.com'

function isEnterprisePlatform(platform) {
  return ENTERPRISE_PLATFORMS.has(platform)
}

const crudRef = ref(null)
const route = useRoute()
const userStore = useUserStore()

// 控制台页签与配置指南
const activeTab = ref('connections')
const guideVisible = ref(false)

// 超管直接放行；普通用户按登录权限 + 当前路由按钮编码判断（v-permission 指令无超管旁路，不适用弹窗内按钮）
const grantedPerms = computed(() => {
  const routeBtns = (route.meta?.btns || []).map(item => item.code)
  return new Set([...(userStore.permissions || []), ...routeBtns])
})

const canManageConnection = computed(() => {
  if (userStore.isAdmin)
    return true
  return grantedPerms.value.has('system:collaboration:connection:update')
    || grantedPerms.value.has('**')
    || grantedPerms.value.has('*:*:*')
})

// 运维页签可见性：原独立菜单的 view 权限已降级为本菜单下的按钮资源
function canViewTab(perm) {
  if (userStore.isAdmin)
    return true
  return grantedPerms.value.has(perm)
    || grantedPerms.value.has('**')
    || grantedPerms.value.has('*:*:*')
}

const { dict, getLabel } = useDict(
  'sys_collab_platform',
  'sys_collab_capability',
  'sys_collab_identity_policy',
  'sys_collab_directory_authority',
  'sys_collab_connection_type',
  'sys_normal_disable',
)

const platformOptions = computed(() => dict.value.sys_collab_platform || [])
const identityPolicyOptions = computed(() => dict.value.sys_collab_identity_policy || [])
const directoryAuthorityOptions = computed(() => dict.value.sys_collab_directory_authority || [])
const connectionTypeOptions = computed(() => dict.value.sys_collab_connection_type || [])
const statusOptions = computed(() => dict.value.sys_normal_disable || [])
// 一期只放开 LOGIN/DIRECTORY/MESSAGE，TODO 待办联动二期开放
const capabilityOptions = computed(() =>
  (dict.value.sys_collab_capability || []).filter(item => item.value !== 'TODO'),
)

// ==================== 角色选项（默认角色配置用） ====================
const roleOptions = ref([])

async function loadRoleOptions() {
  try {
    const res = await request.get('/system/role/page', {
      params: { pageNum: 1, pageSize: 200 },
    })
    if (res.code === 200) {
      roleOptions.value = (res.data?.records || []).map(item => ({
        label: item.roleName,
        value: item.id,
      }))
    }
  }
  catch (e) {
    console.warn('加载角色列表失败:', e)
  }
}
loadRoleOptions()

/**
 * 格式化默认角色：逗号分隔的角色ID字符串 → 角色名称列表
 */
function formatDefaultRoles(roleIdsStr) {
  if (!roleIdsStr)
    return '跟随全局配置'
  const ids = roleIdsStr.split(',').map(id => Number(id.trim())).filter(Boolean)
  if (ids.length === 0)
    return '跟随全局配置'
  const names = ids.map((id) => {
    const role = roleOptions.value.find(r => r.value === id)
    return role ? role.label : `ID:${id}`
  })
  return names.join('、')
}

// ==================== 主表 ====================

const searchSchema = computed(() => [
  {
    field: 'platform',
    label: '平台',
    type: 'select',
    props: { placeholder: '请选择平台', options: platformOptions.value, clearable: true },
  },
  {
    field: 'connectionName',
    label: '连接名称',
    type: 'input',
    props: { placeholder: '请输入连接名称' },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: { placeholder: '请选择', options: statusOptions.value, clearable: true },
  },
])

const LOGO_STYLE = 'width:28px;height:28px;border-radius:6px;object-fit:contain;flex:0 0 auto;background:#f8fafc;border:1px solid #e2e8f0'
const LOGO_PLACEHOLDER_STYLE = 'width:28px;height:28px;border-radius:6px;flex:0 0 auto;display:flex;align-items:center;justify-content:center;background:#eff6ff;color:#2563eb;font-size:13px;font-weight:600'

const tableColumns = computed(() => [
  {
    prop: 'platform',
    label: '平台',
    width: 170,
    render: row => h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
      row.platformLogo
        ? h(AuthImage, { src: row.platformLogo, imgStyle: LOGO_STYLE, lazy: false })
        : h('div', { style: LOGO_PLACEHOLDER_STYLE }, (row.platformName || row.platform || '?').slice(0, 1)),
      h(DictTag, { dictType: 'sys_collab_platform', value: row.platform, size: 'small' }),
    ]),
  },
  {
    prop: 'connectionName',
    label: '连接名称',
    minWidth: 180,
    render: row => h('div', { style: 'line-height:1.4' }, [
      h('div', { style: 'font-weight:500' }, row.connectionName || '-'),
      h('div', { style: 'font-size:12px;color:#94a3b8' }, row.connectionCode || '-'),
    ]),
  },
  { prop: 'enterpriseId', label: '企业ID', width: 170, showOverflowTooltip: true },
  {
    prop: 'identityPolicy',
    label: '身份策略',
    width: 110,
    render: row => h(DictTag, { dictType: 'sys_collab_identity_policy', value: row.identityPolicy, size: 'small' }),
  },
  {
    prop: 'directoryAuthority',
    label: '目录来源',
    width: 100,
    render: row => h(DictTag, { dictType: 'sys_collab_directory_authority', value: row.directoryAuthority, size: 'small' }),
  },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: row => h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status ?? ''), size: 'small' }),
  },
  { prop: 'createTime', label: '创建时间', width: 160 },
  {
    prop: 'action',
    label: '操作',
    width: 370,
    fixed: 'right',
    actions: [
      { label: '详情', key: 'view', type: 'info', onClick: handleView },
      { label: '编辑', key: 'edit', type: 'primary', onClick: row => crudRef.value?.showEdit(row) },
      { label: '应用管理', key: 'manage', type: 'primary', onClick: handleManage },
      {
        label: '测试',
        key: 'test',
        type: 'primary',
        // 连通测试依赖 AccessToken 换取，纯 OAuth 登录平台无该能力
        visible: row => isEnterprisePlatform(row.platform),
        onClick: handleOpenTest,
      },
      {
        label: '同步',
        key: 'sync',
        type: 'primary',
        visible: row => isEnterprisePlatform(row.platform) && row.directoryAuthority === 'EXTERNAL',
        onClick: handleTriggerSync,
      },
      {
        label: '消息测试',
        key: 'msgTest',
        type: 'info',
        // 消息推送走应用 Token，仅企业型平台具备该能力
        visible: row => isEnterprisePlatform(row.platform),
        onClick: handleOpenMsgTest,
      },
      // 显式启用/停用入口：按当前状态互斥展示
      {
        label: '停用',
        key: 'disable',
        type: 'warning',
        visible: row => row.status === 1,
        onClick: handleToggleStatus,
      },
      {
        label: '启用',
        key: 'enable',
        type: 'success',
        visible: row => row.status === 0,
        onClick: handleToggleStatus,
      },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

const editSchema = computed(() => [
  { type: 'divider', label: '基础信息', props: { titlePlacement: 'left' }, span: 2 },
  {
    field: 'platform',
    label: '平台',
    type: 'select',
    labelTip: `选择要对接的第三方平台。
企业微信/钉钉/飞书属于企业型平台，支持通讯录同步和消息推送；
Gitee/GitHub 等仅支持扫码登录。`,
    rules: [{ required: true, message: '请选择平台', trigger: 'change' }],
    props: { options: platformOptions.value, clearable: false },
  },
  {
    field: 'platformName',
    label: '平台显示名称',
    type: 'input',
    labelTip: '展示给用户看的名称，会出现在登录页扫码入口和系统内平台标识上，如「企业微信」。',
    rules: [{ required: true, message: '请输入平台显示名称', trigger: 'blur' }],
    props: { placeholder: '如：企业微信' },
  },
  {
    field: 'platformLogo',
    label: '平台Logo',
    type: 'imageUpload',
    span: 2,
    businessType: 'platform-logo',
    limit: 1,
    fileSize: 2,
    valueType: 'string',
    labelTip: `上传平台Logo图片，用于登录页扫码入口和连接列表展示。
留空时列表以平台名首字占位展示。`,
    props: { showTip: true },
  },
  {
    field: 'connectionCode',
    label: '连接编码',
    type: 'input',
    labelTip: `连接的全局唯一标识，用于拼接免登链接和事件回调地址，创建后不建议修改。
留空将按「平台-随机后缀」自动生成，如 wecom-a1b2。`,
    props: { placeholder: '留空自动生成，如 wecom-a1b2' },
  },
  {
    field: 'connectionName',
    label: '连接名称',
    type: 'input',
    labelTip: '区分不同连接的内部名称，建议包含公司/组织名，如「XX科技企业微信」。',
    rules: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
    props: { placeholder: '如：XX科技企业微信' },
  },
  {
    field: 'enterpriseId',
    label: '外部企业ID',
    type: 'input',
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `外部平台分配给企业的唯一标识，企业型平台必填。获取位置：
・企业微信：管理后台 → 我的企业 → 企业信息 → 企业ID
・钉钉：开放平台首页右上角 CorpId
・飞书：管理后台 → 设置 → 企业信息
・Gitee/GitHub 等纯登录平台可留空`,
    props: { placeholder: '企业型平台必填，纯登录平台可留空' },
  },
  {
    field: 'connectionType',
    label: '连接类型',
    type: 'select',
    defaultValue: 'CORP_INTERNAL',
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `・自建应用：企业在平台管理后台自己创建的应用（最常见）
・第三方应用：通过服务商市场安装的应用
・仅OAuth登录：只用扫码登录，不涉及通讯录和消息
不确定时选「自建应用」即可。`,
    props: { options: connectionTypeOptions.value, clearable: false },
  },
  { type: 'divider', label: '目录与身份', props: { titlePlacement: 'left' }, span: 2, vIf: formData => isEnterprisePlatform(formData.platform) },
  {
    field: 'identityPolicy',
    label: '身份匹配策略',
    type: 'select',
    defaultValue: 'BIND_ONLY',
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `外部用户首次扫码登录时如何对应到本系统账号：
・仅绑定：只允许绑定已有账号，未绑定无法登录（最安全）
・自动建号：无匹配账号时自动创建新用户
・人工处理：无法匹配时生成问题单，由管理员在「问题单」页签处理`,
    rules: [{ required: true, message: '请选择身份匹配策略', trigger: 'change' }],
    props: { options: identityPolicyOptions.value, clearable: false },
  },
  {
    field: 'defaultRoleIds',
    label: '默认角色',
    type: 'select',
    span: 2,
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: '自动建号时为新用户分配的角色（可多选）。留空时跟随全局默认角色配置。',
    props: {
      options: roleOptions.value,
      multiple: true,
      clearable: true,
      placeholder: '自动建号时分配的默认角色（可多选），为空走全局配置',
    },
  },
  {
    field: 'directoryAuthority',
    label: '目录权威来源',
    type: 'select',
    defaultValue: 'NONE',
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `决定组织架构和人员名单以哪边为准：
・外部平台：以企微/钉钉通讯录为准，定期同步到本系统（选此项才能触发同步）
・本系统：以本系统组织架构为准，不从外部拉取
・不同步：不做目录同步，仅用于登录/消息`,
    rules: [{ required: true, message: '请选择目录权威来源', trigger: 'change' }],
    props: { options: directoryAuthorityOptions.value, clearable: false },
  },
  {
    field: 'defaultOrgId',
    label: '默认挂载组织ID',
    type: 'input',
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `同步过来的外部部门会挂在本系统的这个组织节点下。
组织ID可在「系统管理 → 部门管理」中查看，留空时挂在根节点下。`,
    props: { placeholder: '目录同步根组织ID，可空' },
  },
  {
    field: 'apiBaseUrl',
    label: 'API基础地址',
    type: 'input',
    span: 2,
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `调用平台接口的基础地址。绝大多数情况留空即可（自动使用官方地址）；
仅平台私有化部署时填写自建网关地址。`,
    props: { placeholder: '留空使用平台官方地址，私有化部署可自定义' },
  },
  // ── OAuth 平台提示（Gitee/GitHub 等纯登录平台，凭据在应用管理中配置） ──
  {
    type: 'divider',
    label: 'OAuth 凭据说明',
    span: 2,
    vIf: formData => !isEnterprisePlatform(formData.platform),
    props: {
      description: '纯 OAuth 登录平台（如 Gitee、GitHub）的凭据在「应用管理」中配置。保存连接后，请点击列表中的「应用管理」按钮，新增应用并填写 Client ID / Secret / 回调地址等 OAuth 参数。',
    },
  },
  // ── 企业型平台高级功能 ──
  { type: 'divider', label: '客户端免登', props: { titlePlacement: 'left' }, span: 2, vIf: formData => isEnterprisePlatform(formData.platform) },
  {
    field: 'ssoWorkbenchEnabled',
    label: '工作台免登',
    type: 'switch',
    defaultValue: 0,
    checkedValue: 1,
    uncheckedValue: 0,
    span: 2,
    labelTip: `开启后，用户在企业客户端（如企业微信）工作台点击本应用可自动登录，无需手动扫码。
仅企业型平台且已正确配置 OAuth 网页授权可信域名时生效；同平台多个连接只能开启一个。
前端会用本连接的「连接编码」发起免登，无需再在前端写死 connectionCode。`,
    vIf: formData => isEnterprisePlatform(formData.platform),
    props: { },
  },
  { type: 'divider', label: '待办推送', props: { titlePlacement: 'left' }, span: 2, vIf: formData => isEnterprisePlatform(formData.platform) },
  {
    field: 'todoPushEnabled',
    label: '待办卡片推送',
    type: 'switch',
    defaultValue: 0,
    checkedValue: 1,
    uncheckedValue: 0,
    span: 2,
    labelTip: `开启后，流程待办任务会以卡片消息推送到外部平台（如企微），点击卡片可直达待办H5页面。
需先在能力绑定中配置「消息推送」能力。`,
    vIf: formData => isEnterprisePlatform(formData.platform),
    props: { },
  },
  {
    field: 'todoPushH5Url',
    label: '待办H5访问地址',
    type: 'input',
    span: 2,
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `待办卡片点击后跳转的移动端H5地址，开启推送时必填。
填到H5应用根路径即可（如 https://h5.example.com/forge-h5），无需带 #/ 路由前缀，系统会自动拼接待办详情路径。
注意：该域名需在平台后台登记为可信域名（企微：应用详情 → 网页授权及 JS-SDK）。`,
    props: { placeholder: '如 https://h5.example.com/forge-h5，无需带 #/，开启推送时必填' },
  },
  { type: 'divider', label: '定时同步', props: { titlePlacement: 'left' }, span: 2, vIf: formData => isEnterprisePlatform(formData.platform) },
  {
    field: 'syncScheduleEnabled',
    label: '定时目录同步',
    type: 'switch',
    defaultValue: 0,
    checkedValue: 1,
    uncheckedValue: 0,
    span: 2,
    labelTip: `开启后系统会按下方 Cron 周期自动全量同步该连接的组织与成员，无需再去「定时任务」模块手工配置。
关闭后自动移除对应定时任务；连接停用时定时同步同样暂停。`,
    vIf: formData => isEnterprisePlatform(formData.platform),
    props: { },
  },
  {
    field: 'syncCron',
    label: '同步周期(Cron)',
    type: 'input',
    span: 2,
    vIf: formData => isEnterprisePlatform(formData.platform),
    labelTip: `标准 Quartz Cron 表达式（秒 分 时 日 月 周），开启定时同步时必填。
示例：0 0 2 * * ?（每天凌晨2点）、0 0/30 * * * ?（每30分钟）、0 0 1 * * ?（每天1点）。`,
    props: { placeholder: '如 0 0 2 * * ?（每天凌晨2点），开启定时同步时必填' },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    defaultValue: '1',
    labelTip: '停用后该连接的扫码登录、目录同步、消息推送全部暂停，配置保留可随时重新启用。',
    rules: [{ required: true, message: '请选择状态', trigger: 'change' }],
    props: { options: statusOptions.value, clearable: false },
  },
  {
    field: 'remark',
    label: '备注',
    type: 'textarea',
    span: 2,
    props: { placeholder: '备注说明', rows: 2 },
  },
])

function handleBeforeRenderDetail(data) {
  if (!data)
    return data
  if (data.status !== null && data.status !== undefined)
    data.status = String(data.status)
  if (data.defaultOrgId !== null && data.defaultOrgId !== undefined)
    data.defaultOrgId = String(data.defaultOrgId)
  // 待办推送开关：后端 Integer → 数值（switch 组件 checked/unchecked 值）
  data.todoPushEnabled = data.todoPushEnabled === 1 ? 1 : 0
  // 工作台免登开关：后端 Integer → 数值
  data.ssoWorkbenchEnabled = data.ssoWorkbenchEnabled === 1 ? 1 : 0
  // 定时同步开关：后端 Integer → 数值
  data.syncScheduleEnabled = data.syncScheduleEnabled === 1 ? 1 : 0
  // 默认角色：逗号分隔字符串 → 数值数组（供多选组件回显）
  if (data.defaultRoleIds && typeof data.defaultRoleIds === 'string') {
    data.defaultRoleIds = data.defaultRoleIds.split(',').map(id => Number(id.trim())).filter(Boolean)
  }
  else {
    data.defaultRoleIds = []
  }
  return data
}

/**
 * 连接编码自动生成：平台简称 + 4位随机后缀，如 wecom-a1b2
 */
const PLATFORM_CODE_PREFIX = {
  WECHAT_ENTERPRISE: 'wecom',
  DINGTALK: 'dingtalk',
  DINGTALK_ACCOUNT: 'dingtalk',
  FEISHU: 'feishu',
}

function genConnectionCode(platform) {
  const prefix = PLATFORM_CODE_PREFIX[platform] || String(platform || 'conn').toLowerCase().replace(/[^a-z0-9]+/g, '-')
  const suffix = Math.random().toString(36).slice(2, 6)
  return `${prefix}-${suffix}`
}

function handleBeforeSubmit(formData) {
  // 编码留空时自动生成，降低新建门槛
  if (!String(formData.connectionCode || '').trim())
    formData.connectionCode = genConnectionCode(formData.platform)
  // 企业型平台依赖企业ID换取凭据，纯 OAuth 登录平台无此概念，故按平台条件校验而非静态必填
  if (isEnterprisePlatform(formData.platform) && !formData.enterpriseId) {
    window.$message.warning('该平台为企业型连接，请填写外部企业ID')
    return false
  }
  if (formData.status !== null && formData.status !== undefined)
    formData.status = Number(formData.status)
  // 开启待办卡片推送时 H5 地址必填且须为 http/https 地址（企微 textcard 要求）
  formData.todoPushEnabled = formData.todoPushEnabled === 1 ? 1 : 0
  if (formData.todoPushEnabled === 1) {
    const h5Url = (formData.todoPushH5Url || '').trim()
    if (!h5Url) {
      window.$message.warning('开启待办卡片推送后，请填写待办H5访问地址')
      return false
    }
    if (!/^https?:\/\//i.test(h5Url)) {
      window.$message.warning('待办H5访问地址须以 http:// 或 https:// 开头')
      return false
    }
    // 地址栏复制来的地址常带 hash 路由前缀（如 /forge-h5/#/），
    // 与后端拼接的 /#/pages/todo-detail 叠加会产生两个 # 构成非法链接，这里统一存根路径
    const normalizedH5Url = h5Url.split('#')[0].replace(/\/+$/, '')
    if (!normalizedH5Url) {
      window.$message.warning('待办H5访问地址不能只填 # 路由部分，请填写H5应用根路径')
      return false
    }
    if (normalizedH5Url !== h5Url) {
      window.$message.info('已自动去除待办H5地址中的 # 路由部分，仅保留应用根路径')
    }
    formData.todoPushH5Url = normalizedH5Url
  }
  // 定时同步：开启时必须填写 Cron，且校验为 6/7 段表达式，避免脏配置导致建任务失败
  formData.syncScheduleEnabled = formData.syncScheduleEnabled === 1 ? 1 : 0
  if (formData.syncScheduleEnabled === 1) {
    const cron = (formData.syncCron || '').trim()
    if (!cron) {
      window.$message.warning('开启定时目录同步后，请填写同步周期 Cron 表达式')
      return false
    }
    const segments = cron.split(/\s+/)
    if (segments.length < 6 || segments.length > 7) {
      window.$message.warning('Cron 表达式格式不正确，应为 6 或 7 段（秒 分 时 日 月 周 [年]）')
      return false
    }
    formData.syncCron = cron
  }
  else {
    // 关闭时清空 Cron，避免残留旧值
    formData.syncCron = null
  }
  formData.defaultOrgId = formData.defaultOrgId ? Number(formData.defaultOrgId) : null
  // 默认角色：数值数组 → 逗号分隔字符串（后端存储格式）
  if (Array.isArray(formData.defaultRoleIds) && formData.defaultRoleIds.length > 0) {
    formData.defaultRoleIds = formData.defaultRoleIds.join(',')
  }
  else {
    formData.defaultRoleIds = null
  }
  // 企微留空时兜底官方地址，其余平台交由后端按平台默认地址处理
  if (!formData.apiBaseUrl && formData.platform === 'WECHAT_ENTERPRISE')
    formData.apiBaseUrl = WECOM_API_BASE_URL
  // 纯 OAuth 平台不存在自建/第三方应用形态，统一归一为仅登录连接类型
  if (!isEnterprisePlatform(formData.platform)) {
    formData.connectionType = 'OAUTH_ONLY'
    // OAuth 凭据在应用管理中配置，清除连接维度的残留旧值
    delete formData.clientId
    delete formData.clientSecret
    delete formData.redirectUri
    delete formData.scope
    delete formData.agentId
  }
  return formData
}

function handleDelete(row) {
  crudRef.value?.handleDelete(row)
}

/**
 * 启用/停用切换：VO 字段完整，直接用行数据构造保存入参（不含任何凭据字段）
 */
function handleToggleStatus(row) {
  const enabling = row.status !== 1
  window.$dialog.warning({
    title: enabling ? '确认启用' : '确认停用',
    content: enabling
      ? `确定要启用连接「${row.connectionName}」吗？启用后扫码登录、目录同步、消息推送恢复可用。`
      : `确定要停用连接「${row.connectionName}」吗？停用后扫码登录、目录同步、消息推送全部暂停，配置保留可随时重新启用。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await updateConnection({
          id: row.id,
          platform: row.platform,
          platformName: row.platformName,
          platformLogo: row.platformLogo,
          connectionCode: row.connectionCode,
          connectionName: row.connectionName,
          enterpriseId: row.enterpriseId,
          connectionType: row.connectionType,
          identityPolicy: row.identityPolicy,
          defaultRoleIds: row.defaultRoleIds,
          directoryAuthority: row.directoryAuthority,
          defaultOrgId: row.defaultOrgId,
          apiBaseUrl: row.apiBaseUrl,
          ssoWorkbenchEnabled: row.ssoWorkbenchEnabled,
          todoPushEnabled: row.todoPushEnabled,
          todoPushH5Url: row.todoPushH5Url,
          syncScheduleEnabled: row.syncScheduleEnabled,
          syncCron: row.syncCron,
          status: enabling ? 1 : 0,
          remark: row.remark,
        })
        if (res.code === 200) {
          window.$message.success(enabling ? '已启用' : '已停用')
          crudRef.value?.refresh()
        }
      }
      catch {
        window.$message.error(enabling ? '启用失败' : '停用失败')
      }
    },
  })
}

// ==================== 详情与应用管理 ====================

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const currentConnectionId = ref(null)
/** true=应用与能力管理（可增删改），false=只读详情 */
const manageMode = ref(false)

async function loadDetail(id) {
  detailLoading.value = true
  try {
    const res = await getConnectionDetail(id)
    if (res.code === 200)
      detail.value = res.data
  }
  catch {
    window.$message.error('获取连接详情失败')
  }
  finally {
    detailLoading.value = false
  }
}

function handleView(row) {
  manageMode.value = false
  currentConnectionId.value = row.id
  detail.value = null
  detailVisible.value = true
  loadDetail(row.id)
}

function handleManage(row) {
  manageMode.value = true
  currentConnectionId.value = row.id
  detail.value = null
  detailVisible.value = true
  loadDetail(row.id)
}

const appColumns = computed(() => {
  const columns = [
    { title: '应用编码', key: 'appCode', width: 120 },
    { title: '应用名称', key: 'appName', width: 140 },
    { title: 'AgentId', key: 'agentId', width: 100 },
    {
      title: 'Secret',
      key: 'secretMasked',
      width: 120,
      render: row => row.secretConfigured ? (row.secretMasked || '已配置') : '未配置',
    },
    {
      title: '回调凭据',
      key: 'callbackTokenConfigured',
      width: 100,
      render: row => (row.callbackTokenConfigured && row.encodingAesKeyConfigured) ? '已配置' : '未配置',
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: row => h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status ?? ''), size: 'small' }),
    },
  ]
  if (manageMode.value) {
    columns.push({
      title: '操作',
      key: 'action',
      width: 120,
      render: row => h('div', [
        h('a', {
          class: 'text-primary cursor-pointer hover:text-primary-hover mr-3',
          onClick: () => handleEditApp(row),
        }, '编辑'),
        h('a', {
          class: 'text-error cursor-pointer hover:text-error-hover',
          onClick: () => handleDeleteApp(row),
        }, '删除'),
      ]),
    })
  }
  return columns
})

const bindingColumns = computed(() => {
  const columns = [
    {
      title: '业务能力',
      key: 'capability',
      width: 140,
      render: row => h(DictTag, { dictType: 'sys_collab_capability', value: row.capability, size: 'small' }),
    },
    {
      title: '绑定应用',
      key: 'appConfigId',
      render: (row) => {
        const app = (detail.value?.apps || []).find(item => item.id === row.appConfigId)
        return app ? `${app.appName}（${app.appCode}）` : String(row.appConfigId ?? '-')
      },
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: row => h(DictTag, { dictType: 'sys_normal_disable', value: String(row.status ?? ''), size: 'small' }),
    },
  ]
  if (manageMode.value) {
    columns.push({
      title: '操作',
      key: 'action',
      width: 80,
      render: row => h('a', {
        class: 'text-error cursor-pointer hover:text-error-hover',
        onClick: () => handleUnbind(row),
      }, '解绑'),
    })
  }
  return columns
})

const appModalVisible = ref(false)
const appFormRef = ref(null)
const appSubmitLoading = ref(false)
const appForm = ref({})
const appStatusOptions = computed(() =>
  statusOptions.value.map(item => ({ ...item, value: Number(item.value) })),
)

const appFormRules = {
  appCode: [{ required: true, message: '请输入应用编码', trigger: 'blur' }],
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  clientId: [{ required: true, message: '请输入应用ID/Key', trigger: 'blur' }],
}

function handleAddApp() {
  appForm.value = { status: 1 }
  appModalVisible.value = true
}

function handleEditApp(row) {
  // Secret/Token/AESKey 不回显，留空提交即保留现值
  appForm.value = {
    id: row.id,
    appCode: row.appCode,
    appName: row.appName,
    clientId: row.clientId,
    agentId: row.agentId,
    secret: '',
    callbackToken: '',
    encodingAesKey: '',
    redirectUri: row.redirectUri,
    scope: row.scope,
    status: row.status,
    remark: row.remark,
  }
  appModalVisible.value = true
}

async function handleSubmitApp() {
  try {
    await appFormRef.value?.validate()
  }
  catch {
    return
  }
  appSubmitLoading.value = true
  try {
    const payload = { ...appForm.value }
    const res = payload.id
      ? await updateConnectionApp(currentConnectionId.value, payload)
      : await createConnectionApp(currentConnectionId.value, payload)
    if (res.code === 200) {
      window.$message.success('保存成功')
      appModalVisible.value = false
      loadDetail(currentConnectionId.value)
    }
  }
  catch {
    window.$message.error('保存应用失败')
  }
  finally {
    appSubmitLoading.value = false
  }
}

function handleDeleteApp(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定要删除应用「${row.appName}」吗？被能力绑定引用的应用无法删除。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await deleteConnectionApp(currentConnectionId.value, row.id)
        if (res.code === 200) {
          window.$message.success('删除成功')
          loadDetail(currentConnectionId.value)
        }
      }
      catch {
        window.$message.error('删除应用失败')
      }
    },
  })
}

// ==================== 能力绑定 ====================

const bindModalVisible = ref(false)
const bindSubmitLoading = ref(false)
const bindForm = ref({ capability: null, appConfigId: null })

const appSelectOptions = computed(() =>
  (detail.value?.apps || [])
    .filter(app => app.status === 1)
    .map(app => ({ label: `${app.appName}（${app.appCode}）`, value: app.id })),
)

function handleOpenBind() {
  bindForm.value = { capability: null, appConfigId: null }
  bindModalVisible.value = true
}

async function handleSubmitBind() {
  if (!bindForm.value.capability || !bindForm.value.appConfigId) {
    window.$message.warning('请选择能力与应用')
    return
  }
  bindSubmitLoading.value = true
  try {
    const res = await bindConnectionCapability(currentConnectionId.value, bindForm.value)
    if (res.code === 200) {
      window.$message.success('绑定成功')
      bindModalVisible.value = false
      loadDetail(currentConnectionId.value)
    }
  }
  catch {
    window.$message.error('绑定能力失败')
  }
  finally {
    bindSubmitLoading.value = false
  }
}

function handleUnbind(row) {
  window.$dialog.warning({
    title: '确认解绑',
    content: `确定要解绑「${getLabel('sys_collab_capability', row.capability)}」能力吗？解绑后该能力不可用。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await unbindConnectionCapability(currentConnectionId.value, row.capability)
        if (res.code === 200) {
          window.$message.success('解绑成功')
          loadDetail(currentConnectionId.value)
        }
      }
      catch {
        window.$message.error('解绑失败')
      }
    },
  })
}

// ==================== 连通测试与目录同步 ====================

const testModalVisible = ref(false)
const testLoading = ref(false)
const testCapability = ref('MESSAGE')
const testConnectionId = ref(null)

function handleOpenTest(row) {
  testConnectionId.value = row.id
  testCapability.value = 'MESSAGE'
  testModalVisible.value = true
}

async function handleSubmitTest() {
  testLoading.value = true
  try {
    const res = await testConnection(testConnectionId.value, testCapability.value)
    if (res.code === 200) {
      window.$message.success(res.data || '连通测试通过')
      testModalVisible.value = false
    }
  }
  catch {
    window.$message.error('连通测试失败，请检查凭据与能力绑定')
  }
  finally {
    testLoading.value = false
  }
}

function handleTriggerSync(row) {
  window.$dialog.warning({
    title: '触发全量同步',
    content: `确定要对「${row.connectionName}」执行全量目录同步吗？同步结果可在「同步批次」页查看。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await triggerConnectionSync(row.id, { syncType: 'FULL' })
        if (res.code === 200)
          window.$message.success('同步已完成，请到「同步批次」页查看结果')
      }
      catch {
        window.$message.error('触发同步失败')
      }
    },
  })
}

// ==================== 消息测试 ====================

const msgTestVisible = ref(false)
const msgTestLoading = ref(false)
const msgTestFormRef = ref(null)
const msgTestForm = ref({ connectionId: null, userIds: [], userLabels: [], title: '', content: '' })
const msgTestResult = ref(null)

const msgTestRules = {
  userIds: [{
    validator: () => {
      const ids = msgTestForm.value.userIds || []
      if (!ids.length)
        return new Error('请选择测试接收人')
      if (ids.length > 10)
        return new Error('测试接收人不能超过10人')
      return true
    },
    trigger: 'change',
  }],
  content: [{ required: true, message: '请输入消息正文', trigger: 'blur' }],
}

const msgTestResultColumns = [
  { title: '用户ID', key: 'userId', width: 90 },
  {
    title: '投递状态',
    key: 'status',
    width: 90,
    render: (row) => {
      const type = row.status === 'SENT' ? 'success' : row.status === 'SKIPPED' ? 'warning' : 'error'
      return h(NTag, { type, size: 'small' }, () => row.status)
    },
  },
  {
    title: '失败原因',
    key: 'errorMessage',
    render: row => row.errorMessage || (row.status === 'SENT' ? '-' : row.errorCode || '-'),
  },
]

function handleOpenMsgTest(row) {
  msgTestForm.value = {
    connectionId: row.id,
    userIds: [],
    userLabels: [],
    title: '协同消息推送测试',
    content: '',
  }
  msgTestResult.value = null
  msgTestVisible.value = true
}

async function handleSubmitMsgTest() {
  try {
    await msgTestFormRef.value?.validate()
  }
  catch {
    return
  }
  msgTestLoading.value = true
  msgTestResult.value = null
  try {
    const { connectionId, userIds, title, content } = msgTestForm.value
    const res = await sendTestMessage({ connectionId, userIds, title, content })
    if (res.code === 200) {
      msgTestResult.value = res.data
      const deliveries = res.data?.deliveries || []
      const sent = deliveries.filter(item => item.status === 'SENT').length
      if (sent === deliveries.length && sent > 0)
        window.$message.success('全部发送成功，请在企微客户端查看')
      else
        window.$message.warning(`发送完成：成功 ${sent}/${deliveries.length}，失败原因见下方明细`)
    }
  }
  catch {
    window.$message.error('消息测试发送失败')
  }
  finally {
    msgTestLoading.value = false
  }
}
</script>

<style scoped>
.collaboration-console-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 页头与页签同处一张白卡，避免标题裸露在灰底上 */
.console-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

/* 控制台页头 */
.console-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #f1f5f9;
  padding: 14px 16px;
}

.console-header__main {
  min-width: 0;
}

.console-header__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
  color: #0f172a;
}

.console-header__subtitle {
  margin: 4px 0 0;
  max-width: 720px;
  font-size: 12px;
  line-height: 18px;
  color: #94a3b8;
}

.console-guide-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  border: 1px solid currentcolor;
  font-size: 11px;
  line-height: 1;
}

/* 页签铺满剩余高度，并把确定高度继续传给面板内部的表格 */
.console-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.console-tabs :deep(.n-tabs-nav) {
  padding: 0 12px;
}

.console-tabs :deep(.n-tabs-pane-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.console-tabs :deep(.console-tab-pane) {
  height: 100%;
  min-height: 0;
  padding: 12px;
}

/* 配置指南 */
.guide-steps {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.guide-step {
  display: flex;
  gap: 12px;
}

.guide-step__index {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #eff6ff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}

.guide-step__title {
  font-size: 14px;
  font-weight: 500;
  line-height: 24px;
  color: var(--text-color-1, #1f2937);
}

.guide-step__desc {
  margin-top: 2px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-color-3, #6b7280);
}

.guide-section-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-color-1, #1f2937);
}

.guide-table {
  font-size: 13px;
}

.detail-content {
  padding: 4px 0;
}
</style>
