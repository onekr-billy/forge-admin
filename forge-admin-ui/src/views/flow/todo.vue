<template>
  <div class="flow-page">
    <!-- 任务列表 -->
    <FlowTaskCardList
      v-model:selected-keys="selectedTaskKeys"
      v-model:search-value="queryParams.title"
      title="我的待办任务"
      :items="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      search-placeholder="搜索任务名称或编号..."
      empty-text="暂无待办任务"
      @search="handleSearch"
      @refresh="loadData"
      @row-click="openDrawer"
      @update:page="pagination.onChange"
      @update:page-size="pagination.onUpdatePageSize"
    >
      <template #filters>
        <NTreeSelect
          v-model:value="queryParams.category"
          placeholder="流程分类"
          clearable
          class="category-select"
          :options="categoryTreeOptions"
          :default-expand-all="true"
          @update:value="handleSearch"
        />
        <n-select
          v-model:value="queryParams.status"
          placeholder="任务状态"
          clearable
          class="category-select"
          :options="statusOptions"
          @update:value="handleSearch"
        />
        <NButton secondary @click="handleReset">
          重置
        </NButton>
      </template>
      <template #batch-actions>
        <NButton
          v-if="selectedTaskKeys.length > 0"
          size="small"
          type="error"
          secondary
          @click="openQuickAction('reject', selectedTaskKeys)"
        >
          驳回
        </NButton>
        <NButton
          v-if="selectedTaskKeys.length > 0"
          size="small"
          type="primary"
          @click="openQuickAction('approve', selectedTaskKeys)"
        >
          同意
        </NButton>
        <span v-if="urgentCount > 0" class="task-list-hint urgent">
          <i class="i-material-symbols:warning" />
          {{ urgentCount }} 紧急
        </span>
      </template>
      <template #status="{ row }">
        <span class="task-status-pill" :class="row.status === 0 ? 'todo-status-pending' : 'todo-status-active'">
          {{ getLabel('flow_todo_status', row.status) }}
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
        <small>{{ row.createTime || '-' }}</small>
      </template>
      <template #actions="{ row }">
        <button type="button" class="task-row-link-action success" aria-label="同意任务" @click="openQuickAction('approve', [row])">
          同意
        </button>
        <span class="task-row-action-separator" />
        <button type="button" class="task-row-link-action danger" aria-label="驳回任务" @click="openQuickAction('reject', [row])">
          驳回
        </button>
        <span class="task-row-action-separator" />
        <button type="button" class="task-row-link-action" aria-label="去审批" @click="openDrawer(row)">
          审批
        </button>
        <template v-if="row.status === 0 && !row.assignee">
          <span class="task-row-action-separator" />
          <button type="button" class="task-row-link-action info" aria-label="签收任务" @click="handleClaim(row)">
            签收
          </button>
        </template>
        <span class="task-row-action-separator" />
        <button type="button" class="task-row-link-action muted" aria-label="更多操作" @click="openDrawer(row)">
          <i class="i-lucide:more-horizontal" />
        </button>
      </template>
    </FlowTaskCardList>

    <n-modal
      v-model:show="quickActionVisible"
      preset="card"
      class="quick-action-modal"
      :title="quickActionTitle"
      :bordered="false"
      :segmented="{ content: true, footer: true }"
      content-style="padding: 18px;"
    >
      <div class="quick-action-body">
        <div class="quick-action-summary">
          已选择 <strong>{{ quickActionTargets.length }}</strong> 条待办，提交后会按顺序处理。
        </div>
        <n-input
          v-model:value="quickActionForm.comment"
          type="textarea"
          :rows="4"
          :maxlength="500"
          show-count
          :placeholder="quickActionType === 'approve' ? '请输入同意意见' : '请输入驳回原因'"
        />
        <div class="quick-action-tip">
          需要填写动态表单或手写签名的任务会自动跳过，请进入详情处理。
        </div>
      </div>
      <template #footer>
        <div class="quick-action-footer">
          <NButton :disabled="quickActionLoading" @click="quickActionVisible = false">
            取消
          </NButton>
          <NButton :type="quickActionType === 'approve' ? 'primary' : 'error'" :loading="quickActionLoading" @click="submitQuickAction">
            {{ quickActionType === 'approve' ? '同意' : '驳回' }}
          </NButton>
        </div>
      </template>
    </n-modal>

    <!-- 审批详情弹窗 -->
    <FlowTaskDetailShell
      v-model:show="showDrawer"
      :busy="approveLoading"
      :title="currentTask ? getRowDisplayTitle(currentTask) : '审批详情'"
      :subtitle="getTaskDisplayName(currentTask, '') ? `当前节点：${getTaskDisplayName(currentTask)}` : ''"
      :status-text="getLabel('flow_todo_status', currentTask?.status)"
      :status-class="currentTask?.status === 0 ? 'todo-status-pending' : 'todo-status-active'"
      :status-icon="currentTask?.status === 0 ? 'i-material-symbols:schedule' : 'i-material-symbols:assignment-ind'"
      :priority-text="getPriorityText(currentTask?.priority)"
      :priority-class="getPriorityClass(currentTask?.priority)"
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
              <span class="approval-label">当前节点</span>
              <span class="approval-value">{{ getTaskDisplayName(currentTask) }}</span>
            </div>
            <div class="approval-field">
              <span class="approval-label">流程名称</span>
              <span class="approval-value">{{ getProcessDisplayName(currentTask) }}</span>
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
              <span class="approval-label">发起时间</span>
              <span class="approval-value">{{ currentTask.createTime || '-' }}</span>
            </div>
            <div class="approval-field">
              <span class="approval-label">任务状态</span>
              <span class="approval-value">{{ getLabel('flow_todo_status', currentTask.status) || '-' }}</span>
            </div>
          </div>
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

        <section class="approval-detail-section">
          <div class="approval-section-header">
            <i class="i-material-symbols:rate-review" />
            审批处理
          </div>

          <div v-if="formInfoLoading" class="form-loading">
            <n-spin size="small" />
            <span>加载表单中...</span>
          </div>

          <template v-else-if="useComponentTaskForm">
            <FlowBusinessForm
              :form-url="componentTaskFormUrl"
              :task-id="componentTaskFormInfo.taskId"
              :business-key="componentTaskFormInfo.businessKey"
              :process-instance-id="componentTaskFormInfo.processInstanceId"
              :task-def-key="componentTaskFormInfo.taskDefKey"
              :process-def-key="componentTaskFormInfo.processDefKey"
              :variables="taskFormInfo?.variables || {}"
              :approval-policy="approvalPolicy"
              :initial-task-context="businessFormContext"
              :read-only="false"
              :submitting="approveLoading"
              :submitting-action="approveForm.action"
              @submit="handleExternalFormSubmit"
              @cancel="showDrawer = false"
            >
              <template #actions>
                <NButton v-if="canDelegate" size="large" :disabled="isApprovalBusy" @click="handleDelegate">
                  <i class="i-material-symbols:person-add mr-2" />
                  转办
                </NButton>

                <NButton
                  v-if="currentTask.status === 0 && !currentTask.assignee"
                  type="info"
                  size="large"
                  :loading="isClaimingTask(currentTask)"
                  :disabled="isApprovalBusy"
                  @click="handleClaim(currentTask)"
                >
                  <i class="i-material-symbols:assignment-ind mr-2" />
                  签收
                </NButton>
              </template>
            </FlowBusinessForm>
          </template>

          <template v-else>
            <div v-if="businessFormLoading" class="form-loading">
              <n-spin size="small" />
              <span>加载业务表单中...</span>
            </div>

            <div v-else-if="useBusinessManagedForm" class="business-task-form-section">
              <div class="approval-form-title">
                <span>{{ businessFormTitle }}</span>
                <small v-if="businessFormContext?.pageName || businessFormContext?.formRef?.pageName">
                  页面：{{ businessFormContext.pageName || businessFormContext.formRef.pageName }}
                </small>
              </div>
              <AiForm
                ref="businessFormRef"
                v-model:value="businessFormData"
                :schema="businessFormContext.fields || []"
                :field-permissions="businessFormFieldPermissions"
                :show-actions="false"
                :show-feedback="true"
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
              <div v-if="businessFormHasWritableFields || (useBusinessCodeForm && businessCodeFormUrl)" class="business-form-actions">
                <n-tooltip v-if="businessFormHasWritableFields" trigger="hover">
                  <template #trigger>
                    <NButton
                      type="primary"
                      secondary
                      :loading="businessFormSaving"
                      :disabled="isApprovalBusy"
                      @click="() => saveBusinessTaskFormFields({ validate: true, silent: false })"
                    >
                      暂存修改
                    </NButton>
                  </template>
                  同意或驳回时会先提交本节点可编辑字段；这里用于暂存修改，不流转流程。
                </n-tooltip>
                <NButton
                  v-if="useBusinessCodeForm && businessCodeFormUrl"
                  secondary
                  :disabled="isApprovalBusy"
                  @click="openBusinessCodeForm"
                >
                  打开完整业务页
                </NButton>
              </div>
            </div>

            <div v-if="useDynamicForm" class="dynamic-form-section">
              <div class="approval-form-title">
                节点动态表单
              </div>
              <AiForm
                ref="dynamicFormRef"
                v-model:value="dynamicFormData"
                :schema="dynamicFormSchema"
                :field-permissions="dynamicFormFieldPermissions"
                :show-actions="false"
                :show-feedback="true"
                :grid-cols="2"
                label-placement="top"
              />
            </div>

            <n-form :model="approveForm" label-placement="top">
              <n-form-item label="审批意见" :required="requireComment">
                <n-input
                  v-model:value="approveForm.comment"
                  type="textarea"
                  :rows="3"
                  :placeholder="requireComment ? '请输入审批意见' : '请输入审批意见（可选）'"
                  :maxlength="500"
                  show-count
                />
              </n-form-item>
              <n-form-item v-if="requireSignature" label="审批签名" required>
                <SignaturePad
                  :key="approveSignatureKey"
                  ref="approveSignatureRef"
                  v-model="approveForm.signature"
                  :business-id="currentTask?.taskId || currentTask?.id || ''"
                />
              </n-form-item>
            </n-form>

            <div class="action-buttons">
              <n-popconfirm v-if="canApprove" @positive-click="() => submitApprove('approve')">
                <template #trigger>
                  <NButton type="success" size="large" :loading="isActionLoading('approve')" :disabled="isApprovalBusy">
                    <i class="i-material-symbols:check-circle mr-2" />
                    同意
                  </NButton>
                </template>
                确认同意该审批？
              </n-popconfirm>

              <n-popconfirm v-if="canReject" @positive-click="() => submitApprove('reject')">
                <template #trigger>
                  <NButton type="error" size="large" :loading="isActionLoading('reject')" :disabled="isApprovalBusy">
                    <i class="i-material-symbols:cancel mr-2" />
                    驳回
                  </NButton>
                </template>
                确认驳回该审批？
              </n-popconfirm>

              <n-popconfirm v-if="canReturn" @positive-click="() => submitApprove('return')">
                <template #trigger>
                  <NButton type="warning" size="large" :loading="isActionLoading('return')" :disabled="isApprovalBusy">
                    <i class="i-material-symbols:keyboard-return mr-2" />
                    退回
                  </NButton>
                </template>
                确认退回上一审批节点？
              </n-popconfirm>

              <n-popconfirm v-if="canTerminate" @positive-click="() => submitApprove('terminate')">
                <template #trigger>
                  <NButton type="error" ghost size="large" :loading="isActionLoading('terminate')" :disabled="isApprovalBusy">
                    <i class="i-material-symbols:stop-circle mr-2" />
                    终结
                  </NButton>
                </template>
                确认终结该流程？
              </n-popconfirm>

              <NButton v-if="canDelegate" size="large" :disabled="isApprovalBusy" @click="handleDelegate">
                <i class="i-material-symbols:person-add mr-2" />
                转办
              </NButton>

              <NButton
                v-if="currentTask.status === 0 && !currentTask.assignee"
                type="info"
                size="large"
                :loading="isClaimingTask(currentTask)"
                :disabled="isApprovalBusy"
                @click="handleClaim(currentTask)"
              >
                <i class="i-material-symbols:assignment-ind mr-2" />
                签收
              </NButton>
            </div>
          </template>
        </section>
      </template>
    </FlowTaskDetailShell>

    <!-- 转办弹窗 -->
    <n-modal v-model:show="showDelegateModal" preset="card" title="转办任务" style="width: 480px" :mask-closable="false">
      <n-form :model="delegateForm" label-placement="top">
        <n-form-item label="转办给" required>
          <div class="delegate-user-row">
            <div class="delegate-user-display">
              <template v-if="delegateTargetUser">
                <UserAvatar :name="delegateTargetUser.name || delegateTargetUser.username || 'U'" :size="24" />
                <span class="delegate-user-name">{{ delegateTargetUser.name || delegateTargetUser.username }}</span>
                <span class="delegate-user-id">{{ delegateTargetUser.username }}</span>
              </template>
              <span v-else class="delegate-placeholder">未选择转办人</span>
            </div>
            <NButton size="small" @click="showUserSelectModal = true">
              <i class="i-material-symbols:person-search mr-2" />
              选择人员
            </NButton>
          </div>
        </n-form-item>
        <n-form-item label="转办说明">
          <n-input
            v-model:value="delegateForm.comment"
            type="textarea"
            :rows="2"
            :placeholder="requireComment ? '请输入转办说明' : '请输入转办说明（可选）'"
          />
        </n-form-item>
        <n-form-item v-if="requireSignature" label="审批签名" required>
          <SignaturePad
            :key="delegateSignatureKey"
            ref="delegateSignatureRef"
            v-model="delegateForm.signature"
            :business-id="currentTask?.taskId || currentTask?.id || ''"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showDelegateModal = false">
            取消
          </NButton>
          <NButton type="primary" :loading="delegateLoading" @click="submitDelegate">
            确认转办
          </NButton>
        </NSpace>
      </template>
    </n-modal>

    <!-- 用户选择弹窗 -->
    <UserSelectModal
      :show="showUserSelectModal"
      title="选择转办人"
      :multiple="false"
      @update:show="showUserSelectModal = $event"
      @confirm="handleUserSelected"
    />
  </div>
