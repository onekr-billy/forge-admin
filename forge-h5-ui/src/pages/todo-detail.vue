<template>
  <view class="todo-detail-page">
    <AiFeedbackHost />
    <scroll-view class="detail-scroll" scroll-y :show-scrollbar="true">
      <TodoDetailSkeleton v-if="loading" />
      <template v-else-if="task">
        <TodoTaskSummary :task="task" @refresh="refresh" />

        <view class="detail-content">
          <!-- 动态业务表单 -->
          <view v-if="formLoading" class="content-panel detail-loading-card">
            <TodoDetailSkeleton form-only />
          </view>
          <view v-else-if="blockedReason" class="blocked-panel">
            <view class="blocked-panel__icon">
              <AiIcon icon="/static/icons/ai-icon/info.svg" color="#3b82f6" size="md" />
            </view>
            <view class="blocked-panel__copy">
              <text class="blocked-title">请在 PC 端处理</text>
              <text class="blocked-copy">{{ blockedReason }}</text>
            </view>
          </view>

          <view v-else-if="showBusinessFormPanel" class="content-panel">
            <view class="detail-section-head">
              <view class="detail-section-heading">
                <view class="detail-section-icon"><AiIcon icon="/static/icons/ai-icon/file-text.svg" color="#3b82f6" size="sm" /></view>
                <view>
                  <text class="detail-section-title">表单信息</text>
                  <text class="detail-section-desc">当前审批节点对应的业务内容</text>
                </view>
              </view>
              <button
                v-if="businessFormHasWritableFields"
                class="save-form-button"
                :disabled="actionLoading || formSaving"
                @click="saveBusinessFields"
              >
                {{ formSaving ? '暂存中' : '暂存修改' }}
              </button>
            </view>
            <view v-if="businessProviderUnavailable" class="form-provider-notice">
              <text>流程服务未加载该业务表单 Provider，当前仅能展示表单字段结构；部署 Provider 后会自动加载实际数据和节点权限。</text>
            </view>
            <PageSectionRenderer
              v-if="hasLowcodeForm"
              :sections="pageSections"
              :main-fields="mainFields"
              :main-nodes="mainNodes"
              :main-data="mainData"
              :children="allChildren"
              :child-data="childData"
              :mode="formMode"
              :dict-options="dictOptions"
              :runtime-context="runtimeContext"
              :flow-interaction="flowInteraction"
              :current-flow-node-key="currentFlowNodeKey"
              @set-main-form-ref="setMainFormRef"
              @set-child-form-ref="setChildFormRef"
              @add-child-row="addBusinessChildRow"
              @remove-child-row="removeBusinessChildRow"
            />
            <view v-else-if="formSchemaUnavailable" class="form-schema-notice">
              <text>该流程未返回可展示的业务字段配置，已隐藏内部字段和技术标识。</text>
            </view>

            <view v-if="responsibilityDescription || approvalPoints.length" class="approval-duty-panel">
              <view v-if="responsibilityDescription" class="duty-block">
                <text class="form-label">审批职责</text>
                <text class="duty-copy">{{ responsibilityDescription }}</text>
              </view>
              <view v-if="approvalPoints.length" class="duty-block">
                <text class="form-label">审批要点</text>
                <view
                  v-for="point in approvalPoints"
                  :key="point.id"
                  class="approval-point-row"
                  @click="toggleApprovalPoint(point)"
                >
                  <text class="approval-point-check">{{ approvalPointChecks[point.id] ? '☑' : '☐' }}</text>
                  <text class="approval-point-copy">{{ point.content }}</text>
                  <text class="approval-point-tag">{{ point.required ? '必审' : '非必审' }}</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 流程进度与审批记录在同一卡片连续展示 -->
          <view class="workflow-panel">
            <view class="detail-section-head">
              <view class="detail-section-heading">
                <view class="detail-section-icon"><AiIcon icon="/static/icons/ai-icon/check-circle.svg" color="#3b82f6" size="sm" /></view>
                <view>
                  <text class="detail-section-title">审批流程</text>
                  <text class="detail-section-desc">节点进度与办理记录</text>
                </view>
              </view>
            </view>
            <view class="trace-sections">
              <TodoFlowTrace mode="process" :loading="diagramLoading" :items="processNodes" />
              <TodoFlowTrace mode="history" :loading="historyLoading" :items="history" />
            </view>
          </view>

          <!-- 处理模式下才展示审批意见和签名 -->
          <view v-if="!readonlyMode" class="approval-comment-panel">
            <view class="detail-section-head">
              <view class="detail-section-heading">
                <view class="detail-section-icon"><AiIcon icon="/static/icons/ai-icon/edit-3.svg" color="#3b82f6" size="sm" /></view>
                <view>
                  <text class="detail-section-title">审批意见<text v-if="requireComment" class="required-mark"> *</text></text>
                  <text class="detail-section-desc">填写本次处理意见</text>
                </view>
              </view>
            </view>
            <view class="comment-row">
              <FlowCommentPhraseInput
                v-model="comment"
                maxlength="500"
                min-height="96px"
                :placeholder="requireComment ? '请输入审批意见' : '请输入审批意见（选填）'"
              />
            </view>
            <view v-if="requireSignature" class="comment-row signature-row">
              <text class="form-label">手写签名<text class="required-mark"> *</text></text>
              <AiSignaturePad ref="approvalSignatureRef" v-model="signature" />
            </view>
          </view>
        </view>
      </template>
      <view v-else class="page-hint">待办不存在或已处理</view>
    </scroll-view>

    <view v-if="task && !readonlyMode" class="action-bar">
      <AiButton v-if="isCandidateTask" block size="sm" :loading="claimLoading" @click="claimTask">签收后处理</AiButton>
      <template v-else>
        <button v-if="hasMoreActions" class="action-more-button" :disabled="Boolean(blockedReason) || actionLoading" @click="moreVisible = true">
          <AiIcon icon="/static/icons/ai-icon/more-horizontal.svg" color="#475569" size="sm" />
          <text>更多</text>
        </button>
        <AiButton v-if="canReject" size="sm" variant="danger" :disabled="Boolean(blockedReason) || actionLoading" @click="canChooseReturnTarget ? openRejectTarget() : submitAction('reject')">驳回</AiButton>
        <AiButton v-if="canApprove" size="sm" :loading="actionLoading && pendingAction === 'approve'" :disabled="Boolean(blockedReason) || actionLoading" @click="submitAction('approve')">同意</AiButton>
      </template>
    </view>

    <AiPopupSheet v-model="rejectTargetVisible" title="选择驳回节点" description="流程会从所选节点继续，而不是整单结束">
      <view class="return-target-list">
        <button
          v-for="option in returnTargetOptions"
          :key="option.value"
          class="return-target-item"
          :class="{ active: selectedReturnTarget === option.value }"
          @click="selectedReturnTarget = option.value"
        >
          {{ option.label }}
        </button>
      </view>
      <view class="delegate-comment" style="margin-top: 24rpx;">
        <AiButton block :disabled="!selectedReturnTarget" :loading="actionLoading && pendingAction === 'return'" @click="submitAction('return')">确认驳回</AiButton>
      </view>
    </AiPopupSheet>

    <AiPopupSheet v-model="moreVisible" title="更多操作">
      <view class="more-action-grid">
        <button v-if="canRejectToStart" class="more-action-item warning" @click="submitMoreAction('rejectToStart')">
          <view class="more-action-item__icon"><AiIcon icon="/static/icons/ai-icon/rotate-ccw.svg" color="#ff7d00" size="sm" /></view>
          <text>退回发起人修改</text>
        </button>
        <button v-if="canDelegate" class="more-action-item" @click="openDelegate">
          <view class="more-action-item__icon"><AiIcon icon="/static/icons/ai-icon/user-plus.svg" color="#3b82f6" size="sm" /></view>
          <text>转办</text>
        </button>
        <button v-if="canTerminate" class="more-action-item danger" @click="submitMoreAction('terminate')">
          <view class="more-action-item__icon"><AiIcon icon="/static/icons/ai-icon/x-circle.svg" color="#ef4444" size="sm" /></view>
          <text>终结流程</text>
        </button>
      </view>
      <template #footer>
        <button class="more-cancel-button" @click="moreVisible = false">取消</button>
      </template>
    </AiPopupSheet>

    <AiPopupSheet v-model="delegateVisible" title="转办任务" description="选择处理人后，再确认转办" max-height="90vh" body-max-height="calc(90vh - 230rpx - env(safe-area-inset-bottom))">
      <view class="delegate-search"><AiSearchBar v-model="userKeyword" placeholder="搜索姓名或用户名" @search="loadUsers" @clear="loadUsers" /></view>
      <view v-if="delegateUser" class="delegate-choice">
        <view class="delegate-choice__avatar"><AiAuthImage v-if="delegateUser.avatar" :src="delegateUser.avatar" mode="aspectFill" /><text v-else>{{ userInitial(delegateUser) }}</text></view>
        <view class="delegate-choice__copy"><text>已选择</text><text>{{ delegateUserName(delegateUser) }}</text></view>
        <AiIcon icon="/static/icons/ai-icon/check-circle.svg" color="#3b82f6" size="md" />
      </view>
      <view class="user-list">
        <AiListSkeleton v-if="usersLoading" :rows="3" compact />
        <button v-for="user in users" v-else :key="user.id" class="user-row" :class="{ active: isDelegateUserSelected(user) }" @click.stop="selectDelegateUser(user)">
          <view class="user-avatar"><AiAuthImage v-if="user.avatar" :src="user.avatar" mode="aspectFill" /><text v-else>{{ userInitial(user) }}</text></view>
          <view class="user-copy">
            <text class="user-name">{{ delegateUserName(user) }}</text>
            <text class="user-meta">{{ user.username }}{{ user.deptName ? ` · ${user.deptName}` : '' }}</text>
          </view>
          <view class="user-check" :class="{ active: isDelegateUserSelected(user) }"><AiIcon v-if="isDelegateUserSelected(user)" icon="/static/icons/ai-icon/check.svg" color="#ffffff" size="xs" /></view>
        </button>
        <button v-if="!usersLoading && usersHasMore" class="load-more-users" @click="loadMoreUsers">加载更多成员</button>
        <view v-if="!usersLoading && !users.length" class="page-hint">未找到可转办人员</view>
      </view>
      <view class="delegate-comment">
        <text class="form-label">转办说明<text v-if="requireComment" class="required-mark"> *</text></text>
        <AiTextarea v-model="delegateComment" maxlength="500" placeholder="请说明转办原因" />
      </view>
      <view v-if="requireSignature" class="delegate-signature">
        <text class="form-label">手写签名<text class="required-mark"> *</text></text>
        <AiSignaturePad ref="delegateSignatureRef" v-model="delegateSignature" />
      </view>
      <template #footer>
        <AiButton block :disabled="!delegateUser" :loading="actionLoading && pendingAction === 'delegate'" @click="submitAction('delegate')">确认转办</AiButton>
      </template>
    </AiPopupSheet>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AiButton from '@/components/AiButton.vue'
