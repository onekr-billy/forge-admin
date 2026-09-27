<template>
  <div class="subtable-inline-editor">
    <!-- 标题 -->
    <div class="inline-section">
      <label class="inline-label">标题</label>
      <n-input
        :value="localProps.header || ''"
        size="small"
        placeholder="例如：订单明细"
        @update:value="patchProps({ header: $event || '关联子表' })"
      />
    </div>

    <!-- 关联表单 -->
    <div class="inline-section">
      <label class="inline-label">关联表单</label>
      <n-select
        :value="localProps.modelCode || ''"
        :options="objectOptions"
        :loading="loadingObjects"
        :render-label="renderObjectOptionLabel"
        size="small"
        filterable
        placeholder="选择要关联的表单"
        @update:value="handleObjectChange"
      />
      <div v-if="relationStatusText" class="inline-hint" :class="{ 'inline-hint-ok': relationMatched }">
        {{ relationStatusText }}
      </div>
    </div>

    <!-- 多个关系时才需要选择关联方式 -->
    <div v-if="matchedRelations.length > 1" class="inline-section">
      <label class="inline-label">关联方式</label>
      <n-select
        :value="localProps.relationKey || ''"
        :options="relationOptions"
        size="small"
        @update:value="handleRelationChange"
      />
    </div>

    <!-- 数据展示 -->
    <div class="inline-section">
      <label class="inline-label">数据展示</label>
      <n-radio-group
        :value="localProps.displayMode || 'inline_grid'"
        size="small"
        @update:value="patchProps({ displayMode: $event })"
      >
        <n-radio-button value="inline_grid">
          表格
        </n-radio-button>
        <n-radio-button value="card_list">
          卡片
        </n-radio-button>
      </n-radio-group>
    </div>

    <!-- 开关项 -->
    <div class="inline-switch-row">
      <div class="behavior-copy">
        <span class="behavior-title">允许新增</span>
        <span class="behavior-desc">可以直接在子表里新增一行并编辑</span>
      </div>
      <n-switch
        size="small"
        :value="localProps.allowCreate !== false"
        @update:value="patchProps({ allowCreate: $event })"
      />
    </div>
    <div class="inline-switch-row">
      <div class="behavior-copy">
        <span class="behavior-title">选择已有记录</span>
        <span class="behavior-desc">从子表已有数据中挑选记录填充</span>
      </div>
      <n-switch
        size="small"
        :value="localProps.allowSelectExisting === true"
        @update:value="patchProps({ allowSelectExisting: $event })"
      />
    </div>

    <!-- 选择器设置入口：开关打开且已选关联表单才展示 -->
    <div
      v-if="localProps.allowSelectExisting === true && localProps.modelCode"
      class="inline-entry-row"
    >
      <div class="entry-copy">
        <span class="entry-title">选择器设置</span>
        <span class="entry-desc">{{ selectorSummary }}</span>
      </div>
      <n-button size="small" @click="selectorDialogShow = true">
        配置
      </n-button>
    </div>

    <!-- 显示字段入口 -->
    <div v-if="localProps.modelCode" class="inline-entry-row">
      <div class="entry-copy">
        <span class="entry-title">显示字段</span>
        <span class="entry-desc">{{ fieldSummary }}</span>
      </div>
      <n-button size="small" @click="fieldsDialogShow = true">
        选择字段
      </n-button>
    </div>

    <!-- 选择器设置弹窗：选择方式 / 展示列 / 筛选字段 -->
    <SubTableSelectorConfigDialog
      v-model:show="selectorDialogShow"
      :field-options="childFieldOptions"
      :main-field-options="mainFieldOptions"
      :multiple="localProps.selectorMultiple !== false"
      :display-fields="selectedDisplayFields"
      :filter-fields="filterRows"
      :loading="loadingFields"
      @confirm="handleSelectorConfirm"
    />

    <!-- 显示字段弹窗 -->
    <SubTableFieldsPickerDialog
      v-model:show="fieldsDialogShow"
      title="显示字段"
      :options="childFieldOptions"
      :model-value="selectedFieldCodes"
      :loading="loadingFields"
      @confirm="handleFieldsConfirm"
    />
  </div>
