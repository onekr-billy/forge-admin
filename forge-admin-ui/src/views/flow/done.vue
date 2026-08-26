<template>
  <div class="flow-page">
    <!-- 任务列表 -->
    <FlowTaskCardList
      v-model:search-value="queryParams.title"
      title="已办"
      :items="dataSource"
      :loading="loading"
      :pagination="pagination"
      :selectable="false"
      row-key="id"
      search-placeholder="搜索任务名称或编号..."
      empty-text="暂无已办任务"
      status-title="审批结果"
      node-title="处理节点"
      @search="handleSearch"
      @refresh="loadData"
      @row-click="openDrawer"
      @update:page="pagination.onChange"
      @update:page-size="pagination.onUpdatePageSize"
    >
      <template #filters>
        <NTreeSelect v-model:value="queryParams.category" placeholder="流程分类" clearable class="category-select" :options="categoryTreeOptions" :default-expand-all="true" @update:value="handleSearch" />
        <n-select v-model:value="queryParams.status" placeholder="审批结果" clearable class="category-select" :options="statusOptions" @update:value="handleSearch" />
        <NButton secondary @click="handleReset">
          重置
        </NButton>
      </template>
      <template #status="{ row }">
        <span class="task-status-pill" :class="getStatusTagClass(row.status)">
          {{ getStatusText(row.status) }}
        </span>
      </template>
      <template #title="{ row }">
        {{ getRowDisplayTitle(row) }}
      </template>
      <template #identifier="{ row }">
        {{ row.businessKey || row.processInstanceId || row.taskId || row.id }}
      </template>
      <template #node="{ row }">
        {{ getTaskDisplayName(row) }}
      </template>
      <template #user="{ row }">
        <span>{{ row.startUserName || '-' }}</span>
        <small>{{ row.completeTime || '-' }}</small>
      </template>
      <template #summary="{ row }">
        <span v-if="row.comment">审批意见：{{ row.comment }}</span>
      </template>
      <template #actions="{ row }">
        <button type="button" class="task-row-link-action" aria-label="查看详情" @click="openDrawer(row)">
          详情
        </button>
        <span class="task-row-action-separator" />
        <button type="button" class="task-row-link-action muted" aria-label="更多操作" @click="openDrawer(row)">
          <i class="i-lucide:more-horizontal" />
        </button>
      </template>
    </FlowTaskCardList>

    <!-- 详情弹窗 -->
    <FlowTaskDetailShell
      v-model:show="showDrawer"
      :title="currentTask ? getRowDisplayTitle(currentTask) : '审批详情'"
      :subtitle="getTaskDisplayName(currentTask, '') ? `处理节点：${getTaskDisplayName(currentTask)}` : ''"
      :status-text="getStatusText(currentTask?.status)"
      :status-class="getStatusTagClass(currentTask?.status)"
      :status-icon="getStatusIcon(currentTask?.status)"
      :records="approvalHistory"
      record-title="审批记录"
      fullscreen
    >
      <template v-if="currentTask">
        <section class="approval-detail-section">
          <div class="approval-section-header">
            <i class="i-material-symbols:info-outline" />
            基本信息
          </div>
          <div class="approval-field-grid">
            <div class="approval-field">
              <span class="approval-label">任务节点</span>
              <span class="approval-value">{{ getTaskDisplayName(currentTask) }}</span>
            </div>
            <div class="approval-field">
              <span class="approval-label">审批结果</span>
              <span class="approval-value">{{ getStatusText(currentTask.status) }}</span>
            </div>
            <div class="approval-field">
              <span class="approval-label">流程分类</span>
              <span class="approval-value">{{ getCategoryDisplayName(currentTask) }}</span>
            </div>
            <div class="approval-field">
              <span class="approval-label">发起人</span>
              <span class="approval-value approval-user-inline">
                <UserAvatar :name="currentTask.startUserName || '未知'" :size="24" />
                {{ currentTask.startUserName || '-' }}
              </span>
            </div>
            <div class="approval-field">
              <span class="approval-label">发起部门</span>
              <span class="approval-value">{{ currentTask.startDeptName || '-' }}</span>
            </div>
            <div class="approval-field">
              <span class="approval-label">完成时间</span>
              <span class="approval-value">{{ currentTask.completeTime || '-' }}</span>
            </div>
          </div>
        </section>

        <section class="approval-detail-section">
          <div class="approval-section-header">
            <i class="i-material-symbols:rate-review" />
            处理结果
          </div>
          <div class="approval-field-grid">
            <div class="approval-field full">
              <span class="approval-label">审批意见</span>
              <span class="approval-value approval-note">{{ currentTask.comment || '-' }}</span>
            </div>
            <div class="approval-field full">
              <span class="approval-label">审批签名</span>
              <span class="approval-value">
                <SignatureImage :value="currentTask.signature" />
              </span>
            </div>
          </div>
        </section>

        <section class="approval-detail-section">
          <div class="approval-section-header">
            <i class="i-material-symbols:fact-check" />
            表单内容
          </div>

          <div v-if="formInfoLoading" class="form-loading">
            <n-spin size="small" />
            <span>加载表单内容中...</span>
          </div>

          <template v-else>
            <FlowBusinessForm
              v-if="useExternalForm"
              :form-url="taskFormInfo.formUrl"
              :task-id="taskFormInfo.taskId"
              :business-key="taskFormInfo.businessKey"
              :process-instance-id="taskFormInfo.processInstanceId"
              :task-def-key="taskFormInfo.taskDefKey"
              :process-def-key="taskFormInfo.processDefKey"
              :variables="taskFormInfo.variables || {}"
              :approval-policy="readonlyApprovalPolicy"
              read-only
              @submit="noop"
            />

            <div v-else-if="businessFormLoading" class="form-loading">
              <n-spin size="small" />
              <span>加载业务表单中...</span>
            </div>

            <div v-else-if="useBusinessManagedForm" class="business-task-form-section readonly">
              <div class="approval-form-title">
                <span>{{ businessFormTitle }}</span>
              </div>
              <AiForm
                v-model:value="businessFormData"
                :schema="readonlyBusinessFormFields"
                :field-permissions="readonlyBusinessFormFieldPermissions"
                :show-actions="false"
                :show-feedback="false"
                :grid-cols="businessFormGridCols"
                :label-placement="businessFormLabelPlacement"
                :label-width="businessFormLabelWidth"
                :context="businessFormRenderContext"
                :form-assets="businessFormContext.formAssets || []"
              />
              <ChildTableEditor
                v-if="businessFormChildrenConfig.length"
                v-model:value="businessChildFormData"
                :children-config="businessFormChildrenConfig"
                readonly
                :parent-form-data="businessFormData"
                :context="businessFormRenderContext"
              />
              <div v-if="businessFormWarnings.length" class="business-form-warnings">
                <n-alert v-for="warning in businessFormWarnings" :key="warning" type="warning" :show-icon="false">
                  {{ warning }}
                </n-alert>
              </div>
              <div v-if="businessCodeFormUrl" class="business-form-actions">
                <NButton type="primary" secondary @click="openBusinessCodeForm">
                  打开完整业务页
                </NButton>
              </div>
            </div>

            <div v-if="useDynamicForm" class="dynamic-form-section readonly">
              <div class="approval-form-title">
                节点动态表单
              </div>
              <AiForm
                v-model:value="dynamicFormData"
                :schema="readonlyDynamicFormSchema"
                :field-permissions="readonlyDynamicFormFieldPermissions"
                :show-actions="false"
                :show-feedback="false"
                :grid-cols="2"
                label-placement="top"
              />
            </div>

            <n-empty v-if="showNoFormContent" description="暂无可展示的表单内容" size="small" />
          </template>
        </section>

        <section class="approval-detail-section">
          <n-collapse arrow-placement="right">
            <n-collapse-item title="查看流程图" name="diagram">
              <div class="approval-diagram">
                <DingFlowViewer v-if="currentTask.processInstanceId" :process-instance-id="currentTask.processInstanceId" :compact="true" />
                <n-empty v-else description="暂无流程图" size="small" />
              </div>
            </n-collapse-item>
          </n-collapse>
        </section>
      </template>
    </FlowTaskDetailShell>
  </div>
