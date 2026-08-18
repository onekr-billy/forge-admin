import { finishGlobalLoading, startGlobalLoading } from '@/composables/useGlobalLoading'
import { useAuthStore } from '@/store/modules/auth'
import { generateUUID, postEncrypt, request } from '@/utils'

const BASE_URL = import.meta.env.VITE_REQUEST_PREFIX || ''

// ========== 智能体管理 ==========

export function agentPage(params) {
  return request.get('/ai/agent/page', { params })
}

export function agentList() {
  return request.get('/ai/agent/list')
}

export function agentGetById(id) {
  return request.get(`/ai/agent/${id}`)
}

export function agentAdd(data) {
  return request.post('/ai/agent', data)
}

export function agentUpdate(data) {
  return request.put('/ai/agent', data)
}

export function agentDelete(id) {
  return request.delete(`/ai/agent/${id}`)
}

export function streamAgentChat(data, onChunk, onComplete, onError, options = {}) {
  const { maxRetries = 0, retryDelay = 800 } = options
  const controller = new AbortController()
  const authStore = useAuthStore()
  const loadingToken = startGlobalLoading({
    globalLoadingType: 'submit',
    globalLoadingText: '智能体请求处理中，请稍候...',
  })
  let currentRetry = 0
  let isAborted = false
  let completed = false
  let loadingFinished = false

  function finishStreamLoading() {
    if (loadingFinished)
      return

    loadingFinished = true
    finishGlobalLoading(loadingToken)
  }

  controller.signal.addEventListener('abort', () => {
    isAborted = true
    finishStreamLoading()
  })

  function completeOnce(data) {
    if (completed)
      return

    completed = true
    finishStreamLoading()
    onComplete(data)
  }

  function parseEventData(eventData) {
    if (!eventData || eventData === '[DONE]')
      return null

    try {
      return JSON.parse(eventData)
    }
    catch {
      return null
    }
  }

  function parseSseBlock(block) {
    let eventType = 'message'
    const dataLines = []

    for (const rawLine of block.split(/\r?\n/)) {
      const line = rawLine.trimEnd()
      if (!line || line.startsWith(':'))
        continue

      if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      }
      else if (line.startsWith('data:')) {
        const dataLine = line.slice(5)
        dataLines.push(dataLine.startsWith(' ') ? dataLine.slice(1) : dataLine)
      }
    }

    return {
      eventType,
      eventData: dataLines.join('\n'),
    }
  }

  function processSseBlock(block) {
    if (!block.trim() || completed)
      return

    const { eventType, eventData } = parseSseBlock(block)
    const parsedData = parseEventData(eventData)

    if (eventType === 'done' || eventData === '[DONE]') {
      completeOnce(parsedData)
      return
    }

    if (eventType === 'complete') {
      completeOnce(parsedData)
      return
    }

    if (eventType === 'error') {
      completed = true
      finishStreamLoading()
      onError(parsedData?.message || parsedData?.reason || eventData || '智能体测试失败')
      return
    }

    if (eventType === 'progress') {
      onChunk({
        event: 'progress',
        data: parsedData || { message: eventData },
      })
      return
    }

    if (eventData) {
      onChunk({
        event: 'chunk',
        data: parsedData && typeof parsedData === 'object'
          ? parsedData
          : { content: eventData },
      })
    }
  }

  function doFetch() {
    completed = false
    fetch(`${BASE_URL}/ai/client/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Authorization': authStore.accessToken ? `Bearer ${authStore.accessToken}` : '',
        'X-Timestamp': Date.now().toString(),
        'X-Nonce': generateUUID(),
      },
      body: JSON.stringify(data),
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(response.statusText || `HTTP ${response.status}`)
        }

        currentRetry = 0
        if (!response.body) {
          throw new Error('当前浏览器不支持流式响应')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let hasReceivedData = false

        function read() {
          reader.read().then(({ done, value }) => {
            if (done) {
              if (!hasReceivedData) {
                handleRetry('服务器未返回数据')
                return
              }
              buffer += decoder.decode()
              if (buffer.trim()) {
                processSseBlock(buffer)
              }
              completeOnce()
              return
            }
            hasReceivedData = true
            buffer += decoder.decode(value, { stream: true })

            const events = buffer.split(/\r?\n\r?\n/)
            buffer = events.pop() || ''

            for (const eventStr of events) {
              processSseBlock(eventStr)
            }
            read()
          }).catch((error) => {
            if (error.name !== 'AbortError') {
              handleRetry(error.message)
            }
          })
        }

        read()
      })
      .catch((error) => {
        if (error.name !== 'AbortError') {
          handleRetry(error.message)
        }
      })
  }

  function handleRetry(errorMessage) {
    if (isAborted || completed)
      return

    if (currentRetry < maxRetries) {
      currentRetry++
      completed = false
      onChunk({
        event: 'progress',
        data: {
          stage: 'retrying',
          message: `连接中断，正在重试 (${currentRetry}/${maxRetries})...`,
        },
      })
      setTimeout(doFetch, retryDelay)
    }
    else {
      finishStreamLoading()
      onError(`连接失败: ${errorMessage}`)
    }
  }

  doFetch()
  return controller
}

// ========== 供应商管理 ==========

/**
 * 分页查询供应商列表
 */
export function providerPage(params) {
  return request.get('/ai/provider/page', { params })
}

/**
 * 查询供应商详情
 */
export function providerGetById(id) {
  return request.get(`/ai/provider/${id}`)
}

/**
 * 新增供应商
 */
export function providerAdd(data) {
  return postEncrypt('/ai/provider', data)
}

/**
 * 修改供应商
 */
export function providerUpdate(data) {
  return request.put('/ai/provider', data, { encrypt: true })
}

/**
 * 删除供应商
 */
export function providerDelete(id) {
  return request.delete(`/ai/provider/${id}`)
}

/**
 * 测试供应商连接
 */
export function providerTest(data) {
  // 已保存供应商仅传 { id }；未保存供应商才传完整配置。
  return postEncrypt('/ai/provider/test', data)
}

/**
 * 设为默认供应商
 */
export function providerSetDefault(id) {
  return request.put(`/ai/provider/${id}/default`)
}

/**
 * 获取供应商模板列表
 */
export function providerTemplates() {
  return request.get('/ai/provider/templates')
}

/**
 * 拉取供应商可用模型列表
 */
export function providerFetchModels(id) {
  return request.post(`/ai/provider/${id}/fetch-models`)
}

/**
 * 批量导入模型到供应商
 * @param {string|number} id 供应商 ID
 * @param {Array<{modelId: string, modelType?: string}>} items 导入项列表
 */
export function providerBatchImportModels(id, items) {
  return request.post(`/ai/provider/${id}/models/batch`, items)
}

// ========== 模型管理 ==========

/**
 * 分页查询模型列表
 */
export function modelPage(params) {
  return request.get('/ai/model/page', { params })
}

/**
 * 按供应商查询所有模型（下拉选择用）
 */
export function modelListByProvider(providerId) {
  return request.get('/ai/model/list', { params: { providerId } })
}

/**
 * 查询模型详情
 */
export function modelGetById(id) {
  return request.get(`/ai/model/${id}`)
}

/**
 * 新增模型
 */
export function modelAdd(data) {
  return request.post('/ai/model', data)
}

/**
 * 修改模型
 */
export function modelUpdate(data) {
  return request.put('/ai/model', data)
}

/**
 * 删除模型
 */
export function modelDelete(id) {
  return request.delete(`/ai/model/${id}`)
}

export function modelTest(id) {
  return request.post(`/ai/model/${id}/test`)
}

export function routePolicyPage(params) {
  return request.get('/ai/model-routing/policy/page', { params })
}

export function routePolicyGet(id) {
  return request.get(`/ai/model-routing/policy/${id}`)
}

export function routePolicyAdd(data) {
  return postEncrypt('/ai/model-routing/policy', data)
}

export function routePolicyUpdate(data) {
  return request.put('/ai/model-routing/policy', data, { encrypt: true })
}

export function routePolicyDelete(id) {
  return request.delete(`/ai/model-routing/policy/${id}`)
}

export function routePolicyPreview(data) {
  return postEncrypt('/ai/model-routing/policy/preview', data)
}

export function invocationPage(params) {
  return request.get('/ai/model-routing/invocation/page', { params })
}

export function invocationSummary(params) {
  return request.get('/ai/model-routing/invocation/summary', { params })
}

// ========== 会话管理 ==========

export function sessionPage(params) {
  return request.get('/ai/admin/session/page', { params })
}

export function sessionMessages(sessionId) {
  return request.get(`/ai/admin/session/${sessionId}/messages`)
}

export function sessionDelete(sessionId) {
  return request.delete(`/ai/admin/session/${sessionId}`)
}

export function sessionStatistics() {
  return request.get('/ai/admin/session/statistics')
}

// 当前用户会话（AI 对话页使用）
export function sessionList() {
  return request.get('/ai/session/list')
}

export function sessionMessagesByUser(sessionId) {
  return request.get(`/ai/session/${sessionId}/messages`)
}

export function sessionDeleteByUser(sessionId) {
  return request.delete(`/ai/session/${sessionId}`)
}

// ========== AiClient 通用调用 ==========

export function aiClientCall(data) {
  return request.post('/ai/client/call', data)
}

export function aiClientStream(data) {
  return request.post('/ai/client/stream', data)
}

// ========== 上下文配置管理 ==========

export function contextConfigList(agentCode) {
  return request.get('/ai/context/list', { params: { agentCode } })
}

export function contextConfigAdd(data) {
  return request.post('/ai/context/add', data)
}

export function contextConfigUpdate(data) {
  return request.put('/ai/context/update', data)
}

export function contextConfigDelete(id) {
  return request.delete(`/ai/context/${id}`)
}

// ========== CRUD 配置驱动 ==========

export function crudConfigPage(params) {
  return request.get('/ai/crud-config/page', { params })
}

export function crudConfigGetById(id) {
  return request.get(`/ai/crud-config/${id}`)
}

export function crudConfigGetByKey(configKey) {
  return request.get(`/ai/crud-config/by-key/${configKey}`)
}

export function updateSessionMetadata(sessionId, metadata) {
  return request.put(`/ai/admin/session/${sessionId}/metadata`, metadata)
}

export function crudConfigRender(configKey, designPreview = false) {
  return request.get(`/ai/crud-config/render/${configKey}`, {
    params: designPreview ? { designPreview: true } : undefined,
  })
}

export function crudConfigAdd(data) {
  return request.post('/ai/crud-config', data)
}

export function crudConfigUpdate(data) {
  return request.put('/ai/crud-config', data)
}

export function crudConfigDelete(id) {
  return request.delete(`/ai/crud-config/${id}`)
}

export function crudConfigAiGenerate(data) {
  return request.post('/ai/crud-config/ai/generate', data)
}

export function crudConfigAiGenerateFromTable(data) {
  return request.post('/ai/crud-config/ai/generateFromTable', data)
}

// ========== 自定义查询 ==========

export function customQueryExecute(configKey, data, config = {}) {
  return request.post(`/ai/custom-query/${configKey}/execute`, data, config)
}

export function customQuerySchemeList(configKey) {
  return request.get(`/ai/custom-query/${configKey}/scheme/list`)
}

export function customQuerySchemeGet(configKey, id) {
  return request.get(`/ai/custom-query/${configKey}/scheme/${id}`)
}

export function customQuerySchemeAdd(configKey, data) {
  return request.post(`/ai/custom-query/${configKey}/scheme`, data)
}

export function customQuerySchemeUpdate(configKey, data) {
  return request.put(`/ai/custom-query/${configKey}/scheme`, data)
}

export function customQuerySchemeDelete(configKey, id) {
  return request.delete(`/ai/custom-query/${configKey}/scheme/${id}`)
}

// ========== 图片生成 ==========

export function imageGenerate(data) {
  return request.post('/ai/image-generate', data)
}

export function imageGeneratePage(params) {
  return request.get('/ai/image-generate/page', { params })
}

export function imageGenerateGetResult(id) {
  return request.get(`/ai/image-generate/${id}`)
}

// ========== 语音（ASR/TTS） ==========

export function voiceAsr(formData) {
  return request.post('/ai/voice/asr', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function voiceTts(data) {
  return request.post('/ai/voice/tts', data)
}

// ========== AI 创建 Agent ==========

export function agentAiCreateSSE(description, onEvent, onComplete, onError) {
  const authStore = useAuthStore()
  const controller = new AbortController()

  fetch(`${BASE_URL}/ai/agent/ai-create`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Authorization': authStore.accessToken ? `Bearer ${authStore.accessToken}` : '',
    },
    body: JSON.stringify({ description }),
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
            let eventType = 'message'
            let eventData = ''
            for (const line of eventStr.split(/\r?\n/)) {
              if (line.startsWith('event:'))
                eventType = line.slice(6).trim()
              else if (line.startsWith('data:'))
                eventData = line.slice(5).trim()
            }
            if (eventData) {
              try { eventData = JSON.parse(eventData) }
              catch { /* keep raw */ }
              onEvent(eventType, eventData)
            }
          }
          read()
        }).catch(onError)
      }
      read()
    })
    .catch(onError)

  return controller
}

export function agentAiCreateConfirm(config) {
  return request.post('/ai/agent/ai-create/confirm', config)
}

// ========== 技能管理 ==========

export function skillPage(params) {
  return request.get('/ai/skill/page', { params })
}

export function skillGetById(id) {
  return request.get(`/ai/skill/${id}`)
}

export function skillGetFiles(id) {
  return request.get(`/ai/skill/${id}/files`)
}

export function skillGetAgentSkills(agentId) {
  return request.get(`/ai/skill/agent/${agentId}`)
}

export function skillAddAgentSkill(data) {
  return request.post(`/ai/skill/agent/${data.agentId}`, { skillId: data.skillId })
}

export function skillDeleteAgentSkill(agentId, skillId) {
  return request.delete(`/ai/skill/agent/${agentId}/${skillId}`)
}

export function skillAdd(data) {
  return request.post('/ai/skill', data)
}

export function skillUpdate(data) {
  return request.put('/ai/skill', data)
}

export function skillDelete(id) {
  return request.delete(`/ai/skill/${id}`)
}

export function skillUploadZip(formData) {
  return request.post('/ai/skill/upload-zip', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function skillAiGenerate(description) {
  return request.post('/ai/skill/ai-generate', null, { params: { description } })
}

export function skillAiOptimize(id, instruction) {
  return request.post(`/ai/skill/${id}/ai-optimize`, null, { params: { instruction } })
}

// ========== Agent 引擎对话（SSE） ==========

export function streamEngineChat(data, onEvent, onComplete, onError) {
  const authStore = useAuthStore()
  const controller = new AbortController()

  fetch(`${BASE_URL}/ai/engine/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Authorization': authStore.accessToken ? `Bearer ${authStore.accessToken}` : '',
      'X-Timestamp': Date.now().toString(),
      'X-Nonce': generateUUID(),
    },
    body: JSON.stringify(data),
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
            let eventType = 'message'
            let eventData = ''
            for (const line of eventStr.split(/\r?\n/)) {
              if (line.startsWith('event:'))
                eventType = line.slice(6).trim()
              else if (line.startsWith('data:'))
                eventData = line.slice(5).trim()
            }
            if (eventData) {
              try { eventData = JSON.parse(eventData) }
              catch { /* keep raw */ }
              onEvent(eventType, eventData)
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

export function engineResume(interruptId, confirmed) {
  return request.post('/ai/engine/resume', { interruptId, confirmed })
}

// ========== 知识库管理 ==========

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

// ========== 向量存储实例 ==========

/** 分页查询存储实例 */
export function storeInstancePage(params) {
  return request.get('/ai/store/page', { params })
}

/** 查询存储实例详情 */
export function storeInstanceGetById(id) {
  return request.get(`/ai/store/${id}`)
}

/** 新增存储实例 */
export function storeInstanceCreate(data) {
  return request.post('/ai/store', data)
}

/** 修改存储实例 */
export function storeInstanceUpdate(data) {
  return request.put('/ai/store', data)
}

/** 删除存储实例 */
export function storeInstanceDelete(id) {
  return request.delete(`/ai/store/${id}`)
}

/** 测试存储实例连接 */
export function storeInstanceTest(id) {
  return request.post(`/ai/store/${id}/test`)
}

// ========== Agent 工具管理 ==========

export function agentToolPage(params) {
  return request.get('/ai/agent-tool/page', { params })
}

export function agentToolGetById(id) {
  return request.get(`/ai/agent-tool/${id}`)
}

export function agentToolAdd(data) {
  return request.post('/ai/agent-tool', data)
}

export function agentToolUpdate(data) {
  return request.put('/ai/agent-tool', data)
}

export function agentToolDelete(id) {
  return request.delete(`/ai/agent-tool/${id}`)
}

export function agentToolPermissions(agentId, toolKey) {
  return request.get(`/ai/agent-tool/permission/${agentId}`, { params: { toolKey } })
}

export function agentToolSavePermissions(agentId, toolKey, data) {
  return request.post(`/ai/agent-tool/permission/${agentId}`, data, { params: { toolKey } })
}

export function agentToolDeletePermissions(agentId, toolKey) {
  return request.delete(`/ai/agent-tool/permission/${agentId}`, { params: { toolKey } })
}
