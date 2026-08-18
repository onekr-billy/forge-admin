<template>
  <div class="agent-chat-page">
    <!-- 左侧会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <NButton type="primary" block size="small" @click="createSession">
          <template #icon>
            <NIcon><ChatbubblesOutline /></NIcon>
          </template>
          新建对话
        </NButton>
      </div>
      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSessionId === session.id }"
          @click="switchSession(session.id)"
        >
          <div class="session-info">
            <span class="session-name">{{ session.name || '新对话' }}</span>
            <span class="session-time">{{ formatTime(session.createTime) }}</span>
          </div>
          <NPopconfirm @positive-click="deleteSession(session.id)">
            <template #trigger>
              <NButton quaternary circle size="tiny" @click.stop>
                <template #icon>
                  <NIcon size="14">
                    <CloseOutline />
                  </NIcon>
                </template>
              </NButton>
            </template>
            确定删除该会话吗？
          </NPopconfirm>
        </div>
      </div>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-main">
      <!-- Agent 头部信息 -->
      <div class="chat-header">
        <div class="chat-header-left">
          <NButton v-if="agentId" size="small" text title="返回Agent设计器" @click="backToBuilder">
            <template #icon>
              <NIcon><ArrowBackOutline /></NIcon>
            </template>
          </NButton>
          <NTag v-if="currentAgent" type="info" size="small" :bordered="false">
            {{ currentAgent.agentName || currentAgent.agentCode }}
          </NTag>
          <span v-if="currentAgent" class="chat-header-title">{{ currentAgent.description }}</span>
        </div>
      </div>
      <template v-if="currentSessionId">
        <!-- 消息区域 -->
        <div ref="messageAreaRef" class="message-area">
          <div v-for="msg in messages" :key="msg.id" class="message-bubble" :class="[msg.role]">
            <!-- 用户消息 -->
            <template v-if="msg.role === 'user'">
              <div class="message-content user-content">
                {{ msg.content }}
              </div>
            </template>

            <!-- 助手消息 -->
            <template v-else>
              <!-- 思考块 -->
              <template v-if="msg.thinking">
                <NCollapse class="thinking-block">
                  <NCollapseItem title="思考过程" name="thinking">
                    <div class="thinking-content">
                      {{ msg.thinking }}
                    </div>
                  </NCollapseItem>
                </NCollapse>
              </template>

              <!-- 工具调用卡片 -->
              <template v-if="msg.toolCalls && msg.toolCalls.length">
                <div v-for="(tc, idx) in msg.toolCalls" :key="idx" class="tool-call-card">
                  <div class="tool-header">
                    <NTag size="small" type="info">
                      {{ tc.tool }}
                    </NTag>
                  </div>
                  <div v-if="tc.args" class="tool-args">
                    <span class="tool-label">参数:</span>
                    <code>{{ tc.args }}</code>
                  </div>
                  <div v-if="tc.result" class="tool-result">
                    <span class="tool-label">结果:</span>
                    <pre class="tool-result-content">{{ tc.result }}</pre>
                  </div>
                </div>
              </template>

              <!-- 文本内容 -->
              <div v-if="msg.content" class="message-content assistant-content" v-html="renderMarkdown(msg.content)" />

              <!-- 流式光标 -->
              <span v-if="msg.streaming" class="streaming-cursor">|</span>
            </template>
          </div>

          <!-- HITL 确认对话框 -->
          <div v-if="pendingConfirm" class="hitl-confirm">
            <NCard title="需要确认" size="small" :bordered="true">
              <p>
                工具 <NTag size="small" type="warning">
                  {{ pendingConfirm.tool }}
                </NTag> 请求执行确认
              </p>
              <div v-if="pendingConfirm.args" class="tool-args">
                <code>{{ pendingConfirm.args }}</code>
              </div>
              <template #action>
                <NSpace>
                  <NButton size="small" @click="cancelHitl">
                    取消
                  </NButton>
                  <NButton type="error" size="small" @click="handleConfirm(false)">
                    拒绝
                  </NButton>
                  <NButton type="primary" size="small" @click="handleConfirm(true)">
                    确认
                  </NButton>
                </NSpace>
              </template>
            </NCard>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <NInput
            v-model:value="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息..."
            :disabled="isStreaming"
            @keydown.enter.exact="handleSend"
          />
          <NButton
            :type="isStreaming ? 'error' : 'primary'"
            size="small"
            :disabled="!isStreaming && !inputText.trim()"
            @click="isStreaming ? stopChat() : handleSend()"
          >
            {{ isStreaming ? '停止' : '发送' }}
          </NButton>
        </div>
      </template>

      <div v-else class="empty-state">
        <NIcon size="48" :depth="3">
          <ChatbubblesOutline />
        </NIcon>
        <p>选择或创建一个对话开始</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowBackOutline, ChatbubblesOutline, CloseOutline } from '@vicons/ionicons5'
