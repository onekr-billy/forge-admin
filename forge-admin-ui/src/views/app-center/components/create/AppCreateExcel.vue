<template>
  <section class="excel-create">
    <n-upload
      accept=".xlsx,.xls"
      :max="1"
      :default-upload="false"
      @change="handleFileChange"
      @remove="resetPreview"
    >
      <n-upload-dragger>
        <div class="excel-create__upload-icon">
          <n-icon><CloudUploadOutline /></n-icon>
        </div>
        <strong>拖入 Excel，或点击选择文件</strong>
        <p>读取首个 Sheet 的首行表头和最多 50 行样本；文件不超过 10MB。</p>
      </n-upload-dragger>
    </n-upload>

    <n-spin :show="previewing" description="正在识别表头和字段类型">
      <template v-if="preview">
        <div class="excel-create__summary">
          <div>
            <strong>{{ preview.sheetName || '第一个 Sheet' }}</strong>
            <span>{{ preview.fields?.length || 0 }} 个字段 · 参考 {{ preview.sampledRowCount || 0 }} 行样本</span>
          </div>
          <n-tag :bordered="false" type="success">
            识别完成
          </n-tag>
        </div>

        <n-grid cols="1 m:2" :x-gap="14" responsive="screen">
          <n-form-item-gi label="数据对象名称">
            <n-input v-model:value="objectName" placeholder="默认使用 Sheet 名称" />
          </n-form-item-gi>
          <n-form-item-gi label="数据对象编码">
            <n-input v-model:value="objectCode" placeholder="留空则由系统生成" />
          </n-form-item-gi>
        </n-grid>

        <div class="excel-field-table">
          <div class="excel-field-table__head">
            <span>Excel 表头</span><span>字段编码</span><span>推荐类型</span><span>必填</span>
          </div>
          <div v-for="field in fields" :key="`${field.columnIndex}-${field.headerName}`" class="excel-field-row">
            <n-input v-model:value="field.headerName" size="small" />
            <n-input v-model:value="field.fieldCode" size="small" />
            <n-select
              :value="field.fieldType"
              size="small"
              :options="fieldTypeOptions"
              @update:value="updateFieldType(field, $event)"
            />
            <n-switch v-model:value="field.required" size="small" />
            <div v-if="field.fieldType === 'SELECT'" class="excel-field-options">
              <span>候选选项</span>
              <n-dynamic-tags v-model:value="field.suggestedOptions" />
            </div>
          </div>
        </div>
      </template>
    </n-spin>
  </section>
</template>

<script setup>
import { CloudUploadOutline } from '@vicons/ionicons5'
import { ref } from 'vue'
import { previewBusinessApplicationExcel } from '@/api/business-application'

const selectedFile = ref(null)
const previewing = ref(false)
const preview = ref(null)
const fields = ref([])
const objectName = ref('')
const objectCode = ref('')

const fieldTypeOptions = [
  { label: '文本', value: 'TEXT' },
  { label: '数字', value: 'NUMBER' },
  { label: '日期', value: 'DATE' },
  { label: '日期时间', value: 'DATETIME' },
  { label: '开关', value: 'SWITCH' },
  { label: '下拉选项', value: 'SELECT' },
]

async function handleFileChange({ file }) {
  const rawFile = file?.file
  if (!rawFile)
    return
  selectedFile.value = rawFile
  previewing.value = true
  preview.value = null
  fields.value = []
  try {
    const response = await previewBusinessApplicationExcel(rawFile)
    preview.value = response.data || null
    fields.value = (preview.value?.fields || []).map(field => ({
      ...field,
      required: Boolean(field.required),
      suggestedOptions: [...(field.suggestedOptions || [])],
    }))
    objectName.value = preview.value?.sheetName || rawFile.name.replace(/\.(xlsx|xls)$/i, '')
  }
  catch (error) {
    selectedFile.value = null
    window.$message?.error(error?.message || 'Excel 文件识别失败')
  }
  finally {
    previewing.value = false
  }
}

function updateFieldType(field, value) {
  field.fieldType = value
  const defaults = {
    TEXT: ['varchar', 'input', 128, null],
    NUMBER: ['decimal', 'number', 18, 2],
    DATE: ['date', 'date', null, null],
    DATETIME: ['datetime', 'datetime', null, null],
    SWITCH: ['tinyint', 'switch', 1, 0],
    SELECT: ['varchar', 'select', 64, null],
  }[value]
  if (!defaults) {
    return
  }
  field.dataType = defaults[0]
  field.componentType = defaults[1]
  field.length = defaults[2]
  field.precision = defaults[3]
  if (value !== 'SELECT')
    field.suggestedOptions = []
}

function resetPreview() {
  selectedFile.value = null
  preview.value = null
  fields.value = []
  objectName.value = ''
  objectCode.value = ''
  return true
}

function validate() {
  if (!selectedFile.value || !preview.value)
    throw new Error('请先上传并识别 Excel 文件')
  if (!fields.value.length)
    throw new Error('Excel 至少需要保留一个字段')
  const fieldCodes = fields.value.map(field => String(field.fieldCode || '').trim())
  if (fieldCodes.some(code => !code))
    throw new Error('字段编码不能为空')
  if (new Set(fieldCodes).size !== fieldCodes.length)
    throw new Error('字段编码不能重复')
  return true
}

function getPayload() {
  return {
    file: selectedFile.value,
    objectName: objectName.value.trim(),
    objectCode: objectCode.value.trim(),
    fields: fields.value.map(field => ({ ...field })),
  }
}

defineExpose({ getPayload, resetPreview, validate })
</script>

<style scoped>
.excel-create {
  display: grid;
  gap: 14px;
}

.excel-create__upload-icon {
  color: var(--n-primary-color);
  font-size: 32px;
}

.excel-create :deep(.n-upload-dragger p) {
  margin: 6px 0 0;
  color: var(--n-text-color-3);
  font-size: 12px;
}

.excel-create__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--n-border-color);
  border-radius: 7px;
  background: var(--n-color-embedded);
}

.excel-create__summary > div {
  display: grid;
  gap: 2px;
}

.excel-create__summary span {
  color: var(--n-text-color-3);
  font-size: 12px;
}

.excel-field-table {
  overflow: hidden;
  border: 1px solid var(--n-border-color);
  border-radius: 7px;
}

.excel-field-table__head,
.excel-field-row {
  display: grid;
  grid-template-columns: minmax(120px, 1.15fr) minmax(120px, 1fr) 132px 56px;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
}

.excel-field-table__head {
  color: var(--n-text-color-3);
  background: var(--n-color-embedded);
  font-size: 12px;
}

.excel-field-row {
  border-top: 1px solid var(--n-border-color);
}

.excel-field-options {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: 86px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--n-text-color-3);
  font-size: 12px;
}

@media (max-width: 760px) {
  .excel-field-table__head {
    display: none;
  }

  .excel-field-row {
    grid-template-columns: 1fr;
  }

  .excel-field-options {
    grid-column: auto;
    grid-template-columns: 1fr;
  }
}
</style>