</template>

<script setup>
import { NButton, NSpace, NTreeSelect } from 'naive-ui'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { businessTaskFormContext, completeBusinessTaskAction, saveBusinessTaskFormContext } from '@/api/business-app'
import flowApi from '@/api/flow'
import { AiForm } from '@/components/ai-form'
import { formCreateToAiSchema } from '@/components/ai-form/adapters/formCreate'
import FlowBusinessForm from '@/components/common/FlowBusinessForm.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import UserSelectModal from '@/components/common/UserSelectModal.vue'
import DingFlowViewer from '@/components/flow-designer/viewer/DingFlowViewer.vue'
import FlowTaskCardList from '@/components/flow/FlowTaskCardList.vue'
import FlowTaskDetailShell from '@/components/flow/FlowTaskDetailShell.vue'
import SignaturePad from '@/components/flow/SignaturePad.vue'
import ChildTableEditor from '@/components/page-templates/ChildTableEditor.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store'
import { pickFirstNonEmptyFieldPermissions } from '@/utils/field-permissions'
import { buildFlowCategoryTreeOptions, resolveFlowCategoryLabel } from './utils/categoryOptions'
import { FLOW_PRIORITY_LABEL_FALLBACK, getFlowPriorityClass, isUrgentFlowPriority, resolveFlowPriorityLevel, shouldShowFlowPriority } from './utils/priority'
import { getBusinessFormDisplayTitle, getRowDisplayTitle, getTaskDisplayName } from './utils/processDisplay'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { dict, getLabel } = useDict('flow_todo_status', 'flow_priority')
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

