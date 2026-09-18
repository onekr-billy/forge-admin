<script setup>
import { NAlert, NButton, NEmpty, NInput, NModal, NSpace, NSpin, useThemeVars } from 'naive-ui'
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
      <NButton @click="router.push({ path: '/print', query: store.row ? { applicationId: String(store.row.source.applicationId), ...store.row.source } : {} })">
        返回模板列表
      </NButton>
      <template v-if="store.row">
        <NInput v-model:value="store.name" :input-props="{ 'aria-label': '模板名称' }" :maxlength="100" :disabled="!canManage" style="width: 240px" />
        <DictTag dict-type="sys_print_design_status" :value="store.row.designStatus" /><span>修订 {{ store.row.draftRevision }}</span>
        <NSpace class="page-actions">
          <NButton @click="panel('bindings')">
            场景绑定
          </NButton><NButton @click="panel('versions')">
            发布版本
          </NButton>
          <NButton v-if="canPublish" type="primary" :loading="store.saving" :disabled="store.nameDirty || (canManage && canvas.dirty) || !store.name.trim()" @click="store.publish()">
            发布已保存草稿
          </NButton>
        </NSpace>
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
  padding: 10px 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  border-bottom: 1px solid var(--border-light, #ddd);
}
.page-actions {
  margin-left: auto;
}
.page-editor {
  flex: 1;
  min-height: 0;
}
</style>
