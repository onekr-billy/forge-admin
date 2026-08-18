<template>
  <div class="forge-field-shelf">
    <div class="shelf-head">
      <div class="shelf-head-main">
        <h3>字段与布局</h3>
        <p>拖入画布或点击添加。</p>
        <div class="shelf-stats">
          <template v-if="activeShelfTab === 'components'">
            <span>共 {{ allComponentTotal }} 个</span>
            <span v-if="keyword && componentTotal !== allComponentTotal">匹配 {{ componentTotal }} 个</span>
            <span>{{ componentGroupTotal }} 组</span>
          </template>
          <template v-else>
            <span>共 {{ props.fields.length }} 个</span>
            <span v-if="keyword && visibleFields.length !== props.fields.length">匹配 {{ visibleFields.length }} 个</span>
            <span>未用 {{ unusedFields.length }}</span>
          </template>
        </div>
      </div>
      <slot name="actions" />
    </div>

    <n-input
      v-model:value="keyword"
      size="small"
      clearable
      :placeholder="activeShelfTab === 'components' ? '搜索组件' : '搜索字段'"
      class="shelf-search"
    >
      <template #prefix>
        <n-icon><SearchOutline /></n-icon>
      </template>
    </n-input>

    <div class="shelf-mode-tabs">
      <button type="button" :class="{ active: activeShelfTab === 'components' }" @click="activeShelfTab = 'components'">
        组件库 {{ componentTotal }}
      </button>
      <button type="button" :class="{ active: activeShelfTab === 'fields' }" @click="activeShelfTab = 'fields'">
        字段资产 {{ props.fields.length }}
      </button>
    </div>

    <div v-if="activeShelfTab === 'components'" class="component-palette">
      <div v-for="group in visibleTemplateGroups" :key="group.title" class="palette-section">
        <h4>
          <span>{{ group.title }}</span>
          <em>{{ group.items.length }}</em>
        </h4>
        <div class="palette-grid">
          <button
            v-for="item in group.items"
            :key="item.componentKey"
            type="button"
            class="palette-item"
            :class="{ dragging: draggingKey === `template:${item.componentKey}` }"
            draggable="true"
            @dragstart="handleTemplateDragStart($event, item)"
            @dragend="finishDragging"
          >
            <n-icon><component :is="item.icon" /></n-icon>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </div>
      <div v-if="visibleLayoutItems.length" class="palette-section">
        <h4>
          <span>布局与业务区块</span>
          <em>{{ visibleLayoutItems.length }}</em>
        </h4>
        <div class="palette-grid">
          <button
            v-for="item in visibleLayoutItems"
            :key="item.componentKey"
            type="button"
            class="palette-item"
            :class="{ dragging: draggingKey === `layout:${item.componentKey}` }"
            draggable="true"
            @dragstart="handleLayoutDragStart($event, item)"
            @dragend="finishDragging"
          >
            <n-icon><component :is="item.icon" /></n-icon>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </div>
      <n-empty v-if="!componentTotal" size="small" description="没有匹配组件" />
    </div>

    <div v-else class="field-asset-panel">
      <div class="field-tabs">
        <button type="button" :class="{ active: activeTab === 'unused' }" @click="activeTab = 'unused'">
          未使用 {{ unusedFields.length }}
        </button>
        <button type="button" :class="{ active: activeTab === 'used' }" @click="activeTab = 'used'">
          已使用 {{ usedFields.length }}
        </button>
        <button type="button" :class="{ active: activeTab === 'system' }" @click="activeTab = 'system'">
          系统 {{ systemFields.length }}
        </button>
      </div>

      <div class="field-list">
        <button
          v-for="field in visibleFields"
          :key="field.field || field.fieldCode"
          type="button"
          class="field-item"
          :class="{ used: isUsed(field), locked: isLocked(field), dragging: draggingKey === `field:${field.field || field.fieldCode}` }"
          :disabled="isUsed(field) || isLocked(field)"
          :draggable="!isUsed(field) && !isLocked(field)"
          @click="$emit('appendField', field)"
          @dragstart="handleFieldDragStart($event, field)"
          @dragend="finishDragging"
        >
          <span class="drag-handle" aria-hidden="true">
            <svg
              width="1em"
              height="1em"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              data-icon="DragOutlined"
            >
              <path
                d="M8.25 6.5a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5Zm0 7.25a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5Zm1.75 5.5a1.75 1.75 0 1 1-3.5 0 1.75 1.75 0 0 1 3.5 0ZM14.753 6.5a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5ZM16.5 12a1.75 1.75 0 1 1-3.5 0 1.75 1.75 0 0 1 3.5 0Zm-1.747 9a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5Z"
                fill="currentColor"
              />
            </svg>
          </span>
          <span class="field-main">
            <strong>{{ field.label || field.fieldName || field.field }}</strong>
            <small>{{ field.field || field.fieldCode }} · {{ field.componentType || field.dataType || 'input' }}</small>
          </span>
          <em v-if="isUsed(field)">已用</em>
          <em v-else-if="isLocked(field)">系统</em>
        </button>
        <n-empty v-if="!visibleFields.length" size="small" description="没有匹配字段" />
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  AlbumsOutline,
  AlertCircleOutline,
  AnalyticsOutline,
  BrowsersOutline,
  BusinessOutline,
  CalculatorOutline,
  CalendarOutline,
  CashOutline,
  CheckboxOutline,
  ChevronDownCircleOutline,
  CloudOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  ColorPaletteOutline,
  DocumentTextOutline,
  GridOutline,
  HomeOutline,
  ImageOutline,
  KeypadOutline,
  ListOutline,
  LocationOutline,
  MenuOutline,
  NavigateOutline,
  OptionsOutline,
  PersonOutline,
  PricetagOutline,
  QrCodeOutline,
  ReaderOutline,
  ReorderThreeOutline,
  ResizeOutline,
  SearchOutline,
  StarOutline,
  StatsChartOutline,
  SwapHorizontalOutline,
  TerminalOutline,
  TextOutline,
  TimeOutline,
  TimerOutline,
  ToggleOutline,
} from '@vicons/ionicons5'
import { computed, ref } from 'vue'
import { isReadonlySystemField } from '@/components/lowcode-builder/page/page-schema'
import { pageWidgetCatalog } from '@/components/lowcode-builder/shared/page-widget-schema'
import { clearDesignerDragPreview, clearDesignerDragSource, clearDesignerDropKey, setDesignerDragPreview } from './designerDragState'

