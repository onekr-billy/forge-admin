<template>
  <section class="field-linkage-editor">
    <header class="editor-heading">
      <div>
        <strong>字段联动</strong>
        <span>查询回填、字典级联、对象过滤和清空规则统一在事件页维护。</span>
      </div>
      <n-button size="small" type="primary" secondary @click="addRule">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        新增联动
      </n-button>
    </header>

    <div v-if="rules.length" class="rule-list">
      <article v-for="(rule, index) in rules" :key="rule.ruleId || index" class="rule-row">
        <div class="rule-row-heading">
          <div>
            <strong>{{ ruleLabel(rule) }}</strong>
            <span>{{ rule.sourceField || '未选择控制字段' }} → {{ rule.targetField || '未选择目标字段' }}</span>
          </div>
          <n-space size="small">
            <n-switch
              size="small"
              :value="rule.enabled !== false"
              @update:value="updateRule(index, { enabled: $event })"
            />
            <n-button circle quaternary type="error" title="删除联动" @click="removeRule(index)">
              <template #icon>
                <n-icon><TrashOutline /></n-icon>
              </template>
            </n-button>
          </n-space>
        </div>

        <div class="rule-grid">
          <n-form-item label="控制字段">
            <n-select
              :value="rule.sourceField || ''"
              :options="fieldOptions"
              filterable
              clearable
              placeholder="选择源字段"
              @update:value="updateRule(index, { sourceField: $event || '' })"
            />
          </n-form-item>
          <n-form-item label="联动方式">
            <n-select
              :value="rule.type"
              :options="linkageTypeOptions"
              @update:value="updateType(index, $event)"
            />
          </n-form-item>
          <n-form-item label="目标字段">
            <n-select
              :value="rule.targetField || ''"
              :options="targetFieldOptions(rule)"
              filterable
              clearable
              placeholder="选择目标字段"
              @update:value="updateRule(index, { targetField: $event || '' })"
            />
          </n-form-item>
        </div>

        <div v-if="rule.type === 'linkedDict'" class="rule-grid rule-config-grid">
          <n-form-item label="源字典类型">
            <n-input :value="rule.dictConfig?.sourceDictType || ''" @update:value="updateNestedRule(index, 'dictConfig', { sourceDictType: $event })" />
          </n-form-item>
          <n-form-item label="目标字典类型">
            <n-input :value="rule.dictConfig?.targetDictType || ''" @update:value="updateNestedRule(index, 'dictConfig', { targetDictType: $event })" />
          </n-form-item>
          <n-form-item label="关联字典类型字段">
            <n-input :value="rule.dictConfig?.linkedDictType || ''" placeholder="可选" @update:value="updateNestedRule(index, 'dictConfig', { linkedDictType: $event })" />
          </n-form-item>
        </div>

        <div v-else-if="rule.type === 'objectReference'" class="rule-grid rule-config-grid">
          <n-form-item label="目标对象">
            <n-select
              :value="rule.objectConfig?.targetObjectCode || ''"
              :options="objectOptions"
              filterable
              clearable
              placeholder="选择被过滤对象"
              @update:value="updateNestedRule(index, 'objectConfig', { targetObjectCode: $event || '' })"
            />
          </n-form-item>
          <n-form-item label="显示字段">
            <n-input :value="rule.objectConfig?.displayField || ''" @update:value="updateNestedRule(index, 'objectConfig', { displayField: $event })" />
          </n-form-item>
          <n-form-item label="请求参数名">
            <n-input :value="rule.remoteConfig?.paramName || rule.sourceField || ''" @update:value="updateNestedRule(index, 'remoteConfig', { paramName: $event })" />
          </n-form-item>
        </div>

        <div v-else-if="rule.type === 'remoteParam'" class="rule-grid rule-config-grid">
          <n-form-item label="远程接口">
            <n-input :value="rule.remoteConfig?.url || ''" placeholder="/api/..." @update:value="updateNestedRule(index, 'remoteConfig', { url: $event })" />
          </n-form-item>
          <n-form-item label="请求参数名">
            <n-input :value="rule.remoteConfig?.paramName || rule.sourceField || ''" @update:value="updateNestedRule(index, 'remoteConfig', { paramName: $event })" />
          </n-form-item>
          <n-form-item label="请求方式">
            <n-select :value="rule.remoteConfig?.method || 'GET'" :options="methodOptions" @update:value="updateNestedRule(index, 'remoteConfig', { method: $event })" />
          </n-form-item>
        </div>

        <div class="rule-footer">
          <n-form-item label="源值为空时">
            <n-select :value="rule.emptyStrategy" :options="emptyStrategyOptions" @update:value="updateRule(index, { emptyStrategy: $event })" />
          </n-form-item>
          <label class="rule-switch">
            <span>源字段变化后清空目标</span>
            <n-switch :value="rule.clearOnSourceChange !== false" @update:value="updateRule(index, { clearOnSourceChange: $event })" />
          </label>
        </div>
      </article>
    </div>
    <n-empty v-else description="暂无字段联动，点击右上角新增" />
  </section>
