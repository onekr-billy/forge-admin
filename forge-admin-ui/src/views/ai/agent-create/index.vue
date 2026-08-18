<template>
  <div class="agent-create-page">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <div class="page-title">
            AI 创建 Agent
          </div>
          <div class="page-subtitle">
            描述你的需求，AI 自动生成 Agent 配置
          </div>
        </div>
        <n-button quaternary @click="handleBack">
          <template #icon>
            <i class="ai-icon:arrow-left" />
          </template>
          返回智能体管理
        </n-button>
      </div>
    </div>

    <!-- Step 1: 描述 -->
    <n-card v-if="step === 1" title="描述你的需求">
      <n-form label-placement="top">
        <n-form-item label="需求描述">
          <n-input
            v-model:value="description"
            type="textarea"
            :rows="5"
            placeholder="例如：我需要一个客服助手，能回答产品相关问题，处理退款请求，语气友好专业..."
          />
        </n-form-item>
        <n-button
          type="primary"
          size="large"
          :disabled="!description.trim() || generating"
          :loading="generating"
          @click="handleStartGenerate"
        >
          开始生成
        </n-button>
      </n-form>
    </n-card>

    <!-- Step 2: 生成中 -->
    <n-card v-if="step === 2" title="AI 生成中">
      <div class="generate-progress">
        <n-spin :size="40" />
        <p class="progress-text">
          AI 正在根据你的描述生成 Agent 配置...
        </p>
      </div>
      <div class="field-status-list">
        <div
          v-for="field in fieldStatuses"
          :key="field.name"
          class="field-status-item"
          :class="{ done: field.status === 'done', running: field.status === 'running' }"
        >
          <span class="field-name">{{ fieldLabels[field.name] || field.name }}</span>
          <n-tag
            :type="field.status === 'done' ? 'success' : field.status === 'running' ? 'warning' : 'default'"
            size="small"
          >
            {{ field.status === 'done' ? '完成' : field.status === 'running' ? '生成中' : '等待' }}
          </n-tag>
        </div>
      </div>
      <div class="generate-actions">
        <n-button @click="handleCancelGenerate">
          取消生成
        </n-button>
      </div>
    </n-card>

    <!-- Step 3: 确认 -->
    <n-card v-if="step === 3" title="确认 Agent 配置">
      <n-form label-placement="top">
        <n-form-item label="Agent 名称">
          <n-input v-model:value="config.agentName" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="config.description" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="问候语">
          <n-input v-model:value="config.greeting" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="预设问题">
          <n-dynamic-tags v-model:value="config.presetQuestions" />
        </n-form-item>
        <n-form-item label="系统指令">
          <n-input v-model:value="config.instruction" type="textarea" :rows="6" />
        </n-form-item>

        <!-- 推荐绑定 -->
        <n-form-item v-if="recommendations.length" label="推荐绑定">
          <n-checkbox-group v-model:value="selectedRecommendations">
            <n-space vertical>
              <n-checkbox
                v-for="rec in recommendations"
                :key="rec.ref"
                :value="rec.ref"
                :label="`[${rec.type === 'knowledge' ? '知识库' : '工具'}] ${rec.name}（置信度: ${Math.round(rec.confidence * 100)}%）`"
              />
            </n-space>
          </n-checkbox-group>
        </n-form-item>

        <n-space>
          <n-button @click="step = 1">
            返回修改
          </n-button>
          <n-button type="primary" size="large" :loading="creating" @click="handleConfirmCreate">
            确认创建
          </n-button>
        </n-space>
      </n-form>
    </n-card>

    <!-- Step 4: 完成 -->
    <n-card v-if="step === 4" title="创建成功">
      <n-result status="success" title="Agent 创建成功">
        <template #footer>
          <n-button type="primary" @click="handleGoToAgent">
            前往 Agent 工作台
          </n-button>
        </template>
      </n-result>
    </n-card>
  </div>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { agentAiCreateConfirm, agentAiCreateSSE, agentToolAdd } from '@/api/ai'

const router = useRouter()
const message = useMessage()

const step = ref(1)
const description = ref('')
const creating = ref(false)
const generating = ref(false)
const createdAgentId = ref(null)
const generatingAbortController = ref(null)

const fieldLabels = {
  agentName: 'Agent 名称',
  description: '描述',
  greeting: '问候语',
  presetQuestions: '预设问题',
  instruction: '系统指令',
  keeps: '保持项',
}

const fieldStatuses = ref([
  { name: 'agentName', status: 'pending' },
  { name: 'description', status: 'pending' },
  { name: 'greeting', status: 'pending' },
  { name: 'presetQuestions', status: 'pending' },
  { name: 'instruction', status: 'pending' },
])

const config = reactive({
  agentName: '',
  description: '',
  greeting: '',
  presetQuestions: [],
  instruction: '',
})

