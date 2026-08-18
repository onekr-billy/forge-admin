<template>
  <div class="ai-knowledge-page">
    <div class="kb-layout">
      <!-- 左侧：知识库列表 -->
      <aside class="kb-list-panel">
        <div class="kb-list-panel__header">
          <h2>知识库</h2>
          <NButton
            type="primary"
            circle
            size="small"
            aria-label="新增知识库"
            title="新增知识库"
            @click="handleAdd"
          >
            <template #icon>
              <i class="ai-icon:plus" aria-hidden="true" />
            </template>
          </NButton>
        </div>
        <div class="kb-list-filters">
          <n-input
            v-model:value="search.name"
            placeholder="搜索知识库名称"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <i class="ai-icon:search" aria-hidden="true" />
            </template>
          </n-input>
          <div class="kb-list-filters__actions">
            <NButton type="primary" size="small" @click="handleSearch">
              查询
            </NButton>
            <NButton size="small" @click="handleReset">
              重置
            </NButton>
          </div>
        </div>
        <n-spin :show="loading">
          <div class="kb-list">
            <button
              v-for="kb in kbList"
              :key="kb.id"
              type="button"
              class="kb-list-item"
              :class="{ 'kb-list-item--selected': selectedKb?.id === kb.id }"
              :aria-pressed="selectedKb?.id === kb.id"
              @click="handleSelect(kb)"
            >
              <span class="kb-list-item__icon">
                <i v-if="kb.icon" :class="`ai-icon:${kb.icon}`" />
                <i v-else class="ai-icon:apps" />
              </span>
              <span class="kb-list-item__content">
                <span class="kb-list-item__title">
                  <strong>{{ kb.knowledgeName }}</strong>
                  <DictTag dict-type="ai_status" :value="kb.status" size="small" />
                </span>
                <span class="kb-list-item__desc">{{ kb.description || '暂无描述' }}</span>
              </span>
            </button>
            <n-empty v-if="!loading && kbList.length === 0" description="暂无知识库" size="small" />
          </div>
        </n-spin>
        <div class="kb-list-pagination">
          <span>共 {{ pagination.itemCount }} 条</span>
          <n-pagination
            :page="pagination.pageNum"
            :page-size="pagination.pageSize"
            :item-count="pagination.itemCount"
            :page-sizes="pageSizes"
            show-size-picker
            size="small"
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </aside>

      <!-- 右侧：文档管理 -->
      <section class="kb-detail-panel">
        <template v-if="selectedKb">
          <div class="kb-detail-header">
            <div class="kb-detail-header__identity">
              <span class="kb-detail-header__icon">
                <i v-if="selectedKb.icon" :class="`ai-icon:${selectedKb.icon}`" />
                <i v-else class="ai-icon:apps" />
              </span>
              <div>
                <div class="kb-detail-header__title">
                  <h2>{{ selectedKb.knowledgeName }}</h2>
                  <DictTag dict-type="ai_status" :value="selectedKb.status" size="small" />
                </div>
                <p class="kb-detail-header__desc">
                  {{ selectedKb.description || '暂无描述' }}
                </p>
              </div>
            </div>
            <div class="kb-detail-header__actions">
              <NButton secondary @click="handleEdit(selectedKb)">编辑</NButton>
              <NButton secondary @click="openSearchDebug">检索调试</NButton>
              <NPopconfirm @positive-click="handleDelete(selectedKb.id)">
                <template #trigger>
                  <NButton text class="text-error">
                    删除
                  </NButton>
                </template>
                确定删除知识库“{{ selectedKb.knowledgeName }}”吗？该操作将删除其下所有文档。
              </NPopconfirm>
            </div>
          </div>

          <div class="kb-doc-toolbar">
            <div class="kb-doc-toolbar__left">
              <strong>文档管理</strong>
              <n-select
                v-model:value="docSearch.processStatus"
                placeholder="全部状态"
                clearable
                :options="processStatusOptions"
                size="small"
                style="width: 130px"
                @update:value="handleDocSearch"
              />
            </div>
            <div class="kb-doc-toolbar__actions">
              <n-upload
                :action="`${uploadPrefix}/api/file/upload`"
                :headers="uploadHeaders"
                :max="5"
                :default-upload="true"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.md,.markdown,.txt,.html,.htm"
                @finish="handleUploadFinish"
                @error="handleUploadError"
              >
                <NButton type="primary">
                  <template #icon>
                    <i class="ai-icon:upload" />
                  </template>
                  上传文档
                </NButton>
              </n-upload>
            </div>
          </div>

          <n-data-table
            :columns="docColumns"
            :data="docList"
            :loading="docLoading"
            :row-key="row => row.id"
            :scroll-x="1000"
            size="small"
            class="kb-doc-table"
          />
          <div class="kb-doc-pagination">
            <n-pagination
              :page="docPagination.pageNum"
              :page-size="docPagination.pageSize"
              :item-count="docPagination.itemCount"
              :page-sizes="pageSizes"
              show-size-picker
              show-quick-jumper
              size="small"
              @update:page="handleDocPageChange"
              @update:page-size="handleDocPageSizeChange"
            />
          </div>
        </template>

        <div v-else class="kb-detail-empty">
          <i class="ai-icon:apps" aria-hidden="true" />
          <h2>请选择知识库</h2>
          <p>从左侧选择一个知识库，查看和管理其下的文档。</p>
        </div>
      </section>
    </div>

    <!-- 新建/编辑知识库 · 右侧抽屉 -->
    <n-drawer
      v-model:show="kbModal.show"
      :width="kbDrawerWidth"
      placement="right"
      display-directive="if"
    >
      <n-drawer-content
        :title="kbModal.isEdit ? '编辑知识库' : '新增知识库'"
        closable
        body-content-style="padding: 0 20px 20px;"
        :native-scrollbar="false"
      >
        <div class="kb-drawer-scroll">
          <n-form ref="kbFormRef" :model="kbModal.form" :rules="kbRules" label-placement="top" size="medium">
            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:database" aria-hidden="true" />
                <span>基础信息</span>
              </div>
              <n-form-item label="向量存储实例" path="vectorStoreInstanceId" required>
                <n-select
                  v-model:value="kbModal.form.vectorStoreInstanceId"
                  placeholder="请选择向量存储实例"
                  clearable
                  :options="storeInstanceOptions"
                />
              </n-form-item>
              <n-form-item label="知识库名称" path="knowledgeName" required>
                <n-input v-model:value="kbModal.form.knowledgeName" placeholder="请输入知识库名称" maxlength="100" show-count />
              </n-form-item>
              <n-form-item label="描述" path="description">
                <n-input v-model:value="kbModal.form.description" type="textarea" :rows="2" placeholder="请输入知识库描述" />
              </n-form-item>
              <n-form-item label="向量模型（Embedding）" path="embeddingModelId">
                <n-select
                  v-model:value="kbModal.form.embeddingModelId"
                  placeholder="请选择 Embedding 模型"
                  clearable
                  :options="embeddingModelOptions"
                />
              </n-form-item>
              <n-form-item label="Rerank 模型" path="rerankModelId">
                <n-select
                  v-model:value="kbModal.form.rerankModelId"
                  placeholder="请选择 Rerank 模型"
                  clearable
                  :options="rerankModelOptions"
                />
              </n-form-item>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:copy" aria-hidden="true" />
                <span>上传去重</span>
              </div>
              <p class="section-desc">
                控制同一知识库内重复文档的判定与处理方式。
              </p>
              <n-form-item label="去重策略" path="dedupStrategy">
                <n-select
                  v-model:value="kbModal.form.dedupStrategy"
                  placeholder="请选择去重策略"
                  :options="dedupStrategyOptions"
                />
              </n-form-item>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:cut" aria-hidden="true" />
                <span>切片策略</span>
              </div>
              <p class="section-desc">
                选择文档入库时的分块方式。
              </p>
              <div class="chunk-mode-seg" role="tablist" aria-label="切片策略">
                <button
                  v-for="opt in chunkStrategyOptions"
                  :key="opt.value"
                  type="button"
                  role="tab"
                  :aria-selected="kbModal.form.chunkStrategy === opt.value"
                  class="chunk-mode-seg__item"
                  :class="[kbModal.form.chunkStrategy === opt.value && 'chunk-mode-seg__item--active']"
                  @click="kbModal.form.chunkStrategy = opt.value"
                >
                  <span class="chunk-mode-seg__icon">{{ chunkStrategyIcons[opt.value] }}</span>
                  <span class="chunk-mode-seg__label">{{ opt.label }}</span>
                </button>
              </div>

              <div v-if="kbModal.form.chunkStrategy === 'length'" class="chunk-panel">
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">分块长度</span>
                  <n-input-number v-model:value="chunkMaxTokens" :min="50" :max="32000" :show-button="true" size="small" class="chunk-panel__input" />
                </div>
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">重叠长度</span>
                  <n-input-number v-model:value="chunkOverlap" :min="0" :max="4096" clearable :show-button="true" size="small" class="chunk-panel__input" placeholder="默认 16" />
                </div>
              </div>
              <div v-else-if="kbModal.form.chunkStrategy === 'delimiter'" class="chunk-panel">
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">分隔符</span>
                  <n-input v-model:value="chunkDelimiters" size="small" placeholder="如 \n\n 或 。" />
                </div>
              </div>
              <div v-else-if="kbModal.form.chunkStrategy === 'regex'" class="chunk-panel">
                <div class="chunk-panel__row">
                  <span class="chunk-panel__label">正则表达式</span>
                  <n-input v-model:value="chunkRegex" type="textarea" :rows="2" size="small" placeholder="一级切分正则" />
                </div>
              </div>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:tool" aria-hidden="true" />
                <span>检索配置</span>
              </div>
              <p class="section-desc">重排、上下文扩展与混合融合参数。重排序需先在基础信息中选择重排序模型。</p>
              <div class="search-config-grid">
                <n-form-item label="重排序">
                  <n-switch v-model:value="searchCfg.rerankEnable" />
                </n-form-item>
                <n-form-item label="中段遗忘优化">
                  <n-switch v-model:value="searchCfg.lostInMiddle" />
                </n-form-item>
                <n-form-item label="上下文扩展（邻近文档）">
                  <n-input-number v-model:value="searchCfg.nearbyCount" :min="0" :max="10" style="width: 100%" />
                </n-form-item>
                <n-form-item label="默认返回数">
                  <n-input-number v-model:value="searchCfg.topK" :min="1" :max="50" style="width: 100%" />
                </n-form-item>
                <n-form-item label="相似度阈值">
                  <n-input-number v-model:value="searchCfg.threshold" :min="0" :max="1" :step="0.05" style="width: 100%" />
                </n-form-item>
                <n-form-item label="融合方式">
                  <n-select v-model:value="searchCfg.rerankType" :options="rerankTypeOptions" clearable placeholder="RRF" style="width: 100%" />
                </n-form-item>
                <n-form-item label="向量权重（加权融合）">
                  <n-input-number v-model:value="searchCfg.vectorWeight" :min="0" :step="0.1" style="width: 100%" />
                </n-form-item>
                <n-form-item label="BM25 权重（加权融合）">
                  <n-input-number v-model:value="searchCfg.bm25Weight" :min="0" :step="0.1" style="width: 100%" />
                </n-form-item>
                <n-form-item label="RRF k 值">
                  <n-input-number v-model:value="searchCfg.rrfK" :min="1" :max="1000" style="width: 100%" />
                </n-form-item>
              </div>
            </div>

            <div class="kb-section-card">
              <div class="section-title">
                <i class="ai-icon:settings" aria-hidden="true" />
                <span>其他</span>
              </div>
              <n-form-item label="状态" path="status">
                <n-radio-group v-model:value="kbModal.form.status">
                  <n-radio v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </n-radio>
                </n-radio-group>
              </n-form-item>
              <n-form-item label="图标" path="icon">
                <n-input v-model:value="kbModal.form.icon" placeholder="图标标识，如 library-outline" />
              </n-form-item>
            </div>
          </n-form>
        </div>
        <template #footer>
          <div class="kb-drawer-footer">
            <NButton @click="kbModal.show = false">
              取消
            </NButton>
            <NButton type="primary" :loading="kbModal.saving" @click="handleSave">
              确定
            </NButton>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <!-- 检索调试 -->
    <n-modal
      v-model:show="searchModal.show"
      preset="card"
      title="知识库检索调试"
      :style="{ maxWidth: '820px', width: 'calc(100vw - 32px)' }"
      class="forge-debug-modal"
    >
      <div class="fm">
        <!-- 查询输入 -->
        <div class="fm-query">
          <n-input
            v-model:value="searchModal.query"
            type="textarea"
            :rows="2"
            placeholder="输入检索问题，例如：如何配置 API Key？"
            @keydown.enter.exact.prevent="handleSearchDebug"
          />
          <div class="fm-query__actions">
            <NButton type="primary" :loading="searchModal.loading" @click="handleSearchDebug">
              检索
            </NButton>
          </div>
        </div>

        <!-- 检索模式 -->
        <div class="fm-modes">
          <button
            v-for="m in searchModeOptions"
            :key="m.value"
            type="button"
            class="fm-mode"
            :class="{ 'fm-mode--active': searchModal.searchType === m.value }"
            @click="searchModal.searchType = m.value"
          >
            {{ m.label }}
          </button>
        </div>

        <!-- 高级设置 -->
        <div class="fm-adv-toggle" role="button" tabindex="0" @click="searchModal.showAdvanced = !searchModal.showAdvanced">
          <span class="fm-adv-toggle__label">{{ searchModal.showAdvanced ? '收起高级设置' : '高级设置' }}</span>
          <i class="fm-adv-toggle__chevron" :class="{ 'is-open': searchModal.showAdvanced }">▾</i>
        </div>
        <div v-show="searchModal.showAdvanced" class="fm-adv">
          <div class="fm-adv__row">
            <div class="fm-adv__item fm-adv__item--grow">
              <span class="fm-adv__label">阈值</span>
              <n-slider v-model:value="searchModal.threshold" :min="0" :max="1" :step="0.05" />
              <n-input-number v-model:value="searchModal.threshold" :min="0" :max="1" :step="0.05" size="small" style="width: 84px" />
            </div>
          </div>
          <div class="fm-adv__row">
            <div class="fm-adv__item">
              <span class="fm-adv__label">返回条数</span>
              <n-input-number v-model:value="searchModal.topK" :min="1" :max="50" size="small" style="width: 84px" />
            </div>
            <div class="fm-adv__item">
              <span class="fm-adv__label">重排序</span>
              <n-switch v-model:value="searchModal.rerankEnable" size="small" />
            </div>
            <div class="fm-adv__item">
              <span class="fm-adv__label">中段遗忘优化</span>
              <n-switch v-model:value="searchModal.lostInMiddle" size="small" />
            </div>
            <div class="fm-adv__item">
              <span class="fm-adv__label">查询补全</span>
              <n-switch v-model:value="searchModal.queryComplete" size="small" />
            </div>
            <div class="fm-adv__item">
              <span class="fm-adv__label">邻近文档</span>
              <n-input-number v-model:value="searchModal.nearbyCount" :min="0" :max="10" size="small" style="width: 84px" />
            </div>
            <div class="fm-adv__item">
              <span class="fm-adv__label">融合</span>
              <n-select v-model:value="searchModal.fusionStrategy" :options="fusionStrategyOptions" size="small" style="width: 150px" />
            </div>
          </div>
          <div class="fm-adv__row">
            <div class="fm-adv__item fm-adv__item--grow">
              <span class="fm-adv__label">过滤表达式</span>
              <n-input v-model:value="searchModal.filterExpr" placeholder="如 source_id == &quot;doc1&quot;" clearable size="small" />
            </div>
          </div>
        </div>

        <!-- 统计条 -->
        <div v-if="searchModal.meta || searchModal.results.length" class="fm-stats">
          <span class="fm-stats__item"><strong>{{ searchModal.results.length }}</strong> 条命中</span>
          <span class="fm-stats__item">耗时 <strong>{{ searchModal.meta?.elapsedMs ?? 0 }}</strong> ms</span>
          <span class="fm-stats__item fm-stats__item--tag">{{ searchModeLabel }}</span>
          <span v-if="searchModal.meta?.vectorCount != null" class="fm-stats__item">向量 {{ searchModal.meta.vectorCount }}</span>
          <span v-if="searchModal.meta?.bm25Count != null" class="fm-stats__item">BM25 {{ searchModal.meta.bm25Count }}</span>
          <span v-if="searchModal.meta?.hybridCount != null" class="fm-stats__item">混合 {{ searchModal.meta.hybridCount }}</span>
          <span v-if="searchModal.meta?.expandedQuery" class="fm-stats__item fm-stats__item--ellipsis" :title="`补全后查询：${searchModal.meta.expandedQuery}`">
            补全 <code>{{ searchModal.meta.expandedQuery }}</code>
          </span>
        </div>

        <!-- 结果 -->
        <div class="fm-results">
          <n-spin :show="searchModal.loading">
            <div v-if="searchModal.results.length" class="fm-list">
              <div v-for="(r, i) in searchModal.results" :key="i" class="fm-item">
                <div class="fm-item__head">
                  <span class="fm-item__rank">{{ i + 1 }}</span>
                  <div class="fm-item__score">
                    <div class="fm-item__score-bar" :style="{ width: scorePercent(r.score) + '%' }" />
                  </div>
                  <span class="fm-item__score-num">{{ r.score?.toFixed?.(3) ?? '—' }}</span>
                  <span v-if="r.rerankScore && Math.abs(r.rerankScore - (r.score || 0)) > 0.0001" class="fm-item__rerank">
                    重排 {{ r.rerankScore.toFixed(3) }}
                  </span>
                  <div class="fm-item__actions">
                    <NButton text size="tiny" @click="copyText(r.content)">复制</NButton>
                    <NButton text size="tiny" @click="openDocFromResult(r)">查看原文</NButton>
                  </div>
                </div>
                <div v-if="r.title" class="fm-item__title" v-html="highlightText(r.title, searchModal.query)" />
                <div class="fm-item__content" v-html="highlightText(r.content, searchModal.query)" />
                <div class="fm-item__meta">
                  <NTag size="tiny" :bordered="false">{{ r.docName || `文档 #${r.documentId}` }}</NTag>
                  <span v-if="r.chunkIndex != null" class="fm-item__meta-tag">分块 #{{ r.chunkIndex + 1 }}</span>
                  <span v-if="r.sourceId" class="fm-item__meta-tag">{{ r.sourceId }}</span>
                </div>
              </div>
            </div>
            <div v-else-if="!searchModal.loading && searchModal.meta" class="fm-empty">
              没有命中结果 —— 试试降低阈值、换关键词，或切换检索模式。
            </div>
            <div v-else-if="!searchModal.loading && !searchModal.meta" class="fm-hint">
              输入检索问题，点击「检索」开始测试。
            </div>
          </n-spin>
        </div>
      </div>
      <template #action>
        <div class="modal-footer-actions">
          <NButton text @click="resetSearchDebug">重置</NButton>
          <NButton @click="searchModal.show = false">关闭</NButton>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="docViewModal.show"
      preset="card"
      :title="`查看文档 - ${docViewModal.docName || ''}`"
      :style="{ maxWidth: '1180px', width: 'calc(100vw - 48px)' }"
      class="forge-debug-modal"
    >
      <div class="fm">
        <!-- 文档统计条 -->
        <div v-if="!docViewModal.loading" class="fm-doc__stats">
          <div class="fm-doc__stat">
            <span class="fm-doc__stat-num">{{ docCharCount }}</span>
            <span class="fm-doc__stat-label">字符</span>
          </div>
          <div class="fm-doc__stat">
            <span class="fm-doc__stat-num">{{ docParagraphCount }}</span>
            <span class="fm-doc__stat-label">段落</span>
          </div>
          <div class="fm-doc__stat">
            <span class="fm-doc__stat-num">{{ docViewModal.chunks.length }}</span>
            <span class="fm-doc__stat-label">分块</span>
          </div>
          <div class="fm-doc__stat">
            <span class="fm-doc__stat-num">{{ chunkTotalTokens }}</span>
            <span class="fm-doc__stat-label">词元</span>
          </div>
          <div class="fm-doc__stat-actions">
            <NButton size="small" type="primary" ghost @click="copyText(docViewModal.content)">
              复制原文
            </NButton>
          </div>
        </div>

        <n-tabs v-model:value="docViewModal.tab" type="line" animated>
          <n-tab-pane name="content" tab="原文">
            <n-spin :show="docViewModal.loading">
              <template v-if="!docViewModal.loading">
                <div v-if="docViewModal.content" class="fm-doc__paper">
                  <pre class="fm-doc__raw">{{ docViewModal.content }}</pre>
                </div>
                <div v-else class="fm-empty">原文为空</div>
              </template>
            </n-spin>
          </n-tab-pane>
          <n-tab-pane name="chunks" tab="分块">
            <n-spin :show="docViewModal.loading">
              <div v-if="!docViewModal.loading && docViewModal.chunks.length" class="fm-chunk-grid">
                <div v-for="(c, i) in docViewModal.chunks" :key="c.id || i" class="fm-chunk">
                  <div class="fm-chunk__head">
                    <span class="fm-chunk__badge">分块 {{ i + 1 }}</span>
                    <span class="fm-chunk__tokens">{{ c.tokenCount ?? 0 }} 词元</span>
                    <div class="fm-chunk__actions">
                      <NButton text size="tiny" @click="copyText(c.content)">复制</NButton>
                    </div>
                  </div>
                  <div class="fm-chunk__content">{{ c.content }}</div>
                  <div v-if="c.vectorId || c.title" class="fm-chunk__meta">
                    <span v-if="c.vectorId" class="fm-chunk__tag" title="向量 ID">{{ c.vectorId }}</span>
                    <span v-if="c.title" class="fm-chunk__tag fm-chunk__tag--title" title="分块标题">{{ c.title }}</span>
                  </div>
                </div>
              </div>
              <n-empty v-else-if="!docViewModal.loading" description="暂无分块" />
            </n-spin>
          </n-tab-pane>
        </n-tabs>
      </div>
      <template #action>
        <div class="modal-footer-actions">
          <NButton @click="docViewModal.show = false">关闭</NButton>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { NButton, NPopconfirm, NTag } from 'naive-ui'
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  knowledgeDocumentPage as fetchDocPage,
  knowledgePage as fetchKbPage,
  modelPage as fetchModelPage,
  storeInstancePage as fetchStorePage,
  knowledgeCreate,
  knowledgeDelete,
  knowledgeDocumentDelete,
  knowledgeDocumentChunks,
  knowledgeDocumentContent,
  knowledgeDocumentProgressSSE,
  knowledgeDocumentReprocess,
  knowledgeDocumentUpload,
  knowledgeUpdate,
  ragSearchDebug,
} from '@/api/ai'
import DictTag from '@/components/DictTag.vue'
import { useAuthStore } from '@/store'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'AiKnowledge' })

