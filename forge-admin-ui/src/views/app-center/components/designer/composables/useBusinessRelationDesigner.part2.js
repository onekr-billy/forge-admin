/** BusinessRelationDesigner.vue setup part 2. */
import { AddOutline, TrashOutline } from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  businessObjectDesigner,
  businessObjectList,
  businessObjectRelations,
  saveBusinessObjectDesigner,
} from '@/api/business-app'
import LowcodeErDiagram from '@/components/lowcode-builder/model/LowcodeErDiagram.vue'
import { cloneSchema, isSameSchema } from '@/components/lowcode-builder/model/model-schema'
import {
  applyLinkageSchemaToFields,
  createLinkageSchemaFromFields,
  normalizeLinkageSchema,
  validateLinkageSchema,
} from '../form-first/linkageSchema'
export function applyBusinessRelationDesignerPart2(props, emit, expose, deps = {}) {
  const {
    __impl, mut, addLinkageRule, addRelation, addSelectorMapping, addSelectorSearchParam, applyRelationWizardDefaults, buildDesignerActionsFromRelations,
    buildRelationConfig, buildRelationDraft, buildRelationSelectorConfig, buildSelectorDisplayFields, buildSelectorMappings, buildSelectorSearchParams, confirmRelationWizard, createApprovalQuantityAction,
    createSelectorMappingRow, createSelectorMappingRows, createSelectorSearchParamRow, createSelectorSearchParamRows, findApprovalQuantityAction, handleErConnect, isManagedApprovalQuantityAction, listToLines,
    loadBusinessObjects, loadRelations, mappingsToLines, normalizeChildSaveMode, normalizeDisplayFieldCodes, normalizeMappingList, normalizeRelation, normalizeRelationSelector,
    normalizeSearchParams, normalizeSelectorSearchParamRow, normalizeTextList, openRelationWizard, parseMappingLines, parseRelationConfig, parseSearchParamLines, parseSelectorParamValue,
    parseTextList, removeActiveRelation, removeLinkageRule, removeRelation, removeSelectorMapping, removeSelectorSearchParam, resetLinkageSchema, resolveApprovalQuantityConfig,
    saveRelations, scrollToRelationSection, searchParamsToLines, selectErDiagram, selectLinkageRule, selectRelation, toRelationPayload, updateErCandidateObjects,
    updateInlineCreate, updateInlineEdit, updateLinkageSource, updateLinkageTarget, updateLinkageType, updateRelationApprovalQuantityEnabled, updateRelationSelectorEnabled, updateRuleClearOnChange,
    updateRuleEnabled, updateSelectorObject, updateSelectorSearchParamSource, updateShowInDetail, updateStatus, updateTargetObject, message, loading,
    saving, activePanel, activeRelationKey, activeLinkageRuleId, erCandidateObjectCodes, businessObjects, localRelations, localLinkage,
    targetFieldsMap, targetFieldLoadingMap, relationsLoaded, relationDirty, relationWizardVisible, relationWizardLoading, relationWizardForm, childSaveModeOptions,
    approvalQuantityOperationOptions, selectorFilterSourceOptions, linkageTypeOptions, emptyStrategyOptions, methodOptions, sourceFieldOptions, targetObjectOptions, objectNameMap,
    fieldMap, activeRelation, erDiagramSummary, erDiagramSubtitle, erDiagramModels,
  } = deps
  function findForeachQuantityStep(action = {}) {
    const steps = Array.isArray(action.actionConfig?.steps) ? action.actionConfig.steps : []
    for (const foreachStep of steps) {
      if (String(foreachStep?.stepType || '').toUpperCase() !== 'FOREACH')
        continue
      const childSteps = Array.isArray(foreachStep.stepConfig?.steps) ? foreachStep.stepConfig.steps : []
      const quantityStep = childSteps.find((step) => {
        const config = step?.stepConfig || {}
        return String(step?.stepType || '').toUpperCase() === 'DOMAIN_ACTION'
          && String(config.actionType || '').toUpperCase() === 'QUANTITY'
      })
      if (quantityStep) {
        return {
          foreachStep,
          quantityStep,
          itemAlias: foreachStep.stepConfig?.itemAlias || 'item',
        }
      }
    }
    return null
  }

  function relationQuantityActionCode(relation = {}) {
    return `relation_quantity_${relationCollectionKey(relation)}`
  }

  function relationCollectionKey(relation = {}) {
    return relation.relationKey
      || lowerSnake(relation.targetObjectCode || relation.relationName || relation.clientKey || 'detail')
  }

  function wrapExpression(path) {
    return path && !path.endsWith('undefined') && !path.endsWith('null') ? `\${${path}}` : ''
  }

  function unwrapFieldPath(value, prefix = '') {
    const text = String(value || '').trim().replace(/^\$\{|\}$/g, '')
    return prefix && text.startsWith(prefix) ? text.slice(prefix.length) : ''
  }

  function inferSourceFieldByTokens(tokens = []) {
    return sourceFieldOptions.value.find(option => tokens.some(token => optionMatchesToken(option, token)))?.value || ''
  }

  function inferTargetFieldByTokens(relation = {}, tokens = []) {
    return targetFieldOptions(relation).find(option => tokens.some(token => optionMatchesToken(option, token)))?.value || ''
  }

  function optionMatchesToken(option = {}, token = '') {
    const textValue = `${option.label || ''} ${option.value || ''}`.toLowerCase()
    return textValue.includes(String(token || '').toLowerCase())
  }

  function normalizeLinkageRuleDraft(rule = {}) {
    const sourceField = rule.sourceField || ''
    const targetField = rule.targetField || ''
    const type = linkageTypeOptions.some(item => item.value === rule.type) ? rule.type : 'linkedDict'
    const sourceFieldConfig = fieldMap.value.get(sourceField) || {}
    const targetFieldConfig = fieldMap.value.get(targetField) || {}
    const dataSourceType = resolveLinkageDataSourceType(type)
    return {
      ruleId: rule.ruleId || createLinkageRuleId(sourceField, targetField),
      type,
      sourceField,
      targetField,
      dataSourceType,
      matchMode: type,
      dictConfig: {
        ...(rule.dictConfig || {}),
        sourceDictType: rule.dictConfig?.sourceDictType || sourceFieldConfig.dictType || '',
        targetDictType: rule.dictConfig?.targetDictType || targetFieldConfig.dictType || '',
        linkedDictType: rule.dictConfig?.linkedDictType || sourceFieldConfig.dictType || '',
        parentValueSource: rule.dictConfig?.parentValueSource || 'sourceValue',
      },
      remoteConfig: {
        ...(rule.remoteConfig || {}),
        url: rule.remoteConfig?.url || targetFieldConfig.basicProps?.optionSource?.api || '',
        method: rule.remoteConfig?.method || 'GET',
        paramName: rule.remoteConfig?.paramName || sourceField || '',
        valuePath: rule.remoteConfig?.valuePath || 'data',
        labelField: rule.remoteConfig?.labelField || 'label',
        valueField: rule.remoteConfig?.valueField || 'value',
      },
      objectConfig: {
        ...(rule.objectConfig || {}),
        targetObjectCode: rule.objectConfig?.targetObjectCode || targetFieldConfig.referenceObjectCode || targetFieldConfig.basicProps?.referenceObjectCode || '',
        displayField: rule.objectConfig?.displayField || targetFieldConfig.referenceDisplayField || targetFieldConfig.basicProps?.referenceDisplayField || '',
      },
      orgConfig: {
        ...(rule.orgConfig || {}),
        paramName: rule.orgConfig?.paramName || rule.remoteConfig?.paramName || sourceField || '',
      },
      condition: { ...(rule.condition || {}) },
      emptyStrategy: ['empty', 'all', 'disabled'].includes(rule.emptyStrategy) ? rule.emptyStrategy : 'empty',
      clearOnSourceChange: rule.clearOnSourceChange !== false,
      enabled: rule.enabled !== false,
    }
  }

  function fillRuleFieldDefaults(rule) {
    const normalized = normalizeLinkageRuleDraft(rule)
    Object.assign(rule, normalized)
  }

  function syncLinkageModel(dirty = false) {
    if (mut.resettingLinkage)
      return
    const normalized = normalizeLinkageSchema(localLinkage.value)
    if (!isSameSchema(normalized, localLinkage.value))
      localLinkage.value = normalized
    if (!isSameSchema(normalized, props.linkageSchema))
      emit('update:linkageSchema', cloneSchema(normalized))
    if (dirty) {
      emit('dirtyChange', true)
    }
  }

  function resolveLinkageDataSourceType(type) {
    if (['parentDictCode', 'linkedDict'].includes(type))
      return 'dict'
    if (type === 'orgScope')
      return 'org'
    if (type === 'objectReference')
      return 'object'
    return 'remote'
  }

  function createLinkageRuleId(sourceField = '', targetField = '') {
    return `linkage_${sourceField || 'source'}_${targetField || Date.now()}`
      .replace(/\W+/g, '_')
      .replace(/_+/g, '_')
  }

  function inferLinkageType(targetField) {
    const target = fieldMap.value.get(targetField) || {}
    if (target.componentType === 'objectReference' || target.fieldType === 'REFERENCE')
      return 'objectReference'
    if (target.componentType === 'orgTreeSelect' || ['DEPT', 'ORG'].includes(target.fieldType))
      return 'orgScope'
    if (target.dictType || ['DICT', 'RADIO', 'CHECKBOX'].includes(target.fieldType))
      return 'linkedDict'
    return 'remoteParam'
  }

  function linkageTargetFieldOptions(rule = {}) {
    return sourceFieldOptions.value.filter(item => item.value !== rule.sourceField)
  }

  function linkageRuleLabel(rule = {}) {
    const target = fieldLabel(rule.targetField)
    const typeLabel = linkageTypeOptions.find(item => item.value === rule.type)?.label || rule.type
    return `${target || '目标字段'} · ${typeLabel}`
  }

  function linkageRuleSentence(rule = {}) {
    const source = fieldLabel(rule.sourceField) || '上级字段'
    const target = fieldLabel(rule.targetField) || '目标字段'
    const verbs = {
      parentDictCode: '按父级字典过滤',
      linkedDict: '按关联字典过滤',
      remoteParam: '作为远程参数过滤',
      orgScope: '作为组织范围过滤',
      objectReference: '过滤引用对象',
    }
    return `${source} → ${target}，${verbs[rule.type] || '联动'}`
  }

  function relationMatchSummary(relation = {}) {
    if (!relation.targetObjectCode)
      return '先选择目标对象'
    if (relation.sourceFieldCode && relation.targetFieldCode)
      return `已自动推断：${relation.sourceFieldCode} → ${relation.targetFieldCode}`
    return '请补齐匹配字段'
  }

  function relationMatchTagType(relation = {}) {
    return relation.sourceFieldCode && relation.targetFieldCode ? 'success' : 'warning'
  }

  function inlineConfigSummary(relation = {}) {
    const enabledCount = [
      relation.inlineCreateEnabled === true,
      relation.inlineEditEnabled === true,
      relation.showInDetail !== false,
    ].filter(Boolean).length
    return enabledCount ? `${enabledCount} 项已开启` : '未配置'
  }

  function inlineConfigTagType(relation = {}) {
    return inlineConfigSummary(relation) === '未配置' ? 'default' : 'success'
  }

  function selectorConfigSummary(relation = {}) {
    return relation.selectorEnabled ? '已启用' : '未启用'
  }

  function selectorConfigTagType(relation = {}) {
    return relation.selectorEnabled ? 'success' : 'default'
  }

  function mappingConfigSummary(relation = {}) {
    if (!relation.selectorEnabled)
      return '无映射'
    const count = selectorMappingCount(relation)
    return count ? `${count} 条映射` : '无映射'
  }

  function mappingConfigTagType(relation = {}) {
    return selectorMappingCount(relation) ? 'success' : 'default'
  }

  function selectorMappingCount(relation = {}) {
    return (relation.selectorMappings || []).filter(item => item.sourceField && item.targetField).length
  }

  function approvalConfigSummary(relation = {}) {
    return relation.approvalQuantityEnabled ? '已启用' : '未启用'
  }

  function approvalConfigTagType(relation = {}) {
    return relation.approvalQuantityEnabled ? 'success' : 'default'
  }

  function fieldLabel(fieldCode) {
    if (!fieldCode)
      return ''
    const field = fieldMap.value.get(fieldCode)
    return field ? `${field.label || field.fieldName || fieldCode}（${fieldCode}）` : fieldCode
  }

  function relationSentence(relation) {
    const source = props.objectName || props.objectCode || '当前对象'
    const target = objectNameMap.value.get(relation.targetObjectCode) || relation.targetObjectName || relation.targetObjectCode || '目标对象'
    const verbs = {
      DETAIL: '包含多个',
    }
    return `${source}${verbs[relation.relationType] || '关联'}${target}`
  }

  function sourceFieldLabel(relation) {
    return relation?.relationType === 'REFERENCE'
      ? '当前对象中的关联字段'
      : '当前对象匹配字段'
  }

  function sourceFieldPlaceholder() {
    return '通常选择：记录ID'
  }

  function targetFieldLabel(relation) {
    return relation?.relationType === 'REFERENCE'
      ? '目标对象主键字段'
      : '目标对象里指向本对象的字段'
  }

  function targetFieldPlaceholder() {
    return '选择目标对象中的所属字段'
  }

  function displayFieldLabel() {
    return '目标对象回显字段'
  }

  function targetFieldOptions(relation) {
    const fields = targetFieldsMap.value[relation.targetObjectCode] || []
    const options = fields
      .filter(field => field.field && !isInactiveField(field))
      .map(field => ({
        label: businessFieldOptionLabel(field),
        value: field.field,
      }))
    if (relation.targetFieldCode && !options.some(item => item.value === relation.targetFieldCode)) {
      options.unshift({
        label: `已配置字段：${relation.targetFieldCode}`,
        value: relation.targetFieldCode,
      })
    }
    return options
  }

  function targetDisplayFieldOptions(relation) {
    const fields = targetFieldsMap.value[relation.targetObjectCode] || []
    const options = fields
      .filter(field => field.field && !isInactiveField(field) && !field.systemField && field.field !== relation.targetFieldCode)
      .map(field => ({
        label: businessFieldOptionLabel(field),
        value: field.field,
      }))
    if (relation.displayField && !options.some(item => item.value === relation.displayField)) {
      options.unshift({
        label: `已配置字段：${relation.displayField}`,
        value: relation.displayField,
      })
    }
    return options
  }

  function selectorCandidateObjectCode(relation = {}) {
    return relation.selectorObjectCode || relation.targetObjectCode || ''
  }

  function selectorCandidateFieldOptions(relation, currentValue = '') {
    return fieldOptionsForObject(selectorCandidateObjectCode(relation), currentValue)
  }

  function selectorTargetFieldOptions(relation, currentValue = '') {
    return fieldOptionsForObject(relation.targetObjectCode, currentValue)
  }

  function selectorContextFieldOptions(param = {}) {
    if (['query', 'params'].includes(param.sourceType)) {
      return [
        { label: 'id', value: 'id' },
        { label: 'recordId', value: 'recordId' },
        { label: 'objectCode', value: 'objectCode' },
      ]
    }
    return sourceFieldOptions.value
  }

  function fieldOptionsForObject(objectCode, currentValue = '') {
    const fields = targetFieldsMap.value[objectCode] || []
    const options = fields
      .filter(field => field.field && !isInactiveField(field))
      .map(field => ({
        label: businessFieldOptionLabel(field),
        value: field.field,
      }))
    if (currentValue && !options.some(item => item.value === currentValue)) {
      options.unshift({
        label: `已配置字段：${currentValue}`,
        value: currentValue,
      })
    }
    return options
  }

  async function loadTargetFields(objectCode) {
    if (!objectCode || targetFieldsMap.value[objectCode] || targetFieldLoadingMap.value[objectCode])
      return
    const targetObject = businessObjects.value.find(item => item.objectCode === objectCode)
    if (!targetObject?.id)
      return
    targetFieldLoadingMap.value = {
      ...targetFieldLoadingMap.value,
      [objectCode]: true,
    }
    try {
      const res = await businessObjectDesigner(targetObject.id)
      const fields = res.data?.fields || res.data?.modelSchema?.fields || []
      targetFieldsMap.value = {
        ...targetFieldsMap.value,
        [objectCode]: fields.map(toPageField),
      }
    }
    catch {
      targetFieldsMap.value = {
        ...targetFieldsMap.value,
        [objectCode]: [],
      }
    }
    finally {
      targetFieldLoadingMap.value = {
        ...targetFieldLoadingMap.value,
        [objectCode]: false,
      }
    }
  }

  function buildErDiagramModels() {
    const currentCode = props.objectCode || 'current_object'
    const models = [
      {
        modelCode: currentCode,
        modelName: props.objectName || currentCode,
        tableName: currentCode,
        modelSchema: {
          object: {
            code: currentCode,
            name: props.objectName || currentCode,
          },
          tableName: currentCode,
          fields: normalizeErFields(props.fields || []),
          relations: localRelations.value
            .filter(relation => relation.targetObjectCode)
            .map(toErRelation),
        },
      },
    ]
    const targetCodes = Array.from(new Set([
      ...erCandidateObjectCodes.value,
      ...localRelations.value.map(relation => relation.targetObjectCode).filter(Boolean),
    ]))
    targetCodes.forEach((targetCode) => {
      const targetObject = businessObjects.value.find(item => item.objectCode === targetCode) || {}
      models.push({
        modelCode: targetCode,
        modelName: targetObject.objectName || targetCode,
        tableName: targetObject.tableName || targetCode,
        modelSchema: {
          object: {
            code: targetCode,
            name: targetObject.objectName || targetCode,
          },
          tableName: targetObject.tableName || targetCode,
          fields: normalizeErFields(targetFieldsMap.value[targetCode] || []),
          relations: [],
        },
      })
    })
    return models
  }

  function normalizeErFields(fields = []) {
    const rows = (fields || [])
      .map(toPageField)
      .filter(field => field.field && !isInactiveField(field))
      .map(field => ({
        field: field.field,
        columnName: field.columnName || field.field,
        label: field.label || field.fieldName || field.field,
        // 只认存储层 dataType；禁止回退到 fieldType/businessFieldType（TEXT/string 等），
        // 否则保存页面设计时 LowcodeSchemaValidator 会报「不支持的数据类型」。
        dataType: normalizeErStorageDataType(field.dataType || field.dbType || field.columnType),
        primaryKey: Boolean(field.primaryKey) || field.field === 'id' || field.columnName === 'id',
        systemField: Boolean(field.systemField),
      }))
    if (!rows.some(field => field.field === 'id' || field.columnName === 'id')) {
      rows.unshift({
        field: 'id',
        columnName: 'id',
        label: 'ID',
        dataType: 'bigint',
        primaryKey: true,
        systemField: true,
      })
    }
    return rows
  }

  /** 与后端 LowcodeSchemaValidator.normalizeStorageDataType 对齐 */
  function normalizeErStorageDataType(raw) {
    let text = String(raw || 'varchar').trim().toLowerCase()
    const paren = text.indexOf('(')
    if (paren > 0)
      text = text.slice(0, paren).trim()
    const aliases = {
      string: 'varchar',
      str: 'varchar',
      'character varying': 'varchar',
      nvarchar: 'varchar',
      varchar2: 'varchar',
      clob: 'text',
      ntext: 'text',
      integer: 'int',
      int32: 'int',
      mediumint: 'int',
      smallint: 'int',
      long: 'bigint',
      int64: 'bigint',
      number: 'decimal',
      numeric: 'decimal',
      double: 'decimal',
      float: 'decimal',
      money: 'decimal',
      bigdecimal: 'decimal',
      bool: 'tinyint',
      boolean: 'tinyint',
      timestamp: 'datetime',
      textarea: 'text',
      multi_line: 'text',
      richtext: 'text',
    }
    const allowed = new Set(['varchar', 'char', 'text', 'longtext', 'int', 'bigint', 'decimal', 'date', 'datetime', 'time', 'tinyint'])
    const mapped = aliases[text] || text
    return allowed.has(mapped) ? mapped : 'varchar'
  }

  function toErRelation(relation = {}) {
    return {
      relationKey: relation.clientKey || relation.id || '',
      relationType: normalizeErRelationType(relation.relationType),
      targetObjectCode: relation.targetObjectCode,
      sourceField: relation.sourceFieldCode || 'id',
      targetField: relation.targetFieldCode || 'id',
    }
  }

  function normalizeErRelationType(value) {
    const type = normalizeDesignerRelationType(value)
    if (type === 'DETAIL')
      return 'ONE_TO_MANY'
    return type
  }

  function firstTargetField(objectCode, relationType = 'DETAIL', currentValue = '') {
    const fields = targetFieldsMap.value[objectCode] || []
    if (relationType === 'REFERENCE') {
      if (currentValue && currentValue !== 'id' && fields.some(field => field.field === currentValue && !isInactiveField(field)))
        return currentValue
      return fields.find(field => field.field === 'id' && !isInactiveField(field))?.field
        || fields.find(field => field.primaryKey && !isInactiveField(field))?.field
        || fields.find(field => !isInactiveField(field))?.field
        || ''
    }
    const sourceObject = lowerFirst(props.objectCode || '')
    const candidates = [
      `${sourceObject}Id`,
      `${sourceObject}Code`,
      props.objectCode,
      'parentId',
    ].filter(Boolean)
    const activeFields = fields.filter(field => !isInactiveField(field))
    const matched = activeFields.find(field => candidates.includes(field.field))
      || activeFields.find((field) => {
        const label = field.label || ''
        const sourceName = props.objectName || props.objectCode || ''
        return sourceName && label.includes(sourceName)
      })
    if (matched)
      return matched.field
    if (currentValue && currentValue !== 'id' && fields.some(field => field.field === currentValue && !isInactiveField(field)))
      return currentValue
    const fallback = activeFields.find(field => field.field !== 'id' && !field.systemField)
    return fallback?.field || ''
  }

  function firstDisplayField(objectCode, relationField = '') {
    const targetObject = businessObjects.value.find(item => item.objectCode === objectCode)
    const fields = targetFieldsMap.value[objectCode] || []
    const activeFields = fields.filter(field => field.field && !isInactiveField(field) && !field.systemField && field.field !== relationField)
    const configured = targetObject?.displayField
    const matched = activeFields.find(field => configured && field.field === configured)
      || activeFields.find((field) => {
        const fieldName = String(field.field || '').toLowerCase()
        const label = String(field.label || field.fieldName || '')
        return fieldName.includes('name') || label.includes('名称') || label.includes('姓名')
      })
      || activeFields[0]
    return matched?.field || ''
  }

  async function applySelectorDefaults(relation, force = true) {
    const candidateObjectCode = selectorCandidateObjectCode(relation)
    await loadTargetFields(candidateObjectCode)
    await loadTargetFields(relation.targetObjectCode)
    const candidateFields = selectorActiveFields(candidateObjectCode)
    const targetFields = selectorActiveFields(relation.targetObjectCode)
    if (force || !(relation.selectorDisplayFields || []).length)
      relation.selectorDisplayFields = inferSelectorDisplayFields(candidateFields)
    if (force || !(relation.selectorKeywordFields || []).length)
      relation.selectorKeywordFields = inferSelectorKeywordFields(candidateFields, relation.selectorDisplayFields)
    if (force || !(relation.selectorMappings || []).some(item => item.sourceField && item.targetField))
      relation.selectorMappings = inferSelectorMappings(candidateFields, targetFields)
    if (!relation.selectorButtonText)
      relation.selectorButtonText = '选择记录'
    if (!relation.selectorTitle) {
      const objectName = objectNameMap.value.get(candidateObjectCode) || candidateObjectCode
      relation.selectorTitle = objectName ? `选择${objectName}` : ''
    }
    markDirty()
  }

  function selectorActiveFields(objectCode) {
    return (targetFieldsMap.value[objectCode] || [])
      .filter(field => field.field && !isInactiveField(field) && !field.systemField)
  }

  function inferSelectorDisplayFields(fields = []) {
    const preferred = ['code', 'no', 'number', 'name', 'title']
    const matched = fields.filter(field => preferred.some(key => normalizedFieldToken(field).includes(key)))
    return (matched.length ? matched : fields).slice(0, 4).map(field => field.field)
  }

  function inferSelectorKeywordFields(fields = [], displayFields = []) {
    const displaySet = new Set(displayFields || [])
    const preferred = fields.filter((field) => {
      const token = normalizedFieldToken(field)
      return token.includes('code') || token.includes('no') || token.includes('number')
        || token.includes('name') || token.includes('title') || displaySet.has(field.field)
    })
    return (preferred.length ? preferred : fields).slice(0, 3).map(field => field.field)
  }

  function inferSelectorMappings(candidateFields = [], targetFields = []) {
    const candidateByField = new Map(candidateFields.map(field => [field.field, field]))
    const candidateByToken = new Map(candidateFields.map(field => [normalizedFieldToken(field), field]))
    const rows = []
    const usedTargets = new Set()
    targetFields.forEach((target) => {
      const source = candidateByField.get(target.field)
        || candidateByToken.get(normalizedFieldToken(target))
        || findSemanticSourceField(candidateFields, target)
      if (source && !usedTargets.has(target.field)) {
        rows.push(createSelectorMappingRow({
          sourceField: source.field,
          targetField: target.field,
        }))
        usedTargets.add(target.field)
      }
    })
    return rows.slice(0, 8)
  }

  function findSemanticSourceField(candidateFields = [], target = {}) {
    const targetToken = normalizedFieldToken(target)
    const semanticGroups = [
      ['id', 'recordid'],
      ['code', 'no', 'number', 'sn'],
      ['name', 'title', 'label'],
      ['price', 'amount', 'cost', 'fee'],
      ['unit', 'uom'],
      ['spec', 'model', 'type'],
    ]
    const group = semanticGroups.find(items => items.some(item => targetToken.includes(item)))
    if (!group)
      return null
    return candidateFields.find(field => group.some(item => normalizedFieldToken(field).includes(item))) || null
  }

  function normalizedFieldToken(field = {}) {
    return `${field.field || ''}_${field.label || ''}_${field.fieldName || ''}`
      .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
      .toLowerCase()
  }

  function isInactiveField(field = {}) {
    const status = String(field.fieldStatus || '').toUpperCase()
    return status === 'DISABLED' || status === 'HIDDEN'
  }

  function businessFieldLabel(field = {}) {
    const fieldName = field.field || ''
    if (fieldName === 'id')
      return '记录ID'
    if (fieldName === 'createBy')
      return '创建人'
    if (fieldName === 'createTime')
      return '创建时间'
    if (fieldName === 'updateBy')
      return '修改人'
    if (fieldName === 'updateTime')
      return '修改时间'
    if (fieldName === 'createDept')
      return '创建部门'
    return field.label || field.fieldName || fieldName
  }

  function businessFieldOptionLabel(field = {}) {
    const code = field.field || field.fieldCode || ''
    const label = businessFieldLabel(field)
    return label && code && label !== code ? `${label}（${code}）` : label || code
  }

  function relationLabel(relation) {
    return relationSentence(relation)
  }

  function canInlineEdit(relation = {}) {
    return normalizeDesignerRelationType(relation.relationType) === 'DETAIL'
  }

  function normalizeDesignerRelationType() {
    return 'DETAIL'
  }

  function firstSourceField(relationType = 'DETAIL', targetObjectCode = '', currentValue = '') {
    if (relationType === 'REFERENCE') {
      const matched = findReferenceSourceField(targetObjectCode)
      if (matched)
        return matched.value
      if (currentValue && sourceFieldOptions.value.some(item => item.value === currentValue))
        return currentValue
    }
    else {
      const idField = sourceFieldOptions.value.find(item => item.value === 'id')?.value
      if (idField)
        return idField
      if (currentValue && sourceFieldOptions.value.some(item => item.value === currentValue))
        return currentValue
    }
    return sourceFieldOptions.value.find(item => item.value === 'id')?.value || sourceFieldOptions.value[0]?.value || ''
  }

  function findReferenceSourceField(targetObjectCode = '') {
    const targetObject = businessObjects.value.find(item => item.objectCode === targetObjectCode)
    const targetCode = lowerFirst(targetObjectCode || '')
    const targetName = targetObject?.objectName || ''
    const candidates = [
      `${targetCode}Id`,
      `${targetCode}Code`,
      targetObjectCode,
    ].filter(Boolean)
    return sourceFieldOptions.value.find(item => candidates.includes(item.value))
      || sourceFieldOptions.value.find((item) => {
        const label = item.label || ''
        return targetName && label.includes(targetName)
      })
  }

  function createClientKey() {
    return `relation_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  }

  function lowerFirst(value) {
    if (!value)
      return ''
    const normalized = String(value)
      .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
      .replace(/\W+/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_|_$/g, '')
      .toLowerCase()
    return normalized.replace(/_([a-z0-9])/g, (_, char) => char.toUpperCase())
  }

  function lowerSnake(value) {
    return String(value || '')
      .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
      .replace(/\W+/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_|_$/g, '')
      .toLowerCase()
  }

  function toPageField(field) {
    return {
      ...field,
      field: field.field || field.fieldCode,
      label: field.label || field.fieldName || field.fieldCode,
      fieldStatus: field.fieldStatus,
      basicProps: { ...(field.basicProps || {}) },
      advancedProps: { ...(field.advancedProps || {}) },
    }
  }

  function toFieldPayload(field = {}) {
    return {
      fieldName: field.fieldName || field.label,
      fieldCode: field.fieldCode || field.field,
      columnName: field.columnName,
      fieldType: field.fieldType || field.businessFieldType,
      dataType: field.dataType,
      length: field.length,
      precision: field.precision,
      required: field.required,
      defaultValue: field.defaultValue,
      searchable: field.searchable,
      listVisible: field.listVisible,
      formVisible: field.formVisible,
      importable: field.importable,
      exportable: field.exportable,
      componentType: field.componentType,
      queryType: field.queryType,
      dictType: field.dictType,
      sensitiveType: field.sensitiveType,
      encryptAlgorithm: field.encryptAlgorithm,
      sortable: field.sortable,
      systemField: field.systemField,
      readonly: field.readonly,
      fieldStatus: field.fieldStatus,
      referenceObjectCode: field.referenceObjectCode,
      referenceDisplayField: field.referenceDisplayField,
      placeholder: field.basicProps?.placeholder || field.placeholder || '',
      remark: field.remark,
      sortOrder: field.sortOrder,
      fieldBinding: { ...(field.fieldBinding || {}) },
      basicProps: { ...(field.basicProps || {}) },
      advancedProps: { ...(field.advancedProps || {}) },
    }
  }

  function markDirty() {
    markRelationDirty()
  }

  function markLinkageDirty() {
    syncLinkageModel(true)
  }

  function markRelationDirty() {
    relationDirty.value = true
    emit('dirtyChange', true)
  }

  expose?.({
    saveRelations,
    loadRelations,
  })
  __impl.findForeachQuantityStep = findForeachQuantityStep
  __impl.relationQuantityActionCode = relationQuantityActionCode
  __impl.relationCollectionKey = relationCollectionKey
  __impl.wrapExpression = wrapExpression
  __impl.unwrapFieldPath = unwrapFieldPath
  __impl.inferSourceFieldByTokens = inferSourceFieldByTokens
  __impl.inferTargetFieldByTokens = inferTargetFieldByTokens
  __impl.optionMatchesToken = optionMatchesToken
  __impl.normalizeLinkageRuleDraft = normalizeLinkageRuleDraft
  __impl.fillRuleFieldDefaults = fillRuleFieldDefaults
  __impl.syncLinkageModel = syncLinkageModel
  __impl.resolveLinkageDataSourceType = resolveLinkageDataSourceType
  __impl.createLinkageRuleId = createLinkageRuleId
  __impl.inferLinkageType = inferLinkageType
  __impl.linkageTargetFieldOptions = linkageTargetFieldOptions
  __impl.linkageRuleLabel = linkageRuleLabel
  __impl.linkageRuleSentence = linkageRuleSentence
  __impl.relationMatchSummary = relationMatchSummary
  __impl.relationMatchTagType = relationMatchTagType
  __impl.inlineConfigSummary = inlineConfigSummary
  __impl.inlineConfigTagType = inlineConfigTagType
  __impl.selectorConfigSummary = selectorConfigSummary
  __impl.selectorConfigTagType = selectorConfigTagType
  __impl.mappingConfigSummary = mappingConfigSummary
  __impl.mappingConfigTagType = mappingConfigTagType
  __impl.selectorMappingCount = selectorMappingCount
  __impl.approvalConfigSummary = approvalConfigSummary
  __impl.approvalConfigTagType = approvalConfigTagType
  __impl.fieldLabel = fieldLabel
  __impl.relationSentence = relationSentence
  __impl.sourceFieldLabel = sourceFieldLabel
  __impl.sourceFieldPlaceholder = sourceFieldPlaceholder
  __impl.targetFieldLabel = targetFieldLabel
  __impl.targetFieldPlaceholder = targetFieldPlaceholder
  __impl.displayFieldLabel = displayFieldLabel
  __impl.targetFieldOptions = targetFieldOptions
  __impl.targetDisplayFieldOptions = targetDisplayFieldOptions
  __impl.selectorCandidateObjectCode = selectorCandidateObjectCode
  __impl.selectorCandidateFieldOptions = selectorCandidateFieldOptions
  __impl.selectorTargetFieldOptions = selectorTargetFieldOptions
  __impl.selectorContextFieldOptions = selectorContextFieldOptions
  __impl.fieldOptionsForObject = fieldOptionsForObject
  __impl.loadTargetFields = loadTargetFields
  __impl.buildErDiagramModels = buildErDiagramModels
  __impl.normalizeErFields = normalizeErFields
  __impl.normalizeErStorageDataType = normalizeErStorageDataType
  __impl.toErRelation = toErRelation
  __impl.normalizeErRelationType = normalizeErRelationType
  __impl.firstTargetField = firstTargetField
  __impl.firstDisplayField = firstDisplayField
  __impl.applySelectorDefaults = applySelectorDefaults
  __impl.selectorActiveFields = selectorActiveFields
  __impl.inferSelectorDisplayFields = inferSelectorDisplayFields
  __impl.inferSelectorKeywordFields = inferSelectorKeywordFields
  __impl.inferSelectorMappings = inferSelectorMappings
  __impl.findSemanticSourceField = findSemanticSourceField
  __impl.normalizedFieldToken = normalizedFieldToken
  __impl.isInactiveField = isInactiveField
  __impl.businessFieldLabel = businessFieldLabel
  __impl.businessFieldOptionLabel = businessFieldOptionLabel
  __impl.relationLabel = relationLabel
  __impl.canInlineEdit = canInlineEdit
  __impl.normalizeDesignerRelationType = normalizeDesignerRelationType
  __impl.firstSourceField = firstSourceField
  __impl.findReferenceSourceField = findReferenceSourceField
  __impl.createClientKey = createClientKey
  __impl.lowerFirst = lowerFirst
  __impl.lowerSnake = lowerSnake
  __impl.toPageField = toPageField
  __impl.toFieldPayload = toFieldPayload
  __impl.markDirty = markDirty
  __impl.markLinkageDirty = markLinkageDirty
  __impl.markRelationDirty = markRelationDirty

  return {
    ...deps,
  }
}