const recommendations = ref([])
const selectedRecommendations = ref([])

function handleStartGenerate() {
  step.value = 2
  generating.value = true
  // 重置状态
  fieldStatuses.value.forEach(f => f.status = 'pending')

  generatingAbortController.value = agentAiCreateSSE(
    description.value,
    (eventType, data) => {
      if (eventType === 'start') {
        // 生成开始
      }
      else if (eventType === 'field_done') {
        // 字段完成
        const fieldName = data.name
        const fieldValue = data.value
        config[fieldName] = fieldValue

        const field = fieldStatuses.value.find(f => f.name === fieldName)
        if (field)
          field.status = 'done'

        // 标记下一个为 running
        const nextField = fieldStatuses.value.find(f => f.status === 'pending')
        if (nextField)
          nextField.status = 'running'
      }
      else if (eventType === 'recommend') {
        recommendations.value = data.items || []
      }
      else if (eventType === 'error') {
        message.error(data.message || '生成失败')
        generating.value = false
        step.value = 1
      }
    },
    () => {
      // 完成
      generating.value = false
      if (step.value === 2) {
        step.value = 3
        // 确保 presetQuestions 是数组
        if (typeof config.presetQuestions === 'string') {
          try { config.presetQuestions = JSON.parse(config.presetQuestions) }
          catch { config.presetQuestions = [config.presetQuestions] }
        }
        if (!Array.isArray(config.presetQuestions)) {
          config.presetQuestions = config.presetQuestions ? [String(config.presetQuestions)] : []
        }
      }
    },
    (error) => {
      generating.value = false
      message.error(`生成失败: ${error.message || '未知错误'}`)
      step.value = 1
    },
  )

  // 标记第一个字段为 running
  if (fieldStatuses.value.length > 0) {
    fieldStatuses.value[0].status = 'running'
  }
}

function handleCancelGenerate() {
  if (generatingAbortController.value) {
    generatingAbortController.value.abort()
  }
  generating.value = false
  step.value = 1
}

async function handleConfirmCreate() {
  creating.value = true
  try {
    const payload = { ...config }
    // 添加选中的推荐绑定（知识库）
    if (selectedRecommendations.value.length > 0) {
      const knowledgeIds = selectedRecommendations.value.filter(id => typeof id === 'number')
      if (knowledgeIds.length > 0) {
        payload.knowledgeIds = knowledgeIds
      }
    }
    const res = await agentAiCreateConfirm(payload)
    if (res.data) {
      createdAgentId.value = res.data.agentId || res.data
      // 绑定推荐的工具（工具绑定需在 Agent 创建后写入 ai_agent_tool_config）
      const toolKeys = selectedRecommendations.value.filter(id => typeof id === 'string')
      if (toolKeys.length > 0 && createdAgentId.value) {
        try {
          await bindRecommendedTools(createdAgentId.value, toolKeys)
        }
        catch (e) {
          message.warning(`Agent 已创建，但部分工具绑定失败：${e.message || '未知错误'}`)
        }
      }
      step.value = 4
      message.success('Agent 创建成功')
    }
  }
  catch (e) {
    message.error(`创建失败: ${e.message || '未知错误'}`)
  }
  finally {
    creating.value = false
  }
}

async function bindRecommendedTools(agentId, toolKeys) {
  const tools = recommendations.value.filter(r => r.type === 'tool')
  for (const key of toolKeys) {
    const rec = tools.find(t => t.ref === key)
    await agentToolAdd({
      agentId,
      toolSource: 'mcp',
      toolKey: key,
      toolGroup: 'default',
      enabled: '1',
    })
  }
}

function handleBack() {
  // 生成中允许直接返回（SSE 已中止则无需额外处理）
  if (generatingAbortController.value) {
    generatingAbortController.value.abort()
    generating.value = false
  }
  router.push('/ai/agent')
}

function handleGoToAgent() {
  if (createdAgentId.value) {
    router.push('/ai/agent')
  }
  else {
    router.push('/ai/agent/chat')
  }
}

onBeforeUnmount(() => {
  if (generatingAbortController.value) {
    generatingAbortController.value.abort()
  }
})
</script>

<style scoped>
.agent-create-page {
  padding: 20px;
}
.page-header {
  margin-bottom: 20px;
}
.page-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
}
.page-subtitle {
  color: var(--text-color-3);
  font-size: 14px;
  margin-top: 4px;
}
.generate-actions {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
.generate-progress {
  text-align: center;
  padding: 30px;
}
.progress-text {
  margin-top: 16px;
  color: var(--text-color-3);
}
.field-status-list {
  margin-top: 20px;
}
.field-status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 4px;
  transition: background 0.2s;
}
.field-status-item.running {
  background: var(--warning-color-supressed);
}
.field-status-item.done {
  background: var(--success-color-supressed);
}
.field-name {
  font-weight: 500;
}
</style>
