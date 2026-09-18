<script setup>
import { ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import PrintDesigner from '@/components/print/designer/PrintDesigner.vue'

// M2 local editor; application source identity and server persistence arrive in T28/T31.
const designer = ref(null)
onBeforeRouteLeave(() => designer.value?.canLeave() ?? true)
</script>

<template>
  <div class="print-designer-page">
    <PrintDesigner ref="designer" />
  </div>
</template>

<style scoped>
.print-designer-page {
  height: 100%;
  min-height: 0;
}
</style>