const urgentCount = ref(0)
const selectedTaskKeys = ref([])

// 抽屉状态
const showDrawer = ref(false)
const currentTask = ref(null)
const approvalHistory = ref([])

// 业务自定义表单
const taskFormInfo = ref(null)
const formInfoLoading = ref(false)
const dynamicFormRef = ref(null)
const dynamicFormData = ref({})
const dynamicFormSchema = computed(() => formCreateToAiSchema(taskFormInfo.value?.formJson || []))
const useDynamicForm = computed(() => taskFormInfo.value?.formType === 'dynamic' && dynamicFormSchema.value.length > 0)
const businessFormContext = ref(null)
const businessFormData = ref({})
const businessChildFormData = ref({})
const businessFormRef = ref(null)
const businessFormLoading = ref(false)
const businessFormSaving = ref(false)
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
const businessFormHasWritableFields = computed(() => hasWritableBusinessFormFields(businessFormContext.value))
const businessCodeFormUrl = computed(() => businessFormContext.value?.formUrl || businessFormContext.value?.formRef?.formUrl || '')
const businessFormGridCols = computed(() => Math.max(1, Number(businessFormContext.value?.gridCols || 1)))
const businessFormLabelPlacement = computed(() => ['left', 'top'].includes(businessFormContext.value?.labelPlacement)
  ? businessFormContext.value.labelPlacement
  : 'left')