import AiAuthImage from '@/components/AiAuthImage.vue'
import AiFeedbackHost from '@/components/feedback/AiFeedbackHost.vue'
import AiIcon from '@/components/AiIcon.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'
import AiSearchBar from '@/components/AiSearchBar.vue'
import AiSignaturePad from '@/components/AiSignaturePad.vue'
import AiTextarea from '@/components/AiTextarea.vue'
import FlowCommentPhraseInput from '@/components/flow/FlowCommentPhraseInput.vue'
import PageSectionRenderer from '@/components/lowcode/PageSectionRenderer.vue'
import TodoFlowTrace from '@/components/flow/TodoFlowTrace.vue'
import TodoDetailSkeleton from '@/components/flow/TodoDetailSkeleton.vue'
import TodoTaskSummary from '@/components/flow/TodoTaskSummary.vue'
import { useBusinessTaskFormState } from '@/composables/lowcode/useBusinessTaskFormState'
import api from '@/api'
import { useAuthStore } from '@/store'
import { showConfirmDialog } from '@/utils/dialog'
import { createFlowActionCredentials } from '@/utils/flow-action-idempotency'
import { compactObject as compact, parseNestedJson as parseJson, resolveApiErrorMessage as resolveErrorMessage } from '@/utils/flow-page'
import { toast } from '@/utils/notify'
import { hasDeclaredFormCreateRules } from '@/utils/form-create-mobile'
import { resolveTaskUiDocument } from '@/utils/task-ui-document'
import { normalizeDictOptions } from '@/utils/lowcode-runtime'
import {
  adaptBusinessTaskFields,
  adaptChildrenConfig,
  buildBusinessTaskFormData,
  buildDefaultPageSections,
  extractPageSections,
  extractMainData,
  collectDictTypes,
  buildFlowInteraction,
  hasWritableBusinessTaskForm,
} from '@/utils/business-task-form-adapter'