import { marked } from 'marked'
import { NButton, NCard, NCollapse, NCollapseItem, NIcon, NInput, NPopconfirm, NSpace, NTag, useMessage } from 'naive-ui'
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { agentGetById, agentList, engineResume, sessionDeleteByUser, sessionList, sessionMessagesByUser, streamEngineChat } from '@/api/ai'

defineOptions({ name: 'AiAgentChat' })

const route = useRoute()
const router = useRouter()
const message = useMessage()

// 会话管理
const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const pendingConfirm = ref(null)
const messageAreaRef = ref(null)
let abortController = null
let hitlTimer = null

function clearHitlTimer() {
  if (hitlTimer) {
    clearTimeout(hitlTimer)
    hitlTimer = null
  }
}

// Agent 信息
const currentAgent = ref(null)
const agentId = ref(null)

// Agent 选择
const agents = ref([])
const selectedAgentCode = ref('')

// 生成 UUID（会话 ID）
function generateSessionId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

async function loadAgent() {
  const id = route.query.agentId
  if (id) {
    agentId.value = Number(id)
    try {
      const res = await agentGetById(agentId.value)
      currentAgent.value = res.data
      if (currentAgent.value) {
        selectedAgentCode.value = currentAgent.value.agentCode
      }
    }
    catch { /* ignore */ }
  }
  // 同时加载 agent 列表（供侧栏切换）
  try {
    const res = await agentList()
    agents.value = res.data || []
  }
  catch { /* ignore */ }
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    const res = await sessionList()
    let list = res.data || []
    // 若指定了 Agent，按 agentCode 过滤
    if (selectedAgentCode.value) {
      list = list.filter(s => !s.agentCode || s.agentCode === selectedAgentCode.value)
    }
    sessions.value = list
    // 若当前没有选中会话且有历史，默认选第一个
    if (!currentSessionId.value && list.length > 0) {
      await switchSession(list[0].id)
    }
  }
  catch (e) {
    message.error(e.message || '加载会话失败')
  }
  finally {
    loadingSessions.value = false
  }
}

async function loadMessages(sessionId) {
  loadingMessages.value = true
  try {
    const res = await sessionMessagesByUser(sessionId)
    const records = res.data || []
    // 后端 record 转前端消息格式
    messages.value = records.map((r, idx) => {
      if (r.role === 'user') {
        return { id: `hist-${r.id}-${idx}`, role: 'user', content: r.content || '' }
      }
      return { id: `hist-${r.id}-${idx}`, role: 'assistant', content: r.content || '', thinking: '', toolCalls: [] }
    })
  }
  catch (e) {
    messages.value = []
  }
  finally {
    loadingMessages.value = false
  }
}

function backToBuilder() {
  if (agentId.value) {
    router.push({ path: '/ai/agent', query: { agentId: agentId.value, mode: 'builder' } })
  }
  else {
    router.push('/ai/agent')
  }
}

onMounted(async () => {
  await loadAgent()
  // 如果没有指定 agentId，默认选第一个
  if (!selectedAgentCode.value && agents.value.length > 0) {
    selectedAgentCode.value = agents.value[0].agentCode
  }
  await loadSessions()
})

function createSession() {
  const id = generateSessionId()
  // 新会话不落库，发送首条消息时后端自动创建
  sessions.value.unshift({
    id,
    name: '新对话',
    createTime: new Date().toISOString(),
  })
  currentSessionId.value = id
  messages.value = []
  pendingConfirm.value = null
}

async function switchSession(id) {
  if (currentSessionId.value === id)
    return
  currentSessionId.value = id
  pendingConfirm.value = null
  await loadMessages(id)
}

async function deleteSession(id) {
  try {
    await sessionDeleteByUser(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = sessions.value.length > 0 ? sessions.value[0].id : null
      if (currentSessionId.value) {
        await loadMessages(currentSessionId.value)
      }
      else {
        messages.value = []
      }
    }
    message.success('会话已删除')
  }
  catch (e) {
    message.error(e.message || '删除失败')
  }
}