const authStore = useAuthStore()
const { dict } = useDict('ai_status', 'ai_knowledge_process_status', 'ai_store_instance_category', 'ai_vector_store_type')

const statusOptions = computed(() => dict.value.ai_status || [])
const processStatusOptions = computed(() => dict.value.ai_knowledge_process_status || [])
const pageSizes = [10, 20, 50]
const modalCardStyle = { maxWidth: '860px', width: 'calc(100vw - 32px)' }

// 创建/编辑抽屉宽度：窄屏时自适应，避免横向溢出
const kbDrawerWidth = computed(() => {
  if (typeof window === 'undefined')
    return 760
  return Math.min(760, Math.max(400, window.innerWidth - 24))
})

const chunkStrategyOptions = [
  { label: '长度分块', value: 'length' },
  { label: '分隔符分块', value: 'delimiter' },
  { label: '正则分块', value: 'regex' },
  { label: '智能分块', value: 'smart' },
  { label: '问答分块', value: 'qa' },
]
const chunkStrategyIcons = {
  length: '⚡',
  delimiter: '✂',
  regex: '.*',
  smart: '🧠',
  qa: '❓',
}
const dedupStrategyOptions = [
  { label: '不去重', value: 'none' },
  { label: '按名称去重', value: 'name' },
  { label: '按内容去重', value: 'content' },
  { label: '名称或内容', value: 'name_or_content' },
]

