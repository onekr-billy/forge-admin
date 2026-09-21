<template>
  <section class="online-test-panel">
    <div class="panel-heading">
      <div>
        <h3>测试配置</h3>
        <p>使用所选客户端的真实认证方式调用开放网关，结果可下载后交给外围系统联调。</p>
      </div>
      <n-space>
        <n-button size="small" :disabled="!guide" @click="downloadIntegrationExample">
          <template #icon>
            <i class="i-material-symbols:download-rounded" />
          </template>
          下载接入示例
        </n-button>
        <n-button size="small" :disabled="!testReport" @click="downloadTestReport">
          <template #icon>
            <i class="i-material-symbols:receipt-long-outline-rounded" />
          </template>
          下载测试报文
        </n-button>
      </n-space>
    </div>

    <n-alert :type="credentialAutoFilled ? 'success' : 'warning'" :show-icon="true" class="security-alert">
      <template #header>
        {{ credentialAutoFilled ? '已带入本次浏览器会话中的一次性凭据' : '需要准备客户端凭据' }}
      </template>
      {{ credentialAutoFilled
        ? '凭据只保存在当前页面内存，刷新浏览器后会清空；下载内容会自动脱敏。'
        : 'Client Secret、Signing Key 和用户断言私钥无法从服务端反查。请到“接入工作台 → 接入配置”生成或轮换，保存后返回本页会自动带入。' }}
      <n-button v-if="!credentialAutoFilled" text type="primary" @click="goClientWorkbench">
        去接入工作台
      </n-button>
    </n-alert>

    <n-alert
      v-if="guide?.requestNotes?.length"
      type="info"
      :show-icon="true"
      class="request-note-alert"
    >
      <template #header>
        填写请求前先确认
      </template>
      <ul class="request-note-list">
        <li v-for="note in guide.requestNotes" :key="note">
          {{ note }}
        </li>
      </ul>
    </n-alert>

    <div class="test-form-grid">
      <div class="test-field">
        <span class="field-label">认证方式</span>
        <n-select
          v-model:value="authMode"
          :options="authOptions"
          placeholder="请选择认证方式"
          :disabled="!guide?.ready"
        />
      </div>
      <div class="test-field">
        <span class="field-label">{{ credentialLabel }}</span>
        <n-input
          v-model:value="credential"
          type="password"
          show-password-on="click"
          :placeholder="credentialPlaceholder"
          :disabled="!authMode"
          autocomplete="new-password"
        />
      </div>
      <div v-if="requiresSubjectToken" class="test-field test-field-wide">
        <span class="field-label">真实用户身份来源</span>
        <n-radio-group v-model:value="subjectTokenMode" type="button" size="small">
          <n-radio-button value="OIDC">
            受信 OIDC JWT
          </n-radio-button>
          <n-radio-button v-if="guide?.userAssertionEnabled" value="USER_ASSERTION">
            客户端签名用户断言
          </n-radio-button>
        </n-radio-group>
      </div>
      <div v-if="requiresSubjectToken && subjectTokenMode === 'OIDC'" class="test-field test-field-wide">
        <span class="field-label">受信 OIDC subject_token</span>
        <n-input
          v-model:value="subjectToken"
          type="password"
          show-password-on="click"
          placeholder="粘贴外围用户的受信 OIDC JWT，仅本次测试使用"
          autocomplete="off"
        />
      </div>
      <template v-if="requiresSubjectToken && subjectTokenMode === 'USER_ASSERTION'">
        <div class="test-field">
          <span class="field-label">外围用户标识（JWT sub）</span>
          <n-input
            v-model:value="userAssertionSubject"
            maxlength="512"
            placeholder="必须已在客户端页面预绑定"
            autocomplete="off"
          />
        </div>
        <div v-if="guide?.userAssertionMappingMode === 'VERIFIED_PHONE'" class="test-field">
          <span class="field-label">已验证手机号（JWT phone_number）</span>
          <n-input
            v-model:value="userAssertionPhone"
            placeholder="首次调用用于租户内唯一匹配，后续复用已固化映射"
            autocomplete="off"
          />
        </div>
        <div class="test-field">
          <span class="field-label">Forge 组织 ID（可选）</span>
          <n-input
            v-model:value="userAssertionOrgId"
            placeholder="不填则使用该用户默认组织"
            autocomplete="off"
          />
        </div>
        <div class="test-field test-field-wide">
          <span class="field-label">用户断言私钥（PKCS#8 PEM，仅本次测试）</span>
          <n-input
            v-model:value="userAssertionPrivateKey"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 10 }"
            placeholder="粘贴生成密钥时一次性保存的 -----BEGIN PRIVATE KEY----- PEM"
            autocomplete="off"
            class="private-key-input"
          />
          <div class="assertion-protocol-hint">
            kid {{ guide.userAssertionKeyId }} · iss {{ guide.userAssertionIssuer }} · aud {{ guide.userAssertionAudience }}
          </div>
        </div>
      </template>
      <div class="test-field test-field-wide">
        <div class="body-label-line">
          <span class="field-label">请求 Body（JSON 对象）</span>
          <n-button text type="primary" size="tiny" @click="resetBody">
            恢复示例
          </n-button>
        </div>
        <n-input
          v-model:value="requestBody"
          type="textarea"
          :autosize="{ minRows: 7, maxRows: 16 }"
          placeholder="请输入合法的 JSON 对象"
          class="json-input"
        />
      </div>
    </div>

    <div class="test-actions">
      <n-alert v-if="!guide?.ready" type="error" :show-icon="true">
        当前存在调用阻断，请先处理上方“调用前检查”中的红色项目。
      </n-alert>
      <n-button
        type="primary"
        :loading="testing"
        :disabled="!guide?.ready || !authMode"
        @click="handleTest"
      >
        <template #icon>
          <i class="i-material-symbols:play-arrow-rounded" />
        </template>
        发起真实调用
      </n-button>
    </div>

    <div v-if="testReport" class="test-result">
      <div class="result-summary">
        <div>
          <strong>测试结果</strong>
          <span>{{ testReport.startedAt }} · {{ testReport.durationMs }} ms</span>
        </div>
        <n-tag :type="testReport.success ? 'success' : 'error'" round>
          {{ testReport.success ? '调用成功' : '调用失败' }}
        </n-tag>
      </div>
      <n-alert v-if="testReport.error" type="error" :show-icon="true" class="result-error">
        {{ testReport.error }}
      </n-alert>
      <n-tabs type="line" animated>
        <n-tab-pane v-if="testReport.tokenExchange" name="token" tab="Token 报文">
          <pre class="report-panel"><code>{{ exchangeText(testReport.tokenExchange) }}</code></pre>
        </n-tab-pane>
        <n-tab-pane name="invoke" tab="能力调用报文">
          <pre class="report-panel"><code>{{ exchangeText(testReport.invocation) }}</code></pre>
        </n-tab-pane>
      </n-tabs>
    </div>
  </section>