const authStore = useAuthStore()
const taskId = ref('')
const sourceMessageId = ref('')
const task = ref(null)
const formInfo = ref(null)
const businessContext = ref(null)
const businessContextError = ref('')
const history = ref([])
const loading = ref(true)
const formLoading = ref(true)
const formSaving = ref(false)
const historyLoading = ref(true)
const diagramLoading = ref(true)
const diagramInfo = ref(null)
const pageMode = ref('todo')
const approvalPointChecks = ref({})
const comment = ref('')
const signature = ref('')
const approvalSignatureRef = ref(null)
const mainData = reactive({})
const childData = reactive({})
const dictOptions = reactive({})
const mainSectionFormRefs = new Map()
const childFormRefs = new Map()
const actionLoading = ref(false)
const pendingAction = ref('')
const claimLoading = ref(false)
const moreVisible = ref(false)
const delegateVisible = ref(false)
const userKeyword = ref('')
const users = ref([])
const usersLoading = ref(false)
const userPageNum = ref(1)
const userTotal = ref(0)
const usersExhausted = ref(false)
const delegateUser = ref(null)
const delegateComment = ref('')
const delegateSignature = ref('')
const delegateSignatureRef = ref(null)

const userId = computed(() => String(authStore.userInfo?.id || authStore.userInfo?.userId || authStore.userInfo?.user_id || ''))
// 业务表单上下文携带服务端最终策略，Flow 表单快照仅覆盖其中明确返回的属性。
const taskPolicySource = computed(() => ({ ...(businessContext.value || {}), ...(formInfo.value || {}) }))
const requireComment = computed(() => taskPolicySource.value?.requireComment !== false)
const requireSignature = computed(() => taskPolicySource.value?.requireSignature === true)
const responsibilityDescription = computed(() => formInfo.value?.responsibilityDescription || '')
const approvalPoints = computed(() => Array.isArray(formInfo.value?.approvalPoints) ? formInfo.value.approvalPoints : [])
const isCandidateTask = computed(() => Number(task.value?.status) === 0 && !task.value?.assignee)
const canApprove = computed(() => taskPolicySource.value?.allowApprove !== false)
const canReject = computed(() => taskPolicySource.value?.allowReject !== false)
const canRejectToStart = computed(() => taskPolicySource.value?.allowRejectToStart === true)
const canReturn = computed(() => taskPolicySource.value?.allowReturn === true)
const returnTargetOptions = computed(() => (Array.isArray(taskPolicySource.value?.returnTargets)
  ? taskPolicySource.value.returnTargets
  : []).map(item => ({
  label: item.activityName || item.activityId,
  value: item.activityId,
})))
const canChooseReturnTarget = computed(() => taskPolicySource.value?.allowMultiReturn === true && returnTargetOptions.value.length > 0)
const selectedReturnTarget = ref('')
const rejectTargetVisible = ref(false)

