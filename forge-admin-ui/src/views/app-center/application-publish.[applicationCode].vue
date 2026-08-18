<template>
  <div class="application-publish-page">
    <header class="publish-page-header">
      <div class="publish-page-title">
        <n-button quaternary circle aria-label="返回应用工作台" @click="returnWorkspace">
          <template #icon>
            <n-icon><ArrowBackOutline /></n-icon>
          </template>
        </n-button>
        <div><span>{{ application?.applicationName || '应用' }}</span><h1>发布与分发</h1></div>
      </div>
      <n-space>
        <n-button secondary :disabled="!application" @click="openSettings">
          应用设置
        </n-button>
        <n-button type="primary" :disabled="!application || application.status !== 1" @click="historyRef?.preparePublish()">
          立即发布
        </n-button>
      </n-space>
    </header>

    <n-spin :show="loading">
      <main v-if="application" class="publish-page-content">
        <AppPublishStatusCard :application="application" :toggling="toggling" @toggle="toggleStatus" @publish="historyRef?.preparePublish()" />
        <div class="publish-page-grid">
          <AppPublishAccess :application="application" />
          <AppPublishAiAssistant :application="application" :pages="pages" @changed="loadApplication" />
        </div>
        <AppPublishDistribute :application="application" @changed="loadApplication" />
        <AppPublishVersionHistory ref="historyRef" :application="application" @changed="loadApplication" @navigate="handleNavigate" />
      </main>
      <n-result v-else-if="!loading" status="error" title="应用发布信息加载失败" :description="loadError">
        <template #footer>
          <n-button @click="loadApplication">
            重新加载
          </n-button>
        </template>
      </n-result>
    </n-spin>
  </div>
</template>

<script setup>
import { ArrowBackOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessApplicationDetailByCode,
  updateBusinessApplicationStatus,
} from '@/api/business-application'
import { parseJsonObject } from './components/portal/portal-config'
import AppPublishAccess from './components/publish/AppPublishAccess.vue'
import AppPublishAiAssistant from './components/publish/AppPublishAiAssistant.vue'
import AppPublishDistribute from './components/publish/AppPublishDistribute.vue'
import AppPublishStatusCard from './components/publish/AppPublishStatusCard.vue'
import AppPublishVersionHistory from './components/publish/AppPublishVersionHistory.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const application = ref(null)
const loading = ref(false)
const toggling = ref(false)
const loadError = ref('')
const historyRef = ref(null)
const pages = computed(() => parseJsonObject(application.value?.options)?.inAppBuilder?.nodes || [])

watch(() => String(route.params.applicationCode || ''), loadApplication, { immediate: true })

async function loadApplication() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await businessApplicationDetailByCode(route.params.applicationCode)
    application.value = response.data || null
    if (!application.value)
      throw new Error('应用不存在')
  }
  catch (error) {
    application.value = null
    loadError.value = error?.message || '暂时无法读取应用发布信息。'
  }
  finally {
    loading.value = false
  }
}

async function toggleStatus() {
  if (!application.value || toggling.value)
    return
  toggling.value = true
  try {
    const nextStatus = application.value.status === 1 ? 0 : 1
    await updateBusinessApplicationStatus(application.value.id, nextStatus)
    message.success(nextStatus === 1 ? '应用已启用' : '应用已停用')
    await loadApplication()
  }
  catch (error) {
    message.error(error?.message || '更新应用状态失败')
  }
  finally {
    toggling.value = false
  }
}

function returnWorkspace() {
  router.push({ name: 'BusinessApplicationWorkspace', params: { applicationCode: route.params.applicationCode } })
}

function openSettings() {
  router.push({ name: 'BusinessApplicationSettings', params: { applicationCode: route.params.applicationCode } })
}

function handleNavigate(section) {
  router.push({
    name: 'BusinessApplicationWorkspace',
    params: { applicationCode: route.params.applicationCode },
    query: { section: section || 'overview' },
  })
}
</script>

<style scoped>
.application-publish-page {
  min-height: 100%;
  background: var(--body-color, #f5f7fa);
}

.publish-page-header {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color, #e5e6eb);
  background: var(--card-color, #fff);
  padding: 0 20px;
}

.publish-page-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.publish-page-title span {
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.publish-page-title h1 {
  margin: 1px 0 0;
  font-size: 18px;
}

.publish-page-content {
  display: grid;
  width: min(1280px, calc(100% - 32px));
  gap: 18px;
  margin: 20px auto;
}

.publish-page-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(0, 1.18fr);
  gap: 18px;
}

.publish-page-content :deep(.publish-section-card) {
  min-width: 0;
  border: 1px solid var(--border-color, #e5e6eb);
  border-radius: 10px;
  background: var(--card-color, #fff);
  padding: 20px;
}

.publish-page-content :deep(.publish-section-card > header) {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.publish-page-content :deep(.publish-section-card > header h2) {
  margin: 0;
  font-size: 17px;
}

.publish-page-content :deep(.publish-section-card > header p) {
  margin: 5px 0 0;
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.publish-page-content :deep(.publish-card-alert) {
  margin-bottom: 18px;
}

@media (max-width: 900px) {
  .publish-page-grid {
    grid-template-columns: 1fr;
  }
}
</style>
