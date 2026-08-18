<template>
  <view class="todo-detail-page">
    <view class="detail-nav">
      <button class="nav-back" @click="goBack">
        <AiIcon icon="/static/icons/ai-icon/arrow-left.svg" color="#1f2329" size="sm" />
      </button>
      <text class="nav-title">审批详情</text>
      <button class="nav-more" @click="refresh">
        <AiIcon icon="/static/icons/ai-icon/refresh-cw.svg" color="#4e5969" size="sm" />
      </button>
    </view>

    <scroll-view class="detail-scroll" scroll-y :show-scrollbar="false">
      <view v-if="loading" class="detail-skeleton">
        <AiListSkeleton :rows="2" />
        <AiListSkeleton :rows="4" compact />
      </view>
      <template v-else-if="task">
        <view class="task-summary">
          <text class="task-title">{{ taskTitle(task) }}</text>
          <text class="task-node">{{ task.taskName || task.name || '审批节点' }}</text>
          <view class="task-facts">
            <view class="task-fact"><text>申请人</text><text>{{ task.startUserName || task.createByName || '-' }}</text></view>
            <view class="task-fact"><text>发起部门</text><text>{{ task.startDeptName || '-' }}</text></view>
            <view class="task-fact"><text>流程分类</text><text>{{ task.categoryName || task.category || '-' }}</text></view>
            <view class="task-fact"><text>提交时间</text><text>{{ task.createTime || task.startTime || '-' }}</text></view>
          </view>
        </view>

        <AiTabs v-model="activeTabIndex" :tabs="detailTabs" class="detail-tabs">
          <AiTab :index="0">
          <view v-if="formLoading" class="page-hint">正在加载表单…</view>
          <view v-else-if="blockedReason" class="blocked-panel">
            <AiIcon icon="/static/icons/ai-icon/info.svg" color="#1677ff" size="md" />
            <text class="blocked-title">请在 PC 端处理</text>
            <text class="blocked-copy">{{ blockedReason }}</text>
          </view>

          <view v-else class="content-panel">
            <view v-if="businessProviderUnavailable" class="form-provider-notice">
              <text>流程服务未加载该业务表单 Provider，当前仅能展示表单字段结构；部署 Provider 后会自动加载实际数据和节点权限。</text>
            </view>
            <view v-if="businessFormHasWritableFields" class="form-panel-head">
              <text>业务表单</text>
              <button class="save-form-button" :disabled="actionLoading || formSaving" @click="saveBusinessFields">
                {{ formSaving ? '暂存中' : '暂存修改' }}
              </button>
            </view>
            <view v-if="displayFields.length" class="field-list">
              <view v-for="field in displayFields" :key="field.key" class="form-row">
                <text class="form-label">{{ field.label }}<text v-if="field.required" class="required-mark"> *</text></text>
                <textarea
                  v-if="field.multiline && !field.readonly"
                  v-model="fieldValues[field.key]"
                  class="form-textarea"
                  :placeholder="`请输入${field.label}`"
                />
                <AiRadioGroup
                  v-else-if="field.radio && field.options.length && !field.readonly"
                  v-model="fieldValues[field.key]"
                  :options="field.options"
                  class="form-radio-group"
                />
                <AiSelect
                  v-else-if="field.select && field.options.length && !field.readonly"
                  v-model="fieldValues[field.key]"
                  :options="field.options"
                  :title="field.label"
                  :placeholder="`请选择${field.label}`"
                  class="form-select"
                />
                <picker
                  v-else-if="(field.date || field.datetime) && !field.readonly"
                  :mode="field.datetime ? 'datetime' : 'date'"
                  :value="pickerDateValue(fieldValues[field.key])"
                  @change="setDateValue(field.key, $event)"
                >
                  <view class="form-date-picker" :class="{ 'is-placeholder': !fieldValues[field.key] }">
                    <text>{{ fieldValues[field.key] || `请选择${field.label}` }}</text>
                    <AiIcon icon="/static/icons/ai-icon/calendar.svg" color="#94a3b8" size="sm" />
                  </view>
                </picker>
                <AiFileUpload
                  v-else-if="field.file && !field.readonly"
                  v-model="fieldValues[field.key]"
                  :business-type="`flow_${field.key}`"
                />
                <view v-else-if="field.file" class="form-file-value">
                  <AiIcon icon="/static/icons/ai-icon/file-text.svg" color="#64748b" size="sm" />
                  <text>{{ displayValue(fieldValues[field.key]) }}</text>
                </view>
                <input
                  v-else-if="!field.readonly"
                  v-model="fieldValues[field.key]"
                  class="form-input"
                  :type="field.inputType"
                  :placeholder="`请输入${field.label}`"
                />
                <text v-else class="form-readonly">{{ displayValue(fieldValues[field.key]) }}</text>
              </view>
            </view>
            <view v-else-if="formSchemaUnavailable" class="form-schema-notice">
              <text>该流程未返回可展示的业务字段配置，已隐藏内部字段和技术标识。</text>
            </view>
            <view v-if="businessChildren.length" class="business-children">
              <view v-for="child in businessChildren" :key="child.key" class="business-child-card">
                <view class="business-child-head">
                  <text>{{ child.label }}</text><text>{{ child.rows.length }} 条</text>
                </view>
                <view v-for="(row, rowIndex) in child.rows" :key="`${child.key}-${rowIndex}`" class="business-child-row">
                  <view v-for="field in child.fields" :key="field.key" class="business-child-field">
                    <text>{{ field.label }}</text><text>{{ displayValue(row[field.key]) }}</text>
                  </view>
                </view>
              </view>
            </view>

            <view v-if="!readonlyMode" class="comment-row">
              <text class="form-label">审批意见<text v-if="requireComment" class="required-mark"> *</text></text>
              <textarea v-model="comment" class="form-textarea comment" maxlength="500" placeholder="请输入审批意见" />
            </view>
            <view v-if="!readonlyMode && requireSignature" class="comment-row signature-row">
              <text class="form-label">手写签名<text class="required-mark"> *</text></text>
              <AiSignaturePad ref="approvalSignatureRef" v-model="signature" />
            </view>
          </view>
          </AiTab>
          <AiTab :index="1">
        <view class="history-panel">
          <AiListSkeleton v-if="historyLoading" :rows="4" compact />
          <view v-else-if="history.length" class="timeline">
            <view v-for="item in history" :key="historyKey(item)" class="timeline-item">
              <view class="timeline-dot" />
              <view class="timeline-copy">
                <text class="timeline-title">{{ item.activityName || item.taskName || item.name || '流程节点' }}</text>
                <text class="timeline-meta">{{ item.assigneeName || item.userName || item.operatorName || '-' }} · {{ item.endTime || item.createTime || item.startTime || '-' }}</text>
                <text v-if="item.comment" class="timeline-comment">{{ item.comment }}</text>
              </view>
            </view>
          </view>
          <view v-else class="page-hint">暂无审批记录</view>
        </view>
          </AiTab>
          <AiTab :index="2">
            <view class="history-panel process-panel">
              <AiListSkeleton v-if="diagramLoading" :rows="4" compact />
              <view v-else-if="processNodes.length" class="process-nodes">
                <view v-for="node in processNodes" :key="node.nodeId || node.id" class="process-node" :class="`is-${node.status || 'pending'}`">
                  <view class="process-node__mark" />
                  <view class="process-node__copy">
                    <text>{{ node.nodeName || node.name || '流程节点' }}</text>
                    <text>{{ node.assigneeNames?.join('、') || node.assigneeName || node.comment || node.statusText || node.status || '等待处理' }}</text>
                  </view>
                </view>
              </view>
              <view v-else class="page-hint">暂无可展示的流程节点</view>
            </view>
          </AiTab>
        </AiTabs>
      </template>
      <view v-else class="page-hint">待办不存在或已处理</view>
    </scroll-view>

    <view v-if="task && !readonlyMode" class="action-bar">
      <AiButton v-if="isCandidateTask" block size="sm" :loading="claimLoading" @click="claimTask">签收后处理</AiButton>
      <template v-else>
        <AiButton v-if="canDelegate || canTerminate || canReturn" class="more-action" size="sm" variant="secondary" :disabled="actionLoading" @click="moreVisible = true">
          <template #leftIcon><AiIcon icon="/static/icons/ai-icon/more-horizontal.svg" color="#475569" size="sm" /></template>
          更多
        </AiButton>
        <AiButton v-if="canReject" size="sm" variant="danger" :disabled="Boolean(blockedReason) || actionLoading" @click="submitAction('reject')">驳回</AiButton>
        <AiButton v-if="canApprove" size="sm" :loading="actionLoading && pendingAction === 'approve'" :disabled="Boolean(blockedReason) || actionLoading" @click="submitAction('approve')">同意</AiButton>
      </template>
    </view>

    <AiPopupSheet v-model="moreVisible" title="更多操作" description="操作权限以当前审批节点配置为准">
      <view class="more-list">
        <button v-if="canDelegate" class="more-row" @click="openDelegate">
          <view class="more-row__icon"><AiIcon icon="/static/icons/ai-icon/user-plus.svg" color="#2563eb" size="sm" /></view>
          <view class="more-row__copy"><text>转办</text><text>交由其他成员继续处理</text></view>
          <AiIcon icon="/static/icons/ai-icon/chevron-right.svg" color="#94a3b8" size="sm" />
        </button>
        <button v-if="canReturn" class="more-row" @click="submitAction('return')">
          <view class="more-row__icon"><AiIcon icon="/static/icons/ai-icon/corner-up-left.svg" color="#2563eb" size="sm" /></view>
          <view class="more-row__copy"><text>退回上一节点</text><text>退回至前一处理环节</text></view>
          <AiIcon icon="/static/icons/ai-icon/chevron-right.svg" color="#94a3b8" size="sm" />
        </button>
        <button v-if="canTerminate" class="more-row danger" @click="submitAction('terminate')">
          <view class="more-row__icon"><AiIcon icon="/static/icons/ai-icon/x-circle.svg" color="#c2410c" size="sm" /></view>
          <view class="more-row__copy"><text>终结流程</text><text>结束当前流程，不可恢复</text></view>
          <AiIcon icon="/static/icons/ai-icon/chevron-right.svg" color="#94a3b8" size="sm" />
        </button>
      </view>
    </AiPopupSheet>

    <AiPopupSheet v-model="delegateVisible" title="转办任务" description="选择处理人后，再确认转办">
      <view class="delegate-search"><AiSearchBar v-model="userKeyword" placeholder="搜索姓名或用户名" @search="loadUsers" @clear="loadUsers" /></view>
      <view v-if="delegateUser" class="delegate-choice">
        <view class="delegate-choice__avatar">{{ userInitial(delegateUser) }}</view>
        <view class="delegate-choice__copy"><text>已选择</text><text>{{ delegateUserName(delegateUser) }}</text></view>
        <AiIcon icon="/static/icons/ai-icon/check-circle.svg" color="#2563eb" size="md" />
      </view>
      <view class="user-list">
        <AiListSkeleton v-if="usersLoading" :rows="3" compact />
        <button v-for="user in users" v-else :key="user.id" class="user-row" :class="{ active: isDelegateUserSelected(user) }" @click.stop="selectDelegateUser(user)">
          <view class="user-avatar">{{ userInitial(user) }}</view>
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
        <textarea v-model="delegateComment" class="form-textarea" maxlength="500" placeholder="请说明转办原因" />
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
import AiFileUpload from '@/components/AiFileUpload.vue'
import AiIcon from '@/components/AiIcon.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'
import AiRadioGroup from '@/components/AiRadioGroup.vue'
import AiSearchBar from '@/components/AiSearchBar.vue'
import AiSelect from '@/components/AiSelect.vue'
import AiSignaturePad from '@/components/AiSignaturePad.vue'
import AiTab from '@/components/AiTab.vue'
import AiTabs from '@/components/AiTabs.vue'
import api from '@/api'
import { useAuthStore } from '@/store'
import { ensureLogin } from '@/utils/auth-guard'
import { showConfirmDialog } from '@/utils/dialog'
import { toast } from '@/utils/notify'