</template>

<script setup>
import { AddOutline, TrashOutline } from '@vicons/ionicons5'
import { computed } from 'vue'
import { normalizeLinkageSchema } from '../form-first/linkageSchema'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  fields: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue'])
const linkageTypeOptions = [
  { label: '字典级联', value: 'linkedDict' },
  { label: '对象引用过滤', value: 'objectReference' },
  { label: '远程选项过滤', value: 'remoteParam' },
  { label: '仅清空目标', value: 'clear' },
]
const emptyStrategyOptions = [
  { label: '清空目标', value: 'empty' },
  { label: '保留全部选项', value: 'all' },
  { label: '禁用目标字段', value: 'disabled' },
]
const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
]
const rules = computed(() => normalizeLinkageSchema({ rules: props.modelValue }).rules)
const fieldOptions = computed(() => props.fields.map((field) => {
  const value = String(field?.fieldCode || field?.field || '').trim()
  return value ? { label: `${field?.fieldName || field?.label || value}（${value}）`, value } : null
}).filter(Boolean))
const objectOptions = computed(() => props.relations.map((relation) => {
  const value = String(relation?.targetObjectCode || relation?.modelCode || '').trim()
  return value ? { label: `${relation?.targetObjectName || relation?.modelName || value}（${value}）`, value } : null
}).filter(Boolean))

function targetFieldOptions(rule) {
  return fieldOptions.value.filter(option => option.value !== rule?.sourceField)
}

function ruleLabel(rule) {
  return linkageTypeOptions.find(option => option.value === rule?.type)?.label || '字段联动'
}

function addRule() {
  emitRules([...rules.value, {
    ruleId: `linkage_${Date.now()}`,
    type: 'clear',
    sourceField: '',
    targetField: '',
    emptyStrategy: 'empty',
    clearOnSourceChange: true,
    enabled: true,
  }])
}

function updateType(index, type) {
  const patch = { type, dataSourceType: type === 'linkedDict' ? 'dict' : type === 'objectReference' ? 'object' : type === 'clear' ? 'none' : 'remote' }
  if (type === 'clear') {
    patch.emptyStrategy = 'empty'
  }
  updateRule(index, patch)
}

function updateRule(index, patch = {}) {
  emitRules(rules.value.map((rule, ruleIndex) => ruleIndex === index ? { ...rule, ...patch } : rule))
}

function updateNestedRule(index, key, patch = {}) {
  const current = rules.value[index] || {}
  updateRule(index, { [key]: { ...(current[key] || {}), ...patch } })
}

function removeRule(index) {
  emitRules(rules.value.filter((_, ruleIndex) => ruleIndex !== index))
}

function emitRules(nextRules) {
  emit('update:modelValue', normalizeLinkageSchema({ rules: nextRules }).rules)
}
</script>

<style scoped>
.field-linkage-editor {
  display: grid;
  gap: 14px;
  padding: 2px 0 26px;
}

.editor-heading,
.rule-row-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor-heading strong,
.editor-heading span,
.rule-row-heading strong,
.rule-row-heading span {
  display: block;
}

.editor-heading strong,
.rule-row-heading strong {
  font-size: 13px;
}

.editor-heading span,
.rule-row-heading span {
  margin-top: 3px;
  color: #86909c;
  font-size: 11px;
}

.rule-list {
  display: grid;
  gap: 10px;
}

.rule-row {
  padding: 14px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
}

.rule-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 12px;
  margin-top: 12px;
}

.rule-footer {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(220px, 1fr);
  align-items: end;
  gap: 16px;
  margin-top: 5px;
}

.rule-switch {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 7px;
  color: #4e5969;
  font-size: 12px;
}

@media (max-width: 760px) {
  .rule-grid,
  .rule-footer {
    grid-template-columns: 1fr;
  }
}
</style>
