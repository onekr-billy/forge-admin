<template>
  <n-modal
    :show="show"
    preset="card"
    :title="isEditing ? '编辑关联子表' : '添加关联子表'"
    :bordered="false"
    class="sub-table-config-dialog"
    :style="{ width: 'min(720px, calc(100vw - 32px))' }"
    :mask-closable="false"
    @update:show="emit('update:show', $event)"
  >
    <n-steps :current="currentStep" size="small" class="config-steps">
      <n-step title="选择对象" description="选择子表对应的业务对象" />
      <n-step title="配置字段" description="选择可见字段与展示方式" />
    </n-steps>

    <!-- Step 1: Select target object -->
    <div v-if="currentStep === 1" class="step-content">
      <n-form label-placement="top">
        <n-form-item label="目标业务对象" required>
          <n-select
            v-model:value="selectedObjectCode"
            :options="objectOptions"
            :loading="loadingObjects"
            :render-label="renderObjectOptionLabel"
            filterable
            placeholder="搜索并选择子表业务对象"
            @update:value="handleObjectChange"
          />
        </n-form-item>

        <n-form-item v-if="matchedRelations.length" label="关联关系">
          <n-radio-group v-model:value="selectedRelationKey" class="relation-radio-group">
            <n-radio
              v-for="relation in matchedRelations"
              :key="resolveRelationKey(relation)"
              :value="resolveRelationKey(relation)"
              class="relation-radio-item"
            >
              <div class="relation-option-content">
                <strong>{{ relation.relationName || relation.targetObjectCode }}</strong>
                <small>{{ relationTypeLabel(relation.relationType) }} · {{ relation.sourceFieldCode || '?' }} → {{ relation.targetFieldCode || '?' }}</small>
              </div>
            </n-radio>
          </n-radio-group>
        </n-form-item>

        <div v-if="selectedObjectCode && !matchedRelations.length && !loadingFields" class="relation-hint">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" fill="currentColor" /></svg>
          未找到主表与「{{ selectedObjectName }}」的预定义关系。运行时将按对象编码自动匹配；如需精确定义关系，可在业务对象设计的关系与级联中配置。
        </div>

        <n-form-item label="子表标题" required>
          <n-input
            v-model:value="sectionTitle"
            placeholder="例如：订单明细、费用行"
          />
        </n-form-item>
      </n-form>
    </div>

    <!-- Step 2: Field & display config -->
    <div v-if="currentStep === 2" class="step-content">
      <div class="config-grid">
        <n-form-item label="展示方式" class="display-mode-item">
          <n-radio-group v-model:value="displayMode" size="small">
            <n-radio-button value="inline_grid">
              行内表格
            </n-radio-button>
            <n-radio-button value="card_list">
              卡片列表
            </n-radio-button>
          </n-radio-group>
        </n-form-item>

        <n-form-item label="数据填充" class="data-fill-item">
          <n-space>
            <n-checkbox v-model:checked="allowCreate">
              允许新增行
            </n-checkbox>
            <n-checkbox v-model:checked="allowSelectExisting">
              允许选择已有记录
            </n-checkbox>
          </n-space>
        </n-form-item>
      </div>

      <section class="field-selector">
        <div class="field-selector-head">
          <div>
            <strong>可见字段</strong>
            <span v-if="availableFields.length">已选 {{ visibleFieldCodes.length }} / {{ availableFields.length }}</span>
          </div>
          <n-checkbox
            v-if="availableFields.length"
            :checked="allFieldsSelected"
            :indeterminate="someFieldsSelected"
            @update:checked="toggleAllFields"
          >
            全选
          </n-checkbox>
        </div>

        <n-spin :show="loadingFields">
          <n-checkbox-group
            v-if="availableFields.length"
            v-model:value="visibleFieldCodes"
            class="field-checkbox-grid"
          >
            <n-checkbox
              v-for="field in availableFields"
              :key="fieldCode(field)"
              :value="fieldCode(field)"
            >
              <span class="field-checkbox-copy">
                <strong>{{ fieldLabel(field) }}</strong>
                <small>{{ fieldCode(field) }}</small>
              </span>
            </n-checkbox>
          </n-checkbox-group>
          <n-empty
            v-else
            size="small"
            :description="selectedObjectCode ? '未读取到字段，请保存对象字段后重试' : '请先选择目标对象'"
          />
        </n-spin>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <n-button
          v-if="currentStep > 1"
          quaternary
          @click="currentStep--"
        >
          上一步
        </n-button>
        <div class="footer-spacer" />
        <n-button @click="closeDialog">
          取消
        </n-button>
        <n-button
          v-if="currentStep < 2"
          type="primary"
          :disabled="!canProceedStep1"
          @click="goToStep2"
        >
          下一步
        </n-button>
        <n-button
          v-else
          type="primary"
          :disabled="!canConfirm"
          @click="confirmConfig"
        >
          {{ isEditing ? '保存' : '确认添加' }}
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed, h, ref, watch } from 'vue'
import { businessObjectFields, businessObjectList } from '@/api/business-app'
import IconRenderer from '@/components/IconRenderer.vue'
import { resolveChildTableRelationKey } from '../../child-table-section-config'

