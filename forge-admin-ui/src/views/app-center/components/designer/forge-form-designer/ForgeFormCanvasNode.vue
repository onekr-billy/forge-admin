<template>
  <div
    class="node-wrap"
    :class="{ selected: isSelected }"
    :style="nodeWrapStyle"
    :data-forge-node-id="component.id"
    :data-forge-parent-id="parentId"
    :data-forge-index="index"
    @dragover.prevent.stop="handleNodeDragOver"
    @dragleave="handleNodeDragLeave"
    @drop.stop="handleNodeDrop"
  >
    <div
      class="drop-line before"
      :class="{ active: beforeActive }"
      :style="dropIndicatorStyle"
    />
    <article
      ref="nodeRef"
      class="canvas-node"
      :class="[`node-${component.componentKey}`, { 'selected': isSelected, 'layout': isLayout, 'dragging': dragging, 'resizing': resizing, 'structural-slot': isStructuralSlot, 'border-hidden': designerBorderHidden }]"
      :style="nodeCustomStyle"
      draggable="false"
      tabindex="0"
      @click.stop="selectNode"
      @focus="selectNode"
    >
      <!-- 统一操作条：表单字段 / 栅格格子 -->
      <DesignerNodeOverlay
        v-if="!isStructuralSlot"
        mode="overlay"
        :show-required-switch="isField"
        :required="Boolean(component.validation?.required)"
        :show-menu="true"
        :menu-options="nodeMenuOptions"
        @toggle-required="toggleRequired"
        @duplicate="duplicateNode"
        @delete="removeNode"
        @drag-start="startPointerDrag"
        @menu-select="handleNodeMenuSelect"
      />
      <DesignerNodeOverlay
        v-else-if="isGridColumn"
        mode="column"
        :show-delete="true"
        :delete-disabled="columnRemovalLocked"
        :delete-title="columnDeleteTitle"
        :show-duplicate="false"
        :show-required-switch="false"
        :show-drag-handle="false"
        :show-menu="false"
        @delete="removeGridColumn"
      />
      <div v-if="isSelected" class="node-resize-handles" @click.stop>
        <span
          v-for="direction in resizeDirections"
          :key="direction"
          class="resize-anchor"
          :class="`anchor-${direction}`"
          :title="resizeAnchorTitle(direction)"
          @pointerdown.stop.prevent="startNodeResize(direction, $event)"
        />
      </div>

      <AiFormItem
        v-if="isField"
        :field="previewField"
        :value="previewValue"
        :form-data="previewFormData"
        :context="previewContext"
        @update:value="previewValue = $event"
      />
      <AiFormSectionTitle
        v-else-if="isFormDividerComponent(component)"
        class="form-divider-preview"
        v-bind="buildFormDividerProps(component)"
      />
      <AiFormGroupTitle
        v-else-if="isTitle"
        v-bind="buildGroupTitleProps(component)"
      />
      <div v-else-if="isSubTableComponent" class="subtable-preview" :class="{ unconfigured: !subTableConfigured }">
        <div class="subtable-preview-header">
          <div class="subtable-preview-title">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <path d="M3 3h18v18H3V3zm2 2v14h14V5H5zm2 2h10v2H7V7zm0 4h10v2H7v-2zm0 4h6v2H7v-2z" fill="currentColor" />
            </svg>
            <strong>{{ component.props?.header || '关联子表' }}</strong>
          </div>
          <span class="subtable-preview-actions">
            <span class="subtable-mode-badge">{{ subTableDisplayModeLabel }}</span>
            <n-button size="tiny" secondary @click.stop="emit('configureSubTable', component.props?.relationKey || '')">
              {{ subTableConfigured ? '编辑' : '配置' }}
            </n-button>
          </span>
        </div>
        <div v-if="!subTableConfigured" class="subtable-preview-empty">
          点击「配置」选择目标对象和可见字段
        </div>
        <template v-else>
          <div v-if="component.props?.modelCode" class="subtable-preview-meta">
            {{ component.props.modelCode }}
          </div>
          <div v-if="subTableColumns.length" class="subtable-preview-fields">
            <span
              v-for="col in subTableColumns.slice(0, 6)"
              :key="col.fieldCode || col"
              class="subtable-field-tag"
            >
              {{ col.fieldLabel || col.fieldCode || col }}
            </span>
            <span v-if="subTableColumns.length > 6" class="subtable-field-more">
              +{{ subTableColumns.length - 6 }}
            </span>
          </div>
          <div v-else class="subtable-preview-grid">
            <span v-for="column in 4" :key="column" class="subtable-preview-cell" />
          </div>
        </template>
      </div>
      <div v-else-if="isButton" class="button-preview">
        <n-button
          :type="component.props?.type || 'primary'"
          :size="component.props?.size || 'medium'"
          :secondary="!!component.props?.secondary"
          :tertiary="!!component.props?.tertiary"
          :quaternary="!!component.props?.quaternary"
          :dashed="!!component.props?.dashed"
          :round="!!component.props?.round"
          :block="!!component.props?.block"
          :loading="!!component.props?.loading"
          :disabled="!!component.props?.disabled"
        >
          <template v-if="component.props?.icon" #icon>
            <IconRenderer :icon="component.props.icon" :size="16" />
          </template>
          {{ component.props?.text || component.label || '按钮' }}
        </n-button>
      </div>
      <div
        v-else-if="isCrudBlock"
        class="crud-preview"
        @dragenter.prevent.stop="handleInsideDragOver"
        @dragover.prevent.stop="handleInsideDragOver"
        @drop.stop="handleInsideDrop"
      >
        <div class="crud-live-preview">
          <AiCrudPage
            v-bind="crudPreviewOptions"
            :lazy="true"
            :api-config="crudApiConfig"
            :row-key="component.props?.rowKey || 'id'"
            :columns="crudPreviewColumns"
            :search-schema="crudSearchSchema"
            :edit-schema="crudEditSchema"
          />
        </div>
        <div v-if="!component.children?.length" class="crud-empty-drop" :class="{ active: activeInside }">
          拖入字段生成查询、表格列和编辑表单
        </div>
      </div>
      <n-card
        v-else-if="isCardLayout"
        class="real-layout real-layout-card"
        :size="component.props?.size || 'small'"
        :bordered="component.props?.bordered !== false"
        :embedded="!!component.props?.embedded"
        :segmented="component.props?.segmented || false"
        :hoverable="!!component.props?.hoverable"
      >
        <template #header>
          {{ component.props?.header || component.label || '卡片分组' }}
        </template>
        <div
          class="layout-children real-layout-children"
          :class="{ active: activeInside }"
          @dragenter.prevent.stop="handleInsideDragOver"
          @dragover.prevent.stop="handleInsideDragOver"
          @drop.stop="handleInsideDrop"
        >
          <div v-if="!component.children?.length" class="empty-child-zone" :class="{ active: activeInside }">
            拖入字段或布局
          </div>
          <ForgeFormCanvasNode
            v-for="(child, childIndex) in component.children"
            :key="child.id"
            :component="child"
            :fields="fields"
            :schema="schema"
            :selected-id="selectedId"
            :depth="depth + 1"
            :parent-id="component.id"
            :index="childIndex"
            @select="$emit('select', $event)"
            @update:schema="$emit('update:schema', $event)"
            @configure="$emit('configure', $event)"
            @drop-before="event => handleChildDrop(childIndex, event)"
            @drop-after="event => handleChildDrop(childIndex + 1, event)"
          />
        </div>
      </n-card>
      <n-tabs
        v-else-if="isTabsLayout"
        class="real-layout real-layout-tabs"
        :type="component.props?.type || 'line'"
        :size="component.props?.size || 'medium'"
        :placement="component.props?.placement || 'top'"
        :trigger="component.props?.trigger || 'click'"
        :animated="component.props?.animated !== false"
        :closable="!!component.props?.closable"
        :addable="!!component.props?.addable"
        :justify-content="component.props?.justifyContent"
        :tabs-padding="component.props?.tabsPadding"
        :pane-style="component.props?.paneStyle"
        :tab-style="component.props?.tabStyle"
        :default-value="component.children?.[0]?.props?.name || component.children?.[0]?.id"
      >
        <n-tab-pane
          v-for="(child, childIndex) in component.children"
          :key="child.id"
          :name="child.props?.name || child.id"
          :tab="child.props?.label || child.label || `标签 ${childIndex + 1}`"
        >
          <ForgeFormCanvasNode
            :component="child"
            :fields="fields"
            :schema="schema"
            :selected-id="selectedId"
            :depth="depth + 1"
            :parent-id="component.id"
            :index="childIndex"
            @select="$emit('select', $event)"
            @update:schema="$emit('update:schema', $event)"
            @configure="$emit('configure', $event)"
            @drop-before="event => handleChildDrop(childIndex, event)"
            @drop-after="event => handleChildDrop(childIndex + 1, event)"
          />
        </n-tab-pane>
        <n-tab-pane v-if="!component.children?.length" name="empty" tab="标签一">
          <div
            class="layout-children real-layout-children"
            :class="{ active: activeInside }"
            @dragenter.prevent.stop="handleInsideDragOver"
            @dragover.prevent.stop="handleInsideDragOver"
            @drop.stop="handleInsideDrop"
          >
            <div class="empty-child-zone" :class="{ active: activeInside }">
              拖入标签页
            </div>
          </div>
        </n-tab-pane>
      </n-tabs>
      <n-collapse
        v-else-if="isCollapseLayout"
        class="real-layout real-layout-collapse"
        :accordion="!!component.props?.accordion"
        :arrow-placement="component.props?.arrowPlacement || 'left'"
        :display-directive="component.props?.displayDirective || 'if'"
        :trigger-areas="component.props?.triggerAreas || ['main', 'arrow']"
        :default-expanded-names="component.props?.defaultExpandedNames || component.children?.map(child => child.props?.name || child.id)"
        :expanded-names="component.props?.expandedNames"
      >
        <n-collapse-item
          v-for="(child, childIndex) in component.children"
          :key="child.id"
          :name="child.props?.name || child.id"
          :title="child.props?.title || child.label || `分组 ${childIndex + 1}`"
        >
          <ForgeFormCanvasNode
            :component="child"
            :fields="fields"
            :schema="schema"
            :selected-id="selectedId"
            :depth="depth + 1"
            :parent-id="component.id"
            :index="childIndex"
            @select="$emit('select', $event)"
            @update:schema="$emit('update:schema', $event)"
            @configure="$emit('configure', $event)"
            @drop-before="event => handleChildDrop(childIndex, event)"
            @drop-after="event => handleChildDrop(childIndex + 1, event)"
          />
        </n-collapse-item>
        <n-collapse-item v-if="!component.children?.length" name="empty" title="分组一">
          <div
            class="layout-children real-layout-children"
            :class="{ active: activeInside }"
            @dragenter.prevent.stop="handleInsideDragOver"
            @dragover.prevent.stop="handleInsideDragOver"
            @drop.stop="handleInsideDrop"
          >
            <div class="empty-child-zone" :class="{ active: activeInside }">
              拖入折叠项
            </div>
          </div>
        </n-collapse-item>
      </n-collapse>
      <div
        v-else-if="isTabPaneLayout"
        class="layout-children real-layout-children tab-pane-drop-zone"
        :class="{ active: activeInside }"
        @dragenter.prevent.stop="handleInsideDragOver"
        @dragover.prevent.stop="handleInsideDragOver"
        @drop.stop="handleInsideDrop"
      >
        <div v-if="!component.children?.length" class="empty-child-zone" :class="{ active: activeInside }">
          拖入组件到当前标签页
        </div>
        <ForgeFormCanvasNode
          v-for="(child, childIndex) in component.children"
          :key="child.id"
          :component="child"
          :fields="fields"
          :schema="schema"
          :selected-id="selectedId"
          :depth="depth + 1"
          :parent-id="component.id"
          :index="childIndex"
          @select="$emit('select', $event)"
          @update:schema="$emit('update:schema', $event)"
          @configure="$emit('configure', $event)"
          @drop-before="event => handleChildDrop(childIndex, event)"
          @drop-after="event => handleChildDrop(childIndex + 1, event)"
        />
      </div>
      <PageWidgetRenderer
        v-else-if="isPageWidget"
        :component-key="component.componentKey"
        :props-data="component.props || {}"
        @update:props-data="handlePageWidgetPropsUpdate"
      />
      <!-- 栅格行：使用统一 DesignerGridRenderer -->
      <div
        v-else-if="isGridRow"
        class="layout-children grid-row-container"
        :class="{ active: activeInside }"
        @dragenter.prevent.stop="handleInsideDragOver"
        @dragover.prevent.stop="handleInsideDragOver"
        @drop.stop="handleInsideDrop"
      >
        <div v-if="!component.children?.length" class="empty-child-zone" :class="{ active: activeInside }">
          拖入栅格列
        </div>
        <DesignerGridRenderer
          :columns="resolveRowColumns(component)"
          :gutter="resolveGap(component.props?.gutter, 12)"
          :row-gap="resolveGap(component.props?.rowGap, 8)"
          :show-cell-border="component.props?.showCellBorder !== false"
          :cell-background="component.props?.cellBackground"
          :cells="gridRowCells"
        >
          <template #cell="{ cell }">
            <ForgeFormCanvasNode
              :component="cell._component"
              :fields="fields"
              :schema="schema"
              :selected-id="selectedId"
              :depth="depth + 1"
              :parent-id="component.id"
              :index="cell._index"
              @select="$emit('select', $event)"
              @update:schema="$emit('update:schema', $event)"
              @configure="$emit('configure', $event)"
              @drop-before="event => handleChildDrop(cell._index, event)"
              @drop-after="event => handleChildDrop(cell._index + 1, event)"
            />
          </template>
        </DesignerGridRenderer>
      </div>
      <div
        v-else
        class="layout-children"
        :class="{ 'table-layout-children': isTableLayout, 'active': activeInside }"
        :style="childrenGridStyle"
        @dragenter.prevent.stop="handleInsideDragOver"
        @dragover.prevent.stop="handleInsideDragOver"
        @drop.stop="handleInsideDrop"
      >
        <div v-if="!component.children?.length" class="empty-child-zone" :class="{ active: activeInside }">
          拖入字段或布局
        </div>
        <ForgeFormCanvasNode
          v-for="(child, childIndex) in component.children"
          :key="child.id"
          :component="child"
          :fields="fields"
          :schema="schema"
          :selected-id="selectedId"
          :depth="depth + 1"
          :parent-id="component.id"
          :index="childIndex"
          @select="$emit('select', $event)"
          @update:schema="$emit('update:schema', $event)"
          @configure="$emit('configure', $event)"
          @drop-before="event => handleChildDrop(childIndex, event)"
          @drop-after="event => handleChildDrop(childIndex + 1, event)"
        />
      </div>
    </article>
    <div
      class="drop-line after"
      :class="{ active: afterActive }"
      :style="dropIndicatorStyle"
    />
  </div>
</template>

<script>
import { forgeFormCanvasNodeLocalComponents } from './forgeFormCanvasNodeLocalComponents'
import { useForgeFormCanvasNode } from './composables/useForgeFormCanvasNode'

export default {
  name: 'ForgeFormCanvasNode',
  components: {
    ...forgeFormCanvasNodeLocalComponents,
  },
  props: {
  component: {
    type: Object,
    required: true,
  },
  schema: {
    type: Object,
    required: true,
  },
  fields: {
    type: Array,
    default: () => [],
  },
  selectedId: {
    type: String,
    default: '',
  },
  depth: {
    type: Number,
    default: 0,
  },
  parentId: {
    type: String,
    default: '',
  },
  index: {
    type: Number,
    default: 0,
  },
},
  emits: ['select', 'update:schema', 'dropBefore', 'dropAfter', 'configure', 'configureSubTable'],
  setup(props, { emit, expose }) {
    return useForgeFormCanvasNode(props, emit, expose)
  },
}
</script>

<style scoped src="./forgeFormCanvasNode.css"></style>
