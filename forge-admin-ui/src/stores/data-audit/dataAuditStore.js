import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  dataAuditAdminPage,
  dataAuditEventDetail,
  dataAuditFieldPage,
  dataAuditRecordPage,
  dataAuditReveal,
} from '@/api/data-audit'
import { useAuthStore, useUserStore } from '@/store'

function unwrap(response) {
  if (response?.code !== 200)
    throw new Error(response?.message || response?.msg || '数据审计请求失败')
  return response.data
}

function contextKey() {
  const auth = useAuthStore()
  const user = useUserStore()
  return `${user.tenantId || ''}:${user.userId || ''}:${auth.accessToken ? '1' : '0'}`
}

export const useDataAuditStore = defineStore('data-audit', () => {
  const events = ref([])
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const error = ref('')
  const emptyReason = ref('none')
  const currentKey = ref('')
  const fieldsByEvent = ref({})
  const revealCache = ref({})
  let sequence = 0
  let sessionKey = ''

  function ensureSession() {
    const next = contextKey()
    if (next !== sessionKey) {
      clear()
      sessionKey = next
    }
  }

  function clear() {
    sequence += 1
    events.value = []
    total.value = 0
    pageNum.value = 1
    error.value = ''
    emptyReason.value = 'none'
    currentKey.value = ''
    fieldsByEvent.value = {}
    revealCache.value = {}
  }

  async function loadRecordEvents({ objectId, recordId, filters = {}, page = 1, size = 20, accessMode = 'RECORD' }) {
    ensureSession()
    const key = `record:${objectId}:${recordId}`
    currentKey.value = key
    loading.value = true
    error.value = ''
    const token = ++sequence
    try {
      const data = unwrap(await dataAuditRecordPage(objectId, recordId, {
        ...filters,
        accessMode,
        pageNum: page,
        pageSize: size,
      }))
      if (token !== sequence || currentKey.value !== key)
        return
      events.value = data?.records || []
      total.value = Number(data?.total || 0)
      pageNum.value = page
      pageSize.value = size
      emptyReason.value = events.value.length ? 'none' : 'empty'
    }
    catch (err) {
      if (token !== sequence)
        return
      error.value = err?.message || '加载变更记录失败'
      emptyReason.value = 'error'
      events.value = []
      total.value = 0
    }
    finally {
      if (token === sequence)
        loading.value = false
    }
  }

  async function loadAdminEvents(filters = {}, page = 1, size = 20) {
    ensureSession()
    currentKey.value = 'admin'
    loading.value = true
    error.value = ''
    const token = ++sequence
    try {
      const data = unwrap(await dataAuditAdminPage({
        ...filters,
        accessMode: 'AUDIT',
        pageNum: page,
        pageSize: size,
      }))
      if (token !== sequence)
        return
      events.value = data?.records || []
      total.value = Number(data?.total || 0)
      pageNum.value = page
      pageSize.value = size
      emptyReason.value = events.value.length ? 'none' : 'empty'
    }
    catch (err) {
      if (token !== sequence)
        return
      error.value = err?.message || '加载数据变更记录失败'
      emptyReason.value = 'error'
      events.value = []
    }
    finally {
      if (token === sequence)
        loading.value = false
    }
  }

  async function loadEventDetail(eventId, accessMode = 'AUDIT') {
    ensureSession()
    return unwrap(await dataAuditEventDetail(eventId, { accessMode }))
  }

  async function loadEventFields(eventId, params = {}) {
    ensureSession()
    const data = unwrap(await dataAuditFieldPage(eventId, {
      accessMode: 'AUDIT',
      pageNum: 1,
      pageSize: 100,
      ...params,
    }))
    fieldsByEvent.value = {
      ...fieldsByEvent.value,
      [String(eventId)]: data?.records || [],
    }
    return data
  }

  async function revealField(eventId, fieldId, reason, accessMode = 'AUDIT') {
    ensureSession()
    const data = unwrap(await dataAuditReveal(eventId, fieldId, { reason, accessMode }))
    revealCache.value = {
      ...revealCache.value,
      [`${eventId}:${fieldId}`]: data,
    }
    return data
  }

  return {
    events,
    total,
    pageNum,
    pageSize,
    loading,
    error,
    emptyReason,
    fieldsByEvent,
    revealCache,
    loadRecordEvents,
    loadAdminEvents,
    loadEventDetail,
    loadEventFields,
    revealField,
    clear,
  }
})
