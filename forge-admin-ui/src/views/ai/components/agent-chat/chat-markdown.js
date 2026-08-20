/**
 * agent-chat Markdown 渲染工具（从 agent-chat.vue 抽出）。
 *
 * 这些都是无组件状态依赖的纯函数：`marked` 全局配置（模块导入时执行一次）、
 * 全量解析（历史回放 / 流结束 flush）与流式增量解析（只重算最后一个稳定块之后的尾块）。
 * 组件侧只需 import 使用，渲染节流（renderTimer）与滚动仍留在组件内。
 */
import hljs from 'highlight.js'
import { marked } from 'marked'

// HTML 转义：代码高亮失败兜底 + 整篇解析失败兜底
export function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

// marked 全局配置（模块级，随本模块导入执行一次）：GFM + 换行符转 <br> + 代码块高亮
marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    code({ text, lang }) {
      const language = lang && hljs.getLanguage(lang) ? lang : ''
      let highlighted
      try {
        highlighted = language
          ? hljs.highlight(text, { language }).value
          : hljs.highlightAuto(text).value
      }
      catch {
        highlighted = escapeHtml(text)
      }
      const label = language || 'text'
      return `<div class="code-block"><div class="code-block-header"><span class="code-lang">${label}</span>`
        + `<button class="code-copy-btn" type="button" data-code="${encodeURIComponent(text)}">复制</button></div>`
        + `<pre class="hljs"><code class="hljs">${highlighted}</code></pre></div>`
    },
  },
})

// 轻量 XSS 清洗（marked 默认转义 raw HTML，这里兜底）
export function sanitizeHtml(html) {
  if (!html)
    return html
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/on\w+\s*=/gi, '')
}

// renderMarkdown：全量解析（历史回放 / 流结束 flush 用，保证最终渲染一致），带 LRU 上限缓存
const mdCache = new Map()
export function renderMarkdown(text) {
  if (!text)
    return ''
  const cached = mdCache.get(text)
  if (cached)
    return cached
  let html
  try {
    html = sanitizeHtml(marked.parse(text))
  }
  catch {
    html = escapeHtml(text)
  }
  if (mdCache.size > 200)
    mdCache.clear()
  mdCache.set(text, html)
  return html
}

// 流式增量渲染（决策 23：只对最后一个块做快速重算）：
// 以最后一个「空行块边界」把内容切成「已稳定前缀 + 正在输出尾块」；前缀 parse 一次即缓存复用，
// 每个 tick 只重算尾块，避免流式期间对整篇反复 marked.parse。切点要求前缀内代码围栏(```)成对闭合，
// 否则不切（回退整篇解析），防止把未闭合的代码块截断。
const streamStableCache = new Map()
function findStableSplit(text) {
  let idx = text.lastIndexOf('\n\n')
  while (idx > 0) {
    const prefix = text.slice(0, idx + 2)
    if (((prefix.match(/```/g) || []).length % 2) === 0)
      return idx + 2
    idx = text.lastIndexOf('\n\n', idx - 1)
  }
  return 0
}
export function renderMarkdownStreaming(text) {
  if (!text)
    return ''
  const splitAt = findStableSplit(text)
  if (splitAt <= 0)
    return renderMarkdown(text)
  const prefix = text.slice(0, splitAt)
  let stableHtml = streamStableCache.get(prefix)
  if (stableHtml == null) {
    try {
      stableHtml = sanitizeHtml(marked.parse(prefix))
    }
    catch {
      stableHtml = escapeHtml(prefix)
    }
    if (streamStableCache.size > 200)
      streamStableCache.clear()
    streamStableCache.set(prefix, stableHtml)
  }
  let tailHtml
  try {
    tailHtml = sanitizeHtml(marked.parse(text.slice(splitAt)))
  }
  catch {
    tailHtml = escapeHtml(text.slice(splitAt))
  }
  return stableHtml + tailHtml
}
