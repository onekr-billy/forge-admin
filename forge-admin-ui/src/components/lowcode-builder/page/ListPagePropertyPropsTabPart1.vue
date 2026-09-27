<template>
          <div v-show="propertyPanelTab === 'props'" class="prop-head">
            <div>
              <div class="prop-title">
                {{ selectedBlockMeta?.title || selectedBlock.blockType }}
              </div>
              <div class="prop-meta">
                {{ selectedBlock.gridW }} 列 × {{ selectedBlock.gridH }} 行
              </div>
            </div>
          </div>
            <div v-show="propertyPanelTab === 'props'" class="property-tab-content">
              <div class="property-search-anchor" data-property-search="属性 区块标题 组件标题 默认排序 字段 行间距" />
              <n-form-item label="区块标题">
                <n-input
                  :value="selectedBlock.label"
                  @update:value="patchBlock(selectedBlock.id, { label: $event })"
                />
              </n-form-item>

              <template v-if="selectedBlock.blockType === 'grid-layout'">
                <n-divider>栅格配置</n-divider>
                <div class="grid-preset-block">
                  <div class="grid-preset-title">
                    快速布局
                  </div>
                  <div class="grid-preset-list">
                    <button
                      v-for="preset in gridLayoutPresets"
                      :key="preset.key"
                      type="button"
                      class="grid-preset-item"
                      :title="preset.desc"
                      @click="applyGridLayoutPreset(preset.key)"
                    >
                      <span class="grid-preset-thumb" :style="preset.thumbStyle" aria-hidden="true">
                        <i
                          v-for="(cell, idx) in preset.previewCells"
                          :key="idx"
                          :style="{ gridColumn: `span ${cell.span}` }"
                        />
                      </span>
                      <strong>{{ preset.label }}</strong>
                    </button>
                  </div>
                  <div class="field-help">
                    一键生成格子结构；生成后可拖入或右键插入组件。已有格子里的组件会按位置尽量保留。
                  </div>
                </div>
                <!-- 统一栅格属性面板（spec 驱动）：与表单设计器共用同一份 grid spec 渲染。
                     列表画布消费全部属性（columns/gutter/rowGap/对齐/格子样式/背景），格子内容编辑走下方手写区 -->
                <SpecPropertyPanel
                  class="grid-spec-panel-stack"
                  :block-type="selectedBlock.blockType"
                  :model-props="selectedBlock.props || {}"
                  @update:prop="handleSpecPropUpdate"
                />
                <n-form-item label="格子内容" class="grid-cell-content-item">
                  <div class="container-child-editor">
                    <div
                      v-for="(cell, idx) in selectedGridCells"
                      :key="cell.key"
                      class="grid-cell-editor"
                    >
                      <div class="grid-cell-editor-head">
                        <n-input
                          :value="cell.title"
                          size="small"
                          :placeholder="`栅格 ${idx + 1}`"
                          @update:value="updateGridCell(idx, { title: $event })"
                        />
                        <n-input-number
                          :value="cell.span || 6"
                          :min="1"
                          :max="selectedBlock.props?.columns || 24"
                          size="small"
                          class="grid-cell-span-input"
                          placeholder="span"
                          @update:value="updateGridCell(idx, { span: $event || 1 })"
                        />
                        <n-button size="tiny" quaternary type="error" @click="removeGridCell(idx)">
                          删格
                        </n-button>
                      </div>
                      <div
                        v-for="child in (cell.children || [])"
                        :key="child.id"
                        class="container-child-row"
                      >
                        <span>{{ child.label || child.blockType }}</span>
                        <n-button size="tiny" quaternary type="error" @click="removeGridCellChild(selectedBlock.id, cell.key, child.id)">
                          删除
                        </n-button>
                      </div>
                      <n-select
                        :options="childBlockTypeOptions"
                        size="small"
                        placeholder="添加组件到此格"
                        clearable
                        @update:value="value => value && appendGridCellChild(selectedBlock.id, cell.key, value)"
                      />
                    </div>
                    <n-button size="small" dashed block @click="addGridCell">
                      + 添加格子
                    </n-button>
                  </div>
                </n-form-item>
              </template>

              <!-- 统一组件属性（designer-core spec 驱动）：常用属性平铺已收敛，
                   改用「更多属性」按钮 + 抽屉（与表单侧 ForgePropertyPanel 一致），避免组件属性面板撑满右侧栏 -->
              <template v-if="specPanelPropertyCount">
                <n-divider>组件属性</n-divider>
                <n-form-item :show-label="false">
                  <n-button size="small" dashed block @click="specDrawerVisible = true">
                    <template #icon>
                      <n-icon :size="14">
                        <SettingsOutline />
                      </n-icon>
                    </template>
                    更多属性
                  </n-button>
                </n-form-item>
              </template>
            </div>

</template>

<script>
import { useListPageDesignerApi } from './listPageDesignerContext'
import { listPageDesignerLocalComponents } from './listPageDesignerLocalComponents'

export default {
  components: { ...listPageDesignerLocalComponents },
  name: 'ListPagePropertyPropsTabPart1',
  setup() {
    return useListPageDesignerApi()
  },
}
</script>

<style scoped src="./list-page-grid-designer-shell.css"></style>
<style scoped src="./list-page-grid-designer-panels.css"></style>