</template>

<script setup>
import { NButton, NTreeSelect } from 'naive-ui'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { businessTaskFormReadonlyContext } from '@/api/business-app'
import flowApi from '@/api/flow'
import { AiForm } from '@/components/ai-form'
import { formCreateToAiSchema } from '@/components/ai-form/adapters/formCreate'
import FlowBusinessForm from '@/components/common/FlowBusinessForm.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import DingFlowViewer from '@/components/flow-designer/viewer/DingFlowViewer.vue'
import FlowTaskCardList from '@/components/flow/FlowTaskCardList.vue'
import FlowTaskDetailShell from '@/components/flow/FlowTaskDetailShell.vue'
import SignatureImage from '@/components/flow/SignatureImage.vue'
import ChildTableEditor from '@/components/page-templates/ChildTableEditor.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store'
import { pickFirstNonEmptyFieldPermissions } from '@/utils/field-permissions'
import { buildFlowCategoryTreeOptions, resolveFlowCategoryLabel } from './utils/categoryOptions'
import { getBusinessFormDisplayTitle, getRowDisplayTitle, getTaskDisplayName } from './utils/processDisplay'

const userStore = useUserStore()
const router = useRouter()
const { dict, getLabel } = useDict('flow_done_status')
const loading = ref(false)
const dataSource = ref([])
const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
  onChange: (page) => {
    pagination.page = page
    loadData()
  },
  onUpdatePageSize: (size) => {
    pagination.pageSize = size
    pagination.page = 1
    loadData()
  },
})

