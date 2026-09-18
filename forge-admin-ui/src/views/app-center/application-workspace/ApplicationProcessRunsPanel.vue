<script setup>
import { storeToRefs } from 'pinia'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { businessProcessRunDetail, cancelBusinessProcessRun, retryBusinessProcessRun } from '@/api/business-process'
import AiSearch from '@/components/ai-form/AiSearch.vue'
import AiTable from '@/components/ai-form/AiTable.vue'
import AiModal from '@/components/ai-modal/index.vue'
import SystemTableCell from '@/components/common/SystemTableCell.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { useProcessListStore } from '@/stores/business-process/processListStore'

const store = useProcessListStore()
const { applicationId, objectOptions, runFilters, runRecords, runTotal, runPage, runPageSize, runLoading, runError, processOptions, optionsLoading } = storeToRefs(store)
const { dict } = useDict('ai_business_process_run_status')
const searchRef = ref(null)
const detailModalRef = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref('')
const actionId = ref('')
let detailRequest = 0
let searchTimer
const activeStatuses = new Set(['PENDING', 'RUNNING', 'WAITING'])

const searchSchema = computed(() => [
  {
    field: 'processId',
    label: '流程',
    type: 'select',
    props: {
      placeholder: '搜索流程名称或编码',
      clearable: true,
      filterable: true,
      remote: true,
      options: processOptions.value,
      loading: optionsLoading.value,
      onSearch: searchProcessOptions,
    },
  },
  {
    field: 'subjectObjectCode',
    label: '业务对象',
    type: 'select',
    props: { placeholder: '全部业务对象', clearable: true, options: objectOptions.value.map(item => ({ label: item.label, value: item.code })) },
  },
  {
    field: 'status',
    label: '运行状态',
    type: 'select',
    props: { placeholder: '全部状态', clearable: true, options: dict.value?.ai_business_process_run_status || [] },
  },
])
const columns = [
  { prop: 'processName', label: '流程', minWidth: 200, slot: 'identity' },
  { prop: 'subjectRecordId', label: '业务数据', minWidth: 200, slot: 'subject' },
  { prop: 'status', label: '运行状态', width: 110, slot: 'status' },
  { prop: 'currentNodeName', label: '当前节点', minWidth: 150, slot: 'node' },
  { prop: 'startTime', label: '开始时间', width: 170 },
  { prop: 'endTime', label: '结束时间', width: 170 },
  { prop: 'action', label: '操作', width: 150, fixed: 'right', slot: 'actions' },
]
const timelineColumns = [
  { prop: 'nodeName', label: '节点', minWidth: 170, slot: 'node' },
  { prop: 'attemptNo', label: '尝试次数', width: 90 },
  { prop: 'status', label: '状态', width: 110, slot: 'status' },
  { prop: 'outputSummary', label: '执行摘要', minWidth: 220, slot: 'summary' },
  { prop: 'startTime', label: '开始时间', width: 170 },
]
const pagination = computed(() => ({
  page: runPage.value,
  pageSize: runPageSize.value,
  itemCount: runTotal.value,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  showQuickJumper: true,
  prefix: ({ itemCount }) => `共 ${itemCount} 项`,
}))
const hasFilters = computed(() => Object.values(runFilters.value).some(Boolean))
const emptyTitle = computed(() => runError.value ? '运行记录加载失败' : hasFilters.value ? '没有匹配的运行记录' : '暂无运行记录')
const emptyDescription = computed(() => runError.value || (hasFilters.value ? '请调整筛选条件或点击重置。' : '流程触发后，可在这里查看进度与执行结果。'))

watch(applicationId, () => {
  detailRequest += 1
  detailModalRef.value?.close()
  store.loadRuns()
  store.searchProcesses()
}, { immediate: true })

watch(() => store.activeSection, (section) => {
  if (section === 'runs') {
    store.loadRuns()
    store.searchProcesses()
  }
})

onBeforeUnmount(() => {
  detailRequest += 1
  clearTimeout(searchTimer)
})

function searchProcessOptions(keyword) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => store.searchProcesses(keyword), 250)
}

function searchRuns(values = {}) {
  store.setRunFilters(values)
  runPage.value = 1
  return store.loadRuns()
}

function changePage(value) {
  runPage.value = value
  return store.loadRuns()
}

