<template>
  <view class="message-page">
    <view class="message-content">
      <view class="page-head">
        <button class="back-button" @click="goBack">
          <AiIcon name="chevron-left" color="#475569" size="md" />
        </button>
        <view class="title-block">
          <text class="page-title">消息中心</text>
          <text class="page-subtitle">通知、审批提醒和系统消息集中处理</text>
        </view>
        <button class="refresh-button" @click="refresh">
          <AiIcon name="refresh-cw" color="#2563eb" size="sm" />
        </button>
      </view>

      <view class="filter-panel">
        <AiSearchBar
          v-model="keyword"
          placeholder="搜索标题或内容"
          @search="refresh"
          @clear="refresh"
        />
        <scroll-view class="tab-scroll" scroll-x :show-scrollbar="false">
          <view class="tab-row">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="filter-tab"
              :class="{ active: activeTab === tab.key }"
              @click="switchTab(tab.key)"
            >
              <text>{{ tab.label }}</text>
            </button>
          </view>
        </scroll-view>
        <button v-if="unreadCount > 0" class="mark-read-button" @click="markAllRead">
          <AiIcon name="check" color="#2563eb" size="sm" />
          <text>全部标为已读</text>
        </button>
      </view>

      <view class="message-list">
        <AiListSkeleton v-if="loading" :rows="6" />

        <AiEmpty
          v-else-if="filteredMessages.length === 0"
          title="暂无消息"
          description="当前筛选条件下没有站内消息。"
          icon="inbox"
        />

        <template v-else>
          <view
            v-for="item in filteredMessages"
            :key="item.id"
            class="message-card"
            :class="{ unread: item.readFlag === 0 }"
            @click="openDetail(item)"
          >
            <view class="message-icon" :class="getToneClass(item)">
              <AiIcon :name="isApprovalMessage(item) ? 'check-square' : 'message-square'" :color="getToneColor(item)" size="md" />
            </view>
            <view class="message-main">
              <view class="message-meta">
                <AiTag :type="getTagType(item)" size="small" round>
                  {{ getMessageCategory(item) }}
                </AiTag>
                <text class="message-time">{{ formatMessageTime(item.createTime || item.receiveTime) }}</text>
              </view>
              <view class="message-title-row">
                <text class="message-title">{{ item.title || '消息通知' }}</text>
                <view v-if="item.readFlag === 0" class="unread-dot" />
              </view>
              <text class="message-desc">{{ stripHtml(item.content || item.description || '-') }}</text>
            </view>
          </view>
        </template>
      </view>
    </view>

    <AiPopupSheet
      v-model="showDetail"
      :title="currentMessage?.title || '消息详情'"
      :description="detailDescription"
      max-height="84vh"
      body-max-height="calc(84vh - 230rpx - env(safe-area-inset-bottom))"
    >
      <view class="detail-content">
        <view class="detail-tags">
          <AiTag :type="getTagType(currentMessage)" size="small" round>
            {{ getMessageCategory(currentMessage) }}
          </AiTag>
          <AiTag :type="currentMessage?.readFlag === 0 ? 'danger' : 'success'" size="small" round>
            {{ currentMessage?.readFlag === 0 ? '未读' : '已读' }}
          </AiTag>
        </view>
        <rich-text v-if="detailHtml" class="detail-html" :nodes="detailHtml" />
        <text v-else class="detail-empty">暂无正文内容</text>

      </view>

      <template #footer>
        <view class="detail-actions">
          <AiButton
            v-if="currentMessage?.readFlag === 0"
            variant="secondary"
            size="sm"
            @click="markRead(currentMessage)"
          >
            标记已读
          </AiButton>
          <AiButton
            v-if="currentMessage?.bizType || currentMessage?.jumpUrl"
            size="sm"
            @click="openBiz(currentMessage)"
          >
            查看业务
          </AiButton>
        </view>
      </template>
    </AiPopupSheet>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import AiEmpty from '@/components/AiEmpty.vue'
