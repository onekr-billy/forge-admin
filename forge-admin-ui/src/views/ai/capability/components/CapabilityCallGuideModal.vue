<template>
  <n-modal :show="show" preset="card" class="capability-call-guide-modal" :mask-closable="false" @update:show="emit('update:show', $event)">
    <template #header>
      <div class="guide-title">
        <strong>调用与测试</strong><span>{{ capability?.capabilityName }}</span><code>{{ capability?.capabilityCode }}</code>
      </div>
    </template>
    <div class="call-workspace">
      <div class="client-bar">
        <div class="client-picker">
          <label for="call-guide-client">接入系统</label>
          <n-select v-model:value="tools.selectedClientId" :input-props="{ id: 'call-guide-client' }" :options="tools.clientOptions" :loading="tools.clientsLoading" :disabled="tools.busy || tools.versionSwitching" placeholder="先选择谁来调用这项能力" filterable clearable @update:value="tools.loadGuide" />
        </div>
        <n-button :loading="tools.guideLoading" :disabled="!tools.selectedClientId || tools.busy || tools.versionSwitching" @click="tools.loadGuide(tools.selectedClientId)">
          重新检查
        </n-button>
        <span v-if="tools.guide" class="context-version">实际调用 {{ tools.guide.version ? `v${tools.guide.version}` : '版本待确认' }}</span>
      </div>
      <div v-if="tools.clientsLoading || tools.guideLoading" class="guide-placeholder" aria-live="polite">
        <n-spin size="small" /><strong>{{ tools.clientsLoading ? '正在加载接入系统' : '正在检查授权与调用条件' }}</strong>
      </div>
      <div v-else-if="tools.clientsError || tools.guideError" class="guide-placeholder" role="alert">
        <strong>{{ tools.clientsError ? '接入系统加载失败' : '调用检查失败' }}</strong><p>{{ tools.clientsError || tools.guideError }}</p>
        <n-button type="primary" secondary @click="tools.clientsError ? tools.loadClients() : tools.loadGuide(tools.selectedClientId)">
          重新加载
        </n-button>
      </div>
      <div v-else-if="!tools.guide" class="guide-placeholder">
        <strong>{{ tools.clients.length ? '从选择接入系统开始' : '还没有接入系统' }}</strong>
        <p>{{ tools.clients.length ? '接入系统代表调用方。选择后会检查它是否已获得这项能力的授权。' : '先创建一个接入系统，并为它授权当前能力，再回来测试。' }}</p>
        <n-button v-if="!tools.clients.length" type="primary" @click="tools.goClientWorkbench">
          前往接入系统
        </n-button>
      </div>
      <template v-else>
        <nav class="workspace-nav" aria-label="调用工作区">
          <button v-for="item in sections" :key="item.key" type="button" :class="{ active: tools.activeGuideTab === item.key }" :aria-current="tools.activeGuideTab === item.key ? 'page' : undefined" :disabled="tools.busy" @click="tools.activeGuideTab = item.key">
            {{ item.label }}
          </button>
        </nav>
        <section v-if="tools.activeGuideTab === 'overview'" class="preparation-panel">
          <div class="next-action">
            <div><h3>{{ tools.guide.ready ? '接入检查通过，可以准备测试' : '先处理以下阻断项' }}</h3><p>{{ tools.guide.ready ? '接下来填写调用凭据和业务参数；检查通过不代表已经执行成功。' : '检查不会发送业务请求。处理完成后，点击“重新检查”。' }}</p></div>
            <n-button v-if="tools.guide.ready" type="primary" @click="tools.activeGuideTab = 'test'">
              开始测试
            </n-button>
          </div>
          <div v-if="failedChecks.length" class="blocking-list">
            <div v-for="check in failedChecks" :key="check.code" class="blocking-row">
              <i class="i-material-symbols:error-outline-rounded" aria-hidden="true" />
              <div><strong>{{ check.label }}</strong><p>{{ check.message }}</p></div>
              <n-button v-if="clientCheckCodes.includes(check.code)" size="small" @click="tools.goClientWorkbench">
                前往配置
              </n-button>
            </div>
          </div>
          <p v-if="!tools.guide.ready && !failedChecks.length" class="muted">
            当前尚不具备调用条件，请查看完整检查或联系管理员。
          </p>
          <div class="context-summary">
            <div><span>以谁的身份执行</span><strong>{{ tools.actorTypeLabel }}</strong><p>{{ tools.guide.tokenExchangeRequired ? '需提供真实用户身份，不能使用后台登录 Token 代替。' : '使用接入系统已配置的执行身份。' }}</p></div>
            <div><span>本次调用会做什么</span><strong>{{ tools.guide.behavior === 'READ_ONLY' ? '读取数据' : '执行真实业务操作' }}</strong><p>{{ tools.guide.behavior === 'READ_ONLY' ? '仍按调用方权限校验数据范围。' : '可能新增记录、启动流程或触发动作；执行前会再次确认。' }}</p></div>
          </div>
          <div v-if="tools.guide.versionUpgradeAvailable" class="version-notice">
            <div><strong>授权仍固定在旧版本</strong><p>实际调用 v{{ tools.guide.version || tools.guide.grantFixedVersion }}，当前能力为 v{{ tools.guide.currentVersion }}。不会自动替换已有授权。</p></div>
            <n-button v-if="canUpdateGrant && tools.guide.grantId" :loading="tools.versionSwitching" @click="tools.confirmUseCurrentVersion">
              审核并切换版本
            </n-button>
            <span v-else class="muted">请联系授权管理员</span>
          </div>
          <details class="check-details">
            <summary>完整检查 · {{ tools.guide.checks?.length || 0 }} 项</summary>
            <div v-for="check in tools.guide.checks || []" :key="check.code" class="check-row">
              <span :class="{ failed: check.status === 'FAILED' }">{{ tools.checkStatusLabel(check.status) }}</span><strong>{{ check.label }}</strong><p>{{ check.message }}</p>
            </div>
          </details>
          <p class="help-footnote">
            要交给开发人员接入？在“开发文档”中查看地址、参数和示例代码。
          </p>
        </section>
        <CapabilityOnlineTestPanel v-show="tools.activeGuideTab === 'test'" :guide="tools.guide" @prepare="tools.activeGuideTab = 'overview'" @manage="tools.goClientWorkbench" />
        <CapabilityGuideDocumentation v-if="tools.activeGuideTab === 'docs'" :tools="tools" />
      </template>
    </div>
    <template #footer>
      <div class="guide-footer">
        <span>仅点击“确认并调用”才会发送业务请求。</span><n-button @click="emit('update:show', false)">
          关闭
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { computed, reactive } from 'vue'
import CapabilityGuideDocumentation from './CapabilityGuideDocumentation.vue'
import CapabilityOnlineTestPanel from './CapabilityOnlineTestPanel.vue'
import { useCapabilityCallGuide } from './useCapabilityCallGuide'

