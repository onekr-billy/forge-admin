<script setup>
import { NAlert, NButton, NEmpty, NSelect, NSpin } from 'naive-ui'
import { computed, onBeforeUnmount, watch } from 'vue'
import { loadPrintFile } from '@/api/print'
import { usePrintRuntimeStore } from '@/stores/print/printRuntimeStore'
import PrintPreview from './PrintPreview.vue'

const props = defineProps({ record: { type: Object, required: true } })
const store = usePrintRuntimeStore()
const options = computed(() => store.options.map(item => ({ label: `${item.templateName} · v${item.versionNo}`, value: item.id })))
watch(() => props.record, value => store.open(value), { immediate: true, deep: true })
onBeforeUnmount(() => store.close())
</script>

<template>
  <section class="runtime-picker">
    <div class="picker-toolbar">
      <NSelect :value="store.selectedId" :options="options" :loading="store.loading" placeholder="选择打印模板" aria-label="打印模板" style="width: 300px" @update:value="store.select" /><NButton :disabled="store.loading" @click="store.open(record)">
        重新读取已保存数据
      </NButton>
    </div>
    <NAlert v-if="store.error" type="error">
      {{ store.error }}
    </NAlert>
    <NAlert v-if="store.eventError" type="warning">
      {{ store.eventError }}<NButton text :loading="store.eventPending" @click="store.event(store.pendingEvent)">
        重试记录事件
      </NButton>
    </NAlert>
    <NSpin v-if="store.loading" show style="padding: 40px" />
    <PrintPreview v-else-if="store.prepared" :key="store.prepared.executionId" :template="store.prepared.template" :context="store.prepared.context" :catalog="store.prepared.catalog.fields" :template-version="store.prepared.templateVersionId" :resolve-file="loadPrintFile" @execution="store.event" @error="store.failed" />
    <NEmpty v-else-if="!store.error" :description="store.options.length ? '请选择打印模板' : '当前记录没有可用的已发布打印模板'" />
  </section>
</template>

<style scoped>
.runtime-picker {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
}
.runtime-picker :deep(.print-preview) {
  flex: 1;
  min-height: 0;
}
</style>
