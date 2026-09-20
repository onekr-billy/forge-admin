<template>
  <div class="data-change-audit-page">
    <header class="audit-page-header">
      <div>
        <h1>数据变更记录</h1>
        <p>先选择应用和页面，再从页面字段中筛选数据变化，无需记忆对象或字段编码。</p>
      </div>
      <n-tag size="small" :bordered="false">
        只读记录
      </n-tag>
    </header>
    <section class="audit-list-panel">
      <AiCrudPage
        :api-config="{
          list: 'get@/ai/data-audit/page',
          detail: 'get@/ai/data-audit/:id',
        }"
        :search-schema="searchSchema"
        :columns="tableColumns"
        row-key="id"
        :hide-add="true"
        :hide-selection="true"
        :hide-batch-delete="true"
        :show-export="false"
        :show-render-mode-switch="false"
        table-size="small"
        :search-grid-cols="4"
        :search-max-visible-fields="4"
        :before-search="handleBeforeSearch"
        :before-render-reset="handleSearchReset"
      />
    </section>

    <n-modal
      v-model:show="detailVisible"
      title="数据变更详情"
      preset="card"
      class="data-audit-detail-modal"
      :style="{ width: 'min(1080px, 94vw)' }"
    >
      <div v-if="currentEvent" class="audit-detail">
        <div class="audit-summary">
          <div class="summary-item">
            <span>对象</span>
            <strong>{{ currentEvent.objectName || currentEvent.objectCode }}</strong>
            <small>{{ currentEvent.objectCode }}</small>
          </div>
          <div class="summary-item">
            <span>记录</span>
            <strong>{{ currentEvent.recordLabel || currentEvent.recordId }}</strong>
            <small>{{ currentEvent.recordId }}</small>
          </div>
          <div class="summary-item">
            <span>操作者</span>
            <strong>{{ currentEvent.actorName || '-' }}</strong>
          </div>
          <div class="summary-item">
            <span>时间</span>
            <strong>{{ currentEvent.occurredAt }}</strong>
          </div>
        </div>
        <DataAuditEventDiff
          :fields="detailFields"
          :loading="detailLoading"
          :event-type="currentEvent.eventType"
          :expanded="detailExpanded"
          @toggle="detailExpanded = !detailExpanded"
          @reveal="handleReveal"
        />
      </div>
    </n-modal>
  </div>
</template>

<script setup>
import { computed, h, onMounted, ref } from 'vue'
import { dataAuditEventDetail, dataAuditFieldPage, dataAuditFilterOptions, dataAuditReveal } from '@/api/data-audit'
import { AiCrudPage } from '@/components/ai-form'
import { promptAuditReason } from '@/components/data-audit/data-audit-submit'
import DataAuditEventDiff from '@/components/data-audit/DataAuditEventDiff.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'
import { formatDateTime } from '@/utils'
import {
  buildDataAuditApplicationOptions,
  buildDataAuditFieldOptions,
  buildDataAuditPageOptions,
  buildDataAuditSearchParams,
  normalizeDataAuditFilterOptions,
} from './data-audit-search'

defineOptions({ name: 'DataChangeAudit' })

const { dict } = useDict(
  'sys_data_audit_event_type',
  'sys_data_audit_source_type',
  'sys_data_audit_actor_type',
)

const eventTypeOptions = computed(() => dict.value.sys_data_audit_event_type || [])
const sourceOptions = computed(() => dict.value.sys_data_audit_source_type || [])
const actorOptions = computed(() => dict.value.sys_data_audit_actor_type || [])

const detailVisible = ref(false)
const currentEvent = ref(null)
const detailFields = ref([])
const detailLoading = ref(false)
const detailExpanded = ref(false)
const filterApplications = ref([])
const selectedApplicationId = ref('')
const selectedPageId = ref('')
const applicationOptions = computed(() => buildDataAuditApplicationOptions(filterApplications.value))
const pageOptions = computed(() => buildDataAuditPageOptions(
  filterApplications.value,
  selectedApplicationId.value,
))
const fieldOptions = computed(() => buildDataAuditFieldOptions(
  filterApplications.value,
  selectedApplicationId.value,
  selectedPageId.value,
))

function handleBeforeSearch(params) {
  if (params.applicationId && !params.pageId) {
    window.$message?.warning?.('请选择要查询的应用页面')
    return false
  }
  return buildDataAuditSearchParams(params, filterApplications.value, formatDateTime)
}

function handleSearchReset() {
  selectedApplicationId.value = ''
  selectedPageId.value = ''
}

function handleApplicationChange({ value, formData }) {
  selectedApplicationId.value = String(value || '')
  const pages = buildDataAuditPageOptions(filterApplications.value, value)
  const pageId = pages.length === 1 ? pages[0].value : null
  selectedPageId.value = String(pageId || '')
  formData.pageId = pageId
  formData.fieldCode = null
}

function handlePageChange({ value, formData }) {
  selectedPageId.value = String(value || '')
  formData.fieldCode = null
}

