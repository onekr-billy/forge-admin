import { useAuthStore } from '@/store/modules/auth'
import { request } from '@/utils'

const BASE_URL = import.meta.env.VITE_REQUEST_PREFIX || ''

/** 分页查询知识库 */
export function knowledgePage(params) {
  return request.get('/ai/knowledge/page', { params })
}

/** 查询知识库详情 */
export function knowledgeGetById(id) {
  return request.get(`/ai/knowledge/${id}`)
}

/** 新增知识库 */
export function knowledgeCreate(data) {
  return request.post('/ai/knowledge', data)
}

/** 修改知识库 */
export function knowledgeUpdate(data) {
  return request.put('/ai/knowledge', data)
}

/** 删除知识库 */
export function knowledgeDelete(id) {
  return request.delete(`/ai/knowledge/${id}`)
}

/** 分页查询知识库文档 */
export function knowledgeDocumentPage(params) {
  return request.get('/ai/knowledge/document/page', { params })
}

/** 上传文档（两步上传第一步） */
export function knowledgeDocumentUpload(data) {
  return request.post('/ai/knowledge/document/upload', data)
}

/** 确认处理文档（两步上传第二步） */
export function knowledgeDocumentConfirm(documentId) {
  return request.post(`/ai/knowledge/document/${documentId}/confirm`)
}

/** 重新处理失败文档 */
export function knowledgeDocumentReprocess(documentId) {
  return request.post(`/ai/knowledge/document/${documentId}/reprocess`)
}

/** 查看文档分块列表 */
export function knowledgeDocumentChunks(documentId) {
  return request.get(`/ai/knowledge/document/${documentId}/chunks`)
}

/** 查看文档原始内容 */
export function knowledgeDocumentContent(documentId) {
  return request.get(`/ai/knowledge/document/${documentId}/content`)
}

/** 删除文档 */
export function knowledgeDocumentDelete(documentId) {
  return request.delete(`/ai/knowledge/document/${documentId}`)
}

/** 订阅文档处理进度（SSE） */
export function knowledgeDocumentProgressSSE(documentId, onEvent, onComplete, onError) {
  const authStore = useAuthStore()
  const controller = new AbortController()
  fetch(`${BASE_URL}/ai/knowledge/document/${documentId}/progress`, {
    method: 'GET',
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Authorization': authStore.accessToken ? `Bearer ${authStore.accessToken}` : '',
    },
    signal: controller.signal,
  })
    .then((response) => {
      if (!response.ok)
        throw new Error(response.statusText)
      if (!response.body)
        throw new Error('浏览器不支持流式响应')
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      function read() {
        reader.read().then(({ done, value }) => {
          if (done) { onComplete(); return }
          buffer += decoder.decode(value, { stream: true })
          const events = buffer.split(/\r?\n\r?\n/)
          buffer = events.pop() || ''
          for (const eventStr of events) {
            let eventData = ''
            for (const line of eventStr.split(/\r?\n/)) {
              if (line.startsWith('data:'))
                eventData = line.slice(5).trim()
            }
            if (eventData) {
              try { eventData = JSON.parse(eventData) }
              catch { /* keep raw */ }
              onEvent(eventData)
            }
          }
          read()
        }).catch((err) => {
          if (err.name !== 'AbortError')
            onError(err)
        })
      }
      read()
    })
    .catch((err) => {
      if (err.name !== 'AbortError')
        onError(err)
    })
  return controller
}

/** 知识库检索调试 */
export function knowledgeSearch(data) {
  return request.post('/ai/knowledge/search', data)
}

/** RAG 增强检索（管线：融合/Rerank/查询补全，支持 searchType=vector/bm25/hybrid） */
export function ragSearch(data) {
  return request.post('/ai/rag/search', data)
}

/** RAG 增强检索调试（额外返回元信息：实际检索类型/各路命中数/耗时/补全query） */
export function ragSearchDebug(data) {
  return request.post('/ai/rag/search/debug', data)
}
