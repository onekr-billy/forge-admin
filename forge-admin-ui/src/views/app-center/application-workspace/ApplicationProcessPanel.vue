<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  businessProcessPage,
  copyBusinessProcess,
  createBusinessProcess,
  deleteBusinessProcess,
  publishBusinessProcess,
  updateBusinessProcessStatus,
  validateBusinessProcess,
} from '@/api/business-process'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

const props = defineProps({
  application: {
    type: Object,
    default: null,
  },
  initialObjects: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['changed', 'navigate', 'openDesigner'])
const route = useRoute()
const router = useRouter()
const { dict } = useDict('sys_normal_disable', 'ai_business_process_design_status')

const loading = ref(false)
const creating = ref(false)
const actionId = ref('')
const createVisible = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(normalizePage(route.query.processPage))
const pageSize = ref(10)
const keyword = ref(String(route.query.processKeyword || ''))
const status = ref(normalizeOptionalStatus(route.query.processStatus))
const createForm = reactive(createEmptyForm())

const objectOptions = computed(() => (props.initialObjects || [])
  .map(item => ({
    label: item.objectName || item.objectCode,
    code: item.objectCode,
    role: item.objectRole,
    value: stringValue(item.objectId || item.id),
  }))
  .filter(item => item.value))

const statusOptions = computed(() => (dict.value?.sys_normal_disable || [])
  .map(item => ({
    label: item.label || item.dictLabel,
    value: normalizeOptionalStatus(item.value ?? item.dictValue),
  }))
  .filter(item => item.value != null))

const applicationVersion = computed(() => (
  props.application?.publishedVersion
  || props.application?.currentVersion
  || props.application?.lastPublishVersion
  || 0
))

watch(() => props.application?.id, (applicationId) => {
  if (!applicationId) {
    records.value = []
    total.value = 0
    return
  }
  loadProcesses()
}, { immediate: true })

// 从流程画布返回时（returnTo 携带 processRefresh），重载列表同步最新草稿状态。
watch(() => route.query.processRefresh, () => {
  if (props.application?.id)
    loadProcesses()
})

async function loadProcesses() {
  if (!props.application?.id || loading.value)
    return
  loading.value = true
  try {
    const response = await businessProcessPage({
      applicationId: stringValue(props.application.id),
      keyword: keyword.value || undefined,
      status: status.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    const data = response.data || {}
    records.value = Array.isArray(data.records) ? data.records : (data.list || [])
    total.value = Number(data.total || records.value.length)
  }
  catch (error) {
    records.value = []
    total.value = 0
    notify('error', errorMessage(error, '业务流程加载失败'))
  }
  finally {
    loading.value = false
  }
}

function openCreate() {
  if (!objectOptions.value.length)
    return
  Object.assign(createForm, createEmptyForm())
  const primary = objectOptions.value.find(item => item.role === 'PRIMARY') || objectOptions.value[0]
  createForm.subjectObjectId = primary?.value || ''
  createVisible.value = true
}

async function confirmCreate() {
  const processName = createForm.processName.trim()
  if (!processName) {
    notify('warning', '请输入流程名称')
    return
  }
  if (!createForm.subjectObjectId) {
    notify('warning', '请选择主业务对象')
    return
  }
  creating.value = true
  try {
    const response = await createBusinessProcess({
      applicationId: stringValue(props.application.id),
      processName,
      processDescription: createForm.processDescription.trim(),
      subjectObjectId: stringValue(createForm.subjectObjectId),
      status: 1,
    })
    createVisible.value = false
    notify('success', '业务流程已创建')
    emit('changed')
    const processId = stringValue(response.data?.id)
    if (processId)
      openDesigner(processId)
    else
      await loadProcesses()
  }
  finally {
    creating.value = false
  }
}

async function copyProcess(item) {
  const processId = stringValue(item.id)
  if (!processId || actionId.value)
    return
  actionId.value = `copy:${processId}`
  try {
    const response = await copyBusinessProcess(processId, {})
    notify('success', '流程副本已创建')
    emit('changed')
    await loadProcesses()
    const copiedId = stringValue(response.data?.id)
    if (copiedId)
      openDesigner(copiedId)
  }
  finally {
    actionId.value = ''
  }
}

async function toggleStatus(item) {
  const processId = stringValue(item.id)
  if (!processId || actionId.value)
    return
  const nextStatus = Number(item.status) === 1 ? 0 : 1
  actionId.value = `status:${processId}`
  try {
    await updateBusinessProcessStatus(processId, nextStatus)
    notify('success', nextStatus === 1 ? '业务流程已启用' : '业务流程已停用')
    emit('changed')
    await loadProcesses()
  }
  finally {
    actionId.value = ''
  }
}

function removeProcess(item) {
  const processId = stringValue(item.id)
  if (!processId || actionId.value)
    return
  const performDelete = async () => {
    actionId.value = `delete:${processId}`
    try {
      await deleteBusinessProcess(processId)
      notify('success', '业务流程已删除')
      emit('changed')
      if (records.value.length === 1 && pageNum.value > 1)
        pageNum.value -= 1
      await loadProcesses()
    }
    finally {
      actionId.value = ''
    }
  }
  if (!window.$dialog)
    return
  window.$dialog.warning({
    title: '删除业务流程',
    content: `确认删除“${item.processName || item.processCode}”吗？存在发布版本或运行记录时，服务端会拒绝删除。`,
    positiveText: '确认删除',
    negativeText: '取消',
    onPositiveClick: performDelete,
  })
}

function openDesigner(processId) {
  emit('openDesigner', { processId: stringValue(processId) })
}

function openRunDetail() {
  notify('info', '运行记录将在编排运行服务接入后开放')
}

function previewMigration() {
  notify('info', '迁移预览将在存量配置迁移服务接入后开放')
}

function requestApplicationPublish() {
  emit('navigate', 'releases')
}

// 独立发布：只生成该流程的不可变版本并切换运行投影，不触发应用发布，也不影响其他流程。
function publishProcess(item) {
  const processId = stringValue(item.id)
  if (!processId || actionId.value)
    return
  if (!window.$dialog)
    return
  window.$dialog.warning({
    title: '发布业务流程',
    content: `将为“${item.processName || item.processCode}”生成不可变流程版本并立即生效，应用内其他资产与流程不受影响。`,
    positiveText: '检查并发布',
    negativeText: '取消',
    onPositiveClick: () => executePublish(item),
  })
}

async function executePublish(item) {
  const processId = stringValue(item.id)
  if (!processId || actionId.value)
    return
  actionId.value = `publish:${processId}`
  try {
    const validation = await validateBusinessProcess(processId)
    if (!validation.data?.valid) {
      const errorCount = Number(validation.data?.errorCount || 0)
      notify('warning', errorCount
        ? `流程检查发现 ${errorCount} 项错误，请修正后再发布`
        : '流程检查未通过，请修正后再发布')
      return
    }
    const response = await publishBusinessProcess(processId)
    const versionNo = Number(response.data?.versionNo || 0)
    notify('success', versionNo ? `业务流程已发布为 V${versionNo}` : '业务流程发布成功')
    emit('changed')
    await loadProcesses()
  }
  catch (error) {
    notify('error', errorMessage(error, '业务流程发布失败'))
  }
  finally {
    actionId.value = ''
  }
}

async function applyFilters() {
  pageNum.value = 1
  await syncRouteFilters()
  await loadProcesses()
}

async function changePage(value) {
  pageNum.value = normalizePage(value)
  await syncRouteFilters()
  await loadProcesses()
}

function syncRouteFilters() {
  return router.replace({
    path: route.path,
    query: {
      ...route.query,
      processKeyword: keyword.value || undefined,
      processStatus: status.value == null ? undefined : String(status.value),
      processPage: pageNum.value > 1 ? String(pageNum.value) : undefined,
    },
  })
}

function subjectName(item) {
  const objectId = stringValue(item.subjectObjectId)
  const objectCode = item.subjectObjectCode
  return objectOptions.value.find(object => object.value === objectId || object.code === objectCode)?.label
    || objectCode
    || '未识别对象'
}

function createEmptyForm() {
  return {
    processName: '',
    processDescription: '',
    subjectObjectId: '',
  }
}

function normalizePage(value) {
  const page = Number(value)
  return Number.isInteger(page) && page > 0 ? page : 1
}

function normalizeOptionalStatus(value) {
  if (value === '' || value == null)
    return null
  const normalized = Number(value)
  return Number.isInteger(normalized) ? normalized : null
}

function stringValue(value) {
  return value == null ? '' : String(value)
}

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function notify(type, message) {
  window.$message?.[type]?.(message)
}
</script>

<template>
  <section class="process-panel" data-workspace-process>
    <header class="process-panel-header">
      <div>
        <div class="header-title-row">
          <h2>业务流程</h2>
          <span v-if="applicationVersion">应用版本 {{ applicationVersion }}</span>
        </div>
        <p>在当前应用内统一编排触发、条件、Flowable 审批和自动化动作。</p>
      </div>
      <div class="header-actions">
        <n-button size="small" secondary :loading="loading" @click="loadProcesses">
          刷新
        </n-button>
        <n-button size="small" secondary @click="requestApplicationPublish">
          应用发布
        </n-button>
        <n-button
          data-process-action="create"
          size="small"
          type="primary"
          :disabled="!objectOptions.length"
          @click="openCreate"
        >
          新建流程
        </n-button>
      </div>
    </header>

    <div class="process-sections" aria-label="业务流程功能区">
      <button type="button" class="section-tab is-active">
        流程列表
        <span>{{ total }}</span>
      </button>
      <button
        type="button"
        class="section-tab is-reserved"
        disabled
        title="等待编排运行服务接入"
        @click="openRunDetail"
      >
        运行记录
        <small>待接入</small>
      </button>
      <button
        type="button"
        class="section-tab is-reserved"
        disabled
        title="等待存量配置迁移服务接入"
        @click="previewMigration"
      >
        迁移与问题
        <small>待接入</small>
      </button>
    </div>

    <div class="process-filter-bar">
      <label class="keyword-field">
        <span class="sr-only">搜索业务流程</span>
        <input
          v-model="keyword"
          type="search"
          placeholder="搜索流程名称或编码"
          @keyup.enter="applyFilters"
        >
      </label>
      <label>
        <span class="sr-only">流程状态</span>
        <select v-model="status" @change="applyFilters">
          <option :value="null">全部状态</option>
          <option v-for="item in statusOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>
      <n-button size="small" @click="applyFilters">
        查询
      </n-button>
    </div>

    <n-spin :show="loading">
      <div v-if="records.length" class="process-table-wrap">
        <table class="process-table">
          <thead>
            <tr>
              <th>流程</th>
              <th>主业务对象</th>
              <th>设计状态</th>
              <th>版本</th>
              <th>启停状态</th>
              <th class="operation-heading">
                操作
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in records" :key="String(item.id)">
              <td>
                <button
                  type="button"
                  class="process-identity"
                  :data-process-open="String(item.id)"
                  @click="openDesigner(item.id)"
                >
                  <strong>{{ item.processName || item.processCode }}</strong>
                  <code>{{ item.processCode }}</code>
                </button>
              </td>
              <td>
                <div class="subject-cell">
                  <strong>{{ subjectName(item) }}</strong>
                  <small>{{ item.subjectObjectCode }}</small>
                </div>
              </td>
              <td>
                <DictTag
                  dict-type="ai_business_process_design_status"
                  :value="item.designStatus"
                  :bordered="false"
                />
              </td>
              <td>
                <div class="version-cell">
                  <strong>{{ item.publishedVersion ? `V${item.publishedVersion}` : '未发布' }}</strong>
                  <small>草稿 {{ item.currentVersion || 0 }}</small>
                </div>
              </td>
              <td>
                <DictTag dict-type="sys_normal_disable" :value="item.status" :bordered="false" />
              </td>
              <td>
                <div class="row-actions">
                  <button type="button" class="text-primary" @click="openDesigner(item.id)">
                    设计
                  </button>
                  <button
                    type="button"
                    class="text-success"
                    :data-process-publish="String(item.id)"
                    :disabled="Boolean(actionId)"
                    @click="publishProcess(item)"
                  >
                    发布
                  </button>
                  <button
                    type="button"
                    class="text-primary"
                    :data-process-copy="String(item.id)"
                    :disabled="Boolean(actionId)"
                    @click="copyProcess(item)"
                  >
                    复制
                  </button>
                  <button
                    type="button"
                    :class="Number(item.status) === 1 ? 'text-warning' : 'text-success'"
                    :data-process-status="String(item.id)"
                    :disabled="Boolean(actionId)"
                    @click="toggleStatus(item)"
                  >
                    {{ Number(item.status) === 1 ? '停用' : '启用' }}
                  </button>
                  <button
                    type="button"
                    class="text-error"
                    :data-process-delete="String(item.id)"
                    :disabled="Boolean(actionId)"
                    @click="removeProcess(item)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <n-empty
        v-else-if="!loading"
        :description="objectOptions.length ? '当前应用还没有业务流程' : '请先在业务对象分区加入主业务对象'"
      >
        <template v-if="objectOptions.length" #extra>
          <n-button size="small" type="primary" @click="openCreate">
            新建第一个流程
          </n-button>
        </template>
      </n-empty>
    </n-spin>

    <footer v-if="total > pageSize" class="process-pagination">
      <span>共 {{ total }} 项</span>
      <n-pagination
        :page="pageNum"
        :page-size="pageSize"
        :item-count="total"
        @update:page="changePage"
      />
    </footer>

    <n-modal v-model:show="createVisible" preset="card" title="新建业务流程" class="process-create-modal">
      <div class="create-form">
        <label>
          <span>流程名称</span>
          <input
            v-model="createForm.processName"
            data-process-field="name"
            maxlength="128"
            placeholder="例如：采购提交审批"
          >
        </label>
        <label>
          <span>主业务对象</span>
          <select v-model="createForm.subjectObjectId" data-process-field="subject">
            <option v-for="item in objectOptions" :key="item.value" :value="item.value">
              {{ item.label }}（{{ item.code }}）
            </option>
          </select>
          <small>流程运行记录将以该对象的记录作为业务主体，创建后不可切换。</small>
        </label>
        <label>
          <span>说明</span>
          <textarea
            v-model="createForm.processDescription"
            maxlength="500"
            rows="3"
            placeholder="说明流程适用场景，可稍后补充"
          />
        </label>
        <div class="generated-code-note">
          流程编码由系统根据名称生成并保证应用内唯一，创建后保持稳定。
        </div>
      </div>
      <template #footer>
        <div class="modal-actions">
          <n-button @click="createVisible = false">
            取消
          </n-button>
          <n-button
            data-process-action="confirm-create"
            type="primary"
            :loading="creating"
            @click="confirmCreate"
          >
            创建并设计
          </n-button>
        </div>
      </template>
    </n-modal>
  </section>
</template>

<style scoped>
.process-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 14px;
}

.process-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light, #e5e6eb);
}

.header-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-title-row h2 {
  margin: 0;
  font-size: 18px;
}

.header-title-row span {
  padding: 2px 7px;
  border-radius: 10px;
  color: var(--text-tertiary, #86909c);
  background: var(--bg-tertiary, #f2f3f5);
  font-size: 11px;
}

.process-panel-header p {
  margin: 5px 0 0;
  color: var(--text-tertiary, #86909c);
  font-size: 13px;
}

.header-actions,
.row-actions,
.modal-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.process-sections {
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--border-light, #e5e6eb);
}

.section-tab {
  display: flex;
  min-height: 38px;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  border-bottom: 2px solid transparent;
  color: var(--text-secondary, #4e5969);
  font-size: 13px;
}

.section-tab.is-active {
  border-bottom-color: var(--primary-color, #165dff);
  color: var(--primary-color, #165dff);
  font-weight: 600;
}

.section-tab span,
.section-tab small {
  padding: 1px 6px;
  border-radius: 8px;
  background: var(--bg-tertiary, #f2f3f5);
  color: var(--text-tertiary, #86909c);
  font-size: 10px;
  font-weight: 400;
}

.section-tab.is-reserved {
  cursor: not-allowed;
  opacity: 0.58;
}

.process-filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--border-light, #e5e6eb);
  border-radius: 7px;
  background: var(--bg-secondary, #f7f8fa);
}

.process-filter-bar input,
.process-filter-bar select,
.create-form input,
.create-form select,
.create-form textarea {
  min-height: 34px;
  border: 1px solid var(--border-default, #c9cdd4);
  border-radius: 6px;
  background: var(--card-color, #fff);
  padding: 6px 9px;
  color: var(--text-primary, #1d2129);
  outline: none;
}

.process-filter-bar input:focus,
.process-filter-bar select:focus,
.create-form input:focus,
.create-form select:focus,
.create-form textarea:focus {
  border-color: var(--primary-color, #165dff);
}

.keyword-field {
  width: min(320px, 42vw);
}

.keyword-field input {
  width: 100%;
}

.process-table-wrap {
  overflow: auto;
  border: 1px solid var(--border-light, #e5e6eb);
  border-radius: 7px;
}

.process-table {
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
  table-layout: fixed;
}

.process-table th,
.process-table td {
  padding: 11px 12px;
  border-bottom: 1px solid var(--border-light, #e5e6eb);
  text-align: left;
  vertical-align: middle;
}

.process-table th {
  color: var(--text-tertiary, #86909c);
  background: var(--bg-secondary, #f7f8fa);
  font-size: 12px;
  font-weight: 500;
}

.process-table th:first-child {
  width: 25%;
}

.process-table th:nth-child(2) {
  width: 18%;
}

.process-table th:nth-child(3),
.process-table th:nth-child(4),
.process-table th:nth-child(5) {
  width: 12%;
}

.process-table tbody tr:last-child td {
  border-bottom: 0;
}

.process-table tbody tr:hover {
  background: var(--bg-hover, #f7f8fa);
}

.operation-heading {
  width: 21%;
  text-align: right !important;
}

.process-identity,
.subject-cell,
.version-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  text-align: left;
}

.process-identity strong,
.subject-cell strong,
.version-cell strong {
  overflow: hidden;
  max-width: 100%;
  color: var(--text-primary, #1d2129);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-identity:hover strong {
  color: var(--primary-color, #165dff);
}

.process-identity code,
.subject-cell small,
.version-cell small {
  overflow: hidden;
  max-width: 100%;
  color: var(--text-tertiary, #86909c);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  justify-content: flex-end;
  white-space: nowrap;
}

.row-actions button {
  cursor: pointer;
  font-size: 12px;
}

.row-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.process-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-tertiary, #86909c);
  font-size: 12px;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.create-form label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.create-form label > span {
  color: var(--text-primary, #1d2129);
  font-size: 13px;
  font-weight: 600;
}

.create-form label > small,
.generated-code-note {
  color: var(--text-tertiary, #86909c);
  font-size: 12px;
  line-height: 1.55;
}

.generated-code-note {
  padding: 9px 10px;
  border: 1px solid var(--border-light, #e5e6eb);
  border-radius: 6px;
  background: var(--bg-secondary, #f7f8fa);
}

.modal-actions {
  justify-content: flex-end;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

@media (max-width: 860px) {
  .process-panel-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .process-filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .keyword-field {
    width: 100%;
  }
}
</style>
