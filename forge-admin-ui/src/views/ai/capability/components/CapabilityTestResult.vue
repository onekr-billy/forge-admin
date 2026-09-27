<template>
  <section v-if="report" class="test-result" aria-live="polite">
    <div class="result-heading">
      <div><h3>{{ report.success ? '调用成功' : report.invocation ? '业务调用未通过' : report.tokenExchange ? '获取身份令牌失败' : '请求未完成' }}</h3><p>{{ report.durationMs }} ms · {{ report.startedAt }}</p></div><n-button size="small" @click="emit('download')">
        下载脱敏报文
      </n-button>
    </div>
    <n-alert v-if="!report.success" type="error" :show-icon="true">
      {{ report.error || '请查看响应详情' }}<p>{{ report.tokenExchange && !report.invocation ? '请核对接入系统密钥、用户身份来源与绑定关系，再返回身份配置。' : '请先核对业务记录和调用日志。网络失败不代表业务未执行，不要直接重复提交写操作。' }}</p>
    </n-alert>
    <div class="result-meta">
      <span v-if="report.invocation">HTTP {{ report.invocation.response.status }}</span><span v-if="requestId">请求编号 <code>{{ requestId }}</code></span><span v-if="report.invocation?.request?.headers?.['Idempotency-Key']">幂等键 <code>{{ report.invocation.request.headers['Idempotency-Key'] }}</code></span>
    </div>
    <h4>业务返回</h4><pre class="report-code"><code>{{ responseText }}</code></pre>
    <details>
      <summary>技术报文（已脱敏）</summary><h4>能力调用</h4><pre class="report-code"><code>{{ JSON.stringify(report.invocation, null, 2) || '未发送' }}</code></pre><template v-if="report.tokenExchange">
        <h4>身份令牌交换</h4><pre class="report-code"><code>{{ JSON.stringify(report.tokenExchange, null, 2) }}</code></pre>
      </template>
    </details>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useCapabilityOnlineTestStore } from '@/stores/capability/onlineTestStore'

const emit = defineEmits(['download'])
const state = useCapabilityOnlineTestStore()
const report = computed(() => state.testReport)
const requestId = computed(() => report.value?.invocation?.response?.body?.requestId || report.value?.invocation?.response?.headers?.['x-request-id'] || report.value?.invocation?.response?.headers?.['x-forge-request-id'])
const responseText = computed(() => report.value?.invocation ? JSON.stringify(report.value.invocation.response.body, null, 2) : '尚未取得业务返回。')
</script>

<style scoped>
.result-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
h3 {
  font-size: 15px;
  margin: 0;
}
h4 {
  font-size: 13px;
  font-weight: 500;
  margin: 16px 0 10px;
}
p {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.7;
}
.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 16px 0;
  font-size: 12px;
  color: var(--text-secondary);
}
.result-meta code {
  overflow-wrap: anywhere;
}
.report-code {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  padding: 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
details {
  margin-top: 18px;
}
summary {
  cursor: pointer;
  color: var(--text-tertiary);
  font-size: 12px;
}
@media (max-width: 640px) {
  .result-heading {
    flex-wrap: wrap;
  }
}
</style>
