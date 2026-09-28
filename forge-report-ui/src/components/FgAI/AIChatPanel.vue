<template>
  <div class="go-ai-chat-panel">
    <div class="messages-container" ref="messagesContainerRef" @scroll="handleMessagesScroll">
      <div v-if="aiStore.getChatMessages.length === 0" class="empty-state">
        <n-icon size="40" color="#51d6a9" style="margin-bottom: 12px">
          <SparklesIcon />
        </n-icon>
        <p class="empty-title">Forge AI 助手</p>
        <p class="empty-desc">选择业务定义或描述你想要的数据大屏，AI 将优先基于数据集生成</p>
        <div class="quick-prompts">
          <n-button
            v-for="prompt in quickPrompts"
            :key="prompt"
            size="small"
            secondary
            class="quick-btn"
            @click="useQuickPrompt(prompt)"
          >{{ prompt }}</n-button>
        </div>
      </div>

      <template v-else>
        <div
          v-for="(msg, index) in aiStore.getChatMessages"
          :key="msg.id"
          class="message-item"
          :class="msg.role"
        >
          <div v-if="msg.role === 'assistant'" class="avatar ai-avatar">
            <n-icon size="14" color="#51d6a9"><SparklesIcon /></n-icon>
          </div>
          <div class="bubble-wrapper">
            <div v-if="msg.role === 'assistant' && msg.reasoning?.trim()" class="reasoning-section">
              <div class="reasoning-header" @click="toggleReasoning(index)">
                <div class="reasoning-header-left">
                  <n-icon size="14" color="#51d6a9"><SparklesIcon /></n-icon>
                  <span>思考过程</span>
                  <span v-if="msg.reasoningTime" class="reasoning-duration">用时 {{ msg.reasoningTime }}s</span>
                  <span v-else-if="msg.isReasoning" class="reasoning-duration thinking">思考中...</span>
                </div>
                <span class="reasoning-toggle" :class="{ expanded: expandedReasonings[index] }">⌄</span>
              </div>
              <div
                v-if="expandedReasonings[index]"
                :ref="el => setReasoningContentRef(el, index)"
                class="reasoning-content"
                @scroll.stop="handleReasoningScroll(index)"
              >
                {{ msg.reasoning }}
              </div>
            </div>
            <div class="bubble" :class="msg.role">
              <div
                v-if="msg.role === 'assistant' && msg.canvasResponse"
                class="generate-result-card"
              >
                <div class="generate-result-icon">
                  <n-icon size="18"><AnalyticsIcon /></n-icon>
                </div>
                <div class="generate-result-main">
                  <div class="generate-result-title">
                    大屏生成完成
                  </div>
                  <div class="generate-result-name">
                    {{ msg.canvasResponse.title || '未命名大屏' }}
                  </div>
                  <div class="generate-result-meta">
                    <span>{{ msg.canvasResponse.components?.length || 0 }} 个组件</span>
                    <span v-if="msg.validationSummary">{{ msg.validationSummary.bound }} 个动态绑定</span>
                    <span v-if="msg.validationSummary?.staticFallback">{{ msg.validationSummary.staticFallback }} 个静态降级</span>
                    <span>可应用到当前画布</span>
                  </div>
                  <div v-if="msg.validationSummary" class="validation-summary">
                    <div class="validation-metrics">
                      <span class="metric bound">动态 {{ msg.validationSummary.bound }}</span>
                      <span class="metric static">静态 {{ msg.validationSummary.static + msg.validationSummary.staticFallback }}</span>
                      <span class="metric repaired">修复 {{ msg.validationSummary.repaired }}</span>
                    </div>
                    <div class="validation-items">
                      <div
                        v-for="item in visibleValidationItems(msg.validationSummary)"
                        :key="`${item.index}-${item.key}`"
                        class="validation-item"
                      >
                        <span class="validation-status" :class="getValidationStatusClass(item.status)">
                          {{ getValidationStatusLabel(item.status) }}
                        </span>
                        <span class="validation-name" :title="item.title">{{ item.title }}</span>
                        <span class="validation-dataset" :title="getValidationDatasetText(item)">
                          {{ getValidationDatasetText(item) }}
                        </span>
                      </div>
                    </div>
                    <div v-if="msg.validationSummary.warnings.length" class="validation-warning">
                      {{ msg.validationSummary.warnings[0] }}
                    </div>
                  </div>
                </div>
              </div>
              <div
                v-else-if="msg.role === 'assistant' && msg.streaming && msg.progressSteps?.length"
                class="generate-progress-card"
              >
                <div class="generate-progress-title">
                  AI 正在生成大屏
                </div>
                <div class="generate-progress-list">
                  <div
                    v-for="step in msg.progressSteps"
                    :key="step.key"
                    class="generate-progress-step"
                    :class="step.status"
                  >
                    <span class="generate-progress-dot" />
                    <span class="generate-progress-label">{{ step.label }}</span>
                  </div>
                </div>
              </div>
              <span v-else class="msg-content" v-html="renderContent(msg.content)"></span>
              <span v-if="msg.streaming" class="typing-cursor">|</span>
            </div>
            <div v-if="msg.role === 'assistant' && !msg.streaming && msg.canvasResponse" class="msg-actions">
              <n-button size="small" type="primary" ghost @click="applyToCanvas(msg.canvasResponse!)">
                <template #icon><n-icon><AnalyticsIcon /></n-icon></template>
                应用到画布
              </n-button>
            </div>
          </div>
          <div v-if="msg.role === 'user'" class="avatar user-avatar">
            <n-icon size="14"><PersonIcon /></n-icon>
          </div>
        </div>

        <div v-if="aiStore.getGenerating && !hasStreamingMessage" class="message-item assistant">
          <div class="avatar ai-avatar">
            <n-icon size="14" color="#51d6a9"><SparklesIcon /></n-icon>
          </div>
          <div class="bubble assistant">
            <span class="thinking-dots">
              <span></span><span></span><span></span>
            </span>
          </div>
        </div>
      </template>
    </div>

    <div class="style-row">
      <span class="style-label">风格：</span>
      <n-radio-group v-model:value="styleRef" size="small" :disabled="aiStore.getGenerating">
        <n-radio-button value="dark">深色</n-radio-button>
        <n-radio-button value="light">浅色</n-radio-button>
      </n-radio-group>
      <n-tooltip placement="top" trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary style="margin-left: 8px" @click="showModeSelect = !showModeSelect">
            <template #icon><n-icon><SettingsSharpIcon /></n-icon></template>
          </n-button>
        </template>
        模式设置
      </n-tooltip>
      <n-tooltip placement="top" trigger="hover">
        <template #trigger>
          <n-button
            size="tiny"
            quaternary
            :disabled="historyButtonDisabled"
            @click="toggleHistoryPanel"
          >
            <template #icon><n-icon><AnalyticsIcon /></n-icon></template>
          </n-button>
        </template>
        最近生成记录
      </n-tooltip>
      <n-tooltip placement="top" trigger="hover">
        <template #trigger>
          <n-button
            size="tiny"
            quaternary
            :disabled="chatModeRef !== 'generate'"
            @click="togglePromptTemplatePanel"
          >
            <template #icon><n-icon><DocumentTextIcon /></n-icon></template>
          </n-button>
        </template>
        提示词模板
      </n-tooltip>
    </div>

    <n-collapse-transition :show="showHistoryPanel">
      <div class="ai-history-panel">
        <div class="ai-history-header">
          <span>最近生成记录</span>
          <n-button size="tiny" quaternary :loading="historyLoading" @click="loadGenerateRecords(true)">
            刷新
          </n-button>
        </div>
        <div v-if="historyLoading && !generationHistoryItems.length" class="ai-history-empty">
          正在加载生成记录...
        </div>
        <div v-else-if="!generationHistoryItems.length" class="ai-history-empty">
          暂无生成记录
        </div>
        <template v-else>
          <div
            v-for="record in generationHistoryItems.slice(0, 8)"
            :key="record.id"
            class="ai-history-item"
            :class="{ failed: record.status !== 'success' }"
          >
            <div class="ai-history-main">
              <div class="ai-history-title">
                {{ record.title || '未命名大屏' }}
              </div>
              <div class="ai-history-meta">
                <span>{{ record.source === 'remote' ? '云端' : '本地' }}</span>
                <span>{{ formatHistoryTime(record.timestamp || record.createTime) }}</span>
                <span>{{ record.componentCount || 0 }} 组件</span>
                <span v-if="record.boundCount !== undefined">{{ record.boundCount }} 动态</span>
                <span v-if="record.businessName">{{ record.businessName }}</span>
                <span v-if="record.status !== 'success'" class="ai-history-status">
                  {{ getHistoryStatusLabel(record.status) }}
                </span>
              </div>
              <div v-if="record.errorMessage" class="ai-history-error">
                {{ record.errorMessage }}
              </div>
            </div>
            <div class="ai-history-actions">
              <n-button
                size="tiny"
                type="primary"
                ghost
                :disabled="!record.response"
                @click="record.response && applyToCanvas(record.response)"
              >
                应用
              </n-button>
              <n-tooltip v-if="record.source === 'remote' && record.recordId" placement="top" trigger="hover">
                <template #trigger>
                  <n-button size="tiny" quaternary circle @click="deleteGenerateRecord(record)">
                    <template #icon><n-icon><CloseIcon /></n-icon></template>
                  </n-button>
                </template>
                删除记录
              </n-tooltip>
            </div>
          </div>
        </template>
      </div>
    </n-collapse-transition>

    <n-collapse-transition :show="chatModeRef === 'generate' && showPromptTemplatePanel">
      <div class="prompt-template-panel">
        <div class="prompt-template-header">
          <span>提示词模板</span>
          <n-input
            v-model:value="promptTemplateKeyword"
            size="tiny"
            clearable
            placeholder="搜索模板"
            :disabled="promptTemplateLoading"
            @keydown.enter.prevent="loadPromptTemplates(true)"
          />
          <n-button size="tiny" quaternary :loading="promptTemplateLoading" @click="loadPromptTemplates(true)">
            刷新
          </n-button>
        </div>
        <div v-if="promptTemplateLoading && !promptTemplateItems.length" class="prompt-template-empty">
          正在加载模板...
        </div>
        <div v-else-if="!promptTemplateItems.length" class="prompt-template-empty">
          暂无可用模板
        </div>
        <template v-else>
          <div
            v-for="template in promptTemplateItems"
            :key="template.id"
            class="prompt-template-item"
          >
            <div class="prompt-template-main">
              <div class="prompt-template-title">
                <span>{{ template.templateName || '未命名模板' }}</span>
                <span v-if="template.isRecommended === '1'" class="prompt-template-badge">推荐</span>
              </div>
              <div class="prompt-template-meta">
                <span v-if="template.businessCategory">{{ template.businessCategory }}</span>
                <span v-if="template.domainCategory">{{ template.domainCategory }}</span>
                <span>{{ formatPromptTemplateCounts(template) }}</span>
              </div>
              <div class="prompt-template-summary">
                {{ template.contentSummary || template.description || '暂无摘要' }}
              </div>
            </div>
            <div class="prompt-template-actions">
              <n-tooltip placement="top" trigger="hover">
                <template #trigger>
                  <n-button size="tiny" quaternary circle @click="previewPromptTemplate(template)">
                    <template #icon><n-icon><EyeOutlineIcon /></n-icon></template>
                  </n-button>
                </template>
                预览
              </n-tooltip>
              <n-button size="tiny" type="primary" ghost @click="applyPromptTemplate(template)">
                使用
              </n-button>
              <n-tooltip placement="top" trigger="hover">
                <template #trigger>
                  <n-button size="tiny" quaternary circle @click="downloadPromptTemplate(template)">
                    <template #icon><n-icon><DownloadOutlineIcon /></n-icon></template>
                  </n-button>
                </template>
                下载
              </n-tooltip>
            </div>
          </div>
        </template>
      </div>
    </n-collapse-transition>

    <div v-if="chatModeRef === 'generate'" class="business-context-row">
      <span class="style-label">业务：</span>
      <n-select
        v-model:value="selectedBusinessId"
        size="small"
        clearable
        filterable
        :options="businessOptions"
        :loading="businessLoading"
        :disabled="aiStore.getGenerating"
        placeholder="可选业务定义"
      />
      <n-tooltip placement="top" trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary :loading="businessLoading" @click="loadBusinessDefinitions">
            <template #icon><n-icon><RefreshOutlineIcon /></n-icon></template>
          </n-button>
        </template>
        刷新业务定义
      </n-tooltip>
    </div>

    <div v-if="chatModeRef === 'generate' && selectedBusiness" class="business-context-tip" :class="{ 'is-expanded': businessContextExpanded }">
      <div class="business-context-compact">
        <div class="business-tip-main">
          <span class="business-tip-name" :title="selectedBusiness.businessName">{{ selectedBusiness.businessName }}</span>
          <span v-if="selectedBusiness.datasetCount" class="business-tip-chip">{{ selectedBusiness.datasetCount }} 数据集</span>
          <span v-if="selectedBusinessReadiness" class="business-tip-chip" :class="`is-${selectedBusinessReadiness.level}`">
            准备度 {{ selectedBusinessReadiness.score }}
          </span>
          <span class="business-tip-chip" :class="runtimePreviewClassName">
            预检 {{ runtimePreviewTitle }}
            <template v-if="businessRuntimePreview"> / 可查 {{ businessRuntimePreview.ready }}</template>
          </span>
        </div>
        <div class="business-tip-actions">
          <n-button
            size="tiny"
            quaternary
            :loading="runtimePreviewLoading"
            :disabled="!selectedBusinessPreviewContext || aiStore.getGenerating"
            @click="refreshBusinessRuntimePreview(selectedBusinessPreviewContext, true)"
          >
            刷新
          </n-button>
          <n-button size="tiny" quaternary @click="businessContextExpanded = !businessContextExpanded">
            {{ businessContextExpanded ? '收起' : '详情' }}
            <span class="business-expand-icon" :class="{ expanded: businessContextExpanded }">⌄</span>
          </n-button>
        </div>
      </div>

      <n-collapse-transition :show="businessContextExpanded">
        <div class="business-context-detail">
          <div v-if="selectedBusinessReadiness" class="business-readiness" :class="`is-${selectedBusinessReadiness.level}`">
            <div class="business-readiness-score">
              AI 准备度 {{ selectedBusinessReadiness.score }} / {{ selectedBusinessReadiness.label }}
            </div>
            <div class="business-readiness-meta">
              <span>{{ selectedBusinessReadiness.datasetCount }} 数据集</span>
              <span>{{ selectedBusinessReadiness.fieldCount }} 字段</span>
              <span v-if="selectedBusinessReadiness.primaryDatasetName">{{ selectedBusinessReadiness.primaryDatasetName }}</span>
            </div>
            <div v-if="selectedBusinessReadiness.suggestions.length" class="business-readiness-suggestion">
              {{ selectedBusinessReadiness.suggestions[0] }}
            </div>
          </div>
          <div class="business-runtime-preview" :class="runtimePreviewClassName">
            <div class="runtime-preview-head">
              <div class="runtime-preview-title">
                数据预检 {{ runtimePreviewTitle }}
              </div>
            </div>
            <div class="runtime-preview-meta">
              <span>可查 {{ businessRuntimePreview?.ready || 0 }}</span>
              <span>空 {{ businessRuntimePreview?.empty || 0 }}</span>
              <span>异常 {{ businessRuntimePreview?.failed || 0 }}</span>
              <span v-if="businessRuntimePreview?.skipped">跳过 {{ businessRuntimePreview.skipped }}</span>
            </div>
            <div v-if="runtimePreviewMessage" class="runtime-preview-message">
              {{ runtimePreviewMessage }}
            </div>
          </div>
        </div>
      </n-collapse-transition>
    </div>

    <n-collapse-transition :show="showModeSelect">
      <div class="mode-row">
        <span class="style-label">模式：</span>
        <n-radio-group v-model:value="chatModeRef" size="small">
          <n-radio-button value="generate">生成大屏</n-radio-button>
          <n-radio-button value="chat">自由对话</n-radio-button>
        </n-radio-group>
      </div>

      <div class="config-grid">
        <div class="config-item config-provider">
          <span class="style-label">供应商：</span>
          <n-select
            v-model:value="selectedProviderId"
            size="small"
            :options="providerOptions"
            :loading="providerLoading"
            :disabled="aiStore.getGenerating || providerOptions.length === 0"
            placeholder="请选择供应商"
          />
        </div>

        <div class="config-item config-model">
          <span class="style-label">模型：</span>
          <n-select
            v-model:value="selectedModelName"
            size="small"
            :options="modelOptions"
            :disabled="aiStore.getGenerating || !selectedProvider"
            placeholder="请选择模型"
            filterable
            tag
          />
        </div>

        <div class="config-item config-temperature">
          <span class="style-label">温度：</span>
          <n-input-number
            v-model:value="temperatureRef"
            size="small"
            :min="0"
            :max="2"
            :step="0.1"
            :precision="1"
            :disabled="aiStore.getGenerating"
          />
        </div>

        <div class="config-item config-max-tokens">
          <span class="style-label">Max Tokens：</span>
          <n-input-number
            v-model:value="maxTokensRef"
            size="small"
            :min="1"
            :step="100"
            clearable
            :disabled="aiStore.getGenerating"
            placeholder="可选"
          />
        </div>
      </div>

      <div v-if="selectedProvider" class="provider-tip">
        当前使用：{{ selectedProvider.providerName }}
        <span v-if="selectedModelName"> / {{ selectedModelName }}</span>
      </div>
    </n-collapse-transition>

    <div v-if="referencedComponents.length" class="selection-context active">
      <div class="selection-context-main">
        <span class="selection-context-icon">
          <n-icon size="14"><LayersIcon /></n-icon>
        </span>
        <div class="selection-context-info">
          <div class="selection-context-title">
            已引用 {{ referencedComponents.length }} 个选中元素
          </div>
          <div class="selection-context-name">
            {{ referencedComponentSummary }}
          </div>
        </div>
      </div>
      <n-tooltip placement="top" trigger="hover">
        <template #trigger>
          <n-button size="tiny" quaternary circle @click="clearReferencedComponents">
            <template #icon><n-icon><CloseIcon /></n-icon></template>
          </n-button>
        </template>
        取消引用
      </n-tooltip>
    </div>

    <div class="input-area">
      <n-input
        v-model:value="inputRef"
        type="textarea"
        :placeholder="inputPlaceholder"
        :rows="5"
        :disabled="aiStore.getGenerating"
        @keydown.enter.exact.prevent="handleSend"
        @keydown.shift.enter.prevent="inputRef += '\n'"
      />
      <div class="input-footer">
        <div class="input-tools">
          <n-tooltip placement="top" trigger="hover">
            <template #trigger>
              <n-button
                class="reference-btn"
                :class="{ active: referencePickMode }"
                size="small"
                quaternary
                circle
                :disabled="aiStore.getGenerating"
                @click="toggleReferencePickMode"
              >
                <template #icon> <img src="~@/assets/images/Click.png" alt="" style="width: 20px"/></template>
              </n-button>
            </template>
            {{ referencePickMode ? '关闭引用模式' : '开启引用模式' }}
          </n-tooltip>
          <span class="hint-text">Enter 发送，Shift+Enter 换行</span>
        </div>
        <n-button v-if="aiStore.getGenerating" type="error" size="small" @click="handleStop">
          <template #icon><n-icon><CloseIcon /></n-icon></template>
          停止生成
        </n-button>
        <n-button
          v-else
          type="primary"
          size="small"
          :disabled="!canSendMessage || !selectedProviderId"
          @click="handleSend"
        >
          <template #icon><n-icon><SendIcon /></n-icon></template>
          发送
        </n-button>
      </div>
    </div>

    <n-modal
      v-model:show="promptTemplatePreviewVisible"
      preset="card"
      title="提示词模板预览"
      style="width: min(820px, calc(100vw - 24px))"
    >
      <n-spin :show="promptTemplatePreviewLoading">
        <div v-if="currentPromptTemplate" class="prompt-template-preview">
          <div class="prompt-template-preview-title">
            {{ currentPromptTemplate.templateName || '未命名模板' }}
          </div>
          <div class="prompt-template-preview-meta">
            <span v-if="currentPromptTemplate.businessCategory">{{ currentPromptTemplate.businessCategory }}</span>
            <span v-if="currentPromptTemplate.domainCategory">{{ currentPromptTemplate.domainCategory }}</span>
            <span>{{ formatPromptTemplateCounts(currentPromptTemplate) }}</span>
          </div>
          <pre class="prompt-template-preview-content">{{ currentPromptTemplate.promptContent || currentPromptTemplate.contentSummary || '-' }}</pre>
        </div>
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button @click="promptTemplatePreviewVisible = false">
            关闭
          </n-button>
          <n-button v-if="currentPromptTemplate" @click="downloadPromptTemplate(currentPromptTemplate)">
            下载
          </n-button>
          <n-button v-if="currentPromptTemplate" type="primary" @click="applyPromptTemplate(currentPromptTemplate)">
            使用
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, toRaw, watch } from 'vue'
import {
  type AiDashboardComponentLineageItem,
  type AiDashboardGenerateRecord,
  type AiDashboardGenerateRecordSaveRequest,
  type AiPromptTemplate,
  type AiProvider,
  aiChatStream,
  aiGenerateStream,
  deleteDashboardGenerateRecordApi,
  downloadPromptTemplateApi,
  getDashboardGenerateRecentApi,
  getPromptTemplateListApi,
  getProviderPageApi,
  previewPromptTemplateApi,
  saveDashboardGenerateRecordApi,
  usePromptTemplateApi
} from '@/api/ai'
import {
  getDataBusinessAiContext,
  getDataBusinessList,
  type DataBusinessAiContext,
  type DataBusinessDatasetContext,
  type DataBusinessOption
} from '@/api/data/business'
import type { DataDatasetField } from '@/api/data/dataset'
import type { AIGenerateResponse } from '@/api/ai/ai.d'
import { applyAIToCanvas } from './aiEngine'
import { parseStreamedResponse } from './llmClient'
import { getComponentCatalogText } from './componentRegistry'
import {
  validateAIGenerateResponse,
  type GenerateValidationItem,
  type GenerateValidationStatus,
  type GenerateValidationSummary
} from './generateValidation'
import { evaluateBusinessReadiness, type BusinessReadinessResult } from './businessReadiness'
import {
  previewBusinessRuntimeData,
  type BusinessRuntimePreviewSummary,
  type DatasetRuntimePreviewStatus
} from './datasetRuntimePreview'
import { icon } from '@/plugins'
import type { CreateComponentGroupType, CreateComponentType } from '@/packages/index.d'
import { useAIStore } from '@/store/modules/aiStore/aiStore'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { fetchRouteParamsLocation } from '@/utils'

