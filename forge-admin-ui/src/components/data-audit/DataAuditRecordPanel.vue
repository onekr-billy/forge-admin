<template>
  <div class="data-audit-record-panel">
    <n-alert v-if="!available" type="info" :bordered="false">
      当前记录尚无可查看的变更历史。
    </n-alert>
    <template v-else>
      <n-alert v-if="!enabled && historyAvailable" type="warning" :bordered="false" class="collection-notice">
        当前对象已停用新的变更采集，下面仍保留停用前的历史记录。
      </n-alert>
      <div class="panel-toolbar">
        <n-select
          v-model:value="selectedFieldCode"
          clearable
          filterable
          size="small"
          :loading="fieldOptionsLoading"
          :options="fieldOptions"
          placeholder="选择变更字段"
          style="width: 200px"
        />
        <n-date-picker
          v-model:value="timeRange"
          type="datetimerange"
          clearable
          size="small"
          style="width: min(320px, 100%)"
        />
        <n-button size="small" type="primary" @click="reload">
          查询
        </n-button>
      </div>
      <n-spin :show="store.loading">
        <n-empty
          v-if="!store.loading && !store.events.length"
          :description="emptyDescription"
        />
        <n-timeline v-else class="audit-timeline">
          <n-timeline-item
            v-for="event in store.events"
            :key="event.id"
            :type="eventTimelineType(event)"
            :time="event.occurredAt"
          >
            <div class="timeline-event-card">
              <div class="event-summary">
                <div class="event-main">
                  <strong>{{ event.actorName || event.actorId || '系统操作' }}</strong>
                  <DictTag :options="eventTypeOptions" :value="event.eventType" size="small" />
                  <DictTag :options="sourceOptions" :value="event.sourceType" size="small" />
                </div>
                <div class="event-meta">
                  <span>第 {{ event.revision }} 次记录</span>
                  <span>{{ eventChangeSummary(event) }}</span>
                </div>
              </div>
              <p v-if="event.changeReason" class="event-reason">
                变更原因：{{ event.changeReason }}
              </p>
              <DataAuditEventDiff
                class="timeline-event-diff"
                :fields="store.fieldsByEvent[String(event.id)] || []"
                :loading="isFieldLoading(event.id)"
                :empty-text="fieldEmptyText(event.id)"
                :event-type="event.eventType"
                :expanded="isExpanded(event.id)"
                @toggle="toggleEvent(event)"
                @reveal="field => handleReveal(event, field)"
              />
            </div>
          </n-timeline-item>
        </n-timeline>
        <div v-if="store.events.length" class="panel-pager">
          <span>共 {{ store.total }} 次变更</span>
          <n-pagination
            v-if="store.total > 10"
            :page="store.pageNum"
            :page-size="eventPageSize"
            :item-count="store.total"
            :page-sizes="[10, 20, 50]"
            show-size-picker
            size="small"
            @update:page="page => load(page)"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </n-spin>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { dataAuditObjectFieldOptions } from '@/api/data-audit'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'
import { useDataAuditStore } from '@/stores/data-audit/dataAuditStore'
import { auditFieldSummary } from './data-audit-display'
import { promptAuditReason } from './data-audit-submit'
import DataAuditEventDiff from './DataAuditEventDiff.vue'

const props = defineProps({
  objectId: { type: [String, Number], default: '' },
  recordId: { type: [String, Number], default: '' },
  enabled: { type: Boolean, default: false },
  historyAvailable: { type: Boolean, default: false },
  accessMode: { type: String, default: 'RECORD' },
  taskId: { type: String, default: '' },
})

const store = useDataAuditStore()
const { dict } = useDict('sys_data_audit_event_type', 'sys_data_audit_source_type')
const eventTypeOptions = computed(() => dict.value.sys_data_audit_event_type || [])
const sourceOptions = computed(() => dict.value.sys_data_audit_source_type || [])
const selectedFieldCode = ref(null)
const fieldOptions = ref([])
const fieldOptionsLoading = ref(false)
const timeRange = ref(null)
const fieldLoadingIds = ref([])
const fieldLoadErrorIds = ref([])
const expandedEventIds = ref([])
const eventPageSize = ref(10)

const available = computed(() => props.enabled || props.historyAvailable)
const emptyDescription = computed(() => {
  if (store.emptyReason === 'error')
    return store.error || '加载失败'
  if (selectedFieldCode.value || timeRange.value)
    return '没有符合筛选条件的变更记录'
  return '已启用审计，但暂无变更记录'
})

