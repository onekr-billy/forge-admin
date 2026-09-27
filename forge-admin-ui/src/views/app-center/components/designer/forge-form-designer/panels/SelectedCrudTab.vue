<template>
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
        
</template>

<script>
import { useForgePropertyPanelApi } from '../forgePropertyPanelContext'
import { forgePropertyPanelLocalComponents } from '../forgePropertyPanelLocalComponents'

export default {
  name: 'SelectedCrudTab',
  components: { ...forgePropertyPanelLocalComponents },
  setup() {
    return useForgePropertyPanelApi()
  },
}
</script>

<style scoped src="../forge-property-panel-shell.css"></style>
<style scoped src="../forge-property-panel-fields.css"></style>