const props = defineProps({
  show: { type: Boolean, default: false },
  relations: { type: Array, default: () => [] },
  objectCode: { type: String, default: '' },
  editConfig: { type: Object, default: null },
})

const emit = defineEmits(['update:show', 'confirm'])
const message = useMessage()

const currentStep = ref(1)
const selectedObjectCode = ref('')
const selectedRelationKey = ref('')
const sectionTitle = ref('')
const displayMode = ref('inline_grid')
const allowCreate = ref(true)
const allowSelectExisting = ref(false)
const availableFields = ref([])
const visibleFieldCodes = ref([])
const loadingObjects = ref(false)
const loadingFields = ref(false)
let objectCache = []
let loadFieldId = 0

const isEditing = computed(() => Boolean(props.editConfig))

const objectOptions = computed(() => {
  return objectCache
    .filter(obj => obj.objectCode !== props.objectCode)
    .map(obj => ({
      label: obj.objectName || obj.objectCode,
      value: obj.objectCode,
      icon: obj.icon || '',
    }))
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

const selectedObjectName = computed(() => {
  const obj = objectCache.find(o => o.objectCode === selectedObjectCode.value)
  return obj?.objectName || selectedObjectCode.value
})

const matchedRelations = computed(() => {
  if (!selectedObjectCode.value)
    return []
  return (props.relations || []).filter(r =>
    r.targetObjectCode === selectedObjectCode.value
    || r.sourceObjectCode === selectedObjectCode.value,
  )
})

const allFieldsSelected = computed(() =>
  Boolean(availableFields.value.length)
  && visibleFieldCodes.value.length === availableFields.value.length,
)
const someFieldsSelected = computed(() =>
  visibleFieldCodes.value.length > 0 && !allFieldsSelected.value,
)
const canProceedStep1 = computed(() =>
  Boolean(selectedObjectCode.value) && Boolean(sectionTitle.value.trim()),
)
const canConfirm = computed(() =>
  visibleFieldCodes.value.length > 0,
)

watch(() => props.show, async (show) => {
  if (show) {
    await loadObjectList()
    initDialog()
  }
})

async function initDialog() {
  currentStep.value = 1
  selectedObjectCode.value = ''
  selectedRelationKey.value = ''
  sectionTitle.value = ''
  displayMode.value = 'inline_grid'
  allowCreate.value = true
  allowSelectExisting.value = false
  availableFields.value = []
  visibleFieldCodes.value = []

  if (props.editConfig) {
    selectedObjectCode.value = props.editConfig.modelCode || ''
    sectionTitle.value = props.editConfig.title || ''
    displayMode.value = props.editConfig.displayMode || 'inline_grid'
    selectedRelationKey.value = props.editConfig.relationKey || ''
    allowCreate.value = props.editConfig.allowCreate !== false
    allowSelectExisting.value = props.editConfig.allowSelectExisting === true
    if (selectedObjectCode.value) {
      await loadFields(selectedObjectCode.value, props.editConfig.fieldCodes)
    }
  }
}

async function loadObjectList() {
  loadingObjects.value = true
  try {
    const res = await businessObjectList({})
    const list = unwrapList(res?.data)
    objectCache = list.filter(o => o && o.objectCode)
  }
  catch {
    objectCache = []
  }
  finally {
    loadingObjects.value = false
  }
}

function handleObjectChange(objectCode) {
  selectedObjectCode.value = objectCode || ''
  availableFields.value = []
  visibleFieldCodes.value = []
  // Auto-select first matched relation
  const matched = matchedRelations.value
  if (matched.length) {
    selectedRelationKey.value = resolveRelationKey(matched[0])
  }
  else {
    selectedRelationKey.value = ''
  }
  // Auto-fill title from object name if not editing
  if (!props.editConfig && !sectionTitle.value.trim()) {
    sectionTitle.value = selectedObjectName.value
  }
  if (objectCode)
    loadFields(objectCode)
}

async function loadFields(objectCode, selectedCodes = null) {
  const requestId = ++loadFieldId
  loadingFields.value = true
  try {
    let obj = objectCache.find(o => o.objectCode === objectCode)
    if (!obj?.id) {
      const listRes = await businessObjectList({ objectCode })
      const objects = unwrapList(listRes?.data)
      obj = objects.find(o => o.objectCode === objectCode) || obj
    }
    if (obj?.id) {
      const fieldRes = await businessObjectFields(obj.id)
      const fields = unwrapList(fieldRes?.data)
        .filter(f => f && !f.systemField)
        .filter(f => !['DISABLED', 'HIDDEN'].includes(String(f.fieldStatus || '').toUpperCase()))
        .filter(f => Boolean(fieldCode(f)))
      if (requestId !== loadFieldId)
        return
      availableFields.value = fields
      const codes = fields.map(fieldCode).filter(Boolean)
      const selected = Array.isArray(selectedCodes) ? new Set(selectedCodes) : null
      visibleFieldCodes.value = selected
        ? codes.filter(c => selected.has(c))
        : codes
    }
  }
  catch (err) {
    if (requestId !== loadFieldId)
      return
    message.error(err?.message || '读取字段失败')
  }
  finally {
    if (requestId === loadFieldId)
      loadingFields.value = false
  }
}

function goToStep2() {
  if (!canProceedStep1.value)
    return
  currentStep.value = 2
  if (!availableFields.value.length && selectedObjectCode.value)
    loadFields(selectedObjectCode.value)
}

function toggleAllFields(checked) {
  visibleFieldCodes.value = checked
    ? availableFields.value.map(fieldCode).filter(Boolean)
    : []
}

function confirmConfig() {
  if (!canConfirm.value)
    return

  const selectedCodes = new Set(visibleFieldCodes.value)
  const selectedFields = availableFields.value.filter(f => selectedCodes.has(fieldCode(f)))
  const matched = matchedRelations.value.find(r => resolveRelationKey(r) === selectedRelationKey.value)

  const config = {
    relation: matched || null,
    relationKey: selectedRelationKey.value || selectedObjectCode.value,
    title: sectionTitle.value.trim(),
    displayMode: displayMode.value,
    modelCode: selectedObjectCode.value,
    modelName: selectedObjectName.value,
    tableName: '',
    fields: selectedFields,
    allowCreate: allowCreate.value,
    allowSelectExisting: allowSelectExisting.value,
  }

  emit('confirm', config)
  closeDialog()
}

function closeDialog() {
  emit('update:show', false)
}

function resolveRelationKey(relation = {}) {
  return resolveChildTableRelationKey(relation)
}

function relationTypeLabel(type = '') {
  const map = { REFERENCE: '引用', DETAIL: '明细', CHILD_LIST: '子列表', MANY_TO_MANY: '多对多' }
  return map[String(type).toUpperCase()] || type || '引用'
}

function fieldCode(field = {}) {
  return field.fieldCode || field.sourceField || field.field || ''
}

function fieldLabel(field = {}) {
  return field.fieldName || field.rawLabel || field.label || fieldCode(field)
}

function unwrapList(value) {
  if (Array.isArray(value))
    return value
  if (Array.isArray(value?.records))
    return value.records
  if (Array.isArray(value?.list))
    return value.list
  return []
}
</script>

<style scoped>
.config-steps {
  margin-bottom: 20px;
}

.step-content {
  min-height: 240px;
}

.relation-radio-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.relation-radio-item {
  padding: 6px 10px;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 6px;
  width: 100%;
}

.relation-option-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.relation-option-content strong {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1f2937);
}

.relation-option-content small {
  font-size: 11px;
  color: var(--text-tertiary, #86909c);
}

.relation-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  color: #614700;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 12px;
}

.relation-hint svg {
  flex-shrink: 0;
  margin-top: 2px;
  color: #d48806;
}

.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.field-selector {
  overflow: hidden;
  min-height: 160px;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 8px;
}

.field-selector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-light, #e5e7eb);
  background: var(--bg-secondary, #f9fafb);
  padding: 8px 12px;
}

.field-selector-head > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-selector-head strong {
  font-size: 13px;
  font-weight: 600;
}

.field-selector-head span {
  font-size: 12px;
  color: var(--text-tertiary, #86909c);
}

.field-checkbox-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 16px;
  max-height: 260px;
  overflow-y: auto;
  padding: 10px 12px;
}

.field-checkbox-copy {
  display: inline-flex;
  flex-direction: column;
  gap: 1px;
  margin-left: 4px;
}

.field-checkbox-copy strong {
  color: var(--text-primary, #1f2937);
  font-size: 13px;
  font-weight: 500;
}

.field-checkbox-copy small {
  color: var(--text-tertiary, #86909c);
  font-size: 11px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-spacer {
  flex: 1;
}

@media (max-width: 600px) {
  .config-grid,
  .field-checkbox-grid {
    grid-template-columns: 1fr;
  }
}
</style>