function openRejectTarget() {
  selectedReturnTarget.value = ''
  rejectTargetVisible.value = true
}
const canDelegate = computed(() => taskPolicySource.value?.allowDelegate !== false)
const canTerminate = computed(() => taskPolicySource.value?.allowTerminate === true)
const hasMoreActions = computed(() => canRejectToStart.value || canDelegate.value || canTerminate.value)
const readonlyMode = computed(() => pageMode.value === 'readonly')
const processNodes = computed(() => Array.isArray(diagramInfo.value?.nodes) ? diagramInfo.value.nodes : [])
const businessSchemaFallback = computed(() => {
  const context = businessContext.value || {}
  if (Array.isArray(context.fields) && context.fields.length) return []
  return context.formRef?.fields || context.formRef?.fieldCatalog || []
})
const businessProviderUnavailable = computed(() => {
  const warnings = Array.isArray(businessContext.value?.warnings) ? businessContext.value.warnings : []
  return warnings.some(item => String(item).includes('Provider未注册'))
})
const documentForm = computed(() => resolveTaskUiDocument(businessContext.value, businessSchemaFallback.value))
const mainNodes = computed(() => documentForm.value?.nodes || [])
const mainFields = computed(() => {
  if (documentForm.value) return documentForm.value.fields
  const context = businessContext.value
  if (Array.isArray(context?.fields) && context.fields.length)
    return adaptBusinessTaskFields(context.fields, context.fieldPermissions)
  if (businessSchemaFallback.value.length)
    return adaptBusinessTaskFields(businessSchemaFallback.value, context?.fieldPermissions)
  return []
})
const allChildren = computed(() => adaptChildrenConfig(
  businessContext.value?.childrenConfig || [],
  businessContext.value?.fieldPermissions || [],
))
const pageSections = computed(() =>
  buildDefaultPageSections(mainFields.value, allChildren.value, documentForm.value?.sections || extractPageSections(businessContext.value)),
)
const flowInteraction = computed(() => buildFlowInteraction(businessContext.value))
const currentFlowNodeKey = computed(() => String(businessContext.value?.taskDefKey || ''))
const runtimeContext = computed(() => ({
  routeQuery: { taskId: taskId.value },
  user: authStore.userInfo || {},
  currentUser: authStore.userInfo || {},
}))
const hasLowcodeForm = computed(() => mainFields.value.length > 0 || allChildren.value.length > 0)
const showBusinessFormPanel = computed(() => Boolean(
  businessContext.value && (isConfiguredBusinessTaskForm(businessContext.value) || hasLowcodeForm.value),
))
const formMode = computed(() => {
  if (readonlyMode.value || businessProviderUnavailable.value || (businessSchemaFallback.value.length > 0 && !documentForm.value?.hasComponentTree))
    return 'detail'
  return hasWritableBusinessTaskForm(mainFields.value, allChildren.value) ? 'edit' : 'detail'
})
const businessFormHasWritableFields = computed(() =>
  hasWritableBusinessTaskForm(mainFields.value, allChildren.value) && formMode.value === 'edit',
)
const {
  applyBusinessContext, resetBusinessData, addBusinessChildRow, removeBusinessChildRow,
} = useBusinessTaskFormState({ mainData, childData, formInfo, seedApprovalPointChecks, getMode: () => formMode.value })
const formSchemaUnavailable = computed(() =>
  !hasLowcodeForm.value && Boolean(
    Object.keys(extractMainData(businessContext.value?.recordData) || {}).length,
  ),
)
const blockedReason = computed(() => {
  if (businessContextError.value)
    return `业务表单加载失败：${businessContextError.value}`
  if (!formInfo.value && !isConfiguredBusinessTaskForm(businessContext.value))
    return '未取得审批节点的表单和权限配置，请刷新后重试。'
  if (isConfiguredBusinessTaskForm(businessContext.value) && !hasLowcodeForm.value)
    return '当前业务表单没有可在移动端渲染的字段配置，请检查流程节点表单资产。'
  if (formInfo.value?.formType === 'external' && formInfo.value?.formUrl && !hasLowcodeForm.value)
    return '此节点未提供可移动端渲染的字段描述，不能跳过 PC 专属表单直接审批。'
  if (hasDeclaredFormCreateRules(formInfo.value?.formJson) && !hasLowcodeForm.value)
    return '此动态表单没有可识别的字段描述，不能跳过填写直接审批。'
  return ''
})

