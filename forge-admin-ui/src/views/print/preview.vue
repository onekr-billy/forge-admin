<script setup>
import { NButton, NEmpty, useThemeVars } from 'naive-ui'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { printRecordFromQuery } from '@/components/print/management/printRouteContext'
import PrintTemplatePicker from '@/components/print/runtime/PrintTemplatePicker.vue'

const route = useRoute()
const router = useRouter()
const theme = useThemeVars()
const themeStyle = computed(() => ({ background: theme.value.bodyColor, color: theme.value.textColor1 }))
const record = computed(() => printRecordFromQuery(route.query))
</script>

<template>
  <main class="print-runtime-page" :style="themeStyle">
    <header>
      <NButton @click="router.back()">
        返回
      </NButton><span>单据打印 · 当前已保存数据</span>
    </header>
    <PrintTemplatePicker v-if="record" :record="record" />
    <NEmpty v-else description="请从已保存单据的打印入口进入" />
  </main>
</template>

<style scoped>
.print-runtime-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
header {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.print-runtime-page :deep(.runtime-picker) {
  flex: 1;
}
</style>
