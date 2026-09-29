<script setup>
import { NAlert, NButton, NForm, NFormItem, NInput, NModal, NSelect } from 'naive-ui'
import { computed, reactive, ref, watch } from 'vue'
import { getDataDatasetPage } from '@/api/data/dataset'
import { usePrintCenterStore } from '@/stores/print/printCenterStore'

const props = defineProps({
  show: Boolean,
  source: { type: Object, default: null },
})
const emit = defineEmits(['update:show', 'saved'])
const store = usePrintCenterStore()
const formRef = ref(null)
const datasets = ref([])
const datasetLoading = ref(false)
const error = ref('')
const form = reactive(emptyForm())
const editing = computed(() => Boolean(form.id))
const title = computed(() => editing.value ? '来源设置' : '新增业务来源')
const sourceTypeOptions = [
  { label: '业务服务', value: 'SERVICE' },
  { label: '已发布数据集', value: 'DATASET' },
]
const datasetOptions = computed(() => datasets.value.map(item => ({
  label: `${item.datasetName} · ${item.datasetCode}`,
  value: String(item.id),
})))
const rules = {
  sourceName: { required: true, message: '请输入来源名称', trigger: ['input', 'blur'] },
  sourceCode: {
    required: true,
    pattern: /^[A-Z][\w-]{0,79}$/i,
    message: '编码须以字母开头，只能包含字母、数字、下划线和短横线',
    trigger: ['input', 'blur'],
  },
  objectCode: {
    required: true,
    pattern: /^[A-Z]\w{0,99}$/i,
    message: '对象编码格式不正确',
    trigger: ['input', 'blur'],
  },
}

watch(() => props.show, async (show) => {
  if (!show)
    return
  Object.assign(form, props.source ? sourceForm(props.source) : emptyForm())
  error.value = ''
  if (form.sourceType === 'DATASET' || !editing.value)
    await loadDatasets()
})

watch(() => form.sourceType, (type) => {
  if (type === 'DATASET') {
    form.providerCode = null
    if (!form.mappingJson)
      form.mappingJson = JSON.stringify({ recordIdParam: 'id', maxRows: 1000 }, null, 2)
  }
  else {
    form.datasetId = null
    if (form.mappingJson === '{}')
      form.mappingJson = null
  }
})

function emptyForm() {
  return {
    id: null,
    sourceRevision: null,
    sourceName: '',
    sourceCode: '',
    sourceType: 'SERVICE',
    providerCode: '',
    datasetId: null,
    objectCode: '',
    parameterSchemaJson: '{}',
    mappingJson: null,
  }
}

function sourceForm(source) {
  return {
    ...emptyForm(),
    ...source,
    datasetId: source.datasetId == null ? null : String(source.datasetId),
  }
}

async function loadDatasets() {
  datasetLoading.value = true
  try {
    const { data } = await getDataDatasetPage({
      pageNum: 1,
      pageSize: 100,
      status: 1,
      publishStatus: 1,
    })
    datasets.value = data?.records || []
  }
  catch (reason) {
    error.value = reason.message || '无法读取已发布数据集'
  }
  finally {
    datasetLoading.value = false
  }
}

function validateJson(value, label, required = false) {
  if (!value && !required)
    return true
  try {
    const parsed = JSON.parse(value || '')
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object')
      throw new Error('JSON 不是对象')
    return true
  }
  catch {
    error.value = `${label}必须是 JSON 对象`
    return false
  }
}

async function save() {
  error.value = ''
  try {
    await formRef.value?.validate()
    if (form.sourceType === 'SERVICE' && !String(form.providerCode || '').trim()) {
      error.value = '业务服务来源必须填写服务端 Provider 编码'
      return false
    }
    if (form.sourceType === 'DATASET' && !form.datasetId) {
      error.value = '请选择已发布数据集'
      return false
    }
    if (!validateJson(form.parameterSchemaJson, '参数协议')
      || !validateJson(form.mappingJson, '数据映射', form.sourceType === 'DATASET')) {
      return false
    }
    const saved = await store.save({
      ...form,
      sourceName: form.sourceName.trim(),
      sourceCode: form.sourceCode.trim(),
      providerCode: String(form.providerCode || '').trim() || null,
      objectCode: form.objectCode.trim(),
    })
    emit('saved', saved)
    emit('update:show', false)
  }
  catch (reason) {
    error.value = reason.message || '保存打印来源失败'
  }
  return false
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="title"
    :style="{ width: 'min(680px, 94vw)' }"
    :mask-closable="!store.saving"
    @update:show="value => !store.saving && emit('update:show', value)"
  >
    <NAlert v-if="error" type="error" class="source-form__alert">
      {{ error }}
    </NAlert>
    <NAlert v-if="!editing" type="info" class="source-form__alert">
      新来源保存后默认停用。完成模板和场景绑定，再启用给业务页面调用。
    </NAlert>
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="96">
      <NFormItem label="来源名称" path="sourceName">
        <NInput v-model:value="form.sourceName" maxlength="100" placeholder="例如：采购单打印" />
      </NFormItem>
      <NFormItem label="来源编码" path="sourceCode">
        <NInput
          v-model:value="form.sourceCode"
          :disabled="editing"
          maxlength="80"
          placeholder="例如：purchase_order"
        />
      </NFormItem>
      <NFormItem label="来源类型">
        <NSelect v-model:value="form.sourceType" :disabled="editing" :options="sourceTypeOptions" />
      </NFormItem>
      <NFormItem v-if="form.sourceType === 'SERVICE'" label="Provider 编码">
        <NInput v-model:value="form.providerCode" placeholder="服务端注册的业务 Provider code" />
      </NFormItem>
      <NFormItem v-else label="已发布数据集">
        <NSelect
          v-model:value="form.datasetId"
          filterable
          :loading="datasetLoading"
          :options="datasetOptions"
          placeholder="选择具备访问权限的数据集"
        />
      </NFormItem>
      <NFormItem label="对象编码" path="objectCode">
        <NInput v-model:value="form.objectCode" maxlength="100" placeholder="例如：purchase_order" />
      </NFormItem>
      <NFormItem label="参数协议">
        <NInput
          v-model:value="form.parameterSchemaJson"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 9 }"
          placeholder="声明允许业务页面传入的参数及类型"
        />
      </NFormItem>
      <NFormItem label="数据映射">
        <NInput
          v-model:value="form.mappingJson"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 8 }"
          :placeholder="form.sourceType === 'DATASET' ? '至少配置 recordIdParam' : '业务服务可留空'"
        />
      </NFormItem>
    </NForm>
    <template #footer>
      <div class="source-form__actions">
        <NButton :disabled="store.saving" @click="emit('update:show', false)">
          取消
        </NButton>
        <NButton type="primary" :loading="store.saving" @click="save">
          保存
        </NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.source-form__alert {
  margin-bottom: 12px;
}

.source-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
