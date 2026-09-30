<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PrintCenterWorkspace from '@/components/print/center/PrintCenterWorkspace.vue'
import { printSourceFromQuery } from '@/components/print/management/printRouteContext'
import PrintTemplateList from '@/components/print/management/PrintTemplateList.vue'

const route = useRoute()
const source = computed(() => printSourceFromQuery(route.query))
const applicationId = computed(() => /^[1-9]\d*$/.test(String(route.query.applicationId || '')) ? String(route.query.applicationId) : null)
const legacyApplicationScope = computed(() => Boolean(applicationId.value))
const standaloneSection = computed(() => {
  if (route.path === '/print/sources')
    return 'sources'
  if (route.path === '/print/bindings')
    return 'bindings'
  return 'templates'
})
</script>

<template>
  <!-- 保留低代码应用从设置页进入的原有模板管理入口。 -->
  <PrintTemplateList
    v-if="legacyApplicationScope"
    :application-id="applicationId"
    :source="source"
  />
  <PrintCenterWorkspace v-else :section="standaloneSection" />
</template>