const businessFormLabelWidth = computed(() => businessFormContext.value?.labelWidth || '100')
const useBusinessCodeComponentForm = computed(() => useBusinessCodeForm.value && Boolean(businessCodeFormUrl.value))
const useComponentTaskForm = computed(() => useExternalForm.value || useBusinessCodeComponentForm.value)
const componentTaskFormUrl = computed(() => useBusinessCodeComponentForm.value ? businessCodeFormUrl.value : taskFormInfo.value?.formUrl)
const componentTaskFormInfo = computed(() => ({
  taskId: businessFormContext.value?.taskId || taskFormInfo.value?.taskId,
  businessKey: businessFormContext.value?.businessKey || taskFormInfo.value?.businessKey,
  processInstanceId: businessFormContext.value?.processInstanceId || taskFormInfo.value?.processInstanceId,
  taskDefKey: businessFormContext.value?.taskDefKey || taskFormInfo.value?.taskDefKey,
  processDefKey: businessFormContext.value?.processDefKey || taskFormInfo.value?.processDefKey,
}))
const businessFormFieldPermissions = computed(() => pickFirstNonEmptyFieldPermissions([
  businessFormContext.value?.fieldPermissions,
  taskFormInfo.value?.fieldPermissions,
  taskFormInfo.value?.formFieldPermissions,
]))
const dynamicFormFieldPermissions = computed(() => pickFirstNonEmptyFieldPermissions([
  taskFormInfo.value?.fieldPermissions,
  taskFormInfo.value?.formFieldPermissions,
]))
const businessFormRenderContext = computed(() => ({
  task: currentTask.value,
  taskFormInfo: taskFormInfo.value,
  businessFormContext: businessFormContext.value,
  formAssets: businessFormContext.value?.formAssets || [],
}))
const taskPolicySource = computed(() => taskFormInfo.value || businessFormContext.value || {})
const canApprove = computed(() => taskPolicySource.value?.allowApprove !== false)
const canReject = computed(() => taskPolicySource.value?.allowReject !== false)
const canDelegate = computed(() => taskPolicySource.value?.allowDelegate !== false)
const canReturn = computed(() => taskPolicySource.value?.allowReturn === true)
const canTerminate = computed(() => taskPolicySource.value?.allowTerminate === true)
const requireComment = computed(() => taskPolicySource.value?.requireComment !== false)
const requireSignature = computed(() => taskPolicySource.value?.requireSignature === true)
const approvalPolicy = computed(() => ({
  allowApprove: canApprove.value,
  allowReject: canReject.value,
  allowDelegate: canDelegate.value,
  allowReturn: canReturn.value,
  allowTerminate: canTerminate.value,
  requireComment: requireComment.value,
  requireSignature: requireSignature.value,
}))

// 审批表单
const approveLoading = ref(false)
const approveForm = reactive({ action: '', comment: '', signature: '' })
const approveSignatureRef = ref(null)
const approveSignatureKey = ref(0)
const claimLoadingTaskId = ref('')
const quickActionVisible = ref(false)
const quickActionLoading = ref(false)
const quickActionType = ref('approve')
const quickActionTargets = ref([])
const quickActionForm = reactive({ comment: '' })
const quickActionTitle = computed(() => quickActionType.value === 'approve' ? '同意审批' : '驳回审批')

// 转办
const showDelegateModal = ref(false)
const showUserSelectModal = ref(false)
const delegateLoading = ref(false)
const delegateTargetUser = ref(null)
const delegateForm = reactive({ comment: '', signature: '' })
const delegateSignatureRef = ref(null)
const delegateSignatureKey = ref(0)
const routeTaskOpening = ref(false)

const statusOptions = computed(() => toNumberOptions(dict.value.flow_todo_status))
const isApprovalBusy = computed(() => approveLoading.value || delegateLoading.value || businessFormSaving.value || Boolean(claimLoadingTaskId.value))

// 优先级
function getPriorityClass(p) {
  return getFlowPriorityClass(p)
}
function getPriorityText(p) {
  if (!shouldShowFlowPriority(p))
    return ''
  const level = resolveFlowPriorityLevel(p)
  const label = getLabel('flow_priority', level)
  return String(label) === String(level) ? FLOW_PRIORITY_LABEL_FALLBACK[level] : label
}

function getProcessDisplayName(task) {
  return task?.processName || task?.processTitle || task?.modelName || task?.businessType || '-'
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

function resetBusinessTaskForm() {
  businessFormContext.value = null
  businessFormData.value = {}
  businessChildFormData.value = {}
  businessFormLoading.value = false
  businessFormSaving.value = false
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

function buildBusinessTaskFormQuery(row = {}, formInfo = {}) {
  const taskId = formInfo.taskId || row.taskId || row.id
  return compactParams({
    taskId,
    businessKey: formInfo.businessKey || row.businessKey,
    processInstanceId: formInfo.processInstanceId || row.processInstanceId,
    processDefKey: formInfo.processDefKey,
    taskDefKey: formInfo.taskDefKey || row.taskDefKey || row.taskDefinitionKey,
    objectCode: formInfo.objectCode || row.objectCode,
    recordId: formInfo.recordId || row.recordId,
    formKey: formInfo.formKey,
  })
}

function compactParams(source = {}) {
  const result = {}
  Object.entries(source).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '')
      result[key] = value
  })
  return result
}

function hasBusinessTaskFormQuery(query = {}) {
  return Boolean(query.taskId || query.processInstanceId || query.businessKey || (query.objectCode && query.recordId))
}

function hasWritableBusinessFormFields(context) {
  return Array.isArray(context?.fields) && context.fields.some(field =>
    field?.writable === true && field?.readonly !== true && field?.disabled !== true,
  )
}

