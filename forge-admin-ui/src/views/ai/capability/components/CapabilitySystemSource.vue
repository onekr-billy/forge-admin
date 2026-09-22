<template>
  <div class="system-source">
    <n-form-item v-if="!loading && options.length > 1" label="能力类型" path="systemServiceCode">
      <n-select v-model:value="form.systemServiceCode" :options="options" :loading="loading" :disabled="upgrade" placeholder="选择能力类型" @update:value="emit('serviceChange')" />
    </n-form-item>
    <n-empty v-if="!loading && !options.length" description="尚无可用来源，请确认后端版本和发布权限。" />
    <p v-if="service" class="source-description">
      {{ service.description }}
    </p>
    <template v-if="kind === 'REST'">
      <n-form-item label="系统接口" path="systemEndpointId">
        <n-select v-model:value="form.systemEndpointId" :options="endpoints" :disabled="upgrade" filterable placeholder="按接口名称或路径搜索" @update:value="emit('parametersChange')">
          <template #empty>
            <n-empty size="small" description="暂无可开放接口，需开发者为接口声明 OpenRestCapability。" />
          </template>
        </n-select>
      </n-form-item>
      <section v-if="endpoint" class="endpoint-summary">
        <code>{{ endpoint.method }} {{ endpoint.path }}</code>
        <p>{{ endpoint.description }}</p>
        <span>所需业务权限：<code>{{ endpoint.permission }}</code></span>
        <div class="parameter-list">
          <div v-for="(schema, name) in endpoint.inputSchema?.properties || {}" :key="name">
            <code>{{ name }}</code><span>{{ schema.type }}{{ endpoint.inputSchema.required?.includes(name) ? ' · 必填' : ' · 可选' }}</span>
          </div>
          <n-empty v-if="!Object.keys(endpoint.inputSchema?.properties || {}).length" size="small" description="此接口无业务输入参数" />
        </div>
      </section>
      <n-alert type="info">
        只开放代码显式声明的接口。参数契约自动生成，调用时仍校验用户权限；不接受任意 URL 或身份覆盖。
      </n-alert>
    </template>
    <template v-if="kind === 'FORM'">
      <CapabilityApplicationSource v-if="!upgrade && !directObject" @select="selectPage" />
      <n-button v-if="!upgrade && !sourceContext.lockApplication" text size="small" class="source-toggle" @click="directObject = !directObject">
        {{ directObject ? '从应用页面选择' : '高级：直接选择已发布表单对象' }}
      </n-button>
      <n-form-item v-if="upgrade || directObject" label="表单对象" path="systemFormId">
        <n-select v-model:value="form.systemFormId" :options="forms" filterable :disabled="upgrade" placeholder="选择已发布表单对象" @update:value="emit('formChange')" />
      </n-form-item>
      <n-alert v-if="pageError || selectedForm && !selectedForm.available" type="warning" title="当前表单暂不能通过开放接口填报">
        {{ pageError || selectedForm.unavailableReason }}
      </n-alert>
      <template v-if="selectedForm?.available">
        <p class="source-description">
          {{ selectedForm.name }} · 已发布版本 v{{ selectedForm.publishedVersion }}
        </p>
        <n-form-item label="允许外部填写的字段" path="allowedFields">
          <n-select v-model:value="form.allowedFields" multiple filterable :options="fields" placeholder="选择开放字段" @update:value="normalizeFields" />
        </n-form-item>
        <n-form-item label="必须填写的字段">
          <n-select v-model:value="form.requiredFields" multiple filterable :options="fields.filter(item => form.allowedFields.includes(item.value))" @update:value="normalizeFields" />
        </n-form-item>
        <n-alert type="info">
          系统字段不可填写，模型必填项已锁定。这里只创建记录；需要填报并送审，请改选“流程操作 → 提交业务申请”。
        </n-alert>
      </template>
      <p class="source-description form-support">
        表单填报直接复用应用内的新增逻辑，支持自动建表，无需另外创建业务动作或配置接口地址。当前开放字段配置支持单表；主子表和复杂明细暂不支持此入口。
      </p>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
import CapabilityApplicationSource from './CapabilityApplicationSource.vue'

const props = defineProps({ services: { type: Array, default: () => [] }, options: { type: Array, default: () => [] }, loading: Boolean, upgrade: Boolean })
const emit = defineEmits(['serviceChange', 'formChange', 'parametersChange'])
const { form, sourceContext } = useCapabilityRegistrationStore()
const directObject = ref(false)
const pageError = ref('')
const service = computed(() => props.services.find(item => item.serviceCode === form.systemServiceCode))
const kind = computed(() => service.value?.options?.registrationKind)
const endpoint = computed(() => service.value?.options?.endpoints?.find(item => item.id === form.systemEndpointId))
const endpoints = computed(() => (service.value?.options?.endpoints || []).map(item => ({ value: item.id, label: `${item.name} · ${item.method} ${item.path}${item.available ? '' : ` · ${item.unavailableReason}`}`, disabled: !item.available })))
const forms = computed(() => (service.value?.options?.forms || []).map(item => ({ value: item.id, label: `${item.name} · ${item.objectCode}${item.available ? '' : ` · ${item.unavailableReason}`}`, disabled: !item.available })))
const selectedForm = computed(() => service.value?.options?.forms?.find(item => item.id === form.systemFormId))
const fields = computed(() => (selectedForm.value?.fields || []).map(item => ({ value: item.field, label: `${item.label || item.field}${item.required ? ' · 必填' : ''}`, disabled: item.required })))

function selectPage(page) {
  const source = service.value?.options?.forms?.find(item => String(item.objectId) === String(page?.objectId))
  form.systemFormId = source?.id || null
  pageError.value = page && !source ? '此页面绑定的业务对象尚无可开放的表单来源，请先发布业务对象。' : ''
  emit('formChange')
}

function normalizeFields() {
  const required = (selectedForm.value?.fields || []).filter(item => item.required).map(item => item.field)
  form.allowedFields = [...new Set([...form.allowedFields, ...required])]
  form.requiredFields = [...new Set([...form.requiredFields.filter(item => form.allowedFields.includes(item)), ...required])]
}
</script>

<style scoped>
.source-description {
  margin: 0 0 16px;
  color: var(--text-tertiary);
  line-height: 1.6;
}
.source-toggle {
  margin-bottom: 16px;
}
.endpoint-summary {
  border: 1px solid var(--border-light);
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;
  overflow-wrap: anywhere;
}
.endpoint-summary > code {
  font-weight: 600;
}
.endpoint-summary p {
  color: var(--text-secondary);
}
.endpoint-summary > span {
  font-size: 12px;
  color: var(--text-tertiary);
}
.parameter-list {
  margin-top: 16px;
}
.parameter-list > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  border-top: 1px solid var(--border-light);
}
.parameter-list span {
  color: var(--text-tertiary);
}
.form-support {
  margin-top: 14px;
  font-size: 12px;
}
</style>
