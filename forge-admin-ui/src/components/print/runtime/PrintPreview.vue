<script setup>
import { NAlert, NButton, NEmpty, NSelect, NSpin, useThemeVars } from 'naive-ui'
import { computed, ref, shallowRef, watch } from 'vue'
import { layoutPrintDocument } from '../engine/layout'
import { createBrowserMeasurer } from '../engine/measure'
import { preparePrintResources } from '../engine/resources'
import { validateFieldCatalog } from '../protocol/fieldCatalog'
import { PrintError } from '../protocol/types'
import { assertPrintDocument } from '../protocol/validate'
import { createBrowserPrintSession } from './browserPrint'
import PrintPage from './PrintPage.vue'

const props = defineProps({
  template: { type: Object, required: true },
  context: { type: Object, required: true },
  catalog: { type: Array, default: () => [] },
  templateVersion: { type: [String, Number], default: null },
  dataLabel: { type: String, default: '当前已保存数据' },
  allowPrint: { type: Boolean, default: true },
  resolveFile: { type: Function, default: undefined },
})
const emit = defineEmits(['ready', 'error', 'execution'])
const theme = useThemeVars()
const themeStyle = computed(() => ({ '--bg-primary': theme.value.cardColor, '--gray-100': theme.value.bodyColor, '--text-primary': theme.value.textColor1, '--border-light': theme.value.borderColor }))
const layout = shallowRef(null)
const loading = ref(false)
const printing = ref(false)
const error = shallowRef(null)
const zoom = ref(0.8)
// Technical view scales are editor controls, not persisted business enums.
const zoomOptions = [0.5, 0.8, 1, 1.25].map(value => ({ value, label: `${Math.round(value * 100)}%` }))
let printSession
let generation = 0
let activeController

watch(() => [props.template, props.context, props.catalog, props.templateVersion], async (_, __, onCleanup) => {
  const current = ++generation
  const controller = new AbortController()
  activeController = controller
  let resources
  onCleanup(() => {
    if (current === generation) {
      generation++
    }
    controller.abort()
    printSession?.dispose()
    resources?.dispose()
  })
  layout.value = null
  error.value = null
  loading.value = true
  try {
    assertPrintDocument(props.template)
    const issues = validateFieldCatalog(props.template, props.catalog)
    if (issues.length) {
      throw new PrintError('FIELD_NOT_ALLOWED', issues[0].message, issues[0].path)
    }
    resources = await preparePrintResources(props.template, props.context, { signal: controller.signal, resolveFile: props.resolveFile })
    if (controller.signal.aborted) {
      resources.dispose()
      return
    }
    const measure = createBrowserMeasurer()
    try {
      layout.value = layoutPrintDocument(props.template, props.context, { measure, resources, catalog: props.catalog, templateVersion: props.templateVersion })
      emit('ready', layout.value)
    }
    finally {
      measure.dispose()
    }
  }
  catch (reason) {
    resources?.dispose()
    if (!controller.signal.aborted && current === generation) {
      error.value = reason
      emit('error', reason)
    }
  }
  finally {
    if (current === generation) {
      loading.value = false
    }
  }
}, { immediate: true, deep: true })

async function print() {
  if (!props.allowPrint || !layout.value || printing.value) {
    return
  }
  printing.value = true
  const current = generation
  try {
    printSession?.dispose()
    const session = await createBrowserPrintSession(layout.value, { signal: activeController.signal, onEvent: event => emit('execution', event) })
    if (current !== generation) {
      session.dispose()
      return
    }
    printSession = session
    session.print()
  }
  catch (reason) {
    if (current === generation) {
      error.value = reason
      emit('error', reason)
    }
  }
  finally {
    printing.value = false
  }
}
</script>

<template>
  <section class="print-preview" :style="themeStyle">
    <div class="print-toolbar">
      <span v-if="layout">共 {{ layout.pages.length }} 页 · {{ dataLabel }} · {{ layout.generatedAt }}</span>
      <span v-else>打印预览</span>
      <div class="print-actions">
        <NSelect v-model:value="zoom" aria-label="预览缩放" :options="zoomOptions" style="width: 100px" size="small" />
        <NButton v-if="allowPrint" type="primary" :disabled="!layout || loading || !!error" :loading="printing" @click="print">
          打印
        </NButton>
      </div>
    </div>
    <NAlert v-if="error" type="error" title="无法准备打印" :bordered="false">
      {{ error.message }}<span v-if="error.path">（{{ error.path }}）</span>
    </NAlert>
    <div class="print-scroll">
      <NSpin :show="loading">
        <div v-if="layout" class="print-pages">
          <div v-for="page in layout.pages" :key="page.number" class="print-paper-space" :style="{ width: `${layout.geometry.widthMm * zoom}mm`, height: `${layout.geometry.heightMm * zoom}mm` }">
            <div :style="{ transform: `scale(${zoom})`, transformOrigin: 'top left' }">
              <PrintPage :page="page" :geometry="layout.geometry" />
            </div>
          </div>
        </div>
        <NEmpty v-else-if="!loading && !error" description="暂无打印内容" />
      </NSpin>
    </div>
  </section>
</template>

<style scoped>
.print-preview {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background: var(--bg-primary, #fff);
  color: var(--text-primary, #222);
}
.print-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-light, #ddd);
  font-size: 12px;
  flex-wrap: wrap;
}
.print-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.print-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  background: var(--gray-100, #f6f8fb);
}
.print-pages {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  min-width: max-content;
}
.print-paper-space {
  flex: none;
  outline: 1px solid #d6d6d6;
}
</style>
