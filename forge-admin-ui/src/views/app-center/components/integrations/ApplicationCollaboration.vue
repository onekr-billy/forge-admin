<template>
  <n-result v-if="!store.has('system:collaboration:connection:list')" status="403" title="需要企业协同查看权限" description="请联系管理员授权；应用编辑权限不会自动开放连接和凭据。" />
  <n-spin v-else :show="store.loading.collaboration">
    <div class="integration-toolbar">
      <span class="integration-help">连接配置 <n-tag size="small" :bordered="false" :type="dirty ? 'warning' : 'default'">{{ dirty ? '有未保存的更改' : store.config.connectionId ? '已保存' : '未绑定' }}</n-tag></span>
      <n-button size="small" :disabled="store.writing" @click="store.loadCollaboration">
        刷新状态
      </n-button>
    </div>
    <n-alert v-if="store.errors.collaboration" type="error" class="integration-error">
      {{ store.errors.collaboration }}
    </n-alert>
    <div class="configuration-sheet">
      <section class="configuration-section">
        <div class="section-label">
          <h3>企业连接</h3><p class="integration-help">
            复用平台凭据<br>仅保存连接引用
          </p>
        </div>
        <div class="section-content">
          <n-form-item label="企业连接" label-placement="top">
            <n-select v-model:value="store.connectionId" :options="options" :disabled="!editable || store.writing" filterable clearable placeholder="选择平台中已有的连接" />
          </n-form-item>
          <n-alert v-if="store.config.connectionId && !savedConnection" type="warning">
            原连接已停用、删除或不可见。原消息通道不会回退到其他连接，请重新选择或取消绑定。
          </n-alert>
          <n-empty v-if="!options.length && !store.loading.collaboration" description="还没有可用的企业连接" class="integration-empty">
            <template #extra>
              <n-button @click="manageConnections">
                去配置企业协同
              </n-button>
            </template>
          </n-empty>
          <div class="integration-row">
            <n-space>
              <n-button type="primary" size="small" :loading="store.writing" :disabled="!editable || (!dirty && !store.config.connectionId)" @click="save">
                {{ dirty || !store.config.connectionId ? '保存绑定' : '重新应用配置' }}
              </n-button>
              <n-button v-if="dirty" size="small" :disabled="store.writing" @click="store.connectionId = store.config.connectionId == null ? null : String(store.config.connectionId)">
                取消更改
              </n-button>
            </n-space>
            <n-button text size="small" @click="manageConnections">
              平台连接管理 ↗
            </n-button>
          </div>
          <p class="integration-help">
            保存后立即使用，无需发布应用。取消绑定会停用本应用消息通道；刷新不会丢弃未保存的选择。
          </p>
        </div>
      </section>
      <section class="configuration-section">
        <div class="section-label">
          <h3>工作台免登</h3><p class="integration-help">
            企业微信内访问<br>遵循应用访问权限
          </p>
        </div>
        <div class="section-content">
          <div class="readiness-line">
            <n-tag size="small" :bordered="false" :type="savedConnection?.loginAvailable ? 'success' : 'default'">
              {{ savedConnection?.loginAvailable ? '配置齐全' : '待配置' }}
            </n-tag><span class="integration-help">{{ savedConnection?.loginAvailable ? '仍需在企业微信验证可信域名与可见范围' : '需绑定连接，配置 LOGIN 应用并开启工作台免登' }}</span>
          </div>
          <template v-if="savedConnection?.loginAvailable">
            <label>企业微信工作台应用主页</label>
            <div class="copy-row">
              <n-input :value="entryUrl" readonly aria-label="企业微信应用访问地址" /><n-button :disabled="!entryUrl || !store.published" @click="copy(entryUrl)">
                复制地址
              </n-button>
            </div>
            <p class="integration-help">
              {{ store.published ? '普通浏览器打开仍需登录。请将此地址填入企业微信应用主页。' : '请先发布并启用应用，再分发访问地址。' }}
            </p>
          </template>
        </div>
      </section>
      <section class="configuration-section">
        <div class="section-label">
          <h3>业务消息通知</h3><p class="integration-help">
            在流程运行时<br>发送企业微信消息
          </p>
        </div>
        <div class="section-content">
          <div class="readiness-line">
            <n-tag size="small" :bordered="false" :type="savedConnection?.messageAvailable ? 'success' : 'default'">
              {{ savedConnection?.messageAvailable ? '发送连接已配置' : '待配置发送连接' }}
            </n-tag><span class="integration-help">{{ savedConnection?.messageAvailable ? '不会自动发送，还需在业务流程中设置发送时机和内容' : '先绑定企业连接，并在平台连接管理中配置消息应用（MESSAGE）' }}</span>
          </div>
          <template v-if="savedConnection?.messageAvailable">
            <dl class="message-guide">
              <dt>发给谁</dt><dd>当前流程的发起人，须先绑定企业账号。</dd>
              <dt>何时发送</dt><dd>业务流程运行到“发送消息”节点时，例如提交成功或审批完成后。</dd>
              <dt>发送什么</dt><dd>在该节点选择消息模板，并勾选“通过本应用企业协同通道发送”，保存并发布流程。</dd>
            </dl>
            <n-button size="small" secondary @click="openFlows">
              去业务流程配置通知 →
            </n-button>
            <p class="integration-help">
              发送结果及失败原因请查看企业协同投递记录；这里仅检查连接条件，不代表通知规则已启用或投递成功。
            </p>
            <n-collapse class="message-technical">
              <n-collapse-item title="技术详情（无需手工配置）" name="channel">
                <p class="integration-help">
                  下面的编码由系统自动生成，用于把本应用的消息转交给已绑定的企业连接。配置流程时只需勾选，不需要复制或填写此编码。
                </p>
                <div class="copy-row">
                  <n-input :value="channelCode" readonly aria-label="业务消息通道编码" /><n-button size="small" @click="copy(channelCode)">
                    复制编码
                  </n-button>
                </div>
              </n-collapse-item>
            </n-collapse>
          </template>
        </div>
      </section>
    </div>
    <p v-if="dirty" class="integration-help">
      免登与消息状态基于已保存连接；上方未保存的选择尚未生效。
    </p>
    <p class="integration-help">
      本应用集成暂不包含钉钉、飞书及原生待办同步。配置齐全不代表外部平台连通性已验证。
    </p>
  </n-spin>