// 切片配置（按策略展开）
const chunkMaxTokens = ref(600)
const chunkOverlap = ref(null)
const chunkDelimiters = ref('')
const chunkRegex = ref('')

const uploadPrefix = import.meta.env.VITE_REQUEST_PREFIX || '/dev-api'
const uploadHeaders = computed(() => {
  const token = authStore.accessToken
  return { Authorization: token ? `Bearer ${token}` : '' }
})

const search = reactive({ name: '' })
const kbList = ref([])
const loading = ref(false)
const selectedKb = ref(null)
const pagination = reactive({ pageNum: 1, pageSize: 10, itemCount: 0 })

const docSearch = reactive({ processStatus: null })
const docList = ref([])
const docLoading = ref(false)
const docPagination = reactive({ pageNum: 1, pageSize: 10, itemCount: 0 })
const docProcessing = reactive({})

const kbFormRef = ref(null)
const kbModal = reactive({ show: false, isEdit: false, saving: false, form: createKbForm() })
const searchModal = reactive({
  show: false,
  query: '',
  topK: 5,
  threshold: 0.5,
  loading: false,
  results: [],
  meta: null,
  searchType: '',
  rerankEnable: false,
  lostInMiddle: false,
  fusionStrategy: 'rrf',
  queryComplete: false,
  nearbyCount: 0,
  filterExpr: '',
  showAdvanced: false,
})

