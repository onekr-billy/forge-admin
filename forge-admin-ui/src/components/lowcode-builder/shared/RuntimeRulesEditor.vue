<template>
  <div class="runtime-rules-editor">
    <div class="runtime-rules-head">
      <div>
        <strong>{{ title }}</strong>
        <span>按当前数据控制显示、编辑状态和样式。</span>
      </div>
      <n-button size="tiny" type="primary" secondary @click="addRule">
        添加规则
      </n-button>
    </div>

    <div v-if="!localRules.length" class="runtime-rules-empty">
      暂无规则。常见用法：状态为“已完成”时隐藏按钮，或状态为“已支付”时金额字段只读。
    </div>

    <div v-for="(rule, index) in localRules" :key="rule.id || index" class="runtime-rule-card">
      <div class="runtime-rule-card-head">
        <n-switch
          size="small"
          :value="rule.enabled !== false"
          @update:value="patchRule(index, { enabled: $event })"
        />
        <strong>规则 {{ index + 1 }}</strong>
        <n-button size="tiny" quaternary type="error" @click="removeRule(index)">
          删除
        </n-button>
      </div>

      <div class="runtime-rule-grid">
        <label>
          <span>数据来源</span>
          <n-select
            :value="firstCondition(rule).source || 'record'"
            :options="sourceOptions"
            size="small"
            @update:value="patchFirstCondition(index, { source: $event || 'record', field: '' })"
          />
        </label>
        <label>
          <span>{{ sourceFieldLabel(firstCondition(rule).source) }}</span>
          <n-select
            v-if="!isFreePathSource(firstCondition(rule).source)"
            :value="firstCondition(rule).field || ''"
            :options="fieldOptions"
            size="small"
            filterable
            clearable
            placeholder="选择字段"
            @update:value="patchFirstCondition(index, { field: $event || '' })"
          />
          <n-input
            v-else
            :value="firstCondition(rule).field || ''"
            size="small"
            placeholder="例如 id、recordId、status"
            @update:value="patchFirstCondition(index, { field: $event || '' })"
          />
        </label>
        <label>
          <span>满足</span>
          <n-select
            :value="firstCondition(rule).operator || 'eq'"
            :options="operatorOptions"
            size="small"
            @update:value="patchFirstCondition(index, { operator: $event || 'eq' })"
          />
        </label>
        <label v-if="!['empty', 'notEmpty'].includes(firstCondition(rule).operator)" class="runtime-rule-wide">
          <span>目标值</span>
          <n-input
            :value="firstCondition(rule).value ?? ''"
            size="small"
            placeholder="例如 PAID、1、已完成"
            @update:value="patchFirstCondition(index, { value: $event })"
          />
        </label>
      </div>

      <div class="runtime-rule-effects">
        <label>
          <span>满足时</span>
          <n-select
            size="small"
            :value="effectMode(rule)"
            :options="effectOptions"
            @update:value="patchRuleEffectMode(index, $event)"
          />
        </label>
        <label>
          <span>只读</span>
          <n-switch size="small" :value="rule.effect?.readonly === true" @update:value="patchRuleEffect(index, { readonly: $event })" />
        </label>
        <label>
          <span>禁用</span>
          <n-switch size="small" :value="rule.effect?.disabled === true" @update:value="patchRuleEffect(index, { disabled: $event })" />
        </label>
        <label>
          <span>必填</span>
          <n-switch size="small" :value="rule.effect?.required === true" @update:value="patchRuleEffect(index, { required: $event })" />
        </label>
      </div>

      <div class="runtime-rule-grid">
        <label>
          <span>文字颜色</span>
          <n-color-picker
            :value="rule.effect?.textColor || ''"
            size="small"
            :show-alpha="false"
            @update:value="patchRuleEffect(index, { textColor: $event || '' })"
          />
        </label>
        <label>
          <span>附加类名</span>
          <n-input
            :value="rule.effect?.className || ''"
            size="small"
            placeholder="可选"
            @update:value="patchRuleEffect(index, { className: $event || '' })"
          />
        </label>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  rules: {
    type: Array,
    default: () => [],
  },
  fieldOptions: {
    type: Array,
    default: () => [],
  },
  title: {
    type: String,
    default: '运行规则',
  },
})

const emit = defineEmits(['update:rules'])

