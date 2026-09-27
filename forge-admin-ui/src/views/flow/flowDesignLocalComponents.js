/** Components for flow design Options shell. */
import { NTag, NTreeSelect } from 'naive-ui'
import NodePropertiesPanel from '@/components/bpmn/NodePropertiesPanel.vue'
import FlowPropertyPanelShell from '@/components/flow/FlowPropertyPanelShell.vue'
import FlowFormCreateDesigner from '@/components/form-create/FlowFormCreateDesigner.vue'
import FlowFormCreateRenderer from '@/components/form-create/FlowFormCreateRenderer.vue'
import BusinessFlowFormAssetSelect from '@/views/app-center/components/designer/BusinessFlowFormAssetSelect.vue'
import VersionHistory from './version.vue'
import { DingFlowDesigner } from '@/components/flow-designer'

export const flowDesignLocalComponents = {
  NTag,
  NTreeSelect,
  NodePropertiesPanel,
  FlowPropertyPanelShell,
  FlowFormCreateDesigner,
  FlowFormCreateRenderer,
  BusinessFlowFormAssetSelect,
  VersionHistory,
  DingFlowDesigner,
}