</template>

<script setup>
import { computed, h, inject, onMounted, ref, watch } from 'vue'
import { businessObjectFields, businessObjectList } from '@/api/business-app'
import IconRenderer from '@/components/IconRenderer.vue'
import { useFormDesignerStore } from '@/store'
import { resolveChildTableRelationKey } from '../../child-table-section-config'
import SubTableFieldsPickerDialog from './SubTableFieldsPickerDialog.vue'
import SubTableSelectorConfigDialog from './SubTableSelectorConfigDialog.vue'
import {
  buildSubTableDisplayFieldOptions,
  collectRequiredFieldCodes,
  ensureRequiredDisplayFieldCodes,
  resolveFieldCode,
  resolveFieldLabel,
  toSubTableColumnDefs,
} from './sub-table-display-fields'

const props = defineProps({
  component: { type: Object, required: true },
})

const designerStore = useFormDesignerStore()
const pageSchemaBridge = inject('subTableConfigBridge', null)

const loadingObjects = ref(false)
const loadingFields = ref(false)
const objectCache = ref([])
const availableFields = ref([])
const selectorDialogShow = ref(false)
const fieldsDialogShow = ref(false)
let fieldRequestId = 0

const localProps = computed(() => {
  const selected = designerStore.selectedComponent
  if (selected?.id === props.component.id)
    return selected.props || {}
  return props.component.props || {}
})

const objectOptions = computed(() => {
  const currentCode = designerStore.objectCode || ''
  return objectCache.value
    .filter(o => o.objectCode !== currentCode)
    .map(o => ({ label: o.objectName || o.objectCode, value: o.objectCode, icon: o.icon || '' }))
})

/** 对象下拉选项：图标 + 名称（下拉菜单经 Teleport 渲染，用内联样式） */
function renderObjectOptionLabel(option) {
  return h('div', { style: { display: 'flex', alignItems: 'center', gap: '6px', minWidth: 0 } }, [
    h(IconRenderer, { icon: option.icon, size: 14 }),
    h('span', {
      style: {
        flex: 1,
        minWidth: 0,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      },
    }, option.label),
  ])
}

const relations = computed(() => designerStore.relations || [])

const matchedRelations = computed(() => {
  const code = localProps.value.modelCode
  if (!code)
    return []
  return relations.value.filter(r =>
    r.targetObjectCode === code || r.sourceObjectCode === code,
  )
})

const relationMatched = computed(() => matchedRelations.value.length > 0)

/** 关联状态提示：业务话术，不暴露外键概念 */
const relationStatusText = computed(() => {
  if (!localProps.value.modelCode)
    return ''
  if (relationMatched.value)
    return '已自动关联，子表数据将跟随当前记录保存'
  return '系统将按编号自动关联两个表单的数据'
})

const relationOptions = computed(() =>
  matchedRelations.value.map(rel => ({
    label: rel.relationName || rel.targetObjectCode,
    value: resolveRelKey(rel),
  })),
)

const selectedFieldCodes = computed(() => {
  const cols = localProps.value.columns
  return Array.isArray(cols) ? cols.map(c => c.fieldCode || c) : []
})

const selectedDisplayFields = computed(() =>
  Array.isArray(localProps.value.selectorDisplayFields) ? localProps.value.selectorDisplayFields : [],
)

/** 选择器弹窗顶部的筛选字段（含默认值来源），传给设置弹窗回显 */
const filterRows = computed(() => {
  const rows = localProps.value.selectorFilterFields
  return Array.isArray(rows) ? rows : []
})

/** 子表对象字段选项（弹窗内字段下拉/网格共用） */
const childFieldOptions = computed(() =>
  buildSubTableDisplayFieldOptions(availableFields.value),
)

/** 主表字段选项（默认值联动来源） */
const mainFieldOptions = computed(() =>
  (designerStore.fields || [])
    .map(f => ({ label: fl(f), value: fc(f) }))
    .filter(o => o.value),
)

