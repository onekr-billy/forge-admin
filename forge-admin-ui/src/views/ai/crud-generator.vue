<template>
  <div class="crud-generator-page">
    <div class="generator-sidebar" :class="[{ collapsed: sidebarCollapsed }]">
      <div class="sidebar-header">
        <template v-if="!sidebarCollapsed">
          <n-button size="small" text @click="goBack">
            <template #icon>
              <n-icon><ArrowBackOutline /></n-icon>
            </template>
            返回
          </n-button>
          <n-button size="small" type="primary" @click="handleNewSession">
            <template #icon>
              <n-icon><AddOutline /></n-icon>
            </template>
            新对话
          </n-button>
        </template>
        <template v-else>
          <n-button size="small" quaternary circle @click="sidebarCollapsed = false">
            <template #icon>
              <n-icon><ChevronForwardOutline /></n-icon>
            </template>
          </n-button>
        </template>
      </div>
      <template v-if="!sidebarCollapsed">
        <div class="sidebar-section-title">
          <span>历史对话</span>
          <n-button size="tiny" quaternary circle @click="sidebarCollapsed = true">
            <template #icon>
              <n-icon size="14">
                <ChevronBackOutline />
              </n-icon>
            </template>
          </n-button>
        </div>
        <div class="session-list">
          <div
            v-for="session in sessionList"
            :key="session.id"
            class="session-item" :class="[{ active: sessionId === session.id }]"
            @click="loadSession(session.id)"
          >
            <div class="session-title">
              <span v-if="session.metadata && session.metadata.configKey" class="session-config-key">{{ session.metadata.configKey }}</span>
              <span v-else>{{ session.sessionName || '未命名会话' }}</span>
            </div>
            <div class="session-meta">
              <span v-if="session.metadata && session.metadata.tableName" class="session-table">{{ session.metadata.tableName }}</span>
              <span class="session-time">{{ formatTime(session.createTime) }}</span>
            </div>
            <n-button class="delete-btn" size="tiny" text type="error" @click.stop="deleteSession(session.id)">
              <n-icon><CloseOutline /></n-icon>
            </n-button>
          </div>
          <div v-if="sessionList.length === 0" class="empty-tip">
            暂无历史对话
          </div>
        </div>
      </template>
    </div>

    <div class="generator-main">
      <div class="chat-area">
        <!-- 生成阶段进度条 -->
        <div v-if="generating && currentStageIndex >= 0" class="stage-progress">
          <div
            v-for="(stage, idx) in generateStages"
            :key="stage.key"
            class="stage-step" :class="[{
              done: idx < currentStageIndex,
              active: idx === currentStageIndex,
            }]"
          >
            <div class="stage-step-dot" />
            <span class="stage-step-label">{{ stage.label }}</span>
          </div>
        </div>

        <div ref="messageListRef" class="message-list">
          <div v-for="(msg, idx) in messages" :key="idx" class="message" :class="[msg.role]">
            <div class="message-avatar">
              <div v-if="msg.role === 'user'" class="avatar user-avatar">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
              </div>
              <div v-else class="avatar ai-avatar">
                <n-icon size="18">
                  <SparklesOutline />
                </n-icon>
              </div>
            </div>
            <div class="message-body">
              <div v-if="msg.stage" class="stage-indicator">
                <span class="stage-indicator-dot" />
                {{ getStageLabel(msg.stage) }}
              </div>
              <div v-if="msg.reasoning && msg.reasoning.trim()" class="reasoning-section">
                <div class="reasoning-header" @click="toggleReasoning(idx)">
                  <div class="reasoning-header-left">
                    <n-icon size="16" class="reasoning-icon">
                      <SparklesOutline />
                    </n-icon>
                    <span class="reasoning-label">思考过程</span>
                    <span v-if="msg.reasoningTime" class="reasoning-duration">用时 {{ msg.reasoningTime }}s</span>
                    <span v-else-if="msg.isReasoning" class="reasoning-duration thinking">思考中...</span>
                  </div>
                  <n-icon size="16" class="reasoning-toggle">
                    <ChevronDownOutline v-if="!expandedReasonings[idx]" />
                    <ChevronUpOutline v-else />
                  </n-icon>
                </div>
                <div v-if="expandedReasonings[idx]" class="reasoning-content">
                  {{ msg.reasoning }}
                </div>
              </div>
              <div class="message-bubble">
                <div class="message-content">
                  {{ msg.content }}
                </div>
                <div v-if="msg.streaming && msg.isReasoning" class="message-typing">
                  <span /><span /><span />
                </div>
              </div>
            </div>
          </div>
          <div v-if="messages.length === 0" class="empty-chat">
            <div class="empty-chat-hero">
              <div class="empty-chat-icon">
                <n-icon size="32">
                  <SparklesOutline />
                </n-icon>
              </div>
              <div class="empty-chat-title">
                AI 辅助配置
              </div>
              <div class="empty-chat-tip">
                面向技术人员生成和优化页面配置；业务人员优先使用低代码工作台完成可视化搭建和发布
              </div>
              <n-button size="small" type="primary" class="empty-chat-action" @click="router.push('/ai/lowcode-apps')">
                <template #icon>
                  <n-icon><ServerOutline /></n-icon>
                </template>
                进入低代码工作台
              </n-button>
            </div>
            <div class="example-prompts">
              <div class="example-title">
                试试这些示例：
              </div>
              <div class="example-list">
                <div v-for="example in examplePrompts" :key="example.label" class="example-item" @click="fillExample(example)">
                  <div class="example-icon">
                    <n-icon size="14">
                      <AddOutline />
                    </n-icon>
                  </div>
                  <div class="example-text">
                    <span class="example-label">{{ example.label }}</span>
                    <span class="example-desc">{{ example.text }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="input-section">
          <!-- 配置行 -->
          <div class="config-bar">
            <!-- 页面标识 -->
            <div class="config-field" :class="[{ 'has-error': configKeyError }]">
              <span class="field-tag">标识</span>
              <n-input
                v-model:value="configKey"
                placeholder="如 employee_manage"
                size="small"
                :status="configKeyError ? 'error' : undefined"
                class="field-input"
              >
                <template #suffix>
                  <span class="auto-btn" @click="autoGenerateConfigKey">自动</span>
                </template>
              </n-input>
              <span v-if="configKeyError" class="field-error-tip">{{ configKeyError }}</span>
            </div>

            <div class="config-divider" />

            <!-- 关联表 -->
            <div class="config-field">
              <span class="field-tag">关联表</span>
              <n-select
                v-model:value="tableName"
                :options="tableOptions"
                placeholder="选择或搜索"
                clearable
                filterable
                size="small"
                class="field-input"
                @update:value="handleTableSelect"
              />
            </div>

            <div v-if="templateList.length > 0" class="config-divider" />

            <!-- 页面模板 -->
            <div v-if="templateList.length > 0" class="config-field config-field--shrink">
              <span class="field-tag">模板</span>
              <n-select
                v-model:value="layoutType"
                :options="templateList.map(t => ({ label: t.templateName, value: t.templateKey }))"
                size="small"
                class="field-input"
                :consistent-menu-width="false"
              />
            </div>

            <div class="config-divider" />

            <!-- 操作按钮 -->
            <n-button size="small" text type="primary" class="bar-action-btn" @click="showImportModal = true">
              <template #icon>
                <n-icon><AddOutline /></n-icon>
              </template>
              导入表
            </n-button>
          </div>

          <!-- 输入框 -->
          <div class="input-area">
            <n-input
              v-model:value="inputText"
              type="textarea"
              :rows="3"
              placeholder="详细描述你需要的管理页面功能，如：员工管理系统，包含姓名、工号、部门、职位、入职日期、手机号、邮箱、状态等字段"
              style="flex: 1"
              @keydown.enter.exact.prevent="sendMessage"
            />
          </div>

          <!-- 输入底部：模型选择 + 发送按钮 -->
          <div class="input-footer">
            <n-popover
              v-model:show="showModelPanel"
              trigger="click"
              placement="top-start"
              :show-arrow="false"
              :width="320"
              raw
            >
              <template #trigger>
                <button
                  type="button"
                  class="model-trigger" :class="[{ active: showModelPanel, empty: !modelId }]"
                  :title="modelId ? `${currentProviderLabel} · ${currentModelLabel}` : '请选择对话模型'"
                >
                  <n-icon size="16" class="model-trigger-icon">
                    <SparklesOutline />
                  </n-icon>
                  <span class="model-trigger-provider">{{ currentProviderLabel }}</span>
                  <span class="model-trigger-divider">·</span>
                  <span class="model-trigger-model">{{ currentModelLabel }}</span>
                  <n-icon size="14" class="model-trigger-chevron">
                    <ChevronDownOutline />
                  </n-icon>
                </button>
              </template>

              <div class="model-panel">
                <div class="model-panel-section">
                  <div class="model-panel-label">
                    <span>供应商</span>
                    <span v-if="providerOptions.length === 0" class="model-panel-empty-tip">暂无可用供应商</span>
                  </div>
                  <n-select
                    v-model:value="providerId"
                    :options="providerOptions"
                    placeholder="选择供应商"
                    size="small"
                    filterable
                  />
                </div>
                <div class="model-panel-section">
                  <div class="model-panel-label">
                    <span>模型</span>
                    <span v-if="modelOptions.length > 0" class="model-panel-count">{{ modelOptions.length }} 个</span>
                  </div>
                  <div v-if="modelOptions.length === 0" class="model-panel-empty">
                    {{ providerId ? '该供应商暂无可用模型' : '请先选择供应商' }}
                  </div>
                  <div v-else class="model-list">
                    <div
                      v-for="m in modelOptions"
                      :key="m.value"
                      class="model-list-item" :class="[{ active: modelId === m.value }]"
                      @click="modelId = m.value; showModelPanel = false"
                    >
                      <div class="model-list-item-main">
                        <span class="model-list-item-name">{{ m.modelCode || m.label }}</span>
                        <span v-if="m.isDefault === '1'" class="model-tag">默认</span>
                      </div>
                      <div v-if="m.label && m.label !== m.modelCode" class="model-list-item-desc">
                        {{ m.label }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </n-popover>

            <div class="input-buttons">
              <n-button v-if="generating" type="error" @click="abortGenerate">
                <template #icon>
                  <n-icon><CloseOutline /></n-icon>
                </template>
                停止
              </n-button>
              <n-button
                v-else
                type="primary"
                :disabled="!configKey || !inputText.trim() || !modelId"
                @click="sendMessage"
              >
                <template #icon>
                  <n-icon><PaperPlaneOutline /></n-icon>
                </template>
                发送
              </n-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="generator-preview" :class="[{ 'preview-collapsed': previewCollapsed }]">
      <!-- 折叠状态下的竖向标签栏 -->
      <div v-if="previewCollapsed" class="preview-collapsed-bar" @click="previewCollapsed = false">
        <n-icon size="16">
          <ChevronBackOutline />
        </n-icon>
        <span class="preview-collapsed-text">配置面板</span>
      </div>

      <template v-if="!previewCollapsed">
        <div class="preview-header">
          <div class="preview-header-left">
            <n-button size="small" quaternary circle title="折叠配置面板" @click="previewCollapsed = true">
              <template #icon>
                <n-icon><ChevronForwardOutline /></n-icon>
              </template>
            </n-button>
            <span class="preview-title">配置面板</span>
          </div>
          <div class="preview-actions">
            <n-button-group size="small">
              <n-button quaternary title="复制当前配置" @click="copyCurrentFile">
                <template #icon>
                  <n-icon><CopyOutline /></n-icon>
                </template>
              </n-button>
              <n-button quaternary title="导出全部配置" @click="exportAllFiles">
                <template #icon>
                  <n-icon><DownloadOutline /></n-icon>
                </template>
              </n-button>
            </n-button-group>
            <n-button type="primary" size="small" @click="openSaveModal">
              <template #icon>
                <n-icon><SaveOutline /></n-icon>
              </template>
              保存
            </n-button>
            <n-dropdown
              :options="moreActionOptions"
              trigger="click"
              @select="handleMoreAction"
            >
              <n-button size="small" :disabled="!configSaved">
                更多 ▾
              </n-button>
            </n-dropdown>
          </div>
        </div>

        <!-- Tab 分组：核心配置 / 高级配置 -->
        <div class="preview-tab-group">
          <div class="tab-group-switcher">
            <span
              class="group-tab" :class="[{ active: activeTabGroup === 'core' }]"
              @click="switchTabGroup('core')"
            >核心配置</span>
            <span
              class="group-tab" :class="[{ active: activeTabGroup === 'advanced' }]"
              @click="switchTabGroup('advanced')"
            >高级配置</span>
            <span
              class="group-tab" :class="[{ active: activeTabGroup === 'sql' }]"
              @click="switchTabGroup('sql')"
            >SQL / 表结构</span>
          </div>
        </div>

        <div class="preview-tabs">
          <!-- 核心配置 -->
          <template v-if="activeTabGroup === 'core'">
            <n-tabs v-model:value="activeFile" type="line" animated :tabs-padding="12">
              <n-tab-pane name="searchSchema" tab="搜索配置">
                <div class="editor-area">
                  <SchemaFieldEditor
                    mode="search"
                    :value="displayContent.searchSchema"
                    :table-structure="displayContent.tableStructure"
                    @update:value="displayContent.searchSchema = $event"
                  />
                </div>
              </n-tab-pane>
              <n-tab-pane name="columnsSchema" tab="表格列">
                <div class="editor-area">
                  <SchemaFieldEditor
                    mode="columns"
                    :value="displayContent.columnsSchema"
                    :table-structure="displayContent.tableStructure"
                    @update:value="displayContent.columnsSchema = $event"
                  />
                </div>
              </n-tab-pane>
              <n-tab-pane name="editSchema" tab="编辑表单">
                <div class="editor-area">
                  <SchemaFieldEditor
                    mode="edit"
                    :value="displayContent.editSchema"
                    :table-structure="displayContent.tableStructure"
                    @update:value="displayContent.editSchema = $event"
                  />
                </div>
              </n-tab-pane>
              <n-tab-pane name="apiConfig" tab="接口配置">
                <div class="editor-area">
                  <ApiConfigEditor
                    :value="displayContent.apiConfig"
                    @update:value="displayContent.apiConfig = $event"
                  />
                </div>
              </n-tab-pane>
            </n-tabs>
          </template>

          <!-- 高级配置 -->
          <template v-if="activeTabGroup === 'advanced'">
            <n-tabs v-model:value="activeFile" type="line" animated :tabs-padding="12">
              <n-tab-pane name="dictConfig" tab="字典">
                <div class="editor-area">
                  <DictConfigPanel
                    :search-schema="displayContent.searchSchema"
                    :columns-schema="displayContent.columnsSchema"
                    :edit-schema="displayContent.editSchema"
                    :dict-config="displayContent.dictConfig"
                    @update:value="displayContent.dictConfig = $event"
                  />
                </div>
              </n-tab-pane>
              <n-tab-pane name="desensitizeConfig" tab="脱敏">
                <div class="editor-area">
                  <DesensitizeConfigPanel
                    :value="displayContent.desensitizeConfig"
                    :columns-schema="displayContent.columnsSchema"
                    @update:value="displayContent.desensitizeConfig = $event"
                  />
                </div>
              </n-tab-pane>
              <n-tab-pane name="encryptConfig" tab="加解密">
                <div class="editor-area">
                  <EncryptConfigPanel
                    :value="displayContent.encryptConfig"
                    @update:value="displayContent.encryptConfig = $event"
                  />
                </div>
              </n-tab-pane>
              <n-tab-pane name="transConfig" tab="翻译">
                <div class="editor-area">
                  <TransConfigPanel
                    :value="displayContent.transConfig"
                    :columns-schema="displayContent.columnsSchema"
                    :edit-schema="displayContent.editSchema"
                    @update:value="displayContent.transConfig = $event"
                  />
                </div>
              </n-tab-pane>
            </n-tabs>
          </template>

          <!-- SQL / 表结构 -->
          <template v-if="activeTabGroup === 'sql'">
            <n-tabs v-model:value="activeFile" type="line" animated :tabs-padding="12">
              <n-tab-pane name="createTableSql" tab="建表 SQL">
                <div class="editor-area sql-editor-wrap">
                  <textarea
                    v-model="currentFileContent"
                    class="json-editor"
                    :placeholder="generating ? '正在生成...' : '等待生成...'"
                    spellcheck="false"
                  />
                  <div class="sql-actions">
                    <n-button
                      type="warning"
                      size="small"
                      :loading="executingSql"
                      :disabled="!currentFileContent"
                      @click="executeCreateTableSql"
                    >
                      执行建表
                    </n-button>
                  </div>
                </div>
              </n-tab-pane>
              <n-tab-pane name="tableStructure" tab="表结构">
                <div class="editor-area">
                  <textarea
                    v-model="currentFileContent"
                    class="json-editor"
                    :placeholder="generating ? '正在生成...' : '等待生成...'"
                    spellcheck="false"
                  />
                </div>
              </n-tab-pane>
            </n-tabs>
          </template>

          <div v-if="generating && currentFileContent" class="typing-indicator">
            正在写入...
          </div>
        </div>
      </template>
    </div>
  </div>
  <ImportDbTableModal v-model:show="showImportModal" @success="handleImportSuccess" />
  <MenuTreeSelectModal
    v-model:show="showMenuModal"
    @confirm="handleMenuConfirm"
  />
</template>

<script>
import { crudGeneratorLocalComponents } from './crudGeneratorLocalComponents'
import { useCrudGenerator } from './composables/useCrudGenerator'

export default {
  name: 'CrudGenerator',
  components: {
    ...crudGeneratorLocalComponents,
  },
  setup() {
    return useCrudGenerator()
  },
}
</script>

<style scoped src="./crudGenerator.css"></style>
