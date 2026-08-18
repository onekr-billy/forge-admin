<template>
  <view v-if="control.visible" class="lowcode-field">
    <view class="lowcode-field__label">
      <text>{{ field.label }}</text>
      <text v-if="control.required" class="lowcode-field__required">*</text>
    </view>
    <view class="lowcode-field__control">
      <view v-if="readonly" class="lowcode-field__readonly">{{ displayValue }}</view>
      <view v-else-if="field.type === 'barcodeScanner'" class="lowcode-field__barcode">
        <AiField
          :model-value="modelValue"
          :placeholder="field.props?.placeholder || '请输入或扫描条码'"
          :maxlength="field.props?.maxlength || 2048"
          :clearable="field.props?.allowManualInput !== false"
          @update:model-value="updateValue"
          @blur="emit('blur')"
          @confirm="completeManualScan"
        />
        <AiButton
          class="lowcode-field__scan"
          size="sm"
          :loading="scanning"
          :disabled="scanning"
          @click="scan"
        >
          {{ scanning ? '扫描中' : (field.props?.buttonText || '扫码') }}
        </AiButton>
      </view>
      <textarea
        v-else-if="field.type === 'textarea'"
        class="lowcode-field__textarea"
        :value="modelValue"
        :maxlength="field.props?.maxlength || 2048"
        :disabled="disabled"
        :placeholder="field.props?.placeholder || `请输入${field.label}`"
        @input="updateValue($event.detail.value)"
        @blur="emit('blur')"
      />
      <AiField
        v-else-if="isTextField"
        :model-value="modelValue"
        type="text"
        :placeholder="field.props?.placeholder || `请输入${field.label}`"
        :maxlength="field.props?.maxlength || 2048"
        :clearable="field.props?.clearable !== false"
        @update:model-value="updateValue"
        @blur="emit('blur')"
      />
      <input
        v-else-if="isNumberField"
        class="lowcode-field__input"
        type="number"
        :value="modelValue"
        :min="field.min ?? field.props?.min"
        :max="field.max ?? field.props?.max"
        :step="field.step ?? field.props?.step ?? (field.type === 'integer' ? 1 : 0.01)"
        :disabled="disabled"
        :placeholder="field.props?.placeholder || `请输入${field.label}`"
        @input="updateValue($event.detail.value)"
        @blur="emit('blur')"
      />
      <AiSelect
        v-else-if="field.type === 'select' || field.type === 'dictSelect'"
        :model-value="modelValue"
        :options="options"
        :placeholder="field.props?.placeholder || `请选择${field.label}`"
        :title="field.label"
        @update:model-value="updateValue"
        @change="emit('change', $event)"
      />
      <PillSelect
        v-else-if="field.type === 'pillSelect'"
        :model-value="modelValue"
        :options="options"
        :clearable="field.props?.clearable !== false"
        :disabled="disabled"
        @update:model-value="updateValue"
        @change="emit('change', $event)"
      />
      <switch
        v-else-if="field.type === 'switch'"
        :checked="Boolean(modelValue)"
        :disabled="disabled"
        @change="updateValue($event.detail.value)"
      />
      <picker
        v-else-if="field.type === 'date' || field.type === 'datetime'"
        mode="date"
        :value="pickerValue"
        @change="handlePickerChange"
      >
        <view class="lowcode-field__picker">{{ displayValue || `请选择${field.label}` }}</view>
      </picker>
      <AiField
        v-else
        :model-value="modelValue"
        :placeholder="field.props?.placeholder || `请输入${field.label}`"
        @update:model-value="updateValue"
        @blur="emit('blur')"
      />
    </view>
    <text v-if="error" class="lowcode-field__error">{{ error }}</text>
    <text v-if="scanMessage || hint" class="lowcode-field__hint" :class="{ 'is-error': scanMessage }">
      {{ scanMessage || hint }}
    </text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import AiButton from '@/components/AiButton.vue'
import AiField from '@/components/AiField.vue'
import AiSelect from '@/components/AiSelect.vue'
import PillSelect from './PillSelect.vue'
import { scanBarcode } from '@/utils/barcode-scanner'

const props = defineProps({
  field: { type: Object, default: () => ({}) },
  modelValue: { type: [String, Number, Boolean, Array], default: '' },
  options: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  error: { type: String, default: '' },
  hint: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'blur', 'change', 'scan'])