const authStore = useAuthStore()
const taskId = ref('')
const task = ref(null)
const formInfo = ref(null)
const businessContext = ref(null)
const history = ref([])
const loading = ref(true)
const formLoading = ref(true)
const formSaving = ref(false)
const historyLoading = ref(true)
const diagramLoading = ref(true)
const diagramInfo = ref(null)
const activeTabIndex = ref(0)
const detailTabs = ['业务内容', '审批记录', '流程进度']
const pageMode = ref('todo')
const comment = ref('')
const signature = ref('')
const approvalSignatureRef = ref(null)
const fieldValues = reactive({})
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
const taskPolicySource = computed(() => formInfo.value || businessContext.value || {})
const requireComment = computed(() => taskPolicySource.value?.requireComment !== false)
const isCandidateTask = computed(() => Number(task.value?.status) === 0 && !task.value?.assignee)
const canApprove = computed(() => taskPolicySource.value?.allowApprove !== false)
const canReject = computed(() => taskPolicySource.value?.allowReject !== false)
const canReturn = computed(() => taskPolicySource.value?.allowReturn === true)
const canDelegate = computed(() => taskPolicySource.value?.allowDelegate !== false)
const canTerminate = computed(() => taskPolicySource.value?.allowTerminate === true)
const readonlyMode = computed(() => pageMode.value === 'readonly')
const processNodes = computed(() => Array.isArray(diagramInfo.value?.nodes) ? diagramInfo.value.nodes : [])
const dynamicFields = computed(() => normalizeFields(resolveTaskFormFields(formInfo.value)))
const businessRecordData = computed(() => normalizeBusinessRecordData(businessContext.value?.recordData))
const businessSchemaFallback = computed(() => {
  const context = businessContext.value || {}
  if (Array.isArray(context.fields) && context.fields.length) return []
  return context.formRef?.fields || context.formRef?.fieldCatalog || []
})
const businessProviderUnavailable = computed(() => {
  const warnings = Array.isArray(businessContext.value?.warnings) ? businessContext.value.warnings : []
  return warnings.some(item => String(item).includes('Provider未注册'))
})
const businessFields = computed(() => normalizeFields(
  Array.isArray(businessContext.value?.fields) && businessContext.value.fields.length
    ? businessContext.value.fields
    : businessSchemaFallback.value,
  { forceReadonly: businessSchemaFallback.value.length > 0 || businessProviderUnavailable.value },
))
const businessFormHasWritableFields = computed(() => businessFields.value.some(field => !field.readonly))
const renderFields = computed(() => businessFields.value.length ? businessFields.value : dynamicFields.value)
const fallbackReadonlyFields = computed(() => [])
const displayFields = computed(() => renderFields.value.length ? renderFields.value : fallbackReadonlyFields.value)
const formSchemaUnavailable = computed(() => !displayFields.value.length && Boolean(Object.keys(businessRecordData.value || formInfo.value?.variables || {}).length))
const businessChildren = computed(() => normalizeBusinessChildren(businessContext.value))
const unsupportedWritableField = computed(() => displayFields.value.find(field => field.unsupported && !field.readonly))
const blockedReason = computed(() => {
  if (formInfo.value?.formType === 'external' && formInfo.value?.formUrl && !renderFields.value.length) return '此节点未提供可移动端渲染的字段描述，不能跳过 PC 专属表单直接审批。'
  if (formInfo.value?.formType === 'dynamic' && formInfo.value?.formJson && !dynamicFields.value.length) return '此动态表单没有可识别的字段描述，不能跳过填写直接审批。'
  if (unsupportedWritableField.value) return `“${unsupportedWritableField.value.label}”为移动端尚未支持的可编辑字段，不能跳过填写直接审批。`
  return ''
})

