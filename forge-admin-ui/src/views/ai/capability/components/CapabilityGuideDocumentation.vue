<template>
  <section class="guide-docs">
    <h3>开发接入文档</h3><p>以下契约和示例对应当前接入系统实际授权的 v{{ tools.guide.version || '-' }}。</p>
    <n-tabs type="line">
      <n-tab-pane name="contract" tab="请求与返回">
        <h4>请求参数</h4><n-data-table :columns="tools.fieldColumns" :data="tools.guide.requestFields || []" :row-key="row => row.path" :scroll-x="860" :bordered="false" size="small" />
        <ul v-if="tools.guide.requestNotes?.length">
          <li v-for="note in tools.guide.requestNotes" :key="note">
            {{ note }}
          </li>
        </ul>
        <h4>返回参数</h4><n-data-table :columns="tools.fieldColumns" :data="tools.guide.responseFields || []" :row-key="row => row.path" :scroll-x="860" :bordered="false" size="small" />
        <ul v-if="tools.guide.responseNotes?.length">
          <li v-for="note in tools.guide.responseNotes" :key="note">
            {{ note }}
          </li>
        </ul>
        <details v-if="tools.guide.businessRules?.length">
          <summary>业务校验规则</summary><ol>
            <li v-for="rule in tools.guide.businessRules" :key="rule">
              {{ rule }}
            </li>
          </ol>
        </details>
      </n-tab-pane>
      <n-tab-pane name="examples" tab="示例代码">
        <div class="example-toolbar">
          <n-select v-model:value="state.exampleAuthMode" :options="tools.exampleAuthOptions" aria-label="示例认证方式" class="auth-select" /><n-radio-group v-model:value="state.activeExample" size="small">
            <n-radio-button value="CURL">
              Curl
            </n-radio-button><n-radio-button value="JAVA">
              Java 17
            </n-radio-button>
          </n-radio-group><n-button :disabled="!tools.currentExample" @click="tools.copyValue(tools.currentExample, '调用示例已复制')">
            复制示例
          </n-button>
        </div>
        <p>{{ tools.exampleCredentialNotice }} 请从环境变量或密钥管理系统注入，不要写入代码仓库。</p><pre class="code-panel"><code>{{ tools.currentExample }}</code></pre>
      </n-tab-pane>
      <n-tab-pane name="addresses" tab="地址与协议">
        <div v-for="item in addresses" :key="item.label" class="address-row">
          <span>{{ item.label }}</span><code>{{ item.value || '-' }}</code><n-button text type="primary" :disabled="!item.value" @click="tools.copyValue(item.value, `${item.label}已复制`)">
            复制
          </n-button>
        </div>
        <dl><dt>执行身份</dt><dd>{{ tools.actorTypeLabel }}</dd><dt>认证方式</dt><dd>{{ tools.authModeLabel }}</dd><dt>授权策略</dt><dd>{{ tools.grantVersionStrategyLabel }}</dd></dl>
        <template v-if="tools.guide.userAssertionEnabled">
          <h4>用户断言协议</h4><dl><dt>算法 / kid</dt><dd>RS256 / {{ tools.guide.userAssertionKeyId }}</dd><dt>Audience</dt><dd>{{ tools.guide.userAssertionAudience }}</dd><dt>Subject Token Type</dt><dd>{{ tools.guide.userAssertionSubjectTokenType }}</dd></dl>
        </template>
      </n-tab-pane>
    </n-tabs>
    <div class="doc-downloads">
      <span>通用文档导出按能力当前版本生成；联调当前授权版本请使用上方示例。</span><n-button size="small" :loading="tools.markdownDownloading" @click="tools.downloadMarkdown">
        下载 Markdown
      </n-button><n-button size="small" :loading="tools.openApiDownloading" @click="tools.downloadOpenApi">
        下载 OpenAPI
      </n-button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useCapabilityCallGuideStore } from '@/stores/capability/callGuideStore'

const props = defineProps({ tools: { type: Object, required: true } })
const state = useCapabilityCallGuideStore()
const addresses = computed(() => [{ label: '调用地址', value: props.tools.guide.invokeUrl }, { label: 'Token 地址', value: props.tools.guide.tokenUrl }, { label: 'OAuth Resource', value: props.tools.guide.openapiResource }])
</script>

<style scoped>
.guide-docs {
  padding-top: 20px;
  min-width: 0;
}
h3 {
  margin: 0;
  font-size: 15px;
}
h4 {
  margin: 16px 0 12px;
  font-size: 13px;
  font-weight: 500;
}
p,
ul,
ol,
dl {
  font-size: 12px;
  line-height: 1.8;
  color: var(--text-secondary);
}
p {
  margin: 6px 0 14px;
  color: var(--text-tertiary);
}
summary {
  padding-top: 12px;
  cursor: pointer;
  font-size: 13px;
}
.example-toolbar,
.doc-downloads {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin: 14px 0;
}
.auth-select {
  width: 180px;
}
.code-panel {
  max-height: 420px;
  overflow: auto;
  padding: 16px;
  border: 1px solid var(--border-light);
  background: var(--bg-secondary);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.address-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-light);
  align-items: start;
  font-size: 12px;
}
.address-row code,
dd {
  overflow-wrap: anywhere;
}
.address-row span,
dt {
  color: var(--text-tertiary);
}
dl {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 12px;
}
dd {
  margin: 0;
  min-width: 0;
}
.doc-downloads {
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}
.doc-downloads span {
  flex: 1;
  min-width: 200px;
  font-size: 12px;
  color: var(--text-tertiary);
}
@media (max-width: 640px) {
  .address-row {
    grid-template-columns: 1fr auto;
  }
  .address-row span {
    grid-column: 1 / -1;
  }
  .doc-downloads span {
    flex-basis: 100%;
  }
  dl {
    grid-template-columns: 90px 1fr;
  }
}
</style>
