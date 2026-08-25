import { useAuthStore } from '@/store/modules/auth'
import { generateUUID, request } from '@/utils'

const BASE_URL = import.meta.env.VITE_REQUEST_PREFIX || ''

function consumeEventStream(url, data, onEvent, onComplete, onError) {
  const authStore = useAuthStore()
  const controller = new AbortController()

  fetch(`${BASE_URL}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
      Authorization: authStore.accessToken ? `Bearer ${authStore.accessToken}` : '',
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
          if (done) {
            onComplete?.()
            return
          }
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
              try {
                eventData = JSON.parse(eventData)
              }
              catch {
                /* keep raw */
              }
              onEvent?.(eventType, eventData)
            }
          }
          read()
        }).catch((err) => {
          if (err.name !== 'AbortError')
            onError?.(err)
        })
      }

      read()
    })
    .catch((err) => {
      if (err.name !== 'AbortError')
        onError?.(err)
    })

  return controller
}

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

export function aiClientCall(data) {
  return request.post('/ai/client/call', data)
}

export function aiClientStream(data) {
  return request.post('/ai/client/stream', data)
}

export function streamAgentChat(data, onEvent, onComplete, onError) {
  return consumeEventStream('/ai/engine/stream', data, onEvent, onComplete, onError)
}

export function agentAiCreateSSE(description, onEvent, onComplete, onError) {
  return consumeEventStream('/ai/agent/ai-create', { description }, onEvent, onComplete, onError)
}

export function agentAiCreateConfirm(config) {
  return request.post('/ai/agent/ai-create/confirm', config)
}
