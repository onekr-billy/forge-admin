<template>
  <n-modal
    :show="show"
    preset="card"
    class="capability-call-guide-modal"
    :mask-closable="false"
    @update:show="emit('update:show', $event)"
  >
    <template #header>
      <div class="modal-heading">
        <span class="modal-title">调用与测试</span>
        <span v-if="capability" class="modal-subtitle">
          {{ capability.capabilityName }} · {{ capability.capabilityCode }}
        </span>
      </div>
    </template>

    <div class="guide-body">
      <div class="client-bar">
        <div class="client-field">
          <span class="field-label">调用客户端</span>
          <n-select
            v-model:value="selectedClientId"
            :options="clientOptions"
            :loading="clientsLoading"
            placeholder="选择一个客户端后生成可执行指南"
            filterable
            clearable
            @update:value="loadGuide"
          >
            <template #empty>
              <n-empty size="small" description="暂无机器客户端，请先创建客户端" />
            </template>
          </n-select>
        </div>
        <n-button
          quaternary
          circle
          :loading="guideLoading"
          :disabled="!selectedClientId"
          aria-label="刷新调用检查"
          @click="loadGuide(selectedClientId)"
        >
          <template #icon>
            <i class="i-material-symbols:refresh-rounded" />
          </template>
        </n-button>
        <n-tag v-if="guide" :type="guide.ready ? 'success' : 'error'" round>
          {{ guide.ready ? '已具备静态调用条件' : '存在调用阻断' }}
        </n-tag>
      </div>

      <n-alert v-if="clientsError" type="error" :show-icon="true">
        {{ clientsError }}
      </n-alert>
      <n-alert v-else-if="!clientsLoading && clients.length === 0" type="warning" :show-icon="true">
        还没有可用于诊断的接入系统。请先在“接入系统”页面创建，再通过接入工作台授权此能力。
      </n-alert>
      <n-spin v-else-if="guideLoading" class="guide-loading" description="正在核对网关、客户端和授权状态…" />

      <template v-else-if="guide">
        <n-alert
          v-if="guide.versionUpgradeAvailable"
          type="warning"
          :show-icon="true"
          class="version-alert"
        >
          <template #header>
            这个客户端仍在使用旧能力版本
          </template>
          <div class="version-alert-content">
            <span>
              能力当前是 v{{ guide.currentVersion }}，但客户端授权按“{{ grantVersionStrategyLabel }}”
              实际调用 v{{ guide.version || guide.grantFixedVersion || '-' }}。
              发布新版本不会自动修改已有授权。
            </span>
            <n-button
              v-if="canUpdateGrant && guide.grantId"
              type="warning"
              secondary
              :loading="versionSwitching"
              @click="confirmUseCurrentVersion"
            >
              切换到当前 v{{ guide.currentVersion }}
            </n-button>
            <span v-else class="version-permission-tip">
              请让具有能力授权维护权限的管理员切换授权版本。
            </span>
          </div>
        </n-alert>

        <n-tabs v-model:value="activeGuideTab" type="line" animated class="guide-tabs">
          <n-tab-pane name="overview" tab="接入概览">
            <section class="guide-section guide-section-first">
              <div class="section-heading">
                <div>
                  <h3>调用地址与身份</h3>
                  <p>以下内容由所选客户端的实际授权版本实时生成。</p>
                </div>
              </div>
              <div class="endpoint-grid">
                <div class="endpoint-item endpoint-wide">
                  <span class="endpoint-label">调用地址</span>
                  <div class="copy-line">
                    <code>{{ guide.invokeUrl }}</code>
                    <n-button text type="primary" @click="copyValue(guide.invokeUrl, '调用地址已复制')">
                      复制
                    </n-button>
                  </div>
                </div>
                <div class="endpoint-item endpoint-wide">
                  <span class="endpoint-label">OAuth Resource</span>
                  <div class="copy-line">
                    <code>{{ guide.openapiResource }}</code>
                    <n-button text type="primary" @click="copyValue(guide.openapiResource, 'Resource 已复制')">
                      复制
                    </n-button>
                  </div>
                </div>
                <div class="endpoint-item endpoint-wide">
                  <span class="endpoint-label">Token 地址</span>
                  <div class="copy-line">
                    <code>{{ guide.tokenUrl }}</code>
                    <n-button text type="primary" @click="copyValue(guide.tokenUrl, 'Token 地址已复制')">
                      复制
                    </n-button>
                  </div>
                </div>
                <div class="endpoint-item">
                  <span class="endpoint-label">实际调用版本</span>
                  <strong>{{ guide.version ? `v${guide.version}` : '授权版本尚未解析' }}</strong>
                </div>
                <div class="endpoint-item">
                  <span class="endpoint-label">当前能力版本</span>
                  <strong>{{ guide.currentVersion ? `v${guide.currentVersion}` : '-' }}</strong>
                </div>
                <div class="endpoint-item">
                  <span class="endpoint-label">客户端授权策略</span>
                  <strong>{{ grantVersionStrategyLabel }}</strong>
                </div>
                <div class="endpoint-item">
                  <span class="endpoint-label">客户端</span>
                  <strong>{{ guide.clientName }}（{{ guide.clientCode }}）</strong>
                </div>
                <div class="endpoint-item">
                  <span class="endpoint-label">调用主体</span>
                  <strong>{{ actorTypeLabel }}</strong>
                </div>
                <div class="endpoint-item">
                  <span class="endpoint-label">可用认证</span>
                  <strong>{{ authModeLabel }}</strong>
                </div>
                <template v-if="guide.userAssertionEnabled">
                  <div class="endpoint-item">
                    <span class="endpoint-label">客户端用户断言</span>
                    <strong>RS256 · kid {{ guide.userAssertionKeyId }}</strong>
                  </div>
                  <div class="endpoint-item">
                    <span class="endpoint-label">断言 Audience</span>
                    <strong>{{ guide.userAssertionAudience }}</strong>
                  </div>
                  <div class="endpoint-item endpoint-wide">
                    <span class="endpoint-label">用户断言 Subject Token Type</span>
                    <div class="copy-line">
                      <code>{{ guide.userAssertionSubjectTokenType }}</code>
                      <n-button text type="primary" @click="copyValue(guide.userAssertionSubjectTokenType, 'Token Type 已复制')">
                        复制
                      </n-button>
                    </div>
                  </div>
                </template>
              </div>
            </section>

            <section class="guide-section readiness-section">
              <div class="section-heading">
                <div>
                  <h3>调用前检查</h3>
                  <p>红色项会直接阻断请求；“运行时”项由实际用户或服务账号在调用时校验。</p>
                </div>
              </div>
              <div class="check-list">
                <div v-for="check in guide.checks" :key="check.code" class="check-row">
                  <div class="check-state" :class="`state-${check.status.toLowerCase()}`">
                    <i :class="checkIcon(check.status)" />
                  </div>
                  <div class="check-content">
                    <div class="check-title">
                      <strong>{{ check.label }}</strong>
                      <n-tag :type="checkTagType(check.status)" size="small" :bordered="false">
                        {{ checkStatusLabel(check.status) }}
                      </n-tag>
                    </div>
                    <p>{{ check.message }}</p>
                  </div>
                </div>
              </div>
            </section>
          </n-tab-pane>

          <n-tab-pane name="contract" tab="接口契约">
            <section class="guide-section guide-section-first contract-section">
              <div class="section-heading">
                <div>
                  <h3>请求、返回与业务校验</h3>
                  <p>中文名称用于理解业务含义，字段编码才是外围系统实际发送的 JSON Key。</p>
                </div>
              </div>

              <n-tabs type="segment" animated>
                <n-tab-pane name="request-contract" tab="请求参数">
                  <n-data-table
                    :columns="fieldColumns"
                    :data="guide.requestFields || []"
                    :row-key="row => row.path"
                    :bordered="false"
                    size="small"
                    class="contract-table"
                  />
                  <div v-if="guide.requestNotes?.length" class="contract-notes">
                    <strong>请求说明</strong>
                    <ul>
                      <li v-for="note in guide.requestNotes" :key="note">
                        {{ note }}
                      </li>
                    </ul>
                  </div>
                </n-tab-pane>
                <n-tab-pane name="response-contract" tab="返回参数">
                  <n-data-table
                    :columns="fieldColumns"
                    :data="guide.responseFields || []"
                    :row-key="row => row.path"
                    :bordered="false"
                    size="small"
                    class="contract-table"
                  />
                  <div v-if="guide.responseNotes?.length" class="contract-notes">
                    <strong>返回说明</strong>
                    <ul>
                      <li v-for="note in guide.responseNotes" :key="note">
                        {{ note }}
                      </li>
                    </ul>
                  </div>
                </n-tab-pane>
                <n-tab-pane name="business-rules" tab="业务校验">
                  <n-empty v-if="!guide.businessRules?.length" description="当前版本未补充业务校验说明" />
                  <ol v-else class="business-rule-list">
                    <li v-for="rule in guide.businessRules" :key="rule">
                      {{ rule }}
                    </li>
                  </ol>
                </n-tab-pane>
              </n-tabs>
            </section>
          </n-tab-pane>

          <n-tab-pane name="examples" tab="示例代码">
            <section class="guide-section guide-section-first">
              <div class="section-heading example-heading">
                <div>
                  <h3>复制调用示例</h3>
                  <p>只保留 Curl 和 Java 两种交付格式；先选择认证方式，再复制完整示例。</p>
                </div>
                <n-space align="center">
                  <n-select
                    v-model:value="exampleAuthMode"
                    :options="exampleAuthOptions"
                    size="small"
                    style="width: 180px"
                  />
                  <n-button
                    v-if="currentExample"
                    size="small"
                    @click="copyValue(currentExample, '调用示例已复制')"
                  >
                    <template #icon>
                      <i class="i-material-symbols:content-copy-outline-rounded" />
                    </template>
                    复制示例
                  </n-button>
                </n-space>
              </div>
              <n-tabs v-model:value="activeExample" type="line" animated>
                <n-tab-pane name="CURL" tab="Curl">
                  <n-alert v-if="exampleCredentialNotice" type="info" class="example-alert">
                    {{ exampleCredentialNotice }}
                  </n-alert>
                  <pre class="code-panel"><code>{{ curlExample }}</code></pre>
                </n-tab-pane>
                <n-tab-pane name="JAVA" tab="Java 17">
                  <n-alert type="warning" class="example-alert">
                    凭据从环境变量或密钥管理系统注入，不要写入代码仓库。
                  </n-alert>
                  <pre class="code-panel"><code>{{ javaExample }}</code></pre>
                </n-tab-pane>
              </n-tabs>
            </section>
          </n-tab-pane>

          <n-tab-pane name="test" tab="在线测试">
            <section class="guide-section guide-section-first test-section">
              <CapabilityOnlineTestPanel :guide="guide" />
            </section>
          </n-tab-pane>
        </n-tabs>
      </template>

      <div v-else-if="!clientsLoading" class="guide-placeholder">
        <i class="i-material-symbols:integration-instructions-outline-rounded" />
        <strong>选择客户端，平台会告诉你能不能调用</strong>
        <span>检查结果会覆盖网关开关、能力状态、主体模式、认证方式、授权和版本。</span>
      </div>
    </div>

    <template #footer>
      <div class="modal-footer">
        <n-space>
          <n-button :loading="markdownDownloading" :disabled="!capability?.id" @click="downloadMarkdown">
            <template #icon>
              <i class="i-material-symbols:description-outline-rounded" />
            </template>
            下载 Markdown
          </n-button>
          <n-button :loading="openApiDownloading" :disabled="!capability?.id" @click="downloadOpenApi">
            <template #icon>
              <i class="i-material-symbols:code-rounded" />
            </template>
            下载 OpenAPI JSON
          </n-button>
        </n-space>
        <n-button @click="emit('update:show', false)">
          关闭
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import CapabilityOnlineTestPanel from './CapabilityOnlineTestPanel.vue'
import { useCapabilityCallGuide } from './useCapabilityCallGuide'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  capability: {
    type: Object,
    default: null,
  },
  canUpdateGrant: {
    type: Boolean,
    default: false,
  },
})
const emit = defineEmits(['update:show'])

