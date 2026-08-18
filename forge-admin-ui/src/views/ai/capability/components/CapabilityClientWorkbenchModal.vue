<template>
  <n-modal
    :show="show"
    preset="card"
    class="client-workbench-modal"
    :mask-closable="false"
    @update:show="emit('update:show', $event)"
  >
    <template #header>
      <div class="workbench-title">
        <strong>客户端工作台</strong>
        <span v-if="client">{{ client.clientName }} · {{ client.clientCode }} · AppId {{ client.id }}</span>
      </div>
    </template>

    <n-tabs v-if="client" v-model:value="activeTab" type="line" animated @update:value="handleTabChange">
      <n-tab-pane name="overview" tab="概览与凭据">
        <n-alert type="info" class="tab-alert">
          一个客户端就是一个外围系统接入身份。先准备凭据，再授权能力，最后在调用日志中排查真实请求。
        </n-alert>
        <div class="overview-grid">
          <div><span>客户端名称</span><strong>{{ client.clientName }}</strong></div>
          <div><span>客户端编码</span><strong>{{ client.clientCode }}</strong></div>
          <div><span>AppId</span><strong>{{ client.id }}</strong></div>
          <div><span>状态</span><strong>{{ dictLabel(clientStatusOptions, client.status) }}</strong></div>
          <div><span>主体模式</span><strong>{{ dictLabel(actorModeOptions, client.actorMode) }}</strong></div>
          <div><span>认证方式</span><strong>{{ authModeText }}</strong></div>
          <div><span>服务账号 ID</span><strong>{{ client.serviceUserId || '-' }}</strong></div>
          <div><span>生效组织 ID</span><strong>{{ client.activeOrgId || '-' }}</strong></div>
          <div><span>有效期</span><strong>{{ client.expiresAt || '长期有效' }}</strong></div>
          <div><span>最近调用</span><strong>{{ client.lastUsedAt || '尚未调用' }}</strong></div>
        </div>
        <section class="credential-section">
          <div class="section-heading">
            <div>
              <h3>凭据管理</h3>
              <p>Secret、Signing Key 和 RSA 私钥只在生成或轮换后展示一次，平台不会提供明文反查。</p>
            </div>
          </div>
          <n-space>
            <NButton v-if="canRotate && client.status === 'ENABLED'" type="primary" secondary @click="emit('rotate-secret', client)">
              轮换 Client Secret
            </NButton>
            <NButton
              v-if="canEdit && signatureEnabled && client.status === 'ENABLED'"
              type="primary"
              secondary
              @click="emit('rotate-signing-key', client)"
            >
              轮换 Signing Key
            </NButton>
            <NButton
              v-if="canEdit && userDelegationEnabled && client.status === 'ENABLED'"
              type="primary"
              secondary
              @click="emit('configure-identity', client)"
            >
              用户身份与 RSA 私钥
            </NButton>
            <NButton v-if="canRevoke && client.status === 'ENABLED'" type="error" secondary @click="emit('revoke', client)">
              吊销客户端
            </NButton>
          </n-space>
        </section>
      </n-tab-pane>

      <n-tab-pane v-if="canGrantQuery" name="grants" tab="能力授权">
        <div class="section-heading toolbar-heading">
          <div>
            <h3>这个客户端可以调用哪些能力</h3>
            <p>授权默认锚定当前能力版本；必填字段会自动保留，客户端只能进一步收窄字段范围。</p>
          </div>
          <NButton v-if="canGrant" type="primary" @click="openGrantModal()">
            新增授权
          </NButton>
        </div>
        <n-data-table
          :columns="grantColumns"
          :data="grantRows"
          :loading="grantLoading"
          :pagination="grantPagination"
          :row-key="row => row.id"
          remote
          :bordered="false"
          @update:page="changeGrantPage"
          @update:page-size="changeGrantPageSize"
        />
      </n-tab-pane>

      <n-tab-pane name="mappings" tab="外围用户映射">
        <n-alert type="warning" class="tab-alert">
          用户委托能力必须落实到真实 Forge 用户。默认采用管理员预绑定；只有外围系统能可靠验证手机号并保护客户端私钥时，才启用手机号唯一匹配。
        </n-alert>
        <div class="identity-card">
          <div>
            <span>当前映射规则</span>
            <strong>{{ mappingModeLabel }}</strong>
            <p>在这里可以生成或轮换 RSA 私钥、切换映射规则、分页查询及维护外围用户映射。</p>
          </div>
          <NButton
            v-if="canEdit && userDelegationEnabled && client.status === 'ENABLED'"
            type="primary"
            @click="emit('configure-identity', client)"
          >
            配置身份映射
          </NButton>
        </div>
      </n-tab-pane>

      <n-tab-pane v-if="canLogQuery" name="logs" tab="调用日志">
        <div class="section-heading toolbar-heading">
          <div>
            <h3>这个客户端的真实调用记录</h3>
            <p>可查看实际调用用户、失败阶段和脱敏错误摘要；请求 Body、Token 和密钥不会写入审计日志。</p>
          </div>
        </div>
        <div class="log-filter-bar">
          <n-input
            v-model:value="logFilters.requestId"
            clearable
            placeholder="请求 ID"
            @keyup.enter="searchLogs"
          />
          <n-input
            v-model:value="logFilters.capabilityKeyword"
            clearable
            placeholder="能力名称或编码"
            @keyup.enter="searchLogs"
          />
          <n-input
            v-model:value="logFilters.actorKeyword"
            clearable
            placeholder="用户 ID、用户名或姓名"
            @keyup.enter="searchLogs"
          />
          <n-space :wrap="false">
            <NButton type="primary" :loading="logLoading" @click="searchLogs">
              查询
            </NButton>
            <NButton :disabled="logLoading" @click="resetLogFilters">
              重置
            </NButton>
          </n-space>
        </div>
        <n-data-table
          :columns="logColumns"
          :data="logRows"
          :loading="logLoading"
          :pagination="logPagination"
          :row-key="row => row.id"
          :scroll-x="1490"
          remote
          :bordered="false"
          size="small"
          @update:page="changeLogPage"
          @update:page-size="changeLogPageSize"
        />
      </n-tab-pane>
    </n-tabs>

    <template #footer>
      <n-space justify="end">
        <NButton @click="emit('update:show', false)">
          关闭
        </NButton>
      </n-space>
    </template>

    <n-modal v-model:show="grantVisible" preset="card" :title="editingGrantId ? '调整客户端授权' : '为当前客户端新增授权'" style="width: min(640px, calc(100vw - 32px))">
      <n-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-placement="left" label-width="100px">
        <n-form-item label="客户端">
          <n-input :value="`${client?.clientName || '-'}（${client?.clientCode || '-'}）`" disabled />
        </n-form-item>
        <n-form-item label="能力" path="capabilityId">
          <n-select
            v-model:value="grantForm.capabilityId"
            :options="capabilityOptions"
            :loading="grantOptionLoading"
            :disabled="!!editingGrantId"
            filterable
            placeholder="请选择要开放给该客户端的能力"
            @update:value="handleCapabilityChange"
          />
        </n-form-item>
        <n-form-item label="版本策略" path="versionStrategy">
          <n-select v-model:value="grantForm.versionStrategy" :options="versionStrategyOptions" />
        </n-form-item>
        <n-form-item label="基准版本" path="fixedVersion">
          <n-input v-model:value="grantForm.fixedVersion" placeholder="默认使用能力当前版本" />
        </n-form-item>
        <n-form-item v-if="grantFieldOptions.length" label="允许字段" path="allowedFields">
          <n-select v-model:value="grantForm.allowedFields" :options="grantFieldOptions" multiple filterable />
        </n-form-item>
        <n-form-item v-if="grantOperationOptions.length" label="允许操作" path="allowedOperations">
          <n-select v-model:value="grantForm.allowedOperations" :options="grantOperationOptions" multiple />
        </n-form-item>
        <n-form-item label="过期时间">
          <n-date-picker v-model:value="grantForm.expiresAt" type="datetime" clearable class="w-full" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <NButton @click="grantVisible = false">
            取消
          </NButton>
          <NButton type="primary" :loading="grantSubmitting" @click="submitGrant">
            {{ editingGrantId ? '保存调整' : '确认授权' }}
          </NButton>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="logDetailVisible" preset="card" title="调用日志详情" style="width: min(820px, calc(100vw - 32px))">
      <n-spin :show="logDetailLoading">
        <template v-if="logDetail">
          <n-alert :type="logDetail.resultStatus === 'SUCCESS' ? 'success' : 'error'" class="tab-alert">
            {{ logDetail.errorMessage || logDetail.resultCode || '无额外错误摘要' }}
          </n-alert>
          <div class="overview-grid detail-grid">
            <div><span>请求 ID</span><strong>{{ logDetail.requestId || '-' }}</strong></div>
            <div><span>调用时间</span><strong>{{ logDetail.createTime || '-' }}</strong></div>
            <div><span>能力</span><strong>{{ capabilityDisplay(logDetail) }} · v{{ logDetail.capabilityVersion || '-' }}</strong></div>
            <div><span>实际调用用户</span><strong>{{ userLabel(logDetail) }}</strong></div>
            <div><span>主体类型</span><strong>{{ dictLabel(actorTypeOptions, logDetail.actorType) }}</strong></div>
            <div><span>生效组织 ID</span><strong>{{ logDetail.activeOrgId || '-' }}</strong></div>
            <div><span>结果码</span><strong>{{ logDetail.resultCode || '-' }}</strong></div>
            <div><span>错误码</span><strong>{{ logDetail.errorCode || '-' }}</strong></div>
            <div><span>失败阶段</span><strong>{{ failureStageLabel(logDetail.failureStage, logDetail.resultStatus) }}</strong></div>
            <div><span>Schema 路径</span><strong>{{ logDetail.schemaPath || '-' }}</strong></div>
            <div><span>Trace ID</span><strong>{{ logDetail.traceId || '-' }}</strong></div>
            <div><span>耗时</span><strong>{{ logDetail.durationMs ?? '-' }} ms</strong></div>
          </div>
          <pre class="error-summary">{{ logDetail.errorMessage || '无详细错误摘要' }}</pre>
        </template>
      </n-spin>
    </n-modal>
  </n-modal>
