<script setup>
import { NColorPicker, NFormItem, NInput, NInputNumber, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
const target = computed(() => store.activeElement || (store.activeSurface?.kind === 'TEXT' ? store.activeSurface : null))
const alignments = [{ label: '左对齐', value: 'left' }, { label: '居中', value: 'center' }, { label: '右对齐', value: 'right' }]
const formats = [{ label: '文本', value: 'TEXT' }, { label: '金额（分转元）', value: 'MONEY' }, { label: '数字', value: 'NUMBER' }, { label: '日期', value: 'DATE' }, { label: '布尔值', value: 'BOOLEAN' }]
function patch(key, value, group = 'style') {
  if (value === null)
    return
  const change = { [group]: { ...target.value[group], [key]: value } }
  if (store.activeElement)
    store.patchSelected(change)
  else store.patchSurface(change)
}
</script>

<template>
  <section v-if="target" class="designer-group">
    <h3>外观与格式</h3>
    <NFormItem label="字体" size="small">
      <NInput :value="target.style?.fontFamily || 'Arial'" @change="patch('fontFamily', $event)" />
    </NFormItem>
    <div class="panel-grid">
      <NFormItem label="字号 pt" size="small">
        <NInputNumber :value="target.style?.fontSizePt || 10" :min="6" :max="144" :show-button="false" @update:value="patch('fontSizePt', $event)" />
      </NFormItem>
      <NFormItem label="行高倍数" size="small">
        <NInputNumber :value="target.style?.lineHeight || 1.4" :min="1" :max="4" :step="0.1" :show-button="false" @update:value="patch('lineHeight', $event)" />
      </NFormItem>
    </div>
    <NFormItem label="对齐" size="small">
      <NSelect :value="target.style?.textAlign || 'left'" :options="alignments" @update:value="patch('textAlign', $event)" />
    </NFormItem>
    <NFormItem label="字重" size="small">
      <NSelect :value="target.style?.fontWeight || 400" :options="[{ label: '常规', value: 400 }, { label: '加粗', value: 700 }]" @update:value="patch('fontWeight', $event)" />
    </NFormItem>
    <div class="panel-grid">
      <NFormItem label="字形" size="small">
        <NSelect :value="target.style?.fontStyle || 'normal'" :options="[{ label: '常规', value: 'normal' }, { label: '斜体', value: 'italic' }]" @update:value="patch('fontStyle', $event)" />
      </NFormItem>
      <NFormItem label="装饰" size="small">
        <NSelect :value="target.style?.textDecoration || 'none'" :options="[{ label: '无', value: 'none' }, { label: '下划线', value: 'underline' }]" @update:value="patch('textDecoration', $event)" />
      </NFormItem>
      <NFormItem label="文字颜色" size="small">
        <NColorPicker :value="target.style?.color || '#000000'" :show-alpha="false" :modes="['hex']" @update:value="patch('color', $event)" />
      </NFormItem>
      <NFormItem label="背景颜色" size="small">
        <NColorPicker :value="target.style?.backgroundColor || '#ffffff'" :show-alpha="false" :modes="['hex']" @update:value="patch('backgroundColor', $event)" />
      </NFormItem>
      <NFormItem label="边框颜色" size="small">
        <NColorPicker :value="target.style?.borderColor || '#000000'" :show-alpha="false" :modes="['hex']" @update:value="patch('borderColor', $event)" />
      </NFormItem>
      <NFormItem label="边框 mm" size="small">
        <NInputNumber :value="target.style?.borderWidthMm ?? 0" :min="0" :max="3" :step="0.1" :show-button="false" @update:value="patch('borderWidthMm', $event)" />
      </NFormItem>
      <NFormItem label="内边距 mm" size="small">
        <NInputNumber :value="target.style?.paddingMm ?? 0" :min="0" :max="20" :step="0.5" :show-button="false" @update:value="patch('paddingMm', $event)" />
      </NFormItem>
    </div>
    <NFormItem v-if="target.binding" label="数据格式" size="small">
      <NSelect :value="target.format?.type || 'TEXT'" :options="formats" @update:value="patch('type', $event, 'format')" />
    </NFormItem>
    <NFormItem v-if="target.format?.type === 'NUMBER'" label="小数位" size="small">
      <NInputNumber :value="target.format?.scale ?? 2" :min="0" :max="6" @update:value="patch('scale', $event, 'format')" />
    </NFormItem>
  </section>
</template>