function handleSend(e) {
  if (e && e.shiftKey)
    return
  if (e)
    e.preventDefault()
  if (!inputText.value.trim() || isStreaming.value)
    return
  if (!selectedAgentCode.value) {
    message.warning('请先选择一个Agent')
    return
  }

  const text = inputText.value.trim()
  inputText.value = ''

  // 更新会话名称（首条消息作为标题）
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (session && session.name === '新对话') {
    session.name = text.length > 20 ? `${text.slice(0, 20)}…` : text
  }

  // 添加用户消息
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: text,
  })

  // 添加助手占位消息
  const assistantMsg = {
    id: Date.now() + 1,
    role: 'assistant',
    content: '',
    thinking: '',
    toolCalls: [],
    streaming: true,
  }
  messages.value.push(assistantMsg)

  startStream(text, assistantMsg)
}

function startStream(text, assistantMsg) {
  isStreaming.value = true

  abortController = streamEngineChat(
    {
      agentCode: selectedAgentCode.value,
      sessionId: currentSessionId.value,
      message: text,
    },
    (eventType, data) => {
      handleSSEEvent(eventType, data, assistantMsg)
    },
    () => {
      assistantMsg.streaming = false
      isStreaming.value = false
      scrollToBottom()
    },
    (err) => {
      assistantMsg.streaming = false
      isStreaming.value = false
      message.error(`对话失败: ${err.message || err}`)
    },
  )
}

function handleSSEEvent(eventType, data, assistantMsg) {
  switch (eventType) {
    case 'TEXT_BLOCK_DELTA': {
      const text = data?.text || ''
      assistantMsg.content += text
      break
    }
    case 'THINKING_BLOCK_DELTA': {
      const text = data?.text || ''
      assistantMsg.thinking += text
      break
    }
    case 'TOOL_CALL_START': {
      assistantMsg.toolCalls.push({
        tool: data?.tool || '',
        args: data?.args || '',
        result: '',
      })
      break
    }
    case 'TOOL_RESULT_TEXT_DELTA':
    case 'TOOL_RESULT_DATA_DELTA': {
      const lastTool = assistantMsg.toolCalls[assistantMsg.toolCalls.length - 1]
      if (lastTool) {
        lastTool.result += data?.content || ''
      }
      break
    }
    case 'REQUIRE_USER_CONFIRM': {
      pendingConfirm.value = {
        interruptId: data?.interruptId || '',
        tool: data?.tool || '',
        args: data?.args || '',
      }
      // HITL 超时自动拒绝（30 秒无操作）
      clearHitlTimer()
      hitlTimer = setTimeout(() => {
        if (pendingConfirm.value) {
          message.warning(`工具确认超时，已自动拒绝 "${pendingConfirm.value.tool}"`)
          handleConfirm(false)
        }
      }, 30000)
      break
    }
    case 'HINT_BLOCK': {
      message.info(data?.hint || '提示信息')
      break
    }
    case 'AGENT_END': {
      assistantMsg.streaming = false
      break
    }
    default:
      break
  }
  scrollToBottom()
}

async function handleConfirm(confirmed) {
  if (!pendingConfirm.value)
    return
  const interruptId = pendingConfirm.value.interruptId
  const tool = pendingConfirm.value.tool
  clearHitlTimer()
  pendingConfirm.value = null

  try {
    if (!interruptId) {
      message.error('确认信息缺失，无法恢复对话')
      return
    }
    await engineResume(interruptId, confirmed)
  }
  catch (e) {
    message.error('确认操作失败')
  }
}

function cancelHitl() {
  // 取消 = 终止当前流式对话，不做确认/拒绝恢复
  clearHitlTimer()
  pendingConfirm.value = null
  stopChat()
}

function stopChat() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  isStreaming.value = false
}

function scrollToBottom() {
  nextTick(() => {
    if (messageAreaRef.value) {
      messageAreaRef.value.scrollTop = messageAreaRef.value.scrollHeight
    }
  })
}

// 轻量 XSS 清洗：阻止脚本标签与危险协议（marked 默认 html:false 已转义 raw HTML，这里兜底）
function sanitizeHtml(html) {
  if (!html)
    return html
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/onerror\s*=/gi, '')
    .replace(/onload\s*=/gi, '')
    .replace(/onclick\s*=/gi, '')
    .replace(/on\w+\s*=/gi, '')
}

function renderMarkdown(text) {
  if (!text)
    return ''
  try {
    return sanitizeHtml(marked(text))
  }
  catch {
    return text
  }
}