onLoad(async (options = {}) => {
  taskId.value = String(options.taskId || '')
  sourceMessageId.value = String(options.messageId || '')
  pageMode.value = options.mode === 'readonly' ? 'readonly' : 'todo'
  await refresh()
})

async function refresh() {
  if (!taskId.value) { loading.value = false; formLoading.value = false; return }
  loading.value = true
  formLoading.value = true
  historyLoading.value = true
  diagramLoading.value = true
  formInfo.value = null
  businessContext.value = null
  businessContextError.value = ''
  history.value = []
  diagramInfo.value = null
  resetBusinessData()
  try {
    task.value = readCachedTask(taskId.value)
    try {
      const detail = await api.getFlowTaskDetail(taskId.value)
      task.value = detail?.data || task.value
    }
    catch (error) {
      if (!task.value) throw error
      console.warn('读取运行中任务详情失败，改用列表摘要:', error)
    }
    if (!task.value) return
    const currentTaskId = task.value.taskId || task.value.id || taskId.value
    const tracePromise = Promise.allSettled([
      task.value.processInstanceId ? api.getFlowTaskHistory(task.value.processInstanceId) : Promise.resolve({ data: [] }),
      task.value.processInstanceId ? api.getFlowDiagramInfo(task.value.processInstanceId) : Promise.resolve({ data: null }),
    ])
    const context = readonlyMode.value
      ? await loadReadonlyBusinessContext({ taskId: currentTaskId })
      : await loadBusinessContext({ taskId: currentTaskId })
    // 与 PC 的 loadTaskFormBundle 保持一致：优先复用业务上下文随响应携带的
    // taskFormInfo JSON 快照，避免再次请求 Flow 后拿到不完整旧协议而渲染空白。
    if (context?.taskFormInfo && typeof context.taskFormInfo === 'object')
      formInfo.value = context.taskFormInfo
    if (!formInfo.value) {
      try {
        const response = readonlyMode.value
          ? await api.getFlowProcessForm(compact({ taskId: currentTaskId, processInstanceId: task.value.processInstanceId, businessKey: task.value.businessKey, processDefKey: task.value.processDefKey || task.value.processDefinitionKey, taskDefKey: task.value.taskDefKey || task.value.taskDefinitionKey }))
          : await api.getFlowTaskForm(currentTaskId)
        formInfo.value = response?.data || null
      }
      catch (error) { console.warn('读取流程任务表单失败:', error) }
    }
    const [historyResult, diagramResult] = await tracePromise
    seedApprovalPointChecks(formInfo.value)
    await loadDictOptions()
    if (historyResult.status === 'fulfilled') history.value = Array.isArray(historyResult.value?.data) ? historyResult.value.data : []
    if (diagramResult.status === 'fulfilled') diagramInfo.value = diagramResult.value?.data || null
  }
  catch (error) {
    console.error('加载审批详情失败:', error)
    toast(resolveErrorMessage(error, '审批详情加载失败'), { type: 'error' })
  }
  finally {
    loading.value = false
    formLoading.value = false
    historyLoading.value = false
    diagramLoading.value = false
  }
}