</template>

<script setup>
import { NButton, NTag } from 'naive-ui'
import { computed, h, reactive, ref, watch } from 'vue'
import {
  addCapabilityGrant,
  getCapabilityGrantOptions,
  getCapabilityGrantPage,
  getCapabilityInvocationDetail,
  getCapabilityInvocationPage,
  revokeCapabilityGrant,
  updateCapabilityGrant,
  useCurrentCapabilityGrantVersion,
} from '@/api/ai/capability'
import { useDict } from '@/composables'
import { formatDateTime } from '@/utils'

const props = defineProps({
  show: Boolean,
  client: { type: Object, default: null },
  canRotate: Boolean,
  canEdit: Boolean,
  canRevoke: Boolean,
  canGrant: Boolean,
  canGrantQuery: Boolean,
  canGrantRevoke: Boolean,
  canLogQuery: Boolean,
})

const emit = defineEmits([
  'update:show',
  'rotate-secret',
  'rotate-signing-key',
  'configure-identity',
  'revoke',
])

const { dict, reload: reloadWorkbenchDicts } = useDict(
  'ai_capability_client_actor_mode',
  'ai_capability_client_status',
  'ai_capability_auth_mode',
  'ai_capability_user_mapping_mode',
  'ai_capability_version_strategy',
  'ai_capability_grant_status',
  'ai_capability_flow_operation',
  'ai_capability_actor_type',
)