const props = defineProps({ show: Boolean, capability: { type: Object, default: null }, canUpdateGrant: Boolean })
const emit = defineEmits(['update:show'])
const tools = reactive(useCapabilityCallGuide(props, emit))
const sections = [{ key: 'overview', label: '接入检查' }, { key: 'test', label: '在线测试' }, { key: 'docs', label: '开发文档' }]
const clientCheckCodes = ['CLIENT', 'ACTOR', 'AUTH', 'GRANT', 'VERSION', 'SUBMISSION_GRANT']
const failedChecks = computed(() => (tools.guide?.checks || []).filter(check => check.status === 'FAILED'))
</script>

<style scoped>
.guide-title,
.client-bar,
.next-action,
.version-notice,
.guide-footer {
  display: flex;
  align-items: center;
  gap: 16px;
}
.guide-title {
  flex-wrap: wrap;
  gap: 8px 14px;
}
.guide-title strong {
  font-size: 17px;
}
.guide-title span {
  font-size: 13px;
}
.guide-title code,
.context-version,
.muted,
.help-footnote,
.guide-footer span {
  color: var(--text-tertiary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.call-workspace {
  min-height: 420px;
  min-width: 0;
}
.client-bar {
  align-items: flex-end;
  flex-wrap: wrap;
  padding-bottom: 18px;
}
.client-picker {
  flex: 1;
  min-width: 220px;
  max-width: 480px;
}
.client-picker label {
  display: block;
  font-size: 12px;
  margin-bottom: 7px;
  color: var(--text-secondary);
}
.context-version {
  padding-bottom: 7px;
}
.workspace-nav {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--border-light);
}
.workspace-nav button {
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--text-secondary);
  padding: 12px 2px;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
}
.workspace-nav button.active {
  border-bottom-color: var(--primary-color);
  color: var(--primary-color);
  font-weight: 600;
}
.workspace-nav button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: -2px;
}
.workspace-nav button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.preparation-panel {
  padding: 22px 0 4px;
}
.next-action {
  justify-content: space-between;
  margin-bottom: 20px;
}
h3 {
  margin: 0;
  font-size: 15px;
  color: var(--text-primary);
}
p {
  margin: 6px 0 0;
  line-height: 1.65;
  color: var(--text-tertiary);
  font-size: 12px;
}
.blocking-list {
  border: 1px solid var(--border-light);
  border-radius: 6px;
  margin-bottom: 20px;
}
.blocking-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border-bottom: 1px solid var(--border-light);
}
.blocking-row:last-child {
  border-bottom: 0;
}
.blocking-row i,
.failed {
  color: var(--error-color);
}
.blocking-row i {
  flex: none;
  font-size: 19px;
}
.blocking-row > div {
  flex: 1;
  min-width: 0;
}
.blocking-row strong,
.context-summary strong,
.version-notice strong {
  font-size: 13px;
  font-weight: 500;
}
.context-summary {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  padding: 18px;
  background: var(--bg-secondary);
  border-radius: 6px;
}
.context-summary span,
.context-summary strong {
  display: block;
}
.context-summary span {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
}
.version-notice {
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid var(--border-light);
}
.check-details {
  margin-top: 20px;
}
summary {
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 10px 0;
}
.check-row {
  display: grid;
  grid-template-columns: 86px 110px 1fr;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
}
.check-row strong {
  font-weight: 500;
}
.check-row p {
  margin: 0;
  overflow-wrap: anywhere;
}
.help-footnote {
  margin-top: 20px;
}
.guide-placeholder {
  display: flex;
  min-height: 340px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
}
.guide-placeholder strong {
  font-size: 15px;
}
.guide-placeholder p {
  max-width: 420px;
  margin: 0;
}
.guide-footer {
  justify-content: space-between;
}
:global(.capability-call-guide-modal) {
  width: min(1080px, calc(100vw - 24px));
}
:global(.capability-call-guide-modal > .n-card-content) {
  max-height: calc(100dvh - 190px);
  overflow-y: auto;
  min-height: 0;
}
@media (max-width: 640px) {
  .guide-title code {
    display: none;
  }
  .client-bar {
    gap: 10px;
  }
  .client-picker {
    max-width: none;
    flex-basis: 100%;
    min-width: 0;
  }
  .next-action,
  .version-notice {
    align-items: stretch;
    flex-direction: column;
  }
  .context-summary {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .workspace-nav {
    gap: 22px;
  }
  .check-row {
    grid-template-columns: 80px 1fr;
  }
  .check-row p {
    grid-column: 1 / -1;
  }
  .guide-footer span {
    max-width: 70%;
  }
  .blocking-row {
    flex-wrap: wrap;
  }
  :global(.capability-call-guide-modal > .n-card-content) {
    max-height: calc(100dvh - 180px);
  }
}
</style>