async function loadReadonlyBusinessContext(overrides = {}) {
  const query = buildBusinessContextQuery(overrides)
  if (!hasBusinessContextQuery(query)) return null
  try {
    const res = await api.getBusinessTaskReadonlyContext(query)
    businessContext.value = res?.data || null
    applyBusinessContext(businessContext.value)
    return businessContext.value
  }
  catch (error) {
    console.error('加载只读业务表单失败:', error)
    businessContext.value = null
    businessContextError.value = resolveErrorMessage(error, '接口未返回业务表单')
    return null
  }
}

async function loadBusinessContext(overrides = {}) {
  const query = buildBusinessContextQuery(overrides)
  if (!hasBusinessContextQuery(query)) return null
  try {
    const res = await api.getBusinessTaskFormContext(query)
    businessContext.value = res?.data || null
    applyBusinessContext(businessContext.value)
    return businessContext.value
  }
  catch (error) {
    // 审批业务表单只能信任 task-form-context 返回的记录、字段和节点权限。
    // 接口失败时禁止用表单资产或流程变量拼装可编辑表单，避免展示错误数据。
    businessContext.value = null
    businessContextError.value = resolveErrorMessage(error, '接口未返回业务表单')
    console.warn('业务表单上下文不可用，已停止渲染动态表单:', businessContextError.value)
    return null
  }
}

function buildBusinessContextQuery(overrides = {}) {
  const info = formInfo.value || {}
  const rawRef = parseJson(info.formJson, {})
  const formRef = info.formRef || (rawRef && !Array.isArray(rawRef) ? rawRef.formRef || rawRef : {})
  return compact({
    taskId: overrides.taskId || info.taskId || task.value?.taskId || taskId.value,
    businessKey: info.businessKey || task.value?.businessKey,
    processInstanceId: info.processInstanceId || task.value?.processInstanceId,
    processDefKey: info.processDefKey || task.value?.processDefKey || task.value?.processDefinitionKey,
    taskDefKey: info.taskDefKey || task.value?.taskDefKey || task.value?.taskDefinitionKey,
    objectCode: info.objectCode || formRef.objectCode || task.value?.objectCode,
    recordId: info.recordId || formRef.recordId || task.value?.recordId,
    formKey: info.formKey || formRef.formKey,
  })
}
function hasBusinessContextQuery(query) {
  return Boolean(query.taskId || query.businessKey || query.processInstanceId || (query.objectCode && query.recordId))
}

function isConfiguredBusinessTaskForm(context) {
  return context?.configured === true && ['business-object', 'business-code'].includes(context?.formType)
}

function seedApprovalPointChecks(info) {
  approvalPointChecks.value = Object.fromEntries(
    (Array.isArray(info?.approvalPoints) ? info.approvalPoints : []).map(point => [point.id, false]),
  )
}

function toggleApprovalPoint(point) {
  if (readonlyMode.value || !point?.id)
    return
  approvalPointChecks.value = {
    ...approvalPointChecks.value,
    [point.id]: !approvalPointChecks.value[point.id],
  }
}

async function loadDictOptions() {
  const types = collectDictTypes(mainFields.value, allChildren.value)
  await Promise.all([...types].map(async type => {
    try { dictOptions[type] = normalizeDictOptions((await api.getDictOptions(type))?.data) }
    catch { dictOptions[type] = [] }
  }))
}

function setMainFormRef({ sectionId, instance }) {
  const key = String(sectionId || 'main')
  if (instance) mainSectionFormRefs.set(key, instance)
  else mainSectionFormRefs.delete(key)
}

function setChildFormRef({ child, row, rowIndex, instance }) {
  if (!child) return
  const key = `${child.modelCode}:${row?.id || rowIndex || 0}`
  if (instance) childFormRefs.set(key, instance)
  else childFormRefs.delete(key)
}

async function claimTask() {
  claimLoading.value = true
  try {
    await api.claimFlowTask(task.value.taskId || task.value.id, userId.value)
    toast('签收成功', { type: 'success' })
    await refresh()
  }
  catch (error) {
    console.error('签收失败:', error)
    toast(resolveErrorMessage(error, '签收失败'), { type: 'error' })
  }
  finally {
    claimLoading.value = false
  }
}

async function openDelegate() {
  moreVisible.value = false
  delegateVisible.value = true
  delegateUser.value = null
  userKeyword.value = ''
  delegateComment.value = ''
  delegateSignature.value = ''
  userPageNum.value = 1
  userTotal.value = 0
  usersExhausted.value = false
  await loadUsers()
}