const {
  clients,
  clientsLoading,
  clientsError,
  selectedClientId,
  guide,
  guideLoading,
  activeGuideTab,
  activeExample,
  exampleAuthMode,
  markdownDownloading,
  openApiDownloading,
  versionSwitching,
  clientOptions,
  actorTypeLabel,
  authModeLabel,
  grantVersionStrategyLabel,
  fieldColumns,
  exampleAuthOptions,
  curlExample,
  javaExample,
  exampleCredentialNotice,
  currentExample,
  loadGuide,
  confirmUseCurrentVersion,
  checkIcon,
  checkTagType,
  checkStatusLabel,
  copyValue,
  downloadMarkdown,
  downloadOpenApi,
} = useCapabilityCallGuide(props, emit)
</script>

<style scoped>
.guide-body {
  min-height: 420px;
}

.version-alert {
  margin-top: 16px;
}

.version-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.version-alert-content > span:first-child {
  line-height: 1.7;
}

.version-permission-tip {
  flex: none;
  color: var(--text-tertiary);
  font-size: 12px;
}

.modal-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.modal-title {
  flex: none;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.modal-subtitle {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.client-bar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border-light);
}

.client-field {
  width: min(520px, 100%);
}

.field-label,
.endpoint-label {
  display: block;
  margin-bottom: 7px;
  color: var(--text-secondary);
  font-size: 12px;
}

