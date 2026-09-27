<template>
  <div class="business-workbench">
    <WorkbenchHeader />
    <WorkbenchBreadcrumb />
    <main class="business-layout-content">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useBusinessWorkbenchStore } from '@/stores/layout/businessWorkbenchStore'
import WorkbenchBreadcrumb from './components/WorkbenchBreadcrumb.vue'
import WorkbenchHeader from './components/WorkbenchHeader.vue'
import './workbench.css'

const route = useRoute()
const store = useBusinessWorkbenchStore()
watch(() => route.fullPath, () => store.closeMenus())
onUnmounted(() => store.closeMenus())
</script>
