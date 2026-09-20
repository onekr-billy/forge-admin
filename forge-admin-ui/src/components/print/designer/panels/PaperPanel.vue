<script setup>
import { NButton, NFormItem, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
const orientations = [{ label: '纵向', value: 'PORTRAIT' }, { label: '横向', value: 'LANDSCAPE' }]
function paper(key, value) {
  if (value === null)
    return
  if (key === 'orientation') {
    store.execute((doc) => {
      doc.paper.orientation = value
      store.clampDocumentToPaper(doc)
    })
    return
  }
  store.execute((doc) => { doc.paper[key] = value })
}
function margin(key, value) {
  if (value !== null) {
    store.execute((doc) => {
      doc.paper.marginMm[key] = value
      store.clampDocumentToPaper(doc)
    })
  }
}
function preset(width, height) {
  store.setPaperSize(width, height)
}
</script>

<template>
  <section class="designer-group">
    <h3>纸张与页边距</h3>
    <div class="panel-row paper-presets">
      <NButton size="tiny" @click="preset(297, 420)">
        A3
      </NButton>
      <NButton size="tiny" @click="preset(210, 297)">
        A4
      </NButton>
      <NButton size="tiny" @click="preset(148, 210)">
        A5
      </NButton>
      <NButton size="tiny" @click="preset(250, 353)">
        B4
      </NButton>
      <NButton size="tiny" @click="preset(176, 250)">
        B5
      </NButton>
    </div>
    <NFormItem label="方向" size="small">
      <NSelect :value="store.document.paper.orientation" :options="orientations" @update:value="paper('orientation', $event)" />
    </NFormItem>
    <div class="panel-grid">
      <NFormItem v-for="(label, key) in { widthMm: '短边 mm', heightMm: '长边 mm' }" :key="key" :label="label" size="small">
        <NInputNumber :value="store.document.paper[key]" :min="10" :max="2000" :show-button="false" @update:value="paper(key, $event)" />
      </NFormItem>
      <NFormItem v-for="(label, key) in { top: '上边距 mm', right: '右边距 mm', bottom: '下边距 mm', left: '左边距 mm' }" :key="key" :label="label" size="small">
        <NInputNumber :value="store.document.paper.marginMm[key]" :min="0" :show-button="false" @update:value="margin(key, $event)" />
      </NFormItem>
    </div>
    <template v-for="(label, key) in { header: '页眉', footer: '页脚' }" :key="key">
      <div class="panel-grid band-row">
        <NFormItem :label="`${label}高度 mm`" size="small">
          <NInputNumber :value="store.document[key].heightMm" :min="0" :show-button="false" @update:value="$event !== null && store.execute(doc => { doc[key].heightMm = $event })" />
        </NFormItem>
        <NFormItem :label="`${label}每页重复`" size="small">
          <NSwitch :value="store.document[key].repeat" @update:value="store.execute(doc => { doc[key].repeat = $event })" />
        </NFormItem>
      </div>
      <div class="panel-row">
        <NButton size="tiny" :disabled="store.document[key].heightMm > 0" @click="store.expandBand(key)">
          展开为 12mm
        </NButton>
        <NButton size="tiny" :disabled="store.document[key].heightMm <= 0" @click="store.collapseBand(key)">
          收起
        </NButton>
        <NButton size="tiny" secondary @click="store.selectSurface(key)">
          编辑{{ label }}
        </NButton>
      </div>
    </template>
  </section>
</template>