function changePageSize(value) {
  runPageSize.value = value
  runPage.value = 1
  return store.loadRuns()
}

function subjectName(code) {
  return objectOptions.value.find(item => item.code === code)?.label || code || '未识别对象'
}

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function openDetail(row) {
  detailId.value = String(row.id)
  detail.value = null
  detailModalRef.value?.open({
    title: '运行详情',
    width: '960px',
    modalStyle: { maxWidth: 'calc(100vw - 32px)' },
    onClose: () => { detailRequest += 1 },
  })
  loadDetail()
}

async function loadDetail() {
  const requestId = ++detailRequest
  detailLoading.value = true
  detailError.value = ''
  try {
    const response = await businessProcessRunDetail(detailId.value)
    if (requestId === detailRequest) {
      detail.value = response.data
    }
  }
  catch (error) {
    if (requestId === detailRequest) {
      detailError.value = errorMessage(error, '运行详情加载失败')
    }
  }
  finally {
    if (requestId === detailRequest) {
      detailLoading.value = false
    }
  }
}

function confirmRunAction(row, action) {
  if (actionId.value) {
    return
  }
  const retry = action === 'retry'
  const sourceApplicationId = applicationId.value
  const dialog = window.$dialog?.warning({
    title: retry ? '重试运行' : '取消运行',
    content: retry ? `确认重试“${row.processName || row.processCode}”的失败运行吗？` : `确认取消“${row.processName || row.processCode}”的本次运行吗？取消不会撤销已经执行的业务操作。`,
    positiveText: retry ? '确认重试' : '确认取消运行',
    negativeText: '返回',
    onPositiveClick: async () => {
      if (actionId.value || sourceApplicationId !== applicationId.value) {
        return false
      }
      const id = String(row.id)
      actionId.value = `${action}:${id}`
      if (dialog) {
        dialog.loading = true
      }
      try {
        await (retry ? retryBusinessProcessRun(id) : cancelBusinessProcessRun(id))
        window.$message?.success?.(retry ? '已提交重试' : '运行已取消')
        await store.loadRuns()
      }
      catch (error) {
        window.$message?.error?.(errorMessage(error, retry ? '重试运行失败' : '取消运行失败'))
        return false
      }
      finally {
        actionId.value = ''
        if (dialog) {
          dialog.loading = false
        }
      }
    },
  })
}
</script>