onLoad(async (options = {}) => {
  taskId.value = String(options.taskId || '')
  pageMode.value = options.mode === 'readonly' ? 'readonly' : 'todo'
  const ok = await ensureLogin({ redirect: `/pages/todo-detail?taskId=${encodeURIComponent(taskId.value)}` })
  if (ok) await refresh()
})

async function refresh() {
  if (!taskId.value) return
  loading.value = true
  formLoading.value = true
  historyLoading.value = true
  diagramLoading.value = true
  formInfo.value = null
  businessContext.value = null
  history.value = []
  diagramInfo.value = null
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
    const [businessResult, historyResult, diagramResult] = await Promise.allSettled([
      readonlyMode.value ? loadReadonlyBusinessContext({ taskId: currentTaskId }) : loadBusinessContext({ taskId: currentTaskId }),
      task.value.processInstanceId ? api.getFlowTaskHistory(task.value.processInstanceId) : Promise.resolve({ data: [] }),
      task.value.processInstanceId ? api.getFlowDiagramInfo(task.value.processInstanceId) : Promise.resolve({ data: null }),
    ])
    const isBusinessManaged = businessResult.status === 'fulfilled' && isConfiguredBusinessTaskForm(businessResult.value)
    if (!isBusinessManaged) {
      const formResult = readonlyMode.value
        ? await api.getFlowProcessForm(compact({ taskId: currentTaskId, processInstanceId: task.value.processInstanceId, businessKey: task.value.businessKey, processDefKey: task.value.processDefKey || task.value.processDefinitionKey, taskDefKey: task.value.taskDefKey || task.value.taskDefinitionKey }))
        : await api.getFlowTaskForm(currentTaskId)
      formInfo.value = formResult?.data || null
      seedFieldValues(formInfo.value?.variables)
    }
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
    seedFieldValues(businessRecordData.value)
    return businessContext.value
  }
  catch (error) {
    console.error('加载只读业务表单失败:', error)
    return null
  }
}

