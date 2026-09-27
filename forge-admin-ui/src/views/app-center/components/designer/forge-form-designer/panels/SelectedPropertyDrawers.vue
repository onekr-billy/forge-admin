<template>
      <n-drawer
        v-if="isCrudBlock"
        v-model:show="advancedConfigVisible"
        :width="380"
        placement="right"
        :trap-focus="false"
        :block-scroll="false"
      >
        <n-drawer-content title="CRUD 更多配置" closable>
          <n-form label-placement="top" :show-feedback="false" class="property-form drawer-property-form">
            <section class="panel-item">
              <div class="panel-item-title">
                AiCrudPage API
              </div>
              <div class="form-api-endpoint-list">
                <div v-for="item in crudApiFields" :key="item.key" class="form-api-endpoint-row">
                  <span class="form-api-method-badge" :class="resolveCrudApiMethodClass(item)">
                    {{ resolveCrudApiMethodLabel(item) }}
                  </span>
                  <n-input
                    :value="crudApiConfig[item.key]"
                    clearable
                    :placeholder="item.placeholder"
                    @update:value="updateCrudApiConfig(item.key, $event)"
                  />
                </div>
              </div>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                查询表单细节
              </div>
              <n-form-item label="搜索标签宽度">
                <n-input
                  :value="crudOptions.searchLabelWidth || 'auto'"
                  placeholder="auto / 100"
                  @update:value="updateCrudOption('searchLabelWidth', $event || 'auto')"
                />
              </n-form-item>
              <n-form-item label="搜索行间距">
                <n-input-number
                  :value="crudOptions.searchYGap || 16"
                  :min="0"
                  :max="40"
                  @update:value="updateCrudOption('searchYGap', $event || 16)"
                />
              </n-form-item>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                表格细节
              </div>
              <n-form-item label="列宽拖拽">
                <n-switch
                  size="small"
                  :value="crudOptions.resizable !== false"
                  @update:value="updateCrudOption('resizable', $event)"
                />
              </n-form-item>
              <n-form-item label="表格最大高度">
                <n-input
                  :value="crudOptions.maxHeight || ''"
                  clearable
                  placeholder="例如 520 / 60vh"
                  @update:value="updateCrudOption('maxHeight', $event || undefined)"
                />
              </n-form-item>
              <n-form-item label="横向滚动宽度">
                <n-input-number
                  :value="crudOptions.scrollX"
                  clearable
                  :min="0"
                  :max="5000"
                  @update:value="updateCrudOption('scrollX', $event || undefined)"
                />
              </n-form-item>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                展开面板
              </div>
              <div class="crud-expand-config-panel">
                <div class="crud-expand-config-head">
                  <n-switch
                    size="small"
                    :value="crudOptions.expandConfig?.enabled === true"
                    @update:value="updateCrudExpandEnabled"
                  />
                  <span>{{ crudOptions.expandConfig?.enabled === true ? '已启用行展开' : '未启用行展开' }}</span>
                </div>
              </div>
              <template v-if="crudOptions.expandConfig?.enabled === true">
                <div class="crud-expand-config-section">
                  <div class="crud-expand-section-title">
                    基础设置
                  </div>
                  <div class="crud-expand-config-grid">
                    <label class="crud-expand-config-field">
                      <span>
                        触发方式
                      </span>
                      <n-select
                        :value="crudOptions.expandConfig?.trigger || 'icon'"
                        :options="expandTriggerOptions"
                        size="small"
                        @update:value="value => updateCrudExpandConfig({ trigger: value || 'icon' })"
                      />
                    </label>
                    <label class="crud-expand-config-field">
                      <span>
                        展示方式
                      </span>
                      <n-select
                        :value="crudOptions.expandConfig?.layout?.mode || 'single'"
                        :options="expandLayoutModeOptions"
                        size="small"
                        @update:value="value => updateCrudExpandConfig({ layout: { ...(crudOptions.expandConfig?.layout || {}), mode: value || 'single' } })"
                      />
                    </label>
                  </div>
                </div>
                <div class="crud-expand-config-section">
                  <div class="crud-expand-section-title">
                    内容设置
                  </div>
                  <div class="crud-expand-config-grid">
                    <label class="crud-expand-config-field">
                      <span>
                        面板类型
                      </span>
                      <n-select
                        :value="firstCrudExpandPanel?.type || 'descriptions'"
                        :options="expandPanelTypeOptions"
                        size="small"
                        @update:value="value => updateFirstCrudExpandPanel({ type: value || 'descriptions' })"
                      />
                    </label>
                    <label class="crud-expand-config-field">
                      <span>
                        面板标题
                      </span>
                      <n-input
                        :value="firstCrudExpandPanel?.title || ''"
                        size="small"
                        clearable
                        placeholder="概览 / 明细"
                        @update:value="value => updateFirstCrudExpandPanel({ title: value || undefined })"
                      />
                    </label>
                    <label class="crud-expand-config-field">
                      <span>
                        数据来源
                      </span>
                      <n-select
                        :value="firstCrudExpandPanel?.dataSource?.type || 'row'"
                        :options="expandDataSourceTypeOptions"
                        size="small"
                        @update:value="value => updateFirstCrudExpandPanel({ dataSource: { ...(firstCrudExpandPanel?.dataSource || {}), type: value || 'row' } })"
                      />
                    </label>
                  </div>
                </div>
                <div v-if="['api', 'quantity'].includes(firstCrudExpandPanel?.dataSource?.type)" class="crud-expand-config-section">
                  <div class="crud-expand-section-title">
                    {{ firstCrudExpandPanel?.dataSource?.type === 'quantity' ? '数量查询参数' : '接口参数' }}
                  </div>
                  <label v-if="firstCrudExpandPanel?.dataSource?.type === 'api'" class="crud-expand-config-field">
                    <span>
                      接口地址
                    </span>
                    <n-input
                      :value="firstCrudExpandPanel?.dataSource?.api || ''"
                      size="small"
                      clearable
                      placeholder="get@/api/order/item/page"
                      @update:value="value => updateFirstCrudExpandDataSource({ api: value || '' })"
                    />
                  </label>
                  <label class="crud-expand-config-field">
                    <span>
                      参数映射
                    </span>
                    <n-input
                      :value="stringifyJsonProp(firstCrudExpandPanel?.dataSource?.paramsMap || {})"
                      type="textarea"
                      :autosize="{ minRows: 2, maxRows: 4 }"
                      placeholder="例如 {&quot;sourceRecordId&quot;:&quot;${row.id}&quot;}"
                      @update:value="updateFirstCrudExpandParamsMap"
                    />
                  </label>
                </div>
                <div v-if="firstCrudExpandPanel?.type === 'descriptions'" class="crud-expand-config-section">
                  <div class="crud-expand-section-title">
                    描述字段
                  </div>
                  <div class="bitable-config-summary-row">
                    <div class="bitable-config-summary-label">
                      展示字段
                    </div>
                    <div class="bitable-config-summary-value">
                      <div class="bitable-config-value-box">
                        <div class="bitable-config-value-box-text">
                          {{ resolveCrudExpandDescriptionSelectedFields(firstCrudExpandPanel).length }} 个字段
                        </div>
                        <div class="bitable-config-value-box-btn">
                          <n-popover
                            v-model:show="crudDescriptionFieldPanelOpen"
                            trigger="click"
                            placement="bottom-end"
                            :show-arrow="false"
                            raw
                          >
                            <template #trigger>
                              <button type="button" class="bitable-config-icon-button" title="设置展示字段">
                                <n-icon><SettingsOutline /></n-icon>
                              </button>
                            </template>
                            <div class="bitable-field-popover-panel">
                              <div class="bitable-field-panel-arrow" />
                              <div class="bitable-field-popover-head">
                                展示字段
                              </div>
                              <div class="bitable-field-panel-list">
                                <draggable
                                  :model-value="resolveCrudExpandDescriptionPanelFields(firstCrudExpandPanel)"
                                  item-key="field"
                                  handle=".crud-bitable-field-drag"
                                  :animation="160"
                                  @update:model-value="handleCrudExpandDescriptionPanelReorder"
                                >
                                  <template #item="{ element }">
                                    <div class="crud-bitable-field-row" :class="{ invisible: !element.selected }">
                                      <button type="button" class="crud-bitable-field-drag" title="拖拽排序">
                                        <n-icon>
                                          <BitableDragIcon />
                                        </n-icon>
                                      </button>
                                      <n-icon class="crud-bitable-field-icon">
                                        <component :is="resolveCrudBitableFieldIconComponent(element)" />
                                      </n-icon>
                                      <div class="crud-bitable-field-name">
                                        <span>{{ element.label || element.field }}</span>
                                        <small>{{ element.field }}</small>
                                      </div>
                                      <button
                                        type="button"
                                        class="crud-bitable-field-visible"
                                        :title="element.selected ? '隐藏字段' : '显示字段'"
                                        @click="toggleFirstCrudExpandDescriptionField(element.field, !element.selected)"
                                      >
                                        <n-icon>
                                          <component :is="element.selected ? EyeOutline : EyeOffOutline" />
                                        </n-icon>
                                      </button>
                                    </div>
                                  </template>
                                </draggable>
                                <span v-if="!crudDescriptionFieldOptions.length" class="custom-action-empty">暂无可选字段</span>
                              </div>
                            </div>
                          </n-popover>
                        </div>
                      </div>
                    </div>
                  </div>
                  <n-empty
                    v-if="!crudDescriptionFieldOptions.length"
                    size="small"
                    description="暂无可选字段"
                  />
                </div>
              </template>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                编辑弹窗
                <small class="panel-item-hint">配置已移至「表单属性 → 表单项配置」</small>
              </div>
              <div class="crud-readonly-summary">
                <span>打开方式：{{ schema.layout?.formOpenMode || schema.layout?.modalType || 'modal' }}</span>
                <span>弹窗宽度：{{ schema.layout?.modalWidth || '800px' }}</span>
                <span>表单列数：{{ normalizedFormGridColumns }}</span>
              </div>
              <n-form-item label="每页条数">
                <n-input-number
                  :value="crudOptions.pageSize || 10"
                  :min="1"
                  :max="200"
                  @update:value="updateCrudOption('pageSize', $event || 10)"
                />
              </n-form-item>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                开关
              </div>
              <div class="switch-list">
                <label v-for="item in crudSwitchFields" :key="item.key">
                  <span>{{ item.label }}</span>
                  <n-switch
                    size="small"
                    :value="resolveCrudSwitchValue(item.key, item.defaultValue)"
                    @update:value="updateCrudOption(item.key, $event)"
                  />
                </label>
              </div>
            </section>
          </n-form>
        </n-drawer-content>
      </n-drawer>

      <n-drawer
        v-if="isCrudBlock"
        v-model:show="crudFieldDrawerVisible"
        :width="420"
        placement="right"
        :trap-focus="false"
        :block-scroll="false"
      >
        <n-drawer-content :title="`${editingCrudField?.label || '字段'} 配置`" closable>
          <n-form label-placement="top" :show-feedback="false" class="property-form drawer-property-form">
            <section class="panel-item">
              <div class="panel-item-title">
                查询条件
              </div>
              <n-form-item label="查询标签">
                <n-input
                  :value="editingCrudConfig.search?.label || ''"
                  clearable
                  placeholder="默认使用字段名称"
                  @update:value="updateEditingCrudFieldConfig('search', { label: $event || undefined })"
                />
              </n-form-item>
              <n-form-item label="查询占位提示">
                <n-input
                  :value="editingCrudConfig.search?.placeholder || ''"
                  clearable
                  placeholder="默认使用字段占位提示"
                  @update:value="updateEditingCrudFieldConfig('search', { placeholder: $event || undefined })"
                />
              </n-form-item>
              <n-form-item label="查询控件跨度">
                <n-input-number
                  :value="editingCrudConfig.search?.span || editingCrudField?.layout?.span || 1"
                  :min="1"
                  :max="maxFormGridColumns"
                  @update:value="updateEditingCrudFieldConfig('search', { span: $event || 1 })"
                />
              </n-form-item>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                表格列
              </div>
              <n-form-item label="列标题">
                <n-input
                  :value="editingCrudConfig.table?.title || ''"
                  clearable
                  placeholder="默认使用字段名称"
                  @update:value="updateEditingCrudFieldConfig('table', { title: $event || undefined })"
                />
              </n-form-item>
              <div class="crud-inline-grid">
                <n-form-item label="列宽">
                  <n-input-number
                    :value="editingCrudConfig.table?.width"
                    clearable
                    :min="60"
                    :max="800"
                    @update:value="updateEditingCrudFieldConfig('table', { width: $event || undefined })"
                  />
                </n-form-item>
                <n-form-item label="最小宽度">
                  <n-input-number
                    :value="editingCrudConfig.table?.minWidth || 120"
                    :min="60"
                    :max="800"
                    @update:value="updateEditingCrudFieldConfig('table', { minWidth: $event || undefined })"
                  />
                </n-form-item>
                <n-form-item label="对齐方式">
                  <n-select
                    :value="editingCrudConfig.table?.align || 'left'"
                    :options="tableAlignOptions"
                    @update:value="updateEditingCrudFieldConfig('table', { align: $event || undefined })"
                  />
                </n-form-item>
                <n-form-item label="固定列">
                  <n-select
                    :value="editingCrudConfig.table?.fixed || ''"
                    :options="tableFixedOptions"
                    @update:value="updateEditingCrudFieldConfig('table', { fixed: $event || undefined })"
                  />
                </n-form-item>
              </div>
              <div class="switch-list">
                <label>
                  <span>文字省略</span>
                  <n-switch
                    size="small"
                    :value="editingCrudConfig.table?.ellipsis !== false"
                    @update:value="updateEditingCrudFieldConfig('table', { ellipsis: $event })"
                  />
                </label>
                <label>
                  <span>可排序</span>
                  <n-switch
                    size="small"
                    :value="!!editingCrudConfig.table?.sorter"
                    @update:value="updateEditingCrudFieldConfig('table', { sorter: $event })"
                  />
                </label>
              </div>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                编辑弹窗
              </div>
              <n-form-item label="编辑标签">
                <n-input
                  :value="editingCrudConfig.edit?.label || ''"
                  clearable
                  placeholder="默认使用字段名称"
                  @update:value="updateEditingCrudFieldConfig('edit', { label: $event || undefined })"
                />
              </n-form-item>
              <n-form-item label="编辑占位提示">
                <n-input
                  :value="editingCrudConfig.edit?.placeholder || ''"
                  clearable
                  placeholder="默认使用字段占位提示"
                  @update:value="updateEditingCrudFieldConfig('edit', { placeholder: $event || undefined })"
                />
              </n-form-item>
              <n-form-item label="编辑控件跨度">
                <n-input-number
                  :value="editingCrudConfig.edit?.span || editingCrudField?.layout?.span || 1"
                  :min="1"
                  :max="maxFormGridColumns"
                  @update:value="updateEditingCrudFieldConfig('edit', { span: $event || 1 })"
                />
              </n-form-item>
              <div class="switch-list">
                <label>
                  <span>只读</span>
                  <n-switch
                    size="small"
                    :value="!!editingCrudConfig.edit?.readonly"
                    @update:value="updateEditingCrudFieldConfig('edit', { readonly: $event })"
                  />
                </label>
              </div>
            </section>
          </n-form>
        </n-drawer-content>
      </n-drawer>

      <n-drawer
        v-model:show="componentPropsVisible"
        :width="440"
        placement="right"
        :trap-focus="false"
        :block-scroll="false"
      >
        <n-drawer-content :title="`${selectedLabel} 组件属性`" closable>
          <!-- 统一属性面板引擎：常用属性平铺 + 高级属性折叠 + 搜索过滤 -->
          <SpecPropertyPanel
            :block-type="selectedComponent.componentKey"
            :model-props="selectedComponent.props || {}"
            :exclude-keys="specPanelExcludedProps"
            @update:prop="handleSpecPropUpdate"
          />
        </n-drawer-content>
      </n-drawer>
</template>

<script>
import { useForgePropertyPanelApi } from '../forgePropertyPanelContext'
import { forgePropertyPanelLocalComponents } from '../forgePropertyPanelLocalComponents'

export default {
  name: 'SelectedPropertyDrawers',
  components: { ...forgePropertyPanelLocalComponents },
  setup() {
    return useForgePropertyPanelApi()
  },
}
</script>

<style scoped src="../forge-property-panel-shell.css"></style>
<style scoped src="../forge-property-panel-fields.css"></style>
