<template>
    <aside class="block-property-panel">
      <div class="property-panel-head">
        <div>
          <div class="property-panel-title">
            配置组件
          </div>
          <div class="property-panel-desc">
            选中画布组件后在这里调整属性、字段、动作和外观。
          </div>
        </div>
        <n-button
          class="panel-collapse-button"
          circle
          size="small"
          secondary
          title="收起配置区块"
          @click="propertyCollapsed = true"
        >
          <template #icon>
            <n-icon><ChevronForwardOutline /></n-icon>
          </template>
        </n-button>
      </div>
      <div class="property-search designer-panel-search">
        <n-input
          v-model:value="propertyKeyword"
          clearable
          size="small"
          placeholder="搜索配置项，例如：接口、弹窗、工具栏、树、字段"
        >
          <template #prefix>
            <n-icon><SearchOutline /></n-icon>
          </template>
        </n-input>
      </div>
      <div class="property-panel-tabs" aria-label="配置类型">
        <button type="button" :class="{ active: propertyPanelTab === 'props' }" @click="selectPropertyPanelTab('props')">
          <n-icon><SettingsOutline /></n-icon>
          属性
        </button>
        <button type="button" :class="{ active: propertyPanelTab === 'style' }" @click="selectPropertyPanelTab('style')">
          <n-icon><ColorPaletteOutline /></n-icon>
          样式
        </button>
        <button type="button" :class="{ active: propertyPanelTab === 'interaction' }" @click="selectPropertyPanelTab('interaction')">
          <n-icon><FlashOutline /></n-icon>
          交互
        </button>
      </div>
      <div :ref="setPropertyPanelRef" class="property-panel">
      <ListPagePropertyTreeModel />
        <div v-if="!selectedBlock" class="property-empty">
          <p>选中画布上的组件以编辑属性</p>
        </div>
        <div v-else class="property-body">
          <n-form size="small" label-placement="top" :show-feedback="false">
      <ListPagePropertyPropsTabPart1 />
      <ListPagePropertyPropsTabPart2 />
      <ListPagePropertyPropsTabPart3 />
      <ListPagePropertyStyleTab />
      <ListPagePropertyInteractionTab />
          </n-form>
        </div>
      </div>
    </aside>

</template>

<script>
import { useListPageDesignerApi } from './listPageDesignerContext'
import { listPageDesignerLocalComponents } from './listPageDesignerLocalComponents'
import ListPagePropertyTreeModel from './ListPagePropertyTreeModel.vue'
import ListPagePropertyPropsTabPart1 from './ListPagePropertyPropsTabPart1.vue'
import ListPagePropertyPropsTabPart2 from './ListPagePropertyPropsTabPart2.vue'
import ListPagePropertyPropsTabPart3 from './ListPagePropertyPropsTabPart3.vue'
import ListPagePropertyStyleTab from './ListPagePropertyStyleTab.vue'
import ListPagePropertyInteractionTab from './ListPagePropertyInteractionTab.vue'

export default {
  name: 'ListPageBlockPropertyPanel',
  components: { ...listPageDesignerLocalComponents, 
    ListPagePropertyTreeModel, ListPagePropertyPropsTabPart1, ListPagePropertyPropsTabPart2, ListPagePropertyPropsTabPart3, ListPagePropertyStyleTab, ListPagePropertyInteractionTab
  },
  setup() {
    return useListPageDesignerApi()
  },
}
</script>

<style scoped src="./list-page-grid-designer-shell.css"></style>
<style scoped src="./list-page-grid-designer-panels.css"></style>
