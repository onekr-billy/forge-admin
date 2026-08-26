<template>
  <view class="todo-page">
    <view class="todo-content">
      <view class="todo-header">
        <view>
          <text class="todo-title">待办</text>
          <text class="todo-summary">集中处理分配给你的审批事项</text>
        </view>
        <button class="refresh-button" @click="refreshList">
          <AiIcon icon="/static/icons/ai-icon/refresh-cw.svg" color="#2563eb" size="sm" />
        </button>
      </view>

      <view class="todo-tools">
        <view class="work-scope-tabs">
          <button v-for="scope in workScopes" :key="scope.value" class="work-scope-tab" :class="{ active: activeScope === scope.value }" @click="setScope(scope.value)">
            {{ scope.label }}
          </button>
        </view>
        <AiSearchBar v-model="keyword" placeholder="搜索流程名称" @search="handleSearch" @clear="clearSearch" />
        <scroll-view class="filter-scroll" scroll-x :show-scrollbar="false">
          <view class="filter-list">
            <button
              v-if="activeScope === 'todo'"
              v-for="item in statusFilters"
              :key="item.value"
              class="filter-button"
              :class="{ active: statusFilter === item.value }"
              @click="setStatusFilter(item.value)"
            >
              {{ item.label }}
            </button>
            <button class="filter-button category-trigger" :class="{ active: categoryFilter }" @click="categoryPickerVisible = true">
              <text>{{ selectedCategoryLabel }}</text>
              <AiIcon icon="/static/icons/ai-icon/chevron-down.svg" :color="categoryFilter ? '#ffffff' : '#64748b'" size="xs" />
            </button>
          </view>
        </scroll-view>
      </view>

      <scroll-view class="todo-list" scroll-y :show-scrollbar="false" @scrolltolower="loadMore">
        <AiListSkeleton v-if="loading && !tasks.length" :rows="6" />
        <template v-else-if="tasks.length">
          <view v-for="task in tasks" :key="taskKey(task)" class="task-card" @click="openTask(task)">
            <view class="task-card__head">
              <view class="task-card__title">
                <view class="priority-mark" :class="priorityClass(task)" />
                <text>{{ taskTitle(task) }}</text>
              </view>
              <text class="status-tag" :class="{ pending: isCandidateTask(task), done: activeScope === 'done' }">{{ statusText(task) }}</text>
            </view>
            <view class="task-card__node">
              <text class="meta-key">当前节点</text>
              <text class="meta-value">{{ task.taskName || task.name || '审批节点' }}</text>
            </view>
            <view class="task-card__meta-grid">
              <view class="task-meta-item">
                <text class="meta-key">申请人</text>
                <text class="meta-value">{{ task.startUserName || task.createByName || '-' }}</text>
              </view>
              <view class="task-meta-item">
                <text class="meta-key">流程分类</text>
                <text class="meta-value">{{ task.categoryName || task.category || '-' }}</text>
              </view>
              <view class="task-meta-item task-meta-item--wide">
                <text class="meta-key">提交时间</text>
                <text class="meta-value">{{ task.createTime || task.startTime || '-' }}</text>
              </view>
            </view>
            <view class="task-card__footer">
              <text class="task-process">{{ task.processName || task.processDefinitionName || '流程审批' }}</text>
              <button v-if="activeScope === 'started' && canWithdraw(task)" class="claim-button" @click.stop="withdrawTask(task)">撤回</button>
              <button v-else-if="isCandidateTask(task)" class="claim-button" @click.stop="claimTask(task)">签收</button>
              <AiIcon v-else icon="/static/icons/ai-icon/chevron-right.svg" color="#94a3b8" size="sm" />
            </view>
          </view>
          <AiListSkeleton v-if="loading" :rows="2" compact />
          <view v-else class="list-foot">{{ hasMore ? '上拉加载更多' : `没有更多${scopeLabel}` }}</view>
        </template>
        <view v-else class="state-box">
          <AiIcon icon="/static/icons/ai-icon/check-circle.svg" color="#1677ff" size="lg" />
          <text class="state-title">{{ flowServiceUnavailable ? '流程服务不可用' : `暂无${scopeLabel}` }}</text>
          <text class="state-copy">{{ emptyDescription }}</text>
        </view>
      </scroll-view>
    </view>

    <AiTabBar active="todo" />

    <AiPopupSheet v-model="categoryPickerVisible" title="流程分类" description="选择后自动刷新待办列表">
      <view class="category-picker-list">
        <button
          v-for="option in categoryOptions"
          :key="String(option.value)"
          class="category-picker-row"
          :class="{ active: String(option.value) === String(categoryFilter) }"
          @click="selectCategory(option.value)"
        >
          <text>{{ option.label }}</text>
          <AiIcon v-if="String(option.value) === String(categoryFilter)" icon="/static/icons/ai-icon/check.svg" color="#2563eb" size="sm" />
        </button>
      </view>
    </AiPopupSheet>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import AiIcon from '@/components/AiIcon.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import AiSearchBar from '@/components/AiSearchBar.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'