const actorModeOptions = computed(() => dict.value.ai_capability_client_actor_mode || [])
const clientStatusOptions = computed(() => dict.value.ai_capability_client_status || [])
const mappingModeOptions = computed(() => dict.value.ai_capability_user_mapping_mode || [])
const versionStrategyOptions = computed(() => dict.value.ai_capability_version_strategy || [])
const grantStatusOptions = computed(() => dict.value.ai_capability_grant_status || [])
const actorTypeOptions = computed(() => dict.value.ai_capability_actor_type || [])
const flowOperationOptions = computed(() => dict.value.ai_capability_flow_operation || [])
const activeTab = ref('overview')

const signatureEnabled = computed(() => authModes.value.includes('SIGNATURE'))
const userDelegationEnabled = computed(() => Number(props.client?.oauthEnabled) === 1
  && ['USER_DELEGATION', 'HYBRID'].includes(props.client?.actorMode))
const authModes = computed(() => String(props.client?.authModes || '').split(',').filter(Boolean))
const authModeText = computed(() => authModes.value
  .map(mode => dictLabel(dict.value.ai_capability_auth_mode || [], mode))
  .join(' / ') || '-')
const mappingModeLabel = computed(() => dictLabel(
  mappingModeOptions.value,
  props.client?.userAssertionMappingMode || 'PREBOUND',
))