async function loadBusinessTaskFormContext(row, formInfo) {
  businessFormContext.value = null
  businessFormData.value = {}
  businessChildFormData.value = {}

  const query = buildBusinessTaskFormQuery(row, formInfo)
  if (!hasBusinessTaskFormQuery(query))
    return null

  businessFormLoading.value = true
  try {
    const res = await businessTaskFormContext(query)
    if (res.code !== 200) {
      console.error('加载业务表单上下文失败', res.message)
      return null
    }
    businessFormContext.value = res.data || null
    businessFormData.value = normalizeBusinessRecordData(res.data?.recordData)
    businessChildFormData.value = normalizeBusinessChildrenData(res.data?.recordData)
    logBusinessApprovalChildren('todo', res.data?.recordData)
    return businessFormContext.value
  }
  catch (error) {
    console.error('加载业务表单上下文失败', error)
    return null
  }
  finally {
    businessFormLoading.value = false
  }
}

async function loadTaskFormInfo(taskId) {
  if (!taskId)
    return null
  try {
    const res = await flowApi.getTaskFormInfo(taskId)
    if (res.code === 200) {
      taskFormInfo.value = res.data
      dynamicFormData.value = { ...(res.data?.variables || {}) }
      return taskFormInfo.value
    }
  }
  catch (error) {
    console.error('加载表单信息失败', error)
  }
  return null
}

function isConfiguredBusinessTaskForm(context) {
  return context?.configured === true && ['business-object', 'business-code'].includes(context?.formType)
}

function buildBusinessTaskFormSavePayload() {
  const context = businessFormContext.value || {}
  return compactParams({
    taskId: context.taskId || taskFormInfo.value?.taskId || currentTask.value?.taskId || currentTask.value?.id,
    businessKey: context.businessKey || taskFormInfo.value?.businessKey,
    processInstanceId: context.processInstanceId || taskFormInfo.value?.processInstanceId || currentTask.value?.processInstanceId,
    processDefKey: context.processDefKey || taskFormInfo.value?.processDefKey || currentTask.value?.processDefKey || currentTask.value?.processDefinitionKey,
    taskDefKey: context.taskDefKey || taskFormInfo.value?.taskDefKey || currentTask.value?.taskDefKey || currentTask.value?.taskDefinitionKey,
    objectCode: context.objectCode || taskFormInfo.value?.objectCode || currentTask.value?.objectCode,
    recordId: context.recordId || taskFormInfo.value?.recordId || currentTask.value?.recordId,
    formKey: context.formKey || taskFormInfo.value?.formKey,
    data: { ...businessFormData.value },
  })
}

async function saveBusinessTaskFormFields(options = {}) {
  if (!useBusinessManagedForm.value || !businessFormHasWritableFields.value)
    return null

  const { validate = true, silent = true } = options
  businessFormSaving.value = true
  try {
    if (validate)
      await businessFormRef.value?.validate?.()

    const res = await saveBusinessTaskFormContext(buildBusinessTaskFormSavePayload())
    if (res.code !== 200)
      throw new Error(res.message || '业务字段保存失败')

    businessFormContext.value = res.data || businessFormContext.value
    businessFormData.value = normalizeBusinessRecordData(businessFormContext.value?.recordData || businessFormData.value)
    businessChildFormData.value = normalizeBusinessChildrenData(businessFormContext.value?.recordData)
    if (!silent)
      window.$message.success('修改已暂存')
    return businessFormContext.value
  }
  catch (error) {
    if (!silent) {
      window.$message.error(error?.message || '业务字段保存失败')
      return null
    }
    throw error
  }
  finally {
    businessFormSaving.value = false
  }
}

async function persistBusinessTaskFormBeforeAction(action) {
  if (!['approve', 'reject'].includes(action))
    return
  if (!useBusinessManagedForm.value || !businessFormHasWritableFields.value)
    return
  await saveBusinessTaskFormFields({ validate: true, silent: true })
}

function buildBusinessTaskActionPayload(action, comment, signature, variables = {}) {
  const context = businessFormContext.value || {}
  return compactParams({
    action,
    taskId: context.taskId || taskFormInfo.value?.taskId || currentTask.value?.taskId || currentTask.value?.id,
    businessKey: context.businessKey || taskFormInfo.value?.businessKey || currentTask.value?.businessKey,
    processInstanceId: context.processInstanceId || taskFormInfo.value?.processInstanceId || currentTask.value?.processInstanceId,
    processDefKey: context.processDefKey || taskFormInfo.value?.processDefKey || currentTask.value?.processDefKey || currentTask.value?.processDefinitionKey,
    taskDefKey: context.taskDefKey || taskFormInfo.value?.taskDefKey || currentTask.value?.taskDefKey || currentTask.value?.taskDefinitionKey,
    objectCode: context.objectCode || taskFormInfo.value?.objectCode || currentTask.value?.objectCode,
    recordId: context.recordId || taskFormInfo.value?.recordId || currentTask.value?.recordId,
    formKey: context.formKey || taskFormInfo.value?.formKey,
    userId: userStore.userId,
    comment,
    signature,
    variables,
    data: { ...businessFormData.value },
  })
}

async function submitTaskAction(action, comment, signature, variables = {}) {
  if (isConfiguredBusinessTaskForm(businessFormContext.value)) {
    return completeBusinessTaskAction(buildBusinessTaskActionPayload(action, comment, signature, variables))
  }
  const api = resolveActionApi(action)
  return api({
    taskId: currentTask.value.taskId || currentTask.value.id,
    userId: userStore.userId,
    comment,
    signature,
    variables,
  })
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
      source: 'flowTodo',
    }),
  })
}

async function loadQuickBusinessTaskFormContext(row, formInfo) {
  const query = buildBusinessTaskFormQuery(row, formInfo)
  if (!hasBusinessTaskFormQuery(query))
    return null
  const res = await businessTaskFormContext(query)
  if (res.code !== 200)
    throw new Error(res.message || '业务表单策略加载失败')
  return res.data || null
}