.guide-loading {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
}

.guide-section {
  padding: 22px 0;
  border-bottom: 1px solid var(--border-light);
}

.guide-tabs {
  margin-top: 16px;
}

.guide-section-first {
  padding-top: 10px;
}

.guide-section:last-child {
  padding-bottom: 4px;
  border-bottom: 0;
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
  font-size: 15px;
  font-weight: 600;
}

.section-heading p {
  margin: 5px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.endpoint-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--border-light);
  border-left: 1px solid var(--border-light);
}

.endpoint-item {
  min-width: 0;
  padding: 13px 15px;
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
}

.endpoint-wide {
  grid-column: 1 / -1;
}

.endpoint-item strong {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  word-break: break-word;
}

.copy-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.copy-line code {
  overflow: hidden;
  color: var(--text-primary);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--border-light);
  border-left: 1px solid var(--border-light);
}

.check-row {
  display: flex;
  gap: 11px;
  min-height: 82px;
  padding: 13px 15px;
  border-right: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
}

.check-state {
  display: flex;
  width: 24px;
  height: 24px;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 18px;
}

.state-passed {
  color: var(--success-color);
}

.state-failed {
  color: var(--error-color);
}

.state-runtime,
.state-info {
  color: var(--info-color);
}

.check-content {
  min-width: 0;
}

