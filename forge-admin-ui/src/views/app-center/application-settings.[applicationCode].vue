<template>
  <div class="application-settings-page">
    <header class="settings-page-header">
      <div class="settings-page-title">
        <n-button quaternary circle aria-label="返回应用工作台" @click="returnWorkspace">
          <template #icon>
            <n-icon><ArrowBackOutline /></n-icon>
          </template>
        </n-button>
        <div>
          <span>{{ application?.applicationName || '应用' }}</span>
          <h1>应用设置</h1>
        </div>
      </div>
      <n-space>
        <n-button secondary :disabled="!application" @click="openPortal">
          预览门户
        </n-button>
        <n-button type="primary" :loading="saving" :disabled="!application" @click="saveSettings">
          保存设置
        </n-button>
      </n-space>
    </header>

    <n-spin :show="loading">
      <div v-if="application" class="settings-layout">
        <aside class="settings-nav">
          <button
            v-for="item in sections"
            :key="item.key"
            type="button"
            :class="{ active: activeSection === item.key }"
            @click="selectSection(item.key)"
          >
            <n-icon><component :is="item.icon" /></n-icon>
            <span>{{ item.label }}</span>
          </button>
        </aside>
        <main class="settings-content">
          <AppSettingsBasic v-if="activeSection === 'basic'" v-model="settingsModel" />
          <AppSettingsAccess v-else-if="activeSection === 'access'" ref="accessRef" v-model="settingsModel" />
          <AppSettingsNavigation v-else-if="activeSection === 'navigation'" v-model="settingsModel" :pages="applicationPages" />
          <AppSettingsPermission v-else-if="activeSection === 'permission'" v-model="settingsModel" />
          <AppSettingsGlobalization v-else-if="activeSection === 'globalization'" v-model="settingsModel" />
          <AppSettingsAdvanced v-else v-model="settingsModel" />
        </main>
      </div>
      <n-result v-else-if="!loading" status="error" title="应用设置加载失败" :description="loadError">
        <template #footer>
          <n-button @click="loadSettings">
            重新加载
          </n-button>
        </template>
      </n-result>
    </n-spin>
  </div>
</template>

<script setup>
import {
  ArrowBackOutline,
  ColorPaletteOutline,
  EarthOutline,
  LinkOutline,
  LockClosedOutline,
  MenuOutline,
  OptionsOutline,
} from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessApplicationDetailByCode,
  checkBusinessApplicationSlugAvailable,
  saveBusinessApplicationPortalConfig,
  updateBusinessApplication,
} from '@/api/business-application'
import { normalizePortalConfig, parseJsonObject } from './components/portal/portal-config'
import AppSettingsAccess from './components/settings/AppSettingsAccess.vue'
import AppSettingsAdvanced from './components/settings/AppSettingsAdvanced.vue'
import AppSettingsBasic from './components/settings/AppSettingsBasic.vue'
import AppSettingsGlobalization from './components/settings/AppSettingsGlobalization.vue'
import AppSettingsNavigation from './components/settings/AppSettingsNavigation.vue'
import AppSettingsPermission from './components/settings/AppSettingsPermission.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const application = ref(null)
const applicationOptions = ref({})
const settingsModel = ref({})
const accessRef = ref(null)
const activeSection = ref(String(route.query.section || 'basic'))

const sections = [
  { key: 'basic', label: '基础属性', icon: ColorPaletteOutline },
  { key: 'access', label: '访问地址', icon: LinkOutline },
  { key: 'navigation', label: '导航设置', icon: MenuOutline },
  { key: 'permission', label: '应用权限', icon: LockClosedOutline },
  { key: 'globalization', label: '全球化', icon: EarthOutline },
  { key: 'advanced', label: '高级设置', icon: OptionsOutline },
]

const applicationPages = computed(() => applicationOptions.value?.inAppBuilder?.nodes || [])

watch(() => String(route.params.applicationCode || ''), loadSettings, { immediate: true })