// 存储实例 / 模型下拉数据
const storeInstanceOptions = ref([])
const embeddingModelOptions = ref([])
const rerankModelOptions = ref([])

// 检索模式选项（检索调试弹窗分段按钮；空串=管线默认融合）
const searchModeOptions = [
  { label: '默认融合', value: '' },
  { label: '纯向量', value: 'vector' },
  { label: 'BM25', value: 'bm25' },
  { label: '混合', value: 'hybrid' },
]
// 融合策略选项（检索调试弹窗高级设置；对齐后端 fusionStrategy=rrf/weighted_sum）
const fusionStrategyOptions = [
  { label: 'RRF 融合（默认）', value: 'rrf' },
  { label: '加权融合', value: 'weighted_sum' },
]
// 融合方式选项（知识库检索配置）
const rerankTypeOptions = [
  { label: 'RRF 融合（默认）', value: 'rrf' },
  { label: '加权融合（向量 + BM25）', value: 'weighted' },
]

// 检索配置（存 search_config_json；可空项不写入，交给后端默认）
function defaultSearchCfg() {
  return {
    rerankEnable: false,
    lostInMiddle: false,
    nearbyCount: 0,
    topK: null,
    threshold: null,
    rerankType: null,
    vectorWeight: null,
    bm25Weight: null,
    rrfK: null,
  }
}

const searchCfg = reactive(defaultSearchCfg())

function resetSearchCfg() {
  Object.assign(searchCfg, defaultSearchCfg())
}

function loadSearchCfg(json) {
  if (!json)
    return
  let cfg
  try {
    cfg = JSON.parse(json)
  }
  catch { return }
  if (!cfg || typeof cfg !== 'object')
    return
  searchCfg.rerankEnable = cfg.rerank_enable != null ? cfg.rerank_enable : false
  searchCfg.lostInMiddle = cfg.lost_in_middle != null ? cfg.lost_in_middle : false
  searchCfg.nearbyCount = cfg.nearby_count != null ? cfg.nearby_count : 0
  searchCfg.topK = cfg.topK != null ? cfg.topK : null
  searchCfg.threshold = cfg.threshold != null ? cfg.threshold : null
  searchCfg.rerankType = cfg.rerank_type || null
  searchCfg.vectorWeight = cfg.vector_weight != null ? cfg.vector_weight : null
  searchCfg.bm25Weight = cfg.bm25_weight != null ? cfg.bm25_weight : null
  searchCfg.rrfK = cfg.rrf_k != null ? cfg.rrf_k : null
}

function createKbForm() {
  return {
    knowledgeName: '',
    description: '',
    icon: '',
    vectorStoreInstanceId: null,
    embeddingModelId: null,
    rerankModelId: null,
    dimensionOfVectorModel: null,
    chunkStrategy: 'length',
    chunkConfigJson: '',
    searchConfigJson: '',
    dedupStrategy: 'none',
    dedupAction: 'reject',
    uploadConfirm: '0',
    status: '0',
  }
}

const kbRules = {
  knowledgeName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  vectorStoreInstanceId: [{ required: true, message: '请选择向量存储实例', trigger: 'change' }],
}

async function loadKbs() {
  loading.value = true
  try {
    const res = await fetchKbPage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...(search.name ? { knowledgeName: search.name } : {}),
    })
    if (res.code === 200 && res.data) {
      kbList.value = res.data.records || []
      pagination.itemCount = Number(res.data.total || 0)
      if (selectedKb.value) {
        const current = kbList.value.find(k => k.id === selectedKb.value.id)
        if (current)
          selectedKb.value = current
      }
    }
  }
  catch {}
  finally {
    loading.value = false
  }
}

