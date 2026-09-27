<template>
        <n-tab-pane v-if="isCrudBlock" name="crud">
          <template #tab>
            <span class="property-tab-label">
              <n-icon><ServerOutline /></n-icon>
              CRUD
            </span>
          </template>
          <n-form label-placement="top" :show-feedback="false" class="property-form">
            <section class="panel-item panel-item-strong form-api-panel">
              <div class="panel-title-row">
                <div class="panel-item-title">
                  接口与数据源
                </div>
                <n-button size="tiny" type="primary" @click="advancedConfigVisible = true">
                  更多配置
                </n-button>
              </div>
              <div class="form-api-field">
                <span class="form-api-label">基础路径 / 行主键</span>
                <div class="form-api-base-row">
                  <n-input
                    :value="selectedComponent.props?.apiBase"
                    clearable
                    placeholder="/employee"
                    @update:value="updateCrudApiBase"
                  />
                  <n-input
                    :value="selectedComponent.props?.rowKey || 'id'"
                    placeholder="id"
                    @update:value="updateComponent({ props: { rowKey: $event || 'id' } })"
                  />
                </div>
              </div>
              <div class="form-api-field">
                <span class="form-api-label">API 接口地址配置</span>
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
              </div>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                字段与列配置
              </div>
              <div v-if="crudConfigFields.length" class="crud-field-config-list">
                <div v-for="item in crudConfigFields" :key="item.id" class="crud-field-config-card">
                  <div class="crud-field-card-head">
                    <button type="button" class="crud-field-name" @click="$emit('update:selectedId', item.id)">
                      <strong>{{ item.label }}</strong>
                      <small>{{ item.fieldCode }}</small>
                    </button>
                    <div class="crud-role-switches">
                      <label>
                        <span>查询</span>
                        <n-switch
                          size="small"
                          :value="item.roles.search"
                          @update:value="updateCrudFieldRole(item.id, 'search', $event)"
                        />
                      </label>
                      <label>
                        <span>表格列</span>
                        <n-switch
                          size="small"
                          :value="item.roles.table"
                          @update:value="updateCrudFieldRole(item.id, 'table', $event)"
                        />
                      </label>
                      <label>
                        <span>编辑</span>
                        <n-switch
                          size="small"
                          :value="item.roles.edit"
                          @update:value="updateCrudFieldRole(item.id, 'edit', $event)"
                        />
                      </label>
                    </div>
                  </div>
                  <div class="crud-inline-grid">
                    <n-form-item label="列标题">
                      <n-input
                        :value="item.config.table?.title || ''"
                        size="small"
                        clearable
                        placeholder="默认字段名"
                        @update:value="updateCrudFieldConfigById(item.id, 'table', { title: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item label="列宽">
                      <n-input-number
                        :value="item.config.table?.width"
                        size="small"
                        clearable
                        :min="60"
                        :max="800"
                        :show-button="false"
                        @update:value="updateCrudFieldConfigById(item.id, 'table', { width: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item label="对齐">
                      <n-select
                        :value="item.config.table?.align || 'left'"
                        size="small"
                        :options="tableAlignOptions"
                        @update:value="updateCrudFieldConfigById(item.id, 'table', { align: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item label="固定">
                      <n-select
                        :value="item.config.table?.fixed || ''"
                        size="small"
                        :options="tableFixedOptions"
                        @update:value="updateCrudFieldConfigById(item.id, 'table', { fixed: $event || undefined })"
                      />
                    </n-form-item>
                  </div>
                  <div class="crud-compact-switches">
                    <label>
                      <span>省略</span>
                      <n-switch
                        size="small"
                        :value="item.config.table?.ellipsis !== false"
                        @update:value="updateCrudFieldConfigById(item.id, 'table', { ellipsis: $event })"
                      />
                    </label>
                    <label>
                      <span>排序</span>
                      <n-switch
                        size="small"
                        :value="!!item.config.table?.sorter"
                        @update:value="updateCrudFieldConfigById(item.id, 'table', { sorter: $event })"
                      />
                    </label>
                    <n-button size="tiny" tertiary @click="openCrudFieldDrawer(item.id)">
                      更多字段配置
                    </n-button>
                  </div>
                </div>
              </div>
              <div v-else class="crud-field-empty">
                先把字段拖入 CRUD 区块，字段会自动生成查询条件、表格列和编辑弹窗字段。
              </div>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                查询表单
              </div>
              <n-form-item label="搜索列数">
                <n-input-number
                  :value="crudOptions.searchGridCols || 4"
                  :min="1"
                  :max="6"
                  @update:value="updateCrudOption('searchGridCols', $event || 4)"
                />
              </n-form-item>
              <n-form-item label="最大显示字段数">
                <n-input-number
                  :value="crudOptions.searchMaxVisibleFields || 3"
                  :min="1"
                  :max="12"
                  @update:value="updateCrudOption('searchMaxVisibleFields', $event || 3)"
                />
              </n-form-item>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                表格
              </div>
              <n-form-item label="表格尺寸">
                <n-select
                  :value="crudOptions.tableSize || 'medium'"
                  :options="tableDensityOptions"
                  @update:value="updateCrudOption('tableSize', $event || 'medium')"
                />
              </n-form-item>
              <n-form-item label="渲染模式">
                <n-radio-group
                  :value="crudOptions.renderMode || 'table'"
                  size="small"
                  @update:value="updateCrudOption('renderMode', $event)"
                >
                  <n-radio-button value="table">
                    表格
                  </n-radio-button>
                  <n-radio-button value="card">
                    卡片
                  </n-radio-button>
                </n-radio-group>
              </n-form-item>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                编辑表单
              </div>
              <n-form-item label="编辑列数">
                <n-input-number
                  :value="crudOptions.editGridCols || 1"
                  :min="1"
                  :max="maxFormGridColumns"
                  @update:value="updateCrudOption('editGridCols', $event || 1)"
                />
              </n-form-item>
              <n-form-item label="标签位置">
                <n-radio-group
                  :value="crudOptions.editLabelPlacement || 'left'"
                  size="small"
                  @update:value="updateCrudOption('editLabelPlacement', $event)"
                >
                  <n-radio-button value="left">
                    左侧
                  </n-radio-button>
                  <n-radio-button value="top">
                    顶部
                  </n-radio-button>
                </n-radio-group>
              </n-form-item>
              <n-form-item label="编辑表单尺寸">
                <n-select
                  :value="crudOptions.editSize || 'medium'"
                  :options="componentSizeOptions.filter(item => item.value)"
                  @update:value="updateCrudOption('editSize', $event || 'medium')"
                />
              </n-form-item>
            </section>
          </n-form>
        </n-tab-pane>

        <n-tab-pane v-if="false" name="layout">
          <template #tab>
            <span class="property-tab-label">
              <n-icon><GridOutline /></n-icon>
              布局
            </span>
          </template>
          <n-form label-placement="top" :show-feedback="false" class="property-form">
            <section class="panel-item">
              <div class="panel-item-title">
                栅格
              </div>
              <n-form-item v-if="!isRowLayout" label="占据列数">
                <div class="slider-control">
                  <n-slider
                    :value="selectedComponent.layout?.span || 1"
                    :min="1"
                    :max="normalizedFormGridColumns"
                    :step="1"
                    @update:value="updateComponent({ layout: { span: $event || 1 } })"
                  />
                  <n-input-number
                    :value="selectedComponent.layout?.span || 1"
                    :min="1"
                    :max="normalizedFormGridColumns"
                    :show-button="false"
                    size="small"
                    @update:value="updateComponent({ layout: { span: $event || 1 } })"
                  />
                </div>
              </n-form-item>
              <n-form-item v-if="isRowLayout" label="栅格总列数">
                <div class="slider-control">
                  <n-slider
                    :value="rowTotalColumns"
                    :min="1"
                    :max="maxFormGridColumns"
                    :step="1"
                    :marks="gridColumnMarks"
                    @update:value="updateRowTotalColumns"
                  />
                  <n-input-number
                    :value="rowTotalColumns"
                    :min="1"
                    :max="maxFormGridColumns"
                    :show-button="false"
                    size="small"
                    @update:value="updateRowTotalColumns($event || 1)"
                  />
                </div>
              </n-form-item>
              <n-form-item v-if="isRowLayout" label="格子数量">
                <n-input-number
                  :value="rowColumnCount"
                  :min="1"
                  :max="maxFormGridColumns"
                  size="small"
                  @update:value="updateRowCellCount($event || 1)"
                />
              </n-form-item>
              <n-form-item v-if="isRowLayout" label="列间距">
                <n-input-number
                  :value="selectedComponent.props?.gutter ?? 16"
                  :min="0"
                  :max="40"
                  @update:value="updateComponent({ props: { gutter: $event ?? 16 } })"
                />
              </n-form-item>
              <n-form-item v-if="isRowLayout" label="每列 span">
                <div class="grid-column-span-editor">
                  <div
                    v-for="(column, columnIndex) in rowColumns"
                    :key="column.id || columnIndex"
                    class="grid-column-span-row"
                  >
                    <span>{{ column.label || `第 ${columnIndex + 1} 列` }}</span>
                    <n-input-number
                      :value="column.layout?.span || column.props?.span || 1"
                      :min="1"
                      :max="rowTotalColumns"
                      size="small"
                      :show-button="false"
                      @update:value="updateRowColumnSpan(columnIndex, $event || 1)"
                    />
                  </div>
                </div>
              </n-form-item>
              <n-form-item v-if="isColumnLayout" label="栅格列宽">
                <div class="slider-control">
                  <n-slider
                    :value="selectedComponent.layout?.span || selectedComponent.props?.span || 1"
                    :min="1"
                    :max="maxFormGridColumns"
                    :step="1"
                    :marks="gridColumnMarks"
                    @update:value="updateComponent({ layout: { span: $event || 1 }, props: { span: $event || 1 } })"
                  />
                  <n-input-number
                    :value="selectedComponent.layout?.span || selectedComponent.props?.span || 1"
                    :min="1"
                    :max="maxFormGridColumns"
                    :show-button="false"
                    size="small"
                    @update:value="updateComponent({ layout: { span: $event || 1 }, props: { span: $event || 1 } })"
                  />
                </div>
              </n-form-item>
              <n-form-item v-if="isField" label="标签宽度">
                <n-input-number
                  :value="selectedComponent.layout?.labelWidth"
                  clearable
                  :min="60"
                  :max="260"
                  @update:value="updateComponent({ layout: { labelWidth: $event || undefined } })"
                />
              </n-form-item>
            </section>
            <section v-if="isCardLayout" class="panel-item">
              <div class="panel-item-title">
                Card 属性
              </div>
              <n-form-item label="size">
                <n-select
                  :value="selectedComponent.props?.size || 'small'"
                  :options="cardSizeOptions"
                  @update:value="updateComponent({ props: { size: $event || 'small' } })"
                />
              </n-form-item>
              <div class="switch-list">
                <label>
                  <span>bordered</span>
                  <n-switch
                    size="small"
                    :value="selectedComponent.props?.bordered !== false"
                    @update:value="updateComponent({ props: { bordered: $event } })"
                  />
                </label>
                <label>
                  <span>embedded</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.embedded"
                    @update:value="updateComponent({ props: { embedded: $event } })"
                  />
                </label>
                <label>
                  <span>segmented</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.segmented"
                    @update:value="updateComponent({ props: { segmented: $event } })"
                  />
                </label>
                <label>
                  <span>hoverable</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.hoverable"
                    @update:value="updateComponent({ props: { hoverable: $event } })"
                  />
                </label>
              </div>
            </section>

            <section v-if="isTabsLayout" class="panel-item">
              <div class="panel-item-title">
                页签管理
              </div>
              <!-- tabs 外观属性统一由「更多属性」抽屉的 SpecPropertyPanel 配置（spec 唯一属性源） -->
              <n-form-item v-if="hasSpecPanelProps" label="组件属性">
                <n-button size="small" dashed block @click="componentPropsVisible = true">
                  <template #icon>
                    <n-icon :size="14">
                      <SettingsOutline />
                    </n-icon>
                  </template>
                  更多属性
                </n-button>
              </n-form-item>
              <div class="layout-child-manager">
                <div class="panel-title-row">
                  <div class="panel-item-title">
                    页签管理
                  </div>
                  <n-button size="tiny" type="primary" secondary @click="addLayoutChild('tabPane')">
                    新增页签
                  </n-button>
                </div>
                <div v-for="(child, childIndex) in layoutChildren" :key="child.id" class="layout-child-card">
                  <div class="layout-child-card-main">
                    <n-input
                      :value="child.props?.label || child.label"
                      size="small"
                      placeholder="页签名称"
                      @update:value="updateLayoutChild(childIndex, { label: $event || `标签 ${childIndex + 1}`, props: { label: $event || `标签 ${childIndex + 1}` } })"
                    />
                    <n-input
                      :value="child.props?.name || child.id"
                      size="small"
                      placeholder="name"
                      @update:value="updateLayoutChild(childIndex, { props: { name: $event || undefined } })"
                    />
                  </div>
                  <div class="layout-child-actions">
                    <n-button size="tiny" tertiary @click="$emit('update:selectedId', child.id)">
                      配置内容
                    </n-button>
                    <n-button size="tiny" tertiary :disabled="childIndex === 0" @click="moveLayoutChild(childIndex, -1)">
                      上移
                    </n-button>
                    <n-button size="tiny" tertiary :disabled="childIndex === layoutChildren.length - 1" @click="moveLayoutChild(childIndex, 1)">
                      下移
                    </n-button>
                    <n-button size="tiny" quaternary type="error" :disabled="layoutChildren.length <= 1" @click="removeLayoutChild(childIndex)">
                      删除
                    </n-button>
                  </div>
                </div>
              </div>
            </section>

            <section v-if="isCollapseLayout" class="panel-item">
              <div class="panel-item-title">
                Collapse 属性
              </div>
              <n-form-item label="arrowPlacement">
                <n-select
                  :value="selectedComponent.props?.arrowPlacement || 'left'"
                  :options="collapseArrowPlacementOptions"
                  @update:value="updateComponent({ props: { arrowPlacement: $event || 'left' } })"
                />
              </n-form-item>
              <n-form-item label="displayDirective">
                <n-select
                  :value="selectedComponent.props?.displayDirective || 'if'"
                  :options="collapseDisplayDirectiveOptions"
                  @update:value="updateComponent({ props: { displayDirective: $event || 'if' } })"
                />
              </n-form-item>
              <n-form-item label="triggerAreas">
                <n-select
                  multiple
                  :value="selectedComponent.props?.triggerAreas || ['main', 'arrow']"
                  :options="collapseTriggerAreaOptions"
                  @update:value="updateComponent({ props: { triggerAreas: $event?.length ? $event : ['main', 'arrow'] } })"
                />
              </n-form-item>
              <div class="switch-list">
                <label>
                  <span>accordion</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.accordion"
                    @update:value="updateComponent({ props: { accordion: $event } })"
                  />
                </label>
              </div>
              <div class="layout-child-manager">
                <div class="panel-title-row">
                  <div class="panel-item-title">
                    面板管理
                  </div>
                  <n-button size="tiny" type="primary" secondary @click="addLayoutChild('collapseItem')">
                    新增面板
                  </n-button>
                </div>
                <div v-for="(child, childIndex) in layoutChildren" :key="child.id" class="layout-child-card">
                  <div class="layout-child-card-main">
                    <n-input
                      :value="child.props?.title || child.label"
                      size="small"
                      placeholder="面板标题"
                      @update:value="updateLayoutChild(childIndex, { label: $event || `分组 ${childIndex + 1}`, props: { title: $event || `分组 ${childIndex + 1}` } })"
                    />
                    <n-input
                      :value="child.props?.name || child.id"
                      size="small"
                      placeholder="name"
                      @update:value="updateLayoutChild(childIndex, { props: { name: $event || undefined } })"
                    />
                  </div>
                  <div class="layout-child-actions">
                    <n-button size="tiny" tertiary @click="$emit('update:selectedId', child.id)">
                      配置内容
                    </n-button>
                    <n-button size="tiny" tertiary :disabled="childIndex === 0" @click="moveLayoutChild(childIndex, -1)">
                      上移
                    </n-button>
                    <n-button size="tiny" tertiary :disabled="childIndex === layoutChildren.length - 1" @click="moveLayoutChild(childIndex, 1)">
                      下移
                    </n-button>
                    <n-button size="tiny" quaternary type="error" :disabled="layoutChildren.length <= 1" @click="removeLayoutChild(childIndex)">
                      删除
                    </n-button>
                  </div>
                </div>
              </div>
            </section>
          </n-form>
        </n-tab-pane>

        <n-tab-pane v-if="false" name="state">
          <template #tab>
            <span class="property-tab-label">
              <n-icon><ToggleOutline /></n-icon>
              状态
            </span>
          </template>
          <n-form label-placement="top" :show-feedback="false" class="property-form">
            <section class="panel-item">
              <div class="panel-item-title">
                可见性与校验
              </div>
              <div class="switch-list">
                <label v-if="isField">
                  <span>必填</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.validation?.required"
                    @update:value="updateComponent({ validation: { required: $event } })"
                  />
                </label>
                <label v-if="isField">
                  <span>唯一校验</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.advancedProps?.unique"
                    @update:value="updateUniqueValidation"
                  />
                </label>
                <label v-if="isField">
                  <span>只读</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.visibility?.readonly"
                    @update:value="updateComponent({ visibility: { readonly: $event } })"
                  />
                </label>
                <label v-if="isField || isButtonComponent">
                  <span>禁用</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.disabled"
                    @update:value="updateComponent({ props: { disabled: $event } })"
                  />
                </label>
                <label>
                  <span>隐藏</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.visibility?.hidden"
                    @update:value="updateComponentHidden"
                  />
                </label>
              </div>
              <n-form-item v-if="isField" label="必填提示">
                <n-input
                  :value="selectedComponent.validation?.requiredMessage"
                  clearable
                  placeholder="为空时使用默认提示"
                  @update:value="updateComponent({ validation: { requiredMessage: $event } })"
                />
              </n-form-item>
            </section>
          </n-form>
        </n-tab-pane>

        <n-tab-pane name="interaction">
          <template #tab>
            <span class="property-tab-label">
              <n-icon><FlashOutline /></n-icon>
              交互
            </span>
          </template>
          <n-form label-placement="top" :show-feedback="false" class="property-form">
            <section class="panel-item">
              <div class="panel-item-title">
                显示与编辑状态
              </div>
              <div class="switch-list">
                <label>
                  <span>隐藏组件</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.visibility?.hidden"
                    @update:value="updateComponentHidden"
                  />
                </label>
                <label v-if="isField">
                  <span>只读</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.visibility?.readonly"
                    @update:value="updateComponent({ visibility: { readonly: $event } })"
                  />
                </label>
                <label v-if="isField || isButtonComponent">
                  <span>禁用</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.disabled"
                    @update:value="updateComponent({ props: { disabled: $event } })"
                  />
                </label>
              </div>
              <RuntimeRulesEditor
                title="条件规则"
                :rules="selectedComponent.props?.runtimeRules || []"
                :field-options="runtimeRuleFieldOptions"
                @update:rules="updateComponent({ props: { runtimeRules: $event } })"
              />
            </section>

            <section v-if="isField && selectedDrivenRuntimeRules.length" class="panel-item driven-runtime-rules-panel">
              <div class="panel-item-title">
                其他字段引用了它
              </div>
              <div class="driven-runtime-rule-list">
                <article v-for="item in selectedDrivenRuntimeRules" :key="item.key" class="driven-runtime-rule-card">
                  <div>
                    <strong>{{ item.targetLabel }}</strong>
                    <span>{{ item.summary }}</span>
                  </div>
                  <n-button size="tiny" text type="primary" @click="emit('update:selectedId', item.targetId)">
                    查看目标字段
                  </n-button>
                </article>
              </div>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                联动动作
              </div>
              <div class="interaction-presets">
                <button
                  v-for="preset in interactionPresets"
                  :key="preset.key"
                  type="button"
                  class="interaction-preset-card"
                  @click="addInteractionPreset(preset.key)"
                >
                  <strong>{{ preset.title }}</strong>
                  <span>{{ preset.description }}</span>
                </button>
              </div>
              <div v-if="interactionRules.length" class="interaction-rule-list">
                <details v-for="(rule, ruleIndex) in interactionRules" :key="rule.id || ruleIndex" class="interaction-rule-card" open>
                  <summary class="interaction-rule-head">
                    <div>
                      <strong>{{ resolveTriggerLabel(rule.trigger) }}</strong>
                      <span>{{ resolveActionLabel(rule.action) }}</span>
                    </div>
                    <n-button size="tiny" quaternary type="error" @click.stop.prevent="removeInteractionRule(ruleIndex)">
                      删除
                    </n-button>
                  </summary>
                  <div class="interaction-grid">
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          触发事件
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            什么时候执行这条规则。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.trigger || defaultTrigger"
                        size="small"
                        :consistent-menu-width="false"
                        :options="triggerOptions"
                        @update:value="updateInteractionRule(ruleIndex, { trigger: $event || defaultTrigger })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          执行动作
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            规则触发后对目标组件做什么。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.action || 'setValue'"
                        size="small"
                        :consistent-menu-width="false"
                        :options="actionOptions"
                        @update:value="updateInteractionRule(ruleIndex, { action: $event || 'setValue' })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          目标组件
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            被这条规则影响的字段、按钮或区块。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.targetId || ''"
                        size="small"
                        filterable
                        clearable
                        :consistent-menu-width="false"
                        :options="componentTargetOptions"
                        @update:value="updateInteractionRule(ruleIndex, { targetId: $event || '' })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          触发值等于
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            用于下拉联动，例如选择“省份A”时才更新城市。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-input
                        :value="rule.whenValue ?? ''"
                        size="small"
                        clearable
                        placeholder="为空表示任何值都触发"
                        @update:value="updateInteractionRule(ruleIndex, { whenValue: $event || undefined })"
                      />
                    </n-form-item>
                  </div>
                  <template v-if="rule.action === 'setOptions'">
                    <n-form-item label="选项来源 API">
                      <n-input
                        :value="rule.api || ''"
                        size="small"
                        clearable
                        placeholder="get@/api/options?parent=:value"
                        @update:value="updateInteractionRule(ruleIndex, { api: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item label="静态选项 JSON">
                      <n-input
                        :value="rule.optionsJson || stringifyJsonProp(rule.options)"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 5 }"
                        placeholder="[{&quot;label&quot;:&quot;选项&quot;,&quot;value&quot;:&quot;A&quot;}]"
                        @update:value="updateInteractionRuleJson(ruleIndex, 'options', $event)"
                      />
                    </n-form-item>
                  </template>
                  <n-form-item v-else-if="['setValue', 'showHide', 'enableDisable'].includes(rule.action)">
                    <template #label>
                      <span class="field-label-with-help">
                        {{ resolveActionValueMeta(rule.action).label }}
                        <n-tooltip trigger="hover">
                          <template #trigger>
                            <span class="help-icon">?</span>
                          </template>
                          {{ resolveActionValueMeta(rule.action).help }}
                        </n-tooltip>
                      </span>
                    </template>
                    <n-input
                      :value="rule.value ?? ''"
                      size="small"
                      clearable
                      :placeholder="resolveActionValueMeta(rule.action).placeholder"
                      @update:value="updateInteractionRule(ruleIndex, { value: $event || undefined })"
                    />
                  </n-form-item>
                  <template v-else-if="rule.action === 'openModal'">
                    <n-form-item label="弹窗标题">
                      <n-input
                        :value="rule.modalTitle || ''"
                        size="small"
                        clearable
                        placeholder="请输入弹窗标题"
                        @update:value="updateInteractionRule(ruleIndex, { modalTitle: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          弹窗内容
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            不需要手写 JSON。可以复用当前表单，或选择画布里的某个组件作为弹窗内容。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.modalContentMode || 'currentForm'"
                        :options="modalContentModeOptions"
                        @update:value="updateInteractionRule(ruleIndex, { modalContentMode: $event || 'currentForm' })"
                      />
                    </n-form-item>
                    <n-form-item v-if="rule.modalContentMode === 'component'" label="弹窗组件">
                      <n-select
                        :value="rule.modalComponentId || ''"
                        filterable
                        clearable
                        :consistent-menu-width="false"
                        :options="componentTargetOptions"
                        @update:value="updateInteractionRule(ruleIndex, { modalComponentId: $event || '' })"
                      />
                    </n-form-item>
                    <n-form-item v-if="rule.modalContentMode === 'formAsset'" label="引用表单">
                      <n-select
                        :value="rule.modalFormKey || 'current'"
                        filterable
                        :consistent-menu-width="false"
                        :options="formAssetOptions"
                        @update:value="updateInteractionRule(ruleIndex, { modalFormKey: $event || 'current' })"
                      />
                    </n-form-item>
                  </template>
                  <n-form-item v-else-if="rule.action === 'apiRequest'" label="请求接口">
                    <n-input
                      :value="rule.api || ''"
                      size="small"
                      clearable
                      placeholder="post@/api/action"
                      @update:value="updateInteractionRule(ruleIndex, { api: $event || undefined })"
                    />
                  </n-form-item>
                </details>
              </div>
              <div v-else class="empty-config-box">
                暂无联动动作
              </div>
              <n-button size="small" dashed block @click="addInteractionRule">
                + 新增规则
              </n-button>
            </section>
          </n-form>
        </n-tab-pane>

        <n-tab-pane v-if="false" name="source">
          <template #tab>
            <span class="property-tab-label">
              <n-icon><CodeSlashOutline /></n-icon>
              源码
            </span>
          </template>
          <n-form label-placement="top" :show-feedback="false" class="property-form">
            <section class="panel-item source-panel">
              <div class="panel-title-row">
                <div>
                  <div class="panel-item-title">
                    当前组件 JSON
                  </div>
                  <div class="source-path">
                    {{ selectedComponent.id }}
                  </div>
                </div>
                <div class="source-actions">
                  <n-button size="tiny" tertiary @click="resetSelectedCodeDraft">
                    重置
                  </n-button>
                  <n-button size="tiny" type="primary" secondary @click="applySelectedCode">
                    应用
                  </n-button>
                </div>
              </div>
              <n-input
                :value="selectedCodeText"
                type="textarea"
                class="source-editor"
                :autosize="{ minRows: 16, maxRows: 28 }"
                @update:value="updateSelectedCodeDraft"
              />
              <div v-if="sourceError" class="source-error">
                {{ sourceError }}
              </div>
            </section>
          </n-form>
        </n-tab-pane>

</template>

<script>
import { useForgePropertyPanelApi } from '../forgePropertyPanelContext'
import { forgePropertyPanelLocalComponents } from '../forgePropertyPanelLocalComponents'

export default {
  name: 'SelectedExtraTabs',
  components: { ...forgePropertyPanelLocalComponents },
  setup() {
    return useForgePropertyPanelApi()
  },
}
</script>

<style scoped src="../forge-property-panel-shell.css"></style>
<style scoped src="../forge-property-panel-fields.css"></style>
