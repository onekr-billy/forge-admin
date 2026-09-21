<template>
  <n-result v-if="!store.has('system:collaboration:connection:list')" status="403" title="需要企业协同查看权限" description="请联系管理员授权；应用编辑权限不会自动开放连接和凭据。" />
  <n-spin v-else :show="store.loading.collaboration">
    <div class="integration-toolbar">
      <span class="integration-help">复用平台连接，不重复填写企业 ID、应用 Secret。</span>
      <n-button size="small" :disabled="store.writing" @click="store.loadCollaboration">
        刷新状态
      </n-button>
    </div>
    <n-alert v-if="store.errors.collaboration" type="error" class="integration-error">
      {{ store.errors.collaboration }}
    </n-alert>
    <div class="collaboration-layout">
      <section class="integration-card">
        <h3>连接企业工作台</h3>
        <p class="integration-help">
          选择已配置的企业连接，保存后生成本应用的访问入口和消息通道。
        </p>
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
          <n-button type="primary" :loading="store.writing" :disabled="!editable || !dirty" @click="save">
            保存绑定
          </n-button>
          <n-button text @click="manageConnections">
            平台连接管理 ↗
          </n-button>
        </div>
        <p class="integration-help">
          保存立即生效，不需要重新发布应用。刷新状态保留未保存的选择；取消绑定会停用本应用的消息通道。
        </p>
      </section>
      <section class="integration-card readiness">
        <h3>绑定后可以做什么</h3>
        <div>
          <strong>工作台免登</strong><span class="integration-help">企业微信内打开应用，无需重复输入账号密码。仍遵循应用访问权限。</span><n-tag size="small" :type="savedConnection?.loginAvailable ? 'success' : 'default'">
            {{ savedConnection?.loginAvailable ? '连接已就绪' : '需配置 LOGIN 应用与免登开关' }}
          </n-tag>
        </div>
        <div>
          <strong>业务消息通知</strong><span class="integration-help">在业务流程的发送消息节点勾选企业协同，使用已有模板通知流程发起人。</span><n-tag size="small" :type="savedConnection?.messageAvailable ? 'success' : 'default'">
            {{ savedConnection?.messageAvailable ? '消息通道可用' : '需配置 MESSAGE 应用' }}
          </n-tag>
        </div>
        <div><strong>待办同步 / 钉钉 / 飞书</strong><span class="integration-help">当前未接入，不会将普通消息通知标记为待办同步。</span></div>
      </section>
    </div>
    <section v-if="savedConnection" class="integration-card entry-card">
      <div class="integration-row">
        <h3>分发与使用</h3><span v-if="dirty" class="integration-help">以下使用已保存的连接，未保存选择不生效</span>
      </div>
      <label>企业微信工作台应用主页</label>
      <div class="copy-row">
        <n-input :value="entryUrl" readonly aria-label="企业微信应用访问地址" /><n-button :disabled="!entryUrl || !store.published || !savedConnection.loginAvailable" @click="copy(entryUrl)">
          复制地址
        </n-button>
      </div>
      <p class="integration-help">
        使用本站 PC 门户地址；请在企业微信后台配置可信域名和应用可见范围。普通浏览器打开仍需正常登录。
      </p>
      <template v-if="savedConnection.messageAvailable">
        <label>业务流程消息通道</label>
        <div class="copy-row">
          <n-input :value="channelCode" readonly aria-label="业务消息通道编码" /><n-button @click="copy(channelCode)">
            复制通道
          </n-button>
        </div>
        <p class="integration-help">
          在发送消息节点勾选“通过本应用企业协同通道发送”，选择消息模板并发布流程。当前通知流程发起人，须先绑定企业账号；绑定连接本身不会触发消息。
        </p>
        <n-button @click="openFlows">
          配置业务流程通知
        </n-button>
      </template>
    </section>
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
.collaboration-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: 16px;
}
h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.readiness > div {
  display: grid;
  gap: 6px;
  justify-items: start;
  padding: 12px 0;
  border-top: 1px solid var(--integration-border);
}
.entry-card {
  margin-top: 16px;
}
label {
  display: block;
  margin: 14px 0 8px;
  font-size: 13px;
}
.copy-row {
  display: flex;
  gap: 8px;
  min-width: 0;
}
@media (max-width: 1000px) {
  .collaboration-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 480px) {
  .copy-row {
    flex-direction: column;
  }
}
</style>