</template>

<script setup>
import { useClipboard } from '@vueuse/core'
import { useDialog, useMessage } from 'naive-ui'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useApplicationIntegrationStore } from '@/stores/application/integrationStore'

const store = useApplicationIntegrationStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const clipboard = useClipboard({ legacy: true })
const editable = computed(() => store.canEdit && store.has('system:collaboration:connection:update'))
const options = computed(() => store.connections.map(item => ({ value: String(item.id), label: `${item.connectionName || item.connectionCode} · ${item.platformName || '企业连接'}`, disabled: !item.loginAvailable && !item.messageAvailable })))
const savedConnection = computed(() => store.connections.find(item => String(item.id) === String(store.config.connectionId)))
const dirty = computed(() => String(store.connectionId || '') !== String(store.config.connectionId || ''))
const channelCode = computed(() => `app_${store.application?.id}_collaboration`)
const entryUrl = computed(() => {
  if (!savedConnection.value || !store.application?.applicationCode)
    return ''
  const href = router.resolve({ path: `/app/${encodeURIComponent(store.application.applicationCode)}`, query: { collaborationConnection: savedConnection.value.connectionCode } }).href
  return new URL(href, window.location.origin).href
})
async function copy(value) {
  try {
    await clipboard.copy(value)
    message.success('已复制')
  }
  catch { message.error('复制失败，请手动选择文本复制') }
}
function manageConnections() {
  router.push('/system/collaboration/connections')
}
function openFlows() {
  router.push({ name: 'BusinessApplicationRuntime', params: { applicationCode: store.application.applicationCode }, query: { ...route.query, view: 'process', settingsSection: undefined } })
}
async function performSave() {
  if (await store.save())
    message.success('应用协同绑定已保存')
}
function save() {
  if (store.config.connectionId && !store.connectionId)
    dialog.warning({ title: '取消企业协同绑定？', content: '本应用的消息通道将停用，已有通知规则再执行时会提示失败。不会删除平台连接、消息记录或客户端授权。', positiveText: '取消绑定', negativeText: '返回', onPositiveClick: performSave })
  else performSave()
}
</script>

<style scoped>
.configuration-sheet {
  border: 1px solid var(--integration-border);
  border-radius: 4px;
  background: var(--integration-surface);
  overflow: hidden;
}
.configuration-section {
  display: grid;
  grid-template-columns: 158px minmax(0, 1fr);
}
.configuration-section + .configuration-section {
  border-top: 1px solid var(--integration-border);
}
.section-label {
  padding: 18px 16px;
  background: var(--integration-hover);
}
.section-content {
  padding: 16px 20px;
  min-width: 0;
}
.section-content :deep(.n-form-item) {
  max-width: 560px;
}
.readiness-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.section-label p {
  margin-bottom: 0;
}
h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
label {
  display: block;
  margin: 12px 0 6px;
  font-size: 12px;
  color: var(--integration-muted);
}
.copy-row {
  display: flex;
  gap: 8px;
  min-width: 0;
}
.message-guide {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px 12px;
  font-size: 13px;
  line-height: 1.6;
  margin: 14px 0;
}
.message-guide dt {
  color: var(--integration-muted);
}
.message-guide dd {
  margin: 0;
}
.message-technical {
  margin-top: 14px;
}
@media (max-width: 700px) {
  .configuration-section {
    grid-template-columns: minmax(0, 1fr);
  }
  .section-label {
    padding: 10px 12px;
  }
  .section-label p {
    display: none;
  }
  .section-content {
    padding: 12px;
  }
}
@media (max-width: 480px) {
  .copy-row {
    flex-direction: column;
  }
}
</style>