import AiIcon from '@/components/AiIcon.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'
import AiSearchBar from '@/components/AiSearchBar.vue'
import AiTag from '@/components/AiTag.vue'
import api from '@/api'
import { showConfirmDialog } from '@/utils/dialog'
import { toast } from '@/utils/notify'

const loading = ref(false)
const messages = ref([])
const bizTypes = ref([])
const unreadCount = ref(0)
const keyword = ref('')
const activeTab = ref('all')
const showDetail = ref(false)
const currentMessage = ref(null)
const pendingOpenId = ref('')

const tabs = computed(() => [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'approval', label: '审批提醒' },
  { key: 'read', label: '已读' },
])

const filteredMessages = computed(() => {
  return messages.value.filter((item) => {
    if (activeTab.value === 'unread' && item.readFlag !== 0) {
      return false
    }
    if (activeTab.value === 'read' && item.readFlag !== 1) {
      return false
    }
    if (activeTab.value === 'approval' && !isApprovalMessage(item)) {
      return false
    }
    return true
  })
})

const detailHtml = computed(() => currentMessage.value?.content || '')
const detailDescription = computed(() => {
  const time = formatMessageTime(currentMessage.value?.createTime || currentMessage.value?.receiveTime)
  return time ? `接收时间 ${time}` : '消息详情'
})

onLoad((query = {}) => {
  pendingOpenId.value = query.id ? String(query.id) : ''
})

onShow(async () => {
  await refresh()
  if (pendingOpenId.value) {
    const target = messages.value.find(item => String(item.id) === pendingOpenId.value)
    if (target) {
      await openDetail(target)
      pendingOpenId.value = ''
    }
  }
})

async function refresh() {
  loading.value = true
  try {
    await Promise.all([
      fetchUnreadCount(),
      fetchMessages(),
      fetchBizTypes(),
    ])
  }
  catch (error) {
    console.error('刷新消息失败:', error)
    toast('消息加载失败，请稍后重试', { type: 'error' })
  }
  finally {
    loading.value = false
  }
}

async function fetchMessages() {
  const params = {
    pageNum: 1,
    pageSize: 80,
  }
  if (keyword.value.trim()) {
    params.keyword = keyword.value.trim()
  }

  const res = await api.getMessagePage(params)
  messages.value = normalizeRecords(res?.data)
}

async function fetchUnreadCount() {
  const res = await api.getUnreadMessageCount()
  unreadCount.value = normalizeUnreadCount(res?.data)
}

async function fetchBizTypes() {
  try {
    const res = await api.getEnabledMessageBizTypes()
    bizTypes.value = Array.isArray(res?.data) ? res.data : []
  }
  catch {
    bizTypes.value = []
  }
}

function normalizeRecords(data) {
  const records = Array.isArray(data)
    ? data
    : data?.records || data?.list || data?.rows || []
  return records.map(item => ({
    ...item,
    readFlag: Number(item.readFlag ?? item.readStatus ?? 0),
  }))
}

function normalizeUnreadCount(data) {
  if (typeof data === 'number') {
    return data
  }
  return Number(data?.totalCount || data?.unreadCount || data?.count || 0)
}

function switchTab(key) {
  activeTab.value = key
}

async function openDetail(item) {
  try {
    const res = await api.getMessageDetail(item.id)
    currentMessage.value = {
      ...item,
      ...(res?.data || {}),
      readFlag: Number((res?.data || item).readFlag ?? item.readFlag ?? 0),
    }
    showDetail.value = true
    if (item.readFlag === 0) {
      await markRead(item, { silent: true })
    }
  }
  catch (error) {
    console.error('加载消息详情失败:', error)
    toast('消息详情加载失败', { type: 'error' })
  }
}

async function markRead(item, options = {}) {
  if (!item?.id || item.readFlag !== 0) {
    return
  }
  try {
    await api.markMessageRead(item.id)
    item.readFlag = 1
    if (currentMessage.value?.id === item.id) {
      currentMessage.value.readFlag = 1
    }
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (!options.silent) {
      toast('已标记为已读', { type: 'success' })
    }
  }
  catch (error) {
    console.error('标记消息已读失败:', error)
    if (!options.silent) {
      toast('操作失败', { type: 'error' })
    }
  }
}

