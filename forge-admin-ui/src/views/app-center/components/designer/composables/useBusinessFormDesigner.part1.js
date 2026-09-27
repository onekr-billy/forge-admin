/** BusinessFormDesigner.vue setup part 1. */
import { ChevronDownOutline, ChevronUpOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { computed, nextTick, provide, ref, watch } from 'vue'
import { saveBusinessObjectDesigner, saveBusinessObjectFormLayout } from '@/api/business-app'
import { cloneSchema, isSameSchema } from '@/components/lowcode-builder/model/model-schema'
import {
  buildPageDesignModelSchema,
  createDefaultPageSchema,
  createPageModelRef,
  isReadonlySystemField,
  syncPageSchemaWithModel,
} from '@/components/lowcode-builder/page/page-schema'
import { isPageWidgetComponentKey } from '@/components/lowcode-builder/shared/page-widget-schema'
import { hasRuntimeVisibilityRules } from '@/components/lowcode-builder/shared/runtime-rules'
import BusinessDetailDesigner from '../BusinessDetailDesigner.vue'
import BusinessFormCreateDesigner from '../BusinessFormCreateDesigner.vue'
import ButtonActionConfig from '../ButtonActionConfig.vue'
import {
  removeChildTableSectionConfig,
  reorderChildTableSectionConfig,
  resolveChildTableSectionEditConfig,
  safeKey,
  upsertChildTableSectionConfig,
} from '../child-table-section-config'
import ChildTableSectionWizard from '../ChildTableSectionWizard.vue'
import ForgeFormDesigner from '../forge-form-designer/ForgeFormDesigner.vue'
import { buildAutoFieldAssets } from '../form-first/autoFieldRegistry'
import { FIELD_COMPONENT_DEFAULTS as COMPONENT_FIELD_DEFAULTS, FORM_FIELD_COMPONENT_KEYS } from '../form-first/fieldComponentCatalog'
import { extractForgeSchemaFieldRefs, forgeSchemaToFormCreate } from '../form-first/forgeToFormCreate'
import { applyGridColumnsToFormDesignerSchema, generateFieldCode, normalizeFormDesignerSchema, normalizeFormDesignerSchemaForSave } from '../form-first/formDesignerSchema'
import RelationOverviewModal from '../RelationOverviewModal.vue'
export function applyBusinessFormDesignerPart1(props, emit) {
  const __impl = {}
  const mut = {
    localSchemaDirtyMode: null,
    syncedSubTableContainerOrder: null,
  }
  const applyComponentFieldDefaults = (...args) => __impl.applyComponentFieldDefaults(...args)
  const booleanFlag = (...args) => __impl.booleanFlag(...args)
  const buildCurrentDesignerDraft = (...args) => __impl.buildCurrentDesignerDraft(...args)
  const buildDesignerDraftFromFormSchema = (...args) => __impl.buildDesignerDraftFromFormSchema(...args)
  const buildFormFieldComponentMap = (...args) => __impl.buildFormFieldComponentMap(...args)
  const buildGeneratedTemplateFieldBase = (...args) => __impl.buildGeneratedTemplateFieldBase(...args)
  const camelToSnake = (...args) => __impl.camelToSnake(...args)
  const canInlineRelation = (...args) => __impl.canInlineRelation(...args)
  const collectFormDesignerComponentGroups = (...args) => __impl.collectFormDesignerComponentGroups(...args)
  const collectFormFieldComponents = (...args) => __impl.collectFormFieldComponents(...args)
  const downgradeDesignerFieldToText = (...args) => __impl.downgradeDesignerFieldToText(...args)
  const findRelationGroup = (...args) => __impl.findRelationGroup(...args)
  const firstNonBlank = (...args) => __impl.firstNonBlank(...args)
  const handleFieldAssetUpdated = (...args) => __impl.handleFieldAssetUpdated(...args)
  const isDictField = (...args) => __impl.isDictField(...args)
  const isGeneratedTemplateFieldCode = (...args) => __impl.isGeneratedTemplateFieldCode(...args)
  const isGenericGeneratedFieldCode = (...args) => __impl.isGenericGeneratedFieldCode(...args)
  const isReferenceField = (...args) => __impl.isReferenceField(...args)
  const isUnconfiguredDictField = (...args) => __impl.isUnconfiguredDictField(...args)
  const isUnconfiguredReferenceField = (...args) => __impl.isUnconfiguredReferenceField(...args)
  const matchesSourceField = (...args) => __impl.matchesSourceField(...args)
  const mergeBusinessFieldAsset = (...args) => __impl.mergeBusinessFieldAsset(...args)
  const mergeFieldWithFormComponent = (...args) => __impl.mergeFieldWithFormComponent(...args)
  const mergePrimaryModelRef = (...args) => __impl.mergePrimaryModelRef(...args)
  const mergeUniqueRefs = (...args) => __impl.mergeUniqueRefs(...args)
  const normalizeBusinessFieldAsset = (...args) => __impl.normalizeBusinessFieldAsset(...args)
  const normalizeGeneratedTemplateFieldCodes = (...args) => __impl.normalizeGeneratedTemplateFieldCodes(...args)
  const normalizeRelationFormRows = (...args) => __impl.normalizeRelationFormRows(...args)
  const normalizeRelationKey = (...args) => __impl.normalizeRelationKey(...args)
  const normalizeUnconfiguredDesignerField = (...args) => __impl.normalizeUnconfiguredDesignerField(...args)
  const parseRelationConfig = (...args) => __impl.parseRelationConfig(...args)
  const relationKeyMatches = (...args) => __impl.relationKeyMatches(...args)
  const requiresDictConfig = (...args) => __impl.requiresDictConfig(...args)
  const requiresReferenceConfig = (...args) => __impl.requiresReferenceConfig(...args)
  const reserveGeneratedTemplateFieldCode = (...args) => __impl.reserveGeneratedTemplateFieldCode(...args)
  const resolveCanvasFieldRefs = (...args) => __impl.resolveCanvasFieldRefs(...args)
  const resolveDesignModelSchema = (...args) => __impl.resolveDesignModelSchema(...args)
  const resolveDesignerFieldSortOrder = (...args) => __impl.resolveDesignerFieldSortOrder(...args)
  const resolveRelationToggle = (...args) => __impl.resolveRelationToggle(...args)
  const resolveSchema = (...args) => __impl.resolveSchema(...args)
  const rewriteGeneratedTemplateField = (...args) => __impl.rewriteGeneratedTemplateField(...args)
  const saveLayout = (...args) => __impl.saveLayout(...args)
  const shouldRewriteGeneratedTemplateField = (...args) => __impl.shouldRewriteGeneratedTemplateField(...args)
  const syncActiveFormSchemaEntry = (...args) => __impl.syncActiveFormSchemaEntry(...args)
  const syncDesignerDraft = (...args) => __impl.syncDesignerDraft(...args)
  const syncDesignerFieldWithFormComponent = (...args) => __impl.syncDesignerFieldWithFormComponent(...args)
  const toBusinessFieldPayload = (...args) => __impl.toBusinessFieldPayload(...args)
  const toPageField = (...args) => __impl.toPageField(...args)
  const walkFormDesignerComponents = (...args) => __impl.walkFormDesignerComponents(...args)
  const DICT_FIELD_TYPES = new Set(['DICT', 'SELECT', 'RADIO', 'CHECKBOX', 'MULTI_SELECT'])
  const DICT_COMPONENT_TYPES = new Set([
    'dictSelect',
    'select',
    'radio',
    'radioButton',
    'checkbox',
    'transfer',
    'cascader',
    'treeSelect',
    'customSelect',
  ])

  const message = useMessage()
  const saving = ref(false)
  const shelfTab = ref('unused')
  const activeObjectKey = ref('primary')
  const formCreateDesignerRef = ref(null)
  const forgeFormDesignerRef = ref(null)
  const childTableWizardVisible = ref(false)
  const childTableWizardValue = ref(null)
  const relationOverviewVisible = ref(false)
  const buttonActionConfigVisible = ref(false)
  const buttonActionIndex = ref(-1)
  const buttonActionValue = ref({})
  const useLegacyFormCreateDesigner = ref(false)
  const activeFormDesignerRef = computed(() => useLegacyFormCreateDesigner.value ? formCreateDesignerRef.value : forgeFormDesignerRef.value)

  // ---- 子表配置桥接：provide/inject 让 FormSubTablePanel 能触发 pageSchema 三处同步 ----
  provide('subTableConfigBridge', {
    syncSubTableConfig(payload = {}) {
      handleSubTableBridgeSync(payload)
    },
  })

  const baseModelSchema = computed(() => {
    const modelFields = props.modelSchema?.fields || []
    return {
      ...(props.modelSchema || {}),
      fields: modelFields.length ? modelFields : props.fields.map(toPageField),
    }
  })

  // Placeholder only — hydrateInitialState() normalizes after part2 wires __impl.
  const localSchema = ref(cloneSchema(props.modelValue || createDefaultPageSchema(props.modelSchema || {})))
  const localFormDesignerSchema = ref(cloneSchema(props.formDesignerSchema || null))
  const localViewSchema = ref(cloneSchema(props.viewSchema || null))

  const effectiveModelSchema = computed(() => resolveDesignModelSchema(localSchema.value, baseModelSchema.value))
  const designFields = computed(() => effectiveModelSchema.value.fields || [])
  const editZone = computed(() => localSchema.value.zones?.find(zone => zone.zoneKey === 'edit') || null)
  const pageModelRefs = computed(() => effectiveModelSchema.value.pageModelRefs || [])
  const buttonTargetPages = computed(() => Array.isArray(localSchema.value?.pages) ? localSchema.value.pages : [])
  const primaryModelCode = computed(() => pageModelRefs.value.find(ref => ref?.primary)?.modelCode || '')
  const primaryDesignFields = computed(() => designFields.value.filter(field => !isRelationField(field)))
  const relationFields = computed(() => designFields.value.filter(field => isRelationField(field)))
  const primaryFieldSet = computed(() => new Set(primaryDesignFields.value.map(field => field.field)))
  const relationFieldSet = computed(() => new Set(relationFields.value.map(field => field.field)))
  const usedFieldSet = computed(() => new Set(extractForgeSchemaFieldRefs(localFormDesignerSchema.value || {}).filter(ref => primaryFieldSet.value.has(ref))))
  const selectedRelationFieldRefs = computed(() => (editZone.value?.fieldRefs || []).filter(ref => relationFieldSet.value.has(ref)))
  const selectedRelationFieldSet = computed(() => new Set(selectedRelationFieldRefs.value))
  const businessFields = computed(() => primaryDesignFields.value.filter(field => !isReadonlySystemField(field)))
  const primaryBusinessFields = computed(() => primaryDesignFields.value.filter(field => !isReadonlySystemField(field)))
  const primaryBusinessFieldAssets = computed(() => {
    const assets = (props.fields || []).map(normalizeBusinessFieldAsset).filter(field => field.fieldCode && !isReadonlySystemField(field))
    return assets.length ? assets : primaryBusinessFields.value.map(normalizeBusinessFieldAsset).filter(field => field.fieldCode)
  })
  const systemFields = computed(() => primaryDesignFields.value.filter(field => isReadonlySystemField(field)))
  const usedFields = computed(() => businessFields.value.filter(field => usedFieldSet.value.has(field.field)))
  const unusedFields = computed(() => businessFields.value.filter(field => !usedFieldSet.value.has(field.field)))
  const formOpenModeOptions = [
    { label: '弹出框', value: 'modal' },
    { label: '抽屉', value: 'drawer' },
    { label: '平铺', value: 'flat' },
    { label: '多页签', value: 'tabWorkspace' },
  ]
  const editFormOpenMode = computed({
    get: () => editZone.value?.props?.formOpenMode || editZone.value?.props?.modalType || 'modal',
    set: value => updateEditZoneProps(normalizeFormOpenModePatch(value)),
  })
  const formGridColumns = computed({
    get: () => clampNumber(localFormDesignerSchema.value?.layout?.gridColumns, 1, 4, 2),
    set: value => updateFormDesignerLayout({ gridColumns: clampNumber(value, 1, 4, 2) }),
  })
  const formDesignerMoreOptions = computed(() => [
    {
      type: 'divider',
      key: 'businessFormDivider',
    },
    {
      label: '补齐未使用字段',
      key: 'appendUnusedFields',
      disabled: !unusedFields.value.length,
    },
    {
      label: useLegacyFormCreateDesigner.value ? '使用新版画布' : '旧版画布',
      key: 'toggleDesignerVersion',
    },
  ])
  const relationFieldGroups = computed(() => {
    return pageModelRefs.value
      .filter(ref => ref && !ref.primary)
      .map((ref) => {
        const fields = sortRelationGroupFields(relationFields.value.filter(field => field.modelCode === ref.modelCode && isRelationFieldAllowed(field, ref)))
        const selectedCount = fields.filter(field => selectedRelationFieldSet.value.has(field.field)).length
        const props = ref.props || {}
        const aliases = buildRelationAliases(ref)
        return {
          key: aliases[0] || ref.modelCode || ref.modelName,
          modelCode: ref.modelCode || '',
          aliases,
          title: props.tabTitle || props.relationName || ref.modelName || ref.modelCode || '关联对象',
          fields,
          selectedCount,
          showInCreate: booleanFlag(props.inlineCreateEnabled),
          showInEdit: booleanFlag(props.inlineEditEnabled),
          showInDetail: props.showInDetail !== false && props.showInDetail !== 'false',
        }
      })
  })
  const relationFormRows = computed(() => normalizeRelationFormRows(props.relations || [], relationFieldGroups.value))
  const formObjectTabs = computed(() => [
    {
      key: 'primary',
      label: '主表单',
    },
    ...relationFormRows.value.map(row => ({
      key: row.key,
      label: `${row.title}表单`,
    })),
  ])
  const isPrimaryObjectActive = computed(() => activeObjectKey.value === 'primary')
  const activeRelationRow = computed(() => relationFormRows.value.find(row => row.key === activeObjectKey.value) || null)
  const activeRelationGroup = computed(() => activeRelationRow.value?.group || null)
  const activeRelationCanvasFields = computed(() => {
    const group = activeRelationGroup.value
    if (!group?.fields?.length)
      return []
    return group.fields.filter(field => selectedRelationFieldSet.value.has(field.field))
  })
  const activeRelationAvailableFields = computed(() => {
    const group = activeRelationGroup.value
    if (!group?.fields?.length)
      return []
    return group.fields.filter(field => !selectedRelationFieldSet.value.has(field.field))
  })
  const activeObjectTitle = computed(() => {
    if (isPrimaryObjectActive.value)
      return `${props.modelSchema?.businessName || props.modelSchema?.object?.name || props.objectName || '当前对象'}主表单`
    return activeRelationRow.value?.title || '关联对象表单'
  })
  const activeObjectDescription = computed(() => {
    if (isPrimaryObjectActive.value)
      return '维护当前业务对象的默认表单，可被新增、编辑、详情或入口按 formKey 引用。'
    return '维护关联对象在当前对象新增、编辑或详情中的内嵌表单字段，不会创建新的业务对象。'
  })
  const visibleShelfFields = computed(() => {
    if (shelfTab.value === 'used')
      return usedFields.value
    if (shelfTab.value === 'system')
      return systemFields.value
    return unusedFields.value
  })

  watch(
    () => props.modelValue,
    (value) => {
      const next = resolveSchema(value, resolveDesignModelSchema(value, baseModelSchema.value))
      assignLocalSchema(next, { markDirty: false })
    },
    { deep: true },
  )

  watch(
    () => props.formDesignerSchema,
    (value) => {
      const next = cloneSchema(value || null)
      if (!isSameSchema(next, localFormDesignerSchema.value))
        localFormDesignerSchema.value = next
    },
    { deep: true },
  )

  watch(
    () => props.viewSchema,
    (value) => {
      const next = cloneSchema(value || null)
      if (!isSameSchema(next, localViewSchema.value))
        localViewSchema.value = next
    },
    { deep: true },
  )

  watch(
    () => editZone.value?.props?.formOpenMode || editZone.value?.props?.modalType,
    (value) => {
      const nextFormOpenMode = normalizeFormOpenMode(value)
      if (!['modal', 'drawer', 'flat', 'tabWorkspace'].includes(nextFormOpenMode))
        return
      const rawLayout = props.formDesignerSchema?.layout || {}
      const rawHasOpenMode = Object.prototype.hasOwnProperty.call(rawLayout, 'formOpenMode')
        || Object.prototype.hasOwnProperty.call(rawLayout, 'modalType')
      if (rawHasOpenMode || localFormDesignerSchema.value?.layout?.formOpenMode === nextFormOpenMode)
        return
      localFormDesignerSchema.value = normalizeFormDesignerSchema({
        ...(localFormDesignerSchema.value || {}),
        layout: {
          ...(localFormDesignerSchema.value?.layout || {}),
          ...normalizeFormOpenModePatch(nextFormOpenMode),
        },
      })
    },
    { immediate: true },
  )

  watch(
    effectiveModelSchema,
    (value) => {
      const next = syncPageSchemaWithModel(localSchema.value, value)
      assignLocalSchema(next, { markDirty: false })
    },
    { deep: true },
  )

  watch(
    localFormDesignerSchema,
    (value) => {
      emit('update:formDesignerSchema', cloneSchema(value || null))
      const nextFormOpenMode = normalizeFormOpenMode(value?.layout?.formOpenMode || value?.layout?.modalType)
      if (editZone.value?.props?.formOpenMode !== nextFormOpenMode)
        updateEditZoneProps(normalizeFormOpenModePatch(nextFormOpenMode))
      syncFormDesignerSchemaToPageSchema(value)
    },
    { deep: true },
  )

  watch(
    localSchema,
    (value) => {
      const dirtyMode = mut.localSchemaDirtyMode
      mut.localSchemaDirtyMode = null
      if (!isSameSchema(value, props.modelValue)) {
        emit('update:modelValue', cloneSchema(value))
        if (dirtyMode !== 'silent')
          emit('dirtyChange', true)
      }
    },
    { deep: true },
  )

  watch(
    localViewSchema,
    (value) => {
      if (!isSameSchema(value, props.viewSchema))
        emit('update:viewSchema', cloneSchema(value || null))
    },
    { deep: true },
  )

  watch(formObjectTabs, (tabs) => {
    if (!tabs.some(tab => tab.key === activeObjectKey.value))
      activeObjectKey.value = 'primary'
  }, { deep: true })

  function syncFormDesignerSchemaToPageSchema(schema, fields = primaryDesignFields.value) {
    if (!editZone.value || !schema)
      return
    const zone = buildFormDesignerEditZone(editZone.value, schema, fields)
    if (zone)
      replaceZone(zone, { markDirty: false })
  }

  function buildFormDesignerEditZone(zone, schema, fields = primaryDesignFields.value) {
    if (!zone || !schema)
      return null
    const normalizedSchema = normalizeFormDesignerSchema(schema)
    const layout = normalizedSchema.layout || {}
    const gridColumns = clampNumber(layout.gridColumns, 1, 4, 2)
    const defaultLabelWidth = resolveNumber(layout.labelWidth, 100)
    const fieldSet = buildVisibleFormFieldSet(fields)
    const { rules, options } = forgeSchemaToFormCreate({
      schema: normalizedSchema,
      fields,
    })
    const compiledSettings = buildFormRuntimeFieldSettings(normalizedSchema, fieldSet, gridColumns, defaultLabelWidth)
    const formLayout = buildRuntimeFormLayout(normalizedSchema, fieldSet, gridColumns)
    const fieldRefs = Object.keys(compiledSettings)
    const modelFieldSet = buildPrimaryModelFieldSet(fields)
    const formOpenMode = normalizeFormOpenMode(layout.formOpenMode || layout.modalType)
    return {
      ...zone,
      fieldRefs: mergeUniqueRefs(fieldRefs, resolveSelectedRelationFieldRefs(zone)),
      props: {
        ...(zone.props || {}),
        formCreateRule: rules,
        formCreateOptions: options,
        fieldSettings: replaceModelFieldSettings(zone.props?.fieldSettings, modelFieldSet, compiledSettings),
        editGridCols: gridColumns,
        labelPlacement: layout.labelPlacement || 'left',
        labelWidth: defaultLabelWidth,
        labelAlign: layout.labelAlign || 'right',
        size: normalizeRuntimeFormSize(layout.size),
        formOpenMode,
        modalType: ['modal', 'drawer'].includes(formOpenMode) ? formOpenMode : 'modal',
        modalWidth: layout.modalWidth || zone.props?.modalWidth || '800px',
        detailModalWidth: layout.detailModalWidth || layout.modalWidth || zone.props?.detailModalWidth || zone.props?.modalWidth || '800px',
        drawerPlacement: layout.drawerPlacement || zone.props?.drawerPlacement || 'right',
        showFeedback: layout.showFeedback !== false,
        hideRequiredAsterisk: Boolean(layout.hideRequiredAsterisk),
        inlineFeedback: Boolean(layout.inlineFeedback),
        editFormStyle: layout.formStyle,
        editFormClass: layout.formClass,
        formAssets: Array.isArray(normalizedSchema.settings?.formAssets) ? normalizedSchema.settings.formAssets : [],
        rowGap: resolveNumber(layout.rowGap, 16),
        columnGap: resolveNumber(layout.columnGap, 16),
        formLayout,
        canvas: undefined,
        compiledFrom: 'formDesignerSchema',
      },
    }
  }

  function resolveSelectedRelationFieldRefs(zone = {}) {
    return (zone?.fieldRefs || []).filter(ref => relationFieldSet.value.has(ref))
  }

  function buildVisibleFormFieldSet(fields = []) {
    return new Set((fields || [])
      .filter(field => !['DISABLED', 'HIDDEN'].includes(String(field?.fieldStatus || 'ENABLED').toUpperCase()))
      .map(field => field.field || field.fieldCode)
      .filter(Boolean))
  }

  function buildFormRuntimeFieldSettings(schema, fieldSet, gridColumns, defaultLabelWidth) {
    const settings = {}
    collectRuntimeFieldComponents(schema.components, gridColumns).forEach(({ component, inheritedSpan }) => {
      const componentKey = component?.componentKey || 'input'
      if (!FORM_FIELD_COMPONENT_KEYS.has(componentKey) || component?.fieldBinding?.mode === 'virtual')
        return
      const fieldCode = component?.fieldBinding?.fieldCode || ''
      if (!fieldCode || !fieldSet.has(fieldCode) || (component?.visibility?.hidden && !hasRuntimeVisibilityRules(component)))
        return
      settings[fieldCode] = buildRuntimeFormFieldSetting(component, gridColumns, defaultLabelWidth, inheritedSpan)
    })
    return settings
  }

  function buildRuntimeFormFieldSetting(component, gridColumns, defaultLabelWidth, inheritedSpan = null) {
    const layout = component.layout || {}
    const rawProps = { ...(component.props || {}) }
    const formCreateMeta = rawProps.__fc && typeof rawProps.__fc === 'object' ? rawProps.__fc : {}
    const props = sanitizeRuntimeFieldProps(rawProps)
    const setting = {
      componentType: normalizeRuntimeComponentType(component.componentKey),
      align: normalizeAlign(layout.align),
      span: clampNumber(inheritedSpan || layout.span, 1, gridColumns, 1),
      labelWidth: resolveNumber(layout.labelWidth, defaultLabelWidth),
    }
    if (component.label)
      setting.label = component.label
    if (Object.keys(props).length)
      setting.props = props
    applyRuntimeFieldMeta(setting, component, props, formCreateMeta)
    if (component.validation && Object.prototype.hasOwnProperty.call(component.validation, 'required'))
      setting.required = Boolean(component.validation.required)
    if (component.validation?.requiredMessage)
      setting.requiredMessage = component.validation.requiredMessage
    if (Array.isArray(component.validation?.rules) && component.validation.rules.length) {
      setting.rules = component.validation.rules.map(rule => ({ ...rule }))
      const requiredRule = setting.rules.find(rule => rule?.required)
      if (requiredRule?.trigger)
        setting.trigger = requiredRule.trigger
      if (!setting.requiredMessage && requiredRule?.message)
        setting.requiredMessage = requiredRule.message
    }
    if (component.visibility && Object.prototype.hasOwnProperty.call(component.visibility, 'hidden')) {
      setting.hidden = Boolean(component.visibility.hidden)
      setting.formVisible = !component.visibility.hidden
    }
    if (component.visibility && Object.prototype.hasOwnProperty.call(component.visibility, 'readonly'))
      setting.readonly = Boolean(component.visibility.readonly)
    if (props.dictType)
      setting.dictType = props.dictType
    if (Object.prototype.hasOwnProperty.call(props, 'defaultValue'))
      setting.defaultValue = props.defaultValue
    return setting
  }

  function collectRuntimeFieldComponents(components = [], gridColumns = 2, inheritedSpan = null) {
    const result = []
    const walk = (items = [], parentSpan = inheritedSpan) => {
      ;(Array.isArray(items) ? items : []).forEach((component) => {
        if (!component || typeof component !== 'object')
          return
        const componentKey = component.componentKey || ''
        if (FORM_FIELD_COMPONENT_KEYS.has(componentKey) && component?.fieldBinding?.mode !== 'virtual') {
          result.push({ component, inheritedSpan: parentSpan })
          return
        }
        const nextSpan = isColumnLayoutComponent(componentKey)
          ? clampNumber(component.layout?.span || component.props?.span, 1, gridColumns, parentSpan || 1)
          : parentSpan
        if (Array.isArray(component.children))
          walk(component.children, nextSpan)
      })
    }
    walk(components, inheritedSpan)
    return result
  }

  function buildRuntimeFormLayout(schema, fieldSet, gridColumns) {
    return buildRuntimeFormLayoutNodes(schema.components || [], fieldSet, gridColumns)
  }

  function buildRuntimeFormLayoutNodes(components = [], fieldSet, gridColumns) {
    const nodes = []
    ;(Array.isArray(components) ? components : []).forEach((component, index) => {
      const node = buildRuntimeFormLayoutNode(component, index, fieldSet, gridColumns)
      if (Array.isArray(node))
        nodes.push(...node)
      else if (node)
        nodes.push(node)
    })
    return nodes
  }

  function buildRuntimeFormLayoutNode(component = {}, index = 0, fieldSet, gridColumns) {
    if (!component || typeof component !== 'object')
      return null
    const componentKey = component.componentKey || ''
    const key = component.id || `${componentKey || 'node'}_${index}`
    if (FORM_FIELD_COMPONENT_KEYS.has(componentKey) && component?.fieldBinding?.mode !== 'virtual') {
      const fieldCode = component.fieldBinding?.fieldCode || ''
      if (!fieldCode || !fieldSet.has(fieldCode) || (component.visibility?.hidden && !hasRuntimeVisibilityRules(component)))
        return null
      return {
        nodeType: 'field',
        key,
        field: fieldCode,
        span: clampNumber(component.layout?.span, 1, gridColumns, 1),
        ...buildRuntimeLayoutMeta(component),
      }
    }

    const children = buildRuntimeFormLayoutNodes(component.children || [], fieldSet, gridColumns)
    const props = sanitizeRuntimeLayoutProps(component.props || {})
    const label = resolveRuntimeLayoutLabel(component)
    const span = clampNumber(component.layout?.span || component.props?.span, 1, gridColumns, gridColumns)
    const meta = buildRuntimeLayoutMeta(component)

    if (isRowLayoutComponent(componentKey)) {
      return { nodeType: 'row', componentKey, key, props, children, span: gridColumns, ...meta }
    }
    if (isColumnLayoutComponent(componentKey)) {
      return { nodeType: 'col', componentKey, key, props, children, span, ...meta }
    }
    if (['elCard', 'card'].includes(componentKey)) {
      return { nodeType: 'card', componentKey, key, label, props, children, span: gridColumns, ...meta }
    }
    if (['elTabs', 'tabs'].includes(componentKey)) {
      return { nodeType: 'tabs', componentKey, key, props, children, span: gridColumns, ...meta }
    }
    if (['elTabPane', 'tabPane'].includes(componentKey)) {
      return { nodeType: 'tabPane', componentKey, key, label, props, children, span: gridColumns, ...meta }
    }
    if (['elCollapse', 'collapse'].includes(componentKey)) {
      return { nodeType: 'collapse', componentKey, key, props, children, span: gridColumns, ...meta }
    }
    if (['elCollapseItem', 'collapseItem'].includes(componentKey)) {
      return { nodeType: 'collapseItem', componentKey, key, label, props, children, span: gridColumns, ...meta }
    }
    if (componentKey === 'button') {
      return { nodeType: 'button', componentKey, key, label, props, span, align: normalizeAlign(component.layout?.align), ...meta }
    }
    if (['table', 'tableGrid'].includes(componentKey)) {
      return {
        nodeType: componentKey,
        componentKey,
        key,
        label,
        props,
        children,
        span: componentKey === 'table' ? gridColumns : span,
        ...meta,
      }
    }
    if (['AiCrudPage', 'aiCrudPage', 'crud', 'crudBlock'].includes(componentKey)) {
      return { nodeType: 'AiCrudPage', componentKey, key, label, props, children, span: gridColumns, ...meta }
    }
    if (isPageWidgetComponentKey(componentKey)) {
      return { nodeType: 'widget', componentKey, key, label, props, children, span, ...meta }
    }
    if (['elDivider', 'divider', 'AiFormSectionTitle'].includes(componentKey)) {
      return { nodeType: 'divider', componentKey, key, label, props, span: gridColumns, ...meta }
    }
    if (['fcTitle', 'title', 'groupTitle'].includes(componentKey)) {
      return { nodeType: 'groupTitle', componentKey, key, label, props, span: gridColumns, ...meta }
    }
    return children.length ? children : null
  }

  function sanitizeRuntimeFieldProps(source = {}) {
    const props = { ...(source || {}) }
    delete props.__fc
    delete props.__fcType
    delete props.fieldBinding
    return props
  }

  function applyRuntimeFieldMeta(setting, component = {}, props = {}, formCreateMeta = {}) {
    copyPropsToRuntimeField(setting, props)
    copyDefined(setting, 'componentStyle', formCreateMeta.style ?? props.style)
    copyDefined(setting, 'componentClass', props.className ?? props.class)
    copyDefined(setting, 'formItemClass', formCreateMeta.className ?? formCreateMeta.class)
    copyDefined(setting, 'formItemStyle', component.layout?.formItemStyle ?? formCreateMeta.wrap?.style)
    if (formCreateMeta.wrap?.labelWidth !== undefined)
      setting.labelWidth = resolveNumber(formCreateMeta.wrap.labelWidth, setting.labelWidth)
    if (formCreateMeta.wrap?.show === false)
      setting.showLabel = false
  }

  function copyPropsToRuntimeField(setting, props = {}) {
    ;[
      'placeholder',
      'clearable',
      'filterable',
      'multiple',
      'size',
      'maxlength',
      'showCount',
      'rows',
      'autosize',
      'min',
      'max',
      'step',
      'precision',
      'showButton',
      'checkedValue',
      'uncheckedValue',
      'checkedText',
      'uncheckedText',
      'format',
      'valueFormat',
      'startPlaceholder',
      'endPlaceholder',
      'showFeedback',
      'showLabel',
    ].forEach(key => copyDefined(setting, key, props[key]))
  }

  function copyDefined(target, key, value) {
    if (value !== undefined && value !== null)
      target[key] = value
  }

  function buildRuntimeLayoutMeta(component = {}) {
    const props = component.props || {}
    const formCreateMeta = props.__fc && typeof props.__fc === 'object' ? props.__fc : {}
    const meta = {}
    copyDefined(meta, 'style', formCreateMeta.style ?? props.style)
    copyDefined(meta, 'className', formCreateMeta.className ?? formCreateMeta.class ?? props.className ?? props.class)
    return meta
  }

  function sanitizeRuntimeLayoutProps(source = {}) {
    const props = { ...(source || {}) }
    delete props.__fc
    delete props.__fcType
    delete props.fieldBinding
    return props
  }

  function resolveRuntimeLayoutLabel(component = {}) {
    const props = component.props || {}
    return props.header || props.label || props.title || props.formCreateChild || component.label || ''
  }

  function isRowLayoutComponent(componentKey = '') {
    return ['fcRow', 'row'].includes(componentKey)
  }

  function isColumnLayoutComponent(componentKey = '') {
    return componentKey === 'col'
  }

  function replaceModelFieldSettings(existingSettings, modelFields, compiledSettings) {
    const next = isPlainObject(existingSettings) ? { ...existingSettings } : {}
    ;(modelFields || new Set()).forEach(field => delete next[field])
    return {
      ...next,
      ...compiledSettings,
    }
  }

  function buildPrimaryModelFieldSet(fields = []) {
    return new Set((fields || [])
      .filter(field => !isRelationField(field))
      .map(field => field.field || field.fieldCode)
      .filter(Boolean))
  }

  function normalizeRuntimeComponentType(componentKey) {
    if (componentKey === 'integer' || componentKey === 'money' || componentKey === 'inputNumber')
      return 'number'
    if (componentKey === 'upload')
      return 'fileUpload'
    if (['orgSelect', 'departmentSelect', 'departmentTreeSelect', 'deptSelect', 'deptTreeSelect', 'elTreeSelect', 'orgName', 'deptName'].includes(componentKey))
      return 'orgTreeSelect'
    if (['userPicker', 'userName'].includes(componentKey))
      return 'userSelect'
    return componentKey || 'input'
  }

  function normalizeAlign(value) {
    return ['left', 'center', 'right'].includes(value) ? value : 'left'
  }

  function clampNumber(value, min, max, fallback = min) {
    const number = Number(value)
    if (!Number.isFinite(number))
      return fallback
    return Math.max(min, Math.min(max, number))
  }

  function resolveNumber(value, fallback) {
    if (typeof value === 'string' && value.trim()) {
      const parsed = Number.parseInt(value, 10)
      return Number.isFinite(parsed) ? parsed : fallback
    }
    const number = Number(value)
    return Number.isFinite(number) ? number : fallback
  }

  function normalizeRuntimeFormSize(value) {
    if (value === 'default' || value === 'medium')
      return 'medium'
    return ['small', 'large'].includes(value) ? value : 'medium'
  }

  function normalizeFormOpenMode(value) {
    if (value === 'tabWorkspace')
      return 'tabWorkspace'
    return ['modal', 'drawer', 'flat'].includes(value) ? value : 'modal'
  }

  function normalizeFormOpenModePatch(value) {
    const formOpenMode = normalizeFormOpenMode(value)
    return {
      formOpenMode,
      modalType: ['modal', 'drawer'].includes(formOpenMode) ? formOpenMode : 'modal',
    }
  }

  function isPlainObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value)
  }

  function appendField(field) {
    if (isReadonlySystemField(field) || usedFieldSet.value.has(field.field))
      return
    activeFormDesignerRef.value?.appendField?.(field)
  }

  function appendAllUnusedFields() {
    unusedFields.value.forEach(appendField)
  }

  function handleFormDesignerMoreSelect(key = '') {
    if (key === 'appendUnusedFields') {
      appendAllUnusedFields()
      return
    }
    if (key === 'toggleDesignerVersion')
      useLegacyFormCreateDesigner.value = !useLegacyFormCreateDesigner.value
  }

  function openChildTableSectionWizard() {
    childTableWizardValue.value = null
    childTableWizardVisible.value = true
  }

  function handleEditChildTableSection(payload = {}) {
    const section = payload.section || payload
    childTableWizardValue.value = resolveChildTableSectionEditConfig({
      pageSchema: localSchema.value,
      formDesignerSchema: localFormDesignerSchema.value,
    }, section)
    childTableWizardVisible.value = true
  }

  function handleChildTableSectionConfirm(config) {
    const result = upsertChildTableSectionConfig({
      pageSchema: localSchema.value,
      formDesignerSchema: localFormDesignerSchema.value,
    }, config)
    assignLocalSchema(result.pageSchema)
    // 子表分区由画布容器承载：向导确认后在画布同步 upsert 关联子表容器（按 relationKey 幂等）。
    localFormDesignerSchema.value = normalizeFormDesignerSchema(upsertSubTableContainer(result.formDesignerSchema, config))
    emit('dirtyChange', true)
    nextTick(() => forgeFormDesignerRef.value?.openPageSections?.())
  }

  // ---- 子表桥接同步：FormSubTablePanel 通过 provide/inject 调用此函数完成三处写入 ----
  function handleSubTableBridgeSync(payload = {}) {
    const { action, config, relationKey } = payload

    if (action === 'remove') {
      // 清理 pageSchema 中的 modelRef + masterDetailConfig + pageSection
      if (relationKey) {
        const result = removeChildTableSectionConfig({
          pageSchema: localSchema.value,
          formDesignerSchema: localFormDesignerSchema.value,
        }, { relationKey })
        if (result.pageSchema) {
          assignLocalSchema(result.pageSchema)
          localFormDesignerSchema.value = normalizeFormDesignerSchema(result.formDesignerSchema)
          emit('dirtyChange', true)
        }
      }
      return
    }

    if (!config)
      return

    // add / update: 走 upsertChildTableSectionConfig 同步三处
    const result = upsertChildTableSectionConfig({
      pageSchema: localSchema.value,
      formDesignerSchema: localFormDesignerSchema.value,
    }, config)
    assignLocalSchema(result.pageSchema)
    localFormDesignerSchema.value = normalizeFormDesignerSchema(
      upsertSubTableContainer(result.formDesignerSchema, config),
    )
    emit('dirtyChange', true)
    nextTick(() => forgeFormDesignerRef.value?.openPageSections?.())
  }

  // ---- 概览栏子表摘要行 ----
  const subTableSummaryRows = computed(() => {
    const components = localFormDesignerSchema.value?.components || []
    const rows = []
    const walk = (list) => {
      ;(Array.isArray(list) ? list : []).forEach((component) => {
        if (!component || typeof component !== 'object')
          return
        if (component.componentKey === 'subTable') {
          const p = component.props || {}
          rows.push({
            key: p.relationKey || component.id,
            title: p.header || component.label || '关联子表',
            fieldCount: Array.isArray(p.columns) ? p.columns.length : 0,
            componentId: component.id,
          })
        }
        if (Array.isArray(component.children))
          walk(component.children)
      })
    }
    walk(components)
    return rows
  })

  function locateSubTableOnCanvas(componentId) {
    // 切换到主表单 → 画布视图 → 选中子表组件
    activeObjectKey.value = 'primary'
    nextTick(() => {
      forgeFormDesignerRef.value?.flushDesigner?.()
      // store 会在 ForgeFormDesigner 内部 sync，这里直接通过 ref 触发选中
      const store = forgeFormDesignerRef.value
      if (store) {
        // ForgeFormDesigner 暴露了 selectComponent 方法或通过 store 直接操作
        store.selectComponent?.(componentId)
      }
    })
  }

  // 画布上的关联子表容器被删除（或关系被改）时，同步清理子表分区的运行时配置。
  const subTableContainerRelationKeys = computed(() => collectSubTableRelationKeys(localFormDesignerSchema.value?.components))
  watch(subTableContainerRelationKeys, (nextKeys, prevKeys) => {
    if (useLegacyFormCreateDesigner.value)
      return
    prevKeys.forEach((relationKey) => {
      if (nextKeys.has(relationKey))
        return
      const result = removeChildTableSectionConfig({
        pageSchema: localSchema.value,
        formDesignerSchema: localFormDesignerSchema.value,
      }, { relationKey })
      if (!result.pageSchema)
        return
      assignLocalSchema(result.pageSchema)
      localFormDesignerSchema.value = normalizeFormDesignerSchema(result.formDesignerSchema)
      emit('dirtyChange', true)
    })
  })

  // 发布态子表顺序取自 pageSchema，画布拖动子表后必须跟随，否则运行页/审批与画布顺序相反。
  const subTableContainerOrder = computed(() => Array.from(subTableContainerRelationKeys.value).join('\u0001'))

  watch(
    [subTableContainerOrder, () => localSchema.value?.modelRefs, () => localSchema.value?.options?.masterDetailConfig?.children],
    ([order]) => {
      if (useLegacyFormCreateDesigner.value)
        return
      // 只有画布拖动子表才算用户修改；打开设计器或加载 schema 时静默纠正存量顺序
      const canvasReordered = mut.syncedSubTableContainerOrder !== null && mut.syncedSubTableContainerOrder !== order
      mut.syncedSubTableContainerOrder = order
      assignLocalSchema(
        reorderChildTableSectionConfig(localSchema.value, subTableContainerRelationKeys.value),
        { markDirty: canvasReordered },
      )
    },
    { immediate: true },
  )

  function upsertSubTableContainer(formDesignerSchema = {}, config = {}) {
    const components = Array.isArray(formDesignerSchema.components) ? [...formDesignerSchema.components] : []
    const relationKey = String(config.relationKey || '').trim()
    if (!relationKey)
      return formDesignerSchema
    const containerId = `subtable_${safeKey(relationKey)}`
    const index = components.findIndex(component => component?.componentKey === 'subTable'
      && (component.id === containerId || String(component.props?.relationKey || '') === relationKey))
    const currentProps = index >= 0 ? components[index].props || {} : {}
    const selectorMultiple = Object.prototype.hasOwnProperty.call(config, 'selectorMultiple')
      ? config.selectorMultiple !== false
      : currentProps.selectorMultiple !== false
    const selectorDisplayFields = Array.isArray(config.selectorDisplayFields)
      ? config.selectorDisplayFields
      : (Array.isArray(currentProps.selectorDisplayFields) ? currentProps.selectorDisplayFields : [])
    const selectorFilterFields = Array.isArray(config.selectorFilterFields)
      ? config.selectorFilterFields
      : (Array.isArray(currentProps.selectorFilterFields) ? currentProps.selectorFilterFields : [])
    const nextProps = {
      header: config.title || '关联子表',
      relationKey,
      displayMode: ['inline_grid', 'card_list', 'bottom_sheet'].includes(config.displayMode) ? config.displayMode : 'inline_grid',
      modelCode: config.modelCode || '',
      columns: Array.isArray(config.fields)
        ? config.fields.map(field => ({
            fieldCode: field.fieldCode || field.sourceField || field.field || '',
            fieldLabel: field.fieldName || field.label || field.fieldCode || field.sourceField || field.field || '',
          })).filter(field => field.fieldCode)
        : [],
      allowCreate: config.allowCreate !== false,
      allowSelectExisting: config.allowSelectExisting === true,
      selectorMultiple,
      selectorDisplayFields,
      selectorFilterFields,
    }
    if (index >= 0) {
      const current = components[index]
      components[index] = { ...current, label: nextProps.header, props: { ...(current.props || {}), ...nextProps } }
    }
    else {
      components.push({
        id: containerId,
        componentKey: 'subTable',
        label: nextProps.header,
        props: nextProps,
      })
    }
    return { ...formDesignerSchema, components }
  }

  function collectSubTableRelationKeys(components = [], keys = new Set()) {
    ;(Array.isArray(components) ? components : []).forEach((component) => {
      if (!component || typeof component !== 'object')
        return
      if (component.componentKey === 'subTable') {
        const relationKey = String(component.props?.relationKey || '').trim()
        if (relationKey)
          keys.add(relationKey)
      }
      if (Array.isArray(component.children))
        collectSubTableRelationKeys(component.children, keys)
    })
    return keys
  }

  // 画布容器上的「配置」入口：打开子表分区向导编辑对应关系。
  function handleEditSubTableContainer(relationKey = '') {
    handleEditChildTableSection({ relationKey })
  }

  function handleConfigureBottomAction(payload = {}) {
    const index = Number(payload.index)
    if (!Number.isInteger(index) || index < 0)
      return
    const { __editorKey: _editorKey, ...action } = payload.action || {}
    buttonActionIndex.value = index
    buttonActionValue.value = cloneSchema(action)
    buttonActionConfigVisible.value = true
  }

  function handleButtonActionConfirm(action) {
    const index = buttonActionIndex.value
    const currentSchema = normalizeFormDesignerSchema(localFormDesignerSchema.value || {})
    const actions = Array.isArray(currentSchema.bottomBar?.actions) ? currentSchema.bottomBar.actions : []
    if (index < 0 || index >= actions.length)
      return
    localFormDesignerSchema.value = normalizeFormDesignerSchema({
      ...currentSchema,
      bottomBar: {
        ...(currentSchema.bottomBar || {}),
        actions: actions.map((item, actionIndex) => actionIndex === index ? { ...item, ...action } : item),
      },
    })
    emit('dirtyChange', true)
    nextTick(() => forgeFormDesignerRef.value?.openPageSections?.())
  }

  function updateEditZoneProps(patch = {}) {
    if (!editZone.value)
      return
    replaceZone({
      ...editZone.value,
      props: {
        ...(editZone.value.props || {}),
        ...patch,
      },
    })
  }

  function updateFormDesignerLayout(patch = {}) {
    const flushedSchema = activeFormDesignerRef.value?.flushDesigner?.()
    const currentSchema = normalizeFormDesignerSchema(flushedSchema || localFormDesignerSchema.value || {})
    const nextGridColumns = patch.gridColumns ? clampNumber(patch.gridColumns, 1, 4, 2) : currentSchema.layout?.gridColumns || 2
    localFormDesignerSchema.value = {
      ...applyGridColumnsToFormDesignerSchema(currentSchema, nextGridColumns),
      layout: {
        ...(currentSchema.layout || {}),
        ...patch,
        gridColumns: nextGridColumns,
      },
    }
    emit('dirtyChange', true)
  }

  function toggleRelationField(field, checked) {
    if (!field?.field || !editZone.value)
      return
    const selected = new Set(selectedRelationFieldRefs.value)
    if (checked)
      selected.add(field.field)
    else
      selected.delete(field.field)
    replaceZone(normalizeEditZoneFieldRefs(editZone.value, Array.from(selected), true))
  }

  function selectRelationGroupFields(group) {
    if (!editZone.value)
      return
    const selected = new Set(selectedRelationFieldRefs.value)
    group.fields.forEach(field => selected.add(field.field))
    replaceZone(normalizeEditZoneFieldRefs(editZone.value, Array.from(selected), true))
  }

  function clearRelationGroupFields(group) {
    if (!editZone.value)
      return
    const removeRefs = new Set(group.fields.map(field => field.field))
    const selected = selectedRelationFieldRefs.value.filter(ref => !removeRefs.has(ref))
    replaceZone(normalizeEditZoneFieldRefs(editZone.value, selected, true))
  }

  function canMoveRelationField(field, direction) {
    const groupRefs = selectedRelationRefsByModel(field?.modelCode)
    const index = groupRefs.indexOf(field?.field)
    const nextIndex = index + direction
    return index >= 0 && nextIndex >= 0 && nextIndex < groupRefs.length
  }

  function moveRelationField(field, direction) {
    if (!field?.field || !editZone.value || !canMoveRelationField(field, direction))
      return
    const groupRefSet = new Set(relationFields.value
      .filter(item => item.modelCode === field.modelCode)
      .map(item => item.field))
    const groupRefs = selectedRelationRefsByModel(field.modelCode)
    const from = groupRefs.indexOf(field.field)
    const to = from + direction
    const [item] = groupRefs.splice(from, 1)
    groupRefs.splice(to, 0, item)
    let groupIndex = 0
    const nextRefs = selectedRelationFieldRefs.value.map((ref) => {
      if (!groupRefSet.has(ref))
        return ref
      return groupRefs[groupIndex++] || ref
    })
    replaceZone(normalizeEditZoneFieldRefs(editZone.value, nextRefs, true))
  }

  function selectedRelationRefsByModel(modelCode) {
    const groupRefSet = new Set(relationFields.value
      .filter(field => field.modelCode === modelCode)
      .map(field => field.field))
    return selectedRelationFieldRefs.value.filter(ref => groupRefSet.has(ref))
  }

  function assignLocalSchema(schema, options = {}) {
    if (isSameSchema(schema, localSchema.value))
      return false
    mut.localSchemaDirtyMode = options.markDirty === false ? 'silent' : 'dirty'
    localSchema.value = schema
    return true
  }

  function replaceZone(zone, options = {}) {
    if (!zone)
      return
    const nextSchema = {
      ...localSchema.value,
      zones: (localSchema.value.zones || []).map(item => item.zoneKey === zone.zoneKey ? zone : item),
    }
    assignLocalSchema(nextSchema, { markDirty: options.markDirty !== false })
  }

  function normalizeEditZoneFieldRefs(zone, relationRefs = Array.from(selectedRelationFieldSet.value), relationSelectionTouched = false) {
    return {
      ...zone,
      fieldRefs: [
        ...resolvePrimaryFieldRefs(zone),
        ...orderRelationRefs(relationRefs),
      ],
      props: {
        ...(zone.props || {}),
        ...(relationSelectionTouched ? { relationFieldSelectionMode: 'CUSTOM' } : {}),
      },
    }
  }

  function resolvePrimaryFieldRefs(zone) {
    const canvasRefs = resolveCanvasFieldRefs(zone?.props?.canvas?.items || []).filter(ref => primaryFieldSet.value.has(ref))
    if (canvasRefs.length)
      return canvasRefs
    const explicitRefs = (zone?.fieldRefs || []).filter(ref => primaryFieldSet.value.has(ref))
    if (explicitRefs.length)
      return explicitRefs
    return (editZone.value?.fieldRefs || []).filter(ref => primaryFieldSet.value.has(ref))
  }

  function orderRelationRefs(refs = []) {
    const seen = new Set()
    return refs.filter((ref) => {
      if (!relationFieldSet.value.has(ref) || seen.has(ref))
        return false
      seen.add(ref)
      return true
    })
  }

  function sortRelationGroupFields(fields = []) {
    const order = new Map(selectedRelationFieldRefs.value.map((ref, index) => [ref, index]))
    return fields.map((field, index) => ({ field, index }))
      .sort((left, right) => {
        const leftOrder = order.has(left.field.field) ? order.get(left.field.field) : Number.MAX_SAFE_INTEGER
        const rightOrder = order.has(right.field.field) ? order.get(right.field.field) : Number.MAX_SAFE_INTEGER
        if (leftOrder !== rightOrder)
          return leftOrder - rightOrder
        return left.index - right.index
      })
      .map(item => item.field)
  }

  function buildRelationAliases(ref = {}) {
    const props = ref.props || {}
    return [
      props.targetObjectCode,
      props.businessObjectCode,
      props.objectCode,
      ref.objectCode,
      ref.businessObjectCode,
      ref.modelCode,
      ref.modelName,
    ]
      .map(normalizeRelationKey)
      .filter(Boolean)
      .filter((value, index, array) => array.indexOf(value) === index)
  }

  function isRelationField(field = {}) {
    const sourceField = field.sourceField || field.field
    return Boolean(field.modelCode)
      && field.field !== sourceField
      && (!primaryModelCode.value || field.modelCode !== primaryModelCode.value)
  }

  function isRelationFieldAllowed(field = {}, ref = {}) {
    if (isReadonlySystemField(field) || field.formVisible === false)
      return false
    const relation = Array.isArray(ref.relations) ? ref.relations[0] : null
    return !matchesSourceField(field, relation?.sourceField)
  }

  __impl.syncFormDesignerSchemaToPageSchema = syncFormDesignerSchemaToPageSchema
  __impl.buildFormDesignerEditZone = buildFormDesignerEditZone
  __impl.resolveSelectedRelationFieldRefs = resolveSelectedRelationFieldRefs
  __impl.buildVisibleFormFieldSet = buildVisibleFormFieldSet
  __impl.buildFormRuntimeFieldSettings = buildFormRuntimeFieldSettings
  __impl.buildRuntimeFormFieldSetting = buildRuntimeFormFieldSetting
  __impl.collectRuntimeFieldComponents = collectRuntimeFieldComponents
  __impl.buildRuntimeFormLayout = buildRuntimeFormLayout
  __impl.buildRuntimeFormLayoutNodes = buildRuntimeFormLayoutNodes
  __impl.buildRuntimeFormLayoutNode = buildRuntimeFormLayoutNode
  __impl.sanitizeRuntimeFieldProps = sanitizeRuntimeFieldProps
  __impl.applyRuntimeFieldMeta = applyRuntimeFieldMeta
  __impl.copyPropsToRuntimeField = copyPropsToRuntimeField
  __impl.copyDefined = copyDefined
  __impl.buildRuntimeLayoutMeta = buildRuntimeLayoutMeta
  __impl.sanitizeRuntimeLayoutProps = sanitizeRuntimeLayoutProps
  __impl.resolveRuntimeLayoutLabel = resolveRuntimeLayoutLabel
  __impl.isRowLayoutComponent = isRowLayoutComponent
  __impl.isColumnLayoutComponent = isColumnLayoutComponent
  __impl.replaceModelFieldSettings = replaceModelFieldSettings
  __impl.buildPrimaryModelFieldSet = buildPrimaryModelFieldSet
  __impl.normalizeRuntimeComponentType = normalizeRuntimeComponentType
  __impl.normalizeAlign = normalizeAlign
  __impl.clampNumber = clampNumber
  __impl.resolveNumber = resolveNumber
  __impl.normalizeRuntimeFormSize = normalizeRuntimeFormSize
  __impl.normalizeFormOpenMode = normalizeFormOpenMode
  __impl.normalizeFormOpenModePatch = normalizeFormOpenModePatch
  __impl.isPlainObject = isPlainObject
  __impl.appendField = appendField
  __impl.appendAllUnusedFields = appendAllUnusedFields
  __impl.handleFormDesignerMoreSelect = handleFormDesignerMoreSelect
  __impl.openChildTableSectionWizard = openChildTableSectionWizard
  __impl.handleEditChildTableSection = handleEditChildTableSection
  __impl.handleChildTableSectionConfirm = handleChildTableSectionConfirm
  __impl.handleSubTableBridgeSync = handleSubTableBridgeSync
  __impl.locateSubTableOnCanvas = locateSubTableOnCanvas
  __impl.upsertSubTableContainer = upsertSubTableContainer
  __impl.collectSubTableRelationKeys = collectSubTableRelationKeys
  __impl.handleEditSubTableContainer = handleEditSubTableContainer
  __impl.handleConfigureBottomAction = handleConfigureBottomAction
  __impl.handleButtonActionConfirm = handleButtonActionConfirm
  __impl.updateEditZoneProps = updateEditZoneProps
  __impl.updateFormDesignerLayout = updateFormDesignerLayout
  __impl.toggleRelationField = toggleRelationField
  __impl.selectRelationGroupFields = selectRelationGroupFields
  __impl.clearRelationGroupFields = clearRelationGroupFields
  __impl.canMoveRelationField = canMoveRelationField
  __impl.moveRelationField = moveRelationField
  __impl.selectedRelationRefsByModel = selectedRelationRefsByModel
  __impl.assignLocalSchema = assignLocalSchema
  __impl.replaceZone = replaceZone
  __impl.normalizeEditZoneFieldRefs = normalizeEditZoneFieldRefs
  __impl.resolvePrimaryFieldRefs = resolvePrimaryFieldRefs
  __impl.orderRelationRefs = orderRelationRefs
  __impl.sortRelationGroupFields = sortRelationGroupFields
  __impl.buildRelationAliases = buildRelationAliases
  __impl.isRelationField = isRelationField
  __impl.isRelationFieldAllowed = isRelationFieldAllowed

  return {
    props, emit, __impl, mut, appendAllUnusedFields, appendField, applyComponentFieldDefaults, applyRuntimeFieldMeta,
    assignLocalSchema, booleanFlag, buildCurrentDesignerDraft, buildDesignerDraftFromFormSchema, buildFormDesignerEditZone, buildFormFieldComponentMap, buildFormRuntimeFieldSettings, buildGeneratedTemplateFieldBase,
    buildPrimaryModelFieldSet, buildRelationAliases, buildRuntimeFormFieldSetting, buildRuntimeFormLayout, buildRuntimeFormLayoutNode, buildRuntimeFormLayoutNodes, buildRuntimeLayoutMeta, buildVisibleFormFieldSet,
    camelToSnake, canInlineRelation, canMoveRelationField, clampNumber, clearRelationGroupFields, collectFormDesignerComponentGroups, collectFormFieldComponents, collectRuntimeFieldComponents,
    collectSubTableRelationKeys, copyDefined, copyPropsToRuntimeField, downgradeDesignerFieldToText, findRelationGroup, firstNonBlank, handleButtonActionConfirm, handleChildTableSectionConfirm,
    handleConfigureBottomAction, handleEditChildTableSection, handleEditSubTableContainer, handleFieldAssetUpdated, handleFormDesignerMoreSelect, handleSubTableBridgeSync, isColumnLayoutComponent, isDictField,
    isGeneratedTemplateFieldCode, isGenericGeneratedFieldCode, isPlainObject, isReferenceField, isRelationField, isRelationFieldAllowed, isRowLayoutComponent, isUnconfiguredDictField,
    isUnconfiguredReferenceField, locateSubTableOnCanvas, matchesSourceField, mergeBusinessFieldAsset, mergeFieldWithFormComponent, mergePrimaryModelRef, mergeUniqueRefs, moveRelationField,
    normalizeAlign, normalizeBusinessFieldAsset, normalizeEditZoneFieldRefs, normalizeFormOpenMode, normalizeFormOpenModePatch, normalizeGeneratedTemplateFieldCodes, normalizeRelationFormRows, normalizeRelationKey,
    normalizeRuntimeComponentType, normalizeRuntimeFormSize, normalizeUnconfiguredDesignerField, openChildTableSectionWizard, orderRelationRefs, parseRelationConfig, relationKeyMatches, replaceModelFieldSettings,
    replaceZone, requiresDictConfig, requiresReferenceConfig, reserveGeneratedTemplateFieldCode, resolveCanvasFieldRefs, resolveDesignModelSchema, resolveDesignerFieldSortOrder, resolveNumber,
    resolvePrimaryFieldRefs, resolveRelationToggle, resolveRuntimeLayoutLabel, resolveSchema, resolveSelectedRelationFieldRefs, rewriteGeneratedTemplateField, sanitizeRuntimeFieldProps, sanitizeRuntimeLayoutProps,
    saveLayout, selectRelationGroupFields, selectedRelationRefsByModel, shouldRewriteGeneratedTemplateField, sortRelationGroupFields, syncActiveFormSchemaEntry, syncDesignerDraft, syncDesignerFieldWithFormComponent,
    syncFormDesignerSchemaToPageSchema, toBusinessFieldPayload, toPageField, toggleRelationField, updateEditZoneProps, updateFormDesignerLayout, upsertSubTableContainer, walkFormDesignerComponents,
    DICT_FIELD_TYPES, DICT_COMPONENT_TYPES, message, saving, shelfTab, activeObjectKey, formCreateDesignerRef, forgeFormDesignerRef,
    childTableWizardVisible, childTableWizardValue, relationOverviewVisible, buttonActionConfigVisible, buttonActionIndex, buttonActionValue, useLegacyFormCreateDesigner, activeFormDesignerRef,
    baseModelSchema, localSchema, localFormDesignerSchema, localViewSchema, effectiveModelSchema, designFields, editZone, pageModelRefs,
    buttonTargetPages, primaryModelCode, primaryDesignFields, relationFields, primaryFieldSet, relationFieldSet, usedFieldSet, selectedRelationFieldRefs,
    selectedRelationFieldSet, businessFields, primaryBusinessFields, primaryBusinessFieldAssets, systemFields, usedFields, unusedFields, formOpenModeOptions,
    editFormOpenMode, formGridColumns, formDesignerMoreOptions, relationFieldGroups, relationFormRows, formObjectTabs, isPrimaryObjectActive, activeRelationRow,
    activeRelationGroup, activeRelationCanvasFields, activeRelationAvailableFields, activeObjectTitle, activeObjectDescription, visibleShelfFields, subTableSummaryRows, subTableContainerRelationKeys,
    subTableContainerOrder,
  }
}
