<template>
  <div class="lowcode-domain-page">
    <DomainTreePanel
      v-model:keyword="domainKeyword"
      :domains="domains"
      :selected-domain-id="selectedDomainId"
      :loading="domainLoading"
      @select="selectDomain"
      @search="loadDomains"
      @refresh="refreshDomains"
      @create="openDomainEditor(null)"
      @create-child="openChildDomainEditor"
      @edit="editDomain"
      @delete="deleteDomain"
    />

    <main class="domain-main">
      <div class="main-toolbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageSubtitle }}</p>
        </div>
        <n-space>
          <n-button
            @click="openAiCreateApp"
          >
            <template #icon>
              <n-icon><SparklesOutline /></n-icon>
            </template>
            AI 智能开发
          </n-button>
          <n-button @click="router.push('/ai/lowcode-models')">
            数据模型设计
          </n-button>
          <n-button @click="triggerAppImport">
            导入配置
          </n-button>
          <n-button @click="refreshAll">
            刷新
          </n-button>
          <n-button type="primary" :disabled="selectedDomain?.status === 'DISABLED'" @click="createApp(selectedDomain)">
            新建应用
          </n-button>
        </n-space>
      </div>

      <section class="apps-board">
        <div class="apps-board-head">
          <div>
            <h2>低代码应用</h2>
            <p>{{ appScopeText }}</p>
          </div>
          <div class="filter-strip">
            <n-input
              v-model:value="keyword"
              clearable
              placeholder="搜索应用 / 表名 / configKey"
              @update:value="handleKeywordSearch"
            />
            <n-select
              v-model:value="publishStatus"
              clearable
              placeholder="发布状态"
              :options="statusOptions"
              @update:value="handleSearch"
            />
          </div>
        </div>

        <n-spin :show="loading">
          <div v-if="apps.length" class="apps-grid">
            <article v-for="app in apps" :key="app.id" class="app-card">
              <div class="app-topline">
                <n-tag size="small" :type="app.publishStatus === 'PUBLISHED' ? 'success' : 'warning'" :bordered="false">
                  {{ statusLabel(app.publishStatus) }}
                </n-tag>
                <span>{{ formatTime(app.updateTime) }}</span>
              </div>
              <div class="app-title-row">
                <div class="app-mark">
                  {{ appInitial(app) }}
                </div>
                <div>
                  <h3>{{ app.appName || app.tableComment || app.configKey }}</h3>
                  <p>{{ app.objectName || app.tableComment || '未命名对象' }}</p>
                </div>
              </div>
              <div class="app-code-grid">
                <div>
                  <span>领域</span>
                  <code>{{ app.domainName || app.domainCode || '-' }}</code>
                </div>
                <div>
                  <span>对象编码</span>
                  <code>{{ app.objectCode || '-' }}</code>
                </div>
                <div>
                  <span>配置键</span>
                  <code>{{ app.configKey }}</code>
                </div>
                <div>
                  <span>数据表</span>
                  <code>{{ app.tableName || '-' }}</code>
                </div>
              </div>
              <div class="app-version-row">
                <span>草稿 v{{ app.draftVersion || 0 }}</span>
                <span>发布 v{{ app.publishedVersion || 0 }}</span>
              </div>
              <div class="app-actions">
                <n-button size="small" type="primary" @click="openBuilder(app.id)">
                  搭建
                </n-button>
                <n-button size="small" :disabled="app.publishStatus !== 'PUBLISHED'" @click="openRuntime(app.configKey)">
                  打开
                </n-button>
                <n-dropdown
                  trigger="click"
                  :options="getAppActionOptions(app)"
                  @select="key => handleAppActionSelect(key, app)"
                >
                  <n-button size="small" class="more-action-button">
                    <template #icon>
                      <n-icon><EllipsisVertical /></n-icon>
                    </template>
                  </n-button>
                </n-dropdown>
              </div>
            </article>
          </div>
          <n-empty v-else-if="!loading" description="当前筛选下暂无低代码应用">
            <template #extra>
              <n-button type="primary" :disabled="selectedDomain?.status === 'DISABLED'" @click="createApp(selectedDomain)">
                新建应用
              </n-button>
            </template>
          </n-empty>
        </n-spin>

        <div v-if="total > 0" class="apps-pagination">
          <n-pagination
            v-model:page="pageNum"
            v-model:page-size="pageSize"
            :item-count="total"
            :page-sizes="[9, 18, 36]"
            show-size-picker
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </section>
    </main>

    <DomainEditorDrawer
      v-model:show="domainEditorVisible"
      :domain="editingDomain"
      :domains="domains"
      @saved="handleDomainSaved"
    />

    <MoveDomainModal
      v-model:show="moveVisible"
      :app="movingApp"
      :domains="domains"
      @moved="handleMoved"
    />
    <LowcodeCodePreviewModal
      v-model:show="codePreviewVisible"
      :app-id="codePreviewApp?.id"
      :app-name="codePreviewApp?.appName || codePreviewApp?.configKey"
    />
    <n-modal
      v-model:show="aiCreateVisible"
      class="lc-agent-modal-card"
      title="AI 建模向导"
      preset="card"
      style="width: min(1280px, calc(100vw - 24px))"
      :mask-closable="false"
      @update:show="handleAiCreateVisibleUpdate"
    >
      <div class="lc-agent-modal">
        <header class="lc-agent-topbar">
          <div class="lc-agent-brand">
            <span class="lc-agent-logo">AI</span>
            <div>
              <strong>AI 建模向导</strong>
              <span>从需求到应用，一键生成</span>
            </div>
          </div>
          <div class="lc-agent-top-actions">
            <n-button size="small" class="lc-save-indicator">
              <template #icon>
                <n-icon><SaveOutline /></n-icon>
              </template>
              用户确认后保存
            </n-button>
            <n-button size="small" quaternary class="lc-agent-ghost-action" @click="closeAiCreateModal">
              关闭
            </n-button>
          </div>
        </header>

        <section class="lc-agent-input">
          <div class="lc-agent-input-head">
            <div>
              <strong>业务需求</strong>
              <span>描述业务范围、对象、字段、流程、查询条件和页面预期</span>
            </div>
            <div class="lc-agent-chip-row">
              <n-button size="tiny" class="lc-helper-button">
                智能提示词示例
              </n-button>
              <n-button size="tiny" class="lc-helper-button">
                自动识别字段 / 补全需求
              </n-button>
            </div>
          </div>

          <div class="lc-agent-input-grid">
            <n-form class="lc-agent-form" label-placement="top" size="small" :show-feedback="false">
              <n-form-item label="供应商">
                <n-select
                  v-model:value="aiProviderId"
                  clearable
                  filterable
                  :loading="aiProviderLoading"
                  :options="aiProviderOptions"
                  placeholder="使用 Agent 默认供应商"
                  @update:value="handleAiProviderChange"
                />
              </n-form-item>

              <n-form-item label="模型">
                <n-select
                  v-model:value="aiModelId"
                  clearable
                  filterable
                  :loading="aiModelLoading"
                  :options="aiModelOptions"
                  placeholder="使用供应商默认模型"
                />
              </n-form-item>

              <n-form-item label="目标业务领域">
                <n-select
                  v-model:value="aiTargetDomainId"
                  clearable
                  filterable
                  :options="domainSelectOptions"
                  placeholder="不选择则由 AI 自动划分业务领域"
                  @update:value="handleAiTargetDomainChange"
                />
              </n-form-item>

              <n-form-item v-if="aiTargetDomainId" label="已有模型上下文">
                <div class="lc-domain-model-context">
                  <n-checkbox v-model:checked="aiIncludeDomainModels" :disabled="domainModelLoading">
                    携带当前领域已有数据模型
                  </n-checkbox>
                  <n-tag size="small" :bordered="false" :type="domainModelContext.length ? 'success' : 'default'">
                    {{ domainModelLoading ? '加载中' : `${domainModelContext.length} 个模型` }}
                  </n-tag>
                </div>
              </n-form-item>
            </n-form>

            <div class="lc-demand-box">
              <div class="lc-demand-head">
                <strong>需求描述</strong>
                <span>{{ aiTargetDomain ? `优先复用“${aiTargetDomain.domainName}”` : 'AI 自动划分业务领域' }}</span>
              </div>
              <n-input
                v-model:value="aiCreateDescription"
                type="textarea"
                :autosize="{ minRows: 5, maxRows: 8 }"
                maxlength="2000"
                show-count
                placeholder="例如：帮我做一个客户合同回款管理系统，需要客户档案、合同、回款记录，支持按客户、状态、日期查询，合同有金额和签订日期，回款记录需要状态跟踪。"
              />
              <div class="lc-agent-command-text">
                {{ aiTargetDomain ? `将优先在已有领域“${aiTargetDomain.domainName}”中生成模型和应用` : '未指定目标领域，AI 会自动划分或复用业务领域' }}
              </div>
            </div>

            <n-button class="lc-agent-start-card" type="primary" :loading="aiCreateLoading" @click="generateAiAppDraft(false)">
              <div class="lc-agent-start-content">
                <span class="lc-agent-start-icon">AI</span>
                <strong>开始生成</strong>
                <span>一键生成应用</span>
              </div>
            </n-button>
          </div>
        </section>

        <section class="lc-agent-workspace">
          <aside class="lc-step-rail">
            <div class="lc-step-rail-head">
              <strong>生成步骤</strong>
              <span>全流程闭环</span>
            </div>
            <div
              v-for="step in aiSteps"
              :key="step.stepKey"
              class="lc-step-item"
              :class="`is-${step.status || 'pending'}`"
            >
              <span class="lc-step-dot">{{ step.orderNo }}</span>
              <div>
                <strong>{{ step.title }}</strong>
                <span>{{ step.message }}</span>
                <small>{{ stepStatusText(step.status) }}</small>
              </div>
            </div>
          </aside>

          <div class="lc-agent-output">
            <div class="lc-output-head">
              <div>
                <strong>生成预览 / 对话流</strong>
                <span>{{ aiCreateLoading ? '模型正在流式生成协议' : aiCreateResult ? '草稿已生成，等待确认保存' : '等待输入业务需求' }}</span>
              </div>
              <n-tag size="small" :bordered="false" :type="aiCreateResult ? 'success' : aiCreateLoading ? 'warning' : 'info'">
                {{ aiCreateResult ? '已生成' : aiCreateLoading ? '生成中' : '待开始' }}
              </n-tag>
            </div>

            <div v-if="!aiCreateResult && !aiCreateLoading && !aiRawContent && !aiReasoningContent" class="lc-agent-welcome">
              <div class="lc-welcome-copy">
                <h3>欢迎使用 AI 建模向导</h3>
                <p>填写并提交业务需求后，AI 将自动完成从需求理解到应用草稿的基础生成。</p>
              </div>
              <div class="lc-capability-grid">
                <div>
                  <span class="lc-capability-icon">理</span>
                  <strong>智能理解</strong>
                  <p>AI 深度理解业务需求，提炼核心要素</p>
                </div>
                <div>
                  <span class="lc-capability-icon">模</span>
                  <strong>自动建模</strong>
                  <p>自动生成数据模型，建立实体关系</p>
                </div>
                <div>
                  <span class="lc-capability-icon">页</span>
                  <strong>页面生成</strong>
                  <p>一键生成应用页面，支持交互预览</p>
                </div>
                <div>
                  <span class="lc-capability-icon">验</span>
                  <strong>协议校验</strong>
                  <p>自动校验低代码协议，确保规范可落地</p>
                </div>
              </div>
            </div>

            <div v-if="aiCreateLoading || aiRawContent || aiReasoningContent" class="lc-stream-panel">
              <div class="lc-stream-section">
                <div class="lc-stream-head">
                  <div>
                    <strong>模型思考过程</strong>
                    <span v-if="aiReasoningTime">用时 {{ aiReasoningTime }}s</span>
                    <span v-else-if="aiIsReasoningPhase">思考中...</span>
                    <span v-else>跟随模型流式输出</span>
                  </div>
                  <n-tag size="small" :bordered="false" :type="aiIsReasoningPhase ? 'warning' : 'default'">
                    {{ aiIsReasoningPhase ? '推理中' : '实时' }}
                  </n-tag>
                </div>
                <pre v-if="aiReasoningContent" class="lc-stream-content">{{ aiReasoningContent }}</pre>
                <div v-else class="lc-stream-empty">
                  {{ aiCreateLoading ? '等待模型返回推理内容；如果当前模型不支持独立推理流，将直接展示结构化输出。' : '当前模型未返回独立推理内容。' }}
                </div>
              </div>

              <div class="lc-stream-section">
                <div class="lc-stream-head">
                  <div>
                    <strong>结构化输出</strong>
                    <span>JSON 协议流会在结束后自动校验并转为草稿预览</span>
                  </div>
                  <n-tag size="small" :bordered="false" type="info">
                    {{ aiRawContent ? '接收中' : '等待' }}
                  </n-tag>
                </div>
                <pre v-if="aiRawContent" class="lc-stream-content">{{ aiRawContent }}</pre>
                <div v-else class="lc-stream-empty">
                  等待模型开始输出领域、模型和应用协议。
                </div>
              </div>
            </div>

            <template v-if="aiCreateResult">
              <div class="lc-result-head">
                <div>
                  <strong>{{ aiCreateResult.requirementSummary || aiCreateResult.appDraft?.appName || '业务系统草稿' }}</strong>
                  <span>确认后将保存 {{ aiGeneratedDomains.length }} 个领域、{{ aiGeneratedModels.length }} 个模型、{{ aiGeneratedApps.length }} 个应用草稿</span>
                </div>
                <n-tag :bordered="false" :type="aiCreateResult.fallback ? 'warning' : 'success'">
                  {{ aiCreateResult.fallback ? '规则 Agent' : 'AI Agent' }}
                </n-tag>
              </div>

              <div class="lc-summary-grid">
                <div>
                  <span>业务领域</span>
                  <strong>{{ aiGeneratedDomains.length }}</strong>
                </div>
                <div>
                  <span>数据模型</span>
                  <strong>{{ aiGeneratedModels.length }}</strong>
                </div>
                <div>
                  <span>应用页面</span>
                  <strong>{{ aiGeneratedApps.length }}</strong>
                </div>
              </div>

              <div class="lc-result-columns">
                <section class="lc-result-section">
                  <h3>业务领域</h3>
                  <div class="lc-mini-list">
                    <div v-for="domain in aiGeneratedDomains" :key="domain.domainCode" class="lc-mini-row">
                      <div>
                        <strong>{{ domain.domainName }}</strong>
                        <span>{{ domain.domainCode }}</span>
                      </div>
                      <n-tag size="small" :bordered="false" :type="domain.existingDomainId ? 'success' : 'info'">
                        {{ domain.existingDomainId ? '复用' : '新建' }}
                      </n-tag>
                    </div>
                  </div>
                </section>

                <section class="lc-result-section">
                  <h3>数据模型</h3>
                  <div class="lc-mini-list">
                    <div v-for="model in aiGeneratedModels" :key="model.modelCode" class="lc-mini-row">
                      <div>
                        <strong>{{ model.modelName }}</strong>
                        <span>{{ model.modelCode }} · {{ model.modelSchema?.fields?.length || 0 }} 字段</span>
                      </div>
                      <n-button size="tiny" @click="activePreviewModelCode = model.modelCode">
                        预览
                      </n-button>
                    </div>
                  </div>
                </section>

                <section class="lc-result-section">
                  <h3>应用草稿</h3>
                  <div class="lc-mini-list">
                    <div v-for="app in aiGeneratedApps" :key="app.configKey" class="lc-mini-row">
                      <div>
                        <strong>{{ app.appName }}</strong>
                        <span>{{ app.configKey }} · {{ layoutName(app.pageSchema?.layoutType) }}</span>
                      </div>
                    </div>
                  </div>
                </section>
              </div>

              <section v-if="activePreviewModel" class="lc-result-section">
                <h3>表模型预览</h3>
                <div class="lc-model-preview-head">
                  <n-select
                    v-model:value="activePreviewModelCode"
                    size="small"
                    :options="previewModelOptions"
                    placeholder="选择模型"
                  />
                  <n-tag size="small" :bordered="false" type="info">
                    {{ activePreviewModel.modelSchema?.tableName || '-' }}
                  </n-tag>
                </div>
                <n-data-table
                  size="small"
                  :bordered="false"
                  :single-line="false"
                  :columns="modelPreviewColumns"
                  :data="activePreviewFields"
                  :pagination="{ pageSize: 8 }"
                />
              </section>

              <section class="lc-result-section">
                <h3>追加优化</h3>
                <div class="lc-refine-box">
                  <n-input
                    v-model:value="aiAppendInstruction"
                    type="textarea"
                    :autosize="{ minRows: 2, maxRows: 4 }"
                    placeholder="基于上面的草稿继续补充，例如：商品分类和商品基本信息放在同一个领域，表名统一以 tf_f_ 开头，商品页面用分类树过滤。"
                  />
                  <n-button :loading="aiCreateLoading" :disabled="!aiCreateResult" @click="generateAiAppDraft(true)">
                    继续优化
                  </n-button>
                </div>
              </section>

              <section v-if="aiDecisions.length" class="lc-result-section">
                <h3>决策摘要</h3>
                <div class="lc-decision-list">
                  <div v-for="decision in aiDecisions" :key="`${decision.decisionType}-${decision.target}-${decision.value}`" class="lc-decision-row">
                    <div>
                      <strong>{{ decision.title }}</strong>
                      <span>{{ decision.target }}：{{ decision.value }}</span>
                    </div>
                    <p>{{ decision.reason }}</p>
                  </div>
                </div>
              </section>

              <ul v-if="aiNotes.length" class="note-list">
                <li v-for="note in aiNotes" :key="note">
                  {{ note }}
                </li>
              </ul>

              <div class="lc-preview-board">
                <article class="lc-preview-card lc-er-preview">
                  <div class="lc-preview-card-head">
                    <div>
                      <strong>ER 图（数据模型）</strong>
                      <span>{{ aiGeneratedModels.length }} 个模型 · {{ aiRelationCount }} 条关系</span>
                    </div>
                  </div>
                  <div v-if="aiGeneratedModels.length" class="lc-er-mini">
                    <span
                      v-for="(model, index) in aiGeneratedModels.slice(0, 3)"
                      :key="model.modelCode || model.modelName || index"
                      :class="{ 'is-primary': index === 0 }"
                    >
                      {{ model.modelName || model.modelCode }}
                    </span>
                    <i v-if="aiGeneratedModels.length > 1" />
                  </div>
                  <div v-else class="lc-preview-empty">
                    暂无生成模型
                  </div>
                  <n-button
                    size="tiny"
                    class="lc-preview-button"
                    :disabled="!aiGeneratedModels.length"
                    @click="openAiPreviewDetail('er')"
                  >
                    查看详情
                  </n-button>
                </article>

                <article class="lc-preview-card">
                  <div class="lc-preview-card-head">
                    <div>
                      <strong>页面结构</strong>
                      <span>{{ aiGeneratedApps.length }} 个页面草稿</span>
                    </div>
                  </div>
                  <div v-if="aiPageStructureRows.length" class="lc-page-structure-list">
                    <div v-for="item in aiPageStructureRows" :key="item.key">
                      <span>{{ item.name }}</span>
                      <small>{{ item.layout }}</small>
                    </div>
                  </div>
                  <div v-else class="lc-preview-empty">
                    暂无生成页面
                  </div>
                  <n-button
                    size="tiny"
                    class="lc-preview-button"
                    :disabled="!aiGeneratedApps.length"
                    @click="openAiPreviewDetail('pages')"
                  >
                    查看详情
                  </n-button>
                </article>

                <article class="lc-preview-card">
                  <div class="lc-preview-card-head">
                    <div>
                      <strong>字段清单（核心实体）</strong>
                      <span>{{ activePreviewModel?.modelName || aiPrimaryModelName }}</span>
                    </div>
                  </div>
                  <div v-if="aiPreviewFields.length" class="lc-field-mini-table">
                    <div v-for="field in aiPreviewFields" :key="field.field">
                      <span>{{ field.label || field.field }}</span>
                      <small>{{ field.dataType || '-' }}</small>
                      <em>{{ field.componentType || '-' }}</em>
                    </div>
                  </div>
                  <div v-else class="lc-preview-empty">
                    暂无字段清单
                  </div>
                  <n-button
                    size="tiny"
                    class="lc-preview-button"
                    :disabled="!activePreviewModel"
                    @click="openAiPreviewDetail('fields')"
                  >
                    查看详情
                  </n-button>
                </article>

                <article class="lc-preview-card">
                  <div class="lc-preview-card-head">
                    <div>
                      <strong>接口建议</strong>
                      <span>{{ aiApiCount }} 个接口建议</span>
                    </div>
                  </div>
                  <div v-if="aiApiPreviewRows.length" class="lc-api-mini-list">
                    <div v-for="api in aiApiPreviewRows" :key="`${api.method}-${api.path}`">
                      <b :class="`is-${api.method.toLowerCase()}`">{{ api.method }}</b>
                      <span>{{ api.path }}</span>
                    </div>
                  </div>
                  <div v-else class="lc-preview-empty">
                    暂无接口建议
                  </div>
                  <n-button
                    size="tiny"
                    class="lc-preview-button"
                    :disabled="!aiApiPreviewRows.length"
                    @click="openAiPreviewDetail('apis')"
                  >
                    查看详情
                  </n-button>
                </article>
              </div>
            </template>
          </div>
        </section>

        <section class="lc-agent-summary-panel">
          <div class="lc-agent-summary-head">
            <strong>生成结果摘要</strong>
            <span>AI 只生成草稿，确认后保存</span>
          </div>
          <div class="lc-agent-summary-metrics">
            <div>
              <span class="lc-summary-icon">域</span>
              <small>业务领域</small>
              <strong>{{ aiPrimaryDomainLabel }}</strong>
            </div>
            <div>
              <span class="lc-summary-icon">体</span>
              <small>核心实体</small>
              <strong>{{ aiGeneratedModels.length || 0 }} 个</strong>
            </div>
            <div>
              <span class="lc-summary-icon">关</span>
              <small>关联关系</small>
              <strong>{{ aiRelationCount }} 条</strong>
            </div>
            <div>
              <span class="lc-summary-icon">页</span>
              <small>应用页面</small>
              <strong>{{ aiGeneratedApps.length || 0 }} 个</strong>
            </div>
            <div>
              <span class="lc-summary-icon">接</span>
              <small>接口数量</small>
              <strong>{{ aiApiCount }} 个</strong>
            </div>
            <div>
              <span class="lc-summary-icon">验</span>
              <small>校验状态</small>
              <strong>{{ aiValidationStatus }}</strong>
            </div>
            <div class="is-progress-metric">
              <span class="lc-summary-progress">{{ aiCompletionPercent }}</span>
              <small>完成进度</small>
              <strong>{{ aiCreateResult ? '可保存' : aiCreateLoading ? '生成中' : '等待开始生成' }}</strong>
            </div>
          </div>
        </section>
      </div>
      <template #footer>
        <n-space class="lc-agent-footer-actions" justify="end">
          <n-button class="lc-agent-secondary-button" @click="closeAiCreateModal">
            取消
          </n-button>
          <n-button
            class="lc-agent-confirm-button"
            type="primary"
            :loading="aiConfirmSaving"
            :disabled="!aiCreateResult || aiCreateLoading"
            @click="confirmAiAppDraft"
          >
            确认并保存草稿
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="aiPreviewDetailVisible"
      preset="card"
      class="lc-ai-preview-detail-modal"
      :title="aiPreviewDetailTitle"
      style="width: min(960px, calc(100vw - 32px))"
    >
      <div class="lc-preview-detail">
        <template v-if="aiPreviewDetailType === 'er'">
          <div class="lc-detail-model-grid">
            <article v-for="model in aiGeneratedModels" :key="model.modelCode || model.modelName" class="lc-detail-model-card">
              <div>
                <strong>{{ model.modelName || model.modelCode }}</strong>
                <span>{{ model.modelCode }} · {{ model.modelSchema?.tableName || '-' }}</span>
              </div>
              <n-tag size="small" :bordered="false" type="info">
                {{ model.modelSchema?.fields?.length || 0 }} 字段
              </n-tag>
            </article>
          </div>
          <section class="lc-detail-section">
            <h3>关联关系</h3>
            <div v-if="aiModelRelationRows.length" class="lc-detail-relation-list">
              <div v-for="relation in aiModelRelationRows" :key="relation.key">
                <strong>{{ relation.source }}</strong>
                <span>{{ relation.type }}</span>
                <strong>{{ relation.target }}</strong>
              </div>
            </div>
            <n-empty v-else description="当前生成结果暂无显式关联关系" />
          </section>
        </template>

        <template v-else-if="aiPreviewDetailType === 'pages'">
          <n-data-table
            size="small"
            :bordered="false"
            :single-line="false"
            :columns="appPreviewColumns"
            :data="aiPageDetailRows"
            :pagination="{ pageSize: 8 }"
          />
        </template>

        <template v-else-if="aiPreviewDetailType === 'fields'">
          <div class="lc-model-preview-head">
            <n-select
              v-model:value="activePreviewModelCode"
              size="small"
              :options="previewModelOptions"
              placeholder="选择模型"
            />
            <n-tag size="small" :bordered="false" type="info">
              {{ activePreviewModel?.modelSchema?.tableName || '-' }}
            </n-tag>
          </div>
          <n-data-table
            size="small"
            :bordered="false"
            :single-line="false"
            :columns="modelPreviewColumns"
            :data="activePreviewFields"
            :pagination="{ pageSize: 10 }"
          />
        </template>

        <template v-else-if="aiPreviewDetailType === 'apis'">
          <n-data-table
            size="small"
            :bordered="false"
            :single-line="false"
            :columns="apiPreviewColumns"
            :data="aiApiDetailRows"
            :pagination="{ pageSize: 10 }"
          />
        </template>
      </div>
    </n-modal>

    <input
      ref="appImportInputRef"
      class="hidden-file-input"
      type="file"
      accept="application/json,.json"
      @change="handleAppImportFile"
    >
  </div>
</template>

<script>
import { lowcodeAppsLocalComponents } from './lowcodeAppsLocalComponents'
import { useLowcodeApps } from './composables/useLowcodeApps'

export default {
  name: 'AiLowcodeApps',
  name: 'LowcodeAppsPage',
  components: {
    ...lowcodeAppsLocalComponents,
  },
  setup() {
    return useLowcodeApps()
  },
}
</script>

<style scoped src="./lowcodeApps-shell.css"></style>
<style scoped src="./lowcodeApps-panels.css"></style>
