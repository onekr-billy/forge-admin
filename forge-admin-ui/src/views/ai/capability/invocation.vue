<template>
  <div class="capability-invocation-page">
    <CapabilityPageHeader title="调用记录" description="按能力、接入系统和请求 ID 追踪调用结果；日志仅包含脱敏摘要。" active="invocation" />
    <AiCrudPage
      ref="crudRef"
      :api-config="{
        list: 'get@/ai/capability/invocation/page',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      row-key="id"
      :hide-add="true"
      :hide-selection="true"
      :hide-batch-delete="true"
      :scroll-x="1770"
      :show-render-mode-switch="false"
      class="platform-list"
    />

    <n-modal
      v-model:show="detailVisible"
      preset="card"
      title="调用日志详情"
      style="width: min(860px, calc(100vw - 32px))"
    >
      <n-spin :show="detailLoading">
        <template v-if="detail">
          <n-alert :type="detail.resultStatus === 'SUCCESS' ? 'success' : 'error'" class="detail-alert">
            <template #header>
              {{ detail.resultStatus === 'SUCCESS' ? '调用成功' : '调用失败' }}
            </template>
            {{ detail.errorMessage || detail.resultCode || '平台未记录额外错误摘要' }}
          </n-alert>
          <div class="detail-grid">
            <div><span>请求 ID</span><strong>{{ detail.requestId || '-' }}</strong></div>
            <div><span>调用时间</span><strong>{{ detail.createTime || '-' }}</strong></div>
            <div><span>客户端</span><strong>{{ detail.clientCode || '-' }}（{{ detail.clientId || '-' }}）</strong></div>
            <div><span>能力与版本</span><strong>{{ capabilityDisplay(detail) }} · v{{ detail.capabilityVersion || '-' }}</strong></div>
            <div><span>实际调用用户</span><strong>{{ actorUserLabel(detail) }}</strong></div>
            <div><span>客户端服务账号</span><strong>{{ serviceUserLabel(detail) }}</strong></div>
            <div><span>主体类型</span><strong>{{ actorTypeLabel(detail.actorType) }}</strong></div>
            <div><span>生效组织 ID</span><strong>{{ detail.activeOrgId || '-' }}</strong></div>
            <div><span>结果码</span><strong>{{ detail.resultCode || '-' }}</strong></div>
            <div><span>错误码</span><strong>{{ detail.errorCode || '-' }}</strong></div>
            <div><span>失败阶段</span><strong>{{ failureStageLabel(detail.failureStage, detail.resultStatus) }}</strong></div>
            <div><span>Schema 路径</span><strong>{{ detail.schemaPath || '-' }}</strong></div>
            <div><span>Trace ID</span><strong>{{ detail.traceId || '-' }}</strong></div>
            <div><span>耗时</span><strong>{{ detail.durationMs ?? '-' }} ms</strong></div>
          </div>
          <div class="error-summary">
            <span>详细错误摘要</span>
            <pre>{{ detail.errorMessage || '无' }}</pre>
          </div>
          <n-alert type="info" class="security-note">
            为避免泄露 Token、密钥和业务敏感数据，审计日志只保存脱敏后的错误摘要，不保存完整请求 Body。
          </n-alert>
        </template>
      </n-spin>
    </n-modal>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { getCapabilityInvocationDetail } from '@/api/ai/capability'
import { AiCrudPage } from '@/components/ai-form'
import { useDict } from '@/composables'
import CapabilityPageHeader from './components/CapabilityPageHeader.vue'

defineOptions({ name: 'CapabilityInvocation' })

const { dict } = useDict('ai_capability_actor_type')

const actorTypeOptions = computed(() => dict.value.ai_capability_actor_type || [])

const crudRef = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

// resultStatus 无字典（网关内部状态机取值），按状态着色展示
const RESULT_STATUS_TYPE = {
  SUCCESS: 'success',
  PENDING_APPROVAL: 'warning',
  ERROR: 'error',
  FAILED: 'error',
}

// 搜索表单配置
const searchSchema = computed(() => [
  {
    field: 'requestId',
    label: '请求ID',
    type: 'input',
    props: {
      placeholder: '输入完整或部分请求 ID',
    },
  },
  {
    field: 'capabilityKeyword',
    label: '能力',
    type: 'input',
    props: {
      placeholder: '能力名称或编码',
    },
  },
  {
    field: 'actorKeyword',
    label: '调用用户',
    type: 'input',
    props: {
      placeholder: '用户 ID、用户名或姓名',
    },
  },
  {
    field: 'clientId',
    label: '客户端ID',
    type: 'input',
    props: {
      placeholder: '请输入客户端ID',
    },
  },
  {
    field: 'resultCode',
    label: '结果码',
    type: 'input',
    props: {
      placeholder: '如 SUCCESS / FORBIDDEN',
    },
  },
])