async function load(page = 1) {
  if (!available.value || !props.objectId || !props.recordId)
    return
  const filters = {
    fieldCode: selectedFieldCode.value || undefined,
    taskId: props.taskId || undefined,
  }
  if (Array.isArray(timeRange.value) && timeRange.value.length === 2) {
    filters.startTime = formatTime(timeRange.value[0])
    filters.endTime = formatTime(timeRange.value[1])
  }
  expandedEventIds.value = []
  fieldLoadErrorIds.value = []
  store.fieldsByEvent = {}
  await store.loadRecordEvents({
    objectId: props.objectId,
    recordId: String(props.recordId),
    filters,
    page,
    size: eventPageSize.value,
    accessMode: props.accessMode,
  })
  await loadVisibleEventFields()
}

function reload() {
  load(1)
}

function handlePageSizeChange(size) {
  eventPageSize.value = size
  load(1)
}

function formatTime(value) {
  const date = value instanceof Date ? value : new Date(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function isExpanded(eventId) {
  return expandedEventIds.value.includes(String(eventId))
}

function isFieldLoading(eventId) {
  return fieldLoadingIds.value.includes(String(eventId))
}

function eventTimelineType(event) {
  if (event.eventType === 'DELETE')
    return 'error'
  if (event.eventType === 'CREATE')
    return 'success'
  return 'info'
}

function eventChangeSummary(event) {
  const fields = store.fieldsByEvent[String(event.id)] || []
  return auditFieldSummary(fields, event.visibleFieldCount ?? event.changedFieldCount)
}

function fieldEmptyText(eventId) {
  if (fieldLoadErrorIds.value.includes(String(eventId)))
    return '字段变化加载失败，请重新查询'
  return '本次变更没有可见字段'
}

function toggleEvent(event) {
  const eventId = String(event.id)
  if (isExpanded(eventId)) {
    expandedEventIds.value = expandedEventIds.value.filter(id => id !== eventId)
    return
  }
  expandedEventIds.value = [...expandedEventIds.value, eventId]
}

async function loadVisibleEventFields() {
  const eventIds = store.events.map(event => String(event.id)).filter(Boolean)
  fieldLoadingIds.value = eventIds
  fieldLoadErrorIds.value = []
  try {
    const results = await Promise.allSettled(eventIds.map(eventId => store.loadEventFields(eventId, {
      accessMode: props.accessMode,
      fieldCode: selectedFieldCode.value || undefined,
    })))
    fieldLoadErrorIds.value = results
      .map((result, index) => result.status === 'rejected' ? eventIds[index] : '')
      .filter(Boolean)
  }
  finally {
    fieldLoadingIds.value = []
  }
}

async function loadFieldOptions() {
  selectedFieldCode.value = null
  fieldOptions.value = []
  if (!props.objectId)
    return
  fieldOptionsLoading.value = true
  try {
    const response = await dataAuditObjectFieldOptions(props.objectId)
    fieldOptions.value = (response.code === 200 ? (response.data || []) : []).map(field => ({
      label: field.fieldLabel || '未命名字段',
      value: String(field.fieldCode),
    }))
  }
  catch (error) {
    window.$message?.error?.(error?.message || '加载页面字段失败')
  }
  finally {
    fieldOptionsLoading.value = false
  }
}

async function handleReveal(event, field) {
  const reason = await promptAuditReason('请填写查看原值原因')
  if (reason === false)
    return
  try {
    const data = await store.revealField(event.id, field.id, reason, props.accessMode)
    const list = store.fieldsByEvent[String(event.id)] || []
    store.fieldsByEvent = {
      ...store.fieldsByEvent,
      [String(event.id)]: list.map((item) => {
        if (String(item.id) !== String(field.id))
          return item
        return { ...item, before: data.before, after: data.after, masked: false, canReveal: false }
      }),
    }
  }
  catch (error) {
    window.$message?.error(error?.message || '查看原值失败')
  }
}

watch(() => props.objectId, loadFieldOptions, { immediate: true })

watch(
  () => [props.objectId, props.recordId, props.enabled, props.historyAvailable],
  () => {
    store.clear()
    if (available.value)
      load(1)
  },
  { immediate: true },
)
</script>

<style scoped>
.data-audit-record-panel {
  min-height: 220px;
  padding-top: 8px;
}

.collection-notice {
  margin-bottom: 10px;
}

.panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.audit-timeline {
  padding: 8px 4px 0;
}

.audit-timeline :deep(.n-timeline-item-content__time) {
  font-variant-numeric: tabular-nums;
}

.timeline-event-card {
  padding: 10px 12px;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 6px;
  background: var(--bg-primary, #fff);
}

.event-summary,
.event-main,
.event-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.event-summary {
  justify-content: space-between;
  gap: 8px 16px;
  font-size: 13px;
}

.event-main,
.event-meta {
  gap: 8px 10px;
}

.event-meta span {
  color: var(--text-tertiary, #64748b);
}

.event-reason {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px dashed var(--border-light, #e5e7eb);
  color: var(--text-secondary, #475569);
  font-size: 12px;
}

.timeline-event-diff {
  margin-top: 10px;
}

.panel-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
}
</style>
