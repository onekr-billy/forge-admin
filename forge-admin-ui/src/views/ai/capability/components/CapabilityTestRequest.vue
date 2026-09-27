<template>
  <section>
    <div class="request-heading">
      <div><h3>填写业务参数</h3><p>示例值仅供参考，请替换成可用于本次调用的真实数据。</p></div><n-button size="small" @click="confirmReset">
        恢复示例
      </n-button>
    </div>
    <n-radio-group v-if="fields?.length" v-model:value="state.requestMode" size="small" aria-label="参数编辑方式">
      <n-radio-button value="fields" :disabled="!fieldsCompatible">
        按字段填写
      </n-radio-button><n-radio-button value="json">
        JSON 编辑
      </n-radio-button>
    </n-radio-group>
    <p v-else class="hint">
      此契约包含复杂或未声明结构，请使用 JSON 编辑；不会自动改写或丢弃字段。
    </p>
    <div v-if="fields?.length && state.requestMode === 'fields' && fieldsCompatible" class="request-grid">
      <div v-for="(field, index) in fields" :key="field.path" class="request-field">
        <label :for="`request-field-${index}`">{{ field.fieldLabel || field.fieldCode }}<span>{{ field.required ? '必填' : '可选' }}</span></label>
        <code>{{ field.path.slice(2) }}</code>
        <n-input v-if="field.baseType === 'string'" :value="value(field) ?? ''" :input-props="{ id: `request-field-${index}` }" :placeholder="field.fieldCode === 'recordId' ? '填写已保存且当前用户可访问的记录 ID' : '请输入'" @update:value="value => update(field, value)" />
        <n-input-number v-else-if="['integer', 'number'].includes(field.baseType)" :value="value(field) ?? null" :precision="field.baseType === 'integer' ? 0 : undefined" :min="-Number.MAX_SAFE_INTEGER" :max="Number.MAX_SAFE_INTEGER" :input-props="{ id: `request-field-${index}` }" :show-button="false" clearable @update:value="value => update(field, value)" />
        <n-select v-else :value="value(field) == null ? null : String(value(field))" :options="booleanOptions" :input-props="{ id: `request-field-${index}` }" placeholder="请选择 true / false" clearable @update:value="value => update(field, value === null ? null : value === 'true')" />
        <p v-if="field.description && field.description !== '当前版本未提供字段说明'" class="field-hint">
          {{ field.description }}
        </p>
      </div>
    </div>
    <div v-else class="json-editor">
      <label for="test-request-json">请求参数（JSON 对象）</label><n-input v-model:value="state.requestBody" type="textarea" :autosize="{ minRows: 8, maxRows: 16 }" :input-props="{ id: 'test-request-json', spellcheck: false }" />
    </div>
    <p v-if="parseError || editError" class="request-error" role="alert">
      {{ parseError || editError }}
    </p>
    <details v-if="state.guide?.requestNotes?.length || state.guide?.businessRules?.length" class="request-notes">
      <summary>填写说明与业务规则</summary><ul>
        <li v-for="note in [...(state.guide.requestNotes || []), ...(state.guide.businessRules || [])]" :key="note">
          {{ note }}
        </li>
      </ul>
    </details>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useCapabilityOnlineTestStore } from '@/stores/capability/onlineTestStore'
import { editableRequestFields, requestFieldValue, requestObject, updateRequestField } from './capabilityTestRequest'

const emit = defineEmits(['reset'])
const state = useCapabilityOnlineTestStore()
const fields = computed(() => editableRequestFields(state.guide))
const editError = ref('')
let resetDialog
let mounted = true
onBeforeUnmount(() => {
  mounted = false
  resetDialog?.destroy()
})
const booleanOptions = [{ label: 'true', value: 'true' }, { label: 'false', value: 'false' }]
const parseError = computed(() => {
  try {
    requestObject(state.requestBody)
    return ''
  }
  catch (error) { return error.message }
})
const fieldsCompatible = computed(() => !parseError.value && fields.value?.every((field) => {
  const current = value(field)
  if (current == null)
    return true
  if (field.baseType === 'string')
    return typeof current === 'string'
  if (field.baseType === 'boolean')
    return typeof current === 'boolean'
  return typeof current === 'number' && Number.isFinite(current) && (field.baseType !== 'integer' || Number.isSafeInteger(current))
}))
function value(field) {
  return requestFieldValue(requestObject(state.requestBody), field)
}
function update(field, value) {
  try {
    state.requestBody = updateRequestField(state.requestBody, field, value)
    editError.value = ''
    state.inputError = ''
  }
  catch (error) { editError.value = error.message }
}
function confirmReset() {
  const currentGuide = state.guide
  resetDialog = window.$dialog.warning({ title: '恢复示例参数', content: '将替换当前填写的业务参数，是否继续？', positiveText: '恢复示例', negativeText: '保留填写', onPositiveClick: () => {
    if (!mounted || state.guide !== currentGuide)
      return
    emit('reset')
    editError.value = ''
    state.inputError = ''
  } })
}
</script>

<style scoped>
.request-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
h3 {
  margin: 0;
  font-size: 15px;
}
p,
.hint {
  margin: 6px 0 0;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}
.request-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
  margin-top: 20px;
}
.request-field {
  min-width: 0;
}
label {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 5px;
}
label span {
  margin-left: 8px;
  font-size: 11px;
  color: var(--text-tertiary);
}
.request-field code {
  display: block;
  color: var(--text-tertiary);
  font-size: 11px;
  margin-bottom: 8px;
  overflow-wrap: anywhere;
}
.field-hint {
  overflow-wrap: anywhere;
}
.json-editor {
  margin-top: 18px;
}
.json-editor :deep(textarea) {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
}
.request-error {
  color: var(--error-color);
}
.request-notes {
  margin-top: 20px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--text-tertiary);
}
summary {
  cursor: pointer;
}
ul {
  padding-left: 18px;
}
@media (max-width: 640px) {
  .request-grid {
    grid-template-columns: 1fr;
  }
  .request-heading {
    flex-wrap: wrap;
  }
}
</style>