async function loadBusinessContext(overrides = {}) {
  const query = buildBusinessContextQuery(overrides)
  if (!hasBusinessContextQuery(query)) return null
  try {
    const res = await api.getBusinessTaskFormContext(query)
    businessContext.value = res?.data || null
    seedFieldValues(businessRecordData.value)
    return businessContext.value
  }
  catch (error) {
    console.error('加载业务表单失败:', error)
    return null
  }
}

function buildBusinessContextQuery(overrides = {}) {
  const info = formInfo.value || {}
  return compact({
    taskId: overrides.taskId || info.taskId || task.value?.taskId || taskId.value,
    businessKey: info.businessKey || task.value?.businessKey,
    processInstanceId: info.processInstanceId || task.value?.processInstanceId,
    processDefKey: info.processDefKey || task.value?.processDefKey || task.value?.processDefinitionKey,
    taskDefKey: info.taskDefKey || task.value?.taskDefKey || task.value?.taskDefinitionKey,
    objectCode: info.objectCode || task.value?.objectCode,
    recordId: info.recordId || task.value?.recordId,
    formKey: info.formKey,
  })
}
function hasBusinessContextQuery(query) {
  return Boolean(query.taskId || query.businessKey || query.processInstanceId || (query.objectCode && query.recordId))
}

function isConfiguredBusinessTaskForm(context) {
  return context?.configured === true && ['business-object', 'business-code'].includes(context?.formType)
}

