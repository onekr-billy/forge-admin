/** StructuredListPageDesigner.vue setup part 1. */
import { SearchOutline } from '@vicons/ionicons5'
import { NButton, NColorPicker, NIcon, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import { computed, defineComponent, h, nextTick, ref } from 'vue'
import draggable from 'vuedraggable'
import CrudDefaultParamsEditor from '../CrudDefaultParamsEditor.vue'
import CrudHookRulesEditor from '../CrudHookRulesEditor.vue'
import GridBlockRenderer from '../GridBlockRenderer.vue'
import { isPageFieldVisible } from '../page-schema'
export function applyStructuredListPageDesignerPart1(props, emit) {
  const __impl = {}
  const mut = {}

  function resolveFields(zone, fallback) {
    if (!zone || zone.enabled === false)
      return []
    const refs = zone.fieldRefs?.length
      ? zone.fieldRefs
      : props.fields.filter(fallback).map(field => field.field)
    return refs.map(ref => fieldMap.value.get(ref)).filter(Boolean)
  }

  function firstUsableField() {
    return props.fields.find(field => !field.systemField)?.field || props.fields[0]?.field || 'name'
  }

  function uniqueRefs(refs = []) {
    return Array.from(new Set(refs.filter(Boolean)))
  }

  function resolveDefaultSearchComponentType(field = {}) {
    const componentType = field.componentType || field.dataType || 'input'
    if (field.dictType)
      return 'dictSelect'
    if (['int', 'bigint', 'decimal', 'double', 'float'].includes(field.dataType))
      return 'number'
    return componentType === 'inputNumber' ? 'number' : componentType
  }

  function resolveDefaultTableRenderType(field = {}) {
    const componentType = field.componentType || ''
    if (field.dictType)
      return 'dictTag'
    if (componentType === 'orgTreeSelect')
      return 'orgName'
    if (componentType === 'userSelect')
      return 'userName'
    if (componentType === 'regionTreeSelect')
      return 'regionName'
    if (componentType === 'fileUpload' || componentType === 'imageUpload')
      return componentType
    return ''
  }

  function isNameRenderType(renderType) {
    return ['orgName', 'userName', 'regionName', 'fileUpload', 'imageUpload'].includes(renderType)
  }

  function updateZoneRefs(zoneKey, refs) {
    patchZone(zoneKey, { fieldRefs: refs }, createGridPatchForZone(zoneKey, { fieldRefs: refs }))
  }

  function updateTableProp(key, value) {
    patchZone('table', {
      props: {
        ...(tableZone.value?.props || {}),
        [key]: value,
      },
    }, createGridPatchForZone('table', {
      props: {
        [key]: value,
      },
    }))
  }

  function normalizeFormOpenModePatch(value) {
    const formOpenMode = value === 'tabWorkspace' ? 'tabWorkspace' : (['modal', 'drawer', 'flat'].includes(value) ? value : 'modal')
    return {
      formOpenMode,
      modalType: ['modal', 'drawer'].includes(formOpenMode) ? formOpenMode : 'modal',
    }
  }

  function resolveTableFormOpenMode(tableProps = {}) {
    const value = tableProps.formOpenMode || tableProps.modalType || 'modal'
    const mode = String(value || '').trim()
    if (mode === 'tabWorkspace' || mode.toLowerCase() === 'tabworkspace')
      return 'tabWorkspace'
    const normalized = mode.toLowerCase()
    return ['modal', 'drawer', 'flat'].includes(normalized) ? normalized : 'modal'
  }

  function resolveTableModalType(formOpenMode, tableProps = {}) {
    if (['modal', 'drawer'].includes(formOpenMode))
      return formOpenMode
    const normalized = String(tableProps.modalType || '').trim().toLowerCase()
    return ['modal', 'drawer'].includes(normalized) ? normalized : 'modal'
  }

  function updateTableFormOpenMode(value) {
    const patch = normalizeFormOpenModePatch(value)
    patchZone('table', {
      props: {
        ...(tableZone.value?.props || {}),
        ...patch,
      },
    }, createGridPatchForZone('table', {
      props: patch,
    }))
  }

  function applyTableGlobalAlign(value) {
    const align = ['left', 'center', 'right'].includes(value) ? value : 'left'
    const zone = tableZone.value
    if (!zone)
      return
    const nextSettings = { ...(zone.props?.fieldSettings || {}) }
    ;(zone.fieldRefs || []).forEach((fieldName) => {
      nextSettings[fieldName] = {
        ...(nextSettings[fieldName] || {}),
        align,
      }
    })
    patchZone('table', {
      props: {
        ...(zone.props || {}),
        globalAlign: align,
        fieldSettings: nextSettings,
      },
    }, createGridPatchForZone('table', {
      props: {
        globalAlign: align,
        fieldSettings: nextSettings,
      },
    }))
  }

  function updateTableRowGap(value) {
    updateTableProp('rowGap', Math.max(0, Math.min(32, Number(value ?? 8))))
  }

  function updateTreeConfig(key, value) {
    patchZone('table', {
      props: {
        ...(tableZone.value?.props || {}),
        treeConfig: {
          ...(treeConfig.value || {}),
          enabled: true,
          [key]: value,
        },
      },
    })
  }

  function addTableEvent() {
    updateTableProp('events', [
      ...tableEvents.value,
      {
        id: `evt_${Date.now()}`,
        trigger: 'rowClick',
        action: 'none',
        targetPageKey: '',
        targetBlockId: '',
        description: '',
        params: [],
      },
    ])
  }

  function updateTableEvent(eventIdx, patch) {
    const list = [...tableEvents.value]
    list[eventIdx] = { ...(list[eventIdx] || {}), ...patch }
    updateTableProp('events', list)
  }

  function removeTableEvent(eventIdx) {
    const list = [...tableEvents.value]
    list.splice(eventIdx, 1)
    updateTableProp('events', list)
  }

  function addTableCustomAction() {
    updateTableProp('customActions', [
      ...tableCustomActions.value,
      {
        key: `custom_${Date.now()}`,
        label: '操作按钮',
        position: 'row',
        type: 'primary',
        actionType: 'route',
        routePath: '',
        targetPageKey: '',
        params: [],
      },
    ])
  }

  function updateTableCustomAction(actionIdx, patch) {
    const list = [...tableCustomActions.value]
    list[actionIdx] = { ...(list[actionIdx] || {}), ...patch }
    updateTableProp('customActions', list)
  }

  function removeTableCustomAction(actionIdx) {
    const list = [...tableCustomActions.value]
    list.splice(actionIdx, 1)
    updateTableProp('customActions', list)
  }

  function configSectionVisible(keywords = []) {
    const keyword = String(configKeyword.value || '').trim().toLowerCase()
    if (!keyword)
      return true
    return keywords.some(item => String(item || '').toLowerCase().includes(keyword))
  }

  function updateTableHookRules(rules) {
    patchZone('table', {
      props: {
        ...(tableZone.value?.props || {}),
        crudHookRules: rules || {},
        beforeSubmitRules: [],
      },
    }, createGridPatchForZone('table', {
      props: {
        crudHookRules: rules || {},
        beforeSubmitRules: [],
      },
    }))
  }

  function resolveTableDefaultParams() {
    const props = tableZone.value?.props || {}
    return {
      publicParams: props.publicParams || {},
      publicQuery: props.publicQuery || {},
      formDefaultValues: props.formDefaultValues || {},
      submitDefaultParams: props.submitDefaultParams || {},
    }
  }

  function updateTableDefaultParams(params = {}) {
    if (isSameDefaultParams(resolveTableDefaultParams(), params))
      return
    patchZone('table', {
      props: {
        ...(tableZone.value?.props || {}),
        publicParams: params.publicParams || {},
        publicQuery: params.publicQuery || {},
        formDefaultValues: params.formDefaultValues || {},
        submitDefaultParams: params.submitDefaultParams || {},
      },
    }, createGridPatchForZone('table', {
      props: {
        publicParams: params.publicParams || {},
        publicQuery: params.publicQuery || {},
        formDefaultValues: params.formDefaultValues || {},
        submitDefaultParams: params.submitDefaultParams || {},
      },
    }))
  }

  function isSameDefaultParams(left = {}, right = {}) {
    return JSON.stringify(normalizeDefaultParams(left)) === JSON.stringify(normalizeDefaultParams(right))
  }

  function normalizeDefaultParams(source = {}) {
    return ['publicParams', 'publicQuery', 'formDefaultValues', 'submitDefaultParams'].reduce((result, key) => {
      const params = source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])
        ? source[key]
        : {}
      result[key] = Object.keys(params).sort().reduce((next, paramKey) => {
        next[paramKey] = params[paramKey]
        return next
      }, {})
      return result
    }, {})
  }

  function eventTriggerText(trigger) {
    return eventTriggerOptions.find(item => item.value === trigger)?.label || trigger || '触发'
  }

  function eventActionText(action) {
    return eventActionOptions.find(item => item.value === action)?.label || action || '动作'
  }

  function updateZoneFieldSetting(zoneKey, payload) {
    const zone = findZone(zoneKey)
    if (!zone || !payload?.field)
      return
    patchZone(zoneKey, {
      props: {
        ...(zone.props || {}),
        fieldSettings: {
          ...(zone.props?.fieldSettings || {}),
          [payload.field]: payload.settings,
        },
      },
    }, createGridPatchForZone(zoneKey, {
      props: {
        fieldSettings: {
          ...(zone.props?.fieldSettings || {}),
          [payload.field]: payload.settings,
        },
      },
    }))
  }

  function openFieldPanel(zoneKey) {
    activeFieldZone.value = zoneKey
    configCollapsed.value = false
    nextTick(() => {
      fieldEditorAnchor.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    })
  }

  function collapseConfigPanel() {
    configExpanded.value = false
    configCollapsed.value = true
  }

  function normalizeParamName(value) {
    return String(value || '').trim().replace(/[^\w.$-]/g, '')
  }

  function patchZone(zoneKey, patch, gridPatch = null) {
    const zones = (props.modelValue.zones || []).map((zone) => {
      if (zone.zoneKey !== zoneKey)
        return zone
      return {
        ...zone,
        ...patch,
        props: patch.props ? { ...(zone.props || {}), ...patch.props } : zone.props || {},
      }
    })
    const nextValue = { ...props.modelValue, zones }
    if (gridPatch) {
      nextValue.listGridLayout = patchCrudGridLayout(props.modelValue.listGridLayout, gridPatch)
      nextValue.pages = patchPagesGridLayout(props.modelValue.pages || [], 'list', nextValue.listGridLayout)
    }
    emit('update:modelValue', nextValue)
  }

  function patchPagesGridLayout(pages = [], pageKey = 'list', gridLayout) {
    if (!Array.isArray(pages) || !pages.length)
      return pages
    return pages.map(page => page.pageKey === pageKey
      ? { ...page, gridLayout }
      : page)
  }

  function createGridPatchForZone(zoneKey, patch) {
    if (zoneKey === 'search') {
      return {
        blockTypes: ['AiCrudPage', 'search-form'],
        fieldRefs: patch.fieldRefs,
        props: patch.props,
      }
    }
    if (zoneKey === 'table') {
      return {
        blockTypes: ['AiCrudPage', 'data-table', 'AiTable'],
        fieldRefs: patch.fieldRefs,
        props: patch.props,
      }
    }
    return null
  }

  function patchCrudGridLayout(layout = {}, gridPatch = {}) {
    if (!layout?.items?.length)
      return layout
    const blockTypes = new Set(gridPatch.blockTypes || [])
    return {
      ...layout,
      items: (layout.items || []).map((item) => {
        if (!blockTypes.has(item.blockType))
          return item
        return {
          ...item,
          fieldRefs: Array.isArray(gridPatch.fieldRefs) ? gridPatch.fieldRefs : item.fieldRefs,
          props: gridPatch.props
            ? {
                ...(item.props || {}),
                ...gridPatch.props,
                fieldSettings: gridPatch.props.fieldSettings
                  ? {
                      ...(item.props?.fieldSettings || {}),
                      ...gridPatch.props.fieldSettings,
                    }
                  : item.props?.fieldSettings,
              }
            : item.props,
        }
      }),
    }
  }

  function resetSearchFields() {
    updateZoneRefs('search', props.fields.filter(field => isPageFieldVisible(field, 'search')).map(field => field.field))
  }

  function resetTableFields() {
    updateZoneRefs('table', props.fields.filter(field => isPageFieldVisible(field, 'table')).map(field => field.field))
  }
  __impl.resolveFields = resolveFields
  __impl.firstUsableField = firstUsableField
  __impl.uniqueRefs = uniqueRefs
  __impl.resolveDefaultSearchComponentType = resolveDefaultSearchComponentType
  __impl.resolveDefaultTableRenderType = resolveDefaultTableRenderType
  __impl.isNameRenderType = isNameRenderType
  __impl.updateZoneRefs = updateZoneRefs
  __impl.updateTableProp = updateTableProp
  __impl.normalizeFormOpenModePatch = normalizeFormOpenModePatch
  __impl.resolveTableFormOpenMode = resolveTableFormOpenMode
  __impl.resolveTableModalType = resolveTableModalType
  __impl.updateTableFormOpenMode = updateTableFormOpenMode
  __impl.applyTableGlobalAlign = applyTableGlobalAlign
  __impl.updateTableRowGap = updateTableRowGap
  __impl.updateTreeConfig = updateTreeConfig
  __impl.addTableEvent = addTableEvent
  __impl.updateTableEvent = updateTableEvent
  __impl.removeTableEvent = removeTableEvent
  __impl.addTableCustomAction = addTableCustomAction
  __impl.updateTableCustomAction = updateTableCustomAction
  __impl.removeTableCustomAction = removeTableCustomAction
  __impl.configSectionVisible = configSectionVisible
  __impl.updateTableHookRules = updateTableHookRules
  __impl.resolveTableDefaultParams = resolveTableDefaultParams
  __impl.updateTableDefaultParams = updateTableDefaultParams
  __impl.isSameDefaultParams = isSameDefaultParams
  __impl.normalizeDefaultParams = normalizeDefaultParams
  __impl.eventTriggerText = eventTriggerText
  __impl.eventActionText = eventActionText
  __impl.updateZoneFieldSetting = updateZoneFieldSetting
  __impl.openFieldPanel = openFieldPanel
  __impl.collapseConfigPanel = collapseConfigPanel
  __impl.normalizeParamName = normalizeParamName
  __impl.patchZone = patchZone
  __impl.patchPagesGridLayout = patchPagesGridLayout
  __impl.createGridPatchForZone = createGridPatchForZone
  __impl.patchCrudGridLayout = patchCrudGridLayout
  __impl.resetSearchFields = resetSearchFields
  __impl.resetTableFields = resetTableFields

  return {
    props, emit, __impl, mut, addTableCustomAction, addTableEvent, applyTableGlobalAlign, collapseConfigPanel,
    configSectionVisible, createGridPatchForZone, eventActionText, eventTriggerText, findZone, firstUsableField, isNameRenderType, isSameDefaultParams,
    normalizeDefaultParams, normalizeFormOpenModePatch, normalizeParamName, openFieldPanel, patchCrudGridLayout, patchPagesGridLayout, patchZone, removeTableCustomAction,
    removeTableEvent, resetSearchFields, resetTableFields, resolveDefaultSearchComponentType, resolveDefaultTableRenderType, resolveFields, resolveTableDefaultParams, resolveTableFormOpenMode,
    resolveTableModalType, uniqueRefs, updateTableCustomAction, updateTableDefaultParams, updateTableEvent, updateTableFormOpenMode, updateTableHookRules, updateTableProp,
    updateTableRowGap, updateTreeConfig, updateZoneFieldSetting, updateZoneRefs, queryTypeOptions, searchComponentOptions, tableRenderOptions, treeLoadModeOptions,
    sortOrderOptions, alignOptions, sizeOptions, renderModeOptions, formOpenModeOptions, drawerPlacementOptions, labelPlacementOptions, fixedOptions,
    columnClickActionOptions, eventTriggerOptions, eventActionOptions, customActionPositionOptions, customActionTypeOptions, buttonTypeOptions, FieldOrderEditor, fieldMap,
    searchZone, tableZone, activeFieldZone, configExpanded, configCollapsed, fieldEditorAnchor, fieldOptions, pageTargetOptions,
    sortFieldOptions, searchFields, tableFields, treeConfig, tableEvents, tableCustomActions, configKeyword, defaultSortText,
    defaultApiValues, crudPreviewBlock, treePreviewBlock, activeFieldEditor,
  }
}