const props = defineProps({
  fields: {
    type: Array,
    default: () => [],
  },
  usedFieldSet: {
    type: Object,
    default: () => new Set(),
  },
  relations: {
    type: Array,
    default: () => [],
  },
})

defineEmits(['appendField'])

const keyword = ref('')
const activeShelfTab = ref('components')
const activeTab = ref('unused')
const draggingKey = ref('')
const fieldTemplateGroups = [
  {
    title: '输入',
    items: [
      { componentKey: 'input', label: '输入框', icon: TextOutline },
      { componentKey: 'barcodeScanner', label: '扫码输入', icon: StatsChartOutline },
      { componentKey: 'textarea', label: '多行文本', icon: DocumentTextOutline },
      { componentKey: 'number', label: '数字', icon: CalculatorOutline },
      { componentKey: 'money', label: '金额', icon: CashOutline },
      { componentKey: 'slider', label: '滑块', icon: OptionsOutline },
      { componentKey: 'rate', label: '评分', icon: StarOutline },
      { componentKey: 'color', label: '颜色选择', icon: ColorPaletteOutline },
    ],
  },
  {
    title: '选择',
    items: [
      { componentKey: 'select', label: '静态下拉', icon: ListOutline },
      { componentKey: 'dictSelect', label: '字典下拉', icon: PricetagOutline },
      { componentKey: 'radio', label: '单选', icon: CheckboxOutline },
      { componentKey: 'radioButton', label: '按钮单选', icon: CheckboxOutline },
      { componentKey: 'checkbox', label: '多选', icon: CheckboxOutline },
      { componentKey: 'transfer', label: '穿梭框', icon: SwapHorizontalOutline },
      { componentKey: 'cascader', label: '级联选择', icon: ChevronDownCircleOutline },
      { componentKey: 'treeSelect', label: '树形选择', icon: GridOutline },
      { componentKey: 'customSelect', label: '远程选择', icon: CloudOutline },
      { componentKey: 'date', label: '日期', icon: CalendarOutline },
      { componentKey: 'datetime', label: '日期时间', icon: TimeOutline },
      { componentKey: 'daterange', label: '日期范围', icon: CalendarOutline },
      { componentKey: 'datetimerange', label: '日期时间范围', icon: TimeOutline },
      { componentKey: 'month', label: '月份', icon: CalendarOutline },
      { componentKey: 'year', label: '年份', icon: CalendarOutline },
      { componentKey: 'timerange', label: '时间范围', icon: TimerOutline },
      { componentKey: 'switch', label: '开关', icon: ToggleOutline },
    ],
  },
  {
    title: '业务',
    items: [
      { componentKey: 'userSelect', label: '人员选择', icon: PersonOutline },
      { componentKey: 'orgTreeSelect', label: '部门选择', icon: BusinessOutline },
      { componentKey: 'regionTreeSelect', label: '行政区划', icon: LocationOutline },
      { componentKey: 'objectReference', label: '对象引用', icon: BrowsersOutline },
      { componentKey: 'recordSelector', label: '记录选择器', icon: SearchOutline },
      { componentKey: 'fileUpload', label: '文件上传', icon: CloudUploadOutline },
      { componentKey: 'imageUpload', label: '图片上传', icon: ImageOutline },
      { componentKey: 'text', label: '文本展示', icon: TextOutline },
    ],
  },
]
const widgetLayoutItems = pageWidgetCatalog
  .filter(item => item.componentKey !== 'transfer')
  .map(item => ({
    componentKey: item.componentKey,
    label: item.label || item.title,
    icon: resolveWidgetIcon(item.componentKey),
  }))