function submitMoreAction(action) {
  moreVisible.value = false
  submitAction(action)
}

function selectDelegateUser(user) {
  delegateUser.value = user || null
}

function isDelegateUserSelected(user) {
  return String(delegateUser.value?.id || '') === String(user?.id || '')
}

function delegateUserName(user = {}) {
  return user.realName || user.name || user.nickname || user.username || '未命名成员'
}

function userInitial(user = {}) {
  return String(delegateUserName(user)).slice(0, 1).toUpperCase()
}

async function loadUsers() {
  userPageNum.value = 1
  users.value = []
  userTotal.value = 0
  usersExhausted.value = false
  await fetchUsers()
}

const usersHasMore = computed(() => !usersExhausted.value)

async function loadMoreUsers() {
  if (usersLoading.value || !usersHasMore.value) return
  await fetchUsers()
}

async function fetchUsers() {
  usersLoading.value = true
  try {
    const res = await api.getUserPage({ pageNum: userPageNum.value, pageSize: 30, keyword: userKeyword.value.trim() || undefined })
    const page = res?.data || {}
    const records = Array.isArray(page.records) ? page.records : []
    const selfId = String(userId.value || '')
    const currentAssignee = String(task.value?.assignee || '')
    const candidates = records.filter(user => String(user.id || '') !== selfId && String(user.id || '') !== currentAssignee)
    users.value = userPageNum.value === 1 ? candidates : users.value.concat(candidates)
    userTotal.value = Number(page.total || 0)
    usersExhausted.value = records.length < 30 || userPageNum.value * 30 >= userTotal.value
    userPageNum.value += 1
  }
  catch (error) {
    users.value = []
    console.error('加载转办人员失败:', error)
    toast(resolveErrorMessage(error, '转办人员加载失败'), { type: 'error' })
  }
  finally {
    usersLoading.value = false
  }
}

async function submitAction(action) {
  if (blockedReason.value) return
  if (action === 'delegate' && !delegateUser.value) {
    toast('请选择转办人员', { type: 'warning' })
    return
  }
  if (action === 'reject' && canChooseReturnTarget.value) {
    openRejectTarget()
    return
  }
  if (action === 'return' && canChooseReturnTarget.value && !selectedReturnTarget.value) {
    toast('请选择驳回至哪个已审批节点', { type: 'warning' })
    return
  }
  const actionComment = action === 'delegate' ? delegateComment.value : comment.value
  const actionSignature = action === 'delegate' ? delegateSignature.value : signature.value
  const signatureRef = action === 'delegate' ? delegateSignatureRef.value : approvalSignatureRef.value
  if (requireComment.value && !actionComment.trim()) {
    toast('请输入审批意见', { type: 'warning' })
    return
  }
  if (action === 'approve' && approvalPoints.value.some(point => point?.required === true && !approvalPointChecks.value?.[point.id])) {
    toast('请完成全部必审要点', { type: 'warning' })
    return
  }
  if (!validateRequiredFields() || !hasSignature(actionSignature, signatureRef)) return
  const labels = { approve: '同意', reject: '驳回', rejectToStart: '退回发起人修改', return: '退回', terminate: '终结流程', delegate: '转办' }
  const descriptions = {
    rejectToStart: '当前流程会保留并退回发起人，修改后可沿原流程重新提交。',
  }
  const confirmed = await showConfirmDialog({ title: `确认${labels[action]}`, description: descriptions[action] || '提交后将按当前流程策略执行，不能撤销。', confirmText: labels[action], isDestructive: ['reject', 'terminate'].includes(action) })
  if (!confirmed) return

  actionLoading.value = true
  pendingAction.value = action
  try {
    const resolvedSignature = await resolveSignature(actionSignature, signatureRef)
    if (action === 'delegate') delegateSignature.value = resolvedSignature
    else signature.value = resolvedSignature
    const payload = buildActionPayload(action, actionComment, resolvedSignature)
    Object.assign(payload, await createFlowActionCredentials(action, payload.taskId, buildIdempotencyDigestPayload(payload)))
    if (isConfiguredBusinessTaskForm(businessContext.value) && ['approve', 'reject', 'rejectToStart', 'return'].includes(action)) {
      await api.completeBusinessTaskAction(payload)
    }
    else if (action === 'approve') await api.approveFlowTask(payload)
    else if (action === 'reject') await api.rejectFlowTask(payload)
    else if (action === 'rejectToStart') await api.rejectToStartFlowTask(payload)
    else if (action === 'return') await api.returnFlowTask({ ...payload, targetActivityId: selectedReturnTarget.value || undefined })
    else if (action === 'terminate') await api.terminateFlowTask(payload)
    else await api.delegateFlowTask(payload)
    if (sourceMessageId.value) {
      await api.markMessageRead(sourceMessageId.value).catch(error => console.warn('来源消息将由流程完成事件同步已读:', error))
    }
    toast(`${labels[action]}成功`, { type: 'success' })
    delegateVisible.value = false
    rejectTargetVisible.value = false
    setTimeout(goBack, 500)
  }
  catch (error) {
    console.error('提交审批动作失败:', error)
    toast(resolveErrorMessage(error, `${labels[action] || '提交'}失败`), { type: 'error' })
  }
  finally {
    actionLoading.value = false
    pendingAction.value = ''
  }
}