const {
  SparklesIcon,
  SendIcon,
  AnalyticsIcon,
  PersonIcon,
  SettingsSharpIcon,
  CloseIcon,
  LayersIcon,
  RefreshOutlineIcon,
  DocumentTextIcon,
  EyeOutlineIcon,
  DownloadOutlineIcon
} = icon.ionicons5
const emit = defineEmits(['applied'])

const aiStore = useAIStore()
const chartEditStore = useChartEditStore()

const inputRef = ref('')
const styleRef = ref<'dark' | 'light'>('dark')
const chatModeRef = ref<'generate' | 'chat'>('generate')
const showModeSelect = ref(false)
const messagesContainerRef = ref<HTMLElement>()
const reasoningContentRefs = ref<HTMLElement[]>([])
const providerList = ref<AiProvider[]>([])
const providerLoading = ref(false)
const businessList = ref<DataBusinessOption[]>([])
const businessLoading = ref(false)
const selectedBusinessId = ref<number | string | null>(null)
const chatSessionIdRef = ref(aiStore.currentSessionId || createSessionId())
const selectedProviderId = ref<number | string | null>(aiStore.getSelectedProvider?.providerId ?? null)
const selectedModelName = ref(aiStore.getSelectedProvider?.modelName || '')
const temperatureRef = ref(aiStore.getSelectedProvider?.temperature ?? 0.7)
const maxTokensRef = ref<number | null>(aiStore.getSelectedProvider?.maxTokens ?? 384000)
const showHistoryPanel = ref(false)
const selectedBusinessPreviewContext = ref<DataBusinessAiContext | null>(null)
const selectedBusinessReadiness = ref<BusinessReadinessResult | null>(null)
const businessRuntimePreview = ref<BusinessRuntimePreviewSummary | null>(null)
const runtimePreviewLoading = ref(false)
const businessContextExpanded = ref(false)
const remoteGenerateRecords = ref<AiDashboardGenerateRecord[]>([])
const historyLoading = ref(false)
const historyLoaded = ref(false)
const showPromptTemplatePanel = ref(false)
const promptTemplateLoading = ref(false)
const promptTemplateLoaded = ref(false)
const promptTemplateKeyword = ref('')
const promptTemplateItems = ref<AiPromptTemplate[]>([])
const promptTemplatePreviewVisible = ref(false)
const promptTemplatePreviewLoading = ref(false)
const currentPromptTemplate = ref<AiPromptTemplate | null>(null)
const activePromptTemplate = ref<AiPromptTemplate | null>(null)