async function openDrawer(row) {
  currentTask.value = row
  approveForm.comment = ''
  approveForm.action = ''
  approveForm.signature = ''
  approveSignatureKey.value += 1
  approvalHistory.value = []
  taskFormInfo.value = null
  dynamicFormData.value = {}
  resetBusinessTaskForm()
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

  const taskId = row.taskId || row.id
  if (taskId) {
    formInfoLoading.value = true
    promises.push(
      loadBusinessTaskFormContext({}, { taskId })
        .then((context) => {
          if (isConfiguredBusinessTaskForm(context))
            return null
          return loadTaskFormInfo(taskId)
        })
        .finally(() => { formInfoLoading.value = false }),
    )
  }

  await Promise.all(promises)
}

async function handleExternalFormSubmit({ action, comment, signature, variables }) {
  const approvalSignature = signature || variables?.signature
  if (!canRunAction(action))
    return
  if (!validateApprovalInput(comment, approvalSignature))
    return

  approveForm.action = action
  approveLoading.value = true
  try {
    const res = await submitTaskAction(action, comment, approvalSignature, variables)
    if (res.code === 200) {
      window.$message.success(getActionSuccessText(action))
      showDrawer.value = false
      loadData()
    }
    else {
      window.$message.error(res.message || '操作失败')
    }
  }
  catch (error) {
    window.$message.error(error?.message || '操作失败')
  }
  finally {
    approveLoading.value = false
    approveForm.action = ''
  }
}

function canRunAction(action) {
  const allowed = {
    approve: canApprove.value,
    reject: canReject.value,
    return: canReturn.value,
    terminate: canTerminate.value,
    delegate: canDelegate.value,
  }
  if (allowed[action] === false) {
    window.$message.warning('当前节点不允许执行该操作')
    return false
  }
  return true
}

function hasSignatureValue(signature, signatureRef) {
  return Boolean(signature?.trim()) || Boolean(signatureRef?.hasSignature?.())
}

async function resolveSignature(signatureRef, signature) {
  if (!requireSignature.value)
    return signature || ''
  if (!signatureRef?.upload)
    return signature || ''

  try {
    return await signatureRef.upload()
  }
  catch (error) {
    throw new Error(error?.message || '签名图片保存失败')
  }
}

function validateApprovalInput(comment, signature, signatureRef = null) {
  if (requireComment.value && !comment?.trim()) {
    window.$message.warning('请输入审批意见')
    return false
  }
  if (requireSignature.value && !hasSignatureValue(signature, signatureRef)) {
    window.$message.warning('请完成手写签名')
    return false
  }
  return true
}

function resolveActionApi(action) {
  const apiMap = {
    approve: flowApi.approveTask,
    reject: flowApi.rejectTask,
    return: flowApi.returnTask,
    terminate: flowApi.terminateTask,
  }
  return apiMap[action] || flowApi.approveTask
}

function getActionSuccessText(action) {
  const textMap = {
    approve: '审批通过',
    reject: '已驳回',
    return: '已退回',
    terminate: '流程已终结',
  }
  return textMap[action] || '操作成功'
}

async function submitApprove(action) {
  if (!canRunAction(action))
    return
  if (!validateApprovalInput(approveForm.comment, approveForm.signature, approveSignatureRef.value))
    return
  approveForm.action = action
  approveLoading.value = true
  try {
    const signature = await resolveSignature(approveSignatureRef.value, approveForm.signature)
    approveForm.signature = signature
    const variables = await collectDynamicFormVariables(action)
    await persistBusinessTaskFormBeforeAction(action)
    const res = await submitTaskAction(action, approveForm.comment, signature, variables)
    if (res.code === 200) {
      window.$message.success(getActionSuccessText(action))
      showDrawer.value = false
      loadData()
    }
    else {
      window.$message.error(res.message || '操作失败')
    }
  }
  catch (error) {
    window.$message.error(error?.message || '操作失败')
  }
  finally {
    approveLoading.value = false
    approveForm.action = ''
  }
}

function isActionLoading(action) {
  return approveLoading.value && approveForm.action === action
}

function resolveQuickActionTargets(targets = []) {
  return targets
    .map((target) => {
      if (target && typeof target === 'object')
        return target
      return dataSource.value.find(row => String(row.id) === String(target) || String(row.taskId) === String(target))
    })
    .filter(Boolean)
}

function openQuickAction(action, targets) {
  const resolvedTargets = resolveQuickActionTargets(targets)
  if (resolvedTargets.length === 0) {
    window.$message.warning('请选择待办任务')
    return
  }
  quickActionType.value = action
  quickActionTargets.value = resolvedTargets
  quickActionForm.comment = action === 'approve' ? '同意' : '驳回'
  quickActionVisible.value = true
}

function isCandidateTask(row) {
  return row?.status === 0 && !row?.assignee
}

async function claimTaskBeforeQuickAction(row, taskId) {
  if (!isCandidateTask(row))
    return
  const res = await flowApi.claimTask(taskId, userStore.userId)
  if (res.code !== 200)
    throw new Error(res.message || '签收失败')
}

function assertQuickActionAllowed(action, formInfo, businessFormContext = null) {
  const businessManaged = businessFormContext?.configured === true
    && ['business-object', 'business-code'].includes(businessFormContext?.formType)
  if (action === 'approve' && formInfo?.allowApprove === false)
    throw new Error('当前节点不允许同意')
  if (action === 'reject' && formInfo?.allowReject === false)
    throw new Error('当前节点不允许驳回')
  if (formInfo?.requireSignature === true)
    throw new Error('需要手写签名，请进入详情处理')
  if (action === 'approve' && !businessManaged && formInfo?.formType === 'dynamic' && formInfo?.formJson)
    throw new Error('需要填写节点表单，请进入详情处理')
  if (action === 'approve' && !businessManaged && formInfo?.formType === 'external' && formInfo?.formUrl)
    throw new Error('需要填写业务表单，请进入详情处理')
  if (action === 'approve' && businessFormContext?.configured === true && businessFormContext?.formType === 'business-code')
    throw new Error('需要进入业务表单处理')
  if (action === 'approve' && businessFormContext?.configured === true && hasWritableBusinessFormFields(businessFormContext))
    throw new Error('需要填写业务表单，请进入详情处理')
}

