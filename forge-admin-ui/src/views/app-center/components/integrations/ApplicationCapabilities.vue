<template>
  <n-result v-if="!store.has('ai:capability:query')" status="403" title="需要能力目录查看权限" />
  <template v-else>
    <div class="integration-toolbar">
      <n-input v-model:value="store.keyword" clearable placeholder="搜索本应用能力" aria-label="搜索本应用能力" class="capability-search" @keyup.enter="search" @clear="search" />
      <n-space>
        <n-button :loading="store.loading.capabilities" @click="store.loadCapabilities">
          刷新
        </n-button><n-button type="primary" :disabled="!store.published || !store.registerTypes.length" @click="openRegister()">
          注册能力
        </n-button>
      </n-space>
    </div>
    <n-alert v-if="store.errors.capabilities" type="error" class="integration-error">
      {{ store.errors.capabilities }} <n-button text @click="store.loadCapabilities">
        重试
      </n-button>
    </n-alert>
    <n-spin :show="store.loading.capabilities">
      <n-empty v-if="!store.capabilities.length" class="integration-empty" :description="store.keyword ? '没有匹配的应用能力' : '本应用还没有对外能力'">
        <template #extra>
          <p class="integration-help">
            从已发布页面注册表单填报，或开放送审、审批、撤回等流程操作。
          </p><n-button v-if="!store.keyword" :disabled="!store.published || !store.registerTypes.length" @click="openRegister()">
            开放第一个能力
          </n-button>
        </template>
      </n-empty>
      <div v-else class="capability-grid">
        <article v-for="item in store.capabilities" :key="item.id" class="integration-card">
          <div class="integration-row">
            <span class="integration-title">{{ item.capabilityName }}</span><DictTag dict-type="ai_capability_publish_status" :value="item.publishStatus" />
          </div>
          <div class="integration-code">
            {{ item.capabilityCode }}
          </div>
          <p class="integration-help">
            {{ item.description || '已注册，授权后外部系统可按接口契约调用。' }}
          </p>
          <div><DictTag dict-type="ai_capability_source_type" :value="item.sourceType" size="small" /></div>
          <footer>
            <span class="metadata">v{{ item.currentVersion }}</span>
            <n-button text type="primary" :disabled="!store.has('ai:capability:grant:query')" @click="store.selectCapability(item, 'grants')">
              接入授权
            </n-button>
            <n-button text @click="guide = item">
              调用指南
            </n-button>
            <n-dropdown :options="moreOptions(item)" @select="key => more(key, item)">
              <n-button quaternary size="small" aria-label="更多能力操作">
                更多
              </n-button>
            </n-dropdown>
          </footer>
        </article>
      </div>
      <n-pagination v-if="store.total > 12" v-model:page="store.page" :page-size="12" :item-count="store.total" class="integration-pagination" @update:page="store.loadCapabilities" />
    </n-spin>
    <p class="integration-help">
      这里只展示从本应用注册的能力。应用重新发布不会自动升级接口契约；固定版本授权保持不变。
    </p>
    <CapabilityRegisterModal v-if="registerVisible" v-model:show="registerVisible" :capability="upgrade" :allowed-types="store.registerTypes" :initial-context="context" @success="registered" />
    <CapabilityCallGuideModal v-if="guide" :show="true" :capability="guide" :can-update-grant="store.canEdit && store.has('ai:capability:grant:add')" @update:show="value => !value && (guide = null)" />
  </template>
</template>

<script setup>
import { computed, ref } from 'vue'
import DictTag from '@/components/DictTag.vue'
import { useApplicationIntegrationStore } from '@/stores/application/integrationStore'
import CapabilityCallGuideModal from '@/views/ai/capability/components/CapabilityCallGuideModal.vue'
import CapabilityRegisterModal from '@/views/ai/capability/components/CapabilityRegisterModal.vue'

const store = useApplicationIntegrationStore()
const registerVisible = ref(false)
const upgrade = ref(null)
const guide = ref(null)
const context = computed(() => ({ applicationId: store.application?.id, applicationCode: store.application?.applicationCode, applicationName: store.application?.applicationName, lockApplication: true }))
function openRegister(item = null) {
  upgrade.value = item
  registerVisible.value = true
}
function search() {
  store.page = 1
  store.loadCapabilities()
}
function moreOptions(item) {
  return [{ key: 'logs', label: '查看调用记录', disabled: !store.has('ai:capability:invocation:query') }, { key: 'upgrade', label: '发布新版本', disabled: !store.published || !store.registerTypes.includes(item.sourceType) }]
}
function more(key, item) {
  if (key === 'logs')
    store.selectCapability(item, 'logs')
  else openRegister(item)
}
async function registered() {
  store.page = 1
  await store.loadCapabilities()
}
</script>

<style scoped>
.capability-search {
  width: min(100%, 320px);
}
.capability-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  gap: 12px;
}
.integration-code {
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
article {
  display: flex;
  flex-direction: column;
}
article > p {
  flex: 1;
}
.metadata {
  font-size: 12px;
  color: var(--integration-muted);
}
footer {
  display: flex;
  gap: 12px;
  align-items: center;
  border-top: 1px solid var(--integration-border);
  margin: 12px -14px -14px;
  padding: 9px 12px;
  background: var(--integration-hover);
}
footer > :first-child {
  margin-right: auto;
}
</style>