watch(() => props.show, (visible) => {
  if (!visible)
    return
  activeTab.value = 'overview'
  grantRows.value = []
  logRows.value = []
  Object.assign(logFilters, { requestId: '', capabilityKeyword: '', actorKeyword: '' })
})

function handleTabChange(tab) {
  if (tab === 'grants')
    loadGrants()
  if (tab === 'logs')
    loadLogs()
}

function dictLabel(options, value) {
  return options.find(item => String(item.value) === String(value))?.label || value || '-'
}

// ===== 授权 =====
const grantRows = ref([])
const grantLoading = ref(false)
const grantOptions = ref({ clients: [], capabilities: [] })
const grantPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
})
const capabilityMap = computed(() => new Map((grantOptions.value.capabilities || [])
  .map(item => [String(item.id), item])))

const grantColumns = computed(() => [
  {
    title: '能力',
    key: 'capabilityId',
    minWidth: 220,
    render: row => capabilityName(row.capabilityId),
  },
  { title: '版本策略', key: 'versionStrategy', width: 120, render: row => dictLabel(versionStrategyOptions.value, row.versionStrategy) },
  { title: '基准版本', key: 'fixedVersion', width: 100, render: row => row.fixedVersion || '-' },
  { title: '状态', key: 'status', width: 90, render: row => dictLabel(grantStatusOptions.value, row.status) },
  { title: '过期时间', key: 'expiresAt', width: 160, render: row => row.expiresAt || '长期有效' },
  {
    title: '操作',
    key: 'action',
    width: 230,
    fixed: 'right',
    render: row => h('div', { class: 'table-actions' }, [
      row.status === 'ENABLED' && grantVersionUpgradeAvailable(row)
        ? h(NButton, { text: true, type: 'primary', onClick: () => switchGrantVersion(row) }, { default: () => '使用当前版本' })
        : null,
      row.status === 'ENABLED' && props.canGrant
        ? h(NButton, { text: true, type: 'primary', onClick: () => openGrantModal(row) }, { default: () => '调整' })
        : null,
      row.status === 'ENABLED' && props.canGrantRevoke
        ? h(NButton, { text: true, type: 'error', onClick: () => revokeGrant(row) }, { default: () => '撤销' })
        : null,
    ]),
  },
])

async function ensureGrantOptions() {
  const res = await getCapabilityGrantOptions()
  grantOptions.value = res.data || { clients: [], capabilities: [] }
}