function seedFieldValues(source = {}) {
  if (!source || typeof source !== 'object') return
  Object.entries(source).forEach(([key, value]) => {
    if (fieldValues[key] === undefined) fieldValues[key] = value == null ? '' : String(value)
  })
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
  const actionComment = action === 'delegate' ? delegateComment.value : comment.value
  const actionSignature = action === 'delegate' ? delegateSignature.value : signature.value
  const signatureRef = action === 'delegate' ? delegateSignatureRef.value : approvalSignatureRef.value
  if (requireComment.value && !actionComment.trim()) {
    toast('请输入审批意见', { type: 'warning' })
    return
  }
  if (!validateRequiredFields() || !hasSignature(actionSignature, signatureRef)) return
  const labels = { approve: '同意', reject: '驳回', return: '退回', terminate: '终结流程', delegate: '转办' }
  const confirmed = await showConfirmDialog({ title: `确认${labels[action]}`, description: '提交后将按当前流程策略执行，不能撤销。', confirmText: labels[action], isDestructive: ['reject', 'terminate'].includes(action) })
  if (!confirmed) return

  actionLoading.value = true
  pendingAction.value = action
  try {
    await saveBusinessFieldsIfNeeded(action)
    const resolvedSignature = await resolveSignature(actionSignature, signatureRef)
    if (action === 'delegate') delegateSignature.value = resolvedSignature
    else signature.value = resolvedSignature
    const payload = buildActionPayload(action, actionComment, resolvedSignature)
    if (isConfiguredBusinessTaskForm(businessContext.value) && ['approve', 'reject'].includes(action)) {
      await api.completeBusinessTaskAction(payload)
    }
    else if (action === 'approve') await api.approveFlowTask(payload)
    else if (action === 'reject') await api.rejectFlowTask(payload)
    else if (action === 'return') await api.returnFlowTask(payload)
    else if (action === 'terminate') await api.terminateFlowTask(payload)
    else await api.delegateFlowTask(payload)
    toast(`${labels[action]}成功`, { type: 'success' })
    delegateVisible.value = false
    setTimeout(() => uni.navigateBack(), 350)
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
    targetUserId: action === 'delegate' ? String(delegateUser.value?.id || '') : undefined,
    variables: { ...(info.variables || {}), ...pickFieldValues(dynamicFields.value) },
  })
  return base
}

async function saveBusinessFieldsIfNeeded(action) {
  if (!['approve', 'reject'].includes(action) || !isConfiguredBusinessTaskForm(businessContext.value) || !businessFormHasWritableFields.value) return null
  const payload = buildActionPayload(action)
  const res = await api.saveBusinessTaskFormContext({ ...payload, data: pickFieldValues(businessFields.value) })
  businessContext.value = res?.data || businessContext.value
  seedFieldValues(businessRecordData.value)
  return businessContext.value
}

