/** BusinessFieldPropertyPanel.vue setup part 2. */
import { ArrowUndoOutline, SaveOutline } from '@vicons/ionicons5'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { businessObjectDesigner, businessObjectList } from '@/api/business-app'
import { previewFormula, validateFormula } from '@/api/formula'
import DictTypeSelect from '@/components/lowcode-builder/shared/DictTypeSelect.vue'
import FieldTypeSelect from '@/components/lowcode-builder/shared/FieldTypeSelect.vue'
import RegionTreeSelect from '@/components/RegionTreeSelect.vue'
import { COMMON_VALIDATION_PRESETS, getValidationPreset } from '@/utils/validation-presets'
import { camelToSnake } from '../form-first/namingUtils'
import FormulaConfigPanel from '../formula/FormulaConfigPanel.vue'
import FormulaDebuggerPanel from '../formula/FormulaDebuggerPanel.vue'
import FormulaDependencyGraph from '../formula/FormulaDependencyGraph.vue'
import FormulaExecutionLogDrawer from '../formula/FormulaExecutionLogDrawer.vue'
import FieldDefaultValueEditor from '../forge-form-designer/panels/FieldDefaultValueEditor.vue'
export function applyBusinessFieldPropertyPanelPart2(props, emit, expose, deps = {}) {
  const {
    __impl, mut, applyRequiredDefault, buildFormulaConfigPayload, buildPreviewSampleValues, buildValidationConfig, clearCrossObjectForm, clearFormulaForm,
    cloneConditionRule, collectFormulaVariableCodes, createDefaultConditionRule, createFieldForm, emptyDefaultValue, formatDefaultValue, formatLocalDate, formatLocalDateTime,
    handleValidateFormula, hasDefaultValue, insertFormulaToken, insertStringToken, isCurrentAutomaticDefault, isNumericStorageType, isOneHopPath, isTabVisible,
    modeToTriggerMode, normalizeActiveTab, normalizeDefaultValueForEditor, normalizeExpressionToken, normalizeFormulaDependsOn, normalizeFormulaFunctionRefs, normalizeNullableNumber, normalizePayload,
    onConditionExpressionChange, onConditionModeChange, onConditionRuleCompiled, onConditionRuleValidation, onDecimalTotalDigitsChange, onFormulaTypeChange, onValidationPresetChange, openFormulaPreview,
    resetForm, resolveAutomaticRequiredDefault, resolveNumericStorageRange, setFormulaTriggerMode, supportsAutomaticRequiredDefault, updateFormulaEnabled, updateRequired, validateFormulaConfigLocal,
    activeTab, defaultVisibleTabs, enabledTabs, formulaOnly, form, fieldTypeOptions, componentOptions, queryTypeOptions,
    switchDefaultOptions, statusOptions, sensitiveOptions, commonValidationOptions, formulaValidating, formulaPreviewing, formulaValidateResult, formulaPreviewResult,
    formulaPreviewDialogVisible, formulaDebuggerVisible, formulaLogVisible, formulaGraphVisible, formulaConditionRuleValidation, formulaPreviewForm, automaticRequiredDefault, encryptOptions,
    cascadeModeOptions, payload, selectedFormulaField, formulaToolFields, canOpenFormulaDebugger, hasFormulaToolFields, changed, needsDict,
    isUserSelectCascadeField, showCascadeConfig, isRecordSelectorField, isObjectReferenceField, businessObjectOptions, businessObjectLoading, referenceTargetFieldsMap, referenceTargetFieldLoadPromises,
    isRelationField, relationSelectionMode, unifiedRelationObjectCode, unifiedDisplayField, unifiedValueField, unifiedTargetFieldOptions, normalizedDataType, supportsTextLength,
    isDecimalType, isNumericDataType, formulaEnabled, formulaFeedbackLines, previewTargetLabel, previewVariableCodes, previewVariableFields, canPreviewCalculate,
    canOpenFormulaPreview, lengthMax, decimalPrecisionMax, numericStorageRange, numericMinimumPlaceholder, numericMaximumPlaceholder, numericStorageHint, hasSafeRequiredDefault,
    requiredDefaultHint, dependFieldOptions, cascadeSourceFieldOptions, hasOrgFieldForCascade,
  } = deps
  function appendFormulaFunctionRefs(result, expression) {
    const text = stripFormulaStringLiterals(expression)
    if (!text)
      return
    const pattern = /([a-z_]\w*(?:\.[a-z_]\w*)*)\s*\(/gi
    let match = pattern.exec(text)
    while (match) {
      const token = match[1]
      if (isManagedFormulaFunctionName(token) && !result.includes(token))
        result.push(token)
      match = pattern.exec(text)
    }
  }

  function isManagedFormulaFunctionName(token) {
    const value = String(token || '')
    return value.includes('.') || value === 'date_to_string'
  }

  function syncFormulaDependsOnFromExpression() {
    if (mut.resetting || !formulaEnabled.value || form.formulaType === 'AGGREGATE'
      || form.formulaType === 'LOOKUP' || form.formulaCrossObjectEnabled) {
      return
    }
    const hasExpression = Boolean(String(form.formulaExpression || '').trim())
      || (form.formulaType === 'CONDITIONAL' && Boolean(String(form.formulaConditionExpression || '').trim()))
    const expressionDeps = collectExpressionFieldCodes()
    if (!hasExpression && !form.formulaDependsOn.length)
      return
    if (isSameStringArray(form.formulaDependsOn, expressionDeps))
      return
    form.formulaDependsOn = expressionDeps
  }

  function collectExpressionFieldCodes() {
    const availableFields = new Set((props.allFields || [])
      .map(item => item?.fieldCode || item?.field)
      .filter(Boolean)
      .filter(fieldCode => fieldCode !== form.fieldCode))
    const result = []
    appendExpressionVariables(result, form.formulaExpression)
    if (form.formulaType === 'CONDITIONAL')
      appendExpressionVariables(result, form.formulaConditionExpression)
    return result.filter(fieldCode => availableFields.has(fieldCode))
  }

  function appendFormulaDependsOn(result, dependsOn) {
    ;(Array.isArray(dependsOn) ? dependsOn : []).forEach(value => appendFormulaVariable(result, value))
  }

  function appendExpressionVariables(result, expression) {
    extractFormulaVariables(expression).forEach(value => appendFormulaVariable(result, value))
  }

  function appendFormulaVariable(result, value) {
    const append = (value) => {
      const fieldCode = String(value || '').trim()
      if (fieldCode && !result.includes(fieldCode))
        result.push(fieldCode)
    }
    append(value)
  }

  function isSameStringArray(left = [], right = []) {
    const normalizedLeft = Array.isArray(left) ? left : []
    const normalizedRight = Array.isArray(right) ? right : []
    if (normalizedLeft.length !== normalizedRight.length)
      return false
    return normalizedLeft.every((item, index) => item === normalizedRight[index])
  }

  function extractFormulaVariables(expression) {
    const text = stripFormulaStringLiterals(expression)
    if (!text)
      return []
    const variables = []
    const pattern = /[a-z_]\w*/gi
    let match = pattern.exec(text)
    while (match) {
      const token = match[0]
      const previous = text[match.index - 1] || ''
      const nextIndex = match.index + token.length
      const nextText = text.slice(nextIndex).trimStart()
      if (!isFormulaReservedToken(token) && previous !== '.' && !nextText.startsWith('(') && !variables.includes(token))
        variables.push(token)
      match = pattern.exec(text)
    }
    return variables
  }

  function stripFormulaStringLiterals(expression) {
    return String(expression || '').replace(/'[^']*'|"[^"]*"/g, ' ')
  }

  function isFormulaReservedToken(token) {
    return [
      'true',
      'false',
      'null',
      'nil',
      'and',
      'or',
      'not',
      'if',
      'else',
      'return',
      'math',
      'string',
      'seq',
      'date',
    ].includes(String(token || '').toLowerCase())
  }

  function resolveDependFieldMeta(fieldCode) {
    return (props.allFields || []).find((item) => {
      const code = item?.fieldCode || item?.field
      return code === fieldCode
    }) || null
  }

  function buildDependFieldLabel(fieldMeta, fallback) {
    const name = fieldMeta?.fieldName || fieldMeta?.label || fallback
    const code = fieldMeta?.fieldCode || fieldMeta?.field || fallback
    return `${name}（${code}）`
  }

  function resolvePreviewInputType(fieldMeta, fieldCode = '') {
    const fieldType = String(fieldMeta?.fieldType || '').toUpperCase()
    const componentType = String(fieldMeta?.componentType || fieldMeta?.type || '').toLowerCase()
    const dataType = String(fieldMeta?.dataType || '').toLowerCase()
    const code = String(fieldMeta?.fieldCode || fieldMeta?.field || fieldCode).toLowerCase()
    if (fieldType === 'SWITCH' || componentType === 'switch')
      return 'switch'
    if (includesAny(code, ['qty', 'quantity', 'count', 'num', 'number', 'price', 'amount', 'money', 'fee', 'cost', 'total', 'rate', 'ratio', 'percent']))
      return 'number'
    if (['NUMBER', 'MONEY'].includes(fieldType)
      || ['number', 'inputnumber', 'input-number'].includes(componentType)
      || ['int', 'integer', 'bigint', 'decimal', 'double', 'float'].includes(dataType)) {
      return 'number'
    }
    if (!fieldMeta && form.formulaType === 'CALC')
      return 'number'
    return 'text'
  }

  function guessFormulaSampleValue(fieldMeta, index = 0, fallbackCode = '') {
    const code = String(fieldMeta?.fieldCode || fieldMeta?.field || fallbackCode).toLowerCase()
    const type = String(fieldMeta?.fieldType || fieldMeta?.type || fieldMeta?.componentType || '').toUpperCase()
    const defaultValue = fieldMeta?.defaultValue
    if (defaultValue !== null && defaultValue !== undefined && defaultValue !== '')
      return defaultValue
    if (includesAny(code, ['qty', 'quantity', 'count', 'num', 'number', '数量', '件数']))
      return 3
    if (includesAny(code, ['price', 'amount', 'money', 'fee', 'cost', 'total', '金额', '单价', '价格', '费用', '成本']))
      return 100
    if (includesAny(code, ['rate', 'ratio', 'percent', '折扣', '比例', '率']))
      return 0.1
    if (type.includes('SWITCH') || type.includes('BOOLEAN'))
      return true
    if (type.includes('DATE') || type.includes('TIME'))
      return '2026-01-01'
    if (type.includes('TEXT') || type.includes('VARCHAR') || type.includes('CHAR'))
      return '示例'
    return index + 1
  }

  function includesAny(value, keywords = []) {
    return keywords.some(keyword => value.includes(keyword))
  }

  function initializePreviewForm({ overwrite = false } = {}) {
    mut.formulaPreviewInitializing = true
    const sampleValues = buildPreviewSampleValues()
    Object.keys(formulaPreviewForm).forEach((key) => {
      if (!Object.prototype.hasOwnProperty.call(sampleValues, key))
        delete formulaPreviewForm[key]
    })
    Object.entries(sampleValues).forEach(([key, value]) => {
      if (overwrite || !hasPreviewInputValue(formulaPreviewForm[key]))
        formulaPreviewForm[key] = value
    })
    nextTick(() => {
      mut.formulaPreviewInitializing = false
    })
  }

  function resetPreviewForm() {
    clearPreviewTimer()
    Object.keys(formulaPreviewForm).forEach((key) => {
      delete formulaPreviewForm[key]
    })
  }

  function fillPreviewSampleValues() {
    initializePreviewForm({ overwrite: true })
    schedulePreviewCalculation(0)
  }

  function hasPreviewInputValue(value) {
    if (Array.isArray(value))
      return value.length > 0
    return value !== null && value !== undefined && value !== ''
  }

  function schedulePreviewCalculation(delay = 320) {
    clearPreviewTimer()
    if (!formulaPreviewDialogVisible.value || !canPreviewCalculate.value)
      return
    mut.formulaPreviewTimer = setTimeout(() => {
      mut.formulaPreviewTimer = null
      handlePreviewFormula()
    }, delay)
  }

  function clearPreviewTimer() {
    if (!mut.formulaPreviewTimer)
      return
    clearTimeout(mut.formulaPreviewTimer)
    mut.formulaPreviewTimer = null
  }

  function buildFormulaPreviewPayload(sampleValues) {
    const payload = {
      expression: form.formulaExpression,
      type: form.formulaType || 'CALC',
      dependsOn: previewVariableCodes.value,
      sampleValues,
    }
    if (form.formulaType === 'CONDITIONAL') {
      const expression = form.formulaConditionExpression || form.formulaExpression
      payload.expression = expression
      payload.condition = {
        expression,
        trueValue: form.formulaConditionTrueValue ?? '',
        falseValue: form.formulaConditionFalseValue ?? '',
      }
    }
    return payload
  }

  async function handlePreviewFormula() {
    if (!form.formulaExpression)
      return
    if (!canPreviewCalculate.value) {
      formulaPreviewResult.value = { success: false, errorMessage: '请先填写变量字段值' }
      return
    }
    const sampleValues = { ...formulaPreviewForm }
    formulaPreviewing.value = true
    formulaPreviewResult.value = null
    try {
      const res = await previewFormula(buildFormulaPreviewPayload(sampleValues))
      formulaPreviewResult.value = res?.data ?? res
    }
    catch (e) {
      formulaPreviewResult.value = { success: false, errorMessage: e?.message || '预览请求失败' }
    }
    finally {
      formulaPreviewing.value = false
    }
  }

  function formatPreviewValue(value) {
    if (value === null || value === undefined || value === '')
      return '-'
    if (typeof value === 'object')
      return JSON.stringify(value)
    return String(value)
  }

  function updateCascadeEnabled(value) {
    form.basicProps.cascade = createDefaultCascade({
      ...(form.basicProps.cascade || {}),
      enabled: value,
      // 人员组件级联固定按组织过滤，不提供接口/字典联动方式
      mode: isUserSelectCascadeField.value ? 'orgFilter' : (form.basicProps.cascade?.mode || 'linkedDict'),
    })
  }

  function updateCascadeIncludeChildren(value) {
    form.basicProps.cascade = createDefaultCascade({
      ...(form.basicProps.cascade || {}),
      includeChildren: value,
    })
  }

  function createDefaultCascade(source = {}) {
    return {
      enabled: !!source.enabled,
      sourceField: source.sourceField || '',
      mode: source.mode || source.matchMode || 'linkedDict',
      paramName: source.paramName || '',
      includeChildren: source.includeChildren !== false,
      clearOnParentChange: source.clearOnParentChange !== false,
    }
  }

  function normalizeCascade(source = {}) {
    const cascade = createDefaultCascade(source)
    if (!cascade.enabled || !cascade.sourceField)
      return { ...cascade, enabled: false }
    if (cascade.mode === 'orgFilter') {
      // 组织→人员级联：参数名无意义，仅保留组织范围开关
      cascade.paramName = ''
      return cascade
    }
    if (cascade.mode !== 'remoteParam')
      cascade.paramName = ''
    return cascade
  }

  function createDefaultRecordSelector(source = {}) {
    const config = source && typeof source === 'object' ? source : {}
    return {
      suiteCode: config.suiteCode || '',
      objectCode: config.objectCode || '',
      valueField: config.valueField || 'id',
      labelField: config.labelField || config.labelSourceField || '',
      targetLabelField: config.targetLabelField || config.labelTargetField || '',
      displayFields: normalizeTextList(config.displayFields),
      keywordFields: normalizeTextList(config.keywordFields),
      fieldMappings: normalizeMappingList(config.fieldMappings || config.mappings),
      searchParams: normalizeSearchParams(config.searchParams),
    }
  }

  function buildRecordSelectorConfig(source) {
    const isRecordSelector = source.fieldType === 'RECORD_SELECTOR' || source.componentType === 'recordSelector'
    // 下拉（REFERENCE/objectReference）与弹窗共用选择器高级配置；下拉模式目标对象存于 referenceObjectCode，仅需持久化搜索/过滤等配置
    if (!isRecordSelector && source.fieldType !== 'REFERENCE' && source.componentType !== 'objectReference')
      return null
    const config = createDefaultRecordSelector({
      suiteCode: source.recordSelectorSuiteCode,
      objectCode: source.recordSelectorObjectCode,
      valueField: source.recordSelectorValueField,
      labelField: source.recordSelectorLabelField,
      targetLabelField: source.recordSelectorTargetLabelField,
      displayFields: source.recordSelectorDisplayFields,
      keywordFields: source.recordSelectorKeywordFields,
      fieldMappings: parseMappingLines(source.recordSelectorMappingsText),
      searchParams: parseSearchParamLines(source.recordSelectorSearchParamsText),
    })
    // 弹窗模式必须指定目标对象；下拉模式无高级配置时不落盘空对象
    if (isRecordSelector && !config.objectCode)
      return null
    const pruned = pruneRecordSelectorConfig(config)
    return Object.keys(pruned).length ? pruned : null
  }

  function pruneRecordSelectorConfig(config = {}) {
    const result = {}
    ;['suiteCode', 'objectCode', 'valueField', 'labelField', 'targetLabelField'].forEach((key) => {
      if (config[key])
        result[key] = config[key]
    })
    if (config.displayFields?.length)
      result.displayFields = config.displayFields
    if (config.keywordFields?.length)
      result.keywordFields = config.keywordFields
    if (config.fieldMappings?.length)
      result.fieldMappings = config.fieldMappings
    if (config.searchParams && Object.keys(config.searchParams).length)
      result.searchParams = config.searchParams
    return result
  }

  function normalizeTextList(value) {
    if (Array.isArray(value))
      return value.map(item => String(item || '').trim()).filter(Boolean)
    if (typeof value === 'string')
      return parseTextList(value)
    return []
  }

  function parseTextList(value) {
    return String(value || '')
      .split(/[\n,，]/)
      .map(item => item.trim())
      .filter(Boolean)
  }

  function normalizeMappingList(value) {
    if (Array.isArray(value)) {
      return value
        .map(item => ({
          sourceField: String(item?.sourceField || item?.source || '').trim(),
          targetField: String(item?.targetField || item?.target || '').trim(),
        }))
        .filter(item => item.sourceField && item.targetField)
    }
    if (value && typeof value === 'object') {
      return Object.entries(value)
        .map(([sourceField, targetField]) => ({
          sourceField: String(sourceField || '').trim(),
          targetField: String(targetField || '').trim(),
        }))
        .filter(item => item.sourceField && item.targetField)
    }
    if (typeof value === 'string')
      return parseMappingLines(value)
    return []
  }

  function normalizeSearchParams(value) {
    if (!value)
      return {}
    if (typeof value === 'string')
      return parseSearchParamLines(value)
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      return Object.entries(value).reduce((result, [key, item]) => {
        const paramKey = String(key || '').trim()
        if (paramKey && item !== undefined && item !== null && item !== '') {
          result[paramKey] = item
        }
        return result
      }, {})
    }
    return {}
  }

  function parseMappingLines(value) {
    return String(value || '')
      .split(/\n/)
      .map(item => item.trim())
      .filter(Boolean)
      .map((item) => {
        const parts = item.split(/\s*(?:=>|=|:)\s*/, 2)
        return {
          sourceField: String(parts[0] || '').trim(),
          targetField: String(parts[1] || '').trim(),
        }
      })
      .filter(item => item.sourceField && item.targetField)
  }

  function parseSearchParamLines(value) {
    const text = String(value || '').trim()
    if (!text)
      return {}
    if (text.startsWith('{')) {
      try {
        const parsed = JSON.parse(text)
        return normalizeSearchParams(parsed)
      }
      catch {
        return {}
      }
    }
    return text
      .split(/\n/)
      .map(item => item.trim())
      .filter(Boolean)
      .reduce((result, item) => {
        const parts = item.split(/\s*(?:=>|=|:)\s*/, 2)
        const key = String(parts[0] || '').trim()
        const value = String(parts[1] || '').trim()
        if (key && value)
          result[key] = value
        return result
      }, {})
  }

  function mappingsToLines(value) {
    return normalizeMappingList(value)
      .map(item => `${item.sourceField}=${item.targetField}`)
      .join('\n')
  }

  function searchParamsToLines(value) {
    return Object.entries(normalizeSearchParams(value))
      .map(([key, item]) => `${key}=${item}`)
      .join('\n')
  }

  function applyFieldTypeDefaults(fieldType) {
    const defaults = {
      TEXT: { dataType: 'varchar', componentType: 'input', length: 128, precision: 2, queryType: 'like' },
      MULTILINE: { dataType: 'text', componentType: 'textarea', length: null, precision: 2, queryType: 'like' },
      NUMBER: { dataType: 'int', componentType: 'number', length: 11, precision: 0, queryType: 'eq' },
      MONEY: { dataType: 'decimal', componentType: 'number', length: 18, precision: 2, queryType: 'eq' },
      DATE: { dataType: 'date', componentType: 'date', length: null, precision: null, queryType: 'eq' },
      DATETIME: { dataType: 'datetime', componentType: 'datetime', length: null, precision: null, queryType: 'eq' },
      DICT: { dataType: 'varchar', componentType: 'select', length: 64, precision: 2, queryType: 'eq' },
      SELECT: { dataType: 'bigint', componentType: 'treeSelect', length: null, precision: null, queryType: 'eq' },
      RADIO: { dataType: 'varchar', componentType: 'radio', length: 64, precision: 2, queryType: 'eq' },
      CHECKBOX: { dataType: 'varchar', componentType: 'checkbox', length: 255, precision: 2, queryType: 'in' },
      SWITCH: { dataType: 'tinyint', componentType: 'switch', length: 1, precision: 0, queryType: 'eq' },
      FILE: { dataType: 'varchar', componentType: 'fileUpload', length: 512, precision: 2, queryType: 'eq' },
      IMAGE: { dataType: 'varchar', componentType: 'imageUpload', length: 512, precision: 2, queryType: 'eq' },
      USER: { dataType: 'bigint', componentType: 'userSelect', length: null, precision: null, queryType: 'eq' },
      DEPT: { dataType: 'bigint', componentType: 'orgTreeSelect', length: null, precision: null, queryType: 'eq' },
      REGION: { dataType: 'varchar', componentType: 'regionTreeSelect', length: 32, precision: 2, queryType: 'eq' },
      REFERENCE: { dataType: 'bigint', componentType: 'objectReference', length: null, precision: null, queryType: 'eq' },
      RECORD_SELECTOR: { dataType: 'bigint', componentType: 'recordSelector', length: null, precision: null, queryType: 'eq' },
    }[fieldType]
    if (!defaults)
      return
    Object.assign(form, defaults)
    form.minValue = null
    form.maxValue = null
    if (!['DICT', 'RADIO', 'CHECKBOX'].includes(fieldType))
      form.dictType = ''
  }

  async function loadBusinessObjectOptions() {
    if (businessObjectOptions.value.length)
      return
    if (mut.businessObjectLoadPromise)
      return mut.businessObjectLoadPromise
    businessObjectLoading.value = true
    mut.businessObjectLoadPromise = (async () => {
      try {
        const res = await businessObjectList({})
        const list = Array.isArray(res.data) ? res.data : []
        const seen = new Set()
        businessObjectOptions.value = list
          .filter((item) => {
            if (!item.objectCode || seen.has(item.objectCode))
              return false
            seen.add(item.objectCode)
            return true
          })
          .map(item => ({
            label: item.objectName || item.objectCode,
            value: item.objectCode,
            object: item,
          }))
      }
      catch {
        businessObjectOptions.value = []
      }
      finally {
        businessObjectLoading.value = false
        mut.businessObjectLoadPromise = null
      }
    })()
    return mut.businessObjectLoadPromise
  }

  async function loadReferenceTargetFields(objectCode) {
    if (!objectCode)
      return
    const cached = referenceTargetFieldsMap.value[objectCode]
    if (Array.isArray(cached?.options) && cached.options.length)
      return
    if (referenceTargetFieldLoadPromises[objectCode])
      return referenceTargetFieldLoadPromises[objectCode]
    referenceTargetFieldLoadPromises[objectCode] = (async () => {
      if (!businessObjectOptions.value.length)
        await loadBusinessObjectOptions()
      const target = businessObjectOptions.value.find(item => item.value === objectCode)?.object
      if (!target?.id)
        return
      try {
        const res = await businessObjectDesigner(target.id)
        const fields = res.data?.fields || res.data?.modelSchema?.fields || []
        const options = fields
          .filter(field => !['tenantId', 'tenant_id', 'createBy', 'create_by', 'createTime', 'create_time', 'updateBy', 'update_by', 'updateTime', 'update_time', 'delFlag', 'del_flag'].includes(field.fieldCode || field.field))
          .map(field => ({
            label: `${field.fieldName || field.label || field.fieldCode || field.field}（${field.fieldCode || field.field}）`,
            value: field.fieldCode || field.field,
            field,
          }))
        referenceTargetFieldsMap.value = { ...referenceTargetFieldsMap.value, [objectCode]: { options } }
      }
      catch {
        // 失败时不缓存空结果，下次进入编辑仍可重试
      }
      finally {
        delete referenceTargetFieldLoadPromises[objectCode]
      }
    })()
    return referenceTargetFieldLoadPromises[objectCode]
  }

  watch(
    () => isObjectReferenceField.value,
    async (isReference) => {
      if (!isReference)
        return
      await loadBusinessObjectOptions()
      if (form.referenceObjectCode)
        await loadReferenceTargetFields(form.referenceObjectCode)
    },
  )

  watch(
    () => form.referenceObjectCode,
    async (value, oldValue) => {
      if (!value || value === oldValue)
        return
      if (!businessObjectOptions.value.length)
        await loadBusinessObjectOptions()
      if (oldValue)
        form.referenceDisplayField = ''
      if (oldValue)
        form.referenceValueField = ''
      await loadReferenceTargetFields(value)
    },
  )

  // ─── Unified Relation Field Functions ────────────────────────────────────────
  function switchRelationSelectionMode(mode) {
    const currentObjectCode = unifiedRelationObjectCode.value
    const currentDisplayField = unifiedDisplayField.value
    const currentValueField = unifiedValueField.value

    if (mode === 'popup') {
      // Switch to recordSelector
      form.fieldType = 'RECORD_SELECTOR'
      form.componentType = 'recordSelector'
      form.recordSelectorObjectCode = currentObjectCode
      form.recordSelectorLabelField = currentDisplayField
      form.recordSelectorValueField = currentValueField || 'id'
      // Clear objectReference fields
      form.referenceObjectCode = ''
      form.referenceDisplayField = ''
      form.referenceValueField = ''
    }
    else {
      // Switch to objectReference (dropdown)
      form.fieldType = 'REFERENCE'
      form.componentType = 'objectReference'
      form.referenceObjectCode = currentObjectCode
      form.referenceDisplayField = currentDisplayField
      form.referenceValueField = currentValueField || 'id'
      // Clear recordSelector fields
      form.recordSelectorObjectCode = ''
      form.recordSelectorLabelField = ''
      form.recordSelectorValueField = ''
    }
  }

  async function handleUnifiedObjectCodeChange(value) {
    if (isRecordSelectorField.value) {
      form.recordSelectorObjectCode = value || ''
      form.recordSelectorLabelField = ''
      form.recordSelectorValueField = ''
    }
    else {
      form.referenceObjectCode = value || ''
      form.referenceDisplayField = ''
      form.referenceValueField = ''
    }
    if (value) {
      if (!businessObjectOptions.value.length)
        await loadBusinessObjectOptions()
      await loadReferenceTargetFields(value)
      // Auto-infer display/value fields
      autoInferRelationFields(value)
    }
  }

  function autoInferRelationFields(objectCode) {
    const fieldOptions = referenceTargetFieldsMap.value[objectCode]?.options || []
    if (!fieldOptions.length)
      return
    // Auto-infer display field: prefer name/title
    const displayCandidate = fieldOptions.find(f => ['name', 'title', 'label'].includes(f.value))
      || fieldOptions.find(f => /name|title/i.test(f.value))
    // Auto-infer value field: prefer id
    const valueCandidate = fieldOptions.find(f => f.value === 'id')

    const inferredDisplay = displayCandidate?.value || ''
    const inferredValue = valueCandidate?.value || 'id'

    if (isRecordSelectorField.value) {
      if (!form.recordSelectorLabelField)
        form.recordSelectorLabelField = inferredDisplay
      if (!form.recordSelectorValueField)
        form.recordSelectorValueField = inferredValue
    }
    else {
      if (!form.referenceDisplayField)
        form.referenceDisplayField = inferredDisplay
      if (!form.referenceValueField)
        form.referenceValueField = inferredValue
    }
    // 两种模式共用：未配置搜索字段时按名称/编码/标题推断，下拉搜索与弹窗关键字共用
    if (!form.recordSelectorKeywordFields?.length && inferredDisplay) {
      const keywordCandidates = fieldOptions
        .filter(f => /name|code|title/i.test(f.value))
        .map(f => f.value)
        .slice(0, 3)
      form.recordSelectorKeywordFields = keywordCandidates.length ? keywordCandidates : [inferredDisplay]
    }
  }

  function handleUnifiedDisplayFieldChange(value) {
    if (isRecordSelectorField.value)
      form.recordSelectorLabelField = value || ''
    else
      form.referenceDisplayField = value || ''
  }

  function handleUnifiedValueFieldChange(value) {
    if (isRecordSelectorField.value)
      form.recordSelectorValueField = value || ''
    else
      form.referenceValueField = value || ''
  }

  watch(
    () => isRelationField.value,
    async (isRelation) => {
      if (!isRelation)
        return
      await loadBusinessObjectOptions()
      const objectCode = unifiedRelationObjectCode.value
      if (objectCode)
        await loadReferenceTargetFields(objectCode)
    },
    { immediate: true },
  )

  watch(
    () => unifiedRelationObjectCode.value,
    async (objectCode) => {
      if (!isRelationField.value || !objectCode)
        return
      await loadReferenceTargetFields(objectCode)
    },
  )

  watch(
    () => isRecordSelectorField.value,
    async (isSelector) => {
      if (!isSelector)
        return
      await loadBusinessObjectOptions()
      if (form.recordSelectorObjectCode)
        await loadReferenceTargetFields(form.recordSelectorObjectCode)
    },
  )

  expose?.({
    resetForm,
    openTab: tab => (activeTab.value = normalizeActiveTab(tab || 'basic')),
    getPayload: () => payload.value,
    hasChanges: () => changed.value,
  })
  __impl.appendFormulaFunctionRefs = appendFormulaFunctionRefs
  __impl.isManagedFormulaFunctionName = isManagedFormulaFunctionName
  __impl.syncFormulaDependsOnFromExpression = syncFormulaDependsOnFromExpression
  __impl.collectExpressionFieldCodes = collectExpressionFieldCodes
  __impl.appendFormulaDependsOn = appendFormulaDependsOn
  __impl.appendExpressionVariables = appendExpressionVariables
  __impl.appendFormulaVariable = appendFormulaVariable
  __impl.isSameStringArray = isSameStringArray
  __impl.extractFormulaVariables = extractFormulaVariables
  __impl.stripFormulaStringLiterals = stripFormulaStringLiterals
  __impl.isFormulaReservedToken = isFormulaReservedToken
  __impl.resolveDependFieldMeta = resolveDependFieldMeta
  __impl.buildDependFieldLabel = buildDependFieldLabel
  __impl.resolvePreviewInputType = resolvePreviewInputType
  __impl.guessFormulaSampleValue = guessFormulaSampleValue
  __impl.includesAny = includesAny
  __impl.initializePreviewForm = initializePreviewForm
  __impl.resetPreviewForm = resetPreviewForm
  __impl.fillPreviewSampleValues = fillPreviewSampleValues
  __impl.hasPreviewInputValue = hasPreviewInputValue
  __impl.schedulePreviewCalculation = schedulePreviewCalculation
  __impl.clearPreviewTimer = clearPreviewTimer
  __impl.buildFormulaPreviewPayload = buildFormulaPreviewPayload
  __impl.handlePreviewFormula = handlePreviewFormula
  __impl.formatPreviewValue = formatPreviewValue
  __impl.updateCascadeEnabled = updateCascadeEnabled
  __impl.updateCascadeIncludeChildren = updateCascadeIncludeChildren
  __impl.createDefaultCascade = createDefaultCascade
  __impl.normalizeCascade = normalizeCascade
  __impl.createDefaultRecordSelector = createDefaultRecordSelector
  __impl.buildRecordSelectorConfig = buildRecordSelectorConfig
  __impl.pruneRecordSelectorConfig = pruneRecordSelectorConfig
  __impl.normalizeTextList = normalizeTextList
  __impl.parseTextList = parseTextList
  __impl.normalizeMappingList = normalizeMappingList
  __impl.normalizeSearchParams = normalizeSearchParams
  __impl.parseMappingLines = parseMappingLines
  __impl.parseSearchParamLines = parseSearchParamLines
  __impl.mappingsToLines = mappingsToLines
  __impl.searchParamsToLines = searchParamsToLines
  __impl.applyFieldTypeDefaults = applyFieldTypeDefaults
  __impl.loadBusinessObjectOptions = loadBusinessObjectOptions
  __impl.loadReferenceTargetFields = loadReferenceTargetFields
  __impl.switchRelationSelectionMode = switchRelationSelectionMode
  __impl.handleUnifiedObjectCodeChange = handleUnifiedObjectCodeChange
  __impl.autoInferRelationFields = autoInferRelationFields
  __impl.handleUnifiedDisplayFieldChange = handleUnifiedDisplayFieldChange
  __impl.handleUnifiedValueFieldChange = handleUnifiedValueFieldChange

  return {
    ...deps,
  }
}