const queryParams = reactive({ title: '', category: '', status: null })
const categoryTreeOptions = ref([])

const showDrawer = ref(false)
const currentTask = ref(null)
const approvalHistory = ref([])
const taskFormInfo = ref(null)
const formInfoLoading = ref(false)
const dynamicFormData = ref({})
const dynamicFormSchema = computed(() => formCreateToAiSchema(taskFormInfo.value?.formJson || []))
const businessFormContext = ref(null)
const businessFormData = ref({})
const businessChildFormData = ref({})
const businessFormLoading = ref(false)

const statusOptions = computed(() => toNumberOptions(dict.value.flow_done_status).filter(item => item.value !== 6))
const readonlyApprovalPolicy = {
  allowApprove: false,
  allowReject: false,
  allowDelegate: false,
  allowReturn: false,
  allowTerminate: false,
  requireComment: false,
  requireSignature: false,
}
const useDynamicForm = computed(() => taskFormInfo.value?.formType === 'dynamic' && dynamicFormSchema.value.length > 0)
const useBusinessObjectForm = computed(() => businessFormContext.value?.configured === true && businessFormContext.value?.formType === 'business-object')
const useBusinessCodeForm = computed(() => businessFormContext.value?.configured === true && businessFormContext.value?.formType === 'business-code')
const useBusinessManagedForm = computed(() => useBusinessObjectForm.value || useBusinessCodeForm.value)
const useExternalForm = computed(() => !useBusinessManagedForm.value && taskFormInfo.value?.formType === 'external' && taskFormInfo.value?.formUrl)
const businessFormTitle = computed(() => getBusinessFormDisplayTitle(businessFormContext.value, '业务表单'))
const businessFormWarnings = computed(() => Array.isArray(businessFormContext.value?.warnings) ? businessFormContext.value.warnings : [])
const businessFormChildrenConfig = computed(() => {
  const children = Array.isArray(businessFormContext.value?.childrenConfig) ? businessFormContext.value.childrenConfig : []
  return children.filter(child => child?.showInDetail !== false && Array.isArray(child.fields) && child.fields.length)
})
const businessCodeFormUrl = computed(() => businessFormContext.value?.formUrl || businessFormContext.value?.formRef?.formUrl || '')
const businessFormGridCols = computed(() => Math.max(1, Number(businessFormContext.value?.gridCols || 1)))
const businessFormLabelPlacement = computed(() => ['left', 'top'].includes(businessFormContext.value?.labelPlacement)
  ? businessFormContext.value.labelPlacement
  : 'left')
const businessFormLabelWidth = computed(() => businessFormContext.value?.labelWidth || '100')
const businessFormRenderContext = computed(() => ({
  task: currentTask.value,
  taskFormInfo: taskFormInfo.value,
  businessFormContext: businessFormContext.value,
  formAssets: businessFormContext.value?.formAssets || [],
}))
const readonlyBusinessFormFieldPermissions = computed(() => {
  return pickFirstNonEmptyFieldPermissions([
    businessFormContext.value?.fieldPermissions,
    taskFormInfo.value?.fieldPermissions,
    taskFormInfo.value?.formFieldPermissions,
  ], { readOnly: true })
})
const readonlyDynamicFormFieldPermissions = computed(() => {
  return pickFirstNonEmptyFieldPermissions([
    taskFormInfo.value?.fieldPermissions,
    taskFormInfo.value?.formFieldPermissions,
  ], { readOnly: true })
})
const readonlyDynamicFormSchema = computed(() => dynamicFormSchema.value.map(toReadonlyField))
const readonlyBusinessFormFields = computed(() => {
  return (businessFormContext.value?.fields || []).map(toReadonlyField)
})