/** 选择器设置入口摘要 */
const selectorSummary = computed(() => {
  const p = localProps.value
  const mode = p.selectorMultiple === false ? '单选' : '多选'
  const displayCount = selectedDisplayFields.value.length
  const filterCount = filterRows.value.filter(row => row.fieldCode).length
  return `${mode} · 展示 ${displayCount} 列 · 筛选 ${filterCount} 条`
})

/** 显示字段入口摘要 */
const fieldSummary = computed(() => {
  const total = availableFields.value.length
  const requiredCount = collectRequiredFieldCodes(childFieldOptions.value).length
  const selected = selectedFieldCodes.value.length
  if (!total)
    return `已选 ${selected} 项`
  if (requiredCount)
    return `已选 ${selected} / ${total} 项 · 必填 ${requiredCount} 项须显示`
  return `已选 ${selected} / ${total} 项`
})

onMounted(() => loadObjects())

watch(() => localProps.value.modelCode, (code) => {
  if (code)
    loadFields(code)
  else
    availableFields.value = []
}, { immediate: true })

async function loadObjects() {
  loadingObjects.value = true
  try {
    const res = await businessObjectList({})
    objectCache.value = unwrap(res?.data).filter(o => o?.objectCode)
  }
  catch { objectCache.value = [] }
  finally { loadingObjects.value = false }
}

async function loadFields(objectCode, { autoSelectRequired = false } = {}) {
  const reqId = ++fieldRequestId
  loadingFields.value = true
  try {
    let obj = objectCache.value.find(o => o.objectCode === objectCode)
    if (!obj?.id) {
      const listRes = await businessObjectList({ objectCode })
      obj = unwrap(listRes?.data).find(o => o.objectCode === objectCode) || obj
    }
    if (obj?.id) {
      const fieldRes = await businessObjectFields(obj.id)
      const fields = unwrap(fieldRes?.data)
        .filter(f => f && !f.systemField && fc(f))
        .filter(f => !['DISABLED', 'HIDDEN'].includes(String(f.fieldStatus || '').toUpperCase()))
      if (reqId !== fieldRequestId)
        return
      availableFields.value = fields
      // 必填字段必须出现在显示列；缺省或新选对象时自动补齐
      const options = buildSubTableDisplayFieldOptions(fields)
      const selected = selectedFieldCodes.value
      const requiredCodes = collectRequiredFieldCodes(options)
      const missingRequired = requiredCodes.filter(code => !selected.includes(code))
      if (missingRequired.length || (autoSelectRequired && !selected.length && requiredCodes.length)) {
        const nextCodes = ensureRequiredDisplayFieldCodes(selected, options)
        patchProps({ columns: toSubTableColumnDefs(nextCodes, fields) })
      }
    }
  }
  catch {
    if (reqId === fieldRequestId)
      availableFields.value = []
  }
  finally {
    if (reqId === fieldRequestId)
      loadingFields.value = false
  }
}

function handleObjectChange(objectCode) {
  const obj = objectCache.value.find(o => o.objectCode === objectCode)
  const matched = relations.value.filter(r => r.targetObjectCode === objectCode || r.sourceObjectCode === objectCode)
  // 自动取第一个匹配关系；无关系时用对象编码，后端按编号自动关联
  const relKey = matched.length ? resolveRelKey(matched[0]) : objectCode
  const header = localProps.value.header && localProps.value.header !== '关联子表'
    ? localProps.value.header
    : (obj?.objectName || objectCode)

  patchProps({
    modelCode: objectCode,
    relationKey: relKey,
    header,
    columns: [],
    selectorDisplayFields: [],
    selectorFilterFields: [],
  })

  loadFields(objectCode, { autoSelectRequired: true })
}

function handleRelationChange(relationKey) {
  patchProps({ relationKey })
}

/** 显示字段弹窗确定回调 */
function handleFieldsConfirm(codes) {
  const options = childFieldOptions.value
  const nextCodes = ensureRequiredDisplayFieldCodes(codes, options)
  patchProps({ columns: toSubTableColumnDefs(nextCodes, availableFields.value) })
}