import AiTabBar from '@/components/AiTabBar.vue'
import api from '@/api'
import { useAuthStore } from '@/store'
import { toast } from '@/utils/notify'

const authStore = useAuthStore()
const tasks = ref([])
const pageNum = ref(1)
const pageSize = ref(15)
const total = ref(0)
const keyword = ref('')
const activeScope = ref('todo')
const statusFilter = ref('')
const categoryFilter = ref('')
const categoryOptions = ref([{ label: '全部流程', value: '' }])
const categoryPickerVisible = ref(false)
const loading = ref(false)
const flowServiceUnavailable = ref(false)

const statusFilters = [
  { label: '全部', value: '' },
  { label: '待签收', value: '0' },
  { label: '处理中', value: '1' },
]
const workScopes = [
  { label: '待处理', value: 'todo' },
  { label: '已处理', value: 'done' },
  { label: '我发起的', value: 'started' },
]
const userId = computed(() => authStore.userInfo?.id || authStore.userInfo?.userId || authStore.userInfo?.user_id || '')
const hasMore = computed(() => tasks.value.length < total.value)
const selectedCategoryLabel = computed(() => categoryOptions.value.find(item => String(item.value) === String(categoryFilter.value))?.label || '全部流程')
const scopeLabel = computed(() => workScopes.find(item => item.value === activeScope.value)?.label || '待办')
const emptyDescription = computed(() => flowServiceUnavailable.value
  ? '请确认流程服务可用后重试'
  : keyword.value ? `没有符合当前条件的${scopeLabel.value}` : `当前没有${scopeLabel.value}`)

onShow(async () => {
  if (!tasks.value.length) {
    await loadTasks({ reset: true })
  }
  if (categoryOptions.value.length === 1) {
    loadCategories()
  }
})

onPullDownRefresh(async () => {
  try { await loadTasks({ reset: true }) } finally { uni.stopPullDownRefresh() }
})

onReachBottom(loadMore)

async function loadTasks({ reset = false } = {}) {
  if (loading.value || (!reset && !hasMore.value)) return
  if (reset) {
    pageNum.value = 1
    total.value = 0
    tasks.value = []
  }
  loading.value = true
  try {
    flowServiceUnavailable.value = false
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      userId: userId.value || undefined,
      title: keyword.value.trim() || undefined,
      status: activeScope.value === 'todo' && statusFilter.value !== '' ? Number(statusFilter.value) : undefined,
      category: categoryFilter.value || undefined,
    }
    const res = await resolveScopeApi()(params)
    const page = normalizePage(res?.data)
    tasks.value = reset ? page.records : tasks.value.concat(page.records)
    total.value = page.total
    pageNum.value += 1
  }
  catch (error) {
    flowServiceUnavailable.value = isFlowServiceUnavailableError(error)
    if (!flowServiceUnavailable.value) console.error('加载待办失败:', error)
  }
  finally { loading.value = false }
}