function toReadonlyField(field = {}) {
  return {
    ...field,
    writable: false,
    readonly: true,
    disabled: true,
    props: {
      ...(field.props || {}),
      disabled: true,
      readonly: true,
    },
  }
}
const showNoFormContent = computed(() => {
  if (formInfoLoading.value || businessFormLoading.value)
    return false
  return !useExternalForm.value && !useDynamicForm.value && !useBusinessManagedForm.value
})

function getStatusTagClass(status) {
  const cls = { 2: 'success', 3: 'error', 4: 'warning', 5: 'info', 6: 'default', 7: 'warning', 8: 'error' }
  return cls[status] || 'default'
}

function getStatusIcon(status) {
  const icons = {
    2: 'i-material-symbols:check-circle',
    3: 'i-material-symbols:cancel',
    4: 'i-material-symbols:keyboard-return',
    5: 'i-material-symbols:person-add',
    7: 'i-material-symbols:undo',
    8: 'i-material-symbols:stop-circle',
  }
  return icons[status] || 'i-material-symbols:task-alt'
}

function getStatusText(status) {
  return getLabel('flow_done_status', status) || '未知'
}

function getCategoryDisplayName(row) {
  return row?.categoryName || resolveFlowCategoryLabel(row?.category, categoryTreeOptions.value, '-') || '-'
}

function toNumberOptions(options = []) {
  return options.map(item => ({
    ...item,
    value: Number(item.value),
  }))
}

function compactParams(source = {}) {
  const result = {}
  Object.entries(source).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '')
      result[key] = value
  })
  return result
}

function parseJsonObject(value) {
  if (!value)
    return {}
  if (typeof value === 'object')
    return { ...value }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  }
  catch {
    return {}
  }
}

function resetReadonlyForm() {
  taskFormInfo.value = null
  dynamicFormData.value = {}
  businessFormContext.value = null
  businessFormData.value = {}
  businessChildFormData.value = {}
  formInfoLoading.value = false
  businessFormLoading.value = false
}

function normalizeBusinessRecordData(recordData) {
  if (recordData && typeof recordData === 'object' && !Array.isArray(recordData)) {
    const main = recordData.main
    if (main && typeof main === 'object' && !Array.isArray(main))
      return { ...main }
    const { children, ...mainRecord } = recordData
    return { ...mainRecord }
  }
  return {}
}

function normalizeBusinessChildrenData(recordData) {
  const source = recordData?.children && typeof recordData.children === 'object' && !Array.isArray(recordData.children)
    ? recordData.children
    : {}
  const result = {}
  businessFormChildrenConfig.value.forEach((child) => {
    const key = resolveBusinessChildKey(child)
    result[key] = Array.isArray(source[key]) ? source[key] : []
  })
  return result
}

function resolveBusinessChildKey(child = {}) {
  return child.key || child.modelCode || child.tableName || 'children'
}

function logBusinessApprovalChildren(source, recordData) {
  console.warn('[FlowApprovalChildren]', {
    source,
    configKey: businessFormContext.value?.configKey,
    recordId: businessFormContext.value?.recordId,
    childrenConfig: businessFormChildrenConfig.value.map(child => ({
      key: resolveBusinessChildKey(child),
      modelCode: child.modelCode,
      tableName: child.tableName,
      relationType: child.relationType,
      sourceField: child.sourceField,
      targetField: child.targetField,
      fieldCount: Array.isArray(child.fields) ? child.fields.length : 0,
    })),
    recordChildren: summarizeBusinessChildren(recordData?.children),
    renderChildren: summarizeBusinessChildren(businessChildFormData.value),
  })
}

function summarizeBusinessChildren(children) {
  if (!children || typeof children !== 'object' || Array.isArray(children))
    return {}
  return Object.fromEntries(Object.entries(children).map(([key, rows]) => [
    key,
    {
      rows: Array.isArray(rows) ? rows.length : 0,
      rowIds: Array.isArray(rows) ? rows.slice(0, 5).map(row => row?.id) : [],
      firstFields: Array.isArray(rows) && rows[0] ? Object.keys(rows[0]).slice(0, 12) : [],
    },
  ]))
}

