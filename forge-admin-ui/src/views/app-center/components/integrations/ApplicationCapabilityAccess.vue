<template>
  <n-result v-if="!store.has('ai:capability:grant:query')" status="403" title="需要能力授权查看权限" />
  <template v-else>
    <ApplicationCapabilitySelection />
    <template v-if="store.selected">
      <div class="integration-toolbar">
        <span class="integration-help">按能力授权，不会自动开放本应用的其他接口。</span><n-space>
          <n-button :loading="store.loading.grants" @click="store.loadGrants">
            刷新
          </n-button><n-button type="primary" :disabled="!canGrant || store.loading.grants" @click="openGrant">
            授权接入系统
          </n-button>
        </n-space>
      </div>
      <n-alert v-if="store.errors.grants" type="error" class="integration-error">
        {{ store.errors.grants }}
      </n-alert>
      <n-spin :show="store.loading.grants">
        <n-empty v-if="!store.grants.length" description="还没有系统获得此能力的授权" class="integration-empty">
          <template #extra>
            <p class="integration-help">
              先在平台“接入系统”创建客户端，再授予这里选中的能力。
            </p><n-button v-if="store.has('ai:capability:client:query')" @click="router.push('/open-platform/capability-client')">
              管理接入系统 ↗
            </n-button>
          </template>
        </n-empty>
        <div v-else class="grant-list">
          <article v-for="item in store.grants" :key="item.id" class="integration-card">
            <div class="integration-row">
              <span class="integration-title">{{ clientName(item.clientId) }}</span><DictTag dict-type="ai_capability_grant_status" :value="item.status" />
            </div>
            <div class="grant-details integration-help">
              <span><DictTag dict-type="ai_capability_version_strategy" :value="item.versionStrategy" /> · v{{ item.fixedVersion }}</span><span>有效期：{{ item.expiresAt || '长期有效' }}</span>
            </div>
            <div class="integration-row">
              <span class="integration-code">授权后仍校验实际调用人的业务权限</span><n-popconfirm v-if="item.status === 'ENABLED' && store.canEdit && store.has('ai:capability:grant:revoke')" @positive-click="revoke(item)">
                <template #trigger>
                  <n-button text type="error" :disabled="store.writing">
                    撤销授权
                  </n-button>
                </template>撤销后该系统不能再调用这项能力，是否继续？
              </n-popconfirm>
            </div>
          </article>
        </div>
        <n-pagination v-if="store.grantTotal > 10" v-model:page="store.grantPage" :page-size="10" :item-count="store.grantTotal" class="integration-pagination" @update:page="store.loadGrants" />
      </n-spin>
      <n-modal v-model:show="showGrant" preset="card" title="授权接入系统" class="application-grant-modal" :mask-closable="false" :closable="!store.writing" :close-on-esc="!store.writing">
        <n-alert type="info" :bordered="false">
          仅授权“{{ store.selected.capabilityName }}”。继承该版本已经发布的字段范围，不授予额外业务权限。
        </n-alert>
        <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" class="grant-form">
          <n-form-item label="接入系统" path="clientId">
            <n-select v-model:value="form.clientId" :options="clientOptions" filterable placeholder="选择已有客户端">
              <template #empty>
                没有可用客户端，请先在开放平台创建接入系统。
              </template>
            </n-select>
          </n-form-item>
          <n-form-item label="版本策略" path="versionStrategy">
            <n-select v-model:value="form.versionStrategy" :options="versionOptions" :loading="dictLoading" />
          </n-form-item>
          <p class="integration-help">
            当前基准版本 v{{ store.selected.currentVersion }}。固定版本不会随应用或能力更新自动切换。
          </p>
          <n-form-item label="有效期（可选）">
            <n-date-picker v-model:formatted-value="form.expiresAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" clearable class="expiry-input" />
          </n-form-item>
        </n-form>
        <n-alert v-if="formError || store.errors.grants" type="error">
          {{ formError || store.errors.grants }}
        </n-alert>
        <template #footer>
          <n-space justify="end">
            <n-button :disabled="store.writing" @click="showGrant = false">
              取消
            </n-button><n-button type="primary" :loading="store.writing" :disabled="!versionOptions.length" @click="submit">
              确认授权
            </n-button>
          </n-space>
        </template>
      </n-modal>
    </template>
  </template>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'
import { useApplicationIntegrationStore } from '@/stores/application/integrationStore'
import ApplicationCapabilitySelection from './ApplicationCapabilitySelection.vue'

const store = useApplicationIntegrationStore()
const router = useRouter()
const message = useMessage()
const { dict, loading: dictLoading, reload } = useDict('ai_capability_version_strategy')
const versionOptions = computed(() => dict.value.ai_capability_version_strategy || [])
const showGrant = ref(false)
const formRef = ref(null)
const formError = ref('')
const form = reactive({ clientId: null, versionStrategy: 'PINNED', expiresAt: null })
const canGrant = computed(() => store.canEdit && store.has('ai:capability:grant:add') && store.selected?.publishStatus === 'PUBLISHED' && store.selected?.enabled === 1)
const clientOptions = computed(() => store.clients.map(item => ({ label: `${item.clientName} · ${item.clientCode}`, value: String(item.id), disabled: item.status !== 'ENABLED' })))
const rules = { clientId: { required: true, message: '请选择接入系统', trigger: 'change' }, versionStrategy: { required: true, message: '请选择版本策略', trigger: 'change' } }
function clientName(id) {
  return store.clients.find(item => String(item.id) === String(id))?.clientName || `接入系统 ${id}`
}
function openGrant() {
  Object.assign(form, { clientId: null, versionStrategy: 'PINNED', expiresAt: null })
  formError.value = ''
  showGrant.value = true
  reload('ai_capability_version_strategy')
}
let validating = false
async function submit() {
  if (validating || store.writing || !canGrant.value)
    return
  validating = true
  const applicationId = store.application.id
  const capabilityId = store.selected.id
  try {
    await formRef.value?.validate()
    if (!showGrant.value || store.application?.id !== applicationId || store.selected?.id !== capabilityId)
      return
    formError.value = ''
    if (form.expiresAt && new Date(form.expiresAt.replace(' ', 'T')).getTime() <= Date.now()) {
      formError.value = '有效期必须晚于当前时间'
      return
    }
    if (await store.grant({ ...form, fixedVersion: store.selected.currentVersion, fieldPolicy: null })) {
      showGrant.value = false
      message.success('能力授权已生效')
    }
  }
  catch { /* Field validation is displayed alongside its input. */ }
  finally { validating = false }
}
async function revoke(item) {
  if (await store.revoke(item.id))
    message.success('授权已撤销')
}
</script>

<style scoped>
.grant-list {
  display: grid;
  gap: 12px;
}
.grant-details {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin: 10px 0;
}
.grant-form {
  margin-top: 20px;
}
.expiry-input {
  width: 100%;
}
</style>

<style>
.application-grant-modal.n-card {
  width: min(540px, calc(100vw - 24px));
  max-height: calc(100dvh - 32px);
}
.application-grant-modal > .n-card__content {
  overflow: auto;
  min-height: 0;
}
</style>
