<script setup>
import { NColorPicker, NFormItem, NInputNumber, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { designerTablePreview } from '../designerSample'
import { DEFAULT_PRINT_FONT, PRINT_FONT_OPTIONS } from '../printFonts'

const props = defineProps({
  /** all | style | border */
  mode: { type: String, default: 'all' },
})

const store = usePrintDesignerStore()
const STYLE_TYPES = new Set(['TEXT', 'PAGE_NUMBER', 'BARCODE', 'QRCODE', 'IMAGE', 'HTML', 'STATIC_TABLE', 'DATA_TABLE', 'RECTANGLE', 'ELLIPSE', 'LINE'])
const isTableSurface = computed(() => store.activeElement?.type === 'DATA_TABLE'
  || (store.activeSurface?.kind === 'TABLE' && !store.activeElement))
const target = computed(() => {
  if (store.activeElement)
    return STYLE_TYPES.has(store.activeElement.type) ? store.activeElement : null
  if (store.activeSurface?.kind === 'TEXT' || store.activeSurface?.kind === 'TABLE')
    return store.activeSurface
  return null
})
const dataTableSelectionBands = computed(() => {
  if (store.activeElement?.type !== 'DATA_TABLE' || !store.selectedTableColumns.length)
    return { header: false, body: true }
  const range = store.tableSelectionRange
  if (!range)
    return { header: false, body: true }
  const preview = designerTablePreview(store.activeElement, store.catalog, {})
  if (!preview.length)
    return { header: false, body: true }
  const top = Math.max(0, range.top)
  const bottom = Math.min(preview.length - 1, range.bottom)
  const kinds = new Set()
  for (let row = top; row <= bottom; row += 1)
    kinds.add(preview[row]?.kind)
  const header = kinds.has('header')
  const body = kinds.has('data') || kinds.has('footer')
  if (!header && !body)
    return { header: false, body: true }
  return { header, body }
})
/** Values shown in the form — selected detail cells / columns, else table「样式」tab edits headerStyle. */
const styleBag = computed(() => {
  if (store.activeElement?.type === 'DATA_TABLE' && store.selectedTableColumns.length) {
    const col = store.selectedTableColumns[0]
    const hit = store.tableSelectionCells?.[0]
    if (hit) {
      const key = `${hit.kind}:${hit.kindIndex}:${hit.columnId}`
      const override = store.activeElement.cellStyles?.[key]
      if (hit.kind === 'header')
        return override || col.headerStyle || store.activeElement.headerStyle || {}
      return override || col.style || {}
    }
    const bands = dataTableSelectionBands.value
    if (bands.header && !bands.body)
      return col.headerStyle || store.activeElement.headerStyle || {}
    return col.style || {}
  }
  if (isTableSurface.value && props.mode === 'style') {
    if (store.activeElement?.type === 'DATA_TABLE')
      return store.activeElement.headerStyle || {}
    return store.activeSurface?.headerStyle || {}
  }
  return target.value?.style || {}
})
const showTypography = computed(() => {
  if (isTableSurface.value)
    return true
  return !store.activeElement || ['TEXT', 'PAGE_NUMBER', 'BARCODE', 'QRCODE', 'HTML', 'STATIC_TABLE', 'DATA_TABLE'].includes(store.activeElement.type) || store.activeSurface?.kind === 'TEXT'
})
const showStyle = computed(() => props.mode === 'all' || props.mode === 'style')
const showBorder = computed(() => props.mode === 'all' || props.mode === 'border')
const alignments = [{ label: '左对齐', value: 'left' }, { label: '居中', value: 'center' }, { label: '右对齐', value: 'right' }, { label: '两端对齐', value: 'justify' }]
const verticalAlignments = [{ label: '顶部', value: 'top' }, { label: '垂直居中', value: 'middle' }, { label: '底部', value: 'bottom' }]
const decorations = [{ label: '无', value: 'none' }, { label: '下划线', value: 'underline' }, { label: '删除线', value: 'line-through' }, { label: '上划线', value: 'overline' }]
const fontOptions = PRINT_FONT_OPTIONS.map(item => ({ label: item.label, value: item.value }))
const formats = [{ label: '文本', value: 'TEXT' }, { label: '金额（分转元）', value: 'MONEY' }, { label: '数字', value: 'NUMBER' }, { label: '日期', value: 'DATE' }, { label: '布尔值', value: 'BOOLEAN' }]
function normalizeColor(value) {
  if (typeof value !== 'string')
    return value
  const hex = value.trim()
  if (/^#[\da-f]{8}$/i.test(hex))
    return `#${hex.slice(1, 7)}`
  if (/^#[\da-f]{4}$/i.test(hex))
    return `#${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}`
  return hex
}
function patch(key, value, group = 'style') {
  if (value === null || !target.value)
    return
  let next = value
  if (['color', 'backgroundColor', 'borderColor'].includes(key))
    next = normalizeColor(value)
  // Box-selected detail columns: style only those columns, not whole table / headerStyle
  if (store.activeElement?.type === 'DATA_TABLE' && store.selectedTableColumns.length) {
    if (group === 'style') {
      store.patchSelectedDataTableColumnStyles({ [key]: next })
      return
    }
  }
  if (group === 'style' && isTableSurface.value && props.mode === 'style') {
    if (store.activeElement?.type === 'DATA_TABLE') {
      store.patchSelected({ headerStyle: { ...store.activeElement.headerStyle, [key]: next } })
      return
    }
    store.patchSurface({ headerStyle: { ...store.activeSurface.headerStyle, [key]: next } })
    return
  }
  const change = { [group]: { ...target.value[group], [key]: next } }
  if (store.activeElement)
    store.patchSelected(change)
  else store.patchSurface(change)
}
</script>

<template>
  <section v-if="target" class="designer-group">
    <h3 v-if="mode === 'all'">
      {{ showTypography ? '外观与格式' : '边框与背景' }}
    </h3>
    <h3 v-else-if="mode === 'style'">
      {{ store.selectedTableColumns.length && store.activeElement?.type === 'DATA_TABLE'
        ? `已选 ${store.selectedTableColumns.length} 列样式`
        : (isTableSurface ? '表头样式' : '样式') }}
    </h3>
    <h3 v-else>
      边框
    </h3>
    <p v-if="store.activeElement?.type === 'DATA_TABLE' && store.selectedTableColumns.length && mode === 'style'" class="muted tip">
      {{ store.tableSelectionCells?.length
        ? `当前修改只作用于框选的 ${store.tableSelectionCells.length} 个单元格`
        : `当前修改作用于选中的 ${store.selectedTableColumns.length} 列数据样式` }}
    </p>
    <p v-else-if="isTableSurface && mode === 'style'" class="muted tip">
      表头背景、文字色、字号在此统一设置；框选列后再改对齐/颜色只作用于选中列。
    </p>

    <template v-if="showStyle && showTypography">
      <NFormItem label="字体" size="small">
        <NSelect
          :value="styleBag.fontFamily || DEFAULT_PRINT_FONT"
          :options="fontOptions"
          filterable
          :consistent-menu-width="false"
          @update:value="patch('fontFamily', $event)"
        />
      </NFormItem>
      <div class="panel-grid">
        <NFormItem label="字号 pt" size="small">
          <NInputNumber :value="styleBag.fontSizePt || 10" :min="6" :max="144" :show-button="false" @update:value="patch('fontSizePt', $event)" />
        </NFormItem>
        <NFormItem v-if="!isTableSurface" label="行高倍数" size="small">
          <NInputNumber :value="styleBag.lineHeight || 1.4" :min="1" :max="4" :step="0.1" :show-button="false" @update:value="patch('lineHeight', $event)" />
        </NFormItem>
      </div>
      <div class="panel-grid">
        <NFormItem label="水平对齐" size="small">
          <NSelect :value="styleBag.textAlign || 'left'" :options="alignments" @update:value="patch('textAlign', $event)" />
        </NFormItem>
        <NFormItem v-if="!isTableSurface" label="垂直对齐" size="small">
          <NSelect :value="styleBag.verticalAlign || 'top'" :options="verticalAlignments" @update:value="patch('verticalAlign', $event)" />
        </NFormItem>
      </div>
      <NFormItem label="字重" size="small">
        <NSelect :value="styleBag.fontWeight || (isTableSurface ? 700 : 400)" :options="[{ label: '常规', value: 400 }, { label: '加粗', value: 700 }]" @update:value="patch('fontWeight', $event)" />
      </NFormItem>
      <div class="panel-grid">
        <NFormItem label="字形" size="small">
          <NSelect :value="styleBag.fontStyle || 'normal'" :options="[{ label: '常规', value: 'normal' }, { label: '斜体', value: 'italic' }]" @update:value="patch('fontStyle', $event)" />
        </NFormItem>
        <NFormItem v-if="!isTableSurface" label="装饰" size="small">
          <NSelect :value="styleBag.textDecoration || 'none'" :options="decorations" @update:value="patch('textDecoration', $event)" />
        </NFormItem>
        <NFormItem label="文字颜色" size="small">
          <NColorPicker :value="styleBag.color || '#000000'" :show-alpha="false" :modes="['hex']" @update:value="patch('color', $event)" />
        </NFormItem>
        <NFormItem label="背景颜色" size="small">
          <NColorPicker :value="styleBag.backgroundColor || '#ffffff'" :show-alpha="false" :modes="['hex']" @update:value="patch('backgroundColor', $event)" />
        </NFormItem>
      </div>
      <NFormItem v-if="target.binding && target.type !== 'HTML'" label="数据格式" size="small">
        <NSelect :value="target.format?.type || 'TEXT'" :options="formats" @update:value="patch('type', $event, 'format')" />
      </NFormItem>
      <NFormItem v-if="target.format?.type === 'NUMBER'" label="小数位" size="small">
        <NInputNumber :value="target.format?.scale ?? 2" :min="0" :max="6" @update:value="patch('scale', $event, 'format')" />
      </NFormItem>
    </template>

    <div v-if="showStyle && !showTypography" class="panel-grid">
      <NFormItem label="背景颜色" size="small">
        <NColorPicker :value="styleBag.backgroundColor || '#ffffff'" :show-alpha="false" :modes="['hex']" @update:value="patch('backgroundColor', $event)" />
      </NFormItem>
    </div>

    <div v-if="showBorder" class="panel-grid">
      <NFormItem v-if="!showStyle" label="背景颜色" size="small">
        <NColorPicker :value="target.style?.backgroundColor || '#ffffff'" :show-alpha="false" :modes="['hex']" @update:value="patch('backgroundColor', $event)" />
      </NFormItem>
      <NFormItem label="边框颜色" size="small">
        <NColorPicker :value="target.style?.borderColor || '#000000'" :show-alpha="false" :modes="['hex']" @update:value="patch('borderColor', $event)" />
      </NFormItem>
      <NFormItem label="边框 mm" size="small">
        <NInputNumber :value="target.style?.borderWidthMm ?? 0" :min="0" :max="3" :step="0.1" :show-button="false" @update:value="patch('borderWidthMm', $event)" />
      </NFormItem>
      <NFormItem label="边框样式" size="small">
        <NSelect :value="target.style?.borderStyle || 'solid'" :options="[{ label: '实线', value: 'solid' }, { label: '虚线', value: 'dashed' }, { label: '点线', value: 'dotted' }]" @update:value="patch('borderStyle', $event)" />
      </NFormItem>
      <NFormItem label="圆角 mm" size="small">
        <NInputNumber :value="target.style?.borderRadiusMm ?? 0" :min="0" :max="100" :step="0.5" :show-button="false" @update:value="patch('borderRadiusMm', $event)" />
      </NFormItem>
      <NFormItem label="内边距 mm" size="small">
        <NInputNumber :value="target.style?.paddingMm ?? 0" :min="0" :max="20" :step="0.5" :show-button="false" @update:value="patch('paddingMm', $event)" />
      </NFormItem>
    </div>
  </section>
  <p v-else class="muted empty">
    选中元素后可编辑样式
  </p>
</template>

<style scoped>
.empty,
.tip {
  margin: 8px 4px;
  font-size: 11px;
}
:deep(.n-color-picker__value) {
  display: none !important;
}
</style>