async function markAllRead() {
  const confirmed = await showConfirmDialog({
    title: '全部已读',
    description: '确认将所有未读消息标记为已读？',
    icon: 'warning',
    confirmText: '全部已读',
    cancelText: '取消',
  })
  if (!confirmed) {
    return
  }
  try {
    await api.markAllMessagesRead()
    messages.value = messages.value.map(item => ({ ...item, readFlag: 1 }))
    unreadCount.value = 0
    toast('已全部标记为已读', { type: 'success' })
  }
  catch (error) {
    console.error('全部标记已读失败:', error)
    toast('操作失败', { type: 'error' })
  }
}

function openBiz(message) {
  const route = resolveBizRoute(message)
  showDetail.value = false
  if (route && route.startsWith('/pages/todo')) {
    const taskId = getRouteTaskId(route) || message.taskId || message.task_id
    if (taskId) {
      uni.navigateTo({ url: `/pages/todo-detail?taskId=${encodeURIComponent(String(taskId))}` })
    }
    else {
      uni.switchTab({ url: '/pages/todo' })
    }
    return
  }
  if (route && route.startsWith('/pages/')) {
    uni.navigateTo({
      url: route,
      fail: () => toast('移动端业务页暂未接入', { type: 'info' }),
    })
    return
  }
  if (isApprovalMessage(message)) {
    const taskId = message.taskId || message.task_id
    if (taskId) uni.navigateTo({ url: `/pages/todo-detail?taskId=${encodeURIComponent(String(taskId))}` })
    else uni.switchTab({ url: '/pages/todo' })
    return
  }
  toast('该消息暂无移动端业务入口', { type: 'info' })
}