</template>

<script setup>
import { useCapabilityOnlineTest } from './useCapabilityOnlineTest'

const props = defineProps({
  guide: {
    type: Object,
    default: null,
  },
})

const {
  authMode,
  credential,
  subjectTokenMode,
  subjectToken,
  userAssertionSubject,
  userAssertionPhone,
  userAssertionOrgId,
  userAssertionPrivateKey,
  requestBody,
  testing,
  testReport,
  credentialAutoFilled,
  authOptions,
  requiresSubjectToken,
  credentialLabel,
  credentialPlaceholder,
  resetBody,
  handleTest,
  exchangeText,
  goClientWorkbench,
  downloadTestReport,
  downloadIntegrationExample,
} = useCapabilityOnlineTest(props)
</script>

<style scoped>
.online-test-panel {
  padding: 0 0 4px;
}

.panel-heading,
.result-summary,
.body-label-line,
.test-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.panel-heading {
  align-items: flex-start;
  margin-bottom: 14px;
}

.panel-heading h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.panel-heading p {
  margin: 5px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.security-alert {
  margin-bottom: 16px;
}

.request-note-alert {
  margin-bottom: 16px;
}

.request-note-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.8;
}

.test-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.test-field-wide {
  grid-column: 1 / -1;
}

.field-label {
  display: block;
  margin-bottom: 7px;
  color: var(--text-secondary);
  font-size: 12px;
}

.body-label-line .field-label {
  margin-bottom: 7px;
}

.json-input :deep(textarea),
.private-key-input :deep(textarea),
.report-panel {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
}

.assertion-protocol-hint {
  margin-top: 6px;
  color: var(--text-tertiary);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 11px;
  overflow-wrap: anywhere;
}

.test-actions {
  align-items: flex-start;
  margin-top: 16px;
}

.test-actions .n-alert {
  flex: 1;
}

.test-result {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.result-summary strong,
.result-summary span {
  display: block;
}

.result-summary span {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.result-error {
  margin-top: 12px;
}

.report-panel {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 14px;
  border-radius: 6px;
  background: #111827;
  color: #e5e7eb;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 760px) {
  .panel-heading,
  .test-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .test-form-grid {
    grid-template-columns: 1fr;
  }

  .test-field-wide {
    grid-column: auto;
  }
}
</style>
