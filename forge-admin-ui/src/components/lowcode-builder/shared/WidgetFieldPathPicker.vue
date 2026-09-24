<script setup>
import { computed } from 'vue'
import PrintFieldPicker from '@/components/print/designer/PrintFieldPicker.vue'
import { buildWidgetFieldCatalog } from './widget-field-catalog'

const props = defineProps({
  value: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  formDesignerSchema: { type: Object, default: null },
  /** Limit picker to children of a collection path */
  onlyUnder: { type: String, default: '' },
  includeCollections: { type: Boolean, default: false },
  placeholder: { type: String, default: '选择字段' },
  size: { type: String, default: 'small' },
  allowClear: { type: Boolean, default: true },
})

const emit = defineEmits(['update:value'])

const catalog = computed(() => buildWidgetFieldCatalog(props.fields, {
  formDesignerSchema: props.formDesignerSchema,
}))

function clearValue() {
  emit('update:value', '')
}
</script>

<template>
  <div class="widget-field-path-picker">
    <PrintFieldPicker
      :value="value"
      :catalog="catalog"
      :only-under="onlyUnder"
      :include-collections="includeCollections"
      :placeholder="placeholder"
      :size="size"
      @update:value="emit('update:value', $event || '')"
    />
    <n-button
      v-if="allowClear && value"
      class="clear-btn"
      size="tiny"
      quaternary
      @click="clearValue"
    >
      清空
    </n-button>
  </div>
</template>

<style scoped>
.widget-field-path-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  width: 100%;
}
.clear-btn {
  flex: none;
}
</style>