const localRules = computed(() => Array.isArray(props.rules) ? props.rules : [])
const sourceOptions = [
  { label: '当前记录/详情', value: 'record' },
  { label: '当前行数据', value: 'row' },
  { label: '当前表单数据', value: 'formData' },
  { label: 'URL 查询参数', value: 'query' },
  { label: 'URL 路由参数', value: 'params' },
  { label: '当前用户', value: 'user' },
]
const operatorOptions = [
  { label: '等于', value: 'eq' },
  { label: '不等于', value: 'ne' },
  { label: '大于', value: 'gt' },
  { label: '大于等于', value: 'gte' },
  { label: '小于', value: 'lt' },
  { label: '小于等于', value: 'lte' },
  { label: '包含', value: 'contains' },
  { label: '属于列表', value: 'in' },
  { label: '为空', value: 'empty' },
  { label: '不为空', value: 'notEmpty' },
]
const effectOptions = [
  { label: '显示字段', value: 'visible' },
  { label: '隐藏字段', value: 'hidden' },
]

function isFreePathSource(source = 'record') {
  return ['query', 'params', 'route', 'user'].includes(source || 'record')
}

function sourceFieldLabel(source = 'record') {
  const map = {
    record: '字段',
    row: '行字段',
    formData: '表单字段',
    query: 'URL 参数名',
    params: '路由参数名',
    route: '路由路径',
    user: '用户字段',
  }
  return map[source || 'record'] || '字段'
}

function firstCondition(rule = {}) {
  return Array.isArray(rule.conditions) && rule.conditions.length
    ? rule.conditions[0]
    : { source: 'record', field: '', operator: 'eq', value: '' }
}

function addRule() {
  emitRules([
    ...localRules.value,
    {
      id: `rule_${Date.now()}`,
      enabled: true,
      mode: 'all',
      conditions: [{ source: 'record', field: '', operator: 'eq', value: '' }],
      effect: { visible: true, whenUnmatched: 'hidden' },
    },
  ])
}

function removeRule(index) {
  emitRules(localRules.value.filter((_, idx) => idx !== index))
}

function patchRule(index, patch = {}) {
  emitRules(localRules.value.map((rule, idx) => idx === index ? { ...rule, ...patch } : rule))
}

function patchFirstCondition(index, patch = {}) {
  emitRules(localRules.value.map((rule, idx) => {
    if (idx !== index)
      return rule
    const conditions = Array.isArray(rule.conditions) && rule.conditions.length
      ? [...rule.conditions]
      : [{ source: 'record', field: '', operator: 'eq', value: '' }]
    conditions[0] = { source: 'record', ...conditions[0], ...patch }
    return { ...rule, conditions }
  }))
}

function patchRuleEffect(index, patch = {}) {
  emitRules(localRules.value.map((rule, idx) => {
    if (idx !== index)
      return rule
    const effect = Object.fromEntries(Object.entries({ ...(rule.effect || {}), ...patch })
      .filter(([, value]) => value !== undefined && value !== ''))
    return { ...rule, effect }
  }))
}

function effectMode(rule = {}) {
  if (rule.effect?.visible === true)
    return 'visible'
  return 'hidden'
}

function patchRuleEffectMode(index, mode) {
  patchRuleEffect(index, mode === 'visible'
    ? { visible: true, hidden: undefined, whenUnmatched: 'hidden' }
    : { hidden: true, visible: undefined, whenUnmatched: 'visible' })
}

function emitRules(rules = []) {
  emit('update:rules', rules)
}
</script>

<style scoped>
.runtime-rules-editor {
  display: grid;
  gap: 10px;
}

.runtime-rules-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
}

.runtime-rules-head strong,
.runtime-rules-head span {
  display: block;
}

.runtime-rules-head span,
.runtime-rules-empty {
  color: #71717a;
  font-size: 12px;
  line-height: 1.5;
}

.runtime-rules-empty {
  border: 1px dashed #d4d4d8;
  border-radius: 8px;
  background: #fafafa;
  padding: 10px;
}

.runtime-rule-card {
  display: grid;
  gap: 10px;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  background: #fff;
  padding: 10px;
}

.runtime-rule-card-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.runtime-rule-grid {
  display: grid;
  gap: 8px;
}

.runtime-rule-grid label,
.runtime-rule-effects label {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.runtime-rule-grid span,
.runtime-rule-effects span {
  color: #52525b;
  font-size: 12px;
  font-weight: 600;
}

.runtime-rule-effects {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
</style>