async function loadGrants() {
  if (!props.client?.id)
    return
  grantLoading.value = true
  try {
    await ensureGrantOptions()
    const res = await getCapabilityGrantPage({
      pageNum: grantPagination.page,
      pageSize: grantPagination.pageSize,
      clientId: props.client.id,
    })
    grantRows.value = res.data?.records || []
    grantPagination.itemCount = Number(res.data?.total || 0)
  }
  catch (error) {
    window.$message.error(error?.message || '客户端授权加载失败')
  }
  finally {
    grantLoading.value = false
  }
}

function changeGrantPage(page) {
  grantPagination.page = page
  loadGrants()
}

function changeGrantPageSize(pageSize) {
  grantPagination.pageSize = pageSize
  grantPagination.page = 1
  loadGrants()
}

function capabilityName(capabilityId) {
  const capability = capabilityMap.value.get(String(capabilityId))
  return capability ? `${capability.capabilityName}（${capability.capabilityCode}）` : `能力 #${capabilityId}`
}

function grantVersionUpgradeAvailable(row) {
  const capability = capabilityMap.value.get(String(row.capabilityId))
  return capability?.currentVersion
    && String(capability.currentVersion) !== String(row.fixedVersion || '')
}

function revokeGrant(row) {
  window.$dialog.warning({
    title: '撤销能力授权',
    content: `撤销后当前客户端将不能再调用「${capabilityName(row.capabilityId)}」。是否继续？`,
    positiveText: '确认撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await revokeCapabilityGrant(row.id)
      if (res.code === 200) {
        window.$message.success('授权已撤销')
        await loadGrants()
      }
    },
  })
}

async function switchGrantVersion(row) {
  const capability = capabilityMap.value.get(String(row.capabilityId))
  if (!capability?.currentVersion)
    return
  const res = await useCurrentCapabilityGrantVersion(row.id)
  if (res.code === 200) {
    window.$message.success(`授权基准已切换到 v${capability.currentVersion}`)
    await loadGrants()
  }
}

const grantVisible = ref(false)
const editingGrantId = ref(null)
const grantSubmitting = ref(false)
const grantOptionLoading = ref(false)
const grantFormRef = ref(null)
const grantForm = reactive({
  capabilityId: null,
  versionStrategy: null,
  fixedVersion: '',
  allowedFields: [],
  allowedOperations: [],
  expiresAt: null,
})
const selectedCapability = computed(() => (grantOptions.value.capabilities || [])
  .find(item => item.id === grantForm.capabilityId))
const capabilityOptions = computed(() => (grantOptions.value.capabilities || [])
  .filter(item => item.publishStatus === 'PUBLISHED' && item.enabled !== 0)
  .map((item) => {
    const unavailableReason = capabilityUnavailableReason(item)
    return {
      label: `${item.capabilityName}（${item.capabilityCode}）· v${item.currentVersion || '-'}${unavailableReason ? ` · ${unavailableReason}` : ''}`,
      value: item.id,
      disabled: !!unavailableReason,
    }
  }))
const grantFieldOptions = computed(() => {
  const metaMap = new Map((selectedCapability.value?.fields || []).map(item => [item.fieldCode, item]))
  return (selectedCapability.value?.allowedFields || []).map((fieldCode) => {
    const meta = metaMap.get(fieldCode)
    const required = meta?.required || selectedCapability.value?.requiredFields?.includes(fieldCode)
    return {
      label: `${meta?.fieldLabel || '未命名字段'}${required ? '（必填）' : ''}`,
      value: fieldCode,
      disabled: required,
    }
  })
})
const grantOperationOptions = computed(() => (selectedCapability.value?.allowedOperations || [])
  .map(operation => ({ label: dictLabel(flowOperationOptions.value, operation), value: operation })))
const grantRules = {
  capabilityId: {
    trigger: 'change',
    validator: (_rule, value) => value != null && String(value).trim()
      ? true
      : new Error('请选择能力'),
  },
  versionStrategy: { required: true, message: '请选择版本策略', trigger: 'change' },
  fixedVersion: { required: true, message: '请输入基准版本', trigger: 'blur' },
}