function buildActionPayload(action, actionComment = comment.value.trim(), actionSignature = signature.value) {
  const info = formInfo.value || {}
  const base = compact({
    action,
    taskId: info.taskId || task.value?.taskId || task.value?.id,
    businessKey: businessContext.value?.businessKey || info.businessKey || task.value?.businessKey,
    processInstanceId: businessContext.value?.processInstanceId || info.processInstanceId || task.value?.processInstanceId,
    processDefKey: businessContext.value?.processDefKey || info.processDefKey || task.value?.processDefKey,
    taskDefKey: businessContext.value?.taskDefKey || info.taskDefKey || task.value?.taskDefKey,
    objectCode: businessContext.value?.objectCode || info.objectCode || task.value?.objectCode,
    recordId: businessContext.value?.recordId || info.recordId || task.value?.recordId,
    formKey: businessContext.value?.formKey || info.formKey,
    userId: userId.value,
    comment: actionComment.trim(),
    signature: actionSignature || undefined,
    targetActivityId: action === 'return' ? selectedReturnTarget.value || undefined : undefined,
    targetUserId: action === 'delegate' ? String(delegateUser.value?.id || '') : undefined,
    variables: { ...(info.variables || {}), ...mainData },
    data: isConfiguredBusinessTaskForm(businessContext.value) && businessFormHasWritableFields.value
      ? buildCurrentBusinessFormData()
      : undefined,
    approvalPointResults: approvalPoints.value.map(point => ({
      id: point.id,
      content: point.content,
      required: point.required === true,
      checked: Boolean(approvalPointChecks.value?.[point.id]),
    })),
  })
  return base
}

function buildIdempotencyDigestPayload(payload) {
  const { action, taskId, comment, signature, variables, data, targetActivityId, targetUserId, approvalPointResults } = payload
  return { action, taskId, comment, signature, variables, data, targetActivityId, targetUserId, approvalPointResults }
}

function buildCurrentBusinessFormData() {
  return buildBusinessTaskFormData({
    formType: businessContext.value?.formType,
    fields: mainFields.value,
    children: allChildren.value,
    mainData,
    childData,
  })
}

async function saveBusinessFields() {
  if (!businessFormHasWritableFields.value || !validateRequiredFields()) return
  formSaving.value = true
  try {
    const payload = buildActionPayload('approve')
    const res = await api.saveBusinessTaskFormContext({ ...payload, data: buildCurrentBusinessFormData() })
    businessContext.value = res?.data || businessContext.value
    applyBusinessContext(businessContext.value)
    toast('修改已暂存', { type: 'success' })
  }
  catch (error) {
    console.error('暂存业务表单失败:', error)
    toast(resolveErrorMessage(error, '暂存修改失败'), { type: 'error' })
  }
  finally {
    formSaving.value = false
  }
}

function validateRequiredFields() {
  const forms = [...mainSectionFormRefs.values(), ...childFormRefs.values()]
  const invalid = forms.find(form => form?.validate?.() === false)
  if (invalid) {
    toast('请完善必填字段', { type: 'warning' })
    return false
  }
  return true
}

function hasSignature(value, signatureRef) {
  if (!taskPolicySource.value?.requireSignature) return true
  if (String(value || '').trim()) return true
  if (signatureRef?.hasSignature?.()) return true
  toast('请完成手写签名', { type: 'warning' })
  return false
}
async function resolveSignature(value, signatureRef) {
  if (!taskPolicySource.value?.requireSignature) return value || ''
  if (String(value || '').trim() && !signatureRef?.hasSignature?.()) return value
  return signatureRef?.upload ? signatureRef.upload() : value || ''
}
function readCachedTask(id) { try { return uni.getStorageSync(`flow-task:${id}`) || null } catch { return null } }
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/todo' }) }) }
</script>

<style lang="scss" scoped src="./styles/todo-detail.scss"></style>
