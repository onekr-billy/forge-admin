<template>
  <section class="test-workspace">
    <div v-if="!guide?.ready" class="not-ready">
      <h3>当前还不能发起测试</h3><p>先处理接入检查中的阻断项，再回来准备凭据和参数。</p><n-button type="primary" secondary @click="emit('prepare')">
        返回接入检查
      </n-button>
    </div>
    <template v-else>
      <ol class="test-progress" aria-label="测试进度">
        <li v-for="(item, index) in stages" :key="item.key" :class="{ active: stage === item.key }" :aria-current="stage === item.key ? 'step' : undefined">
          <span>{{ index + 1 }}</span>{{ item.label }}
        </li>
      </ol>
      <div class="test-context">
        {{ guide.clientName }} · v{{ guide.version }}<span>{{ guide.behavior === 'READ_ONLY' ? '只读调用' : '真实业务操作' }}</span>
      </div>
      <fieldset :disabled="testing || confirming" class="test-fields">
        <CapabilityTestIdentity v-if="stage === 'identity'" :auth-options="authOptions" :credential-label="credentialLabel" :credential-placeholder="credentialPlaceholder" :requires-subject-token="requiresSubjectToken" @manage="emit('manage')" />
        <CapabilityTestRequest v-else-if="stage === 'request'" @reset="resetBody" />
        <CapabilityTestResult v-else-if="testReport" @download="downloadTestReport" />
      </fieldset>
      <n-alert v-if="inputError" type="error" class="input-error" role="alert">
        {{ inputError }}
      </n-alert>
      <div class="test-actions">
        <template v-if="stage === 'identity'">
          <span>密钥只用于本次测试，不会写入接入配置。</span>
          <n-button type="primary" @click="continueToRequest">
            下一步：填写参数
          </n-button>
        </template>
        <template v-else-if="stage === 'request'">
          <n-button :disabled="testing || confirming" @click="stage = 'identity'; inputError = ''">
            上一步
          </n-button>
          <span>{{ testing ? '正在请求，请勿重复提交；关闭页面不代表撤销已发送的业务。' : guide.behavior === 'READ_ONLY' ? '将通过真实网关读取数据。' : '将真实执行业务操作，提交前会再次确认。' }}</span>
          <n-button type="primary" :loading="testing" :disabled="confirming" @click="handleTest">
            确认并调用
          </n-button>
        </template>
        <template v-else>
          <n-button @click="stage = 'request'; inputError = ''">
            返回参数
          </n-button><span>不会自动重试。写操作失败时请先核对业务记录和调用日志。</span>
          <n-button @click="downloadIntegrationExample">
            导出接入示例
          </n-button>
        </template>
      </div>
    </template>
  </section>
</template>

<script setup>
import CapabilityTestIdentity from './CapabilityTestIdentity.vue'
import CapabilityTestRequest from './CapabilityTestRequest.vue'
import CapabilityTestResult from './CapabilityTestResult.vue'
import { useCapabilityOnlineTest } from './useCapabilityOnlineTest'

const props = defineProps({ guide: { type: Object, default: null } })
const emit = defineEmits(['prepare', 'manage'])
const { stage, inputError, testing, confirming, testReport, authOptions, credentialLabel, credentialPlaceholder, requiresSubjectToken, resetBody, continueToRequest, handleTest, downloadTestReport, downloadIntegrationExample } = useCapabilityOnlineTest(props)
const stages = [{ key: 'identity', label: '准备身份' }, { key: 'request', label: '填写参数' }, { key: 'result', label: '查看结果' }]
</script>

<style scoped>
.test-workspace {
  padding-top: 20px;
  min-width: 0;
}
.test-progress {
  list-style: none;
  display: flex;
  gap: 26px;
  padding: 0;
  margin: 0 0 16px;
}
.test-progress li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-tertiary);
  font-size: 13px;
}
.test-progress span {
  border: 1px solid var(--border-light);
  border-radius: 50%;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.test-progress .active {
  color: var(--primary-color);
  font-weight: 500;
}
.active span {
  border-color: var(--primary-color);
}
.test-context {
  font-size: 12px;
  color: var(--text-tertiary);
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border-light);
}
.test-context span {
  margin-left: 16px;
}
.test-fields {
  border: 0;
  padding: 20px 0 0;
  margin: 0;
  min-width: 0;
}
.test-fields:disabled {
  pointer-events: none;
  opacity: 0.7;
}
.test-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}
.test-actions span {
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
.input-error {
  margin-top: 16px;
}
.not-ready {
  padding: 30px 0;
}
.not-ready h3 {
  font-size: 15px;
}
.not-ready p {
  color: var(--text-tertiary);
  font-size: 13px;
}
@media (max-width: 640px) {
  .test-progress {
    gap: 14px;
  }
  .test-progress li {
    gap: 5px;
    font-size: 12px;
  }
  .test-actions {
    flex-wrap: wrap;
  }
  .test-actions span {
    flex-basis: 100%;
    order: -1;
  }
}
</style>