async function openGrantModal(row = null) {
  grantVisible.value = true
  editingGrantId.value = row?.id || null
  grantOptionLoading.value = true
  try {
    await Promise.all([ensureGrantOptions(), reloadWorkbenchDicts()])
    if (row) {
      const fieldPolicy = parseFieldPolicy(row.fieldPolicy)
      const capability = (grantOptions.value.capabilities || [])
        .find(item => item.id === row.capabilityId)
      Object.assign(grantForm, {
        capabilityId: row.capabilityId,
        versionStrategy: row.versionStrategy,
        fixedVersion: row.fixedVersion || '',
        allowedFields: [...new Set([
          ...(Array.isArray(fieldPolicy.allowedFields) ? fieldPolicy.allowedFields : []),
          ...(capability?.requiredFields || []),
        ])],
        allowedOperations: Array.isArray(fieldPolicy.allowedOperations) ? [...fieldPolicy.allowedOperations] : [],
        expiresAt: parseDateTimeValue(row.expiresAt),
      })
    }
    else {
      Object.assign(grantForm, {
        capabilityId: null,
        versionStrategy: versionStrategyOptions.value.find(item => item.isDefault === 'Y')?.value
          || versionStrategyOptions.value[0]?.value
          || null,
        fixedVersion: '',
        allowedFields: [],
        allowedOperations: [],
        expiresAt: null,
      })
    }
  }
  catch (error) {
    grantVisible.value = false
    window.$message.error(error?.message || '授权候选能力加载失败')
  }
  finally {
    grantOptionLoading.value = false
  }
}

function capabilityUnavailableReason(capability) {
  if (capability.riskLevel === 'HIGH')
    return '高风险能力暂不可授权'
  if (capability.behavior === 'READ_ONLY' || capability.sourceType === 'SYSTEM_SERVICE')
    return ''
  if (capability.sourceType === 'BUSINESS_ACTION')
    return capability.allowedFields?.length ? '' : '缺少允许字段'
  if (capability.sourceType === 'FLOW_ACTION') {
    return capability.allowedOperations?.length
      && (!capability.allowedOperations.includes('SUBMIT') || capability.allowedFields?.length)
      ? ''
      : '缺少允许操作或申请字段'
  }
  return '当前类型不可授权'
}

function handleCapabilityChange(capabilityId) {
  const capability = (grantOptions.value.capabilities || []).find(item => item.id === capabilityId)
  grantForm.fixedVersion = capability?.currentVersion || ''
  grantForm.allowedFields = [...(capability?.allowedFields || [])]
  grantForm.allowedOperations = [...(capability?.allowedOperations || [])]
}

function parseFieldPolicy(value) {
  if (value && typeof value === 'object')
    return value
  if (typeof value !== 'string' || !value.trim())
    return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  }
  catch {
    return {}
  }
}

