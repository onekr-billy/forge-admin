<script setup>
import { ArrowBackOutline, CloudUploadOutline, GitBranchOutline, LinkOutline } from '@vicons/ionicons5'
import { NAlert, NButton, NEmpty, NIcon, NInput, NModal, NSpin, useThemeVars } from 'naive-ui'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { loadPrintFile } from '@/api/print'
import DictTag from '@/components/DictTag.vue'
import PrintDesigner from '@/components/print/designer/PrintDesigner.vue'
import PrintBindingPanel from '@/components/print/management/PrintBindingPanel.vue'
import PrintTemplateVersions from '@/components/print/management/PrintTemplateVersions.vue'
import PrintPreview from '@/components/print/runtime/PrintPreview.vue'
import { useUserStore } from '@/store'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { usePrintTemplateStore } from '@/stores/print/printTemplateStore'

const route = useRoute()
const router = useRouter()
const store = usePrintTemplateStore()
const canvas = usePrintDesignerStore()
const user = useUserStore()
const theme = useThemeVars()
const themeStyle = computed(() => ({ background: theme.value.bodyColor, color: theme.value.textColor1 }))
const designer = ref(null)
const allowed = permission => (user.getDataPermission || []).some(p => p === '**' || p === permission)
const canManage = computed(() => allowed('print:template:manage'))
const canPublish = computed(() => allowed('print:template:publish'))
const canLeave = () => designer.value?.canLeave() ?? true
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
onBeforeUnmount(() => store.clear())
watch(() => route.query.templateId, (id) => {
  if (/^[1-9]\d*$/.test(String(id || ''))) {
    store.open(id)
  }
  else {
    store.clear()
    store.error = '请从打印模板列表选择模板'
  }
}, { immediate: true })
async function panel(name) {
  try {
    if (name === 'versions')
      await store.loadVersions()
    store.panel = name
  }
  catch (error) {
    store.error = error.message || '读取失败'
  }
}
async function restore(id) {
  if (!canManage.value || !await canLeave())
    return
  try {
    const before = canvas.serialize()
    const nameBefore = store.name
    const document = await store.versionDocument(id)
    if (!document)
      return
    if ((before !== canvas.serialize() || nameBefore !== store.name) && !await canLeave())
      return
    canvas.execute((draft) => {
      for (const key of Object.keys(draft)) delete draft[key]
      Object.assign(draft, document)
    })
    store.panel = null
  }
  catch (error) {
    store.error = error.message || '载入版本失败'
  }
}
</script>

<template>
  <div class="print-designer-page" :style="themeStyle">
    <header class="page-toolbar">
      <NButton quaternary size="small" @click="router.push({ path: '/print', query: store.row ? { applicationId: String(store.row.source.applicationId), ...store.row.source } : {} })">
        <template #icon>
          <NIcon :component="ArrowBackOutline" />
        </template>
        模板列表
      </NButton>
      <template v-if="store.row">
        <NInput v-model:value="store.name" :input-props="{ 'aria-label': '模板名称' }" :maxlength="100" :disabled="!canManage" class="template-name" size="small" />
        <div class="template-meta">
          <DictTag dict-type="sys_print_design_status" :value="store.row.designStatus" /><span>修订 {{ store.row.draftRevision }}</span>
        </div>
        <div class="page-actions">
          <button type="button" class="page-tool" title="场景绑定" aria-label="场景绑定" @click="panel('bindings')">
            <NIcon :component="LinkOutline" />
          </button>
          <button type="button" class="page-tool" title="发布版本" aria-label="发布版本" @click="panel('versions')">
            <NIcon :component="GitBranchOutline" />
          </button>
          <NButton v-if="canPublish" type="primary" size="small" :loading="store.saving" :disabled="store.nameDirty || (canManage && canvas.dirty) || !store.name.trim()" @click="store.publish()">
            <template #icon>
              <NIcon :component="CloudUploadOutline" />
            </template>
            发布
          </NButton>
        </div>
      </template>
    </header>
    <NAlert v-if="store.error && !canvas.error.includes(store.error)" type="error">
      {{ store.error }}
    </NAlert>
    <NAlert v-else-if="store.notice && store.notice !== canvas.notice" type="info" closable @close="store.notice = ''">
      {{ store.notice }}
    </NAlert>
    <NSpin v-if="store.loading" show style="padding: 60px" />
    <div v-else-if="store.document" class="page-editor">
      <PrintDesigner v-if="canManage" ref="designer" :key="store.row.id" :template="store.document" :catalog="store.catalog" :save-draft="store.save" :external-dirty="store.nameDirty" :resolve-file="loadPrintFile" />
      <PrintPreview v-else :template="store.document" :context="{}" :catalog="store.catalog" :resolve-file="loadPrintFile" data-label="模板预览" :allow-print="false" />
    </div>
    <NEmpty v-else description="未载入打印模板" />
    <NModal :show="!!store.panel" preset="card" :title="store.panel === 'versions' ? '发布版本' : '场景绑定'" style="width: min(720px, 94vw)" @update:show="value => { if (!value) store.panel = null }">
      <PrintTemplateVersions v-if="store.panel === 'versions'" :can-restore="canManage && !store.saving" @restore="restore" />
      <PrintBindingPanel v-if="store.panel === 'bindings'" :can-manage="canManage" />
    </NModal>
  </div>
</template>

<style scoped>
.print-designer-page {
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary, #fff);
}
.page-toolbar {
  min-height: 44px;
  padding: 5px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--border-light, #ddd);
}
.template-name {
  width: min(240px, 30vw);
}
.template-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #64748b;
  font-size: 11px;
  white-space: nowrap;
}
.page-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 3px;
}
.page-tool {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 5px;
  color: inherit;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
}
.page-tool:hover {
  color: #356cde;
  border-color: rgb(53 108 222 / 22%);
  background: rgb(53 108 222 / 8%);
}
.page-editor {
  flex: 1;
  min-height: 0;
}
@media (max-width: 720px) {
  .template-meta {
    display: none;
  }
  .template-name {
    min-width: 120px;
    flex: 1;
  }
}
</style>