function buildProcessFormInfoQuery(row = {}) {
  return compactParams({
    taskId: row.taskId || row.id,
    businessKey: row.businessKey,
    processInstanceId: row.processInstanceId,
    processDefKey: row.processDefKey || row.processDefinitionKey,
  })
}

function buildBusinessReadonlyQuery(row = {}, formInfo = {}) {
  return compactParams({
    taskId: formInfo.taskId || row.taskId || row.id,
    businessKey: formInfo.businessKey || row.businessKey,
    processInstanceId: formInfo.processInstanceId || row.processInstanceId,
    processDefKey: formInfo.processDefKey || row.processDefKey || row.processDefinitionKey,
    taskDefKey: formInfo.taskDefKey || row.taskDefKey || row.taskDefinitionKey,
    objectCode: formInfo.objectCode || row.objectCode,
    recordId: formInfo.recordId || row.recordId,
    formKey: formInfo.formKey,
  })
}

function hasBusinessReadonlyQuery(query = {}) {
  return Boolean(query.processInstanceId || query.businessKey || (query.objectCode && query.recordId))
}

async function loadReadonlyBusinessTaskFormContext(row, formInfo) {
  businessFormContext.value = null
  businessFormData.value = {}
  businessChildFormData.value = {}
  const query = buildBusinessReadonlyQuery(row, formInfo)
  if (!hasBusinessReadonlyQuery(query))
    return null

  businessFormLoading.value = true
  try {
    const res = await businessTaskFormReadonlyContext(query)
    if (res.code !== 200) {
      console.error('加载业务表单只读上下文失败', res.message)
      return null
    }
    businessFormContext.value = res.data || null
    businessFormData.value = normalizeBusinessRecordData(res.data?.recordData)
    businessChildFormData.value = normalizeBusinessChildrenData(res.data?.recordData)
    logBusinessApprovalChildren('done', res.data?.recordData)
    return businessFormContext.value
  }
  catch (error) {
    console.error('加载业务表单只读上下文失败', error)
    return null
  }
  finally {
    businessFormLoading.value = false
  }
}

async function loadReadonlyFormInfo(row) {
  const query = buildProcessFormInfoQuery(row)
  if (!query.processInstanceId && !query.businessKey && !query.taskId)
    return

  formInfoLoading.value = true
  try {
    const res = await flowApi.getProcessFormInfo(query)
    if (res.code !== 200) {
      console.error('加载流程表单只读信息失败', res.message)
      return
    }
    const formInfo = res.data || {}
    taskFormInfo.value = formInfo
    dynamicFormData.value = {
      ...(formInfo.variables || {}),
      ...parseJsonObject(formInfo.formData),
    }
    await loadReadonlyBusinessTaskFormContext(row, formInfo)
  }
  catch (error) {
    console.error('加载流程表单只读信息失败', error)
  }
  finally {
    formInfoLoading.value = false
  }
}

function openBusinessCodeForm() {
  const url = businessCodeFormUrl.value
  if (!url)
    return
  if (/^https?:\/\//i.test(url)) {
    window.open(url, '_blank', 'noopener,noreferrer')
    return
  }
  router.push({
    path: url,
    query: compactParams({
      taskId: businessFormContext.value?.taskId || taskFormInfo.value?.taskId || currentTask.value?.taskId,
      businessKey: businessFormContext.value?.businessKey,
      processInstanceId: businessFormContext.value?.processInstanceId,
      taskDefKey: businessFormContext.value?.taskDefKey,
      processDefKey: businessFormContext.value?.processDefKey,
      objectCode: businessFormContext.value?.objectCode,
      recordId: businessFormContext.value?.recordId,
      source: 'flowDone',
      readOnly: 'true',
    }),
  })
}

function noop() {}

async function openDrawer(row) {
  currentTask.value = row
  approvalHistory.value = []
  resetReadonlyForm()
  showDrawer.value = true
  const promises = []
  if (row.processInstanceId) {
    promises.push(
      flowApi.getProcessHistory(row.processInstanceId)
        .then((res) => {
          if (res.code === 200)
            approvalHistory.value = res.data || []
        })
        .catch(e => console.error('加载审批历史失败', e)),
    )
  }
  promises.push(loadReadonlyFormInfo(row))
  await Promise.all(promises)
}