function parseDateTimeValue(value) {
  if (!value)
    return null
  const timestamp = new Date(String(value).replace(' ', 'T')).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

async function submitGrant() {
  try {
    await grantFormRef.value?.validate()
  }
  catch {
    return
  }
  const capability = selectedCapability.value
  if (!capability)
    return
  if ((capability.allowedFields || []).length && grantForm.allowedFields.length === 0) {
    window.$message.error('请至少保留一个允许字段')
    return
  }
  if ((capability.allowedOperations || []).length && grantForm.allowedOperations.length === 0) {
    window.$message.error('请至少保留一个允许操作')
    return
  }
  grantSubmitting.value = true
  try {
    const fieldPolicy = capability.sourceType === 'BUSINESS_ACTION'
      ? { allowedFields: grantForm.allowedFields }
      : capability.sourceType === 'FLOW_ACTION'
        ? {
            allowedOperations: grantForm.allowedOperations,
            ...((capability.allowedFields || []).length ? { allowedFields: grantForm.allowedFields } : {}),
          }
        : null
    const payload = {
      versionStrategy: grantForm.versionStrategy,
      fixedVersion: grantForm.fixedVersion,
      fieldPolicy,
      expiresAt: grantForm.expiresAt ? formatDateTime(grantForm.expiresAt) : null,
    }
    const res = editingGrantId.value
      ? await updateCapabilityGrant(editingGrantId.value, payload)
      : await addCapabilityGrant({
          clientId: props.client.id,
          capabilityId: grantForm.capabilityId,
          ...payload,
        })
    if (res.code === 200) {
      window.$message.success(editingGrantId.value ? '授权已调整' : '授权成功')
      grantVisible.value = false
      editingGrantId.value = null
      grantPagination.page = 1
      await loadGrants()
    }
  }
  finally {
    grantSubmitting.value = false
  }
}

// ===== 调用日志 =====
const logRows = ref([])
const logLoading = ref(false)
const logFilters = reactive({
  requestId: '',
  capabilityKeyword: '',
  actorKeyword: '',
})
const logPagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
})
const logColumns = [
  { title: '请求 ID', key: 'requestId', width: 210, fixed: 'left', ellipsis: { tooltip: true } },
  { title: '能力', key: 'capabilityCode', width: 250, ellipsis: { tooltip: true }, render: capabilityDisplay },
  { title: '调用用户', key: 'actorUserId', width: 190, ellipsis: { tooltip: true }, render: userLabel },
  {
    title: '结果',
    key: 'resultStatus',
    width: 95,
    render: row => h(NTag, { size: 'small', type: row.resultStatus === 'SUCCESS' ? 'success' : 'error' }, { default: () => resultStatusLabel(row.resultStatus) }),
  },
  { title: '失败阶段', key: 'failureStage', width: 150, render: row => failureStageLabel(row.failureStage, row.resultStatus) },
  { title: '错误摘要', key: 'errorMessage', width: 250, ellipsis: { tooltip: true }, render: row => row.errorMessage || row.resultCode || '-' },
  { title: '耗时', key: 'durationMs', width: 90, render: row => row.durationMs == null ? '-' : `${row.durationMs} ms` },
  { title: '调用时间', key: 'createTime', width: 170, fixed: 'right', render: row => formatInvocationTime(row.createTime) },
  {
    title: '操作',
    key: 'action',
    width: 70,
    fixed: 'right',
    render: row => h(NButton, { text: true, type: 'primary', onClick: () => openLogDetail(row) }, { default: () => '详情' }),
  },
]

async function loadLogs() {
  if (!props.client?.id)
    return
  logLoading.value = true
  try {
    const res = await getCapabilityInvocationPage({
      pageNum: logPagination.page,
      pageSize: logPagination.pageSize,
      clientId: props.client.id,
      requestId: normalizeFilter(logFilters.requestId),
      capabilityKeyword: normalizeFilter(logFilters.capabilityKeyword),
      actorKeyword: normalizeFilter(logFilters.actorKeyword),
    })
    logRows.value = res.data?.records || []
    logPagination.itemCount = Number(res.data?.total || 0)
  }
  catch (error) {
    window.$message.error(error?.message || '客户端调用日志加载失败')
  }
  finally {
    logLoading.value = false
  }
}

function searchLogs() {
  logPagination.page = 1
  loadLogs()
}

function resetLogFilters() {
  Object.assign(logFilters, { requestId: '', capabilityKeyword: '', actorKeyword: '' })
  searchLogs()
}

function changeLogPage(page) {
  logPagination.page = page
  loadLogs()
}

function changeLogPageSize(pageSize) {
  logPagination.pageSize = pageSize
  logPagination.page = 1
  loadLogs()
}

const logDetailVisible = ref(false)
const logDetailLoading = ref(false)
const logDetail = ref(null)

async function openLogDetail(row) {
  logDetailVisible.value = true
  logDetailLoading.value = true
  logDetail.value = null
  try {
    const res = await getCapabilityInvocationDetail(row.id)
    logDetail.value = res.data || null
  }
  catch (error) {
    window.$message.error(error?.message || '调用日志详情加载失败')
    logDetailVisible.value = false
  }
  finally {
    logDetailLoading.value = false
  }
}