<template>
  <div class="process-runs-panel" data-process-runs>
    <AiSearch
      ref="searchRef"
      :key="applicationId"
      :schema="searchSchema"
      :model-value="runFilters"
      :grid-cols="4"
      :label-width="80"
      :enable-collapse="false"
      :y-gap="8"
      @search="searchRuns"
      @reset="searchRuns()"
      @keydown.enter.prevent="searchRef?.handleSearch()"
    />
    <AiTable
      :columns="columns"
      :data-source="runRecords"
      :row-key="row => String(row.id)"
      :loading="runLoading"
      :pagination="pagination"
      :scroll-x="1150"
      :empty-title="emptyTitle"
      :empty-description="emptyDescription"
      :show-render-mode-switch="false"
      :show-search-toggle="false"
      :show-fullscreen="false"
      hide-selection
      @refresh="store.loadRuns"
      @page-change="changePage"
      @page-size-change="changePageSize"
    >
      <template #toolbar-left>
        <span class="run-hint">运行记录保留历史流程版本与执行结果</span>
      </template>
      <template #identity="{ row }">
        <SystemTableCell :title="row.processName || row.processCode" :subtitle="row.processCode" interactive @activate="openDetail(row)" />
      </template>
      <template #subject="{ row }">
        <div class="run-entity">
          <span>{{ subjectName(row.subjectObjectCode) }}</span><span class="run-hint">记录 {{ row.subjectRecordId || '—' }}</span>
        </div>
      </template>
      <template #status="{ row }">
        <DictTag dict-type="ai_business_process_run_status" :value="row.status" :bordered="false" />
      </template>
      <template #node="{ row }">
        <span>{{ row.currentNodeName || row.currentNodeId || '—' }}</span>
      </template>
      <template #actions="{ row }">
        <div class="run-actions">
          <n-button text type="primary" size="small" :data-run-detail="String(row.id)" @click="openDetail(row)">
            详情
          </n-button>
          <template v-if="row.status === 'FAILED' || activeStatuses.has(row.status)">
            <n-divider vertical />
            <n-button v-if="row.status === 'FAILED'" text type="warning" size="small" :disabled="Boolean(actionId)" :loading="actionId === `retry:${row.id}`" @click="confirmRunAction(row, 'retry')">
              重试
            </n-button>
            <n-button v-else text type="error" size="small" :disabled="Boolean(actionId)" :loading="actionId === `cancel:${row.id}`" @click="confirmRunAction(row, 'cancel')">
              取消运行
            </n-button>
          </template>
        </div>
      </template>
    </AiTable>
    <AiModal ref="detailModalRef">
      <div class="run-detail-body">
        <n-spin :show="detailLoading">
          <n-result v-if="detailError" status="error" title="运行详情加载失败" :description="detailError">
            <template #footer>
              <n-button @click="loadDetail">
                重新加载
              </n-button>
            </template>
          </n-result>
          <template v-else-if="detail">
            <n-descriptions label-placement="top" :column="2" bordered size="small">
              <n-descriptions-item label="流程">
                {{ detail.processName || detail.processCode }}
              </n-descriptions-item>
              <n-descriptions-item label="运行状态">
                <DictTag dict-type="ai_business_process_run_status" :value="detail.status" :bordered="false" />
              </n-descriptions-item>
              <n-descriptions-item label="运行 ID">
                {{ detail.id }}
              </n-descriptions-item>
              <n-descriptions-item label="业务数据">
                {{ subjectName(detail.subjectObjectCode) }} / {{ detail.subjectRecordId || '—' }}
              </n-descriptions-item>
              <n-descriptions-item label="当前节点">
                {{ detail.currentNodeName || detail.currentNodeId || '—' }}
              </n-descriptions-item>
              <n-descriptions-item label="重试次数">
                {{ detail.retryCount || 0 }}
              </n-descriptions-item>
              <n-descriptions-item label="开始时间">
                {{ detail.startTime || '—' }}
              </n-descriptions-item>
              <n-descriptions-item label="结束时间">
                {{ detail.endTime || '—' }}
              </n-descriptions-item>
              <n-descriptions-item v-if="detail.errorSummary" label="异常摘要" :span="2">
                {{ detail.errorSummary }}
              </n-descriptions-item>
            </n-descriptions>
            <h3 class="timeline-title">
              节点执行记录
            </h3>
            <div class="run-timeline">
              <AiTable :columns="timelineColumns" :data-source="detail.timeline || []" :row-key="row => String(row.id)" :pagination="false" :scroll-x="760" :show-toolbar="false" size="small" hide-selection empty-title="暂无节点执行记录" empty-description="节点执行后将在这里展示。">
                <template #node="{ row }">
                  <div class="run-entity">
                    <span>{{ row.nodeName || row.nodeId }}</span><span class="run-hint">{{ row.nodeId }}</span>
                  </div>
                </template>
                <template #status="{ row }">
                  <DictTag dict-type="ai_business_process_run_status" :value="row.status" :bordered="false" />
                </template>
                <template #summary="{ row }">
                  <span class="run-summary" :class="{ 'text-error': row.errorSummary }">{{ row.errorSummary || row.outputSummary || '—' }}</span>
                </template>
              </AiTable>
            </div>
          </template>
          <div v-else class="detail-placeholder" />
        </n-spin>
      </div>
      <template #footer>
        <div class="run-detail-footer">
          <n-button @click="detailModalRef?.close()">
            关闭
          </n-button>
        </div>
      </template>
    </AiModal>
  </div>
</template>

<style scoped>
.process-runs-panel {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}
.run-hint {
  color: var(--text-tertiary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.run-entity {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.run-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
.run-actions :deep(.n-divider) {
  margin: 0;
}
.run-detail-body {
  max-height: 65vh;
  overflow: auto;
}
.run-detail-body :deep(.n-descriptions-table-content) {
  overflow-wrap: anywhere;
}
.run-timeline {
  height: 280px;
  min-height: 0;
}
.timeline-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.run-summary {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.run-detail-footer {
  display: flex;
  justify-content: flex-end;
}
.detail-placeholder {
  min-height: 180px;
}
</style>
