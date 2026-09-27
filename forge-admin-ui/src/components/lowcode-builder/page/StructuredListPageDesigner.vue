<template>
  <div class="structured-designer">
    <div class="structured-workspace" :class="{ 'config-expanded': configExpanded, 'config-collapsed': configCollapsed }">
      <div v-show="!configExpanded" class="structured-preview-column">
        <section class="surface-section crud-preview-section">
          <div class="section-head">
            <div>
              <div class="section-title">
                默认 CRUD 组件预览
              </div>
              <div class="section-desc">
                当前页面默认使用 AiCrudPage。右侧配置字段、排序、列样式后，会同步到自由画布里的 CRUD 组件。
              </div>
            </div>
            <n-space size="small" align="center">
              <NSwitch
                :value="searchZone?.enabled !== false"
                size="small"
                @update:value="patchZone('search', { enabled: $event })"
              />
              <NButton size="small" @click="resetSearchFields">
                恢复默认
              </NButton>
            </n-space>
          </div>

          <div
            class="crud-component-preview-frame"
            :class="{ 'tree-crud-preview': layoutType === 'tree-crud' }"
          >
            <GridBlockRenderer
              v-if="layoutType === 'tree-crud'"
              :block="treePreviewBlock"
              :fields="fields"
              :selected="false"
            />
            <GridBlockRenderer
              :block="crudPreviewBlock"
              :fields="fields"
              :selected="false"
            />
          </div>

          <div class="crud-preview-status">
            <span>排序：{{ defaultSortText }}</span>
            <span>查询字段：{{ searchFields.length }}</span>
            <span>列表列：{{ tableFields.length }}/{{ fields.length }}</span>
            <span v-if="layoutType === 'tree-crud'">模板：左树筛选右表</span>
            <NButton size="tiny" type="primary" secondary @click="openFieldPanel('table')">
              配置列表字段
            </NButton>
          </div>
        </section>
      </div>

      <button
        v-if="configCollapsed"
        type="button"
        class="config-collapsed-rail"
        title="展开 CRUD 配置"
        @click="configCollapsed = false"
      >
        <span>配置</span>
      </button>

      <aside v-else class="structured-config-panel">
        <div class="config-panel-head">
          <div>
            <div class="config-title">
              CRUD 快速配置
            </div>
            <div class="config-desc">
              按步骤配置默认 CRUD 组件，不需要理解内部 JSON。复杂页面布局请切回自由画布。
            </div>
          </div>
          <n-space size="small" align="center">
            <NButton size="small" secondary @click="configExpanded = !configExpanded">
              {{ configExpanded ? '收起配置' : '展开配置' }}
            </NButton>
            <NButton size="small" secondary @click="collapseConfigPanel">
              收起
            </NButton>
            <NButton size="small" type="primary" secondary @click="resetTableFields">
              恢复列
            </NButton>
          </n-space>
        </div>
        <div class="config-panel-search designer-panel-search">
          <NInput
            v-model:value="configKeyword"
            clearable
            size="small"
            placeholder="搜索配置项，例如：接口、弹窗、提交前、树、字段"
          >
            <template #prefix>
              <NIcon><SearchOutline /></NIcon>
            </template>
          </NInput>
        </div>
        <div class="config-scroll-body">
          <div class="config-step-list">
            <div class="config-step active">
              1. 选模板
            </div>
            <div class="config-step active">
              2. 配字段
            </div>
            <div class="config-step">
              3. 保存发布
            </div>
          </div>
          <section v-if="configSectionVisible(['字段配置入口', '字段', '查询字段', '列表字段', '列样式'])" class="config-section field-config-entry">
            <div class="config-section-title">
              字段配置入口
            </div>
            <div class="field-config-entry-actions">
              <NButton
                size="small"
                :type="activeFieldZone === 'search' ? 'primary' : 'default'"
                secondary
                @click="openFieldPanel('search')"
              >
                配置查询字段
              </NButton>
              <NButton
                size="small"
                :type="activeFieldZone === 'table' ? 'primary' : 'default'"
                secondary
                @click="openFieldPanel('table')"
              >
                配置列表字段 / 列样式
              </NButton>
            </div>
            <div class="config-section-tip">
              列表字段里可以配置列宽、固定列、文字颜色、点击跳转、跳转参数、名称字段和渲染方式。字典字段默认按“字典标签”展示，也可以手动切换。
            </div>
          </section>
          <section v-if="configSectionVisible(['基础显示', '标题', '主键', '排序', '行间距', '分页', '导入', '导出', '批量删除', '边框', '斑马纹'])" class="config-section">
            <div class="config-section-title">
              基础显示
            </div>
            <div class="config-form-grid">
              <label class="config-control">
                <span>组件标题</span>
                <NInput
                  :value="tableZone?.props?.title || '业务列表'"
                  size="small"
                  placeholder="列表标题"
                  @update:value="updateTableProp('title', $event)"
                />
              </label>
              <label class="config-control">
                <span>主键字段</span>
                <NSelect
                  :value="tableZone?.props?.rowKey || 'id'"
                  :options="sortFieldOptions"
                  size="small"
                  filterable
                  tag
                  @update:value="updateTableProp('rowKey', $event || 'id')"
                />
              </label>
              <label class="config-control">
                <span>默认排序字段</span>
                <NSelect
                  :value="tableZone?.props?.defaultSortField || 'id'"
                  :options="sortFieldOptions"
                  size="small"
                  filterable
                  @update:value="updateTableProp('defaultSortField', $event)"
                />
              </label>
              <label class="config-control">
                <span>排序方向</span>
                <NSelect
                  :value="tableZone?.props?.defaultSortOrder || 'desc'"
                  :options="sortOrderOptions"
                  size="small"
                  @update:value="updateTableProp('defaultSortOrder', $event)"
                />
              </label>
              <label class="config-control">
                <span>批量列对齐</span>
                <NSelect
                  :value="tableZone?.props?.globalAlign || 'left'"
                  :options="alignOptions"
                  size="small"
                  @update:value="applyTableGlobalAlign"
                />
              </label>
              <label class="config-control">
                <span>行间距</span>
                <NInputNumber
                  :value="tableZone?.props?.rowGap ?? 8"
                  size="small"
                  :min="0"
                  :max="32"
                  :step="2"
                  :show-button="false"
                  @update:value="updateTableRowGap"
                />
              </label>
              <label class="config-control">
                <span>表格尺寸</span>
                <NSelect
                  :value="tableZone?.props?.tableSize || 'medium'"
                  :options="sizeOptions"
                  size="small"
                  @update:value="updateTableProp('tableSize', $event || 'medium')"
                />
              </label>
              <label class="config-control">
                <span>展示模式</span>
                <NSelect
                  :value="tableZone?.props?.renderMode || 'table'"
                  :options="renderModeOptions"
                  size="small"
                  @update:value="updateTableProp('renderMode', $event || 'table')"
                />
              </label>
            </div>
            <div class="config-switch-grid">
              <label>
                <NSwitch
                  :value="searchZone?.enabled !== false"
                  size="small"
                  @update:value="patchZone('search', { enabled: $event })"
                />
                <span>搜索区</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.showPagination !== false"
                  size="small"
                  @update:value="updateTableProp('showPagination', $event)"
                />
                <span>分页</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.hideSelection !== true"
                  size="small"
                  @update:value="updateTableProp('hideSelection', !$event)"
                />
                <span>勾选列</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.showImport !== false"
                  size="small"
                  @update:value="updateTableProp('showImport', $event)"
                />
                <span>显示导入</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.showExport !== false"
                  size="small"
                  @update:value="updateTableProp('showExport', $event)"
                />
                <span>显示导出</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.enableCustomQuery !== false"
                  size="small"
                  @update:value="updateTableProp('enableCustomQuery', $event)"
                />
                <span>自定义查询</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.hideBatchDelete !== true"
                  size="small"
                  @update:value="updateTableProp('hideBatchDelete', !$event)"
                />
                <span>批量删除</span>
              </label>
              <label>
                <NSwitch
                  :value="!!tableZone?.props?.bordered"
                  size="small"
                  @update:value="updateTableProp('bordered', $event)"
                />
                <span>边框</span>
              </label>
              <label>
                <NSwitch
                  :value="!!tableZone?.props?.striped"
                  size="small"
                  @update:value="updateTableProp('striped', $event)"
                />
                <span>斑马纹</span>
              </label>
            </div>
          </section>
          <section v-if="configSectionVisible(['接口配置', '接口', 'api', '分页接口', '新增接口', '编辑接口', '删除接口', '列表数据字段', '总数字段', '真实接口预览'])" class="config-section">
            <div class="config-section-title">
              接口配置
            </div>
            <div class="config-section-tip">
              不填时使用业务对象默认接口；需要接已有后端接口时再配置这些地址。
            </div>
            <div class="config-form-grid one-col">
              <label class="config-control">
                <span>接口前缀</span>
                <NInput
                  :value="tableZone?.props?.api || defaultApiValues.api"
                  size="small"
                  :placeholder="defaultApiValues.api"
                  @update:value="updateTableProp('api', $event)"
                />
              </label>
              <div class="config-switch-grid two">
                <label>
                  <NSwitch
                    :value="tableZone?.props?.previewLiveData === true"
                    size="small"
                    @update:value="updateTableProp('previewLiveData', $event)"
                  />
                  <span>真实接口预览</span>
                </label>
              </div>
              <div class="config-section-tip">
                默认不请求真实接口；打开后中间预览会根据接口配置加载数据，适合验证接口字段和字典回显。
              </div>
              <label class="config-control">
                <span>分页接口</span>
                <NInput
                  :value="tableZone?.props?.listApi || defaultApiValues.listApi"
                  size="small"
                  :placeholder="defaultApiValues.listApi"
                  @update:value="updateTableProp('listApi', $event)"
                />
              </label>
              <div class="config-form-grid nested">
                <label class="config-control">
                  <span>详情接口</span>
                  <NInput
                    :value="tableZone?.props?.detailApi || defaultApiValues.detailApi"
                    size="small"
                    :placeholder="defaultApiValues.detailApi"
                    @update:value="updateTableProp('detailApi', $event)"
                  />
                </label>
                <label class="config-control">
                  <span>新增接口</span>
                  <NInput
                    :value="tableZone?.props?.createApi || defaultApiValues.createApi"
                    size="small"
                    :placeholder="defaultApiValues.createApi"
                    @update:value="updateTableProp('createApi', $event)"
                  />
                </label>
                <label class="config-control">
                  <span>编辑接口</span>
                  <NInput
                    :value="tableZone?.props?.updateApi || defaultApiValues.updateApi"
                    size="small"
                    :placeholder="defaultApiValues.updateApi"
                    @update:value="updateTableProp('updateApi', $event)"
                  />
                </label>
                <label class="config-control">
                  <span>删除接口</span>
                  <NInput
                    :value="tableZone?.props?.deleteApi || defaultApiValues.deleteApi"
                    size="small"
                    :placeholder="defaultApiValues.deleteApi"
                    @update:value="updateTableProp('deleteApi', $event)"
                  />
                </label>
                <label class="config-control">
                  <span>列表数据字段</span>
                  <NInput
                    :value="tableZone?.props?.listDataField || 'records'"
                    size="small"
                    placeholder="records"
                    @update:value="updateTableProp('listDataField', $event || 'records')"
                  />
                </label>
                <label class="config-control">
                  <span>总数字段</span>
                  <NInput
                    :value="tableZone?.props?.listTotalField || 'total'"
                    size="small"
                    placeholder="total"
                    @update:value="updateTableProp('listTotalField', $event || 'total')"
                  />
                </label>
              </div>
            </div>
          </section>
          <section v-if="configSectionVisible(['搜索和表格', '搜索', '查询', '表格', '最大高度', '横向滚动', '搜索折叠', '模式切换'])" class="config-section">
            <div class="config-section-title">
              搜索和表格
            </div>
            <div class="config-form-grid">
              <label class="config-control">
                <span>搜索列数</span>
                <NInputNumber
                  :value="tableZone?.props?.searchGridCols || 4"
                  size="small"
                  :min="1"
                  :max="6"
                  :show-button="false"
                  @update:value="updateTableProp('searchGridCols', $event || 4)"
                />
              </label>
              <label class="config-control">
                <span>默认显示查询项</span>
                <NInputNumber
                  :value="tableZone?.props?.searchMaxVisibleFields || 3"
                  size="small"
                  :min="1"
                  :max="12"
                  :show-button="false"
                  @update:value="updateTableProp('searchMaxVisibleFields', $event || 3)"
                />
              </label>
              <label class="config-control">
                <span>搜索标签宽度</span>
                <NInput
                  :value="tableZone?.props?.searchLabelWidth || 'auto'"
                  size="small"
                  placeholder="auto / 100px"
                  @update:value="updateTableProp('searchLabelWidth', $event || 'auto')"
                />
              </label>
              <label class="config-control">
                <span>表格最大高度</span>
                <NInput
                  :value="tableZone?.props?.maxHeight || ''"
                  size="small"
                  placeholder="例如 520px，留空自适应"
                  @update:value="updateTableProp('maxHeight', $event || undefined)"
                />
              </label>
              <label class="config-control">
                <span>横向滚动宽度</span>
                <NInput
                  :value="tableZone?.props?.scrollX || ''"
                  size="small"
                  placeholder="例如 1200"
                  @update:value="updateTableProp('scrollX', $event || undefined)"
                />
              </label>
            </div>
            <div class="config-switch-grid">
              <label>
                <NSwitch
                  :value="tableZone?.props?.searchEnableCollapse !== false"
                  size="small"
                  @update:value="updateTableProp('searchEnableCollapse', $event)"
                />
                <span>查询折叠</span>
              </label>
              <label>
                <NSwitch
                  :value="tableZone?.props?.showRenderModeSwitch !== false"
                  size="small"
                  @update:value="updateTableProp('showRenderModeSwitch', $event)"
                />
                <span>模式切换</span>
              </label>
            </div>
          </section>
          <section v-if="configSectionVisible(['新增编辑弹窗', '新增', '编辑', '弹窗', '抽屉', '表单', '标签', '详情宽度'])" class="config-section">
            <div class="config-section-title">
              新增编辑弹窗
            </div>
            <div class="config-section-tip">
              新增、编辑弹窗使用当前表单设计字段渲染；这里控制弹窗形态和表单布局。
            </div>
            <div class="config-form-grid">
              <label class="config-control">
                <span>弹出方式</span>
                <NSelect
                  :value="tableZone?.props?.formOpenMode || tableZone?.props?.modalType || 'modal'"
                  :options="formOpenModeOptions"
                  size="small"
                  @update:value="updateTableFormOpenMode"
                />
              </label>
              <label class="config-control">
                <span>抽屉方向</span>
                <NSelect
                  :value="tableZone?.props?.drawerPlacement || 'right'"
                  :options="drawerPlacementOptions"
                  size="small"
                  @update:value="updateTableProp('drawerPlacement', $event || 'right')"
                />
              </label>
              <label class="config-control">
                <span>弹窗宽度</span>
                <NInput
                  :value="tableZone?.props?.modalWidth || '800px'"
                  size="small"
                  placeholder="800px / 80vw"
                  @update:value="updateTableProp('modalWidth', $event || '800px')"
                />
              </label>
              <label class="config-control">
                <span>详情宽度</span>
                <NInput
                  :value="tableZone?.props?.detailModalWidth || 'min(1080px, 92vw)'"
                  size="small"
                  placeholder="min(1080px, 92vw)"
                  @update:value="updateTableProp('detailModalWidth', $event || 'min(1080px, 92vw)')"
                />
              </label>
              <label class="config-control">
                <span>表单列数</span>
                <NInputNumber
                  :value="tableZone?.props?.editGridCols || 1"
                  size="small"
                  :min="1"
                  :max="4"
                  :show-button="false"
                  @update:value="updateTableProp('editGridCols', $event || 1)"
                />
              </label>
              <label class="config-control">
                <span>表单尺寸</span>
                <NSelect
                  :value="tableZone?.props?.editSize || 'medium'"
                  :options="sizeOptions"
                  size="small"
                  @update:value="updateTableProp('editSize', $event || 'medium')"
                />
              </label>
              <label class="config-control">
                <span>标签位置</span>
                <NSelect
                  :value="tableZone?.props?.editLabelPlacement || 'left'"
                  :options="labelPlacementOptions"
                  size="small"
                  @update:value="updateTableProp('editLabelPlacement', $event || 'left')"
                />
              </label>
              <label class="config-control">
                <span>标签宽度</span>
                <NInput
                  :value="tableZone?.props?.editLabelWidth || 'auto'"
                  size="small"
                  placeholder="auto / 100px"
                  @update:value="updateTableProp('editLabelWidth', $event || 'auto')"
                />
              </label>
            </div>
          </section>
          <section v-if="configSectionVisible(['工具栏和事件', '工具栏', '事件', '按钮文案', '导出文件名', '自定义操作', '行操作', '默认参数', '公共参数', '查询默认参数', '提交固定参数', '表单默认值', 'publicParams', 'publicQuery', '回调', '参数处理', '提交前', '搜索前', '加载列表前', 'beforeSubmit'])" class="config-section">
            <div class="config-section-title">
              工具栏和事件
            </div>
            <div class="config-form-grid">
              <label class="config-control">
                <span>新增按钮文案</span>
                <NInput
                  :value="tableZone?.props?.addButtonText || '新增'"
                  size="small"
                  @update:value="updateTableProp('addButtonText', $event || '新增')"
                />
              </label>
              <label class="config-control">
                <span>导出按钮文案</span>
                <NInput
                  :value="tableZone?.props?.exportButtonText || '导出'"
                  size="small"
                  @update:value="updateTableProp('exportButtonText', $event || '导出')"
                />
              </label>
              <label class="config-control">
                <span>导出文件名</span>
                <NInput
                  :value="tableZone?.props?.exportFileName || ''"
                  size="small"
                  placeholder="不填使用页面标题"
                  @update:value="updateTableProp('exportFileName', $event)"
                />
              </label>
            </div>
            <div class="event-editor compact">
              <div v-for="(eventItem, eventIdx) in tableEvents" :key="eventItem.id || eventIdx" class="event-row">
                <div class="event-row-head">
                  <span>{{ eventTriggerText(eventItem.trigger) }} · {{ eventActionText(eventItem.action) }}</span>
                  <NButton size="tiny" quaternary type="error" @click="removeTableEvent(eventIdx)">
                    删除
                  </NButton>
                </div>
                <div class="event-grid">
                  <NSelect
                    :value="eventItem.trigger"
                    :options="eventTriggerOptions"
                    size="tiny"
                    @update:value="updateTableEvent(eventIdx, { trigger: $event })"
                  />
                  <NSelect
                    :value="eventItem.action"
                    :options="eventActionOptions"
                    size="tiny"
                    @update:value="updateTableEvent(eventIdx, { action: $event })"
                  />
                  <NSelect
                    :value="eventItem.targetPageKey"
                    :options="pageTargetOptions"
                    size="tiny"
                    filterable
                    clearable
                    placeholder="目标页面"
                    @update:value="updateTableEvent(eventIdx, { targetPageKey: $event || '' })"
                  />
                  <NInput
                    :value="eventItem.description"
                    size="tiny"
                    placeholder="说明，方便以后维护"
                    @update:value="updateTableEvent(eventIdx, { description: $event })"
                  />
                </div>
              </div>
              <NButton size="tiny" dashed block @click="addTableEvent">
                新增事件
              </NButton>
            </div>
            <CrudDefaultParamsEditor
              class="default-params-block"
              :model-value="resolveTableDefaultParams()"
              :field-options="sortFieldOptions"
              @update:model-value="updateTableDefaultParams"
            />
            <CrudHookRulesEditor
              class="hook-rules-block"
              :model-value="tableZone?.props?.crudHookRules || {}"
              :legacy-before-submit-rules="tableZone?.props?.beforeSubmitRules || []"
              :field-options="sortFieldOptions"
              @update:model-value="updateTableHookRules"
            />
            <div class="custom-action-editor">
              <div class="custom-action-head">
                <strong>列表操作按钮</strong>
                <NButton size="tiny" type="primary" secondary @click="addTableCustomAction">
                  新增操作
                </NButton>
              </div>
              <div v-if="tableCustomActions.length" class="custom-action-list">
                <div v-for="(action, actionIdx) in tableCustomActions" :key="action.key || actionIdx" class="custom-action-row">
                  <NInput
                    :value="action.label"
                    size="tiny"
                    placeholder="按钮名称"
                    @update:value="updateTableCustomAction(actionIdx, { label: $event })"
                  />
                  <NSelect
                    :value="action.position || 'row'"
                    :options="customActionPositionOptions"
                    size="tiny"
                    @update:value="updateTableCustomAction(actionIdx, { position: $event || 'row' })"
                  />
                  <NSelect
                    :value="action.type || 'primary'"
                    :options="buttonTypeOptions"
                    size="tiny"
                    @update:value="updateTableCustomAction(actionIdx, { type: $event || 'primary' })"
                  />
                  <NSelect
                    :value="action.actionType || 'route'"
                    :options="customActionTypeOptions"
                    size="tiny"
                    @update:value="updateTableCustomAction(actionIdx, { actionType: $event || 'route' })"
                  />
                  <NSelect
                    v-if="action.actionType === 'page'"
                    :value="action.targetPageKey || ''"
                    :options="pageTargetOptions"
                    size="tiny"
                    filterable
                    clearable
                    placeholder="目标页面"
                    @update:value="updateTableCustomAction(actionIdx, { targetPageKey: $event || '' })"
                  />
                  <NInput
                    v-else
                    :value="action.routePath || ''"
                    size="tiny"
                    placeholder="路由 / 接口 / 脚本标识"
                    @update:value="updateTableCustomAction(actionIdx, { routePath: $event })"
                  />
                  <NButton size="tiny" quaternary type="error" @click="removeTableCustomAction(actionIdx)">
                    删除
                  </NButton>
                </div>
              </div>
              <div v-else class="config-empty-text">
                暂未配置自定义操作。行操作会显示在列表操作列，工具栏操作会显示在表格上方。
              </div>
            </div>
          </section>
          <section v-if="layoutType === 'tree-crud' && configSectionVisible(['树表模板', '树', '树标题', '父级字段', '显示字段', '加载方式'])" class="config-section">
            <div class="config-section-title">
              树表模板
            </div>
            <div class="config-form-grid">
              <label class="config-control">
                <span>树标题</span>
                <NInput
                  :value="treeConfig.treeTitle"
                  size="small"
                  placeholder="例如：组织架构"
                  @update:value="updateTreeConfig('treeTitle', $event)"
                />
              </label>
              <label class="config-control">
                <span>父级字段</span>
                <NSelect
                  :value="treeConfig.parentField"
                  :options="fieldOptions"
                  size="small"
                  filterable
                  placeholder="请选择父级字段"
                  @update:value="updateTreeConfig('parentField', $event)"
                />
              </label>
              <label class="config-control">
                <span>显示字段</span>
                <NSelect
                  :value="treeConfig.labelField"
                  :options="fieldOptions"
                  size="small"
                  filterable
                  placeholder="请选择树节点显示字段"
                  @update:value="updateTreeConfig('labelField', $event)"
                />
              </label>
              <label class="config-control">
                <span>加载方式</span>
                <NSelect
                  :value="treeConfig.loadMode || 'full'"
                  :options="treeLoadModeOptions"
                  size="small"
                  @update:value="updateTreeConfig('loadMode', $event)"
                />
              </label>
            </div>
            <div class="field-help-text">
              树节点点击后筛选右侧列表。这里配置的是模板数据关系，不是页面左侧菜单。
            </div>
          </section>
          <div ref="fieldEditorAnchor" class="field-config-anchor">
            <n-radio-group
              class="field-zone-tabs"
              :value="activeFieldZone"
              size="small"
              @update:value="openFieldPanel"
            >
              <n-radio-button value="search">
                查询 {{ searchFields.length }}
              </n-radio-button>
              <n-radio-button value="table">
                列表 {{ tableFields.length }}
              </n-radio-button>
            </n-radio-group>
            <div class="quick-config-help">
              快速配置只服务默认 CRUD 组件：新增/删除字段、排序、列宽会同步到自由画布里的 AiCrudPage。
            </div>
            <FieldOrderEditor
              v-if="activeFieldEditor"
              :title="activeFieldEditor.title"
              :empty-text="activeFieldEditor.emptyText"
              :fields="fields"
              :selected-refs="activeFieldEditor.selectedRefs"
              :filter="activeFieldEditor.filter"
              :mode="activeFieldEditor.mode"
              :settings="activeFieldEditor.settings"
              :page-options="pageTargetOptions"
              @update="updateZoneRefs(activeFieldEditor.zoneKey, $event)"
              @update-settings="updateZoneFieldSetting(activeFieldEditor.zoneKey, $event)"
            />
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script>
import { structuredListPageDesignerLocalComponents } from './structuredListPageDesignerLocalComponents'
import { useStructuredListPageDesigner } from './composables/useStructuredListPageDesigner'

export default {
  name: 'StructuredListPageDesigner',
  components: {
    ...structuredListPageDesignerLocalComponents,
  },
  props: {
  modelValue: {
    type: Object,
    required: true,
  },
  fields: {
    type: Array,
    default: () => [],
  },
  layoutType: {
    type: String,
    default: 'simple-crud',
  },
  pages: {
    type: Array,
    default: () => [],
  },
  modelSchema: {
    type: Object,
    default: () => ({}),
  },
},
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return useStructuredListPageDesigner(props, emit)
  },
}
</script>

<style scoped src="./structuredListPageDesigner.css"></style>