async function saveBusinessFields() {
  if (!businessFormHasWritableFields.value || !validateRequiredFields()) return
  formSaving.value = true
  try {
    const payload = buildActionPayload('approve')
    const res = await api.saveBusinessTaskFormContext({ ...payload, data: pickFieldValues(businessFields.value) })
    businessContext.value = res?.data || businessContext.value
    seedFieldValues(businessRecordData.value)
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
  const required = renderFields.value.find(field => field.required && !String(fieldValues[field.key] || '').trim())
  if (!required) return true
  toast(`请填写${required.label}`, { type: 'warning' })
  return false
}

function pickFieldValues(fields) {
  return fields.reduce((result, field) => ({ ...result, [field.key]: fieldValues[field.key] }), {})
}

function normalizeFields(raw, { forceReadonly = false } = {}) {
  const list = Array.isArray(raw)
    ? raw
    : Array.isArray(raw?.fields)
      ? raw.fields
      : Array.isArray(raw?.fieldCatalog)
        ? raw.fieldCatalog
        : Array.isArray(raw?.formRef?.fields)
          ? raw.formRef.fields
          : Array.isArray(raw?.form?.fields)
            ? raw.form.fields
            : Array.isArray(raw?.rule)
              ? raw.rule
              : []
  return list.map((item, index) => {
    const props = item?.props || {}
    const key = item?.field || item?.key || item?.fieldCode || props.fieldCode || props.field || item?.name
    if (!key) return null
    const type = String(item?.type || item?.component || props.type || 'input').toLowerCase()
    const options = normalizeFieldOptions(item?.options || props.options || item?.props?.options)
    const readable = item?.readable !== false && item?.visible !== false && props.visible !== false
    const file = type.includes('file') || type.includes('upload')
    const radio = type.includes('radio')
    const select = !radio && (type.includes('select') || type.includes('picker'))
    const datetime = type.includes('datetime') || type.includes('date-time')
    const date = !datetime && (type === 'date' || type.includes('date-picker'))
    const supported = type.includes('input') || type.includes('textarea') || type.includes('number')
      || file || radio || select || date || datetime
    const readonly = item?.writable === false || item?.readonly === true || item?.disabled === true || props.readonly === true || props.disabled === true
    return {
      key,
      label: item?.label || item?.title || props.label || key,
      required: item?.required === true || props.required === true,
      readonly: readonly || readonlyMode.value || forceReadonly,
      multiline: type.includes('textarea'),
      inputType: type.includes('number') ? 'number' : 'text',
      options,
      radio,
      select,
      date,
      datetime,
      file,
      unsupported: !supported || ((radio || select) && !readonly && !options.length),
      index,
    }
  }).filter(field => field && field.readable)
}

function normalizeFieldOptions(raw) {
  const source = Array.isArray(raw) ? raw : Array.isArray(raw?.options) ? raw.options : []
  return source.map((item) => {
    if (typeof item === 'string' || typeof item === 'number') return { label: String(item), value: item }
    return {
      label: item?.label ?? item?.name ?? item?.text ?? item?.dictLabel ?? String(item?.value ?? item?.id ?? ''),
      value: item?.value ?? item?.id ?? item?.key ?? item?.dictValue ?? '',
    }
  }).filter(option => option.value !== '')
}

function normalizeBusinessRecordData(recordData) {
  if (!recordData || typeof recordData !== 'object' || Array.isArray(recordData)) return {}
  if (recordData.main && typeof recordData.main === 'object' && !Array.isArray(recordData.main)) return recordData.main
  const { children, ...main } = recordData
  return main
}

function normalizeBusinessChildren(context = {}) {
  const source = context?.recordData?.children && typeof context.recordData.children === 'object' ? context.recordData.children : {}
  return (Array.isArray(context?.childrenConfig) ? context.childrenConfig : [])
    .map((child) => {
      const key = child.key || child.modelCode || child.tableName
      const rows = key && Array.isArray(source[key]) ? source[key] : []
      return {
        key,
        label: child.label || child.name || child.title || child.tableComment || '明细',
        fields: normalizeFields(child.fields),
        rows,
      }
    })
    .filter(child => child.key && child.rows.length && child.fields.length)
}

function parseJson(value) {
  if (!value || typeof value === 'object') return value || []
  try {
    const parsed = JSON.parse(value)
    return typeof parsed === 'string' ? parseJson(parsed) : parsed
  }
  catch { return [] }
}

function resolveTaskFormFields(info = {}) {
  const candidates = [
    parseJson(info?.formJson),
    info?.fields,
    info?.formRef?.fields,
    info?.fieldCatalog,
    info?.formRef?.fieldCatalog,
    info?.formRef,
    info,
  ]
  return candidates.find(candidate => normalizeFields(candidate).length) || []
}

function compact(source) {
  return Object.fromEntries(Object.entries(source).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}

function pickerDateValue(value) { return String(value || '').replace(' ', 'T').slice(0, 16) }
function setDateValue(key, event) { fieldValues[key] = event?.detail?.value || '' }
function displayValue(value) { return value === undefined || value === null || value === '' ? '-' : String(value) }
function resolveErrorMessage(error, fallback) {
  const message = error?.data?.message
    || error?.response?.data?.message
    || error?.error?.data?.message
    || error?.message
    || error?.msg
  return message && String(message).trim() ? String(message) : fallback
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
function taskTitle(value = {}) { return value.title || value.businessTitle || value.processName || value.processDefinitionName || value.taskName || '审批任务' }
function historyKey(item) { return item.id || item.taskId || `${item.activityName || item.taskName}-${item.startTime || item.createTime}` }
function readCachedTask(id) { try { return uni.getStorageSync(`flow-task:${id}`) || null } catch { return null } }
function goBack() { uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/todo' }) }) }
</script>

<style lang="scss" scoped>
.todo-detail-page { display: flex; height: 100vh; flex-direction: column; background: var(--page-bg); }
.detail-nav { display: flex; height: calc(88rpx + env(safe-area-inset-top)); align-items: flex-end; gap: 18rpx; padding: 0 24rpx 14rpx; background: var(--page-bg); box-sizing: border-box; }
.nav-back, .nav-more { display: flex; width: 56rpx; height: 56rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; border-radius: 10rpx; background: transparent; }
.nav-back::after, .nav-more::after { border: 0; }
.nav-title { flex: 1; color: var(--text-strong); font-size: 32rpx; font-weight: 600; text-align: center; }
.detail-scroll { height: 0; flex: 1; }
.detail-skeleton { display: flex; flex-direction: column; gap: 20rpx; padding: 24rpx; }
.task-summary, .content-panel, .history-panel { margin: 24rpx; padding: 28rpx; border: 1rpx solid var(--border-color); border-radius: var(--radius-card); background: #fff; }
.task-title, .task-node, .task-fact text, .form-label, .form-readonly, .timeline-title, .timeline-meta, .timeline-comment, .blocked-title, .blocked-copy, .user-name, .user-meta, .business-child-head text, .business-child-field text { display: block; }
.task-title { color: var(--text-strong); font-size: 34rpx; font-weight: 600; line-height: 1.4; }
.task-node { margin-top: 12rpx; color: var(--primary-color); font-size: 25rpx; }
.task-facts { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18rpx 24rpx; margin-top: 24rpx; }
.task-fact { min-width: 0; }
.task-fact text:first-child { color: #94a3b8; font-size: 21rpx; }
.task-fact text:last-child { overflow: hidden; margin-top: 5rpx; color: #4e5969; font-size: 23rpx; text-overflow: ellipsis; white-space: nowrap; }
.detail-tabs { margin: 0 24rpx; }
.detail-tabs :deep(.ai-tabs-content) { min-width: 0; }
.form-panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; color: var(--text-strong); font-size: 28rpx; font-weight: 650; }
.form-provider-notice { margin-bottom: 22rpx; padding: 16rpx 18rpx; border: 1rpx solid #fde7b2; border-radius: 10rpx; color: #8a5a00; font-size: 22rpx; line-height: 1.55; background: #fffbeb; }
.form-provider-notice text { display: block; }
.form-schema-notice { padding: 32rpx 18rpx; border: 1rpx dashed #d7dee8; border-radius: 10rpx; color: #64748b; font-size: 24rpx; line-height: 1.6; text-align: center; background: #fafcff; }
.form-schema-notice text { display: block; }
.save-form-button { height: 54rpx; margin: 0; padding: 0 16rpx; border: 1rpx solid #bfdbfe; border-radius: 8rpx; color: var(--primary-color); font-size: 22rpx; line-height: 52rpx; background: #f8fbff; }
.save-form-button::after { border: 0; }
.save-form-button[disabled] { opacity: .55; }
.field-list { display: flex; flex-direction: column; gap: 26rpx; }
.form-row, .comment-row { display: flex; flex-direction: column; gap: 14rpx; }
.form-label { color: #4e5969; font-size: 25rpx; }
.required-mark { color: #f53f3f; }
.form-input, .form-textarea { width: 100%; padding: 18rpx; border: 1rpx solid var(--border-color); border-radius: 8rpx; color: var(--text-strong); font-size: 27rpx; background: #fff; box-sizing: border-box; }
.form-input { height: 78rpx; }
.form-textarea { min-height: 148rpx; line-height: 1.5; }
.form-textarea.comment { margin-top: 4rpx; }
.form-select { width: 100%; }
.form-radio-group { padding: 2rpx 0; }
.form-date-picker, .form-file-value { display: flex; min-height: 78rpx; align-items: center; justify-content: space-between; gap: 16rpx; padding: 0 18rpx; border: 1rpx solid var(--border-color); border-radius: 8rpx; color: var(--text-strong); font-size: 27rpx; background: #fff; box-sizing: border-box; }
.form-date-picker.is-placeholder { color: #94a3b8; }
.form-file-value { justify-content: flex-start; color: #4e5969; }
.form-file-value text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.form-readonly { min-height: 42rpx; padding: 16rpx 0; color: #4e5969; font-size: 27rpx; }
.business-children { display: flex; flex-direction: column; gap: 18rpx; margin-top: 30rpx; padding-top: 24rpx; border-top: 1rpx solid #edf0f3; }
.business-child-card { overflow: hidden; border: 1rpx solid #e8edf3; border-radius: 14rpx; }
.business-child-head { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 18rpx; color: var(--text-strong); font-size: 25rpx; font-weight: 650; background: #f8fafc; }
.business-child-head text:last-child { color: #94a3b8; font-size: 21rpx; font-weight: 400; }
.business-child-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18rpx; padding: 18rpx; border-top: 1rpx solid #edf0f3; }
.business-child-field { min-width: 0; }
.business-child-field text:first-child { color: #94a3b8; font-size: 20rpx; }
.business-child-field text:last-child { overflow: hidden; margin-top: 5rpx; color: #4e5969; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.blocked-panel { display: flex; flex-direction: column; align-items: flex-start; gap: 14rpx; margin: 24rpx; padding: 32rpx; border: 1rpx solid #b7d7ff; border-radius: var(--radius-card); background: #f0f7ff; }
.blocked-title { color: var(--text-strong); font-size: 29rpx; font-weight: 600; }
.blocked-copy { color: #4e5969; font-size: 25rpx; line-height: 1.6; }
.page-hint { padding: 80rpx 32rpx; color: var(--text-muted); font-size: 26rpx; text-align: center; }
.timeline { padding: 4rpx 0; }
.timeline-item { position: relative; display: flex; gap: 20rpx; padding-bottom: 28rpx; }
.timeline-item:not(:last-child)::before { content: ''; position: absolute; top: 20rpx; bottom: 0; left: 8rpx; width: 2rpx; background: #e5e6eb; }
.timeline-dot { position: relative; z-index: 1; width: 18rpx; height: 18rpx; margin-top: 8rpx; border-radius: 50%; background: var(--primary-color); }
.timeline-copy { min-width: 0; flex: 1; }
.timeline-title { color: var(--text-strong); font-size: 27rpx; font-weight: 600; }
.timeline-meta { margin-top: 8rpx; color: var(--text-muted); font-size: 23rpx; line-height: 1.5; }
.timeline-comment { margin-top: 12rpx; color: #4e5969; font-size: 24rpx; line-height: 1.5; }
.process-nodes { display: flex; flex-direction: column; gap: 0; }
.process-node { position: relative; display: flex; gap: 16rpx; padding: 0 0 24rpx; }
.process-node:not(:last-child)::after { position: absolute; top: 20rpx; bottom: 0; left: 8rpx; width: 2rpx; background: #e5e7eb; content: ''; }
.process-node__mark { position: relative; z-index: 1; width: 18rpx; height: 18rpx; margin-top: 6rpx; border: 4rpx solid #cbd5e1; border-radius: 50%; background: #fff; box-sizing: border-box; }
.process-node.is-running .process-node__mark { border-color: #2563eb; background: #2563eb; box-shadow: 0 0 0 6rpx #dbeafe; }
.process-node.is-completed .process-node__mark { border-color: #16a34a; background: #16a34a; }
.process-node__copy { min-width: 0; flex: 1; }
.process-node__copy text { display: block; }
.process-node__copy text:first-child { color: var(--text-strong); font-size: 26rpx; font-weight: 650; }
.process-node__copy text:last-child { overflow: hidden; margin-top: 6rpx; color: #64748b; font-size: 22rpx; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.action-bar { display: flex; align-items: center; gap: 12rpx; padding: 12rpx 24rpx calc(12rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid var(--border-color); background: #fff; }
.action-bar :deep(.ai-button) { flex: 1; padding: 0 18rpx; }
.action-bar :deep(.ai-button--block) { width: 100%; }
.action-bar :deep(.more-action) { flex: 0 0 144rpx; padding: 0 12rpx; }
.more-list, .user-list { display: flex; flex-direction: column; gap: 12rpx; }
.more-row { display: flex; width: 100%; min-height: 104rpx; align-items: center; gap: 16rpx; margin: 0; padding: 14rpx 6rpx; border: 0; border-bottom: 1rpx solid #edf0f3; color: var(--text-strong); font-size: 28rpx; text-align: left; background: #fff; box-sizing: border-box; }
.more-row__icon { display: flex; width: 54rpx; height: 54rpx; flex: 0 0 54rpx; align-items: center; justify-content: center; border-radius: 12rpx; background: #eff6ff; }
.more-row__copy { min-width: 0; flex: 1; }
.more-row__copy text { display: block; }
.more-row__copy text:first-child { color: var(--text-strong); font-size: 27rpx; font-weight: 650; }
.more-row__copy text:last-child { overflow: hidden; margin-top: 5rpx; color: #94a3b8; font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }
.more-row.danger .more-row__icon { background: #fff7ed; }
.more-row.danger .more-row__copy text:first-child { color: #c2410c; }
.delegate-search { margin-bottom: 16rpx; }
.delegate-comment, .delegate-signature { display: flex; flex-direction: column; gap: 12rpx; margin-top: 18rpx; }
.delegate-choice { display: flex; align-items: center; gap: 14rpx; margin-bottom: 16rpx; padding: 14rpx 16rpx; border: 1rpx solid #bfdbfe; border-radius: 12rpx; background: #f8fbff; }
.delegate-choice__avatar, .user-avatar { display: flex; align-items: center; justify-content: center; border-radius: 50%; color: #1d4ed8; font-weight: 700; background: #dbeafe; }
.delegate-choice__avatar { width: 52rpx; height: 52rpx; flex: 0 0 52rpx; font-size: 24rpx; }
.delegate-choice__copy { min-width: 0; flex: 1; }
.delegate-choice__copy text { display: block; }
.delegate-choice__copy text:first-child { color: #64748b; font-size: 20rpx; }
.delegate-choice__copy text:last-child { overflow: hidden; margin-top: 3rpx; color: var(--text-strong); font-size: 26rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.user-row { display: flex; width: 100%; min-height: 88rpx; align-items: center; gap: 14rpx; margin: 0; padding: 14rpx 8rpx; border: 1rpx solid #edf0f3; border-radius: 12rpx; color: var(--text-strong); font-size: 28rpx; text-align: left; background: #fff; box-sizing: border-box; }
.user-avatar { width: 50rpx; height: 50rpx; flex: 0 0 50rpx; font-size: 23rpx; }
.user-copy { min-width: 0; flex: 1; }
.user-check { display: flex; width: 32rpx; height: 32rpx; flex: 0 0 32rpx; align-items: center; justify-content: center; border: 1rpx solid #cbd5e1; border-radius: 50%; box-sizing: border-box; }
.user-check.active { border-color: #2563eb; background: #2563eb; }
.more-row::after, .user-row::after { border: 0; }
.user-row.active { border-color: #93c5fd; background: #f8fbff; }
.user-name { color: var(--text-strong); font-size: 27rpx; }
.user-meta { margin-top: 6rpx; color: var(--text-muted); font-size: 22rpx; }
.load-more-users { height: 60rpx; margin: 4rpx 0 0; border: 1rpx solid #e2e8f0; border-radius: 10rpx; color: #2563eb; font-size: 23rpx; background: #f8fbff; }
.load-more-users::after { border: 0; }
</style>