// 表格列配置
const tableColumns = computed(() => [
  {
    prop: 'requestId',
    label: '请求ID',
    width: 210,
    fixed: 'left',
    ellipsis: { tooltip: true },
  },
  {
    prop: 'clientCode',
    label: '客户端',
    width: 140,
    ellipsis: { tooltip: true },
    render: row => row.clientCode || '-',
  },
  {
    prop: 'capabilityCode',
    label: '能力',
    width: 250,
    ellipsis: { tooltip: true },
    render: capabilityDisplay,
  },
  {
    prop: 'actorUserId',
    label: '实际调用用户',
    width: 190,
    ellipsis: { tooltip: true },
    render: actorUserLabel,
  },
  {
    prop: 'resultStatus',
    label: '结果状态',
    width: 110,
    render: (row) => {
      if (!row.resultStatus)
        return '-'
      return h(NTag, {
        type: RESULT_STATUS_TYPE[row.resultStatus] || 'default',
        size: 'small',
      }, { default: () => resultStatusLabel(row.resultStatus) })
    },
  },
  {
    prop: 'resultCode',
    label: '结果码',
    width: 140,
    ellipsis: { tooltip: true },
    render: row => row.resultCode || '-',
  },
  {
    prop: 'failureStage',
    label: '失败阶段',
    width: 150,
    ellipsis: { tooltip: true },
    render: row => failureStageLabel(row.failureStage, row.resultStatus),
  },
  {
    prop: 'errorMessage',
    label: '错误摘要',
    width: 230,
    ellipsis: { tooltip: true },
    render: row => row.errorMessage || row.resultCode || '-',
  },
  {
    prop: 'durationMs',
    label: '耗时(ms)',
    width: 100,
    render: row => row.durationMs ?? '-',
  },
  {
    prop: 'createTime',
    label: '调用时间',
    width: 170,
    fixed: 'right',
    render: row => formatInvocationTime(row.createTime),
  },
  {
    prop: 'action',
    label: '操作',
    width: 80,
    fixed: 'right',
    actions: [{
      label: '详情',
      key: 'detail',
      type: 'primary',
      onClick: openDetail,
    }],
  },
])

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getCapabilityInvocationDetail(row.id)
    detail.value = res.data || null
  }
  catch (error) {
    window.$message.error(error?.message || '调用日志详情加载失败')
    detailVisible.value = false
  }
  finally {
    detailLoading.value = false
  }
}

function actorTypeLabel(value) {
  return actorTypeOptions.value.find(item => String(item.value) === String(value))?.label || value || '-'
}

function capabilityDisplay(row) {
  if (!row)
    return '-'
  if (row.capabilityName)
    return `${row.capabilityName}（${row.capabilityCode || '-'}）`
  return row.capabilityCode || '-'
}

function actorUserLabel(row) {
  if (!row?.actorUserId)
    return '-'
  const name = row.actorRealName || row.actorUsername || `用户 #${row.actorUserId}`
  return `${name}${row.actorUsername && row.actorRealName ? `（${row.actorUsername}）` : ''} · ID ${row.actorUserId}`
}

function serviceUserLabel(row) {
  if (!row?.serviceUserId)
    return '-'
  const name = row.serviceRealName || row.serviceUsername || `用户 #${row.serviceUserId}`
  return `${name}${row.serviceUsername && row.serviceRealName ? `（${row.serviceUsername}）` : ''} · ID ${row.serviceUserId}`
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
    ADAPTER_RESOLUTION: '执行适配器解析',
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
.capability-invocation-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.platform-list {
  flex: 1;
  min-height: 0;
}

.detail-alert {
  margin-bottom: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--border-light);
  border-left: 1px solid var(--border-light);
}

.detail-grid > div {
  min-width: 0;
  padding: 12px 14px;
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
}

.detail-grid span,
.detail-grid strong {
  display: block;
}

.detail-grid span,
.error-summary > span {
  margin-bottom: 5px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.detail-grid strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.error-summary {
  margin-top: 16px;
}

.error-summary pre {
  max-height: 220px;
  overflow: auto;
  margin: 0;
  padding: 13px;
  border-radius: 6px;
  background: var(--bg-secondary);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.security-note {
  margin-top: 16px;
}

@media (max-width: 680px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