.check-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.check-title strong {
  color: var(--text-primary);
  font-size: 13px;
}

.check-content p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.example-heading {
  align-items: center;
}

.contract-table {
  min-width: 820px;
}

.contract-section :deep(.n-tab-pane) {
  overflow-x: auto;
}

.contract-notes {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 6px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 12px;
}

.contract-notes ul,
.business-rule-list {
  margin: 8px 0 0;
  padding-left: 20px;
  line-height: 1.8;
}

.business-rule-list {
  padding: 8px 8px 8px 28px;
  color: var(--text-secondary);
}

.test-section {
  padding-bottom: 0;
}

.example-alert {
  margin-bottom: 12px;
}

.code-panel {
  overflow: auto;
  max-height: 360px;
  min-height: 150px;
  margin: 0;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.guide-placeholder {
  display: flex;
  min-height: 350px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  text-align: center;
}

.guide-placeholder i {
  margin-bottom: 16px;
  font-size: 42px;
}

.guide-placeholder strong {
  margin-bottom: 8px;
  color: var(--text-secondary);
  font-size: 14px;
}

.guide-placeholder span {
  max-width: 520px;
  font-size: 12px;
  line-height: 1.7;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

@media (max-width: 760px) {
  .version-alert-content {
    align-items: stretch;
    flex-direction: column;
  }

  .version-permission-tip {
    flex: initial;
  }

  .client-bar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .client-field {
    width: 100%;
  }

  .endpoint-grid,
  .check-list {
    grid-template-columns: 1fr;
  }

  .endpoint-wide {
    grid-column: auto;
  }

  .modal-footer {
    align-items: stretch;
    flex-direction: column;
  }
}

:global(.capability-call-guide-modal) {
  width: min(1040px, calc(100vw - 32px));
}

:global(.capability-call-guide-modal > .n-card__content) {
  max-height: calc(100vh - 190px);
  overflow-y: auto;
}
</style>
