<template>
  <div class="ai-crud-page-wrapper">
    <ListPageGridDesigner
      v-if="shouldRenderRuntimeGrid"
      class="runtime-list-grid"
      :model-value="runtimeGridLayout"
      :fields="runtimeFields"
      :model-schema="renderConfig?.modelSchema || {}"
      :layout-type="runtimeEffectiveLayoutType"
      :page-name="resolveRuntimeTitle(renderConfig)"
      readonly
      :runtime-crud-props="crudProps"
      :runtime-record="runtimeDetailRecord"
    />
    <component
      :is="currentTemplate"
      v-else-if="configLoaded && currentTemplate"
      ref="runtimeCrudRef"
      :crud-props="crudProps"
    />
    <AiCrudPage
      v-else-if="configLoaded && !currentTemplate"
      ref="runtimeCrudRef"
      v-bind="crudProps"
    />
    <div v-else-if="loading" class="loading-wrapper">
      <n-spin size="large" description="加载配置中..." />
    </div>
    <div v-else-if="errorMsg" class="error-wrapper">
      <n-result status="error" :title="errorMsg">
        <template #footer>
          <n-button @click="loadConfig">
            重新加载
          </n-button>
        </template>
      </n-result>
    </div>
  </div>
</template>

<script>
import { crudPageLocalComponents } from './crudPageLocalComponents'
import { useCrudPageView } from './composables/useCrudPageView'

export default {
  name: 'CrudPage',
  components: {
    ...crudPageLocalComponents,
  },
  props: {
  runtimeConfig: {
    type: Object,
    default: null,
  },
  syncDocumentTitle: {
    type: Boolean,
    default: true,
  },
},
  setup(props, { emit }) {
    return useCrudPageView(props, emit)
  },
}
</script>

<style scoped src="./crudPage.css"></style>