async function loadData() {
  loading.value = true
  try {
    const res = await flowApi.getDoneTasks({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      userId: userStore.userId,
      title: queryParams.title || undefined,
      category: queryParams.category || undefined,
      status: queryParams.status ?? undefined,
    })
    if (res.code === 200 && res.data) {
      dataSource.value = res.data.records || []
      pagination.itemCount = res.data.total || 0
    }
  }
  catch (e) {
    console.error('加载已办任务失败:', e)
  }
  finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await flowApi.getCategoryTreeSelect(false)
    if (res.code === 200 && res.data) {
      categoryTreeOptions.value = buildFlowCategoryTreeOptions(res.data)
    }
  }
  catch (e) {
    console.error('加载分类失败', e)
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  queryParams.title = ''
  queryParams.category = ''
  queryParams.status = null
  pagination.page = 1
  loadData()
}

onMounted(() => {
  loadCategories()
  loadData()
})
</script>

<style scoped>
:deep(.n-data-table .n-data-table-th),
:deep(.n-data-table .n-data-table-td) {
  padding: 6px 8px;
}

.flow-page {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
  background: var(--bg-secondary);
}
.page-header {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
}
.title-icon.done {
  background: linear-gradient(135deg, #34d399 0%, #10b981 100%);
}
.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.search-input {
  width: 220px;
}
.category-select {
  width: 132px;
}

.form-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 88px;
  color: #64748b;
}

.dynamic-form-section,
.business-task-form-section {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #d7dde7;
  border-radius: 8px;
  background: #f8fafc;
}

.dynamic-form-section.readonly,
.business-task-form-section.readonly {
  background: #fbfcfe;
}

.approval-form-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 700;
  color: #172033;
}

.business-form-warnings {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.business-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.table-container {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  flex: 1;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
}
:deep(.task-title-link) {
  color: #0369a1;
  cursor: pointer;
  font-weight: 600;
}
:deep(.task-title-link:hover) {
  text-decoration: underline;
}
:deep(.table-user) {
  display: flex;
  align-items: center;
  gap: 8px;
}
:deep(.user-name-text) {
  font-weight: 500;
  color: #0f172a;
}
:deep(.status-tag-mini) {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
:deep(.status-tag-mini.success) {
  background: #dcfce7;
  color: #15803d;
}
:deep(.status-tag-mini.error) {
  background: #fee2e2;
  color: #b91c1c;
}
:deep(.status-tag-mini.warning) {
  background: #fef3c7;
  color: #b45309;
}
:deep(.status-tag-mini.info) {
  background: #dbeafe;
  color: #1e40af;
}
:deep(.status-tag-mini.default) {
  background: #f1f5f9;
  color: #64748b;
}
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.drawer-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.status-dot.success {
  background: #10b981;
}
.status-dot.error {
  background: #ef4444;
}
.status-dot.warning {
  background: #f59e0b;
}
.status-dot.info {
  background: #3b82f6;
}
.status-dot.default {
  background: #94a3b8;
}
.drawer-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}
.status-tag {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}
.status-tag.success {
  background: #dcfce7;
  color: #15803d;
}
.status-tag.error {
  background: #fee2e2;
  color: #b91c1c;
}
.status-tag.warning {
  background: #fef3c7;
  color: #b45309;
}
.status-tag.info {
  background: #dbeafe;
  color: #1e40af;
}
.status-tag.default {
  background: #f1f5f9;
  color: #64748b;
}
.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 178px);
  overflow-y: auto;
  padding-bottom: 20px;
  padding: 18px 20px 20px;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.info-card {
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
}
.info-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}
.info-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.info-label {
  font-size: 12px;
  color: #64748b;
}
.info-value {
  font-size: 13px;
  color: #0f172a;
  font-weight: 500;
}
.info-value.highlight {
  color: #0369a1;
  font-weight: 600;
}
.signature-value {
  flex: 1;
  display: flex;
  justify-content: flex-end;
}
.user-item {
  align-items: flex-start;
}
.user-display {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
}

.flow-task-detail-modal {
  width: min(1080px, calc(100vw - 32px));
}

@media (max-width: 760px) {
  .flow-task-detail-modal {
    width: 100vw;
    height: 100vh;
    margin: 0;
  }

  .drawer-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .drawer-body {
    max-height: calc(100vh - 126px);
    padding: 14px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