const layoutItems = [
  { componentKey: 'row', label: '栅格布局', icon: GridOutline },
  { componentKey: 'table', label: '表格布局', icon: KeypadOutline },
  { componentKey: 'AiCrudPage', label: 'CRUD区块', icon: ListOutline },
  { componentKey: 'subTable', label: '关联子表', icon: ListOutline },
  { componentKey: 'button', label: '按钮', icon: ToggleOutline },
  { componentKey: 'title', label: '分组标题', icon: ReorderThreeOutline },
  { componentKey: 'AiFormSectionTitle', label: '表单分隔线', icon: ReorderThreeOutline },
  { componentKey: 'card', label: '卡片分组', icon: AlbumsOutline },
  { componentKey: 'tabs', label: '标签页', icon: BrowsersOutline },
  { componentKey: 'collapse', label: '折叠面板', icon: ChevronDownCircleOutline },
  ...widgetLayoutItems,
]

const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const visibleTemplateGroups = computed(() => {
  const text = normalizedKeyword.value
  if (!text)
    return fieldTemplateGroups
  return fieldTemplateGroups
    .map(group => ({
      ...group,
      items: group.items.filter(item => [item.label, item.componentKey].some(value => String(value || '').toLowerCase().includes(text))),
    }))
    .filter(group => group.items.length)
})
const visibleLayoutItems = computed(() => {
  const text = normalizedKeyword.value
  // 关联子表依赖对象关系上下文，无关系时不提供入口，避免出现无效配置。
  const baseItems = props.relations.length
    ? layoutItems
    : layoutItems.filter(item => item.componentKey !== 'subTable')
  if (!text)
    return baseItems
  return baseItems.filter(item => [item.label, item.componentKey].some(value => String(value || '').toLowerCase().includes(text)))
})
const componentTotal = computed(() => {
  return visibleTemplateGroups.value.reduce((total, group) => total + group.items.length, 0) + visibleLayoutItems.value.length
})
const allComponentTotal = computed(() => {
  return fieldTemplateGroups.reduce((total, group) => total + group.items.length, 0) + layoutItems.length
})
const componentGroupTotal = computed(() => {
  return visibleTemplateGroups.value.length + (visibleLayoutItems.value.length ? 1 : 0)
})
const filteredFields = computed(() => {
  const text = normalizedKeyword.value
  if (!text)
    return props.fields
  return props.fields.filter((field) => {
    return [field.label, field.fieldName, field.field, field.fieldCode, field.componentType]
      .some(value => String(value || '').toLowerCase().includes(text))
  })
})
const businessFields = computed(() => filteredFields.value.filter(field => !isReadonlySystemField(field)))
const systemFields = computed(() => filteredFields.value.filter(field => isReadonlySystemField(field)))
const usedFields = computed(() => businessFields.value.filter(field => isUsed(field)))
const unusedFields = computed(() => businessFields.value.filter(field => !isUsed(field)))
const visibleFields = computed(() => {
  if (activeTab.value === 'used')
    return usedFields.value
  if (activeTab.value === 'system')
    return systemFields.value
  return unusedFields.value
})