async function executeQuickAction(action, row, comment) {
  const taskId = row.taskId || row.id
  if (!taskId)
    throw new Error('缺少任务ID')

  await claimTaskBeforeQuickAction(row, taskId)

  const formRes = await flowApi.getTaskFormInfo(taskId)
  if (formRes.code !== 200)
    throw new Error(formRes.message || '审批策略加载失败')

  const formInfo = formRes.data || {}
  const businessContext = await loadQuickBusinessTaskFormContext(row, formInfo)
  assertQuickActionAllowed(action, formInfo, businessContext)

  const res = isConfiguredBusinessTaskForm(businessContext)
    ? await completeBusinessTaskAction(compactParams({
        action,
        taskId,
        businessKey: businessContext.businessKey || row.businessKey,
        processInstanceId: businessContext.processInstanceId || row.processInstanceId,
        processDefKey: businessContext.processDefKey || formInfo.processDefKey || row.processDefKey || row.processDefinitionKey,
        taskDefKey: businessContext.taskDefKey || formInfo.taskDefKey || row.taskDefKey || row.taskDefinitionKey,
        objectCode: businessContext.objectCode || row.objectCode,
        recordId: businessContext.recordId || row.recordId,
        formKey: businessContext.formKey || formInfo.formKey,
        userId: userStore.userId,
        comment,
        variables: formInfo.variables || undefined,
      }))
    : await (action === 'approve' ? flowApi.approveTask : flowApi.rejectTask)({
        taskId,
        userId: userStore.userId,
        comment,
        variables: formInfo.variables || undefined,
      })
  if (res.code !== 200)
    throw new Error(res.message || '操作失败')
}

async function submitQuickAction() {
  const comment = quickActionForm.comment.trim()
  if (!comment) {
    window.$message.warning(quickActionType.value === 'approve' ? '请输入同意意见' : '请输入驳回原因')
    return
  }

  quickActionLoading.value = true
  const action = quickActionType.value
  const targets = [...quickActionTargets.value]
  const errors = []
  let successCount = 0

  try {
    for (const row of targets) {
      try {
        await executeQuickAction(action, row, comment)
        successCount += 1
      }
      catch (error) {
        const taskName = getTaskDisplayName(row, row.title || row.taskId || row.id || '未知任务')
        errors.push(`${taskName}：${error?.message || '操作失败'}`)
      }
    }

    if (successCount > 0) {
      window.$message.success(`${getActionSuccessText(action)} ${successCount} 条`)
      selectedTaskKeys.value = []
      quickActionVisible.value = false
      await loadData()
    }

    if (errors.length > 0) {
      const content = errors.slice(0, 6).join('\n')
      if (window.$dialog?.warning) {
        window.$dialog.warning({
          title: successCount > 0 ? '部分任务未处理' : '任务未处理',
          content,
          positiveText: '知道了',
        })
      }
      else {
        window.$message.warning(errors[0])
      }
    }
  }
  finally {
    quickActionLoading.value = false
  }
}

async function collectDynamicFormVariables(action) {
  if (!useDynamicForm.value || !dynamicFormRef.value)
    return undefined
  if (action === 'approve') {
    await dynamicFormRef.value.validate()
  }
  return dynamicFormRef.value.getData?.() || dynamicFormRef.value.getFormData?.() || { ...dynamicFormData.value }
}

function handleDelegate() {
  delegateTargetUser.value = null
  delegateForm.comment = ''
  delegateForm.signature = ''
  delegateSignatureKey.value += 1
  showDelegateModal.value = true
}

function handleUserSelected(user) {
  delegateTargetUser.value = user
}

async function submitDelegate() {
  if (!canRunAction('delegate'))
    return
  if (!delegateTargetUser.value) {
    window.$message.warning('请选择转办人')
    return
  }
  if (!validateApprovalInput(delegateForm.comment, delegateForm.signature, delegateSignatureRef.value))
    return
  delegateLoading.value = true
  try {
    const signature = await resolveSignature(delegateSignatureRef.value, delegateForm.signature)
    delegateForm.signature = signature
    const res = await flowApi.delegateTask({
      taskId: currentTask.value.taskId,
      userId: String(userStore.userId),
      targetUserId: String(delegateTargetUser.value.id),
      comment: delegateForm.comment,
      signature,
    })
    if (res.code === 200) {
      window.$message.success('转办成功')
      showDelegateModal.value = false
      showDrawer.value = false
      loadData()
    }
    else {
      window.$message.error(res.message || '转办失败')
    }
  }
  catch (error) {
    window.$message.error(error?.message || '转办失败')
  }
  finally {
    delegateLoading.value = false
  }
}

async function handleClaim(row) {
  const taskId = row?.taskId || row?.id
  if (!taskId || claimLoadingTaskId.value)
    return
  claimLoadingTaskId.value = String(taskId)
  try {
    const res = await flowApi.claimTask(taskId, userStore.userId)
    if (res.code === 200) {
      window.$message.success('签收成功')
      if (currentTask.value && (currentTask.value.taskId === taskId || currentTask.value.id === taskId)) {
        currentTask.value.status = 1
        currentTask.value.assignee = userStore.userId
      }
      loadData()
    }
    else {
      window.$message.error(res.message || '签收失败')
    }
  }
  catch {
    window.$message.error('签收失败')
  }
  finally {
    claimLoadingTaskId.value = ''
  }
}

function isClaimingTask(row) {
  const taskId = row?.taskId || row?.id
  return Boolean(taskId) && claimLoadingTaskId.value === String(taskId)
}