async function loadStoreInstances() {
  try {
    const res = await fetchStorePage({ pageNum: 1, pageSize: 100 })
    if (res.code === 200 && res.data)
      storeInstanceOptions.value = (res.data.records || []).map(s => ({ label: s.instanceName, value: s.id }))
  }
  catch {}
}

async function loadModelOptions() {
  try {
    const [embRes, rerankRes] = await Promise.all([
      fetchModelPage({ pageNum: 1, pageSize: 100, modelType: 'embedding' }),
      fetchModelPage({ pageNum: 1, pageSize: 100, modelType: 'rerank' }),
    ])
    if (embRes.code === 200 && embRes.data)
      embeddingModelOptions.value = (embRes.data.records || []).map(m => ({ label: m.modelName || m.modelId, value: m.id }))
    if (rerankRes.code === 200 && rerankRes.data)
      rerankModelOptions.value = (rerankRes.data.records || []).map(m => ({ label: m.modelName || m.modelId, value: m.id }))
  }
  catch {}
}

function handleSearch() {
  pagination.pageNum = 1
  loadKbs()
}

function handleReset() {
  search.name = ''
  pagination.pageNum = 1
  loadKbs()
}

function handlePageChange(page) {
  pagination.pageNum = page
  loadKbs()
}

function handlePageSizeChange(pageSize) {
  pagination.pageSize = pageSize
  pagination.pageNum = 1
  loadKbs()
}

function handleSelect(kb) {
  if (selectedKb.value?.id === kb.id)
    return
  selectedKb.value = kb
  docPagination.pageNum = 1
  loadDocs()
}

function handleAdd() {
  kbModal.isEdit = false
  kbModal.form = createKbForm()
  resetSearchCfg()
  kbModal.show = true
}

async function handleEdit(kb) {
  kbModal.isEdit = true
  kbModal.form = { ...createKbForm(), ...kb }
  resetSearchCfg()
  loadSearchCfg(kb.searchConfigJson)
  kbModal.show = true
}