const quickPrompts = [
  '基于所选业务定义生成经营监控大屏',
  '电商销售数据监控大屏',
  '智慧城市运营中心大屏',
  '工厂生产数据监控大屏',
  '财务数据分析大屏'
]

const GENERATE_PROGRESS_STEPS = [
  { key: 'understand', label: '理解需求' },
  { key: 'layout', label: '规划布局' },
  { key: 'charts', label: '生成组件' },
  { key: 'detail', label: '完善配置' },
  { key: 'verify', label: '校验结果' }
]

const validationStatusLabels: Record<GenerateValidationStatus, string> = {
  bound: '动态',
  repaired: '已修复',
  static: '静态',
  staticFallback: '降级',
  unverified: '未校验',
  skipped: '跳过'
}

const validationStatusClassNames: Record<GenerateValidationStatus, string> = {
  bound: 'is-bound',
  repaired: 'is-repaired',
  static: 'is-static',
  staticFallback: 'is-static-fallback',
  unverified: 'is-unverified',
  skipped: 'is-skipped'
}

const historyStatusLabels: Record<string, string> = {
  success: '成功',
  failed: '失败',
  parse_failed: '解析失败',
  stopped: '已停止'
}

const runtimePreviewStatusLabels: Record<DatasetRuntimePreviewStatus, string> = {
  ready: '可查',
  empty: '空数据',
  failed: '异常',
  skipped: '跳过'
}

const hasStreamingMessage = computed(() => aiStore.getChatMessages.some(message => message.streaming))
const referencedComponentIds = ref<string[]>([])
const referencePickMode = ref(false)
const currentSelectedComponentIds = computed(() => sortSelectedComponentIds(chartEditStore.getTargetChart.selectId))
const referencedComponents = computed(() =>
  referencedComponentIds.value
    .map(id => findComponentById(id, chartEditStore.getComponentList))
    .filter(Boolean) as Array<CreateComponentType | CreateComponentGroupType>
)
const referencedComponentSummary = computed(() => {
  const names = referencedComponents.value.map(item => getComponentDisplayName(item))
  if (names.length <= 2) return names.join('、')
  return `${names.slice(0, 2).join('、')} 等`
})
const inputPlaceholder = computed(() => {
  if (referencedComponents.value.length) return '针对已引用元素提问，比如：帮我优化这个模块的配色和层级...'
  if (chatModeRef.value === 'generate' && selectedBusinessId.value) return '可直接发送，AI 将基于所选业务定义和绑定数据集生成大屏...'
  return chatModeRef.value === 'generate' ? '描述你想要的数据大屏...' : '有什么可以帮你？'
})
const expandedReasonings = ref<Record<number, boolean>>({})
const manuallyCollapsedReasonings = ref<Record<number, boolean>>({})
const shouldAutoScrollMessages = ref(true)
const reasoningAutoScrollState = ref<Record<number, boolean>>({})
const aiRawContent = ref('')
const aiReasoningContent = ref('')
const aiIsReasoningPhase = ref(false)
const aiReasoningStartTime = ref<number | null>(null)
const aiReasoningEndTime = ref<number | null>(null)

type AIReferencedComponent = {
  id: string
  key?: string
  title?: string
  category?: string
  isGroup?: boolean
  attr?: Record<string, any>
  styles?: Record<string, any>
  status?: Record<string, any>
  request?: Record<string, any>
  option?: Record<string, any>
  children?: AIReferencedComponent[]
}

type GenerateHistoryDisplayItem = {
  id: string
  source: 'remote' | 'local'
  recordId?: number | string
  title: string
  prompt?: string
  timestamp?: number
  createTime?: string
  response?: AIGenerateResponse | null
  validationSummary?: GenerateValidationSummary
  businessName?: string
  modelName?: string
  status: string
  componentCount?: number
  boundCount?: number
  staticCount?: number
  staticFallbackCount?: number
  repairedCount?: number
  errorMessage?: string
}