async function loadData() {
  loading.value = true
  try {
    const res = await flowApi.getTodoTasks({
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
      urgentCount.value = dataSource.value.filter(r => isUrgentFlowPriority(r.priority)).length
    }
  }
  catch {
    console.error('加载待办任务失败')
  }
  finally {
    loading.value = false
  }
}

function getRouteTaskId() {
  const taskId = route.query.taskId
  if (Array.isArray(taskId))
    return taskId[0] ? String(taskId[0]) : ''
  return taskId ? String(taskId) : ''
}

async function openTaskFromRoute() {
  const taskId = getRouteTaskId()
  if (!taskId || routeTaskOpening.value)
    return

  if (showDrawer.value && currentTask.value?.taskId === taskId)
    return

  routeTaskOpening.value = true
  try {
    const existing = dataSource.value.find(row => row.taskId === taskId || row.id === taskId)
    if (existing) {
      await openDrawer(existing)
      return
    }

    const res = await flowApi.getTaskDetail(taskId)
    if (res.code === 200 && res.data) {
      await openDrawer(res.data)
    }
    else {
      window.$message.warning('待办任务不存在或已处理')
      clearRouteTaskId()
    }
  }
  catch {
    window.$message.warning('待办任务不存在或已处理')
    clearRouteTaskId()
  }
  finally {
    routeTaskOpening.value = false
  }
}

function clearRouteTaskId() {
  if (!getRouteTaskId())
    return
  const query = { ...route.query }
  delete query.taskId
  delete query.source
  delete query.t
  router.replace({ path: route.path, query })
}

async function loadCategories() {
  try {
    const res = await flowApi.getCategoryTreeSelect(false)
    if (res.code === 200 && res.data) {
      categoryTreeOptions.value = buildFlowCategoryTreeOptions(res.data)
    }
  }
  catch {
    console.error('加载分类失败')
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

onMounted(async () => {
  loadCategories()
  await loadData()
  await openTaskFromRoute()
})

watch(
  () => route.fullPath,
  async () => {
    if (route.name === 'ApplicationPortal' || route.path === '/flow/todo' || route.path === '/workspace/todo')
      await openTaskFromRoute()
  },
)
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
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
}

.quick-stats {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #fef3c7;
  color: #b45309;
}

.stat-item.urgent {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  color: #b91c1c;
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

.quick-action-modal {
  width: min(520px, calc(100vw - 32px));
}

.quick-action-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.quick-action-summary {
  color: #334155;
  font-size: 14px;
  line-height: 22px;
}

.quick-action-summary strong {
  color: #0f766e;
}

.quick-action-tip {
  color: #64748b;
  font-size: 12px;
  line-height: 20px;
}

.quick-action-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.search-btn,
.reset-btn {
  display: flex;
  align-items: center;
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

:deep(.task-status-pill.todo-status-pending) {
  background: #fff7ed;
  color: #c2410c;
  box-shadow: inset 0 0 0 1px #fed7aa;
}

:deep(.task-status-pill.todo-status-active) {
  background: #ecfdf5;
  color: #047857;
  box-shadow: inset 0 0 0 1px #bbf7d0;
}

:deep(.approval-status-mark.todo-status-pending) {
  background: #f97316;
}

:deep(.approval-status-mark.todo-status-active) {
  background: #0f766e;
}

:deep(.status-tag-mini) {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
:deep(.status-tag-mini.pending) {
  background: #fef3c7;
  color: #b45309;
}
:deep(.status-tag-mini.claimed) {
  background: #dbeafe;
  color: #1e40af;
}

:deep(.priority-tag-mini) {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: #f1f5f9;
  color: #64748b;
}
:deep(.priority-tag-mini.high) {
  background: #fef3c7;
  color: #b45309;
}
:deep(.priority-tag-mini.urgent) {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  color: #b91c1c;
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
.status-dot.pending {
  background: #f59e0b;
}
.status-dot.claimed {
  background: #3b82f6;
}

.drawer-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.drawer-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}
.status-tag.pending {
  background: #fef3c7;
  color: #b45309;
}
.status-tag.claimed {
  background: #dbeafe;
  color: #1e40af;
}

.priority-tag {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}
.priority-tag.high {
  background: #fef3c7;
  color: #b45309;
}
.priority-tag.urgent {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  color: #b91c1c;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-height: calc(100vh - 178px);
  overflow-y: auto;
  padding-bottom: 20px;
  padding: 18px 20px 20px;
}

.drawer-tabs {
  flex: 0 0 auto;
}

.tab-badge {
  background: #0369a1;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  margin-left: 6px;
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

.user-item {
  align-items: flex-start;
}

.user-display {
  display: flex;
  align-items: center;
  gap: 8px;
}

.diagram-pane {
  min-height: 200px;
}

.approve-section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 16px;
}

.approve-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 12px;
}

.form-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
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

.approval-form-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #172033;
  font-size: 14px;
  font-weight: 700;
}

.approval-form-title small {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
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

.dynamic-form-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.dynamic-form-title {
  font-size: 14px;
  font-weight: 700;
  color: #172033;
}

.dynamic-form-desc {
  margin-top: 2px;
  font-size: 12px;
  color: #667085;
}

.dynamic-form-key {
  max-width: 180px;
  padding: 3px 8px;
  border: 1px solid #d7dde7;
  border-radius: 999px;
  background: #fff;
  color: #475467;
  font-size: 12px;
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.delegate-user-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.delegate-user-display {
  flex: 1;
  min-height: 36px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 12px;
  background: #f8fafc;
}

.delegate-user-name {
  font-weight: 600;
  color: #0f172a;
}

.delegate-user-id {
  font-size: 12px;
  color: #64748b;
}

.delegate-placeholder {
  color: #94a3b8;
  font-size: 13px;
}

.flow-task-detail-modal {
  width: min(1120px, calc(100vw - 32px));
}

@media (max-width: 760px) {
  .flow-task-detail-modal {
    width: 100vw;
    height: 100vh;
    margin: 0;
  }

  .drawer-header,
  .dynamic-form-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .drawer-body {
    max-height: calc(100vh - 126px);
    padding: 14px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .action-buttons,
  .delegate-user-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
