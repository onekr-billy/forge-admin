<template>
  <section class="settings-section-card">
    <header>
      <h2>高级设置</h2>
      <p>这些设置影响代码导出、运行缓存和版本留存策略。</p>
    </header>
    <n-alert type="warning" :bordered="false" class="settings-info-alert">
      修改代码前缀可能影响后续生成物命名，但不会重命名已发布对象或数据库表。
    </n-alert>
    <n-alert type="info" :bordered="false" class="settings-info-alert">
      缓存策略与版本保留数量会随发布快照保存；当前版本已按版本读取正式快照，但尚未接入独立缓存清理任务。
    </n-alert>
    <n-form label-placement="top">
      <n-grid :cols="2" :x-gap="16" responsive="screen">
        <n-form-item-gi label="代码生成前缀">
          <n-input
            :value="advanced.codePrefix"
            maxlength="20"
            placeholder="例如 crm"
            @update:value="patch({ codePrefix: normalizePrefix($event) })"
          />
        </n-form-item-gi>
        <n-form-item-gi label="运行时缓存策略">
          <n-select :value="advanced.cachePolicy" :options="cacheOptions" @update:value="patch({ cachePolicy: $event })" />
        </n-form-item-gi>
        <n-form-item-gi label="版本保留数量">
          <n-input-number
            :value="advanced.versionRetention"
            :min="5"
            :max="100"
            @update:value="patch({ versionRetention: $event || 20 })"
          />
        </n-form-item-gi>
      </n-grid>
    </n-form>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
const advanced = computed(() => props.modelValue.advanced || {})
const cacheOptions = [
  { label: '按发布版本缓存', value: 'version' },
  { label: '每次读取最新快照', value: 'none' },
  { label: '短时缓存（5 分钟）', value: 'short' },
]

function patch(value) {
  emit('update:modelValue', {
    ...props.modelValue,
    advanced: { ...advanced.value, ...value },
  })
}

function normalizePrefix(value) {
  return String(value || '').toLowerCase().replace(/[^a-z0-9_]/g, '')
}
</script>