function formatTime(time) {
  if (!time)
    return ''
  const d = new Date(time)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style scoped>
.agent-chat-page {
  --primary: #2f6fed;
  --primary-light: #f0f4ff;
  --primary-soft: #e8efff;
  --border: #e6e9f0;
  --text-strong: #1f2329;
  --text-body: #4e5969;
  --text-muted: #86909c;
  --bg-soft: #f7f8fa;
  display: flex;
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.05);
}

:global(.dark) .agent-chat-page {
  --primary: #4098ff;
  --primary-light: rgba(64, 152, 255, 0.12);
  --primary-soft: rgba(64, 152, 255, 0.18);
  --border: #2c3a4d;
  --text-strong: #f1f5f9;
  --text-body: #cbd5e1;
  --text-muted: #94a3b8;
  --bg-soft: #111a27;
  background: #151f2d;
}

/* ============ 左侧会话列表 ============ */
.chat-sidebar {
  display: flex;
  width: 250px;
  flex-direction: column;
  background: var(--bg-soft);
  border-right: 1px solid var(--border);
}

.sidebar-header {
  padding: 14px;
}

.sidebar-header :deep(.n-button) {
  border-radius: 8px;
  font-weight: 500;
}

.session-list {
  flex: 1;
  padding: 0 8px;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s ease;
}

.session-item:hover {
  background: rgba(47, 111, 237, 0.06);
}

.session-item.active {
  background: var(--primary-soft);
}

.session-item.active .session-name {
  color: var(--primary);
  font-weight: 600;
}

.session-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.session-name {
  font-size: 13px;
  color: var(--text-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-muted);
}

/* ============ 右侧对话区 ============ */
.chat-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
  background: #fff;
}

:global(.dark) .chat-header {
  background: #151f2d;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-header-title {
  max-width: 400px;
  margin-left: 8px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-area {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
  background: #fff;
}

:global(.dark) .message-area {
  background: #151f2d;
}

/* 消息行 */
.message-bubble {
  margin-bottom: 18px;
  max-width: 82%;
}

.message-bubble.user {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-bubble.assistant {
  margin-right: auto;
}

/* 用户消息气泡 */
.user-content {
  padding: 10px 16px;
  color: #fff;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  background: var(--primary);
  border-radius: 16px 16px 4px 16px;
  box-shadow: 0 4px 12px rgba(47, 111, 237, 0.18);
}

/* 助手消息气泡 */
.assistant-content {
  padding: 12px 16px;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
  background: var(--primary-light);
  border: 1px solid rgba(47, 111, 237, 0.08);
  border-radius: 4px 16px 16px 16px;
}

/* 思考过程块（时钟图标 + 折叠） */
.thinking-block {
  margin-bottom: 8px;
  border-radius: 8px;
  overflow: hidden;
}

.thinking-block :deep(.n-collapse-item__header) {
  padding: 8px 12px;
  font-size: 12px;
}

.thinking-block :deep(.n-collapse-item__header-main) {
  color: var(--text-muted);
}

.thinking-block :deep(.n-collapse-item__content-inner) {
  padding-top: 0;
  padding-bottom: 8px;
}

.thinking-content {
  max-height: 160px;
  padding: 0 12px;
  overflow-y: auto;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 工具调用卡片 */
.tool-call-card {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 8px;
  font-size: 12px;
}

.tool-header {
  margin-bottom: 4px;
}

.tool-args,
.tool-result {
  margin-top: 4px;
  color: var(--text-muted);
}

.tool-label {
  font-weight: 500;
  color: var(--text-body);
  margin-right: 4px;
}

.tool-result-content {
  margin: 0;
  max-height: 120px;
  overflow-y: auto;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
}

.streaming-cursor {
  animation: blink 1s step-end infinite;
  color: var(--primary);
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.hitl-confirm {
  margin: 12px 0;
  max-width: 400px;
}

/* 输入区域 */
.input-area {
  display: flex;
  gap: 8px;
  padding: 14px 18px;
  align-items: flex-end;
  background: #fff;
  border-top: 1px solid var(--border);
}

:global(.dark) .input-area {
  background: #151f2d;
}

.input-area .n-input {
  flex: 1;
}

.input-area :deep(.n-input) {
  border-radius: 10px;
}

.input-area :deep(.n-input:focus-within) {
  box-shadow: 0 0 0 2px rgba(47, 111, 237, 0.15);
}

.input-area .n-button {
  border-radius: 10px;
}

.empty-state {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}
</style>