function createSessionId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `ai-session-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function parseModels(models?: string) {
  if (!models) return []
  const trimmed = models.trim()
  if (!trimmed) return []

  try {
    const parsed = JSON.parse(trimmed)
    if (Array.isArray(parsed)) {
      return parsed.map(item => String(item).trim()).filter(Boolean)
    }
  } catch {
    // ignore invalid json
  }

  return trimmed
    .split(/[\n,，]/)
    .map(item => item.trim())
    .filter(Boolean)
}

const providerOptions = computed(() =>
  providerList.value.map(item => ({
    label: `${item.providerName || '未命名供应商'}${item.isDefault === '1' ? '（默认）' : ''}`,
    value: item.id as number | string
  }))
)

const selectedProvider = computed(() =>
  providerList.value.find(item => String(item.id) === String(selectedProviderId.value)) || null
)

const businessOptions = computed(() =>
  businessList.value.map(item => ({
    label: `${item.businessName || '未命名业务'}${item.businessCode ? ` (${item.businessCode})` : ''}`,
    value: item.id as number | string
  }))
)

const selectedBusiness = computed(() =>
  businessList.value.find(item => String(item.id) === String(selectedBusinessId.value)) || null
)
const canSendMessage = computed(() =>
  !!inputRef.value.trim() || (chatModeRef.value === 'generate' && !!selectedBusinessId.value)
)

const modelOptions = computed(() => {
  if (!selectedProvider.value) return []
  const models = parseModels(selectedProvider.value.models)
  const options = models.map(model => ({ label: model, value: model }))
  if (!options.length && selectedProvider.value.defaultModel) {
    options.push({ label: selectedProvider.value.defaultModel, value: selectedProvider.value.defaultModel })
  }
  return options
})

const generationHistoryItems = computed<GenerateHistoryDisplayItem[]>(() => {
  const remoteItems = remoteGenerateRecords.value
    .map(record => remoteRecordToHistoryItem(record))
    .filter(Boolean) as GenerateHistoryDisplayItem[]
  const remoteKeys = new Set(remoteItems.map(item => `${item.prompt || ''}|${item.title || ''}`))
  const localItems = aiStore.getGenerateHistory
    .map(item => ({
      id: item.id || `local-${item.timestamp}`,
      source: 'local' as const,
      title: item.response?.title || '未命名大屏',
      prompt: item.prompt,
      timestamp: item.timestamp,
      response: item.response,
      validationSummary: item.validationSummary,
      businessName: item.businessName,
      modelName: item.modelName,
      status: 'success',
      componentCount: item.response?.components?.length || 0,
      boundCount: item.validationSummary?.bound,
      staticCount: item.validationSummary?.static,
      staticFallbackCount: item.validationSummary?.staticFallback,
      repairedCount: item.validationSummary?.repaired
    }))
    .filter(item => !remoteKeys.has(`${item.prompt || ''}|${item.title || ''}`))

  return [...remoteItems, ...localItems].slice(0, 20)
})

const historyButtonDisabled = computed(() =>
  historyLoaded.value
  && !historyLoading.value
  && generationHistoryItems.value.length === 0
)

const runtimePreviewTitle = computed(() => {
  if (runtimePreviewLoading.value) return '检查中'
  if (!businessRuntimePreview.value) return '未检查'
  if (businessRuntimePreview.value.ready > 0) return '可用'
  if (businessRuntimePreview.value.failed > 0) return '异常'
  if (businessRuntimePreview.value.empty > 0) return '空数据'
  return '待补充'
})

const runtimePreviewClassName = computed(() => {
  if (runtimePreviewLoading.value) return 'is-loading'
  if (!businessRuntimePreview.value) return 'is-pending'
  if (businessRuntimePreview.value.ready > 0 && businessRuntimePreview.value.failed === 0) return 'is-ready'
  if (businessRuntimePreview.value.ready > 0) return 'is-partial'
  return 'is-failed'
})

const runtimePreviewMessage = computed(() => {
  if (runtimePreviewLoading.value) return '正在抽样查询绑定数据集...'
  if (!businessRuntimePreview.value) return '生成前会自动做一次轻量预检。'
  return businessRuntimePreview.value.suggestions[0] || '已获取可查询数据集样例，AI 将优先基于样例规划组件。'
})

function isNearScrollBottom(el: HTMLElement, threshold = 48) {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

function handleMessagesScroll() {
  const el = messagesContainerRef.value
  if (!el) return
  shouldAutoScrollMessages.value = isNearScrollBottom(el, 96)
}

function handleReasoningScroll(index: number) {
  const el = reasoningContentRefs.value[index]
  if (!el) return
  reasoningAutoScrollState.value[index] = isNearScrollBottom(el, 24)
}

function scrollMessagesToActiveContent() {
  const container = messagesContainerRef.value
  if (!container) return

  const activeIndex = aiStore.getChatMessages.findLastIndex(msg => msg.role === 'assistant' && msg.isReasoning)
  const reasoningEl = reasoningContentRefs.value[activeIndex]
  if (reasoningEl) {
    const containerRect = container.getBoundingClientRect()
    const reasoningRect = reasoningEl.getBoundingClientRect()
    container.scrollTop += reasoningRect.top - containerRect.top - 48
    return
  }
  container.scrollTop = container.scrollHeight
}

const scrollToBottom = async (force = false) => {
  await nextTick()
  scrollActiveReasoningToBottom(force)
  if (force || shouldAutoScrollMessages.value) {
    scrollMessagesToActiveContent()
  }
  requestAnimationFrame(() => {
    scrollActiveReasoningToBottom(force)
    if (force || shouldAutoScrollMessages.value) {
      scrollMessagesToActiveContent()
    }
  })
}

watch(() => aiStore.getChatMessages.length, scrollToBottom)
watch(
  () => aiStore.getChatMessages[aiStore.getChatMessages.length - 1]?.content,
  scrollToBottom
)

watch(
  () => aiStore.getChatMessages[aiStore.getChatMessages.length - 1]?.reasoning,
  scrollToBottom
)

watch(
  selectedProvider,
  provider => {
    if (!provider) {
      selectedModelName.value = ''
      return
    }
    const models = parseModels(provider.models)
    if (!selectedModelName.value) {
      selectedModelName.value = provider.defaultModel || models[0] || ''
      return
    }
    if (models.length && !models.includes(selectedModelName.value)) {
      selectedModelName.value = provider.defaultModel || models[0] || selectedModelName.value
    }
  },
  { immediate: true }
)

watch([selectedProviderId, selectedModelName], ([newProviderId, newModelName], [oldProviderId, oldModelName]) => {
  if (newProviderId !== oldProviderId || newModelName !== oldModelName) {
    chatSessionIdRef.value = createSessionId()
  }
})

watch([selectedProviderId, selectedModelName, temperatureRef, maxTokensRef, providerList], () => {
  if (!selectedProvider.value) {
    aiStore.setSelectedProvider(null)
    return
  }
  aiStore.setSelectedProvider({
    providerId: selectedProviderId.value || undefined,
    providerName: selectedProvider.value.providerName || '未命名供应商',
    modelName: selectedModelName.value || undefined,
    temperature: temperatureRef.value,
    maxTokens: maxTokensRef.value
  })
})

watch(selectedBusinessId, async value => {
  selectedBusinessPreviewContext.value = null
  selectedBusinessReadiness.value = null
  businessRuntimePreview.value = null
  businessContextExpanded.value = false
  if (!value || chatModeRef.value !== 'generate') return
  try {
    const context = await loadSelectedBusinessContext()
    if (String(selectedBusinessId.value) !== String(value)) return
    selectedBusinessPreviewContext.value = context
    selectedBusinessReadiness.value = evaluateBusinessReadiness(context)
    refreshBusinessRuntimePreview(context)
  } catch {
    selectedBusinessReadiness.value = null
  }
})

watch(chatModeRef, mode => {
  businessContextExpanded.value = false
  if (mode !== 'generate') {
    showPromptTemplatePanel.value = false
  }
  if (mode === 'generate' && selectedBusinessId.value && !selectedBusinessReadiness.value) {
    loadSelectedBusinessContext()
      .then(context => {
        selectedBusinessPreviewContext.value = context
        selectedBusinessReadiness.value = evaluateBusinessReadiness(context)
        refreshBusinessRuntimePreview(context)
      })
      .catch(() => {
        selectedBusinessReadiness.value = null
      })
  }
})

function renderContent(content: string): string {
  if (!content) return ''
  let escaped = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  escaped = escaped.replace(/```[\s\S]*?```/g, match => {
    return `<pre class="code-block">${match.replace(/```\w*\n?/g, '').replace(/```/g, '')}</pre>`
  })
  escaped = escaped.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  escaped = escaped.replace(/\n/g, '<br/>')
  return escaped
}

function visibleValidationItems(summary: GenerateValidationSummary): GenerateValidationItem[] {
  return (summary.items || []).slice(0, 8)
}

function getValidationStatusLabel(status: GenerateValidationStatus) {
  return validationStatusLabels[status] || '未知'
}

function getValidationStatusClass(status: GenerateValidationStatus) {
  return validationStatusClassNames[status] || 'is-static'
}

function getValidationDatasetText(item: GenerateValidationItem) {
  if (item.status === 'skipped') return '未知组件'
  if (item.status === 'static' || item.status === 'staticFallback') return '静态数据'
  if (item.status === 'unverified') return item.datasetName || (item.datasetId ? `数据集 ${item.datasetId}` : '未校验数据集')
  return item.datasetName || (item.datasetId ? `数据集 ${item.datasetId}` : '动态数据集')
}

function safeParseJson<T>(value?: string | null): T | null {
  if (!value) return null
  try {
    return JSON.parse(value) as T
  } catch {
    return null
  }
}

function safeJsonStringify(value: unknown) {
  try {
    return JSON.stringify(value)
  } catch {
    return ''
  }
}

function getCurrentProjectId() {
  const rawId = fetchRouteParamsLocation()
  const projectId = Number(rawId)
  return Number.isFinite(projectId) && projectId > 0 ? projectId : undefined
}

function remoteRecordToHistoryItem(record: AiDashboardGenerateRecord): GenerateHistoryDisplayItem {
  const response = safeParseJson<AIGenerateResponse>(record.responseJson)
  const validationSummary = safeParseJson<GenerateValidationSummary>(record.validationSummaryJson)
  const status = record.status || 'success'
  const canApply = status === 'success' && !!response && Array.isArray(response.components)

  return {
    id: `remote-${record.id || record.createTime || Math.random()}`,
    source: 'remote',
    recordId: record.id,
    title: record.generatedTitle || response?.title || (status === 'success' ? '未命名大屏' : '生成未完成'),
    prompt: record.prompt,
    createTime: record.createTime,
    response: canApply ? response : null,
    validationSummary: validationSummary || undefined,
    businessName: record.businessName,
    modelName: record.modelName,
    status,
    componentCount: record.componentCount ?? response?.components?.length ?? 0,
    boundCount: record.boundCount ?? validationSummary?.bound,
    staticCount: record.staticCount ?? validationSummary?.static,
    staticFallbackCount: record.staticFallbackCount ?? validationSummary?.staticFallback,
    repairedCount: record.repairedCount ?? validationSummary?.repaired,
    errorMessage: record.errorMessage
  }
}

function getHistoryStatusLabel(status?: string) {
  return historyStatusLabels[status || 'success'] || status || '未知'
}

function formatHistoryTime(timestamp?: number | string) {
  if (!timestamp) return ''
  const source = typeof timestamp === 'string' ? timestamp.replace(/-/g, '/') : timestamp
  const date = new Date(source)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function loadGenerateRecords(showError = false) {
  historyLoading.value = true
  try {
    const params: { projectId?: number; limit: number } = { limit: 20 }
    const projectId = getCurrentProjectId()
    if (projectId) {
      params.projectId = projectId
    }
    const res = await getDashboardGenerateRecentApi(params)
    remoteGenerateRecords.value = res?.data || []
    historyLoaded.value = true
  } catch (error: any) {
    historyLoaded.value = true
    if (showError) {
      window['$message']?.warning('云端生成记录加载失败: ' + (error?.message || '未知错误'))
    }
  } finally {
    historyLoading.value = false
  }
}

function toggleHistoryPanel() {
  showHistoryPanel.value = !showHistoryPanel.value
  if (showHistoryPanel.value) {
    loadGenerateRecords()
  }
}

function togglePromptTemplatePanel() {
  if (chatModeRef.value !== 'generate') return
  showPromptTemplatePanel.value = !showPromptTemplatePanel.value
  if (showPromptTemplatePanel.value) {
    loadPromptTemplates()
  }
}

async function loadPromptTemplates(force = false) {
  if (promptTemplateLoaded.value && !force) return
  promptTemplateLoading.value = true
  try {
    const res = await getPromptTemplateListApi({
      usageScene: 'dashboard_generate',
      keyword: promptTemplateKeyword.value.trim() || undefined,
      limit: 30
    })
    promptTemplateItems.value = res?.data || []
    promptTemplateLoaded.value = true
  } catch (error: any) {
    window['$message']?.warning('提示词模板加载失败: ' + (error?.message || '未知错误'))
  } finally {
    promptTemplateLoading.value = false
  }
}

async function previewPromptTemplate(template: AiPromptTemplate) {
  if (!template.id) return
  promptTemplatePreviewVisible.value = true
  promptTemplatePreviewLoading.value = true
  currentPromptTemplate.value = null
  try {
    const res = await previewPromptTemplateApi(template.id)
    currentPromptTemplate.value = res?.data || template
  } catch (error: any) {
    window['$message']?.warning('模板预览失败: ' + (error?.message || '未知错误'))
  } finally {
    promptTemplatePreviewLoading.value = false
  }
}

async function applyPromptTemplate(template: AiPromptTemplate) {
  if (!template.id) return
  try {
    const res = await usePromptTemplateApi(template.id)
    const detail = res?.data || template
    const promptContent = detail.promptContent || detail.contentSummary || ''
    if (!promptContent.trim()) {
      window['$message']?.warning('模板内容为空')
      return
    }
    const existing = inputRef.value.trim()
    inputRef.value = existing
      ? `${promptContent.trim()}\n\n用户补充需求：\n${existing}`
      : promptContent.trim()
    activePromptTemplate.value = detail
    chatModeRef.value = 'generate'
    showPromptTemplatePanel.value = false
    promptTemplatePreviewVisible.value = false
    promptTemplateLoaded.value = false
    void loadPromptTemplates(true)
    window['$message']?.success('已使用提示词模板')
  } catch (error: any) {
    window['$message']?.error('使用模板失败: ' + (error?.message || '未知错误'))
  }
}

async function downloadPromptTemplate(template: AiPromptTemplate) {
  if (!template.id) return
  try {
    const res = await downloadPromptTemplateApi(template.id)
    const detail = res?.data || template
    savePromptTemplateAsMarkdown(detail)
    if (currentPromptTemplate.value && String(currentPromptTemplate.value.id) === String(detail.id)) {
      currentPromptTemplate.value = detail
    }
    promptTemplateLoaded.value = false
    void loadPromptTemplates(true)
  } catch (error: any) {
    window['$message']?.error('下载模板失败: ' + (error?.message || '未知错误'))
  }
}

function savePromptTemplateAsMarkdown(template: AiPromptTemplate) {
  const content = [
    `# ${template.templateName || '提示词模板'}`,
    '',
    `- 业务分类：${template.businessCategory || '-'}`,
    `- 领域分类：${template.domainCategory || '-'}`,
    `- 标签：${template.templateTags || '-'}`,
    '',
    '## 提示词内容',
    '',
    template.promptContent || template.contentSummary || '',
    '',
    '## 示例输入',
    '',
    template.exampleInput || '',
    '',
    '## 说明',
    '',
    template.description || ''
  ].join('\n')
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${sanitizePromptTemplateFilename(template.templateName || 'prompt-template')}.md`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function sanitizePromptTemplateFilename(name: string) {
  return String(name).replace(/[\\/:*?"<>|]/g, '_')
}

function formatPromptTemplateCounts(template: AiPromptTemplate) {
  return `用 ${template.useCount || 0} / 测 ${template.testCount || 0} / 下 ${template.downloadCount || 0}`
}

function prependRemoteGenerateRecord(record?: AiDashboardGenerateRecord) {
  if (!record?.id) return
  remoteGenerateRecords.value = [
    record,
    ...remoteGenerateRecords.value.filter(item => String(item.id) !== String(record.id))
  ].slice(0, 20)
  historyLoaded.value = true
}

async function persistGenerateRecord(payload: AiDashboardGenerateRecordSaveRequest) {
  try {
    const res = await saveDashboardGenerateRecordApi(payload)
    prependRemoteGenerateRecord(res?.data)
  } catch (error) {
    console.warn('[AI生成记录] 保存失败', error)
  }
}

async function deleteGenerateRecord(record: GenerateHistoryDisplayItem) {
  if (!record.recordId) return
  try {
    await deleteDashboardGenerateRecordApi(record.recordId)
    remoteGenerateRecords.value = remoteGenerateRecords.value.filter(item => String(item.id) !== String(record.recordId))
    window['$message']?.success('生成记录已删除')
  } catch (error: any) {
    window['$message']?.error('删除生成记录失败: ' + (error?.message || '未知错误'))
  }
}

function buildValidatedResultText(response: AIGenerateResponse, summary: GenerateValidationSummary) {
  return [
    '大屏生成完成',
    `标题：${response.title || '未命名大屏'}`,
    `组件：${summary.accepted}/${summary.total}`,
    `动态绑定：${summary.bound}，静态/降级：${summary.static + summary.staticFallback}`,
    '已完成结果校验，可应用到画布。'
  ].join('\n')
}

function buildLineageItems(summary?: GenerateValidationSummary): AiDashboardComponentLineageItem[] {
  if (!summary?.items?.length) return []
  return summary.items
    .filter(item => item.datasetId && item.status !== 'static' && item.status !== 'staticFallback' && item.status !== 'skipped')
    .map(item => ({
      componentIndex: item.index,
      componentKey: item.key,
      componentTitle: item.title,
      datasetId: item.datasetId,
      datasetName: item.datasetName,
      fieldNames: item.fields || [],
      bindingStatus: item.status
    }))
}

function useQuickPrompt(prompt: string) {
  inputRef.value = prompt
  chatModeRef.value = 'generate'
  activePromptTemplate.value = null
}

function clearReferencedComponents() {
  referencedComponentIds.value = []
  referencePickMode.value = false
}

function toggleReferencePickMode() {
  referencePickMode.value = !referencePickMode.value
  if (!referencePickMode.value) {
    clearReferencedComponents()
  } else if (currentSelectedComponentIds.value.length) {
    referencedComponentIds.value = [...currentSelectedComponentIds.value]
  }
}

watch(currentSelectedComponentIds, (newIds) => {
  if (!referencePickMode.value) return
  referencedComponentIds.value = [...newIds]
})

function toggleReferencedComponents() {
  if (referencedComponentIds.value.length) {
    clearReferencedComponents()
    return
  }

  if (!currentSelectedComponentIds.value.length) {
    window['$message']?.warning('请先在右侧画布选中要引用的元素')
    return
  }

  referencedComponentIds.value = [...currentSelectedComponentIds.value]
}

function findComponentById(
  id: string,
  list: Array<CreateComponentType | CreateComponentGroupType>
): CreateComponentType | CreateComponentGroupType | undefined {
  for (const item of list) {
    if (item.id === id) return item
    if (item.isGroup && item.groupList?.length) {
      const target = findComponentById(id, item.groupList)
      if (target) return target
    }
  }
  return undefined
}

function sortSelectedComponentIds(ids: string[]) {
  if (!ids.length) return []
  const selectedIdSet = new Set(ids)
  const sortedIds: string[] = []

  const collect = (list: Array<CreateComponentType | CreateComponentGroupType>) => {
    list.forEach(item => {
      if (selectedIdSet.has(item.id)) sortedIds.push(item.id)
      if (item.isGroup && item.groupList?.length) collect(item.groupList)
    })
  }

  collect(chartEditStore.getComponentList)
  return sortedIds
}

function getComponentDisplayName(component: CreateComponentType | CreateComponentGroupType) {
  return component.chartConfig?.title || component.key || component.id || '未命名元素'
}

function buildReferencedComponent(component: CreateComponentType | CreateComponentGroupType): AIReferencedComponent {
  const rawComponent = toRaw(component) as CreateComponentType | CreateComponentGroupType
  const referenced: AIReferencedComponent = {
    id: rawComponent.id,
    key: rawComponent.key || rawComponent.chartConfig?.chartKey,
    title: rawComponent.chartConfig?.title,
    category: rawComponent.chartConfig?.categoryName || rawComponent.chartConfig?.category,
    isGroup: rawComponent.isGroup,
    attr: rawComponent.attr,
    styles: rawComponent.styles,
    status: rawComponent.status,
    request: rawComponent.request,
    option: rawComponent.option
  }

  if (rawComponent.isGroup && rawComponent.groupList?.length) {
    referenced.children = rawComponent.groupList.map(item => buildReferencedComponent(item))
  }

  return referenced
}

function buildSelectedComponentInstruction() {
  if (!referencedComponents.value.length) return ''
  const titles = referencedComponents.value.map(item => `${getComponentDisplayName(item)}(${item.id})`).join('、')
  return [
    '当前用户已经在右侧画布选中并引用了以下元素，请优先围绕这些元素回答：',
    titles,
    '如果用户要求调整或优化，请基于 selectedComponents 中的组件配置给出针对性建议；不要忽略当前引用元素。'
  ].join('\n')
}

function toggleReasoning(index: number) {
  const nextExpanded = !expandedReasonings.value[index]
  expandedReasonings.value[index] = nextExpanded
  if (nextExpanded) {
    delete manuallyCollapsedReasonings.value[index]
    reasoningAutoScrollState.value[index] = true
    scrollToBottom(true)
    return
  }
  manuallyCollapsedReasonings.value[index] = true
  reasoningAutoScrollState.value[index] = false
}

function setReasoningContentRef(el: Element | null, index: number) {
  if (el instanceof HTMLElement) {
    reasoningContentRefs.value[index] = el
    if (reasoningAutoScrollState.value[index] === undefined) {
      reasoningAutoScrollState.value[index] = true
    }
  } else {
    delete reasoningContentRefs.value[index]
  }
}

function scrollActiveReasoningToBottom(force = false) {
  const activeIndex = aiStore.getChatMessages.findLastIndex(msg => msg.role === 'assistant' && msg.isReasoning)
  const reasoningEl = reasoningContentRefs.value[activeIndex]
  if (reasoningEl && (force || reasoningAutoScrollState.value[activeIndex] !== false)) {
    reasoningEl.scrollTop = reasoningEl.scrollHeight
  }
}

function resetStreamingState() {
  aiRawContent.value = ''
  aiReasoningContent.value = ''
  aiIsReasoningPhase.value = false
  aiReasoningStartTime.value = null
  aiReasoningEndTime.value = null
  shouldAutoScrollMessages.value = true
  reasoningAutoScrollState.value = {}
  manuallyCollapsedReasonings.value = {}
}

function getReasoningTime() {
  if (!aiReasoningStartTime.value)
    return null
  const endTime = aiReasoningEndTime.value || Date.now()
  return Math.max(1, Math.round((endTime - aiReasoningStartTime.value) / 1000))
}

function updateAssistantStreaming(content: string, canvasResponse?: AIGenerateResponse | null) {
  aiStore.updateLastAssistantMessage(content, canvasResponse, {
    reasoning: aiReasoningContent.value,
    isReasoning: aiIsReasoningPhase.value,
    reasoningTime: aiReasoningContent.value ? getReasoningTime() : null,
    progressSteps: getGenerateProgressSteps(aiRawContent.value)
  })
  const lastMessageIndex = aiStore.getChatMessages.length - 1
  const lastMsg = aiStore.getChatMessages[lastMessageIndex]
  if (lastMsg?.role === 'assistant') {
    lastMsg.streaming = aiStore.getGenerating
    if (aiIsReasoningPhase.value && manuallyCollapsedReasonings.value[lastMessageIndex] !== true) {
      expandedReasonings.value[lastMessageIndex] = true
    }
  }
}

function appendAnswerContent(text: string, mode: 'generate' | 'chat') {
  if (!text)
    return
  aiRawContent.value += text
  const displayContent = mode === 'generate'
    ? buildGenerateStreamingPreview(aiRawContent.value)
    : aiRawContent.value
  updateAssistantStreaming(displayContent, null)
}

function getGenerateProgressSteps(fullText: string) {
  const activeCount = Math.min(
    GENERATE_PROGRESS_STEPS.length,
    Math.max(1, Math.ceil((fullText.trim().length || 1) / 90))
  )
  return GENERATE_PROGRESS_STEPS.map((step, index) => ({
    ...step,
    status: index < activeCount - 1 ? 'done' : index === activeCount - 1 ? 'active' : 'pending'
  }))
}

function consumeReasoningAwareChunk(chunk: string, mode: 'generate' | 'chat') {
  if (!chunk)
    return

  const reasoningDelimiter = '==================== 思考过程 ===================='
  const answerDelimiter = '==================== 完整回复 ===================='
  let remaining = chunk

  while (remaining) {
    const reasoningIndex = remaining.indexOf(reasoningDelimiter)
    const answerIndex = remaining.indexOf(answerDelimiter)

    if (reasoningIndex >= 0 && (answerIndex < 0 || reasoningIndex < answerIndex)) {
      appendAnswerContent(remaining.slice(0, reasoningIndex), mode)
      aiIsReasoningPhase.value = true
      aiReasoningContent.value = ''
      aiReasoningStartTime.value = Date.now()
      aiReasoningEndTime.value = null
      updateAssistantStreaming('', null)
      remaining = remaining.slice(reasoningIndex + reasoningDelimiter.length).replace(/^\s*\n?/, '')
      continue
    }

    if (answerIndex >= 0) {
      const beforeAnswer = remaining.slice(0, answerIndex)
      if (aiIsReasoningPhase.value) {
        aiReasoningContent.value += beforeAnswer
        aiIsReasoningPhase.value = false
        aiReasoningEndTime.value = Date.now()
      } else {
        appendAnswerContent(beforeAnswer, mode)
      }
      remaining = remaining.slice(answerIndex + answerDelimiter.length).replace(/^\s*\n?/, '')
      continue
    }

    if (aiIsReasoningPhase.value) {
      aiReasoningContent.value += remaining
      updateAssistantStreaming('', null)
    } else {
      appendAnswerContent(remaining, mode)
    }
    break
  }
}

function extractAnswerContent(fullText: string) {
  if (aiRawContent.value)
    return aiRawContent.value
  const answerDelimiter = '==================== 完整回复 ===================='
  if (fullText.includes(answerDelimiter)) {
    return fullText.split(answerDelimiter).pop()?.trim() || ''
  }
  return fullText || ''
}

function getCanvasSize() {
  try {
    const config = chartEditStore.getEditCanvasConfig
    if (config?.width && config?.height) {
      return { width: config.width, height: config.height }
    }
  } catch {
    // ignore
  }
  return { width: 1920, height: 1080 }
}

function buildCanvasContext() {
  try {
    const canvasConfig = chartEditStore.getEditCanvasConfig
    const componentList = chartEditStore.getComponentList || []
    const referencedComponentContext = referencedComponents.value.map(item => buildReferencedComponent(item))

    const existingLayouts = componentList.map(item => {
      const component = toRaw(item) as CreateComponentType | CreateComponentGroupType
      return {
        key: component.key || component.chartConfig?.chartKey,
        title: component.chartConfig?.title || '',
        x: component.attr?.x,
        y: component.attr?.y,
        w: component.attr?.w,
        h: component.attr?.h
      }
    })

    return JSON.stringify(
      {
        canvas: {
          projectName: canvasConfig?.projectName,
          width: canvasConfig?.width,
          height: canvasConfig?.height,
          background: canvasConfig?.background || canvasConfig?.backgroundColor
        },
        selectedComponentIds: referencedComponentIds.value,
        selectedComponents: referencedComponentContext,
        selectionInstruction: buildSelectedComponentInstruction(),
        existingComponentLayouts: existingLayouts
      },
      null,
      2
    )
  } catch {
    return ''
  }
}

function buildGenerateStreamingPreview(fullText: string): string {
  return fullText.trim() ? 'AI 正在生成大屏，请稍候...' : 'AI 正在理解需求...'
}

function getDatasetFieldName(field: DataDatasetField) {
  return String(field.fieldName || '').trim()
}

function getFieldsByRole(fields: DataDatasetField[], roles: string[], limit = 8) {
  const roleSet = new Set(roles.map(role => role.toLowerCase()))
  return fields
    .filter(field => isPromptSafeField(field) && roleSet.has(String(field.fieldRole || '').toLowerCase()) && getDatasetFieldName(field))
    .slice(0, limit)
    .map(field => getDatasetFieldName(field))
}

function getDisplayFieldNames(fields: DataDatasetField[], limit = 12) {
  return fields
    .filter(field => isPromptSafeField(field) && field.displayEnabled !== 0 && getDatasetFieldName(field))
    .slice(0, limit)
    .map(field => getDatasetFieldName(field))
}

function getSensitiveLevel(field: DataDatasetField) {
  return String(field.sensitiveLevel || '').trim().toUpperCase()
}

function isPromptSafeField(field: DataDatasetField) {
  return getSensitiveLevel(field) !== 'HIDDEN'
}

function buildPromptField(field: DataDatasetField) {
  if (!isPromptSafeField(field)) return null
  const sensitiveLevel = getSensitiveLevel(field)
  const masked = sensitiveLevel === 'MASK'
  return {
    fieldName: field.fieldName,
    fieldLabel: field.fieldLabel,
    dataType: field.dataType,
    fieldRole: field.fieldRole,
    defaultAgg: field.defaultAgg,
    dataUnit: field.dataUnit,
    dimensionName: field.dimensionName,
    displayEnabled: field.displayEnabled,
    sensitiveLevel: masked ? 'MASK' : sensitiveLevel || undefined,
    safeUsage: masked ? '敏感字段，运行时只允许脱敏展示或聚合统计，AI 不得要求展示原始值。' : undefined,
    description: masked ? '敏感字段，已从 prompt 中移除脱敏规则和原始样例。' : field.description
  }
}

function buildDatasetProfile(dataset: DataBusinessDatasetContext) {
  const fields = dataset.fields || []
  const dimensions = getFieldsByRole(fields, ['DIMENSION', 'DATE', 'TIME', 'CATEGORY', 'NAME'])
  const metrics = getFieldsByRole(fields, ['METRIC', 'MEASURE', 'VALUE', 'NUMBER'])
  const fallbackFields = getDisplayFieldNames(fields)

  return {
    datasetId: dataset.datasetId,
    datasetCode: dataset.datasetCode,
    datasetName: dataset.datasetName,
    priority: dataset.isPrimary === 1 ? 'primary' : 'normal',
    usageRemark: dataset.usageRemark,
    description: dataset.description,
    matchHints: [
      dataset.datasetCode,
      dataset.datasetName,
      dataset.usageRemark
    ].filter(Boolean),
    recommendedFields: {
      dimensions: dimensions.length ? dimensions : fallbackFields.slice(0, 4),
      metrics: metrics.length ? metrics : fallbackFields.slice(1, 7),
      displayFields: fallbackFields
    }
  }
}

const loadProviders = async () => {
  providerLoading.value = true
  try {
    const res = await getProviderPageApi({ pageNum: 1, pageSize: 100 })
    const records = (res?.data?.records || []).filter(item => item.status !== '1')
    providerList.value = records

    if (!records.length) {
      selectedProviderId.value = null
      return
    }

    const matched = records.find(item => String(item.id) === String(selectedProviderId.value))
    if (matched) return

    const defaultProvider = records.find(item => item.isDefault === '1') || records[0]
    selectedProviderId.value = defaultProvider?.id ?? null
  } catch (error: any) {
    window['$message']?.error('加载 AI 供应商失败: ' + (error?.message || '未知错误'))
  } finally {
    providerLoading.value = false
  }
}

const businessContextCache = new Map<string, DataBusinessAiContext>()
const businessRuntimePreviewCache = new Map<string, BusinessRuntimePreviewSummary>()

const loadBusinessDefinitions = async () => {
  businessLoading.value = true
  try {
    businessContextCache.clear()
    businessRuntimePreviewCache.clear()
    const res = await getDataBusinessList()
    businessList.value = res?.data || []
    if (selectedBusinessId.value && !businessList.value.some(item => String(item.id) === String(selectedBusinessId.value))) {
      selectedBusinessId.value = null
    }
  } catch (error: any) {
    window['$message']?.error('加载业务定义失败: ' + (error?.message || '未知错误'))
  } finally {
    businessLoading.value = false
  }
}

async function refreshBusinessRuntimePreview(context?: DataBusinessAiContext | null, force = false) {
  if (!selectedBusinessId.value || !context) {
    businessRuntimePreview.value = null
    return null
  }
  const cacheKey = String(selectedBusinessId.value)
  if (!force && businessRuntimePreviewCache.has(cacheKey)) {
    const cached = businessRuntimePreviewCache.get(cacheKey) || null
    businessRuntimePreview.value = cached
    return cached
  }

  runtimePreviewLoading.value = true
  try {
    const summary = await previewBusinessRuntimeData(context, {
      maxDatasets: 6,
      maxFields: 10,
      maxRows: 3
    })
    if (String(selectedBusinessId.value) !== cacheKey) return summary
    if (summary) {
      businessRuntimePreviewCache.set(cacheKey, summary)
      businessRuntimePreview.value = summary
    }
    return summary
  } catch (error: any) {
    if (force) {
      window['$message']?.warning('数据预检失败: ' + (error?.message || '未知错误'))
    }
    return null
  } finally {
    if (String(selectedBusinessId.value) === cacheKey) {
      runtimePreviewLoading.value = false
    }
  }
}

async function ensureBusinessRuntimePreview(context?: DataBusinessAiContext | null) {
  if (!selectedBusinessId.value || !context) return null
  const cacheKey = String(selectedBusinessId.value)
  if (businessRuntimePreviewCache.has(cacheKey)) {
    const cached = businessRuntimePreviewCache.get(cacheKey) || null
    businessRuntimePreview.value = cached
    return cached
  }
  return refreshBusinessRuntimePreview(context)
}

function compactBusinessContext(context: DataBusinessAiContext, runtimePreview?: BusinessRuntimePreviewSummary | null) {
  const readiness = evaluateBusinessReadiness(context)
  const datasets = (context.datasets || []).slice(0, 8).map(dataset => ({
    datasetId: dataset.datasetId,
    datasetCode: dataset.datasetCode,
    datasetName: dataset.datasetName,
    datasetType: dataset.datasetType,
    isPrimary: dataset.isPrimary,
    usageRemark: dataset.usageRemark,
    description: dataset.description,
    paramSchemaJson: dataset.paramSchemaJson,
    fields: (dataset.fields || [])
      .map(field => buildPromptField(field))
      .filter(Boolean)
      .slice(0, 24)
  }))

  return JSON.stringify({
    instruction: [
      '这是数据驱动大屏生成任务，请优先使用 datasets 中的真实 datasetId 生成动态数据组件。',
      '所有图表、表格、排行、指标组件只要能映射到数据集，必须输出 request.datasetId、request.datasetFields 和 request.datasetMapping。',
      'request.datasetId 只能来自 datasets；request.datasetFields 只能使用该数据集 fields 内的 fieldName，不能使用 fieldLabel。',
      '如果无法确定 datasetId，请优先根据 datasetProfiles.matchHints、recommendedFields、usageRemark 选择最接近的数据集，而不是直接生成静态数据。',
      '字段上下文已按当前用户权限和敏感级别清洗：隐藏字段不会出现，脱敏字段只能用于脱敏展示或聚合统计。',
      '标题、装饰、模块框、说明文字可以静态；业务指标、趋势、排行、表格、地图必须尽量动态绑定。',
      '允许保留 option.dataset 作为预览兜底，但动态组件仍必须带 request。'
    ],
    generationPolicy: {
      bindingPriority: [
        '优先使用 runtimeDataPreview 中 status=ready 的数据集，样例行可用于判断字段取值、量级和图表类型。',
        'status=failed/empty/skipped 的数据集不要作为核心图表首选，除非业务定义明确需要。',
        '优先使用 primary 数据集生成顶部 KPI、主图和核心趋势。',
        '按 usageRemark 匹配趋势、排行、明细、地图、告警等组件。',
        '字段映射优先使用 recommendedFields.dimensions 作为 category/time/name，recommendedFields.metrics 作为 value/series。',
        '不要输出未在当前业务定义中出现的 datasetId。'
      ],
      requiredRequestShape: {
        datasetId: 'number',
        datasetName: 'string',
        datasetFields: ['fieldName'],
        datasetMapping: {
          mode: 'auto',
          fieldMap: {
            category: '维度字段 fieldName',
            value: '指标字段 fieldName',
            time: '时间字段 fieldName',
            series: '系列字段 fieldName'
          },
          outputFields: ['fieldName'],
          syncHeader: true
        }
      },
      readiness
    },
    runtimeDataPreview: runtimePreview
      ? {
          instruction: [
            'sampleRows 只用于理解字段取值和数据形态，不要把样例值写死为最终静态数据。',
            '如果数据集 status 为 ready，请生成动态 request 绑定；如果 status 为 failed/empty/skipped，优先选择其他可查询数据集。',
            '当业务必须使用异常数据集时，组件可以先静态兜底，但标题要表达业务含义。'
          ],
          summary: {
            total: runtimePreview.total,
            ready: runtimePreview.ready,
            empty: runtimePreview.empty,
            failed: runtimePreview.failed,
            skipped: runtimePreview.skipped
          },
          items: runtimePreview.items.map(item => ({
            datasetId: item.datasetId,
            datasetCode: item.datasetCode,
            datasetName: item.datasetName,
            status: item.status,
            statusLabel: runtimePreviewStatusLabels[item.status],
            fields: item.fields,
            rowCount: item.rowCount,
            sampleRows: item.status === 'ready' ? item.sampleRows : [],
            message: item.message
          }))
        }
      : {
          status: 'not_loaded',
          instruction: '当前没有运行时样例，请只基于业务定义和字段语义生成，并严格输出 request.datasetId。'
        },
    business: {
      businessId: context.businessId,
      businessCode: context.businessCode,
      businessName: context.businessName,
      businessDesc: context.businessDesc,
      analysisGoal: context.analysisGoal,
      metricDefinition: context.metricDefinition,
      dimensionDefinition: context.dimensionDefinition,
      usageGuide: context.usageGuide
    },
    datasetProfiles: (context.datasets || []).slice(0, 8).map(dataset => buildDatasetProfile(dataset)),
    datasets
  }, null, 2)
}

async function loadSelectedBusinessContext(): Promise<DataBusinessAiContext | null> {
  if (!selectedBusinessId.value) return null
  const cacheKey = String(selectedBusinessId.value)
  let context = businessContextCache.get(cacheKey)
  if (!context) {
    const res = await getDataBusinessAiContext(selectedBusinessId.value)
    context = res?.data
    if (context) {
      businessContextCache.set(cacheKey, context)
    }
  }
  return context || null
}

async function handleSend() {
  const typedContent = inputRef.value.trim()
  const content = typedContent || (
    chatModeRef.value === 'generate' && selectedBusinessId.value
      ? '请基于所选业务定义和绑定数据集自动生成数据大屏。'
      : ''
  )
  if (!content || aiStore.getGenerating) return
  const selectedPromptTemplate = activePromptTemplate.value
  businessContextExpanded.value = false

  if (!selectedProvider.value || !selectedProviderId.value) {
    window['$message']?.warning('请先选择一个可用的 AI 供应商')
    showModeSelect.value = true
    return
  }

  const modelName = selectedModelName.value || selectedProvider.value.defaultModel || parseModels(selectedProvider.value.models)[0]
  if (!modelName) {
    window['$message']?.warning('当前供应商未配置可用模型，请先在 AI 供应商页面维护')
    return
  }

  let selectedBusinessContext = ''
  let selectedBusinessContextData: DataBusinessAiContext | null = null
  if (chatModeRef.value === 'generate' && selectedBusinessId.value) {
    try {
      selectedBusinessContextData = await loadSelectedBusinessContext()
      selectedBusinessPreviewContext.value = selectedBusinessContextData
      selectedBusinessReadiness.value = evaluateBusinessReadiness(selectedBusinessContextData)
      const runtimePreview = await ensureBusinessRuntimePreview(selectedBusinessContextData)
      selectedBusinessContext = selectedBusinessContextData
        ? compactBusinessContext(selectedBusinessContextData, runtimePreview)
        : ''
      if (selectedBusinessReadiness.value?.level === 'low') {
        window['$message']?.warning(`当前业务定义 AI 准备度 ${selectedBusinessReadiness.value.score}，可能会产生较多静态组件`)
      }
      if (runtimePreview && runtimePreview.ready === 0 && runtimePreview.total > 0) {
        window['$message']?.warning('当前业务数据集预检无可查样例，AI 将更多依赖字段语义生成')
      }
    } catch (error: any) {
      window['$message']?.error('业务定义上下文加载失败: ' + (error?.message || '未知错误'))
      return
    }
  }

  const requestCanvasContext = buildCanvasContext()
  clearReferencedComponents()
  inputRef.value = ''
  activePromptTemplate.value = null
  aiStore.setGenerating(true)
  resetStreamingState()

  aiStore.addChatMessage({
    id: `user-${Date.now()}`,
    role: 'user',
    content,
    timestamp: Date.now(),
    sessionId: chatSessionIdRef.value
  })
  aiStore.addChatMessage({
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    sessionId: chatSessionIdRef.value,
    streaming: true,
    reasoning: '',
    isReasoning: false,
    reasoningTime: null,
    progressSteps: getGenerateProgressSteps(''),
    canvasResponse: null
  })

  await scrollToBottom(true)
  const abortController = aiStore.getAbortController()

  if (chatModeRef.value === 'generate') {
    const { width, height } = getCanvasSize()
    const generateStartedAt = Date.now()
    const generateRequest = {
      prompt: content,
      sessionId: chatSessionIdRef.value,
      style: styleRef.value,
      canvasWidth: width,
      canvasHeight: height,
      componentCatalog: getComponentCatalogText(),
      projectName: chartEditStore.getEditCanvasConfig?.projectName,
      canvasContext: requestCanvasContext,
      businessDefinitionId: selectedBusinessId.value || undefined,
      businessContext: selectedBusinessContext || undefined,
      providerId: selectedProviderId.value,
      modelName,
      temperature: temperatureRef.value,
      maxTokens: maxTokensRef.value || undefined,
      promptTemplateId: selectedPromptTemplate?.id,
      promptTemplateName: selectedPromptTemplate?.templateName
    }
    const buildGenerateRecordPayload = (
      overrides: Partial<AiDashboardGenerateRecordSaveRequest> = {}
    ): AiDashboardGenerateRecordSaveRequest => ({
      sessionId: chatSessionIdRef.value,
      projectId: getCurrentProjectId(),
      projectName: chartEditStore.getEditCanvasConfig?.projectName,
      businessDefinitionId: selectedBusinessId.value || undefined,
      businessName: selectedBusiness.value?.businessName,
      providerId: selectedProviderId.value || undefined,
      providerName: selectedProvider.value?.providerName,
      modelName,
      style: styleRef.value,
      canvasWidth: width,
      canvasHeight: height,
      prompt: content,
      requestJson: safeJsonStringify(generateRequest),
      elapsedMs: Date.now() - generateStartedAt,
      ...overrides
    })

    updateAssistantStreaming(buildGenerateStreamingPreview(''), null)

    await aiGenerateStream(
      generateRequest,
      chunk => {
        consumeReasoningAwareChunk(chunk, 'generate')
        scrollToBottom()
      },
      fullText => {
        if (!fullText) {
          aiStore.updateLastAssistantMessage('⏹️ 已停止生成', null, {
            reasoning: aiReasoningContent.value,
            isReasoning: false,
            reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
          })
          void persistGenerateRecord(buildGenerateRecordPayload({
            status: 'stopped',
            errorMessage: '用户停止生成'
          }))
          aiStore.setGenerating(false)
          scrollToBottom()
          return
        }

        const answerContent = extractAnswerContent(fullText)
        if (!answerContent) {
          aiStore.updateLastAssistantMessage('⏹️ 已停止生成', null, {
            reasoning: aiReasoningContent.value,
            isReasoning: false,
            reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
          })
          void persistGenerateRecord(buildGenerateRecordPayload({
            status: 'stopped',
            errorMessage: '生成响应为空'
          }))
          aiStore.setGenerating(false)
          scrollToBottom()
          return
        }

        try {
          const parsedResponse = parseStreamedResponse(answerContent)
          const validationResult = validateAIGenerateResponse(parsedResponse, {
            businessContext: selectedBusinessContextData,
            canvasWidth: width,
            canvasHeight: height
          })
          const canvasResponse = validationResult.response
          const displayText = buildValidatedResultText(canvasResponse, validationResult.summary)
          aiStore.updateLastAssistantMessage(displayText, canvasResponse, {
            reasoning: aiReasoningContent.value,
            isReasoning: false,
            reasoningTime: aiReasoningContent.value ? getReasoningTime() : null,
            progressSteps: undefined,
            validationSummary: validationResult.summary
          })
          aiStore.addHistory(content, canvasResponse, {
            businessDefinitionId: selectedBusinessId.value || undefined,
            businessName: selectedBusiness.value?.businessName,
            providerName: selectedProvider.value?.providerName,
            modelName,
            validationSummary: validationResult.summary
          })
          void persistGenerateRecord(buildGenerateRecordPayload({
            status: 'success',
            generatedTitle: canvasResponse.title,
            responseJson: safeJsonStringify(canvasResponse),
            validationSummaryJson: safeJsonStringify(validationResult.summary),
            componentCount: validationResult.summary.accepted,
            boundCount: validationResult.summary.bound,
            staticCount: validationResult.summary.static,
            staticFallbackCount: validationResult.summary.staticFallback,
            repairedCount: validationResult.summary.repaired,
            lineageItems: buildLineageItems(validationResult.summary)
          }))
        } catch (error: any) {
          aiStore.updateLastAssistantMessage(`❌ 生成结果解析失败：${error?.message || '返回内容不是合法 JSON'}`, null, {
            reasoning: aiReasoningContent.value,
            isReasoning: false,
            reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
          })
          void persistGenerateRecord(buildGenerateRecordPayload({
            status: 'parse_failed',
            responseJson: answerContent,
            errorMessage: error?.message || '返回内容不是合法 JSON'
          }))
        } finally {
          aiStore.setGenerating(false)
          scrollToBottom()
        }
      },
      error => {
        const errorMessage = error?.message || '未知错误'
        aiStore.updateLastAssistantMessage(`❌ 生成失败：${error?.message || '未知错误'}`, null, {
          reasoning: aiReasoningContent.value,
          isReasoning: false,
          reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
        })
        void persistGenerateRecord(buildGenerateRecordPayload({
          status: 'failed',
          errorMessage
        }))
        aiStore.setGenerating(false)
        scrollToBottom()
      },
      abortController.signal
    )
    return
  }

  const activeSessionId = aiStore.currentSessionId || chatSessionIdRef.value
  aiStore.setCurrentSessionId(activeSessionId)
  chatSessionIdRef.value = activeSessionId

  await aiChatStream(
    {
      content,
      agentCode: undefined,
      sessionId: activeSessionId,
      projectName: chartEditStore.getEditCanvasConfig?.projectName,
      canvasContext: requestCanvasContext,
      providerId: selectedProviderId.value,
      modelName,
      temperature: temperatureRef.value,
      maxTokens: maxTokensRef.value || undefined
    },
    chunk => {
      consumeReasoningAwareChunk(chunk, 'chat')
      scrollToBottom()
    },
    fullText => {
      if (!fullText) {
        aiStore.updateLastAssistantMessage('⏹️ 已停止生成', undefined, {
          reasoning: aiReasoningContent.value,
          isReasoning: false,
          reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
        })
        aiStore.setGenerating(false)
        scrollToBottom()
        return
      }

      const answerContent = extractAnswerContent(fullText)
      if (!answerContent) {
        aiStore.updateLastAssistantMessage('⏹️ 已停止生成', undefined, {
          reasoning: aiReasoningContent.value,
          isReasoning: false,
          reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
        })
      } else {
        aiStore.updateLastAssistantMessage(answerContent, undefined, {
          reasoning: aiReasoningContent.value,
          isReasoning: false,
          reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
        })
      }
      aiStore.setGenerating(false)
      scrollToBottom()
    },
    error => {
      aiStore.updateLastAssistantMessage(`❌ 请求失败：${error.message}`, undefined, {
        reasoning: aiReasoningContent.value,
        isReasoning: false,
        reasoningTime: aiReasoningContent.value ? getReasoningTime() : null
      })
      aiStore.setGenerating(false)
      scrollToBottom()
    },
    abortController.signal
  )
}

function handleStop() {
  aiIsReasoningPhase.value = false
  aiReasoningEndTime.value = Date.now()
  aiStore.abortGenerating()
  const lastMsg = aiStore.getChatMessages[aiStore.getChatMessages.length - 1]
  if (lastMsg?.role === 'assistant') {
    lastMsg.isReasoning = false
    lastMsg.reasoningTime = lastMsg.reasoning ? getReasoningTime() : null
  }
}

async function applyToCanvas(response: AIGenerateResponse) {
  try {
    await applyAIToCanvas(response, true)
    window['$message'].success('AI 大屏应用成功！')
    emit('applied', response)
  } catch (error) {
    window['$message'].error('应用失败：' + (error as Error).message)
  }
}

onMounted(async () => {
  await Promise.all([loadProviders(), loadBusinessDefinitions()])
  loadGenerateRecords()
  aiStore.setChatSessions([])
  if (!aiStore.currentSessionId) {
    aiStore.setCurrentSessionId(chatSessionIdRef.value)
  }
  if (!providerList.value.length) {
    window['$message']?.warning('请先在左侧菜单的 AI 供应商 页面配置可用供应商')
  }
})
</script>

<style lang="scss" scoped>
$topHeight: 40px;

.go-ai-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  width: 100%;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 0, rgba(81, 214, 169, 0.08), transparent 32%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.2), rgba(2, 6, 23, 0.08));

  .messages-container {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overscroll-behavior: contain;
    scrollbar-gutter: stable;
    padding: 10px 10px 8px;
    display: flex;
    flex-direction: column;
    gap: 12px;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: 2px;
      background: rgba(255, 255, 255, 0.15);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      padding: 18px 14px;
      text-align: center;
      border: 1px solid rgba(81, 214, 169, 0.1);
      border-radius: 14px;
      margin: 10px;
      min-height: 360px;
      background:
        linear-gradient(180deg, rgba(81, 214, 169, 0.08), transparent 42%),
        rgba(15, 23, 42, 0.22);

      .empty-title {
        font-size: 16px;
        font-weight: 600;
        @include fetch-theme('color');
        margin-bottom: 6px;
      }

      .empty-desc {
        font-size: 12px;
        color: #888;
        margin-bottom: 16px;
        line-height: 1.5;
      }

      .quick-prompts {
        display: grid;
        grid-template-columns: minmax(0, 1fr);
        gap: 8px;
        width: 100%;
        max-width: 280px;

        .quick-btn {
          width: 100%;
          min-width: 0;
          min-height: 34px;
          height: auto;
          padding: 6px 10px;
          text-align: left;
          font-size: 12px;
          justify-content: flex-start;
          border-radius: 9px;
          background: rgba(15, 23, 42, 0.56);
          border: 1px solid rgba(148, 163, 184, 0.1);

          :deep(.n-button__content) {
            width: 100%;
            min-width: 0;
            justify-content: flex-start;
            white-space: normal;
            line-height: 1.35;
            text-align: left;
          }
        }
      }
    }

    .message-item {
      display: flex;
      gap: 7px;
      align-items: flex-start;

      &.user {
        flex-direction: row-reverse;
      }

      .avatar {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        margin-top: 2px;

        &.ai-avatar {
          background: rgba(81, 214, 169, 0.15);
          border: 1px solid rgba(81, 214, 169, 0.4);
        }

        &.user-avatar {
          @include fetch-bg-color('background-color3');
          border: 1px solid rgba(255, 255, 255, 0.15);
        }
      }

      .bubble-wrapper {
        display: flex;
        flex-direction: column;
        gap: 6px;
        max-width: calc(100% - 36px);

        .reasoning-section {
          border: 1px solid rgba(81, 214, 169, 0.24);
          border-radius: 10px;
          overflow: hidden;
          background: rgba(81, 214, 169, 0.08);

          .reasoning-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            padding: 6px 8px;
            cursor: pointer;
            user-select: none;
            transition: background 0.16s ease;

            &:hover {
              background: rgba(81, 214, 169, 0.1);
            }
          }

          .reasoning-header-left {
            display: flex;
            align-items: center;
            gap: 5px;
            min-width: 0;
            font-size: 12px;
            color: #51d6a9;
            font-weight: 600;
          }

          .reasoning-duration {
            color: rgba(81, 214, 169, 0.7);
            font-size: 11px;
            font-weight: 400;

            &.thinking {
              animation: blink 1.2s infinite;
            }
          }

          .reasoning-toggle {
            color: rgba(255, 255, 255, 0.55);
            font-size: 16px;
            line-height: 1;
            transition: transform 0.18s ease;

            &.expanded {
              transform: rotate(180deg);
            }
          }

          .reasoning-content {
            max-height: 180px;
            overflow-y: auto;
            padding: 8px 10px;
            border-top: 1px solid rgba(81, 214, 169, 0.18);
            white-space: pre-wrap;
            word-break: break-word;
            font-size: 12px;
            line-height: 1.6;
            color: rgba(230, 255, 248, 0.88);
            background: rgba(0, 0, 0, 0.12);

            &::-webkit-scrollbar {
              width: 4px;
            }

            &::-webkit-scrollbar-thumb {
              border-radius: 2px;
              background: rgba(81, 214, 169, 0.32);
            }
          }
        }

        .bubble {
          padding: 8px 10px;
          border-radius: 10px;
          font-size: 13px;
          line-height: 1.55;
          word-break: break-word;

          &.assistant {
            background:
              linear-gradient(180deg, rgba(255, 255, 255, 0.05), transparent),
              rgba(15, 23, 42, 0.56);
            border: 1px solid rgba(148, 163, 184, 0.12);
            @include fetch-theme('color');
            border-bottom-left-radius: 2px;
            box-shadow: 0 8px 22px rgba(0, 0, 0, 0.12);
          }

          &.user {
            background: linear-gradient(135deg, #51d6a9, #38d9ff);
            color: #082016;
            border-bottom-right-radius: 2px;
            box-shadow: 0 8px 20px rgba(81, 214, 169, 0.12);
          }

          .typing-cursor {
            display: inline-block;
            animation: blink 0.8s infinite;
            font-weight: bold;
            color: #51d6a9;
          }

          .thinking-dots {
            display: flex;
            gap: 4px;
            align-items: center;
            padding: 2px 0;

            span {
              width: 6px;
              height: 6px;
              border-radius: 50%;
              background: #51d6a9;
              animation: bounce 1.2s infinite ease-in-out;

              &:nth-child(2) {
                animation-delay: 0.2s;
              }

              &:nth-child(3) {
                animation-delay: 0.4s;
              }
            }
          }

          .generate-progress-card,
          .generate-result-card {
            min-width: 220px;
          }

          .generate-progress-card {
            display: flex;
            flex-direction: column;
            gap: 10px;
          }

          .generate-progress-title {
            font-size: 13px;
            font-weight: 700;
            color: #e8fff8;
          }

          .generate-progress-list {
            display: grid;
            grid-template-columns: 1fr;
            gap: 5px;
          }

          .generate-progress-step {
            height: 26px;
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 6px;
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 0 8px;
            color: #8b949e;
            background: rgba(255, 255, 255, 0.035);
            font-size: 12px;

            &.done {
              color: rgba(81, 214, 169, 0.86);
              border-color: rgba(81, 214, 169, 0.28);
              background: rgba(81, 214, 169, 0.08);
            }

            &.active {
              color: #1a1a2e;
              border-color: rgba(81, 214, 169, 0.8);
              background: #51d6a9;
              font-weight: 700;
            }
          }

          .generate-progress-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            flex-shrink: 0;
            background: currentColor;
            opacity: 0.85;
          }

          .generate-progress-step.active .generate-progress-dot {
            animation: bounce 1.1s infinite ease-in-out;
          }

          .generate-progress-label {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .generate-result-card {
            display: flex;
            align-items: flex-start;
            gap: 10px;
          }

          .generate-result-icon {
            width: 32px;
            height: 32px;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            background: rgba(81, 214, 169, 0.14);
            color: #51d6a9;
            border: 1px solid rgba(81, 214, 169, 0.32);
          }

          .generate-result-main {
            min-width: 0;
            width: 100%;
            display: flex;
            flex-direction: column;
            gap: 4px;
          }

          .generate-result-title {
            font-size: 13px;
            font-weight: 700;
            color: #e8fff8;
          }

          .generate-result-name {
            font-size: 12px;
            color: rgba(255, 255, 255, 0.78);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .generate-result-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;

            span {
              border-radius: 999px;
              padding: 2px 7px;
              background: rgba(255, 255, 255, 0.07);
              color: rgba(255, 255, 255, 0.62);
              font-size: 11px;
              line-height: 1.6;
            }
          }

          .validation-summary {
            margin-top: 6px;
            display: grid;
            gap: 6px;
          }

          .validation-metrics {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;
          }

          .metric {
            border-radius: 6px;
            padding: 2px 6px;
            font-size: 11px;
            line-height: 1.5;
            border: 1px solid rgba(255, 255, 255, 0.08);

            &.bound {
              color: #51d6a9;
              background: rgba(81, 214, 169, 0.09);
              border-color: rgba(81, 214, 169, 0.22);
            }

            &.static {
              color: rgba(226, 232, 240, 0.82);
              background: rgba(148, 163, 184, 0.1);
            }

            &.repaired {
              color: #facc15;
              background: rgba(250, 204, 21, 0.1);
              border-color: rgba(250, 204, 21, 0.22);
            }
          }

          .validation-items {
            max-height: 156px;
            overflow-y: auto;
            display: grid;
            gap: 4px;
            padding-right: 2px;

            &::-webkit-scrollbar {
              width: 4px;
            }

            &::-webkit-scrollbar-thumb {
              border-radius: 2px;
              background: rgba(81, 214, 169, 0.28);
            }
          }

          .validation-item {
            display: grid;
            grid-template-columns: 46px minmax(74px, 1fr) minmax(72px, 0.9fr);
            gap: 6px;
            align-items: center;
            min-width: 0;
            padding: 5px 6px;
            border-radius: 7px;
            background: rgba(255, 255, 255, 0.045);
          }

          .validation-status {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            height: 20px;
            border-radius: 5px;
            font-size: 11px;
            color: rgba(255, 255, 255, 0.76);
            background: rgba(148, 163, 184, 0.16);

            &.is-bound,
            &.is-repaired {
              color: #082016;
              background: #51d6a9;
              font-weight: 700;
            }

            &.is-static-fallback {
              color: #facc15;
              background: rgba(250, 204, 21, 0.14);
            }

            &.is-skipped {
              color: #f87171;
              background: rgba(248, 113, 113, 0.12);
            }
          }

          .validation-name,
          .validation-dataset {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-size: 11px;
          }

          .validation-dataset {
            color: rgba(255, 255, 255, 0.58);
          }

          .validation-warning {
            color: #facc15;
            font-size: 11px;
            line-height: 1.45;
          }
        }

        .msg-actions {
          display: flex;
          gap: 6px;
        }
      }
    }
  }

  .style-row,
  .mode-row,
  .business-context-row,
  .business-context-tip,
  .ai-history-panel,
  .prompt-template-panel,
  .config-grid,
  .selection-context,
  .provider-tip {
    border-top: 1px solid;
    @include fetch-border-color('hover-border-color');
    background: rgba(10, 16, 28, 0.78);
    flex-shrink: 0;
  }

  .style-row,
  .mode-row,
  .business-context-row {
    display: flex;
    align-items: center;
    padding: 7px 10px;
    gap: 7px;

    .style-label {
      font-size: 12px;
      color: #888;
      white-space: nowrap;
    }

    :deep(.n-base-selection) {
      flex: 1;
      min-width: 0;
    }
  }

  .business-context-tip {
    display: grid;
    gap: 6px;
    padding: 6px 10px;
    color: #7f8c8d;
    font-size: 12px;

    &.is-expanded {
      padding-bottom: 8px;
    }
  }

  .business-context-compact {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
  }

  .business-tip-main {
    display: flex;
    align-items: center;
    gap: 5px;
    min-width: 0;
    flex: 1;
    overflow: hidden;
  }

  .business-tip-name {
    min-width: 0;
    max-width: 120px;
    overflow: hidden;
    color: rgba(255, 255, 255, 0.84);
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .business-tip-chip {
    min-width: 0;
    overflow: hidden;
    padding: 2px 6px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.07);
    color: rgba(255, 255, 255, 0.62);
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;

    &.is-high,
    &.is-ready {
      background: rgba(81, 214, 169, 0.12);
      color: #9ff2d7;
    }

    &.is-medium,
    &.is-partial,
    &.is-loading {
      background: rgba(250, 204, 21, 0.12);
      color: #fde68a;
    }

    &.is-low,
    &.is-failed {
      background: rgba(248, 113, 113, 0.13);
      color: #fecaca;
    }
  }

  .business-tip-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-shrink: 0;
  }

  .business-expand-icon {
    display: inline-block;
    margin-left: 2px;
    transition: transform 0.18s ease;

    &.expanded {
      transform: rotate(180deg);
    }
  }

  .business-context-detail {
    display: grid;
    gap: 7px;
    max-height: 172px;
    overflow-y: auto;
    padding-right: 2px;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: 2px;
      background: rgba(255, 255, 255, 0.16);
    }
  }

  .business-readiness {
    display: grid;
    gap: 5px;
    padding: 8px;
    border-radius: 8px;
    border: 1px solid rgba(148, 163, 184, 0.12);
    background: rgba(15, 23, 42, 0.42);

    &.is-high {
      border-color: rgba(81, 214, 169, 0.26);
      background: rgba(81, 214, 169, 0.08);
    }

    &.is-medium {
      border-color: rgba(250, 204, 21, 0.24);
      background: rgba(250, 204, 21, 0.07);
    }

    &.is-low {
      border-color: rgba(248, 113, 113, 0.24);
      background: rgba(248, 113, 113, 0.08);
    }
  }

  .business-readiness-score {
    color: rgba(255, 255, 255, 0.86);
    font-weight: 700;
  }

  .business-readiness-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;

    span {
      padding: 2px 6px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.07);
      color: rgba(255, 255, 255, 0.64);
    }
  }

  .business-readiness-suggestion {
    color: #facc15;
    line-height: 1.45;
  }

  .business-runtime-preview {
    display: grid;
    gap: 5px;
    padding: 8px;
    border-radius: 8px;
    border: 1px solid rgba(148, 163, 184, 0.12);
    background: rgba(15, 23, 42, 0.38);

    &.is-ready {
      border-color: rgba(81, 214, 169, 0.24);
      background: rgba(81, 214, 169, 0.07);
    }

    &.is-partial,
    &.is-loading {
      border-color: rgba(250, 204, 21, 0.24);
      background: rgba(250, 204, 21, 0.07);
    }

    &.is-failed {
      border-color: rgba(248, 113, 113, 0.24);
      background: rgba(248, 113, 113, 0.08);
    }
  }

  .runtime-preview-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .runtime-preview-title {
    min-width: 0;
    color: rgba(255, 255, 255, 0.86);
    font-weight: 700;
  }

  .runtime-preview-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;

    span {
      padding: 2px 6px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.07);
      color: rgba(255, 255, 255, 0.64);
    }
  }

  .runtime-preview-message {
    color: #facc15;
    line-height: 1.45;
  }

  .ai-history-panel {
    display: grid;
    gap: 6px;
    padding: 8px 10px;
    max-height: 220px;
    overflow-y: auto;
  }

  .ai-history-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    color: rgba(255, 255, 255, 0.72);
    font-size: 12px;
    font-weight: 700;
  }

  .ai-history-empty {
    min-height: 42px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px dashed rgba(148, 163, 184, 0.18);
    border-radius: 8px;
    color: #7f8c8d;
    font-size: 12px;
  }

  .ai-history-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
    padding: 8px;
    border: 1px solid rgba(148, 163, 184, 0.12);
    border-radius: 8px;
    background: rgba(15, 23, 42, 0.5);

    &.failed {
      border-color: rgba(248, 113, 113, 0.18);
      background: rgba(127, 29, 29, 0.12);
    }
  }

  .ai-history-main {
    min-width: 0;
    display: grid;
    gap: 4px;
  }

  .ai-history-title {
    color: rgba(255, 255, 255, 0.86);
    font-size: 12px;
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ai-history-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
    color: #7f8c8d;
    font-size: 11px;
  }

  .ai-history-status {
    color: #facc15;
  }

  .ai-history-error {
    min-width: 0;
    color: rgba(248, 113, 113, 0.86);
    font-size: 11px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ai-history-actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .prompt-template-panel {
    display: grid;
    gap: 7px;
    padding: 8px 10px;
    max-height: 260px;
    overflow-y: auto;
  }

  .prompt-template-header {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    gap: 7px;
    color: rgba(255, 255, 255, 0.72);
    font-size: 12px;
    font-weight: 700;
  }

  .prompt-template-empty {
    min-height: 42px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px dashed rgba(148, 163, 184, 0.18);
    border-radius: 8px;
    color: #7f8c8d;
    font-size: 12px;
  }

  .prompt-template-item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
    padding: 8px;
    border: 1px solid rgba(81, 214, 169, 0.14);
    border-radius: 8px;
    background: rgba(15, 23, 42, 0.5);
  }

  .prompt-template-main {
    min-width: 0;
    display: grid;
    gap: 4px;
  }

  .prompt-template-title {
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 6px;
    color: rgba(255, 255, 255, 0.88);
    font-size: 12px;
    font-weight: 700;

    span:first-child {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .prompt-template-badge {
    flex-shrink: 0;
    padding: 1px 5px;
    border-radius: 999px;
    color: #082016;
    background: #51d6a9;
    font-size: 10px;
    line-height: 1.5;
  }

  .prompt-template-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;
    color: #7f8c8d;
    font-size: 11px;

    span {
      padding: 1px 5px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.06);
    }
  }

  .prompt-template-summary {
    min-width: 0;
    color: rgba(226, 232, 240, 0.62);
    font-size: 11px;
    line-height: 1.45;
    display: -webkit-box;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .prompt-template-actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .prompt-template-preview {
    display: grid;
    gap: 8px;
  }

  .prompt-template-preview-title {
    color: rgba(255, 255, 255, 0.88);
    font-size: 14px;
    font-weight: 700;
  }

  .prompt-template-preview-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    color: rgba(255, 255, 255, 0.62);
    font-size: 12px;

    span {
      padding: 2px 7px;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.07);
    }
  }

  .prompt-template-preview-content {
    max-height: 420px;
    margin: 0;
    padding: 10px;
    overflow: auto;
    border-radius: 8px;
    background: rgba(15, 23, 42, 0.82);
    color: rgba(230, 255, 248, 0.9);
    font-size: 12px;
    line-height: 1.65;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .config-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 7px 8px;
    padding: 8px 10px 9px;

    .config-item {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;

      .style-label {
        font-size: 12px;
        color: #888;
        white-space: nowrap;
      }

      :deep(.n-base-selection),
      :deep(.n-input-number) {
        flex: 1;
        min-width: 0;
      }
    }

    .config-provider,
    .config-model,
    .config-temperature,
    .config-max-tokens {
      grid-column: span 2;
    }
  }

  .provider-tip {
    padding: 0 10px 8px;
    font-size: 12px;
    color: #7f8c8d;
  }

  .selection-context {
    min-height: 38px;
    padding: 6px 10px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    color: rgba(255, 255, 255, 0.42);

    &.active {
      background: linear-gradient(90deg, rgba(81, 214, 169, 0.12), rgba(81, 214, 169, 0.04));
      border-top-color: rgba(81, 214, 169, 0.22);
      color: rgba(232, 255, 248, 0.88);
    }

    .selection-context-main {
      min-width: 0;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .selection-context-icon {
      width: 22px;
      height: 22px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      color: #51d6a9;
      background: rgba(81, 214, 169, 0.12);
      border: 1px solid rgba(81, 214, 169, 0.26);
    }

    .selection-context-info {
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .selection-context-title {
      font-size: 12px;
      font-weight: 700;
      color: #dffcf3;
      line-height: 1.3;
    }

    .selection-context-name {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 11px;
      line-height: 1.4;
    }

    .selection-context-name {
      color: rgba(232, 255, 248, 0.62);
    }
  }

  .input-area {
    padding: 8px 10px 10px;
    flex-shrink: 0;
    background:
      linear-gradient(180deg, rgba(15, 23, 42, 0.72), rgba(2, 6, 23, 0.92));
    border-top: 1px solid rgba(var(--app-theme-rgb), 0.1);

    :deep(.n-input) {
      border-radius: 12px;
      background: rgba(2, 6, 23, 0.52);
    }

    :deep(.n-input__textarea-el) {
      font-size: 13px;
      line-height: 1.55;
    }

    .input-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 7px;
      min-height: 26px;
      gap: 8px;

      .input-tools {
        display: flex;
        align-items: center;
        gap: 6px;
        min-width: 0;
      }

      .reference-btn {
        flex-shrink: 0;
        color: #777;
        width: 28px;
        height: 28px;

        &.active {
          color: #51d6a9;
          background: rgba(81, 214, 169, 0.14);
        }
      }

      .hint-text {
        font-size: 11px;
        color: #666;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}

.prompt-template-preview {
  display: grid;
  gap: 8px;
}

.prompt-template-preview-title {
  color: rgba(255, 255, 255, 0.88);
  font-size: 14px;
  font-weight: 700;
}

.prompt-template-preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;

  span {
    padding: 2px 7px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.07);
  }
}

.prompt-template-preview-content {
  max-height: 420px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.82);
  color: rgba(230, 255, 248, 0.9);
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.5;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}

:deep(.code-block) {
  background: rgba(0, 0, 0, 0.3);
  border-radius: 4px;
  padding: 8px;
  margin: 4px 0;
  font-size: 11px;
  overflow-x: auto;
  white-space: pre-wrap;
  font-family: 'Consolas', 'Monaco', monospace;
}

:deep(.inline-code) {
  background: rgba(81, 214, 169, 0.2);
  color: #51d6a9;
  border-radius: 3px;
  padding: 1px 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
