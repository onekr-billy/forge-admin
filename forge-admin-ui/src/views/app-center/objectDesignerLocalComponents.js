/** Components for objectDesigner Options shell. */
import { defineAsyncComponent } from 'vue'
import IconSelector from '@/components/IconSelector.vue'
import BusinessObjectDesignerShell from './components/designer/BusinessObjectDesignerShell.vue'
import DesignerAsyncLoader from './components/designer/DesignerAsyncLoader.vue'

function defineDesignerAsyncComponent(loader) {
  return defineAsyncComponent({
    loader,
    loadingComponent: DesignerAsyncLoader,
    delay: 200,
    timeout: 60000,
  })
}

export const ObjectActionEditor = defineDesignerAsyncComponent(() => import('./components/designer/ObjectActionEditor.vue'))
export const BusinessAdvancedConfig = defineDesignerAsyncComponent(() => import('./components/designer/BusinessAdvancedConfig.vue'))
export const BusinessFieldManager = defineDesignerAsyncComponent(() => import('./components/designer/BusinessFieldManager.vue'))
export const BusinessFlowAppConfigPanel = defineDesignerAsyncComponent(() => import('./components/designer/BusinessFlowAppConfigPanel.vue'))
export const BusinessFormDesigner = defineDesignerAsyncComponent(() => import('./components/designer/BusinessFormDesigner.vue'))
export const BusinessListDesigner = defineDesignerAsyncComponent(() => import('./components/designer/BusinessListDesigner.vue'))
export const ObjectProcessReadOnlyPanel = defineDesignerAsyncComponent(() => import('./components/designer/ObjectProcessReadOnlyPanel.vue'))
export const BusinessPermissionFlowPanel = defineDesignerAsyncComponent(() => import('./components/designer/BusinessPermissionFlowPanel.vue'))
export const BusinessPublishChecklist = defineDesignerAsyncComponent(() => import('./components/designer/BusinessPublishChecklist.vue'))
export const BusinessRelationDesigner = defineDesignerAsyncComponent(() => import('./components/designer/BusinessRelationDesigner.vue'))
export const BusinessTableMappingSummary = defineDesignerAsyncComponent(() => import('./components/designer/BusinessTableMappingSummary.vue'))
export const FormulaFunctionMarket = defineDesignerAsyncComponent(() => import('./components/designer/formula/FormulaFunctionMarket.vue'))

export const objectDesignerLocalComponents = {
  IconSelector,
  BusinessObjectDesignerShell,
  DesignerAsyncLoader,
  ObjectActionEditor,
  BusinessAdvancedConfig,
  BusinessFieldManager,
  BusinessFlowAppConfigPanel,
  BusinessFormDesigner,
  BusinessListDesigner,
  ObjectProcessReadOnlyPanel,
  BusinessPermissionFlowPanel,
  BusinessPublishChecklist,
  BusinessRelationDesigner,
  BusinessTableMappingSummary,
  FormulaFunctionMarket,
}