async function loadSettings() {
  const code = String(route.params.applicationCode || '')
  if (!code)
    return
  loading.value = true
  loadError.value = ''
  try {
    const response = await businessApplicationDetailByCode(code)
    application.value = response.data || null
    if (!application.value)
      throw new Error('应用不存在')
    applicationOptions.value = parseJsonObject(application.value.options)
    const portal = normalizePortalConfig(application.value.portalConfig)
    const pageOrder = applicationPages.value
      .filter(node => node.type === 'page')
      .sort((left, right) => Number(left.sort || 0) - Number(right.sort || 0))
      .map(node => String(node.id))
    settingsModel.value = {
      ...portal,
      id: application.value.id,
      applicationName: application.value.applicationName || '',
      applicationCode: application.value.applicationCode || '',
      portalSlug: application.value.portalSlug || application.value.applicationCode || '',
      icon: application.value.icon || '',
      description: application.value.description || '',
      status: application.value.status === 0 ? 0 : 1,
      navigation: { ...portal.navigation, pageOrder: portal.navigation.pageOrder?.length ? portal.navigation.pageOrder : pageOrder },
    }
  }
  catch (error) {
    application.value = null
    loadError.value = error?.message || '暂时无法读取应用设置。'
  }
  finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!application.value || saving.value)
    return
  if (!String(settingsModel.value.applicationName || '').trim()) {
    message.error('请输入应用名称')
    activeSection.value = 'basic'
    return
  }
  saving.value = true
  try {
    let slugAvailable = true
    if (accessRef.value)
      slugAvailable = await accessRef.value.checkSlug()
    else
      slugAvailable = (await checkBusinessApplicationSlugAvailable(settingsModel.value.portalSlug, application.value.id)).data === true
    if (!slugAvailable) {
      activeSection.value = 'access'
      message.error('请先修正门户访问地址')
      return
    }
    const nextOptions = applyPageOrder(applicationOptions.value, settingsModel.value.navigation?.pageOrder)
    await updateBusinessApplication({
      id: application.value.id,
      applicationCode: application.value.applicationCode,
      applicationName: String(settingsModel.value.applicationName).trim(),
      suiteCode: application.value.suiteCode,
      icon: settingsModel.value.icon || null,
      description: settingsModel.value.description || null,
      status: settingsModel.value.status,
      options: JSON.stringify(nextOptions),
    })
    await saveBusinessApplicationPortalConfig(application.value.id, {
      portalSlug: settingsModel.value.portalSlug,
      portalConfig: stripApplicationFields(settingsModel.value),
    })
    message.success('应用设置已保存，重新发布后正式门户生效')
    await loadSettings()
  }
  catch (error) {
    message.error(error?.message || '保存应用设置失败')
  }
  finally {
    saving.value = false
  }
}

function selectSection(key) {
  activeSection.value = key
  router.replace({ query: { ...route.query, section: key } })
}

function returnWorkspace() {
  router.push({ name: 'BusinessApplicationWorkspace', params: { applicationCode: route.params.applicationCode } })
}

function openPortal() {
  router.push({ name: 'ApplicationPortal', params: { applicationCodeOrSlug: settingsModel.value.portalSlug || route.params.applicationCode } })
}

function applyPageOrder(options, pageOrder = []) {
  const next = JSON.parse(JSON.stringify(options || {}))
  const builder = next.inAppBuilder
  if (!builder || !Array.isArray(builder.nodes) || !Array.isArray(pageOrder))
    return next
  const rank = new Map(pageOrder.map((id, index) => [String(id), index * 10]))
  builder.nodes = builder.nodes.map(node => rank.has(String(node.id)) ? { ...node, sort: rank.get(String(node.id)) } : node)
  return next
}

function stripApplicationFields(model) {
  const clone = JSON.parse(JSON.stringify(model || {}))
  ;['id', 'applicationName', 'applicationCode', 'portalSlug', 'icon', 'description', 'status'].forEach(key => delete clone[key])
  return clone
}
</script>

<style scoped>
.application-settings-page {
  min-height: 100%;
  background: var(--body-color, #f5f7fa);
}

.settings-page-header {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--border-color, #e5e6eb);
  background: var(--card-color, #fff);
  padding: 0 20px;
}

.settings-page-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.settings-page-title div {
  min-width: 0;
}

.settings-page-title span {
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.settings-page-title h1 {
  margin: 1px 0 0;
  font-size: 18px;
  font-weight: 650;
}

.settings-layout {
  display: grid;
  width: min(1180px, calc(100% - 32px));
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 18px;
  margin: 20px auto;
}

.settings-nav,
.settings-content :deep(.settings-section-card) {
  border: 1px solid var(--border-color, #e5e6eb);
  border-radius: 9px;
  background: var(--card-color, #fff);
}

.settings-nav {
  align-self: start;
  padding: 8px;
}

.settings-nav button {
  display: flex;
  width: 100%;
  height: 42px;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-color-2, #4e5969);
  padding: 0 12px;
  cursor: pointer;
}

.settings-nav button:hover,
.settings-nav button.active {
  background: color-mix(in srgb, var(--primary-color, #3370ff) 9%, transparent);
  color: var(--primary-color, #3370ff);
}

.settings-content :deep(.settings-section-card) {
  padding: 24px;
}

.settings-content :deep(.settings-section-card > header) {
  margin-bottom: 22px;
}

.settings-content :deep(.settings-section-card > header h2) {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
}

.settings-content :deep(.settings-section-card > header p) {
  margin: 6px 0 0;
  color: var(--text-color-3, #86909c);
  font-size: 13px;
}

.settings-content :deep(.settings-info-alert) {
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .settings-page-header {
    padding: 0 12px;
  }

  .settings-layout {
    width: calc(100% - 20px);
    grid-template-columns: 1fr;
    margin: 10px auto;
  }

  .settings-nav {
    display: flex;
    overflow-x: auto;
  }

  .settings-nav button {
    width: auto;
    flex: 0 0 auto;
  }
}
</style>