async function handleSave() {
  try {
    await kbFormRef.value?.validate()
  }
  catch { return }
  kbModal.saving = true
  try {
    const payload = { ...kbModal.form }
    // 按切片策略拼 chunkConfigJson
    const strategy = payload.chunkStrategy
    if (strategy === 'length') {
      payload.chunkConfigJson = JSON.stringify({
        max_tokens: chunkMaxTokens.value,
        overlap: chunkOverlap.value ?? 16,
      })
    }
    else if (strategy === 'delimiter') {
      payload.chunkConfigJson = JSON.stringify({ delimiters: chunkDelimiters.value })
    }
    else if (strategy === 'regex') {
      payload.chunkConfigJson = JSON.stringify({ regex: chunkRegex.value })
    }
    // 拼检索配置 searchConfigJson（布尔/数量总是写入以支持关闭；可空项不写入，交给后端默认）
    const cfg = {}
    if (searchCfg.rerankEnable != null)
      cfg.rerank_enable = searchCfg.rerankEnable
    if (searchCfg.lostInMiddle != null)
      cfg.lost_in_middle = searchCfg.lostInMiddle
    if (searchCfg.nearbyCount != null)
      cfg.nearby_count = searchCfg.nearbyCount
    if (searchCfg.topK != null)
      cfg.topK = searchCfg.topK
    if (searchCfg.threshold != null)
      cfg.threshold = searchCfg.threshold
    if (searchCfg.rerankType)
      cfg.rerank_type = searchCfg.rerankType
    if (searchCfg.vectorWeight != null)
      cfg.vector_weight = searchCfg.vectorWeight
    if (searchCfg.bm25Weight != null)
      cfg.bm25_weight = searchCfg.bm25Weight
    if (searchCfg.rrfK != null)
      cfg.rrf_k = searchCfg.rrfK
    payload.searchConfigJson = Object.keys(cfg).length ? JSON.stringify(cfg) : ''
    const res = kbModal.isEdit ? await knowledgeUpdate(payload) : await knowledgeCreate(payload)
    if (res.code === 200) {
      window.$message.success(kbModal.isEdit ? '更新成功' : '新增成功')
      kbModal.show = false
      await loadKbs()
    }
    else {
      window.$message.error(res.msg || '操作失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '操作失败')
  }
  finally {
    kbModal.saving = false
  }
}

async function handleDelete(id) {
  try {
    const res = await knowledgeDelete(id)
    if (res.code === 200) {
      window.$message.success('删除成功')
      if (selectedKb.value?.id === id)
        selectedKb.value = null
      await loadKbs()
    }
    else {
      window.$message.error(res.msg || '删除失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '删除失败')
  }
}

// ===== 文档管理 =====

async function loadDocs() {
  if (!selectedKb.value) {
    docList.value = []
    return
  }
  docLoading.value = true
  try {
    const res = await fetchDocPage({
      pageNum: docPagination.pageNum,
      pageSize: docPagination.pageSize,
      knowledgeId: selectedKb.value.id,
      ...(docSearch.processStatus ? { processStatus: docSearch.processStatus } : {}),
    })
    if (res.code === 200 && res.data) {
      docList.value = res.data.records || []
      docPagination.itemCount = Number(res.data.total || 0)
    }
  }
  catch {}
  finally {
    docLoading.value = false
  }
}

function handleDocSearch() {
  docPagination.pageNum = 1
  loadDocs()
}

function handleDocPageChange(page) {
  docPagination.pageNum = page
  loadDocs()
}

function handleDocPageSizeChange(pageSize) {
  docPagination.pageSize = pageSize
  docPagination.pageNum = 1
  loadDocs()
}

function handleUploadFinish({ event }) {
  try {
    const res = JSON.parse(event.target.response)
    if (res.code === 200 && res.data) {
      const fileId = res.data.fileId || res.data.id
      submitDocumentUpload(fileId, res.data)
    }
    else {
      window.$message.error(res.msg || '上传失败')
    }
  }
  catch {
    window.$message.error('上传失败')
  }
  return false
}

function handleUploadError() {
  window.$message.error('文件上传失败')
}

async function submitDocumentUpload(fileId, fileData) {
  try {
    const res = await knowledgeDocumentUpload({
      knowledgeId: selectedKb.value.id,
      fileId,
      docName: fileData.originalName || fileData.fileName || `文档${Date.now()}`,
      sourceType: 'upload',
      confirm: true,
    })
    if (res.code === 200) {
      window.$message.success('文档上传成功，开始处理')
      await loadDocs()
    }
    else {
      window.$message.error(res.msg || '文档上传失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '文档上传失败')
  }
}

function subscribeDocProgress(doc) {
  if (docProcessing[doc.id])
    return
  docProcessing[doc.id] = true
  knowledgeDocumentProgressSSE(
    doc.id,
    (event) => {
      if (event && (event.percent === 100 || event.status === 'success' || event.status === 'failed')) {
        docProcessing[doc.id] = false
        loadDocs()
      }
    },
    () => { docProcessing[doc.id] = false },
    () => { docProcessing[doc.id] = false },
  )
}

async function handleDeleteDoc(doc) {
  try {
    const res = await knowledgeDocumentDelete(doc.id)
    if (res.code === 200) {
      window.$message.success('删除成功')
      await loadDocs()
    }
    else {
      window.$message.error(res.msg || '删除失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '删除失败')
  }
}

async function reprocessDoc(doc) {
  try {
    const res = await knowledgeDocumentReprocess(doc.id)
    if (res.code === 200) {
      window.$message.success('已重新处理')
      subscribeDocProgress(doc)
      await loadDocs()
    }
    else {
      window.$message.error(res.msg || '重试失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '重试失败')
  }
}

// ===== 文档查看（原文 / 分块） =====

const docViewModal = reactive({
  show: false,
  docName: '',
  tab: 'content',
  content: null,
  chunks: [],
  loading: false,
})

// 文档统计（原文字符/段落、分块词元合计）
const docCharCount = computed(() => (docViewModal.content || '').length)
const docParagraphCount = computed(() => {
  const s = docViewModal.content || ''
  return s ? s.split(/\r?\n/).filter((l) => l.trim()).length : 0
})
const chunkTotalTokens = computed(() =>
  docViewModal.chunks.reduce((sum, c) => sum + (c.tokenCount ?? 0), 0),
)

async function openDocView(doc) {
  docViewModal.show = true
  docViewModal.docName = doc.docName
  docViewModal.tab = 'content'
  docViewModal.content = null
  docViewModal.chunks = []
  docViewModal.loading = true
  try {
    const [contentRes, chunkRes] = await Promise.all([
      knowledgeDocumentContent(doc.id),
      knowledgeDocumentChunks(doc.id),
    ])
    docViewModal.content = contentRes.code === 200 ? contentRes.data : null
    docViewModal.chunks = chunkRes.code === 200 && Array.isArray(chunkRes.data) ? chunkRes.data : []
    if (contentRes.code !== 200) window.$message.error(contentRes.msg || '原文加载失败')
    if (chunkRes.code !== 200) window.$message.error(chunkRes.msg || '分块加载失败')
  }
  catch (e) {
    window.$message.error(e.message || '文档加载失败')
  }
  finally {
    docViewModal.loading = false
  }
}

// ===== 检索调试 =====

function openSearchDebug() {
  // 打开时按知识库检索配置初始化（rerank/nearby 跟随配置，可临时覆盖做对比）
  let cfg = null
  try {
    cfg = selectedKb.value?.searchConfigJson ? JSON.parse(selectedKb.value.searchConfigJson) : null
  }
  catch {}
  searchModal.rerankEnable = cfg?.rerank_enable != null ? cfg.rerank_enable : false
  searchModal.lostInMiddle = cfg?.lost_in_middle != null ? cfg.lost_in_middle : false
  searchModal.nearbyCount = cfg?.nearby_count != null ? cfg.nearby_count : 0
  searchModal.topK = cfg?.topK || 5
  searchModal.threshold = cfg?.threshold != null ? cfg.threshold : 0.5
  searchModal.searchType = ''
  searchModal.fusionStrategy = 'rrf'
  searchModal.queryComplete = false
  searchModal.filterExpr = ''
  searchModal.results = []
  searchModal.meta = null
  searchModal.showAdvanced = false
  searchModal.show = true
}

async function handleSearchDebug() {
  if (!searchModal.query.trim()) {
    window.$message.warning('请输入检索问题')
    return
  }
  searchModal.loading = true
  searchModal.results = []
  searchModal.meta = null
  try {
    const params = {
      knowledgeId: selectedKb.value.id,
      query: searchModal.query,
      topK: searchModal.topK || 5,
      threshold: searchModal.threshold,
      rerankEnable: searchModal.rerankEnable,
      lostInMiddle: searchModal.lostInMiddle,
      fusionStrategy: searchModal.fusionStrategy,
      queryComplete: searchModal.queryComplete,
      nearbyCount: searchModal.nearbyCount > 0 ? searchModal.nearbyCount : undefined,
      filterExpr: searchModal.filterExpr?.trim() || undefined,
    }
    if (searchModal.searchType)
      params.searchType = searchModal.searchType
    // 走 RAG 管线调试端点：返回结果 + 元信息（实际检索类型/各路命中数/耗时/补全query）
    const res = await ragSearchDebug(params)
    if (res.code === 200 && res.data) {
      searchModal.results = res.data.list || []
      searchModal.meta = res.data.meta || null
    }
    else {
      window.$message.error(res.msg || '检索失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '检索失败')
  }
  finally {
    searchModal.loading = false
  }
}

// ===== 检索调试辅助 =====

const searchModeLabel = computed(() => {
  const t = searchModal.meta?.searchType
  if (!t)
    return '默认融合'
  if (t === 'vector')
    return '纯向量'
  if (t === 'bm25')
    return 'BM25'
  if (t === 'hybrid')
    return '混合'
  return t
})

function scorePercent(score) {
  const v = Number(score) || 0
  return Math.max(0, Math.min(100, v * 100))
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

function escapeRegExp(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function highlightText(text, query) {
  if (!text)
    return ''
  let html = escapeHtml(text)
  const terms = (query || '').trim().split(/\s+/).filter(Boolean)
  for (const t of terms) {
    const escaped = escapeHtml(t)
    if (!escaped)
      continue
    html = html.replace(new RegExp(escapeRegExp(escaped), 'gi'), (m) => `<mark class="fm-hl">${m}</mark>`)
  }
  return html
}

function copyText(text) {
  if (!text)
    return
  navigator.clipboard?.writeText(text)
    .then(() => window.$message.success('已复制'))
    .catch(() => window.$message.error('复制失败'))
}

function openDocFromResult(r) {
  openDocView({ id: r.documentId, docName: r.docName || `文档 #${r.documentId}` })
}

function resetSearchDebug() {
  searchModal.query = ''
  searchModal.results = []
  searchModal.meta = null
  searchModal.searchType = ''
  searchModal.showAdvanced = false
  searchModal.threshold = 0.5
  searchModal.topK = 5
  searchModal.rerankEnable = false
  searchModal.lostInMiddle = false
  searchModal.fusionStrategy = 'rrf'
  searchModal.queryComplete = false
  searchModal.nearbyCount = 0
  searchModal.filterExpr = ''
}

// ===== 表格列 =====

const docColumns = [
  { title: '文档名称', key: 'docName', width: 240, ellipsis: { tooltip: true } },
  {
    title: '类型',
    key: 'docType',
    width: 90,
    render(row) { return h('span', {}, row.docType || '—') },
  },
  {
    title: '处理状态',
    key: 'processStatus',
    width: 110,
    render(row) {
      const opts = processStatusOptions.value
      return h(DictTag, { dictType: 'ai_knowledge_process_status', value: row.processStatus, size: 'small' })
    },
  },
  { title: '分块数', key: 'chunkCount', width: 80, align: 'right', render(row) { return row.chunkCount ?? '-' } },
  {
    title: '处理时间',
    key: 'updateTime',
    width: 150,
    render(row) { return row.updateTime ? String(row.updateTime).replace('T', ' ').slice(0, 16) : '—' },
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right',
    render(row) {
      const actions = []
      actions.push(h(NButton, { text: true, size: 'small', onClick: () => openDocView(row) }, { default: () => '查看' }))
      if (row.processStatus === 'pending' || row.processStatus === 'processing') {
        actions.push(h(NButton, { text: true, size: 'small', class: 'text-warning', loading: !!docProcessing[row.id], onClick: () => subscribeDocProgress(row) }, { default: () => docProcessing[row.id] ? '处理中' : '刷新进度' }))
      }
      if (row.processStatus === 'failed') {
        actions.push(h(NButton, { text: true, size: 'small', class: 'text-warning', onClick: () => reprocessDoc(row) }, { default: () => '重试' }))
      }
      actions.push(h(NPopconfirm, { onPositiveClick: () => handleDeleteDoc(row) }, {
        trigger: () => h(NButton, { text: true, size: 'small', class: 'text-error' }, { default: () => '删除' }),
        default: () => '确定删除该文档吗？',
      }))
      return h('div', { class: 'table-actions' }, actions)
    },
  },
]

onMounted(() => {
  loadKbs()
  loadStoreInstances()
  loadModelOptions()
})
</script>

<style scoped>
.ai-knowledge-page {
  --page-bg: #f3f6fa;
  --panel-bg: #ffffff;
  --panel-subtle: #f8fafc;
  --panel-border: #dfe6ee;
  --text-strong: #111827;
  --text-body: #475569;
  --text-muted: #64748b;
  --accent: #0369a1;
  --accent-soft: #eaf4fb;
  --accent-border: #b9d9ec;
  --shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  min-height: 100%;
  padding: 20px;
  color: var(--text-body);
  background: var(--page-bg);
}

:global(.dark) .ai-knowledge-page {
  --page-bg: #0d1420;
  --panel-bg: #151f2d;
  --panel-subtle: #111a27;
  --panel-border: #2c3a4d;
  --text-strong: #f1f5f9;
  --text-body: #cbd5e1;
  --text-muted: #94a3b8;
  --accent: #38bdf8;
  --accent-soft: rgba(14, 165, 233, 0.12);
  --accent-border: rgba(56, 189, 248, 0.3);
}

.kb-layout {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.6fr);
  gap: 16px;
  align-items: start;
}

.kb-layout > * {
  min-width: 0;
}

.kb-list-panel,
.kb-detail-panel {
  min-height: calc(100vh - 150px);
  overflow: hidden;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 9px;
  box-shadow: var(--shadow);
}

.kb-list-panel {
  display: flex;
  flex-direction: column;
}

.kb-list-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--panel-border);
}

.kb-list-panel__header h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
}

.kb-list-filters {
  padding: 12px;
  background: var(--panel-subtle);
  border-bottom: 1px solid var(--panel-border);
}

.kb-list-filters__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.kb-list {
  min-height: 200px;
  max-height: calc(100vh - 340px);
  overflow-y: auto;
}

.kb-list-item {
  display: flex;
  width: 100%;
  padding: 14px 13px;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--panel-border);
  transition: background-color 160ms ease;
}

.kb-list-item:hover {
  background: var(--panel-subtle);
}

.kb-list-item--selected,
.kb-list-item--selected:hover {
  background: var(--accent-soft);
  box-shadow: inset 3px 0 0 var(--accent);
}

.kb-list-item__icon {
  display: grid;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  color: var(--accent);
  font-size: 16px;
  place-items: center;
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  border-radius: 8px;
}

.kb-list-item__content {
  min-width: 0;
  flex: 1;
}

.kb-list-item__title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.kb-list-item__title strong {
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-list-item__desc {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-list-pagination {
  display: flex;
  padding: 11px 12px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  overflow-x: auto;
  color: var(--text-muted);
  border-top: 1px solid var(--panel-border);
  font-size: 11px;
}

.kb-detail-header {
  display: flex;
  padding: 18px 20px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--panel-border);
}

.kb-detail-header__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.kb-detail-header__icon {
  display: grid;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  color: var(--accent);
  font-size: 20px;
  place-items: center;
  background: var(--accent-soft);
  border: 1px solid var(--accent-border);
  border-radius: 10px;
}

.kb-detail-header__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kb-detail-header__title h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 17px;
}

.kb-detail-header__desc {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.kb-detail-header__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.kb-doc-toolbar {
  display: flex;
  padding: 13px 16px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
}

.kb-doc-toolbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.kb-doc-toolbar__left strong {
  color: var(--text-strong);
  font-size: 13px;
}

.kb-doc-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.kb-doc-pagination {
  display: flex;
  padding: 12px 16px;
  justify-content: flex-end;
  overflow-x: auto;
  border-top: 1px solid var(--panel-border);
}

.kb-detail-empty {
  display: flex;
  min-height: calc(100vh - 152px);
  padding: 32px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--text-muted);
  text-align: center;
}

.kb-detail-empty > i {
  margin-bottom: 12px;
  font-size: 32px;
}

.kb-detail-empty h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 18px;
}

.kb-detail-empty p {
  margin: 7px 0 0;
  font-size: 12px;
}

.kb-drawer-scroll {
  max-height: calc(100vh - 140px);
  overflow-y: auto;
  padding-right: 4px;
}

.kb-section-card {
  margin-bottom: 14px;
  padding: 16px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.kb-section-card:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
}

.section-title i {
  color: var(--accent);
  font-size: 16px;
}

.section-desc {
  margin: -6px 0 12px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.kb-drawer-footer {
  padding-top: 8px;
  border-top: 1px solid var(--panel-border);
}

.chunk-mode-seg {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 4px;
  border-radius: 10px;
  background: var(--panel-subtle);
  border: 1px solid var(--panel-border);
}

.chunk-mode-seg__item {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 9px 6px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-muted);
  transition:
    background 0.2s,
    color 0.2s,
    border-color 0.2s;
}

.chunk-mode-seg__item:hover {
  color: var(--text-strong);
  background: color-mix(in srgb, var(--text-strong) 6%, transparent);
}

.chunk-mode-seg__item--active {
  color: var(--accent);
  font-weight: 600;
  background: var(--accent-soft);
  border-color: var(--accent-border);
}

.chunk-mode-seg__icon {
  font-size: 14px;
  line-height: 1;
}

.chunk-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  padding: 12px 14px;
  background: var(--panel-subtle);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
}

.chunk-panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.chunk-panel__label {
  flex: 0 0 auto;
  color: var(--text-body);
  font-size: 12px;
}

.chunk-panel__input {
  max-width: 220px;
}

.modal-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  white-space: nowrap;
}

.search-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

/* ===== 检索调试 / 文档查看 弹窗（forge-debug-modal，teleport 到 body，自包含配色） ===== */
.fm {
  --fm-bg: #ffffff;
  --fm-subtle: #f8fafc;
  --fm-border: #e6ebf2;
  --fm-border-strong: #d7dfe9;
  --fm-text: #334155;
  --fm-text-strong: #0f172a;
  --fm-muted: #94a3b8;
  --fm-accent: #2563eb;
  --fm-accent-soft: #eff6ff;
  --fm-accent-border: #bfdbfe;
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: var(--fm-text);
}

/* 查询输入 */
.fm-query {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.fm-query :deep(.n-input) {
  flex: 1;
}

.fm-query__actions {
  display: flex;
  gap: 8px;
  padding-top: 2px;
}

/* 检索模式 */
.fm-modes {
  display: inline-flex;
  gap: 2px;
  align-self: flex-start;
  padding: 3px;
  border-radius: 8px;
  background: var(--fm-subtle);
  border: 1px solid var(--fm-border);
}

.fm-mode {
  padding: 5px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--fm-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.fm-mode:hover {
  color: var(--fm-text);
}

.fm-mode--active {
  background: var(--fm-bg);
  color: var(--fm-accent);
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

/* 高级设置 */
.fm-adv-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  color: var(--fm-muted);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.fm-adv-toggle:hover {
  color: var(--fm-accent);
}

.fm-adv-toggle__chevron {
  display: inline-block;
  font-size: 10px;
  font-style: normal;
  transform: rotate(0deg);
  transition: transform 0.18s ease;
}

.fm-adv-toggle__chevron.is-open {
  transform: rotate(180deg);
}

.fm-adv {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border: 1px dashed var(--fm-border-strong);
  border-radius: 8px;
  background: var(--fm-subtle);
}

.fm-adv__row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 18px;
  align-items: center;
}

.fm-adv__item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fm-adv__item--grow {
  flex: 1;
  min-width: 200px;
}

.fm-adv__item--grow :deep(.n-slider) {
  flex: 1;
  min-width: 120px;
}

.fm-adv__label {
  flex-shrink: 0;
  color: var(--fm-muted);
  font-size: 12px;
}

/* 统计条 */
.fm-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--fm-accent-soft);
  border: 1px solid var(--fm-accent-border);
  color: var(--fm-text);
  font-size: 12px;
}

.fm-stats__item strong {
  color: var(--fm-accent);
  font-weight: 700;
}

.fm-stats__item code {
  font-size: 11px;
  color: var(--fm-accent);
}

.fm-stats__item--tag {
  padding: 0 8px;
  border-radius: 4px;
  background: var(--fm-bg);
  border: 1px solid var(--fm-accent-border);
}

.fm-stats__item--ellipsis {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 结果列表 */
.fm-results {
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}

.fm-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fm-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background: var(--fm-bg);
  border: 1px solid var(--fm-border);
  border-radius: 10px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.fm-item:hover {
  border-color: var(--fm-accent-border);
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
}

.fm-item__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fm-item__rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 6px;
  background: var(--fm-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.fm-item__score {
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--fm-subtle);
}

.fm-item__score-bar {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.45), var(--fm-accent));
  transition: width 0.3s ease;
}

.fm-item__score-num {
  min-width: 40px;
  color: var(--fm-accent);
  font-size: 12px;
  font-weight: 600;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.fm-item__rerank {
  color: #d97706;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.fm-item__actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  margin-left: auto;
}

.fm-item__title {
  color: var(--fm-text-strong);
  font-size: 13px;
  font-weight: 600;
}

.fm-item__content {
  color: var(--fm-text);
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.fm-item__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.fm-item__meta-tag {
  padding: 1px 8px;
  border-radius: 4px;
  background: var(--fm-subtle);
  border: 1px solid var(--fm-border);
  color: var(--fm-muted);
  font-size: 11px;
}

/* 文档查看 - 统计条 */
.fm-doc__stats {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--fm-subtle);
  border: 1px solid var(--fm-border);
  border-radius: 12px;
}

.fm-doc__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 76px;
  padding: 6px 14px;
  background: var(--fm-bg);
  border: 1px solid var(--fm-border);
  border-radius: 8px;
}

.fm-doc__stat-num {
  color: var(--fm-text-strong);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.fm-doc__stat-label {
  margin-top: 2px;
  color: var(--fm-muted);
  font-size: 11px;
}

.fm-doc__stat-actions {
  display: flex;
  align-items: center;
  margin-left: auto;
}

/* 文档原文（纸张式） */
.fm-doc__paper {
  border: 1px solid var(--fm-border);
  border-radius: 12px;
  background: var(--fm-bg);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.fm-doc__raw {
  margin: 0;
  padding: 20px 24px;
  max-height: 58vh;
  overflow: auto;
  color: var(--fm-text);
  font-size: 13.5px;
  line-height: 1.85;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 分块卡片（两列网格） */
.fm-chunk-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.fm-chunk {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: var(--fm-bg);
  border: 1px solid var(--fm-border);
  border-radius: 12px;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.fm-chunk:hover {
  border-color: var(--fm-accent-border);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}

.fm-chunk__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fm-chunk__badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--fm-accent-soft);
  border: 1px solid var(--fm-accent-border);
  color: var(--fm-accent);
  font-size: 12px;
  font-weight: 600;
}

.fm-chunk__tokens {
  color: var(--fm-muted);
  font-size: 11px;
}

.fm-chunk__actions {
  margin-left: auto;
  flex-shrink: 0;
}

.fm-chunk__content {
  max-height: 240px;
  overflow: auto;
  padding-right: 4px;
  color: var(--fm-text);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.fm-chunk__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px dashed var(--fm-border);
}

.fm-chunk__tag {
  display: inline-flex;
  align-items: center;
  max-width: 220px;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--fm-subtle);
  border: 1px solid var(--fm-border);
  color: var(--fm-muted);
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fm-chunk__tag--title {
  font-family: inherit;
}

@media (max-width: 900px) {
  .fm-chunk-grid {
    grid-template-columns: 1fr;
  }
}

/* 空态 / 提示 */
.fm-empty,
.fm-hint {
  padding: 28px 0;
  text-align: center;
  color: var(--fm-muted);
  font-size: 13px;
}

.fm-hint {
  border: 1px dashed var(--fm-border-strong);
  border-radius: 10px;
}

@media (max-width: 1120px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-list-panel,
  .kb-detail-panel {
    min-height: auto;
  }

  .kb-list {
    max-height: 360px;
  }
}
</style>

<style>
/* forge 调试弹窗（检索调试 / 查看文档）暗色适配。
   n-modal 内容 teleport 到 body；且本工程 vue 编译器对 scoped 内 `:global(.dark) .x` 编译异常
   （变量规则退化为 `.dark {}` 特异性不足、普通规则被整条丢弃），故暗色覆盖放全局作用域，
   用 `.dark` + `.forge-debug-modal` 祖先双重门控保证特异性与正确性。 */
.forge-debug-modal .fm-hl {
  padding: 0 2px;
  border-radius: 3px;
  background: rgba(37, 99, 235, 0.15);
  color: inherit;
  font-style: normal;
}

.dark .forge-debug-modal .fm {
  --fm-bg: #151f2d;
  --fm-subtle: #1a2635;
  --fm-border: #2c3a4d;
  --fm-border-strong: #3a4d63;
  --fm-text: #cbd5e1;
  --fm-text-strong: #e2e8f0;
  --fm-muted: #7b8ba1;
  --fm-accent: #3b82f6;
  --fm-accent-soft: #1d3352;
  --fm-accent-border: #2c4a70;
}

.dark .forge-debug-modal .fm-hl {
  background: rgba(96, 165, 250, 0.22);
}

.dark .forge-debug-modal .fm-mode--active {
  background: var(--fm-accent-soft);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}

.dark .forge-debug-modal .fm-item:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
}

.dark .forge-debug-modal .fm-chunk:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
}

.dark .forge-debug-modal .fm-chunk__badge {
  background: var(--fm-accent-soft);
  border-color: var(--fm-accent-border);
  color: var(--fm-accent);
}

.dark .forge-debug-modal .fm-doc__paper {
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.dark .forge-debug-modal .fm-item__score-bar {
  background: linear-gradient(90deg, rgba(96, 165, 250, 0.4), var(--fm-accent));
}

.dark .forge-debug-modal .fm-item__rerank {
  color: #fbbf24;
}
</style>
