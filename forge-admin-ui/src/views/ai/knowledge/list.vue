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

<script>
import { knowledgeListLocalComponents } from './knowledgeListLocalComponents'
import { useKnowledgeList } from './composables/useKnowledgeList'

export default {
  name: 'KnowledgeList',
  components: {
    ...knowledgeListLocalComponents,
  },
  setup() {
    return useKnowledgeList()
  },
}
</script>

<style scoped src="./knowledgeList.css"></style>
