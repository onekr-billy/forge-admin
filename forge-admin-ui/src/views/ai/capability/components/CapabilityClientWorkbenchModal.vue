<template>
  <section v-if="show" class="client-workbench">
    <header class="workbench-header">
      <NButton size="small" :disabled="grantSubmitting" @click="emit('update:show', false)">
        ← 返回系统列表
      </NButton>
      <div class="workbench-title">
        <strong>{{ client?.clientName || '接入配置' }}</strong>
        <span v-if="client">{{ client.clientCode }} · AppId {{ client.id }}</span>
      </div>
    </header>

    <n-tabs v-if="client" v-model:value="activeTab" type="line" class="workbench-tabs" animated @update:value="handleTabChange">
      <n-tab-pane name="overview" tab="接入配置">
        <div class="onboarding-actions">
          <span>接入顺序</span>
          <NButton text @click="activeTab = 'overview'">
            1. 保存凭据
          </NButton>
          <NButton v-if="canGrantQuery" text type="primary" @click="activeTab = 'grants'; handleTabChange('grants')">
            2. 授权能力
          </NButton>
          <NButton v-if="userDelegationEnabled" text @click="activeTab = 'mappings'">
            3. 配置真实用户
          </NButton>
          <NButton v-if="canLogQuery" text @click="activeTab = 'logs'; handleTabChange('logs')">
            查看调用结果
          </NButton>
        </div>
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
            <NButton v-if="canRotate && client.status === 'ENABLED'" type="primary" secondary @click="emit('rotateSecret', client)">
              轮换 Client Secret
            </NButton>
            <NButton
              v-if="canEdit && signatureEnabled && client.status === 'ENABLED'"
              type="primary"
              secondary
              @click="emit('rotateSigningKey', client)"
            >
              轮换 Signing Key
            </NButton>
            <NButton
              v-if="canEdit && userDelegationEnabled && client.status === 'ENABLED'"
              type="primary"
              secondary
              @click="emit('configureIdentity', client)"
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
            <p>授权默认锚定当前能力版本。字段范围由能力契约限定；业务动作支持进一步收窄，模型必填项不可移除。</p>
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
            @click="emit('configureIdentity', client)"
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

    <n-modal v-model:show="grantVisible" preset="card" :title="editingGrantId ? '调整客户端授权' : '为当前客户端新增授权'" style="width: min(640px, calc(100vw - 32px))" :content-style="{ maxHeight: '70vh', overflow: 'auto' }" :mask-closable="false" :closable="!grantSubmitting" :close-on-esc="!grantSubmitting">
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
          <NButton :disabled="grantSubmitting" @click="grantVisible = false">
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
  </section>
</template>

<script setup>
import { NButton } from 'naive-ui'

import { useCapabilityClientWorkbench } from './useCapabilityClientWorkbench'

const props = defineProps({
  initialTab: { type: String, default: 'overview' },
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
  'rotateSecret',
  'rotateSigningKey',
  'configureIdentity',
  'revoke',
])

const {
  actorModeOptions,
  clientStatusOptions,
  versionStrategyOptions,
  actorTypeOptions,
  activeTab,
  signatureEnabled,
  userDelegationEnabled,
  authModeText,
  mappingModeLabel,
  handleTabChange,
  dictLabel,
  grantRows,
  grantLoading,
  grantPagination,
  grantColumns,
  changeGrantPage,
  changeGrantPageSize,
  grantVisible,
  editingGrantId,
  grantSubmitting,
  grantOptionLoading,
  grantFormRef,
  grantForm,
  capabilityOptions,
  grantFieldOptions,
  grantOperationOptions,
  grantRules,
  openGrantModal,
  handleCapabilityChange,
  submitGrant,
  logRows,
  logLoading,
  logFilters,
  logPagination,
  logColumns,
  searchLogs,
  resetLogFilters,
  changeLogPage,
  changeLogPageSize,
  logDetailVisible,
  logDetailLoading,
  logDetail,
  userLabel,
  capabilityDisplay,
  failureStageLabel,
} = useCapabilityClientWorkbench(props, emit)
</script>

<style scoped>
.client-workbench {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  background: var(--bg-primary);
}
.workbench-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}
.workbench-tabs {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  display: flex;
  flex-direction: column;
}
.workbench-tabs :deep(.n-tabs-pane-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-bottom: 16px;
}
.onboarding-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
}
.onboarding-actions > span {
  color: var(--text-tertiary);
  font-size: 12px;
}
.workbench-title strong,
.workbench-title span {
  display: block;
}

.workbench-title strong {
  color: var(--text-primary);
  font-size: 15px;
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
  padding: 10px 12px;
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-primary);
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
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
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
</style>
