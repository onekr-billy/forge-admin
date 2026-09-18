<script setup>
import { printRenderers } from '../renderers/registry'

defineProps({ page: { type: Object, required: true }, geometry: { type: Object, required: true } })

function position(node) {
  return { position: 'absolute', left: `${node.xMm || 0}mm`, top: `${node.yMm || 0}mm`, width: node.widthMm ? `${node.widthMm}mm` : undefined, height: node.heightMm ? `${node.heightMm}mm` : undefined }
}
</script>

<template>
  <article :data-print-page="page.number" :style="{ position: 'relative', flex: 'none', width: `${geometry.widthMm}mm`, height: `${geometry.heightMm}mm`, boxSizing: 'border-box', background: '#ffffff', color: '#000000', margin: 0, padding: 0, printColorAdjust: 'exact', webkitPrintColorAdjust: 'exact' }">
    <div v-for="(band, index) in [page.header, page.footer]" :key="index" :style="position(band)">
      <div v-for="element in band.elements" :key="element.id" :data-print-element="element.id" :style="position(element)">
        <component :is="printRenderers[element.type]" :node="element" />
      </div>
    </div>
    <div v-for="(fragment, index) in page.fragments" :key="`${fragment.id}-${index}`" :data-print-fragment="fragment.id" :style="position(fragment)">
      <template v-if="fragment.kind === 'FIXED'">
        <div v-for="element in fragment.elements" :key="element.id" :data-print-element="element.id" :style="position(element)">
          <component :is="printRenderers[element.type]" :node="element" />
        </div>
      </template>
      <component :is="printRenderers[fragment.type]" v-else :node="fragment" />
    </div>
  </article>
</template>
