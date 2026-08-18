<script setup>
import { computed, ref, watch } from 'vue'
import { createBusinessProcessNodeTemplate } from './business-process-node-types.js'
import { createStartTemplateConfig, START_NODE_TEMPLATES } from './node-templates.js'

const props = defineProps({
  type: { type: String, required: true },
  config: { type: Object, default: () => ({}) },
  fields: { type: Array, default: () => [] },
  serviceActors: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:type', 'update:config', 'update:recordIdSource'])
const localConfig = ref(clone(props.config))
const selectedTemplate = ref('')

const fieldOptions = computed(() => props.fields
  .map(field => ({
    label: field.fieldName || field.fieldLabel || field.label || field.fieldCode || field.code,
    value: field.fieldCode || field.code || field.value,
  }))
  .filter(item => item.value))

const conditionKey = computed(() => props.type === 'START_MANUAL' ? 'visibleCondition' : 'condition')
const firstRule = computed(() => localConfig.value?.[conditionKey.value]?.rules?.[0] || null)

watch(() => props.config, (value) => {
  localConfig.value = clone(value || {})
  selectedTemplate.value = ''
}, { deep: true })

function applyTemplate(value) {
  const template = createStartTemplateConfig(value)
  if (!template)
    return
  selectedTemplate.value = value
  localConfig.value = template.config
  emit('update:type', template.type)
  emit('update:recordIdSource', recordIdSource(template.type))
  emitConfig()
}

function handleTypeChange(event) {
  const type = event.target.value
  const template = createBusinessProcessNodeTemplate(type)
  selectedTemplate.value = ''
  localConfig.value = clone(template.config)
  emit('update:type', type)
  emit('update:recordIdSource', recordIdSource(type))
  emitConfig()
}

function patchConfig(key, value) {
  localConfig.value = { ...localConfig.value, [key]: value }
  emitConfig()
}

function togglePosition(position, checked) {
  const positions = new Set(localConfig.value.positions || [])
  if (checked)
    positions.add(position)
  else
    positions.delete(position)
  patchConfig('positions', [...positions])
}

function updateConditionRule(key, value) {
  const condition = clone(localConfig.value[conditionKey.value] || { operator: 'AND', rules: [] })
  const rule = { ...(condition.rules?.[0] || { source: 'record', operator: 'EQ' }), [key]: value }
  condition.rules = [rule]
  patchConfig(conditionKey.value, condition)
}

function clearCondition() {
  const next = { ...localConfig.value }
  delete next[conditionKey.value]
  localConfig.value = next
  emitConfig()
}

function emitConfig() {
  emit('update:config', clone(localConfig.value))
}

function recordIdSource(type) {
  return {
    START_MANUAL: 'RUNTIME_RECORD',
    START_EVENT: 'EVENT_RECORD',
    START_SCHEDULE: 'SCHEDULE_SCAN_RECORD',
  }[type]
}

function clone(value) {
  return JSON.parse(JSON.stringify(value || {}))
}
</script>

<template>
  <div class="start-node-config structured-config-stack">
    <section class="template-section" aria-label="开始节点场景模板">
      <div class="template-section-head">
        <strong>场景模板</strong>
        <span>选择后仍可继续调整</span>
      </div>
      <div class="template-grid">
        <button
          v-for="item in START_NODE_TEMPLATES"
          :key="item.value"
          type="button"
          class="template-card"
          :class="{ 'is-selected': selectedTemplate === item.value }"
          :data-start-template="item.value"
          @click="applyTemplate(item.value)"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </div>
    </section>

    <label class="config-field">
      <span>触发方式</span>
      <select data-start-type :value="type" @change="handleTypeChange">
        <option value="START_MANUAL">用户点击开始</option>
        <option value="START_EVENT">记录事件触发</option>
        <option value="START_SCHEDULE">按日期定时扫描</option>
      </select>
      <small>一个业务流程只保留一种触发方式，其他触发场景请创建独立流程。</small>
    </label>

    <template v-if="type === 'START_MANUAL'">
      <div class="config-field">
        <span>显示位置</span>
        <label class="inline-check">
          <input
            type="checkbox"
            :checked="localConfig.positions?.includes('ROW')"
            @change="togglePosition('ROW', $event.target.checked)"
          >
          列表行操作
        </label>
        <label class="inline-check">
          <input
            type="checkbox"
            :checked="localConfig.positions?.includes('DETAIL')"
            @change="togglePosition('DETAIL', $event.target.checked)"
          >
          详情页操作
        </label>
      </div>
      <label class="config-field">
        <span>确认文案</span>
        <input
          :value="localConfig.confirmText || ''"
          placeholder="例如：确认提交当前记录？"
          @input="patchConfig('confirmText', $event.target.value)"
        >
      </label>
      <div class="governed-note">
        运行时使用当前登录人的租户、组织和数据权限；页面不能覆盖执行身份。
      </div>
    </template>

    <template v-else-if="type === 'START_EVENT'">
      <label class="config-field">
        <span>业务事件</span>
        <select :value="localConfig.eventType || 'RECORD_CREATED'" @change="patchConfig('eventType', $event.target.value)">
          <option value="RECORD_CREATED">记录新增后</option>
          <option value="RECORD_UPDATED">记录更新后</option>
          <option value="STATUS_CHANGED">状态变更后</option>
          <option value="FIELD_CHANGED">字段变更后</option>
          <option value="FORM_SUBMITTED">表单提交后</option>
          <option value="ACTION_EXECUTED">业务操作完成后</option>
        </select>
      </label>
      <div class="governed-note">
        运行身份来自服务端事件快照；缺少原操作人、租户或组织时不会创建运行实例。
      </div>
    </template>

    <template v-else>
      <label class="config-field">
        <span>日期字段</span>
        <select :value="localConfig.dueField || ''" @change="patchConfig('dueField', $event.target.value)">
          <option value="" disabled>选择主业务对象日期字段</option>
          <option v-for="item in fieldOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>
      <div class="two-column-fields">
        <label class="config-field">
          <span>提前天数</span>
          <input
            type="number"
            min="0"
            :value="localConfig.lookAheadDays ?? 0"
            @input="patchConfig('lookAheadDays', Number($event.target.value))"
          >
        </label>
        <label class="config-field">
          <span>回看天数</span>
          <input
            type="number"
            min="0"
            :value="localConfig.lookBackDays ?? 0"
            @input="patchConfig('lookBackDays', Number($event.target.value))"
          >
        </label>
      </div>
      <label class="config-field">
        <span>运行账号</span>
        <select
          :value="localConfig.serviceActor?.userConfigKey || ''"
          @change="patchConfig('serviceActor', { mode: 'CONFIGURED_USER', userConfigKey: $event.target.value })"
        >
          <option value="" disabled>选择受限普通服务账号</option>
          <option v-for="item in serviceActors" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <small>未配置合法普通用户时失败关闭，不会回退管理员。</small>
      </label>
    </template>

    <section v-if="type !== 'START_SCHEDULE'" class="condition-card">
      <div class="condition-head">
        <span>{{ type === 'START_MANUAL' ? '按钮显示条件' : '事件过滤条件' }}</span>
        <button v-if="firstRule" type="button" @click="clearCondition">
          清除
        </button>
      </div>
      <div class="condition-row">
        <select :value="firstRule?.field || ''" @change="updateConditionRule('field', $event.target.value)">
          <option value="">
            选择字段
          </option>
          <option v-for="item in fieldOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <select :value="firstRule?.operator || 'EQ'" @change="updateConditionRule('operator', $event.target.value)">
          <option value="EQ">
            等于
          </option>
          <option value="NE">
            不等于
          </option>
          <option value="IN">
            属于
          </option>
          <option value="NOT_EMPTY">
            不为空
          </option>
        </select>
        <input
          v-if="firstRule?.operator !== 'NOT_EMPTY'"
          :value="firstRule?.value ?? ''"
          placeholder="比较值"
          @input="updateConditionRule('value', $event.target.value)"
        >
      </div>
    </section>
  </div>
</template>

<style scoped>
.structured-config-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.template-section {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.template-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.template-section-head strong {
  color: var(--text-color-1, #0f172a);
  font-size: 13px;
}

.template-section-head span {
  color: var(--text-color-3, #64748b);
  font-size: 12px;
}

.template-grid {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.template-card {
  min-width: 0;
  min-height: 76px;
  border: 1px solid rgba(148, 163, 184, 0.38);
  border-radius: 7px;
  background: var(--card-color, #fff);
  padding: 10px;
  text-align: left;
}

.template-card:hover,
.template-card.is-selected {
  border-color: var(--primary-color, #2563eb);
  background: rgba(37, 99, 235, 0.05);
}

.template-card strong,
.template-card span {
  display: block;
}

.template-card strong {
  color: var(--text-color-1, #0f172a);
  font-size: 13px;
}

.template-card span {
  margin-top: 5px;
  color: var(--text-color-3, #64748b);
  font-size: 12px;
  line-height: 1.45;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: var(--text-color-2, #334155);
  font-size: 13px;
}

.config-field > span,
.condition-head > span {
  color: var(--text-color-1, #0f172a);
  font-weight: 600;
}

.config-field select,
.config-field input,
.condition-row select,
.condition-row input {
  min-height: 34px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 6px;
  background: var(--input-color, #fff);
  padding: 6px 9px;
  color: var(--text-color-1, #0f172a);
  outline: none;
}

.config-field select:focus,
.config-field input:focus,
.condition-row select:focus,
.condition-row input:focus {
  border-color: var(--primary-color, #2563eb);
}

.config-field small {
  color: var(--text-color-3, #64748b);
  line-height: 1.5;
}

.inline-check {
  display: flex;
  align-items: center;
  gap: 8px;
}

.two-column-fields {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.governed-note,
.condition-card {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 7px;
  background: rgba(241, 245, 249, 0.62);
  padding: 11px 12px;
  color: var(--text-color-2, #475569);
  font-size: 12px;
  line-height: 1.6;
}

.condition-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.condition-head button {
  color: var(--primary-color, #2563eb);
  font-size: 12px;
}

.condition-row {
  display: grid;
  gap: 8px;
  grid-template-columns: 1fr 92px 1fr;
}
</style>
