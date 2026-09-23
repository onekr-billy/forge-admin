<template>
  <section class="application-integrations" :style="themeVars">
    <header class="integration-heading">
      <div><h2>集成与开放</h2><p>企业连接、对外能力与接入授权，在当前应用内集中管理。</p></div>
      <span class="application-context">{{ application?.applicationName }} · {{ store.published ? `已发布 v${application.lastPublishVersion}` : '尚无可用发布版本' }}</span>
    </header>
    <n-alert v-if="!store.published" type="info" :bordered="false" class="integration-notice">
      可以先配置企业连接。请发布并启用应用后，再分发访问入口或注册对外能力。
    </n-alert>
    <n-tabs v-model:value="store.tab" type="line" animated>
      <n-tab-pane name="collaboration" tab="企业协同">
        <ApplicationCollaboration />
      </n-tab-pane>
      <n-tab-pane name="capabilities" tab="对外能力">
        <ApplicationCapabilities />
      </n-tab-pane>
      <n-tab-pane name="grants" tab="接入授权">
        <ApplicationCapabilityAccess />
      </n-tab-pane>
      <n-tab-pane name="logs" tab="调用记录">
        <ApplicationCapabilityLogs />
      </n-tab-pane>
    </n-tabs>
  </section>
</template>

<script setup>
import { useThemeVars } from 'naive-ui'
import { computed, onBeforeUnmount, watch } from 'vue'
import { useApplicationIntegrationStore } from '@/stores/application/integrationStore'
import ApplicationCapabilities from './ApplicationCapabilities.vue'
import ApplicationCapabilityAccess from './ApplicationCapabilityAccess.vue'
import ApplicationCapabilityLogs from './ApplicationCapabilityLogs.vue'
import ApplicationCollaboration from './ApplicationCollaboration.vue'

const props = defineProps({ application: { type: Object, required: true } })
const store = useApplicationIntegrationStore()
const theme = useThemeVars()
const themeVars = computed(() => ({ '--integration-border': theme.value.borderColor, '--integration-muted': theme.value.textColor3, '--integration-surface': theme.value.cardColor, '--integration-hover': theme.value.hoverColor, '--integration-text': theme.value.textColor1 }))
watch(() => props.application?.id, () => store.reset(props.application), { immediate: true })
// A publish/enable operation may replace the application object without changing its ID.
watch(() => props.application, (application) => {
  store.application = application
})
watch([() => props.application?.id, () => store.tab], () => {
  if (store.tab === 'collaboration')
    store.loadCollaboration()
  else if (store.tab === 'capabilities')
    store.loadCapabilities()
  else if (store.tab === 'grants')
    store.loadGrants()
  else store.loadLogs()
}, { immediate: true })
onBeforeUnmount(() => store.reset(null))
</script>

<style scoped>
.application-integrations {
  min-width: 0;
  color: var(--integration-text);
}
.integration-heading {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}
.integration-heading p {
  margin: 6px 0 0;
  color: var(--integration-muted);
  font-size: 12px;
  line-height: 1.6;
}
.application-context {
  padding: 3px 0;
  color: var(--integration-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.integration-notice {
  margin-bottom: 14px;
}
.application-integrations :deep(.integration-toolbar) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
  margin: 4px 0 12px;
}
.application-integrations :deep(.integration-help) {
  color: var(--integration-muted);
  font-size: 12px;
  line-height: 1.65;
}
.application-integrations :deep(.integration-card) {
  border: 1px solid var(--integration-border);
  border-radius: 4px;
  background: var(--integration-surface);
  padding: 14px;
  min-width: 0;
}
.application-integrations :deep(.integration-error) {
  margin-bottom: 16px;
}
.application-integrations :deep(.integration-pagination) {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
.application-integrations :deep(.integration-empty) {
  padding: 36px 12px;
}
.application-integrations :deep(.integration-row) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  justify-content: space-between;
}
.application-integrations :deep(.integration-title) {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.application-integrations :deep(.integration-code) {
  font-size: 12px;
  color: var(--integration-muted);
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .integration-heading {
    display: grid;
  }
  .application-integrations :deep(.integration-card) {
    padding: 14px;
  }
}
</style>