const searchSchema = computed(() => [
  {
    field: 'applicationId',
    label: '应用',
    type: 'select',
    props: { clearable: true, filterable: true, options: applicationOptions.value, placeholder: '选择应用' },
    onChange: handleApplicationChange,
  },
  {
    field: 'pageId',
    label: '页面',
    type: 'select',
    disabled: !selectedApplicationId.value,
    props: { clearable: true, filterable: true, options: pageOptions.value, placeholder: '先选择应用' },
    onChange: handlePageChange,
  },
  {
    field: 'fieldCode',
    label: '变更字段',
    type: 'select',
    disabled: !selectedPageId.value,
    props: { clearable: true, filterable: true, options: fieldOptions.value, placeholder: '选择页面字段' },
  },
  { field: 'recordKeyword', label: '业务记录', type: 'input', props: { clearable: true, placeholder: '记录名称或业务编号' } },
  {
    field: 'actorId',
    label: '操作人',
    type: 'userSelect',
    props: { clearable: true, placeholder: '选择操作人' },
  },
  {
    field: 'eventType',
    label: '事件类型',
    type: 'select',
    props: { clearable: true, options: eventTypeOptions.value, placeholder: '请选择' },
  },
  {
    field: 'timeRange',
    label: '时间',
    type: 'daterange',
    clearable: true,
    format: 'yyyy-MM-dd HH:mm:ss',
    valueFormat: 'yyyy-MM-dd HH:mm:ss',
    props: { type: 'datetimerange' },
  },
  {
    field: 'sourceType',
    label: '变更来源',
    type: 'select',
    props: { clearable: true, options: sourceOptions.value, placeholder: '选择来源' },
  },
])

const tableColumns = computed(() => [
  { prop: 'objectName', label: '业务对象', minWidth: 140, ellipsis: { tooltip: true }, render: row => row.objectName || row.objectCode || '-' },
  { prop: 'recordId', label: '记录', minWidth: 160, ellipsis: { tooltip: true }, render: row => row.recordLabel || row.recordId },
  { prop: 'actorName', label: '操作者', width: 110, ellipsis: { tooltip: true } },
  {
    prop: 'eventType',
    label: '类型',
    width: 80,
    render: row => h(DictTag, { options: eventTypeOptions.value, value: row.eventType, size: 'small' }),
  },
  {
    prop: 'sourceType',
    label: '来源',
    width: 100,
    render: row => h(DictTag, { options: sourceOptions.value, value: row.sourceType, size: 'small' }),
  },
  {
    prop: 'actorType',
    label: '主体',
    width: 80,
    render: row => h(DictTag, { options: actorOptions.value, value: row.actorType, size: 'small' }),
  },
  { prop: 'revision', label: '修订号', width: 80 },
  { prop: 'visibleFieldCount', label: '字段数', width: 80, render: row => row.visibleFieldCount ?? row.changedFieldCount ?? 0 },
  { prop: 'changeReason', label: '原因', minWidth: 160, ellipsis: { tooltip: true }, render: row => row.changeReason || '-' },
  { prop: 'occurredAt', label: '时间', width: 170 },
  {
    prop: 'action',
    label: '操作',
    width: 86,
    fixed: 'right',
    actions: [{ label: '详情', key: 'detail', type: 'primary', onClick: handleViewDetail }],
  },
])

async function handleViewDetail(row) {
  detailLoading.value = true
  detailExpanded.value = false
  detailFields.value = []
  currentEvent.value = row
  detailVisible.value = true
  try {
    const eventRes = await dataAuditEventDetail(row.id, { accessMode: 'AUDIT' })
    currentEvent.value = eventRes.code === 200 ? eventRes.data : row
    const fieldRes = await dataAuditFieldPage(row.id, { accessMode: 'AUDIT', pageNum: 1, pageSize: 100 })
    detailFields.value = fieldRes.code === 200 ? (fieldRes.data?.records || []) : []
  }
  catch (error) {
    window.$message?.error(error?.message || '加载详情失败')
  }
  finally {
    detailLoading.value = false
  }
}

async function handleReveal(field) {
  const reason = await promptAuditReason('请填写查看原值原因')
  if (reason === false)
    return
  try {
    const res = await dataAuditReveal(currentEvent.value.id, field.id, { reason, accessMode: 'AUDIT' })
    if (res.code === 200) {
      detailFields.value = detailFields.value.map((item) => {
        if (String(item.id) !== String(field.id))
          return item
        return { ...item, before: res.data.before, after: res.data.after, masked: false, canReveal: false }
      })
    }
  }
  catch (error) {
    window.$message?.error(error?.message || '查看原值失败')
  }
}

async function loadFilterOptions() {
  try {
    const res = await dataAuditFilterOptions()
    filterApplications.value = normalizeDataAuditFilterOptions(res.code === 200 ? res.data : {})
  }
  catch (error) {
    filterApplications.value = []
    window.$message?.error?.(error?.message || '加载应用页面筛选项失败')
  }
}

onMounted(loadFilterOptions)
</script>

<style scoped>
.data-change-audit-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
}

.audit-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 6px;
  background: var(--bg-primary, #fff);
}

.audit-page-header h1,
.audit-page-header p {
  margin: 0;
}

.audit-page-header h1 {
  font-size: 16px;
  font-weight: 600;
}

.audit-page-header p {
  margin-top: 3px;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
}

.audit-list-panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 6px;
  background: var(--bg-primary, #fff);
}

.audit-list-panel :deep(.ai-crud-page) {
  height: 100%;
}

.audit-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 12px;
  margin-bottom: 12px;
}

.audit-summary span {
  display: block;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
}

.audit-summary strong {
  display: block;
  font-size: 13px;
}

.audit-summary small {
  display: block;
  margin-top: 2px;
  color: var(--text-tertiary, #64748b);
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

@media (max-width: 768px) {
  .audit-summary {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