function userLabel(row) {
  if (!row?.actorUserId)
    return '-'
  const name = row.actorRealName || row.actorUsername || `用户 #${row.actorUserId}`
  return `${name}${row.actorUsername && row.actorRealName ? `（${row.actorUsername}）` : ''} · ID ${row.actorUserId}`
}

function capabilityDisplay(row) {
  if (!row)
    return '-'
  if (row.capabilityName)
    return `${row.capabilityName}（${row.capabilityCode || '-'}）`
  return row.capabilityCode || '-'
}

function normalizeFilter(value) {
  const normalized = String(value || '').trim()
  return normalized || undefined
}

function formatInvocationTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

function failureStageLabel(value, resultStatus) {
  if (!value)
    return ['ERROR', 'FAILED'].includes(resultStatus) ? '未记录' : '-'
  return {
    SCOPE_AUTHORIZATION: '调用范围校验',
    GRANT_RESOLUTION: '客户端授权解析',
    CAPABILITY_RESOLUTION: '能力版本解析',
    ACTOR_AUTHORIZATION: '主体类型校验',
    RBAC_AUTHORIZATION: '用户权限校验',
    RATE_LIMIT: '调用频率限制',
    AUTHENTICATION: '身份认证',
    AUTHORIZATION: '能力授权',
    INPUT_PREPARATION: '入参准备',
    INPUT_SCHEMA_VALIDATION: '入参校验',
    POLICY_VALIDATION: '能力策略校验',
    IDEMPOTENCY: '幂等校验',
    AUDIT_RESERVATION: '审计预留',
    ADAPTER_RESOLUTION: '适配器解析',
    ADAPTER_EXECUTION: '业务执行',
    OUTPUT_SCHEMA_VALIDATION: '返回校验',
    AUDIT_FINALIZATION: '审计完成',
    AUDIT: '审计记录',
  }[value] || value
}

function resultStatusLabel(value) {
  return {
    SUCCESS: '成功',
    ERROR: '失败',
    FAILED: '失败',
    PENDING_APPROVAL: '等待审批',
  }[value] || value || '-'
}
</script>

<style scoped>
.workbench-title strong,
.workbench-title span {
  display: block;
}

.workbench-title strong {
  color: var(--text-primary);
  font-size: 17px;
}

.workbench-title span {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.tab-alert {
  margin-bottom: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--border-light);
  border-left: 1px solid var(--border-light);
}

.overview-grid > div {
  min-width: 0;
  padding: 12px 14px;
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.overview-grid span,
.overview-grid strong {
  display: block;
}

.overview-grid span {
  margin-bottom: 5px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.overview-grid strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.credential-section {
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 15px;
}

.section-heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.section-heading p {
  margin: 5px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.toolbar-heading {
  align-items: center;
}

.log-filter-bar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) minmax(200px, 1fr) auto;
  gap: 10px;
  margin-bottom: 14px;
}

.identity-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.identity-card span,
.identity-card strong {
  display: block;
}

.identity-card span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.identity-card strong {
  margin-top: 5px;
  color: var(--text-primary);
  font-size: 16px;
}

.identity-card p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.w-full {
  width: 100%;
}

.error-summary {
  max-height: 220px;
  overflow: auto;
  margin: 16px 0 0;
  padding: 14px;
  border-radius: 6px;
  background: var(--bg-secondary);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

:deep(.table-actions) {
  display: flex;
  gap: 10px;
}

@media (max-width: 720px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-heading,
  .identity-card {
    align-items: stretch;
    flex-direction: column;
  }

  .log-filter-bar {
    grid-template-columns: 1fr;
  }
}

:global(.client-workbench-modal) {
  width: min(1080px, calc(100vw - 32px));
}

:global(.client-workbench-modal > .n-card__content) {
  min-height: 520px;
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}
</style>