const scanning = ref(false)
const scanMessage = ref('')
const isTextField = computed(() => ['input', 'textarea', 'text', 'password'].includes(String(props.field.type)))
const isNumberField = computed(() => ['number', 'input-number', 'integer', 'money'].includes(String(props.field.type)))
const control = computed(() => props.field.__runtimeControl || { visible: true, required: props.field.required === true })
const readonly = computed(() => props.readonly || props.disabled || props.field.readonly === true || control.value.readonly)
const displayValue = computed(() => {
  const value = props.modelValue
  if (props.field.type === 'dictSelect' || props.field.type === 'select' || props.field.type === 'pillSelect') {
    return props.options.find(item => String(item.value) === String(value))?.label || value || '-'
  }
  if (value === undefined || value === null || value === '') return '-'
  return String(value)
})
const pickerValue = computed(() => String(props.modelValue || '').slice(0, 10) || new Date().toISOString().slice(0, 10))

function updateValue(value) {
  emit('update:modelValue', value)
}

async function scan() {
  if (scanning.value) return
  scanning.value = true
  scanMessage.value = ''
  try {
    const result = await scanBarcode({ timeoutMs: props.field.props?.timeoutMs })
    updateValue(result.value)
    emit('scan', result)
    emit('blur')
  }
  catch (error) {
    scanMessage.value = resolveScanMessage(error)
  }
  finally {
    scanning.value = false
  }
}

function completeManualScan() {
  const value = String(props.modelValue ?? '').trim()
  if (!value)
    return
  scanMessage.value = ''
  emit('scan', { value, type: 'MANUAL', platform: 'MANUAL' })
}

function resolveScanMessage(error) {
  switch (error?.code) {
    case 'SCAN_CANCELLED':
      return '已取消扫码'
    case 'SCAN_PERMISSION_DENIED':
      return '请允许浏览器使用摄像头，或手工输入条码后按确认键'
    case 'SCAN_UNSUPPORTED':
      return '当前环境不支持摄像头扫码，请手工输入条码后按确认键'
    case 'SCAN_TIMEOUT':
      return '扫码超时，请重试或手工输入条码'
    default:
      return '扫码失败，请重试或手工输入条码'
  }
}

function handlePickerChange(event) {
  updateValue(event.detail.value)
  emit('change', event.detail.value)
  emit('blur')
}
</script>

<style lang="scss" scoped>
.lowcode-field { margin-bottom: 24rpx; }
.lowcode-field__label { display: flex; margin-bottom: 10rpx; color: #475569; font-size: 25rpx; font-weight: 700; }
.lowcode-field__required { margin-left: 6rpx; color: #ef4444; }
.lowcode-field__control { min-height: 76rpx; }
.lowcode-field__readonly { min-height: 76rpx; padding: 20rpx; border: 1rpx solid #edf0f3; border-radius: 12rpx; color: #64748b; background: #f8fafc; box-sizing: border-box; line-height: 1.45; word-break: break-all; }
.lowcode-field__barcode { display: flex; align-items: center; gap: 12rpx; }
.lowcode-field__barcode :deep(.ai-field) { flex: 1; min-width: 0; }
.lowcode-field__scan { flex: 0 0 auto; }
.lowcode-field__input { width: 100%; height: 76rpx; padding: 0 20rpx; border: 1rpx solid var(--border-color); border-radius: 12rpx; color: #334155; font-size: 27rpx; background: #fff; box-sizing: border-box; }
.lowcode-field__textarea { width: 100%; min-height: 150rpx; padding: 20rpx; border: 1rpx solid var(--border-color); border-radius: 12rpx; color: #334155; font-size: 27rpx; line-height: 1.5; background: #fff; box-sizing: border-box; }
.lowcode-field__picker { min-height: 76rpx; padding: 20rpx; border: 1rpx solid var(--border-color); border-radius: 12rpx; color: #334155; background: #fff; box-sizing: border-box; }
.lowcode-field__error { display: block; margin-top: 8rpx; color: #ef4444; font-size: 22rpx; }
.lowcode-field__hint { display: block; margin-top: 8rpx; color: #94a3b8; font-size: 22rpx; }
.lowcode-field__hint.is-error { color: #dc2626; }
</style>
