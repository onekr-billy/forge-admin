<template>
  <div class="process-source">
    <CapabilityApplicationSource v-if="!upgrade" @select="emit('page', $event)" />
    <n-form-item label="业务流程">
      <n-select v-model:value="state.code" :options="options" :loading="state.loading" :disabled="upgrade || state.loading || !state.objectId" filterable placeholder="选择此页面对应的已发布业务流程" />
    </n-form-item>
    <p v-if="state.loading" class="process-hint" role="status">
      正在读取应用已发布的业务流程…
    </p>
    <n-alert v-else-if="state.error" type="error">
      {{ state.error }} <n-button text type="error" @click="emit('retry')">
        重试
      </n-button>
    </n-alert>
    <n-empty v-else-if="state.objectId && !state.options.length" size="small" description="此页面暂无已发布业务流程。请在应用的“业务流程”中关联此表单、配置手动开始节点并发布应用。" />
    <p v-if="selected?.available" class="process-hint">
      {{ selected.name }} · 已发布版本 v{{ selected.version }}
    </p>
    <n-alert type="info" class="process-contract">
      外部系统传入已保存的表单记录 ID，即可发起完整业务流程。审批、条件和业务动作沿用应用配置，无需再绑定旧版“对象主流程”。
      <p>这里只发起业务流程，不创建表单记录，也不代替审批人的同意、驳回或撤回操作。</p>
    </n-alert>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
import CapabilityApplicationSource from './CapabilityApplicationSource.vue'

defineProps({ upgrade: Boolean })
const emit = defineEmits(['page', 'retry'])
const state = useCapabilityRegistrationStore().processSource
const selected = computed(() => state.options.find(item => item.processCode === state.code))
const options = computed(() => state.options.map(item => ({ value: item.processCode, label: `${item.name} · v${item.version}${item.available ? '' : ` · ${item.unavailableReason}`}`, disabled: !item.available })))
</script>

<style scoped>
.process-hint {
  margin: -6px 0 14px;
  color: var(--text-tertiary);
  font-size: 12px;
}
.process-contract {
  margin: 14px 0;
}
.process-contract p {
  margin: 8px 0 0;
}
</style>
