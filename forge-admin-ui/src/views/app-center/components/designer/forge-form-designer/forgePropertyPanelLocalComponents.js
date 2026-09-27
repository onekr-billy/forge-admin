/**
 * Components/icons used by ForgePropertyPanel shell and extracted panels.
 */
import {
  BanOutline,
  CloseOutline,
  CodeSlashOutline,
  ColorPaletteOutline,
  EyeOffOutline,
  EyeOutline,
  FlashOutline,
  GridOutline,
  LayersOutline,
  ServerOutline,
  SettingsOutline,
  ToggleOutline,
} from '@vicons/ionicons5'
import draggable from 'vuedraggable'
import IconRenderer from '@/components/IconRenderer.vue'
import SpecPropertyPanel from '@/components/lowcode-builder/designer-core/panel/SpecPropertyPanel.vue'
import DictTypeSelect from '@/components/lowcode-builder/shared/DictTypeSelect.vue'
import RuntimeRulesEditor from '@/components/lowcode-builder/shared/RuntimeRulesEditor.vue'
import BusinessFieldPropertyPanel from '../BusinessFieldPropertyPanel.vue'
import FieldEventRulesEditor from './FieldEventRulesEditor.vue'
import FieldLinkageRulesEditor from './FieldLinkageRulesEditor.vue'
import FieldDefaultValueEditor from './panels/FieldDefaultValueEditor.vue'
import FieldNumberConstraintPanel from './panels/FieldNumberConstraintPanel.vue'
import FormAssetsPanel from './panels/FormAssetsPanel.vue'
import FormInitPanel from './panels/FormInitPanel.vue'
import FormLayoutPanel from './panels/FormLayoutPanel.vue'
import FormSubTablePanel from './panels/FormSubTablePanel.vue'
import RecordSelectorConfigDialog from './panels/RecordSelectorConfigDialog.vue'
import SubTableInlineEditor from './panels/SubTableInlineEditor.vue'
import {
  BitableAttachmentIcon,
  BitableCalendarIcon,
  BitableDragIcon,
  BitableLookupIcon,
  BitableMemberIcon,
  BitableNumberIcon,
  BitableSelectIcon,
  BitableStyleIcon,
} from './bitableIcons'

export const forgePropertyPanelLocalComponents = {
  BanOutline,
  CloseOutline,
  CodeSlashOutline,
  ColorPaletteOutline,
  EyeOffOutline,
  EyeOutline,
  FlashOutline,
  GridOutline,
  LayersOutline,
  ServerOutline,
  SettingsOutline,
  ToggleOutline,
  BitableDragIcon,
  BitableStyleIcon,
  BitableSelectIcon,
  BitableNumberIcon,
  BitableCalendarIcon,
  BitableAttachmentIcon,
  BitableMemberIcon,
  BitableLookupIcon,
  draggable,
  IconRenderer,
  SpecPropertyPanel,
  DictTypeSelect,
  RuntimeRulesEditor,
  BusinessFieldPropertyPanel,
  FieldEventRulesEditor,
  FieldLinkageRulesEditor,
  FieldDefaultValueEditor,
  FieldNumberConstraintPanel,
  FormAssetsPanel,
  FormInitPanel,
  FormLayoutPanel,
  FormSubTablePanel,
  RecordSelectorConfigDialog,
  SubTableInlineEditor,
}
