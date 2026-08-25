<template>
  <section class="settings-section-card">
    <header>
      <h2>全球化</h2>
      <p>定义门户默认语言、时区与日期显示方式。</p>
    </header>
    <n-form label-placement="top">
      <n-form-item label="启用多语言">
        <n-switch :value="globalization.enabled" @update:value="patch({ enabled: $event })" />
      </n-form-item>
      <n-grid :cols="2" :x-gap="16" responsive="screen">
        <n-form-item-gi label="默认语言">
          <n-select
            :value="globalization.defaultLanguage"
            :options="languageOptions"
            :disabled="!globalization.enabled"
            @update:value="patch({ defaultLanguage: $event })"
          />
        </n-form-item-gi>
        <n-form-item-gi label="默认时区">
          <n-select :value="globalization.timezone" :options="timezoneOptions" filterable @update:value="patch({ timezone: $event })" />
        </n-form-item-gi>
        <n-form-item-gi label="日期格式">
          <n-select :value="globalization.dateFormat" :options="dateFormatOptions" @update:value="patch({ dateFormat: $event })" />
        </n-form-item-gi>
      </n-grid>
    </n-form>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
const globalization = computed(() => props.modelValue.globalization || {})
const languageOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
]
const timezoneOptions = [
  { label: 'Asia/Shanghai (UTC+8)', value: 'Asia/Shanghai' },
  { label: 'Asia/Tokyo (UTC+9)', value: 'Asia/Tokyo' },
  { label: 'Europe/London', value: 'Europe/London' },
  { label: 'America/New_York', value: 'America/New_York' },
  { label: 'UTC', value: 'UTC' },
]
const dateFormatOptions = [
  { label: '2026-08-17', value: 'YYYY-MM-DD' },
  { label: '2026/08/17', value: 'YYYY/MM/DD' },
  { label: '17/08/2026', value: 'DD/MM/YYYY' },
]

function patch(value) {
  emit('update:modelValue', {
    ...props.modelValue,
    globalization: { ...globalization.value, ...value },
  })
}
</script>
