<template>
  <div class="dataset-studio">
    <div class="dataset-workspace">
      <aside class="workspace-sidebar">
        <div class="sidebar-head">
          <h3>数据集分类</h3>
          <n-button type="primary" secondary @click="goToCategoryManage">
            分类管理
          </n-button>
        </div>

        <div class="sidebar-shortcuts">
          <button
            class="scope-chip"
            :class="{ active: activeCategoryScope === 'all' }"
            type="button"
            @click="selectAllCategories"
          >
            全部数据集
          </button>
          <button
            class="scope-chip"
            :class="{ active: activeCategoryScope === 'uncategorized' }"
            type="button"
            @click="selectUncategorized"
          >
            未分类
          </button>
        </div>

        <NInput
          v-model:value="categoryKeyword"
          placeholder="搜索分类名称或编码"
          clearable
          class="category-search"
        >
          <template #prefix>
            <i class="i-material-symbols:search-rounded" />
          </template>
        </NInput>

        <div class="category-tree-shell">
          <n-empty v-if="categoryTreeNodes.length === 0" description="暂无分类，请前往分类管理页配置" size="small" />
          <n-tree
            v-else
            block-line
            :data="categoryTreeNodes"
            :default-expand-all="true"
            :selected-keys="selectedTreeKeys"
            @update:selected-keys="handleCategoryTreeSelect"
          />
        </div>

        <div v-if="selectedCategoryNode" class="category-detail-card">
          <div class="category-detail-top">
            <div>
              <div class="category-detail-name">
                {{ selectedCategoryNode.categoryName }}
              </div>
              <div class="category-detail-code">
                {{ selectedCategoryNode.categoryCode }}
              </div>
            </div>
            <DictTag
              :options="statusOptions"
              :value="selectedCategoryNode.status"
              size="small"
              :bordered="false"
              force-tag
            />
          </div>
          <p class="category-detail-desc">
            {{ selectedCategoryNode.description || '当前分类暂无补充说明。' }}
          </p>
        </div>
      </aside>

      <section class="workspace-main">
        <div class="main-toolbar">
          <div class="toolbar-title-row">
            <h3>数据集列表</h3>
            <div class="toolbar-title-meta">
              <span class="toolbar-scope">{{ activeCategoryScopeLabel }}</span>
              <n-button @click="handleResetFilters">
                重置筛选
              </n-button>
              <n-button type="primary" @click="handleAddDataset">
                新增数据集
              </n-button>
            </div>
          </div>

          <div class="toolbar-filters">
            <NInput
              v-model:value="queryForm.datasetName"
              class="toolbar-filter toolbar-filter--keyword"
              clearable
              placeholder="搜索数据集名称"
              @keydown.enter="applySearch"
            >
              <template #prefix>
                <i class="i-material-symbols:search-rounded" />
              </template>
            </NInput>
            <NSelect
              v-model:value="queryForm.connectionId"
              class="toolbar-filter"
              clearable
              filterable
              placeholder="数据连接"
              :options="connectionOptions"
            />
            <NSelect
              v-model:value="queryForm.datasetType"
              class="toolbar-filter"
              clearable
              placeholder="数据集类型"
              :options="datasetTypeOptions"
            />
            <NSelect
              v-model:value="queryForm.publishStatus"
              class="toolbar-filter"
              clearable
              placeholder="发布状态"
              :options="publishStatusOptions"
            />
            <n-button type="primary" @click="applySearch">
              搜索
            </n-button>
          </div>
        </div>

        <AiCrudPage
          ref="crudRef"
          class="dataset-crud"
          :api-config="{
            list: 'get@/data/dataset/page',
            detail: 'get@/data/dataset/:id',
            add: 'post@/data/dataset',
            update: 'put@/data/dataset',
            delete: 'delete@/data/dataset/:id',
          }"
          :show-search="false"
          :hide-toolbar="true"
          :columns="tableColumns"
          :edit-schema="editSchema"
          :before-render-form="beforeRenderForm"
          :before-render-detail="beforeRenderDetail"
          :before-submit="beforeSubmit"
          :hide-modal-footer="true"
          row-key="id"
          :load-detail-on-edit="true"
          :striped="false"
          :bordered="false"
          :scroll-x="1700"
          :edit-grid-cols="12"
          edit-label-placement="top"
          edit-form-class="data-dataset-edit-form"
          modal-type="modal"
          modal-width="min(1480px, calc(100vw - 32px))"
          add-button-text="新增数据集"
          @modal-close="handleDatasetModalClose"
        >
          <template #form-datasetEditor="{ formData, updateValue }">
            <div class="dataset-editor-page">
              <header class="dataset-editor-header">
                <div class="dataset-editor-icon">
                  <i class="i-material-symbols:database-outline" />
                </div>
                <div class="dataset-editor-heading">
                  <button class="dataset-editor-breadcrumb" type="button" @click="crudRef?.closeModal()">
                    <span>数据资产管理</span>
                    <i>/</i>
                    <span>数据集定义</span>
                  </button>
                  <div class="dataset-editor-title-row">
                    <h2>{{ formData.datasetName || getDatasetTypeLabel(formData.datasetType) }}</h2>
                    <span class="dataset-status-tag">
                      {{ getPublishStatusLabel(formData.publishStatus) }}
                    </span>
                  </div>
                  <div class="dataset-editor-meta">
                    创建于 {{ formatDatasetDate(formData.createTime) }}
                    <span>|</span>
                    更新于 {{ formatDatasetDate(formData.updateTime) }}
                    <span>|</span>
                    创建人：{{ getDatasetCreatorLabel(formData) }}
                  </div>
                </div>

                <div class="dataset-editor-actions">
                  <n-button
                    type="primary"
                    :disabled="isFormReadOnly"
                    @click="crudRef?.submitForm()"
                  >
                    保存
                  </n-button>
                  <n-button
                    v-if="formData.datasetType === 'SQL'"
                    :loading="sqlPreviewLoading"
                    @click="handlePreviewSql(formData, false)"
                  >
                    预览SQL
                  </n-button>
                  <n-button @click="crudRef?.closeModal()">
                    返回
                  </n-button>
                </div>
              </header>

              <div
                class="dataset-editor-steps"
                :style="{ '--dataset-step-index': currentStep - 1 }"
              >
                <button
                  v-for="(step, index) in stepDefinitions"
                  :key="step.label"
                  class="dataset-editor-step"
                  :class="{
                    'is-active': currentStep === index + 1,
                    'is-completed': currentStep > index + 1,
                  }"
                  type="button"
                  @click="setEditorStep(index + 1, true)"
                >
                  <span v-if="index > 0" class="dataset-editor-step__line" />
                  <span class="dataset-editor-step__content">
                    <span class="dataset-editor-step__index">{{ index + 1 }}</span>
                    <span class="dataset-editor-step__text">
                      <span class="dataset-editor-step__label">{{ step.label }}</span>
                      <span class="dataset-editor-step__caption">{{ step.caption }}</span>
                    </span>
                  </span>
                </button>
              </div>

              <div class="dataset-editor-grid">
                <section class="dataset-edit-panel dataset-edit-panel--basic" data-step-section="1">
                  <div class="panel-section-head">
                    <h3>基础信息</h3>
                  </div>

                  <div class="dataset-form-grid">
                    <label class="dataset-field dataset-field--required">
                      <span>数据集名称</span>
                      <NInput
                        :value="formData.datasetName"
                        :disabled="isFormReadOnly"
                        maxlength="64"
                        show-count
                        placeholder="请输入数据集名称"
                        @update:value="value => updateDatasetFormField(formData, 'datasetName', value, updateValue)"
                      />
                    </label>
                    <label class="dataset-field dataset-field--required">
                      <span>数据集编码</span>
                      <NInput
                        :value="formData.datasetCode"
                        :disabled="isFormReadOnly"
                        placeholder="请输入数据集编码"
                        @update:value="value => updateDatasetFormField(formData, 'datasetCode', value, updateValue)"
                      />
                    </label>
                    <label class="dataset-field">
                      <span>所属目录</span>
                      <n-tree-select
                        :value="formData.categoryId"
                        :options="categoryTreeSelectOptions"
                        :disabled="isFormReadOnly"
                        clearable
                        default-expand-all
                        placeholder="请选择业务分类"
                        @update:value="value => updateDatasetFormField(formData, 'categoryId', value, updateValue)"
                      />
                    </label>
                    <label class="dataset-field dataset-field--required">
                      <span>数据源</span>
                      <div class="data-source-select">
                        <i class="data-source-status-dot" />
                        <NSelect
                          :value="formData.connectionId"
                          :options="connectionOptions"
                          :disabled="isFormReadOnly"
                          filterable
                          clearable
                          placeholder="请选择数据连接"
                          @update:value="value => handleConnectionChange(value, formData, updateValue)"
                        />
                      </div>
                    </label>
                    <div class="dataset-field dataset-field--required">
                      <span>数据集类型</span>
                      <div class="dataset-type-segment" :class="{ 'is-disabled': isFormReadOnly }">
                        <button
                          v-for="option in datasetTypeOptions"
                          :key="option.value"
                          class="dataset-type-option"
                          :class="{ 'is-active': formData.datasetType === option.value }"
                          type="button"
                          :disabled="isFormReadOnly"
                          @mousedown.stop.prevent
                          @click.stop.prevent="handleDatasetTypeChange(option.value, formData, updateValue)"
                        >
                          {{ option.label }}
                        </button>
                      </div>
                    </div>
                    <label class="dataset-field">
                      <span>可用状态</span>
                      <n-radio-group
                        :value="formData.status"
                        :disabled="isFormReadOnly"
                        @update:value="value => updateDatasetFormField(formData, 'status', value, updateValue)"
                      >
                        <n-radio-button
                          v-for="option in statusOptions"
                          :key="option.value"
                          :value="option.value"
                        >
                          {{ option.label }}
                        </n-radio-button>
                      </n-radio-group>
                    </label>
                    <label v-if="formData.datasetType === 'TABLE'" class="dataset-field dataset-field--wide dataset-field--required">
                      <span>数据表</span>
                      <NSelect
                        :value="formData.tableName"
                        :options="tableOptions"
                        :loading="tableLoading"
                        :disabled="isFormReadOnly"
                        filterable
                        clearable
                        placeholder="请先选择数据连接，再选择数据表"
                        @update:value="value => handleTableNameChange(value, formData, updateValue)"
                      />
                    </label>
                    <label class="dataset-field dataset-field--wide">
                      <span>描述</span>
                      <NInput
                        :value="formData.description"
                        type="textarea"
                        :disabled="isFormReadOnly"
                        :autosize="{ minRows: 3, maxRows: 5 }"
                        maxlength="200"
                        show-count
                        placeholder="请输入数据集描述"
                        @update:value="value => updateDatasetFormField(formData, 'description', value, updateValue)"
                      />
                    </label>
                    <div class="dataset-field dataset-field--wide">
                      <span>标签</span>
                      <div class="dataset-tag-list">
                        <span
                          v-for="tag in getDatasetTagLabels(formData)"
                          :key="tag"
                          class="dataset-soft-tag"
                        >
                          {{ tag }}
                        </span>
                        <button class="dataset-text-action" type="button" disabled>
                          + 添加标签
                        </button>
                      </div>
                    </div>
                  </div>
                </section>

                <section
                  class="dataset-edit-panel dataset-edit-panel--sql"
                  :class="{ 'is-table-mode': formData.datasetType !== 'SQL' }"
                >
                  <div class="panel-section-head">
                    <h3>{{ formData.datasetType === 'SQL' ? 'SQL编辑器 + 预览结果' : '来源表结构' }}</h3>
                  </div>

                  <div v-if="formData.datasetType === 'SQL'" class="sql-workbench">
                    <div class="sql-editor-shell">
                      <SqlEditor
                        class="dataset-sql-editor"
                        :value="formData.sqlText"
                        :readonly="isFormReadOnly"
                        theme="light"
                        show-fullscreen
                        placeholder="SELECT order_id, order_time, customer_name, amount, status FROM orders WHERE order_time >= :start_time AND order_time < :end_time AND status = :status LIMIT :limit"
                        @update:value="value => updateDatasetFormField(formData, 'sqlText', value, updateValue)"
                      />
                    </div>
                    <div class="sql-preview-shell">
                      <div class="sql-workbench-toolbar">
                        <span>预览结果（前5行）</span>
                        <n-button
                          size="small"
                          :loading="sqlPreviewLoading"
                          :disabled="isFormReadOnly"
                          @click="handlePreviewSql(formData, false)"
                        >
                          刷新预览
                        </n-button>
                      </div>
                      <n-data-table
                        v-if="sqlPreviewColumns.length"
                        size="small"
                        :columns="sqlPreviewColumns"
                        :data="sqlPreviewRows"
                        :loading="sqlPreviewLoading"
                        :pagination="false"
                        :scroll-x="sqlPreviewScrollX"
                        max-height="274px"
                      />
                      <n-empty
                        v-else
                        class="sql-preview-empty"
                        description="暂无预览数据，点击预览SQL获取前5行"
                        size="small"
                      />
                      <div class="sql-preview-note">
                        以上为示例数据，实际结果以运行时为准
                      </div>
                    </div>
                  </div>

                  <div v-else class="table-source-panel">
                    <div class="table-source-summary">
                      <div class="table-source-card">
                        <span>数据连接</span>
                        <strong>{{ formData.connectionId ? getConnectionName(formData.connectionId) : '待选择' }}</strong>
                      </div>
                      <div class="table-source-card">
                        <span>来源数据表</span>
                        <strong>{{ formData.tableName || '待选择' }}</strong>
                      </div>
                      <div class="table-source-card">
                        <span>字段来源</span>
                        <strong>{{ getRowScopeFieldSourceLabel(formData) }}</strong>
                      </div>
                    </div>

                    <div class="table-source-fields">
                      <div class="table-source-fields__head">
                        <span>字段结构</span>
                        <small>{{ getRowScopeFieldOptions(formData).length }} 个字段</small>
                      </div>
                      <div v-if="getRowScopeFieldOptions(formData).length" class="table-source-field-list">
                        <span
                          v-for="field in getRowScopeFieldOptions(formData).slice(0, 18)"
                          :key="field.value"
                          class="table-source-field-chip"
                        >
                          {{ field.label }}
                        </span>
                        <span
                          v-if="getRowScopeFieldOptions(formData).length > 18"
                          class="table-source-field-chip table-source-field-chip--more"
                        >
                          +{{ getRowScopeFieldOptions(formData).length - 18 }}
                        </span>
                      </div>
                      <n-empty
                        v-else
                        size="small"
                        description="暂无字段结构，请先选择数据表并刷新来源字段"
                      />
                    </div>

                    <div class="table-source-actions">
                      <n-button
                        secondary
                        :disabled="isFormReadOnly || !formData.connectionId || !formData.tableName"
                        :loading="rowScopeTableFieldLoading"
                        @click="loadRowScopeTableFields(formData, { force: true })"
                      >
                        刷新来源字段
                      </n-button>
                    </div>
                  </div>
                </section>

                <section class="dataset-edit-panel dataset-edit-panel--params" data-step-section="2">
                  <div class="panel-section-head panel-section-head--inline">
                    <h3>查询参数定义</h3>
                    <n-popover trigger="click" placement="bottom-end" :width="320">
                      <template #trigger>
                        <button class="panel-inline-indicator panel-inline-indicator--button" type="button">
                          参数预览
                        </button>
                      </template>
                      <div class="param-preview-popover">
                        <div v-if="getParamPreviewRows(formData).length" class="param-preview-list">
                          <div
                            v-for="(param, index) in getParamPreviewRows(formData)"
                            :key="`${param.paramName || param.label || param.fieldName}-${index}`"
                            class="param-preview-row"
                          >
                            <strong>{{ param.paramName || '-' }}</strong>
                            <span>{{ getParamPreviewDescription(param, formData.datasetType) }}</span>
                          </div>
                        </div>
                        <n-empty v-else size="small" description="暂无查询参数" />
                      </div>
                    </n-popover>
                  </div>
                  <DatasetParamSchemaEditor
                    :model-value="formData.paramSchemaJson || []"
                    :readonly="isFormReadOnly"
                    :dataset-type="formData.datasetType"
                    :connection-id="formData.connectionId"
                    :table-name="formData.tableName"
                    :sql-text="formData.sqlText"
                    @update:model-value="value => formData.paramSchemaJson = value"
                  />
                </section>

                <section class="dataset-edit-panel dataset-edit-panel--settings" data-step-section="3">
                  <div class="panel-section-head">
                    <h3>执行设置</h3>
                  </div>
                  <div class="execution-settings">
                    <div class="setting-row">
                      <div class="setting-row__head">
                        <span>最大返回行数</span>
                        <strong>{{ formData.maxRows || 10000 }}</strong>
                      </div>
                      <div class="setting-row__control">
                        <n-slider
                          :value="formData.maxRows || 10000"
                          :disabled="isFormReadOnly"
                          :min="100"
                          :max="1000000"
                          :step="100"
                          @update:value="value => updateDatasetFormField(formData, 'maxRows', value, updateValue)"
                        />
                        <n-input-number
                          :value="formData.maxRows"
                          :disabled="isFormReadOnly"
                          :min="100"
                          :max="1000000"
                          :step="100"
                          @update:value="value => updateDatasetFormField(formData, 'maxRows', value, updateValue)"
                        />
                      </div>
                    </div>
                    <div class="setting-row">
                      <div class="setting-row__head">
                        <span>查询超时时间</span>
                        <strong>{{ formData.timeoutSeconds || 60 }} 秒</strong>
                      </div>
                      <div class="setting-row__control">
                        <n-slider
                          :value="formData.timeoutSeconds || 60"
                          :disabled="isFormReadOnly"
                          :min="1"
                          :max="1800"
                          :step="1"
                          @update:value="value => updateDatasetFormField(formData, 'timeoutSeconds', value, updateValue)"
                        />
                        <n-input-number
                          :value="formData.timeoutSeconds"
                          :disabled="isFormReadOnly"
                          :min="1"
                          :max="1800"
                          @update:value="value => updateDatasetFormField(formData, 'timeoutSeconds', value, updateValue)"
                        />
                      </div>
                    </div>
                    <label class="dataset-field dataset-field--full">
                      <span>缓存策略</span>
                      <n-radio-group
                        :value="formData.cacheEnabled === 1 ? 1 : 0"
                        :disabled="isFormReadOnly"
                        @update:value="value => handleCacheStrategyChange(value, formData, updateValue)"
                      >
                        <n-radio-button :value="0">
                          不缓存
                        </n-radio-button>
                        <n-radio-button :value="1">
                          按时间缓存
                        </n-radio-button>
                        <n-radio-button :value="2" disabled>
                          按依赖缓存
                        </n-radio-button>
                      </n-radio-group>
                    </label>
                    <label v-if="formData.cacheEnabled === 1" class="dataset-field">
                      <span>缓存时长(秒)</span>
                      <n-input-number
                        :value="formData.cacheTtlSeconds"
                        :disabled="isFormReadOnly"
                        :min="1"
                        :max="86400"
                        @update:value="value => updateDatasetFormField(formData, 'cacheTtlSeconds', value, updateValue)"
                      />
                    </label>
                    <label class="dataset-field">
                      <span>结果集编码</span>
                      <NSelect
                        :value="formData.__resultEncoding || 'UTF-8'"
                        :options="resultEncodingOptions"
                        :disabled="isFormReadOnly"
                        @update:value="value => formData.__resultEncoding = value"
                      />
                    </label>
                    <label class="setting-switch-row">
                      <span>允许导出</span>
                      <n-switch
                        :value="formData.__allowExport ?? true"
                        :disabled="isFormReadOnly"
                        @update:value="value => formData.__allowExport = value"
                      />
                    </label>
                  </div>
                </section>

                <section class="dataset-edit-panel dataset-edit-panel--access" data-step-section="4">
                  <div class="panel-section-head">
                    <h3>权限控制</h3>
                  </div>

                  <div class="access-control-block">
                    <div class="access-mode-row">
                      <span>访问范围</span>
                      <n-radio-group
                        :value="formData.accessMode"
                        :disabled="isFormReadOnly"
                        @update:value="value => handleAccessModeChange(value, formData, updateValue)"
                      >
                        <n-radio value="PUBLIC">
                          公开（所有已登录用户可访问）
                        </n-radio>
                        <n-radio value="PRIVATE">
                          私有（仅授权用户/用户组可访问）
                        </n-radio>
                      </n-radio-group>
                    </div>

                    <div class="row-permission-strip">
                      <div class="row-permission-title">
                        <span>行级权限规则</span>
                        <n-checkbox
                          :checked="isRowScopeEnabled(formData)"
                          :disabled="isFormReadOnly"
                          @update:checked="checked => handleRowScopeEnabledChange(checked, formData, updateValue)"
                        >
                          根据用户属性设置权限
                        </n-checkbox>
                      </div>
                      <div class="row-scope-expression">
                        {{ getRowScopeConditionPreview(formData) }}
                      </div>
                      <div class="row-permission-rules">
                        <span
                          v-for="rule in getRowScopeRules(formData).filter(item => item.attribute && item.field)"
                          :key="rule.__key"
                          class="rule-chip"
                        >
                          {{ getRowScopeRuleLabel(rule) }}
                        </span>
                        <span v-if="getRowScopeConfiguredCount(formData) === 0" class="rule-chip rule-chip--empty">
                          暂无规则
                        </span>
                        <button
                          class="dataset-text-action"
                          type="button"
                          :disabled="isFormReadOnly"
                          @click="addRowScopeRule(formData, updateValue)"
                        >
                          + 添加规则
                        </button>
                      </div>
                      <div v-if="getRowScopeRules(formData).length" class="row-scope-rule-mini-list">
                        <div
                          v-for="(rule, index) in getRowScopeRules(formData)"
                          :key="rule.__key || index"
                          class="row-scope-rule-mini"
                        >
                          <NSelect
                            :value="rule.attribute"
                            :options="getRowScopeAttributeOptions(formData, rule)"
                            :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                            clearable
                            placeholder="用户属性"
                            @update:value="value => handleRowScopeRuleAttributeChange(formData, rule, value, updateValue)"
                          />
                          <span>=</span>
                          <NSelect
                            :value="rule.field"
                            :options="getRowScopeFieldOptions(formData)"
                            :loading="rowScopeTableFieldLoading"
                            :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                            clearable
                            filterable
                            placeholder="数据表字段"
                            @update:value="value => handleRowScopeRuleFieldChange(formData, rule, value, updateValue)"
                          />
                          <n-button
                            quaternary
                            :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                            @click="removeRowScopeRule(formData, index, updateValue)"
                          >
                            <template #icon>
                              <i class="i-material-symbols:delete-outline-rounded" />
                            </template>
                          </n-button>
                        </div>
                      </div>
                    </div>

                    <div v-if="formData.accessMode === 'PRIVATE'" class="acl-editor acl-editor--compact">
                      <div class="acl-editor__toolbar">
                        <div>
                          <div class="acl-editor__title">
                            授权对象
                          </div>
                          <div class="acl-editor__hint">
                            已配置 {{ getAclCount(formData) }} 个授权主体。
                          </div>
                        </div>
                        <n-button
                          secondary
                          type="primary"
                          :disabled="isFormReadOnly || permissionOptionsLoading"
                          :loading="permissionOptionsLoading"
                          @click="addAclItem(formData, updateValue)"
                        >
                          <template #icon>
                            <i class="i-material-symbols:add-rounded" />
                          </template>
                          选择用户/用户组
                        </n-button>
                      </div>

                      <n-empty
                        v-if="!formData.aclItems || formData.aclItems.length === 0"
                        description="暂无授权主体"
                        size="small"
                      />
                      <div v-else class="acl-tag-list">
                        <span
                          v-for="(item, index) in formData.aclItems"
                          :key="item.__key || `${item.subjectType || 'ACL'}-${index}`"
                          class="acl-tag"
                        >
                          {{ getAclItemLabel(item) }}
                          <button
                            type="button"
                            :disabled="isFormReadOnly"
                            @click="removeAclItem(formData, index, updateValue)"
                          >
                            ×
                          </button>
                        </span>
                      </div>
                      <div v-if="formData.aclItems && formData.aclItems.length > 0" class="acl-rows">
                        <div
                          v-for="(item, index) in formData.aclItems"
                          :key="item.__key || `${item.subjectType || 'ACL'}-${index}`"
                          class="acl-row"
                        >
                          <NSelect
                            :value="item.subjectType"
                            :options="aclSubjectTypeOptions"
                            :disabled="isFormReadOnly"
                            :to="false"
                            @update:value="value => handleAclSubjectTypeChange(item, value, updateValue)"
                          />
                          <n-tree-select
                            v-if="item.subjectType === 'ORG'"
                            :value="item.subjectId"
                            :options="getAclOrgOptions(item)"
                            :disabled="isFormReadOnly || permissionOptionsLoading"
                            :loading="permissionOptionsLoading"
                            :to="false"
                            :virtual-scroll="false"
                            clearable
                            filterable
                            default-expand-all
                            placeholder="选择组织"
                            @update:value="value => handleAclSubjectIdChange(item, value, updateValue)"
                          />
                          <NSelect
                            v-else
                            :value="item.subjectId"
                            :options="getAclSubjectOptions(item)"
                            :disabled="isFormReadOnly || permissionOptionsLoading"
                            :loading="permissionOptionsLoading"
                            :to="false"
                            :virtual-scroll="false"
                            clearable
                            filterable
                            placeholder="选择授权主体"
                            @update:value="value => handleAclSubjectIdChange(item, value, updateValue)"
                          />
                          <NSelect
                            :value="item.accessLevel"
                            :options="accessLevelOptions"
                            :disabled="isFormReadOnly"
                            :to="false"
                            placeholder="权限级别"
                            @update:value="value => handleAclAccessLevelChange(item, value, updateValue)"
                          />
                          <n-button
                            quaternary
                            type="error"
                            :disabled="isFormReadOnly"
                            @click="removeAclItem(formData, index, updateValue)"
                          >
                            <template #icon>
                              <i class="i-material-symbols:delete-outline-rounded" />
                            </template>
                          </n-button>
                        </div>
                      </div>
                    </div>
                  </div>
                </section>

                <section class="dataset-edit-panel dataset-edit-panel--info">
                  <div class="panel-section-head">
                    <h3>数据集信息</h3>
                  </div>
                  <dl class="dataset-info-grid">
                    <div>
                      <dt>数据集ID</dt>
                      <dd>{{ formData.id || '保存后生成' }}</dd>
                    </div>
                    <div>
                      <dt>数据集类型</dt>
                      <dd>{{ getDatasetTypeLabel(formData.datasetType) }}</dd>
                    </div>
                    <div>
                      <dt>数据源</dt>
                      <dd>{{ formData.connectionId ? getConnectionName(formData.connectionId) : '-' }}</dd>
                    </div>
                    <div>
                      <dt>创建人</dt>
                      <dd>{{ getDatasetCreatorLabel(formData) }}</dd>
                    </div>
                    <div>
                      <dt>创建时间</dt>
                      <dd>{{ formatDatasetDate(formData.createTime) }}</dd>
                    </div>
                    <div>
                      <dt>更新时间</dt>
                      <dd>{{ formatDatasetDate(formData.updateTime) }}</dd>
                    </div>
                    <div>
                      <dt>更新人</dt>
                      <dd>{{ getDatasetUpdaterLabel(formData) }}</dd>
                    </div>
                    <div>
                      <dt>版本号</dt>
                      <dd>
                        {{ getDatasetVersionLabel(formData) }}
                        <span class="dataset-current-version">当前版本</span>
                      </dd>
                    </div>
                  </dl>
                </section>
              </div>
            </div>
          </template>

          <template #form-stepIndicator>
            <div class="step-shell" :class="{ 'is-readonly': isFormReadOnly }" :style="stepShellStyle">
              <div class="step-shell__header">
                <div class="step-shell__intro">
                  <p class="step-shell__eyebrow">
                    Dataset Editing Flow
                  </p>
                  <div class="step-shell__title-row">
                    <h3 class="step-shell__title">
                      {{ currentStepMeta.title }}
                    </h3>
                    <span class="step-shell__progress">
                      STEP {{ currentStep }}/{{ totalSteps }}
                    </span>
                  </div>
                  <p class="step-shell__description">
                    {{ currentStepMeta.description }}
                  </p>
                </div>
                <span class="step-shell__status" :class="{ 'is-readonly': isFormReadOnly }">
                  {{ formModeLabel }}
                </span>
              </div>

              <div class="step-progress" :style="stepProgressWrapStyle">
                <div :style="stepProgressBaseLineStyle" />
                <div :style="stepProgressActiveLineStyle" />
                <template v-for="(step, index) in stepDefinitions" :key="step.label">
                  <div
                    class="step-node"
                    :style="getStepNodeInlineStyle(index)"
                    :class="{
                      'is-active': currentStep === index + 1,
                      'is-completed': currentStep > index + 1,
                    }"
                  >
                    <div class="step-circle">
                      <span v-if="currentStep <= index + 1">{{ index + 1 }}</span>
                      <i v-else class="i-material-symbols:check-rounded" />
                    </div>
                    <div class="step-node__meta">
                      <div class="step-label">
                        {{ step.label }}
                      </div>
                      <div class="step-caption">
                        {{ step.caption }}
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </template>

          <template #form-stepNavigation="{ formData }">
            <div class="step-navigation-wrapper" :style="stepNavigationWrapperStyle">
              <div class="step-nav-actions" :style="stepNavigationActionsStyle">
                <div class="step-navigation-meta" :style="stepNavigationMetaStyle">
                  <span class="step-navigation-meta__label">当前步骤</span>
                  <strong class="step-navigation-meta__title">{{ currentStepMeta.label }}</strong>
                  <span class="step-navigation-meta__desc">{{ stepNavigationNote }}</span>
                </div>
                <n-button
                  quaternary
                  @click="crudRef?.closeModal()"
                >
                  取消
                </n-button>
                <n-button
                  v-if="currentStep > 1"
                  quaternary
                  @click="goToPrevStep"
                >
                  <template #icon>
                    <i class="i-material-symbols:arrow-back-rounded" />
                  </template>
                  上一步
                </n-button>
                <n-button
                  v-if="currentStep < totalSteps"
                  type="primary"
                  @click="goToNextStep(formData)"
                >
                  <template #icon>
                    <i class="i-material-symbols:arrow-forward-rounded" />
                  </template>
                  下一步
                </n-button>
                <n-button
                  v-if="currentStep === totalSteps && !isFormReadOnly"
                  type="success"
                  @click="crudRef?.submitForm()"
                >
                  <template #icon>
                    <i class="i-material-symbols:save-rounded" />
                  </template>
                  保存数据集
                </n-button>
              </div>
            </div>
          </template>

          <template #form-sqlText="{ value, updateValue }">
            <SqlEditor
              :value="value"
              :readonly="isFormReadOnly"
              placeholder="SELECT id, name FROM table_name WHERE status = 1"
              @update:value="updateValue"
            />
          </template>

          <template #form-sqlPreviewAction="{ formData }">
            <div class="sql-preview-action">
              <n-button
                type="primary"
                secondary
                :disabled="isFormReadOnly"
                :loading="sqlPreviewLoading"
                @click="handlePreviewSql(formData)"
              >
                预览SQL
              </n-button>
              <n-text depth="3">
                仅执行并展示前 10 条数据，用于校验 SQL 语句
              </n-text>
            </div>
          </template>

          <template #form-sourceGuide="{ formData }">
            <div class="dataset-context-panel">
              <div class="context-panel__main">
                <div class="context-panel__eyebrow">
                  来源配置
                </div>
                <div class="context-panel__title">
                  先确定接入方式，再继续字段同步或 SQL 建模
                </div>
                <div class="context-panel__desc">
                  {{ getDatasetSourceGuide(formData) }}
                </div>
              </div>
              <div class="context-panel__facts">
                <div class="context-fact">
                  <span>数据连接</span>
                  <strong>{{ formData.connectionId ? getConnectionName(formData.connectionId) : '待选择' }}</strong>
                </div>
                <div class="context-fact">
                  <span>接入方式</span>
                  <strong>{{ getDatasetTypeLabel(formData.datasetType) }}</strong>
                </div>
                <div class="context-fact">
                  <span>来源对象</span>
                  <strong>{{ getDatasetSourceSubject(formData) }}</strong>
                </div>
              </div>
            </div>
          </template>

          <template #form-paramGuide="{ formData }">
            <div class="dataset-context-panel dataset-context-panel--muted">
              <div class="context-panel__main">
                <div class="context-panel__eyebrow">
                  条件设计
                </div>
                <div class="context-panel__title">
                  只保留真正会被报表和运行时消费的筛选条件
                </div>
                <div class="context-panel__desc">
                  {{ getDatasetParamGuide(formData) }}
                </div>
              </div>
              <div class="context-panel__facts">
                <div class="context-fact">
                  <span>当前模式</span>
                  <strong>{{ getDatasetTypeLabel(formData.datasetType) }}</strong>
                </div>
                <div class="context-fact">
                  <span>{{ formData.datasetType === 'SQL' ? '识别参数' : '字段准备' }}</span>
                  <strong>{{ getDatasetParamReadiness(formData) }}</strong>
                </div>
                <div class="context-fact">
                  <span>约束要求</span>
                  <strong>{{ getDatasetParamConstraint(formData) }}</strong>
                </div>
              </div>
            </div>
          </template>

          <template #form-paramSchemaJson="{ value, updateValue, formData }">
            <DatasetParamSchemaEditor
              :model-value="value || []"
              :readonly="isFormReadOnly"
              :dataset-type="formData.datasetType"
              :connection-id="formData.connectionId"
              :table-name="formData.tableName"
              :sql-text="formData.sqlText"
              @update:model-value="updateValue"
            />
          </template>

          <template #form-settingGuide="{ formData }">
            <div class="dataset-context-panel dataset-context-panel--compact">
              <div class="context-panel__main">
                <div class="context-panel__eyebrow">
                  运行建议
                </div>
                <div class="context-panel__title">
                  控制查询边界，保证运行稳定性
                </div>
                <div class="context-panel__desc">
                  最大返回行数和超时时间会直接影响下游报表查询体验与系统负载。
                </div>
              </div>
              <div class="context-panel__facts">
                <div class="context-fact">
                  <span>最大行数</span>
                  <strong>{{ formData.maxRows || 1000 }}</strong>
                </div>
                <div class="context-fact">
                  <span>超时时间</span>
                  <strong>{{ formData.timeoutSeconds || 15 }}s</strong>
                </div>
                <div class="context-fact context-fact--wide">
                  <span>治理建议</span>
                  <strong>{{ formData.datasetType === 'SQL' ? '复杂 SQL 建议收紧阈值' : '单表模式建议保持轻量' }}</strong>
                </div>
              </div>
              <div class="context-panel__footnote">
                {{ formData.datasetType === 'SQL' ? '复杂 SQL 建议收紧返回行数和超时时间，避免拖慢报表查询。' : '单表数据集建议保持轻量，先同步字段再逐步补充筛选条件。' }}
              </div>
            </div>
          </template>

          <template #form-accessPermissionConfig="{ formData, updateValue }">
            <div class="permission-panel">
              <div class="permission-panel__header">
                <div>
                  <div class="context-panel__eyebrow">
                    Access Control
                  </div>
                  <div class="permission-panel__title">
                    数据集访问权限
                  </div>
                  <div class="permission-panel__desc">
                    公开数据集保持现有使用方式；私有数据集只对创建人、管理员和授权主体可见可用。
                  </div>
                </div>
                <n-radio-group
                  :value="formData.accessMode"
                  :disabled="isFormReadOnly"
                  @update:value="value => handleAccessModeChange(value, formData, updateValue)"
                >
                  <n-radio-button
                    v-for="option in accessModeOptions"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </n-radio-button>
                </n-radio-group>
              </div>

              <div class="permission-facts">
                <div class="permission-fact">
                  <span>访问模式</span>
                  <strong>{{ getAccessModeLabel(formData.accessMode) }}</strong>
                </div>
                <div class="permission-fact">
                  <span>授权主体</span>
                  <strong>{{ getAclCount(formData) }} 个</strong>
                </div>
                <div class="permission-fact">
                  <span>默认兼容</span>
                  <strong>{{ formData.accessMode === 'PRIVATE' ? '按 ACL 校验' : '保持公开' }}</strong>
                </div>
              </div>

              <div v-if="formData.accessMode === 'PRIVATE'" class="acl-editor">
                <div class="acl-editor__toolbar">
                  <div>
                    <div class="acl-editor__title">
                      授权主体
                    </div>
                    <div class="acl-editor__hint">
                      支持角色、用户和组织授权，查询权限包含查看，管理权限包含查询。
                    </div>
                  </div>
                  <n-button
                    type="primary"
                    secondary
                    :disabled="isFormReadOnly || permissionOptionsLoading"
                    :loading="permissionOptionsLoading"
                    @click="addAclItem(formData, updateValue)"
                  >
                    <template #icon>
                      <i class="i-material-symbols:add-rounded" />
                    </template>
                    添加授权
                  </n-button>
                </div>

                <n-empty
                  v-if="!formData.aclItems || formData.aclItems.length === 0"
                  description="暂无授权主体，当前仅创建人和管理员可访问"
                  size="small"
                />
                <div v-else class="acl-rows">
                  <div
                    v-for="(item, index) in formData.aclItems"
                    :key="item.__key || `${item.subjectType || 'ACL'}-${index}`"
                    class="acl-row"
                  >
                    <NSelect
                      :value="item.subjectType"
                      :options="aclSubjectTypeOptions"
                      :disabled="isFormReadOnly"
                      :to="false"
                      @update:value="value => handleAclSubjectTypeChange(item, value, updateValue)"
                    />
                    <n-tree-select
                      v-if="item.subjectType === 'ORG'"
                      :value="item.subjectId"
                      :options="getAclOrgOptions(item)"
                      :disabled="isFormReadOnly || permissionOptionsLoading"
                      :loading="permissionOptionsLoading"
                      :to="false"
                      :virtual-scroll="false"
                      clearable
                      filterable
                      default-expand-all
                      placeholder="选择组织"
                      @update:value="value => handleAclSubjectIdChange(item, value, updateValue)"
                    />
                    <NSelect
                      v-else
                      :value="item.subjectId"
                      :options="getAclSubjectOptions(item)"
                      :disabled="isFormReadOnly || permissionOptionsLoading"
                      :loading="permissionOptionsLoading"
                      :to="false"
                      :virtual-scroll="false"
                      clearable
                      filterable
                      placeholder="选择授权主体"
                      @update:value="value => handleAclSubjectIdChange(item, value, updateValue)"
                    />
                    <NSelect
                      :value="item.accessLevel"
                      :options="accessLevelOptions"
                      :disabled="isFormReadOnly"
                      :to="false"
                      placeholder="权限级别"
                      @update:value="value => handleAclAccessLevelChange(item, value, updateValue)"
                    />
                    <n-button
                      quaternary
                      type="error"
                      :disabled="isFormReadOnly"
                      @click="removeAclItem(formData, index, updateValue)"
                    >
                      <template #icon>
                        <i class="i-material-symbols:delete-outline-rounded" />
                      </template>
                    </n-button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <template #form-rowScopeConfig="{ formData, updateValue }">
            <div class="permission-panel row-scope-panel">
              <div class="permission-panel__header">
                <div>
                  <div class="context-panel__eyebrow">
                    Row Scope
                  </div>
                  <div class="permission-panel__title">
                    行级权限设置
                  </div>
                  <div class="permission-panel__desc">
                    后端会按当前用户角色绑定的数据范围自动选择生效规则；这里仅维护用户属性与数据表字段的映射，不再手动选择区划范围。
                  </div>
                </div>
                <n-button
                  class="row-permission-add"
                  type="primary"
                  :disabled="isFormReadOnly"
                  @click="addRowScopeRule(formData, updateValue)"
                >
                  <template #icon>
                    <i class="i-material-symbols:add-rounded" />
                  </template>
                  添加
                </n-button>
              </div>

              <div class="permission-facts">
                <div class="permission-fact">
                  <span>权限来源</span>
                  <strong>角色绑定的数据范围</strong>
                </div>
                <div class="permission-fact">
                  <span>字段来源</span>
                  <strong>{{ getRowScopeFieldSourceLabel(formData) }}</strong>
                </div>
                <div class="permission-fact">
                  <span>映射规则</span>
                  <strong>{{ getRowScopeConfiguredCount(formData) }} 条已配置</strong>
                </div>
              </div>

              <div class="row-permission-switch">
                <n-checkbox
                  :checked="isRowScopeEnabled(formData)"
                  :disabled="isFormReadOnly"
                  @update:checked="checked => handleRowScopeEnabledChange(checked, formData, updateValue)"
                >
                  根据用户属性设置权限
                </n-checkbox>
                <span>
                  角色数据范围为“本部门 / 本人 / 行政区划”等模式时，会匹配下方对应字段；未配置的字段不会参与过滤。
                </span>
              </div>

              <n-alert
                v-if="formData.datasetType === 'SQL' && isRowScopeEnabled(formData)"
                type="warning"
                :show-icon="false"
                class="row-scope-alert"
              >
                SQL 数据集启用行权限时，需要在 SQL 的过滤位置预留 /*DATA_SCOPE*/ 占位符。
              </n-alert>

              <n-alert
                v-if="isRowScopeEnabled(formData) && getRowScopeFieldOptions(formData).length === 0"
                type="warning"
                :show-icon="false"
                class="row-scope-alert"
              >
                当前暂无可选字段，请先保存并同步字段；单表数据集也可以刷新来源表字段后再配置。
              </n-alert>

              <div class="row-scope-rule-builder" :class="{ 'is-disabled': !isRowScopeEnabled(formData) }">
                <div class="row-scope-rule-titlebar">
                  <div>
                    <div class="row-scope-rule-title">
                      权限设置
                    </div>
                    <div class="row-scope-rule-desc">
                      规则结构为：用户属性 = 数据表字段，条件关系用于多条规则的可读化拼接。
                    </div>
                  </div>
                  <n-button
                    secondary
                    :disabled="isFormReadOnly || formData.datasetType !== 'TABLE' || !formData.connectionId || !formData.tableName"
                    :loading="rowScopeTableFieldLoading"
                    @click="loadRowScopeTableFields(formData, { force: true })"
                  >
                    <template #icon>
                      <i class="i-material-symbols:refresh-rounded" />
                    </template>
                    刷新字段
                  </n-button>
                </div>

                <div class="row-scope-attribute-strip">
                  <div
                    v-for="option in rowScopeAttributeOptions"
                    :key="option.value"
                    class="row-scope-attribute-chip"
                  >
                    <span>{{ option.label }}</span>
                    <small>{{ option.caption }}</small>
                  </div>
                </div>

                <div class="row-scope-rule-header">
                  <span>用户属性</span>
                  <span />
                  <span>表字段</span>
                  <span>条件关系</span>
                  <span />
                </div>

                <n-empty
                  v-if="getRowScopeRules(formData).length === 0"
                  description="暂无行权限规则，点击右上角添加"
                  size="small"
                  class="row-scope-empty"
                />
                <div
                  v-for="(rule, index) in getRowScopeRules(formData)"
                  :key="rule.__key || index"
                  class="row-scope-rule-row"
                >
                  <NSelect
                    :value="rule.attribute"
                    :options="getRowScopeAttributeOptions(formData, rule)"
                    :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                    clearable
                    placeholder="选择用户属性"
                    @update:value="value => handleRowScopeRuleAttributeChange(formData, rule, value, updateValue)"
                  />
                  <div class="row-scope-equals">
                    =
                  </div>
                  <NSelect
                    :value="rule.field"
                    :options="getRowScopeFieldOptions(formData)"
                    :loading="rowScopeTableFieldLoading"
                    :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                    clearable
                    filterable
                    placeholder="选择数据表字段"
                    @update:value="value => handleRowScopeRuleFieldChange(formData, rule, value, updateValue)"
                  />
                  <NSelect
                    :value="rule.logic"
                    :options="rowScopeLogicOptions"
                    :disabled="isFormReadOnly || !isRowScopeEnabled(formData) || index === getRowScopeRules(formData).length - 1"
                    placeholder="关系"
                    @update:value="value => handleRowScopeRuleLogicChange(rule, value, updateValue)"
                  />
                  <n-button
                    quaternary
                    class="row-scope-delete"
                    :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                    @click="removeRowScopeRule(formData, index, updateValue)"
                  >
                    <template #icon>
                      <i class="i-material-symbols:delete-outline-rounded" />
                    </template>
                  </n-button>
                </div>

                <div class="row-scope-condition-preview">
                  {{ getRowScopeConditionPreview(formData) }}
                </div>
              </div>

              <div class="row-scope-remark">
                <label>备注</label>
                <NInput
                  :value="getRowScopeRemark(formData)"
                  type="textarea"
                  :disabled="isFormReadOnly || !isRowScopeEnabled(formData)"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  placeholder="记录该数据集行权限字段口径"
                  @update:value="value => handleRowScopeRemarkChange(formData, value, updateValue)"
                />
              </div>
            </div>
          </template>
        </AiCrudPage>
      </section>
    </div>

    <n-modal
      v-model:show="fieldModalVisible"
      preset="card"
      :title="fieldModalTitle"
      :style="{ width: 'min(1320px, calc(100vw - 32px))' }"
      :segmented="{ content: 'soft' }"
      :mask-closable="false"
    >
      <div class="field-config-modal">
        <div class="field-config-head">
          <div>
            <div class="field-config-title">
              字段配置台
            </div>
            <div class="field-config-desc">
              维护显示名称、标准类型、字段角色和扩展属性。筛选/展示开关不再外露，保持运行时默认可用。
            </div>
          </div>
          <div class="field-config-stats">
            <div v-for="item in fieldConfigStats" :key="item.label" class="field-config-stat">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>

        <n-alert v-if="fieldConfigReadonly" type="warning" :show-icon="false" class="field-config-alert">
          当前数据集已发布，字段配置处于只读状态。如需调整，请先下架数据集。
        </n-alert>

        <n-data-table
          class="field-config-table"
          :columns="fieldColumns"
          :data="fieldRows"
          :loading="fieldLoading"
          :pagination="{ pageSize: 10 }"
          :scroll-x="fieldTableScrollX"
          max-height="calc(100vh - 390px)"
          size="small"
          striped
        />
      </div>

      <template #footer>
        <div class="field-config-footer">
          <n-button @click="fieldModalVisible = false">
            关闭
          </n-button>
          <n-button
            v-if="currentFieldDataset?.publishStatus !== 1"
            secondary
            :loading="fieldLoading"
            @click="handleSyncCurrentFields"
          >
            同步字段
          </n-button>
          <n-button
            v-if="currentFieldDataset?.publishStatus !== 1"
            type="primary"
            :loading="fieldSaving"
            @click="handleSaveFieldConfig"
          >
            保存字段配置
          </n-button>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="sqlPreviewVisible"
      preset="card"
      title="SQL预览结果"
      style="width: 1000px"
      :segmented="{ content: 'soft' }"
    >
      <n-data-table
        :columns="sqlPreviewColumns"
        :data="sqlPreviewRows"
        :loading="sqlPreviewLoading"
        :pagination="{ pageSize: 10 }"
        :scroll-x="sqlPreviewScrollX"
        size="small"
      />
    </n-modal>
  </div>
</template>

<script>
import { datasetLocalComponents } from './datasetLocalComponents'
import { useDatasetPage } from './composables/useDatasetPage'

export default {
  name: 'DataDataset',
  components: {
    ...datasetLocalComponents,
  },
  setup(_props, { expose }) {
    return useDatasetPage(expose)
  },
}
</script>

<style scoped src="./dataset-shell.css"></style>
<style scoped src="./dataset-panels.css"></style>