/** 选择器设置弹窗确定回调 */
function handleSelectorConfirm(config) {
  patchProps({
    selectorMultiple: config.multiple,
    selectorDisplayFields: config.displayFields,
    selectorFilterFields: config.filterFields,
  })
}

function patchProps(patch) {
  const nextProps = {
    ...localProps.value,
    ...patch,
  }
  designerStore.updateComponent(props.component.id, { props: patch })
  syncCurrentConfig(nextProps)
}

function syncCurrentConfig(overrides = {}) {
  const p = {
    ...localProps.value,
    ...overrides,
  }
  syncBridge({
    action: 'update',
    config: buildConfig(p),
  })
}

function buildConfig(source = {}) {
  const modelCode = source.modelCode || ''
  const relationKey = source.relationKey || modelCode
  const relationsForObject = relations.value.filter(r =>
    r.targetObjectCode === modelCode || r.sourceObjectCode === modelCode,
  )
  const matched = relationsForObject.find(r => resolveRelKey(r) === relationKey)
    || relationsForObject[0]
    || null
  const fields = (Array.isArray(source.columns) ? source.columns : []).map((c) => {
    const fieldCode = c.fieldCode || c
    const asset = availableFields.value.find(f => fc(f) === fieldCode) || {}
    return {
      fieldCode,
      fieldName: c.fieldLabel || fieldCode,
      label: c.fieldLabel || fieldCode,
      // 子表运行态控件依赖字段定义，只传编码会让下拉/人员/引用退化为输入框
      componentType: asset.componentType,
      dictType: asset.dictType,
      dataType: asset.dataType,
      required: asset.required,
      referenceObjectCode: asset.referenceObjectCode,
      referenceDisplayField: asset.referenceDisplayField,
      basicProps: asset.basicProps,
    }
  })
  return {
    relation: matched,
    relationKey: relationKey || modelCode || '',
    title: source.header || '关联子表',
    displayMode: source.displayMode || 'inline_grid',
    modelCode: modelCode || '',
    modelName: objectCache.value.find(o => o.objectCode === modelCode)?.objectName || modelCode || '',
    tableName: '',
    fields,
    allowCreate: source.allowCreate !== false,
    allowSelectExisting: source.allowSelectExisting === true,
    selectorMultiple: source.selectorMultiple !== false,
    selectorDisplayFields: Array.isArray(source.selectorDisplayFields) ? source.selectorDisplayFields : [],
    selectorFilterFields: Array.isArray(source.selectorFilterFields) ? source.selectorFilterFields : [],
  }
}

function syncBridge(payload) {
  if (pageSchemaBridge?.syncSubTableConfig) {
    pageSchemaBridge.syncSubTableConfig({
      ...payload,
      componentId: props.component.id,
    })
  }
}

function resolveRelKey(rel) {
  return resolveChildTableRelationKey(rel)
}

function fc(f) {
  return resolveFieldCode(f)
}

function fl(f) {
  return resolveFieldLabel(f)
}

function unwrap(v) {
  if (Array.isArray(v))
    return v
  if (Array.isArray(v?.records))
    return v.records
  if (Array.isArray(v?.list))
    return v.list
  return []
}
</script>

<style scoped>
.subtable-inline-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 2px 16px;
}

.inline-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.inline-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary, #4b5563);
  line-height: 1.4;
}

.inline-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.inline-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
  gap: 12px;
}

.behavior-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.behavior-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1f2937);
  line-height: 1.4;
}

.behavior-desc {
  font-size: 11px;
  color: var(--text-tertiary, #86909c);
  line-height: 1.4;
}

/** 入口行：标题+摘要文案+按钮，点开弹窗配置 */
.inline-entry-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #ececf0;
  border-radius: 8px;
  background: #fafafc;
}

.entry-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.entry-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1f2937);
  line-height: 1.4;
}

.entry-desc {
  font-size: 11px;
  color: var(--text-tertiary, #86909c);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-hint {
  font-size: 11px;
  color: var(--text-tertiary, #86909c);
  line-height: 1.5;
}

.inline-hint-ok {
  color: #18a058;
}
</style>