function isUsed(field = {}) {
  return props.usedFieldSet.has(field.field || field.fieldCode)
}

function isLocked(field = {}) {
  return isReadonlySystemField(field)
}

function resolveWidgetIcon(componentKey = '') {
  const iconMap = {
    'rich-text': DocumentTextOutline,
    'watermark': TextOutline,
    'vue-component': BrowsersOutline,
    'html-tag': CodeSlashOutline,
    'markdown': ReorderThreeOutline,
    'barcode': StatsChartOutline,
    'qrcode': QrCodeOutline,
    'calendar': CalendarOutline,
    'code': CodeSlashOutline,
    'countdown': TimerOutline,
    'descriptions': ReaderOutline,
    'announcement': AlertCircleOutline,
    'list': ListOutline,
    'log': TerminalOutline,
    'number-animation': AnalyticsOutline,
    'breadcrumb': NavigateOutline,
    'menu': MenuOutline,
    'pagination': ReorderThreeOutline,
    'split': ResizeOutline,
  }
  return iconMap[componentKey] || HomeOutline
}

function handleFieldDragStart(event, field) {
  if (isUsed(field) || isLocked(field)) {
    event.preventDefault()
    return
  }
  draggingKey.value = `field:${field.field || field.fieldCode}`
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-forge-form-field', JSON.stringify(field))
  setDesignerDragPreview({
    componentKey: field.componentType || field.componentKey || field.type || 'input',
    label: field.label || field.fieldName || field.field || '字段',
  })
}

function handleLayoutDragStart(event, item) {
  draggingKey.value = `layout:${item.componentKey}`
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-forge-form-layout', JSON.stringify(item))
  setDesignerDragPreview({
    componentKey: item.componentKey,
    label: item.label,
  })
}

function handleTemplateDragStart(event, item) {
  draggingKey.value = `template:${item.componentKey}`
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-forge-form-template', JSON.stringify(item))
  setDesignerDragPreview({
    componentKey: item.componentKey,
    label: item.label,
  })
}

function finishDragging() {
  draggingKey.value = ''
  clearDesignerDragSource()
  clearDesignerDragPreview()
  clearDesignerDropKey()
}
</script>

<style scoped>
.forge-field-shelf {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  padding: 12px;
  background: #fcfcfc;
}

.shelf-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 0;
  border-bottom: 0;
  background: #fcfcfc;
}

.shelf-head-main {
  min-width: 0;
}

.shelf-head h3,
.shelf-head p {
  margin: 0;
}

.shelf-head h3 {
  color: #18181b;
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
}

.shelf-head p {
  margin-top: 2px;
  color: #71717a;
  font-size: 11px;
  line-height: 16px;
}