function getRouteTaskId(route) {
  const match = String(route || '').match(/[?&]taskId=([^&#]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

function resolveBizRoute(message) {
  if (message?.jumpUrl) {
    return replaceRouteParams(message.jumpUrl, message)
  }
  if (!message?.bizType) {
    return ''
  }
  const config = bizTypes.value.find(item => item.bizType === message.bizType)
  return config?.jumpUrl ? replaceRouteParams(config.jumpUrl, message) : ''
}

function replaceRouteParams(route, message) {
  return String(route || '')
    .replace(/\$\{bizKey\}/g, message?.bizKey || '')
    .replace(/\$\{messageId\}/g, message?.id || '')
}

function isApprovalMessage(item) {
  return item?.bizType === 'FLOW_TODO'
}

function getMessageCategory(item) {
  if (isApprovalMessage(item)) {
    return '审批'
  }
  const map = {
    SYSTEM: '系统',
    SMS: '短信',
    EMAIL: '邮件',
    CUSTOM: '通知',
  }
  return map[item?.type] || '通知'
}

function getTagType(item) {
  if (isApprovalMessage(item)) {
    return 'success'
  }
  if (item?.readFlag === 0) {
    return 'primary'
  }
  return 'default'
}

function getToneClass(item) {
  if (isApprovalMessage(item)) {
    return 'tone-emerald'
  }
  return item?.readFlag === 0 ? 'tone-blue' : 'tone-slate'
}

function getToneColor(item) {
  if (isApprovalMessage(item)) {
    return '#10b981'
  }
  return item?.readFlag === 0 ? '#2563eb' : '#64748b'
}

function stripHtml(value) {
  return String(value || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function formatMessageTime(value) {
  const date = parseMessageDate(value)
  if (!date) {
    return ''
  }
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff >= 0 && diff < 60 * 1000) {
    return '刚刚'
  }
  if (diff >= 0 && diff < 60 * 60 * 1000) {
    return `${Math.max(1, Math.floor(diff / 60000))}分钟前`
  }
  if (diff >= 0 && diff < 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / 3600000)}小时前`
  }
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function parseMessageDate(value) {
  if (!value) {
    return null
  }
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    return new Date(year, month - 1, day, hour, minute, second)
  }
  const date = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value)
  return Number.isNaN(date.getTime()) ? null : date
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style lang="scss" scoped>
.message-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  box-sizing: border-box;
  background: #f8fafc;
}

.message-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 10%, rgba(147, 197, 253, 0.34), transparent 34%),
    radial-gradient(circle at 82% 22%, rgba(167, 243, 208, 0.26), transparent 32%),
    radial-gradient(circle at 52% 82%, rgba(226, 232, 240, 0.42), transparent 36%);
}

.grid-layer {
  position: absolute;
  inset: 0;
  opacity: 0.16;
  pointer-events: none;
  background-image:
    linear-gradient(#e2e8f0 1rpx, transparent 1rpx),
    linear-gradient(90deg, #e2e8f0 1rpx, transparent 1rpx);
  background-size: 80rpx 80rpx;
}

.page-glow {
  position: absolute;
  width: 520rpx;
  height: 520rpx;
  border-radius: 999rpx;
  filter: blur(90rpx);
  pointer-events: none;
}

.page-glow-blue {
  top: -180rpx;
  left: -160rpx;
  background: rgba(147, 197, 253, 0.42);
  animation: floatGlow 13s ease-in-out infinite;
}

.page-glow-green {
  right: -180rpx;
  bottom: 130rpx;
  background: rgba(167, 243, 208, 0.34);
  animation: floatGlow 15s ease-in-out infinite reverse;
}

.message-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: 54rpx 28rpx 48rpx;
  box-sizing: border-box;
}

.page-head {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.back-button,
.refresh-button {
  display: flex;
  width: 78rpx;
  height: 78rpx;
  flex: 0 0 78rpx;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: 1rpx solid rgba(255, 255, 255, 0.88);
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(20rpx);
}

.back-button::after,
.refresh-button::after,
.summary-action::after,
.filter-tab::after {
  border: 0;
}

.title-block {
  min-width: 0;
  flex: 1;
}

.page-title,
.page-subtitle,
.summary-label,
.summary-value,
.summary-desc {
  display: block;
}

.page-title {
  overflow: hidden;
  color: #0f172a;
  font-size: 42rpx;
  font-weight: 950;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-subtitle {
  margin-top: 8rpx;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 700;
}

.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.tab-scroll {
  width: 100%;
  white-space: nowrap;
}

.tab-row {
  display: inline-flex;
  gap: 14rpx;
  min-width: 100%;
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  height: 66rpx;
  margin: 0;
  padding: 0 24rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.86);
  border-radius: 22rpx;
  color: #64748b;
  font-size: 25rpx;
  font-weight: 850;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(18rpx);
}

.filter-tab.active {
  color: #ffffff;
  border-color: rgba(37, 99, 235, 0.2);
  background: #2563eb;
  box-shadow: 0 10rpx 24rpx rgba(37, 99, 235, 0.18);
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  min-height: 260rpx;
  color: #64748b;
  font-size: 26rpx;
  font-weight: 800;
}

.loading-spinner {
  width: 36rpx;
  height: 36rpx;
  border: 4rpx solid #bfdbfe;
  border-right-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.message-card {
  position: relative;
  display: flex;
  gap: 22rpx;
  padding: 28rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.86);
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8rpx 26rpx rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(20rpx);
}

.message-card.unread {
  border-color: rgba(147, 197, 253, 0.72);
  box-shadow: 0 12rpx 32rpx rgba(37, 99, 235, 0.08);
}

.message-icon {
  display: flex;
  width: 78rpx;
  height: 78rpx;
  flex: 0 0 78rpx;
  align-items: center;
  justify-content: center;
  border-radius: 24rpx;
}

.tone-blue {
  background: #eff6ff;
}

.tone-emerald {
  background: #ecfdf5;
}

.tone-slate {
  background: #f1f5f9;
}

.message-main {
  min-width: 0;
  flex: 1;
}

.message-meta,
.message-title-row,
.detail-tags,
.detail-actions {
  display: flex;
  align-items: center;
}

.message-meta {
  justify-content: space-between;
  gap: 18rpx;
}

.message-time {
  flex-shrink: 0;
  color: #94a3b8;
  font-size: 22rpx;
  font-weight: 700;
}

.message-title-row {
  gap: 10rpx;
  margin-top: 16rpx;
}

.message-title {
  display: block;
  min-width: 0;
  overflow: hidden;
  flex: 1;
  color: #1e293b;
  font-size: 30rpx;
  font-weight: 950;
  line-height: 1.28;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-dot {
  width: 14rpx;
  height: 14rpx;
  flex: 0 0 14rpx;
  border-radius: 999rpx;
  background: #ef4444;
}

.message-desc {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 10rpx;
  color: #64748b;
  font-size: 24rpx;
  font-weight: 650;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.detail-tags {
  gap: 12rpx;
}

.detail-html {
  color: #334155;
  font-size: 28rpx;
  line-height: 1.78;
}

.detail-empty {
  display: block;
  color: #94a3b8;
  font-size: 26rpx;
  font-weight: 700;
}

.detail-actions {
  justify-content: flex-end;
  gap: 18rpx;
}

.animate-in {
  animation: enterUp 0.56s ease both;
}

.delay-1 {
  animation-delay: 0.08s;
}

.delay-2 {
  animation-delay: 0.16s;
}

.delay-3 {
  animation-delay: 0.24s;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes enterUp {
  from {
    opacity: 0;
    transform: translateY(28rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-page,
.message-page::before {
  background: var(--page-bg);
}

.message-content {
  gap: 22rpx;
  padding: calc(24rpx + env(safe-area-inset-top)) 24rpx 56rpx;
}

.back-button,
.refresh-button {
  width: 68rpx;
  height: 68rpx;
  flex-basis: 68rpx;
  border-color: var(--border-color);
  border-radius: 16rpx;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
}

.page-title {
  color: var(--text-strong);
  font-size: 38rpx;
  font-weight: 800;
}

.page-subtitle {
  margin-top: 6rpx;
  font-size: 23rpx;
  font-weight: 500;
}

.filter-panel {
  gap: 16rpx;
}

.tab-row {
  gap: 12rpx;
  min-width: auto;
  padding-right: 24rpx;
}

.filter-tab {
  min-width: 116rpx;
  height: 70rpx;
  justify-content: center;
  padding: 0 22rpx;
  border-color: var(--border-color);
  border-radius: 16rpx;
  background: #fff;
  backdrop-filter: none;
  box-sizing: border-box;
}

.filter-tab.active {
  border-color: #2563eb;
  background: #2563eb;
  box-shadow: none;
}

.mark-read-button {
  display: inline-flex;
  width: fit-content;
  height: 56rpx;
  align-items: center;
  gap: 8rpx;
  margin: 0;
  padding: 0 4rpx;
  color: #2563eb;
  font-size: 24rpx;
  font-weight: 700;
  background: transparent;
}

.mark-read-button::after {
  border: 0;
}

.message-list {
  gap: 10rpx;
}

.message-card {
  gap: 14rpx;
  padding: 18rpx;
  border-color: var(--border-color);
  border-radius: 16rpx;
  background: #fff;
  box-shadow: none;
  backdrop-filter: none;
}

.message-card.unread {
  border-color: #b9d5ff;
  box-shadow: none;
}

.message-icon {
  width: 58rpx;
  height: 58rpx;
  flex-basis: 58rpx;
  border-radius: 15rpx;
}

.message-title-row {
  margin-top: 8rpx;
}

.message-title {
  font-size: 27rpx;
  font-weight: 700;
}

.message-desc {
  margin-top: 5rpx;
  font-size: 22rpx;
  font-weight: 500;
  -webkit-line-clamp: 1;
}

.loading-card {
  border-color: var(--border-color);
  box-shadow: none;
}

.animate-in {
  animation: none;
}

@keyframes floatGlow {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(36rpx, 28rpx, 0) scale(1.08);
  }
}
</style>