function loadMore() { loadTasks() }
function refreshList() { loadTasks({ reset: true }) }
function handleSearch() { loadTasks({ reset: true }) }
function clearSearch() { keyword.value = ''; handleSearch() }
function setStatusFilter(value) { if (statusFilter.value !== value) { statusFilter.value = value; loadTasks({ reset: true }) } }
function setScope(value) {
  if (activeScope.value === value) return
  activeScope.value = value
  statusFilter.value = ''
  loadTasks({ reset: true })
}
function selectCategory(value) {
  const nextValue = value === undefined || value === null ? '' : String(value)
  if (categoryFilter.value !== nextValue) categoryFilter.value = nextValue
  categoryPickerVisible.value = false
  loadTasks({ reset: true })
}

async function loadCategories() {
  try {
    const res = await api.getFlowCategories()
    categoryOptions.value = [{ label: '全部流程', value: '' }, ...flattenCategories(res?.data)]
  }
  catch (error) { console.error('加载流程分类失败:', error) }
}

async function claimTask(task) {
  const taskId = task.taskId || task.id
  if (!taskId) return
  try {
    await api.claimFlowTask(taskId, userId.value)
    toast('签收成功', { type: 'success' })
    await loadTasks({ reset: true })
  }
  catch (error) { console.error('签收待办失败:', error) }
}

async function withdrawTask(task) {
  const processInstanceId = task.processInstanceId
  if (!processInstanceId) return toast('该流程缺少实例标识，无法撤回', { type: 'warning' })
  try {
    await api.withdrawFlowProcess({ processInstanceId: String(processInstanceId), userId: String(userId.value) })
    toast('撤回成功', { type: 'success' })
    await loadTasks({ reset: true })
  }
  catch (error) { console.error('撤回流程失败:', error) }
}

function openTask(task) {
  const taskId = task.taskId || task.id
  if (!taskId) return toast('待办任务缺少标识', { type: 'warning' })
  try { uni.setStorageSync(`flow-task:${taskId}`, task) } catch (error) { console.warn('缓存流程摘要失败:', error) }
  const mode = activeScope.value === 'todo' ? 'todo' : 'readonly'
  uni.navigateTo({ url: `/pages/todo-detail?taskId=${encodeURIComponent(String(taskId))}&mode=${mode}` })
}

function normalizePage(data) {
  const records = data?.records || data?.list || data?.rows || []
  const safeRecords = Array.isArray(records) ? records : []
  return { records: safeRecords, total: Number(data?.total ?? data?.totalCount ?? safeRecords.length) || 0 }
}
function flattenCategories(items = [], result = []) {
  ;(Array.isArray(items) ? items : []).forEach((item) => {
    const value = item.category || item.code || item.id || item.value
    const label = item.categoryName || item.name || item.label || value
    if (value) result.push({ label: String(label), value: String(value) })
    if (item.children?.length) flattenCategories(item.children, result)
  })
  return result
}
function isCandidateTask(task = {}) { return Number(task.status) === 0 && !task.assignee }
function taskKey(task) { return task.taskId || task.id || task.processInstanceId || task.title }
function taskTitle(task = {}) { return task.title || task.businessTitle || task.processName || task.processDefinitionName || task.taskName || '审批任务' }
function statusText(task) {
  if (isCandidateTask(task)) return '待签收'
  if (activeScope.value === 'done') return '已处理'
  if (activeScope.value === 'started') return canWithdraw(task) ? '进行中' : '已结束'
  return Number(task.status) === 1 ? '处理中' : '待处理'
}
function canWithdraw(task) { return Number(task.status) === 0 || Number(task.status) === 1 || ['RUNNING', 'IN_PROCESS'].includes(String(task.status || '').toUpperCase()) }
function resolveScopeApi() { return activeScope.value === 'done' ? api.getDoneFlowTasks : activeScope.value === 'started' ? api.getStartedFlowTasks : api.getTodoTasks }
function priorityClass(task) { return Number(task.priority || 0) >= 3 ? 'urgent' : Number(task.priority || 0) >= 2 ? 'high' : '' }
function isFlowServiceUnavailableError(error) {
  const status = Number(error?.code || error?.error?.status || 0)
  return error?.code === 'NETWORK_ERROR' || status === 404 || (status === 500 && !error?.error?.data)
}
</script>

