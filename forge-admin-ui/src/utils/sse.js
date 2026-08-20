/**
 * 通用 SSE（Server-Sent Events）消费工具（阶段四#2）。
 *
 * 基于 fetch + ReadableStream 读取 `text/event-stream` 流式响应：
 * - 支持 POST body 与自定义请求头（`EventSource` 只支持 GET 且不能带鉴权头，不适配后端接口）；
 * - 按 SSE 规范解析事件块：支持多行 `data:`（以 `\n` 合并）、`event:` 类型、忽略以 `:` 开头的注释行；
 * - 自动 flush 结束时缓冲区里没有终止空行的最后一个事件；
 * - `data` 能 JSON.parse 则回调解析后的对象，否则回调原始字符串；
 * - 返回 {@link AbortController}，调用方可随时 `.abort()` 中断（中断不触发 onError）。
 *
 * @param {string} url 完整请求地址（含前缀）
 * @param {object} options
 * @param {object} [options.headers] 追加的请求头（会与默认头合并）
 * @param {any}    [options.body] 请求体；非字符串会 JSON.stringify
 * @param {(eventType: string, data: any) => void} [options.onEvent] 每个事件回调
 * @param {() => void} [options.onComplete] 流正常结束回调
 * @param {(err: Error) => void} [options.onError] 出错回调（AbortError 不触发）
 * @returns {AbortController} 用于中断请求
 */
export function consumeSSE(url, { headers = {}, body, onEvent, onComplete, onError } = {}) {
  const controller = new AbortController()

  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
      ...headers,
    },
    body: body != null && typeof body !== 'string' ? JSON.stringify(body) : body,
    signal: controller.signal,
  })
    .then((response) => {
      if (!response.ok)
        throw new Error(response.statusText || `HTTP ${response.status}`)
      if (!response.body)
        throw new Error('浏览器不支持流式响应')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      function dispatch(eventStr) {
        let eventType = 'message'
        const dataLines = []
        for (const line of eventStr.split(/\r?\n/)) {
          if (line.startsWith(':'))
            continue // 注释行
          if (line.startsWith('event:'))
            eventType = line.slice(6).trim()
          else if (line.startsWith('data:'))
            dataLines.push(line.slice(5).replace(/^ /, ''))
        }
        if (!dataLines.length)
          return
        let data = dataLines.join('\n')
        try {
          data = JSON.parse(data)
        }
        catch {
          /* 保留原始字符串 */
        }
        onEvent?.(eventType, data)
      }

      function pump() {
        reader.read().then(({ done, value }) => {
          if (done) {
            const tail = buffer.trim()
            if (tail)
              dispatch(tail)
            onComplete?.()
            return
          }
          buffer += decoder.decode(value, { stream: true })
          const chunks = buffer.split(/\r?\n\r?\n/)
          buffer = chunks.pop() || ''
          for (const chunk of chunks)
            dispatch(chunk)
          pump()
        }).catch((err) => {
          if (err.name !== 'AbortError')
            onError?.(err)
        })
      }

      pump()
    })
    .catch((err) => {
      if (err.name !== 'AbortError')
        onError?.(err)
    })

  return controller
}
