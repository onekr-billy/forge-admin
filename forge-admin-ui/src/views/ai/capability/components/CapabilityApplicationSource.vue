<template>
  <div class="application-source">
    <div v-if="store.sourceContext.lockApplication" class="current-application">
      <span>当前应用</span><strong>{{ store.sourceContext.applicationName || store.sourceContext.applicationCode }}</strong>
      <small>已自动带入，只需选择要开放的页面或表单</small>
    </div>
    <n-form-item v-else label="低代码应用">
      <n-select v-model:value="store.applicationId" :options="applications" :loading="loading" :disabled="store.sourceContext.lockApplication" filterable :clearable="!store.sourceContext.lockApplication" placeholder="选择已发布的应用" @update:value="loadPages" />
    </n-form-item>
    <n-form-item v-if="store.applicationId" label="页面 / 表单">
      <n-select v-model:value="store.pageId" :options="pages" :loading="pagesLoading" :disabled="pagesLoading" filterable placeholder="选择要开放的页面表单" @update:value="selectPage">
        <template #empty>
          <span v-if="pagesLoading">正在读取已发布页面…</span>
          <n-empty v-else size="small" description="当前发布版本没有绑定数据存储的页面，请先保存表单并发布应用。" />
        </template>
      </n-select>
    </n-form-item>
    <p v-if="pagesLoading" class="page-loading" role="status">
      正在加载应用的已发布页面…
    </p>
    <n-alert v-if="error" type="error">
      {{ error }} <n-button text @click="store.applicationId ? loadPages(store.applicationId) : loadApplications()">
        重试
      </n-button>
    </n-alert>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { businessApplicationList, businessApplicationRuntimeByCode } from '@/api/business-application'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
import { publishedPageSources } from '../registrationSources'

const emit = defineEmits(['select'])
const store = useCapabilityRegistrationStore()
const applications = ref([])
const pages = ref([])
const loading = ref(false)
const pagesLoading = ref(false)
const error = ref('')
let generation = 0
let applicationsGeneration = 0
let disposed = false

async function loadApplications() {
  if (store.sourceContext.lockApplication) {
    applications.value = [{ label: store.sourceContext.applicationName, value: String(store.sourceContext.applicationId), code: store.sourceContext.applicationCode }]
    store.applicationId = applications.value[0].value
    await loadPages(store.applicationId)
    return
  }
  const request = ++applicationsGeneration
  loading.value = true
  error.value = ''
  try {
    const response = await businessApplicationList({})
    if (disposed || request !== applicationsGeneration)
      return
    applications.value = (response.data || []).filter(item => item && item.status === 1 && Number(item.lastPublishVersion) > 0).map(item => ({ label: item.applicationName, value: String(item.id), code: item.applicationCode }))
    const seeded = applications.value.find(item => item.code === store.sourceContext.applicationCode || String(item.value) === String(store.applicationId))
    if (seeded) {
      store.applicationId = seeded.value
      await loadPages(seeded.value)
    }
  }
  catch (cause) {
    if (!disposed && request === applicationsGeneration)
      error.value = cause.message || '应用列表加载失败'
  }
  finally {
    if (!disposed && request === applicationsGeneration)
      loading.value = false
  }
}

async function loadPages(id) {
  const request = ++generation
  store.pageId = null
  pages.value = []
  error.value = ''
  emit('select', null)
  const app = applications.value.find(item => String(item.value) === String(id))
  if (!app) {
    pagesLoading.value = false
    return
  }
  pagesLoading.value = true
  try {
    const response = await businessApplicationRuntimeByCode(app.code)
    if (request !== generation)
      return
    pages.value = publishedPageSources(response.data)
    const seeded = pages.value.find(item => item.value === store.sourceContext.pageId)
    if (seeded || pages.value.length === 1) {
      store.pageId = (seeded || pages.value[0]).value
      selectPage(store.pageId)
    }
  }
  catch (cause) {
    if (request === generation)
      error.value = cause.message || '发布页面加载失败'
  }
  finally {
    if (request === generation)
      pagesLoading.value = false
  }
}
function selectPage(value) {
  emit('select', pages.value.find(item => item.value === value) || null)
}
onMounted(loadApplications)
onBeforeUnmount(() => {
  generation++
  disposed = true
})
</script>

<style scoped>
.current-application {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 16px;
  font-size: 13px;
}
.current-application strong {
  color: var(--text-primary);
  font-weight: 500;
}
.current-application span,
.current-application small,
.page-loading {
  color: var(--text-tertiary);
}
.page-loading {
  margin: -10px 0 16px;
  font-size: 12px;
}
</style>
