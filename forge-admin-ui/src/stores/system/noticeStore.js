import { defineStore } from 'pinia'
import { computed, nextTick, ref, watch } from 'vue'
import { noticeApi } from '@/api/notice'
import { useAuthStore, useUserStore } from '@/store'

function unwrap(response) {
  if (response?.code !== 200)
    throw new Error(response?.msg || response?.message || '公告请求失败')
  return response.data
}

export const useNoticeStore = defineStore('system-notice', () => {
  const auth = useAuthStore()
  const user = useUserStore()
  const notices = ref([])
  const unreadCount = ref(0)
  const total = ref(0)
  const page = ref(0)
  const loading = ref(false)
  const error = ref('')
  const readIds = ref(new Set())
  const readVersion = ref(0)
  const contextVersion = ref(0)
  const showDetail = ref(false)
  const currentNotice = ref(null)
  const detailLoading = ref(false)
  const detailError = ref('')
  const readError = ref('')
  const hasMore = computed(() => notices.value.length < total.value)
  const ready = computed(() => Boolean(auth.accessToken && user.userId))
  let pending = null
  let countSequence = 0
  let detailSequence = 0
  const pendingReads = new Map()

  function isUnread(notice) {
    return Number(notice?.isRead) === 0 && !readIds.value.has(String(notice.noticeId))
  }

  async function refreshCount() {
    if (!ready.value)
      return
    const context = contextVersion.value
    const sequence = ++countSequence
    const count = unwrap(await noticeApi.unreadCount())
    if (context === contextVersion.value && sequence === countSequence)
      unreadCount.value = Number(count) || 0
  }

  function loadPage(append = false) {
    if (!ready.value)
      return Promise.resolve()
    if (pending)
      return pending
    const context = contextVersion.value
    const nextPage = append ? page.value + 1 : 1
    loading.value = true
    error.value = ''
    const task = (async () => {
      try {
        const data = unwrap(await noticeApi.page(nextPage))
        if (context !== contextVersion.value)
          return
        const records = data?.records || []
        const merged = append ? [...notices.value, ...records] : records
        notices.value = [...new Map(merged.map(item => [String(item.noticeId), item])).values()]
        total.value = Number(data?.total) || 0
        page.value = nextPage
      }
      catch (cause) {
        if (context === contextVersion.value)
          error.value = cause?.message || '加载公告失败，请重试'
      }
      finally {
        if (context === contextVersion.value) {
          loading.value = false
          pending = null
        }
      }
    })()
    pending = task
    return task
  }

  async function refresh() {
    const context = contextVersion.value
    await Promise.all([
      loadPage(),
      refreshCount().catch((cause) => {
        if (context === contextVersion.value)
          error.value = cause?.message || '加载公告未读数失败'
      }),
    ])
  }

  function rememberRead(noticeId) {
    const id = String(noticeId)
    if (!readIds.value.has(id)) {
      readIds.value.add(id)
      readVersion.value += 1
    }
    notices.value.forEach((notice) => {
      if (String(notice.noticeId) === id)
        notice.isRead = 1
    })
    if (String(currentNotice.value?.noticeId) === id)
      currentNotice.value.isRead = 1
  }

  async function confirmRead() {
    const notice = currentNotice.value
    if (!showDetail.value || !notice || !isUnread(notice))
      return
    const id = String(notice.noticeId)
    if (pendingReads.has(id))
      return pendingReads.get(id)
    const context = contextVersion.value
    readError.value = ''
    const task = (async () => {
      try {
        unwrap(await noticeApi.markRead(notice.noticeId))
        if (context !== contextVersion.value)
          return
        ++countSequence
        rememberRead(notice.noticeId)
        unreadCount.value = Math.max(0, unreadCount.value - 1)
        await refreshCount()
      }
      catch (cause) {
        if (context === contextVersion.value) {
          if (String(currentNotice.value?.noticeId) === id)
            readError.value = '阅读状态同步失败，请重试'
          console.error('同步公告阅读状态失败:', cause)
        }
      }
      finally {
        if (context === contextVersion.value)
          pendingReads.delete(id)
      }
    })()
    pendingReads.set(id, task)
    return task
  }

  async function openNotice(notice) {
    if (!ready.value || !notice?.noticeId)
      return
    const context = contextVersion.value
    const sequence = ++detailSequence
    showDetail.value = true
    currentNotice.value = null
    detailError.value = ''
    readError.value = ''
    detailLoading.value = true
    try {
      const data = unwrap(await noticeApi.detail(notice.noticeId))
      if (context !== contextVersion.value || sequence !== detailSequence || !showDetail.value)
        return
      if (!data)
        throw new Error('公告不存在或已不可见')
      currentNotice.value = data
      if (Number(data.isRead) === 1)
        rememberRead(data.noticeId)
      detailLoading.value = false
      // 等待正文渲染；仅打开通知面板不会触发已读。
      await nextTick()
      if (sequence === detailSequence && context === contextVersion.value)
        await confirmRead()
    }
    catch (cause) {
      if (context === contextVersion.value && sequence === detailSequence)
        detailError.value = cause?.message || '加载公告详情失败'
    }
    finally {
      if (context === contextVersion.value && sequence === detailSequence)
        detailLoading.value = false
    }
  }

  watch(() => [auth.accessToken, user.userId, user.tenantId, user.activeOrgId], () => {
    contextVersion.value += 1
    ++detailSequence
    ++countSequence
    pending = null
    pendingReads.clear()
    notices.value = []
    unreadCount.value = 0
    total.value = 0
    page.value = 0
    readIds.value = new Set()
    readVersion.value = 0
    showDetail.value = false
    currentNotice.value = null
    loading.value = false
    detailLoading.value = false
    error.value = ''
    detailError.value = ''
    readError.value = ''
  }, { flush: 'sync' })

  return {
    notices,
    unreadCount,
    total,
    loading,
    error,
    hasMore,
    readVersion,
    contextVersion,
    showDetail,
    currentNotice,
    detailLoading,
    detailError,
    readError,
    ready,
    isUnread,
    refresh,
    refreshCount,
    loadMore: () => loadPage(true),
    openNotice,
    confirmRead,
  }
})