.shelf-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.shelf-stats span,
.palette-section h4 em {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
  line-height: 16px;
}

.shelf-search {
  margin-top: 12px;
  width: 100%;
}

.shelf-mode-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 3px;
  margin: 8px 0 10px;
  padding: 3px;
  border: 1px solid #e4e4e7;
  border-radius: 9px;
  background: #f4f4f5;
}

.shelf-mode-tabs button {
  cursor: pointer;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #71717a;
  font-size: 12px;
  font-weight: 600;
  transition:
    border-color 180ms ease,
    background 180ms ease,
    color 180ms ease,
    box-shadow 180ms ease;
}

.shelf-mode-tabs button:hover {
  background: rgba(228, 228, 231, 0.72);
  color: #27272a;
}

.shelf-mode-tabs button.active {
  background: #fff;
  color: #27272a;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.component-palette {
  min-height: 0;
  overflow: auto;
  padding: 2px 0 0;
  background: #fcfcfc;
}

.palette-section + .palette-section {
  margin-top: 12px;
}

.palette-section h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 8px;
  color: #52525b;
  font-size: 12px;
  font-weight: 600;
}

.palette-section h4 span {
  min-width: 0;
}

.palette-section h4::before {
  content: '';
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  margin-left: 3px;
  border-radius: 999px;
  background: #4266f7;
  outline: 3px solid #e8edff;
}

.palette-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.palette-item,
.field-item {
  cursor: grab;
  border: 1px solid #e4e4e7;
  background: #fff;
  transition:
    border-color 180ms ease,
    background 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.palette-item:hover,
.field-item:hover:not(:disabled) {
  border-color: #c7d2fe;
  background: #f8faff;
  box-shadow: 0 8px 18px rgba(49, 83, 216, 0.08);
}

.palette-item.dragging,
.field-item.dragging {
  opacity: 0.48;
  border-color: #60a5fa;
  background: #dbeafe;
}

.palette-item:active,
.field-item:active {
  cursor: grabbing;
}

.palette-item {
  min-height: 48px;
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 7px;
  padding: 7px 8px;
  color: #3f3f46;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  overflow: hidden;
}

.palette-item :deep(.n-icon) {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #f4f4f5;
  color: #64748b;
  font-size: 16px;
  flex: 0 0 auto;
  transition:
    background 0.15s,
    color 0.15s;
}

.palette-item:hover :deep(.n-icon) {
  background: #eff6ff;
  color: #2563eb;
}

.field-asset-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-height: 0;
  border-top: 0;
  background: transparent;
}

.field-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
  margin-bottom: 8px;
  padding: 3px;
  border: 1px solid #e4e4e7;
  border-radius: 9px;
  background: #f4f4f5;
}

.field-tabs button {
  cursor: pointer;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #71717a;
  font-size: 12px;
  font-weight: 600;
  line-height: 28px;
}

.field-tabs button:hover {
  background: rgba(228, 228, 231, 0.72);
  color: #27272a;
}

.field-tabs button.active {
  background: #fff;
  color: #27272a;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.field-list {
  display: grid;
  align-content: start;
  gap: 5px;
  min-height: 0;
  overflow: auto;
  background: transparent;
  padding: 0;
}

.field-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  border-radius: 7px;
  padding: 6px 8px;
  text-align: left;
}

.field-item:disabled {
  cursor: not-allowed;
  background: #f8fafc;
  color: #94a3b8;
}

.drag-handle {
  display: grid;
  place-items: center;
  color: #94a3b8;
}

.field-item:hover:not(:disabled) .drag-handle {
  color: #2563eb;
}

.field-main {
  min-width: 0;
}

.field-main strong,
.field-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-main strong {
  color: #0f172a;
  font-size: 12px;
}

.field-main small {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
}

.field-item em {
  border-radius: 999px;
  background: #e2e8f0;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
  line-height: 20px;
  padding: 0 6px;
}

@media (prefers-reduced-motion: reduce) {
  .palette-item,
  .field-item {
    transition: none;
  }
}
</style>