<style lang="scss" scoped>
.todo-page { display: flex; height: 100vh; flex-direction: column; background: var(--page-bg); }
.todo-header { display: flex; align-items: center; justify-content: space-between; padding: calc(24rpx + env(safe-area-inset-top)) 24rpx 18rpx; background: #fff; }
.todo-title, .todo-summary, .task-card__title text, .task-card__node text, .task-meta-item text, .task-process, .status-tag, .state-title, .state-copy { display: block; }
.todo-title { color: var(--text-strong); font-size: 38rpx; font-weight: 650; line-height: 1.2; }
.todo-summary { margin-top: 6rpx; color: var(--text-muted); font-size: 23rpx; }
.refresh-button { display: flex; width: 64rpx; height: 64rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 1rpx solid var(--border-color); border-radius: 8rpx; background: #fff; }
.refresh-button::after, .clear-button::after, .filter-button::after, .claim-button::after { border: 0; }
.todo-tools { padding: 0 24rpx 16rpx; border-bottom: 1rpx solid var(--border-color); background: #fff; }
.search-box { display: flex; height: 72rpx; align-items: center; gap: 12rpx; padding: 0 18rpx; border: 1rpx solid var(--border-color); border-radius: 8rpx; background: #f7f8fa; }
.search-input { min-width: 0; height: 68rpx; flex: 1; color: var(--text-strong); font-size: 26rpx; }
:deep(.search-placeholder) { color: #86909c; }
.clear-button { width: 40rpx; height: 40rpx; margin: 0; padding: 0; border: 0; color: #86909c; font-size: 34rpx; background: transparent; }
.filter-scroll { margin-top: 14rpx; white-space: nowrap; }
.filter-list { display: inline-flex; gap: 12rpx; }
.filter-button { display: inline-flex; height: 52rpx; align-items: center; gap: 4rpx; margin: 0; padding: 0 18rpx; border: 1rpx solid var(--border-color); border-radius: 6rpx; color: #4e5969; font-size: 23rpx; background: #fff; }
.filter-button.active { border-color: #b7d7ff; color: var(--primary-color); background: #e8f3ff; }
.todo-list { height: 0; flex: 1; padding: 0 24rpx 140rpx; box-sizing: border-box; }
.task-card { padding: 20rpx; border: 1rpx solid var(--border-color); border-radius: 16rpx; background: #fff; }
.task-card__head, .task-card__footer { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12rpx; }
.task-card__title { display: flex; min-width: 0; align-items: center; gap: 10rpx; }
.task-card__title text { overflow: hidden; color: var(--text-strong); font-size: 28rpx; font-weight: 700; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.priority-mark { width: 6rpx; height: 30rpx; flex: 0 0 6rpx; border-radius: 4rpx; background: #b7d7ff; }
.priority-mark.high { background: #ffb74d; }
.priority-mark.urgent { background: #d8a129; }
.task-card__node { display: flex; min-width: 0; gap: 14rpx; margin-top: 16rpx; }
.task-card__meta-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14rpx 20rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f0f1f3; }
.task-meta-item { min-width: 0; }
.task-meta-item--wide { grid-column: span 2; }
.meta-key { flex: 0 0 auto; color: #94a3b8; font-size: 21rpx; }
.meta-value { overflow: hidden; color: #4e5969; font-size: 22rpx; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.task-card__footer { margin-top: 16rpx; }
.task-process { overflow: hidden; flex: 1; color: #64748b; font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }
.status-tag { padding: 4rpx 8rpx; border-radius: 4rpx; color: #4e5969; font-size: 21rpx; background: #eaebed; }
.status-tag.pending { color: #ad6800; background: #fff7e8; }
.claim-button { height: 48rpx; margin: 0; padding: 0 14rpx; border: 1rpx solid #b7d7ff; border-radius: 6rpx; color: var(--primary-color); font-size: 22rpx; background: #fff; }
.state-box { display: flex; min-height: 420rpx; flex-direction: column; align-items: center; justify-content: center; gap: 16rpx; color: var(--text-muted); font-size: 25rpx; text-align: center; }
.state-title { color: var(--text-strong); font-size: 29rpx; font-weight: 600; }
.state-copy { max-width: 480rpx; line-height: 1.5; }
.list-foot { padding: 28rpx 0; color: var(--text-muted); font-size: 22rpx; text-align: center; }

.todo-page { background: var(--page-bg); }
.todo-content { display: flex; min-height: 0; flex: 1; flex-direction: column; gap: 20rpx; padding: calc(24rpx + env(safe-area-inset-top)) 24rpx 160rpx; box-sizing: border-box; }
.todo-header { padding: 0; background: transparent; }
.todo-title { font-size: 38rpx; font-weight: 800; }
.todo-summary { margin-top: 6rpx; font-size: 23rpx; font-weight: 500; }
.refresh-button { width: 68rpx; height: 68rpx; border-radius: 16rpx; background: #fff; }
.todo-tools { padding: 0; border: 0; background: transparent; }
.work-scope-tabs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8rpx; padding: 6rpx; border: 1rpx solid #e4eaf1; border-radius: 16rpx; background: #f8fafc; }
.work-scope-tab { height: 56rpx; margin: 0; padding: 0 6rpx; border: 0; border-radius: 11rpx; color: #64748b; font-size: 23rpx; font-weight: 600; line-height: 56rpx; background: transparent; }
.work-scope-tab::after { border: 0; }
.work-scope-tab.active { color: #1d4ed8; background: #fff; box-shadow: 0 1rpx 4rpx rgba(15, 23, 42, .08); }
.filter-scroll { margin-top: 14rpx; }
.filter-list { gap: 12rpx; padding-right: 24rpx; }
.filter-button { height: 64rpx; padding: 0 20rpx; border-radius: 16rpx; font-size: 24rpx; background: #fff; }
.category-trigger { display: inline-flex; align-items: center; gap: 8rpx; }
.filter-button.active { border-color: #2563eb; color: #fff; background: #2563eb; }
.todo-list { min-height: 0; height: 0; padding: 0; }
.task-card { margin-bottom: 12rpx; padding: 20rpx; }
.task-card__title { gap: 10rpx; }
.task-card__title text { font-size: 27rpx; font-weight: 700; }
.priority-mark { height: 28rpx; }
.task-card__node { margin-top: 14rpx; }
.task-card__meta-grid { margin-top: 14rpx; padding-top: 14rpx; }
.meta-key { font-size: 20rpx; }
.meta-value { font-size: 21rpx; }
.status-tag { padding: 4rpx 8rpx; font-size: 20rpx; }
.status-tag.done { color: #15803d; background: #f0fdf4; }
.claim-button { height: 44rpx; border-radius: 8rpx; font-size: 21rpx; }
.category-picker-list { display: flex; flex-direction: column; gap: 10rpx; }
.category-picker-row { display: flex; width: 100%; min-height: 78rpx; align-items: center; justify-content: space-between; margin: 0; padding: 0 18rpx; border: 1rpx solid #edf0f3; border-radius: 12rpx; color: #475569; font-size: 27rpx; text-align: left; background: #fff; box-sizing: border-box; }
.category-picker-row::after { border: 0; }
.category-picker-row.active { border-color: #bfdbfe; color: var(--primary-color); font-weight: 650; background: #eff6ff; }
</style>
