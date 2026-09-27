/** Local components for GridBlockRenderer. */
import { defineAsyncComponent } from 'vue'
import { ChevronBackOutline, CubeOutline } from '@vicons/ionicons5'
import QRCodeVue3 from 'qrcode-vue3'
import { DesignerGridRenderer } from '@/components/lowcode-builder/designer-core'
import AiCrudPage from '@/components/ai-form/AiCrudPage.vue'
import AiForm from '@/components/ai-form/AiForm.vue'
import AiTable from '@/components/ai-form/AiTable.vue'
import SignaturePad from '@/components/flow/SignaturePad.vue'
import IconRenderer from '@/components/IconRenderer.vue'
import FieldValueRenderer from '@/components/lowcode-builder/shared/FieldValueRenderer.vue'
import InlineRichText from '@/components/lowcode-builder/shared/InlineRichText.vue'
import PageWidgetRenderer from '@/components/lowcode-builder/shared/PageWidgetRenderer.vue'
import ContainerContextMenu from '@/components/lowcode-builder/shared/ContainerContextMenu.vue'
import WorkspaceSummaryMetrics from '@/views/workspace/WorkspaceSummaryMetrics.vue'
const TreeCrudTemplate = defineAsyncComponent(() => import('@/components/page-templates/TreeCrudTemplate.vue'))
const RuntimeListGridFlow = defineAsyncComponent(() => import('@/components/lowcode-builder/page/RuntimeListGridFlow.vue'))

export const gridBlockRendererLocalComponents = {
  ChevronBackOutline,
  CubeOutline,
  QRCodeVue3,
  DesignerGridRenderer,
  AiForm,
  AiTable,
  SignaturePad,
  IconRenderer,
  FieldValueRenderer,
  InlineRichText,
  PageWidgetRenderer,
  ContainerContextMenu,
  WorkspaceSummaryMetrics,
  AiCrudPage,
  TreeCrudTemplate,
  RuntimeListGridFlow,
}
