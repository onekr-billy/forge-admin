/** dataset.vue setup part 2. */
import { NInput, NSelect, NTag } from 'naive-ui'
import { computed, h, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDataConnectionFields, getDataConnectionList, getDataConnectionTables } from '@/api/data/connection'
import {
  deleteDataDataset,
  getDashboardDatasetImpact,
  getDataDatasetById,
  getDataDatasetCategoryTree,
  offlineDataDataset,
  publishDataDataset,
  saveDataDatasetFields,
  syncDataDatasetFields,
} from '@/api/data/dataset'
import { getDataDimensionList } from '@/api/data/dimension'
import { AiCrudPage } from '@/components/ai-form'
import DatasetParamSchemaEditor from '@/components/data/DatasetParamSchemaEditor.vue'
import DictTag from '@/components/DictTag.vue'
import SqlEditor from '@/components/SqlEditor.vue'
import { getDictData, useDict } from '@/composables/useDict'
import { request } from '@/utils'
import { normalizeDictOptionValue, toNumberDictOptions } from '@/utils/dict-options'

export function applyDatasetPagePart2(deps = {}) {
  const {
    __impl, mut, applySearch, beforeRenderDetail, beforeRenderForm, buildCategorySelectOptions, buildCategoryTreeNodes, buildSearchParams,
    canGoToNextStep, confirmSyncDatasetFields, ensureRowScopeDictOptions, extractSqlParamNames, filterCategoryTree, findCategoryById, formatDatasetDate, getAccessModeLabel,
    getCategoryName, getConnectionName, getDatasetCreatorLabel, getDatasetParamConstraint, getDatasetParamGuide, getDatasetParamReadiness, getDatasetSourceGuide, getDatasetSourceSubject,
    getDatasetTagLabels, getDatasetTypeLabel, getDatasetUpdaterLabel, getDatasetVersionLabel, getEnableStatusLabel, getParamPreviewDescription, getParamPreviewRows, getPublishStatusLabel,
    getSqlParamCount, getStepNodeInlineStyle, goToCategoryManage, goToNextStep, goToPrevStep, handleAddDataset, handleCategoryTreeSelect, handleConnectionChange,
    handleDatasetModalClose, handleDatasetTypeChange, handleDelete, handleEdit, handleOfflineDataset, handlePublishDataset, handleResetFilters, handleSaveFieldConfig,
    handleStepReset, handleSyncCurrentFields, handleSyncFields, handleTableNameChange, handleViewDataset, loadCategoryTree, loadConnectionOptions, loadDatasetImpact,
    loadDimensionOptions, prepareDatasetFormData, renderDatasetImpactContent, resetRowScopeTableFields, resetTableOptions, scrollToStepSection, selectAllCategories, selectUncategorized,
    setEditorStep, syncDatasetFields, toIdString, validateFieldRows, router, crudRef, connectionOptions, categoryTree,
    categoryKeyword, tableOptions, dimensionOptions, tableLoading, rowScopeTableFieldLoading, loadedTableConnectionId, loadingTableConnectionId, rowScopeTableFieldKey,
    rowScopeTableFieldOptions, fieldModalVisible, fieldLoading, fieldSaving, fieldModalTitle, fieldRows, currentFieldDataset, sqlPreviewVisible,
    sqlPreviewLoading, sqlPreviewColumns, sqlPreviewRows, sqlPreviewScrollX, roleOptions, userOptions, orgTreeOptions, permissionOptionsLoaded,
    permissionOptionsLoading, activeCategoryScope, selectedCategoryId, currentFormMode, currentEditingDataset, currentStep, stepDefinitions, totalSteps,
    queryForm, datasetTypeOptions, statusOptions, resultEncodingOptions, publishStatusOptions, datasetImpactLimit, datasetImpactVisibleLimit, accessModeOptions,
    aclSubjectTypeOptions, accessLevelOptions, rowScopeAttributeOptions, rowScopeLogicOptions, dataTypeOptions, fieldRoleOptions, sensitiveLevelOptions, maskRuleOptions,
    dateFormatOptions, dataUnitOptions, supportedParamOperators, isFormReadOnly, fieldConfigReadonly, selectedCategoryNode, selectedTreeKeys, currentStepMeta,
    formModeLabel, stepNavigationNote, stepProgressPercent, stepShellStyle, stepProgressWrapStyle, stepProgressBaseLineStyle, stepProgressActiveLineStyle, stepNavigationWrapperStyle,
    stepNavigationActionsStyle, stepNavigationMetaStyle, activeCategoryScopeLabel, categoryTreeNodes, categoryTreeSelectOptions, fieldConfigStats, tableColumns, fieldColumns,
    fieldTableScrollX, editSchema,
  } = deps
  async function loadTableOptions(connectionId) {
    if (!connectionId) {
      resetTableOptions()
      return
    }
    if (loadedTableConnectionId.value === connectionId && tableOptions.value.length > 0) {
      return
    }
    if (tableLoading.value && loadingTableConnectionId.value === connectionId) {
      return
    }

    tableLoading.value = true
    loadingTableConnectionId.value = connectionId
    try {
      const res = await getDataConnectionTables(connectionId)
      if (res.code === 200 && Array.isArray(res.data)) {
        tableOptions.value = res.data.map(table => ({
          label: table.tableComment ? `${table.tableName}（${table.tableComment}）` : table.tableName,
          value: table.tableName,
        }))
        loadedTableConnectionId.value = connectionId
      }
      else {
        resetTableOptions()
      }
    }
    catch (error) {
      console.error('Failed to load tables', error)
      resetTableOptions()
      window.$message?.error('加载数据表失败')
    }
    finally {
      tableLoading.value = false
      loadingTableConnectionId.value = null
    }
  }

  async function loadRowScopeTableFields(formData, options = {}) {
    const { force = false } = options
    if (!formData?.connectionId || formData.datasetType !== 'TABLE' || !formData.tableName) {
      resetRowScopeTableFields()
      return
    }

    const nextKey = `${formData.connectionId}:${formData.tableName}`
    if (!force && rowScopeTableFieldKey.value === nextKey && rowScopeTableFieldOptions.value.length > 0) {
      return
    }

    rowScopeTableFieldLoading.value = true
    try {
      const res = await getDataConnectionFields(formData.connectionId, formData.tableName)
      if (res.code === 200 && Array.isArray(res.data)) {
        rowScopeTableFieldOptions.value = res.data.map(field => ({
          label: field.columnComment ? `${field.columnName}（${field.columnComment}）` : field.columnName,
          value: field.columnName,
        }))
        rowScopeTableFieldKey.value = nextKey
      }
      else {
        resetRowScopeTableFields()
      }
    }
    catch (error) {
      console.error('Failed to load row scope table fields', error)
      resetRowScopeTableFields()
      window.$message?.error('加载数据表字段失败')
    }
    finally {
      rowScopeTableFieldLoading.value = false
    }
  }

  async function loadPermissionOptions() {
    if (permissionOptionsLoaded.value) {
      return
    }
    if (mut.permissionOptionsRequest) {
      return mut.permissionOptionsRequest
    }
    permissionOptionsLoading.value = true
    mut.permissionOptionsRequest = Promise.all([
      loadRoleOptions(),
      loadUserOptions(),
      loadOrgOptions(),
    ])
      .then(() => {
        permissionOptionsLoaded.value = true
      })
      .finally(() => {
        permissionOptionsLoading.value = false
        mut.permissionOptionsRequest = null
      })
    return mut.permissionOptionsRequest
  }

  async function loadRoleOptions() {
    try {
      const res = await request.get('/system/role/page', {
        params: { pageNum: 1, pageSize: 1000 },
      })
      if (res.code === 200) {
        const rows = res.data?.records || res.data?.list || []
        roleOptions.value = rows.map(role => ({
          label: role.roleKey ? `${role.roleName}（${role.roleKey}）` : role.roleName,
          value: toIdString(role.id),
        }))
      }
    }
    catch (error) {
      console.error('Failed to load roles for dataset ACL', error)
    }
  }

  async function loadUserOptions() {
    try {
      const res = await request.get('/system/user/page', {
        params: { pageNum: 1, pageSize: 1000 },
      })
      if (res.code === 200) {
        const rows = res.data?.records || res.data?.list || []
        userOptions.value = rows.map(user => ({
          label: user.realName ? `${user.realName}（${user.username}）` : user.username,
          value: toIdString(user.id),
        }))
      }
    }
    catch (error) {
      console.error('Failed to load users for dataset ACL', error)
    }
  }

  async function loadOrgOptions() {
    try {
      const res = await request.get('/system/org/tree')
      if (res.code === 200) {
        orgTreeOptions.value = transformOrgTreeOptions(res.data || [])
      }
    }
    catch (error) {
      console.error('Failed to load org tree for dataset ACL', error)
    }
  }

  function transformOrgTreeOptions(tree) {
    return (tree || []).map(item => ({
      label: item.orgName,
      value: toIdString(item.id),
      key: toIdString(item.id),
      children: item.children?.length ? transformOrgTreeOptions(item.children) : undefined,
    }))
  }

  function beforeSubmit(formData) {
    if (isFormReadOnly.value) {
      return false
    }

    delete formData.datasetOverview
    delete formData.datasetEditor
    delete formData.stepIndicator
    delete formData.stepNavigation
    delete formData.__sectionBasic
    delete formData.__sectionSource
    delete formData.__sectionParam
    delete formData.__sectionSetting
    delete formData.__sectionAccessPermission
    delete formData.__sectionRowScope
    delete formData.sqlPreviewAction
    delete formData.sourceGuide
    delete formData.paramGuide
    delete formData.settingGuide
    delete formData.accessPermissionConfig
    delete formData.rowScopeConfig
    delete formData.__resultEncoding
    delete formData.__allowExport

    if (!formData.connectionId) {
      window.$message?.error('请选择数据连接')
      return false
    }

    if (formData.datasetType === 'TABLE') {
      if (!formData.tableName) {
        window.$message?.error('请选择数据表')
        return false
      }
      formData.sqlText = null
    }
    else if (formData.datasetType === 'SQL') {
      if (!formData.sqlText) {
        window.$message?.error('请输入查询SQL')
        return false
      }
      formData.tableName = null
    }

    const normalizedSchema = normalizeParamSchema(formData.paramSchemaJson, formData.datasetType)
    if (normalizedSchema === null) {
      return false
    }

    const normalizedAclItems = normalizeSubmitAclItems(formData)
    if (normalizedAclItems === null) {
      return false
    }

    const normalizedRowScope = normalizeSubmitRowScope(formData)
    if (normalizedRowScope === null) {
      return false
    }

    formData.paramSchemaJson = normalizedSchema.length > 0
      ? JSON.stringify(normalizedSchema, null, 2)
      : null
    formData.aclItems = normalizedAclItems
    formData.rowScope = normalizedRowScope

    return formData
  }

  function createDefaultAclItem() {
    return {
      __key: `${Date.now()}-${Math.random()}`,
      subjectType: 'ROLE',
      subjectId: null,
      accessLevel: 'QUERY',
    }
  }

  function createDefaultRowScope() {
    return {
      enabled: 0,
      scopeMode: 'SYSTEM_DATA_SCOPE',
      tenantColumn: null,
      orgColumn: null,
      userColumn: null,
      regionColumn: null,
      regionStrategy: 'SELF_AND_DESCENDANTS',
      ruleItems: [],
      remark: null,
    }
  }

  function normalizeAclItems(items) {
    if (!Array.isArray(items)) {
      return []
    }
    return items.map(item => ({
      __key: `${item.subjectType || 'ACL'}-${item.subjectId || 'NEW'}-${item.accessLevel || 'QUERY'}-${Math.random()}`,
      id: item.id,
      subjectType: normalizeAclSubjectType(item.subjectType),
      subjectId: toIdString(item.subjectId),
      accessLevel: normalizeAccessLevel(item.accessLevel),
    }))
  }

  function normalizeRowScope(rowScope) {
    const normalized = {
      ...createDefaultRowScope(),
      ...(rowScope || {}),
      enabled: rowScope?.enabled === 1 ? 1 : 0,
      regionStrategy: rowScope?.regionStrategy || 'SELF_AND_DESCENDANTS',
    }
    normalized.ruleItems = Array.isArray(rowScope?.ruleItems)
      ? rowScope.ruleItems.map(normalizeRowScopeRule).filter(Boolean)
      : buildRowScopeRuleItems(normalized)
    return normalized
  }

  function createRowScopeRule(attribute = null, field = null, logic = 'AND') {
    return {
      __key: `${Date.now()}-${Math.random()}`,
      attribute,
      field: field ?? null,
      logic: normalizeRowScopeLogic(logic),
    }
  }

  function buildRowScopeRuleItems(rowScope) {
    return rowScopeAttributeOptions.value
      .filter(option => rowScope?.[option.value])
      .map((option, index) => createRowScopeRule(option.value, rowScope[option.value], index === 0 ? 'AND' : 'AND'))
  }

  function normalizeRowScopeRule(rule) {
    if (!rule || typeof rule !== 'object') {
      return null
    }
    const attribute = normalizeDictOptionValue(rowScopeAttributeOptions.value, trimToNull(rule.attribute), null)
    return {
      __key: rule.__key || `${Date.now()}-${Math.random()}`,
      attribute,
      field: trimToNull(rule.field),
      logic: normalizeRowScopeLogic(rule.logic),
    }
  }

  function normalizeRowScopeLogic(logic) {
    return normalizeDictOptionValue(rowScopeLogicOptions.value, trimToNull(logic), 'AND')
  }

  function normalizeAclSubjectType(subjectType) {
    return ['USER', 'ROLE', 'ORG'].includes(subjectType) ? subjectType : 'ROLE'
  }

  function normalizeAccessLevel(accessLevel) {
    return ['VIEW', 'QUERY', 'MANAGE'].includes(accessLevel) ? accessLevel : 'QUERY'
  }

  function syncSlotForm(updateValue) {
    if (typeof updateValue === 'function') {
      updateValue(null)
    }
  }

  function updateDatasetFormField(formData, field, value, updateValue) {
    formData[field] = value
    syncSlotForm(updateValue)
  }

  async function handleAccessModeChange(value, formData, updateValue) {
    formData.accessMode = value === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC'
    syncSlotForm(updateValue)
    if (formData.accessMode === 'PRIVATE') {
      await loadPermissionOptions()
      syncSlotForm(updateValue)
    }
  }

  function getAclCount(formData) {
    return Array.isArray(formData?.aclItems) ? formData.aclItems.length : 0
  }

  async function addAclItem(formData, updateValue) {
    await loadPermissionOptions()
    if (!Array.isArray(formData.aclItems)) {
      formData.aclItems = []
    }
    formData.aclItems.push(createDefaultAclItem())
    if (formData.accessMode !== 'PRIVATE') {
      formData.accessMode = 'PRIVATE'
    }
    syncSlotForm(updateValue)
  }

  function removeAclItem(formData, index, updateValue) {
    if (!Array.isArray(formData.aclItems)) {
      return
    }
    formData.aclItems.splice(index, 1)
    syncSlotForm(updateValue)
  }

  function handleAclSubjectTypeChange(item, value, updateValue) {
    item.subjectType = normalizeAclSubjectType(value)
    item.subjectId = null
    syncSlotForm(updateValue)
  }

  function handleAclSubjectIdChange(item, value, updateValue) {
    item.subjectId = toIdString(value)
    syncSlotForm(updateValue)
  }

  function handleAclAccessLevelChange(item, value, updateValue) {
    item.accessLevel = normalizeAccessLevel(value)
    syncSlotForm(updateValue)
  }

  function getAclItemLabel(item) {
    const subjectTypeLabel = aclSubjectTypeOptions.value.find(option => option.value === item?.subjectType)?.label || '授权主体'
    const accessLevelLabel = accessLevelOptions.value.find(option => option.value === item?.accessLevel)?.label || '查询'
    if (!item?.subjectId) {
      return `${subjectTypeLabel} · 待选择 · ${accessLevelLabel}`
    }
    if (item.subjectType === 'ORG') {
      const option = findTreeOption(orgTreeOptions.value, toIdString(item.subjectId))
      return `${option?.label || `组织 #${item.subjectId}`} · ${accessLevelLabel}`
    }
    const options = item.subjectType === 'USER' ? userOptions.value : roleOptions.value
    const option = options.find(row => toIdString(row.value) === toIdString(item.subjectId))
    return `${option?.label || getAclSubjectFallbackLabel(item)} · ${accessLevelLabel}`
  }

  function findTreeOption(options, value) {
    const normalizedValue = toIdString(value)
    for (const option of options || []) {
      if (toIdString(option.value) === normalizedValue) {
        return option
      }
      const child = findTreeOption(option.children || [], normalizedValue)
      if (child) {
        return child
      }
    }
    return null
  }

  function getAclSubjectOptions(item) {
    const baseOptions = item?.subjectType === 'USER' ? userOptions.value : roleOptions.value
    return appendMissingFlatOption(baseOptions, item?.subjectId, getAclSubjectFallbackLabel(item))
  }

  function getAclOrgOptions(item) {
    return appendMissingTreeOption(orgTreeOptions.value, item?.subjectId, getAclSubjectFallbackLabel(item))
  }

  function getAclSubjectFallbackLabel(item) {
    if (!item?.subjectId) {
      return ''
    }
    if (item.subjectType === 'USER') {
      return `用户 #${item.subjectId}`
    }
    if (item.subjectType === 'ORG') {
      return `组织 #${item.subjectId}`
    }
    return `角色 #${item.subjectId}`
  }

  function appendMissingFlatOption(options, value, label) {
    const normalizedValue = toIdString(value)
    if (!normalizedValue || options.some(item => toIdString(item.value) === normalizedValue)) {
      return options
    }
    return [...options, { label, value: normalizedValue }]
  }

  function appendMissingTreeOption(options, value, label) {
    const normalizedValue = toIdString(value)
    if (!normalizedValue || containsTreeValue(options, normalizedValue)) {
      return options
    }
    return [...options, { label, value: normalizedValue, key: normalizedValue }]
  }

  function containsTreeValue(options, value) {
    const normalizedValue = toIdString(value)
    for (const option of options || []) {
      if (toIdString(option.value) === normalizedValue) {
        return true
      }
      if (containsTreeValue(option.children || [], normalizedValue)) {
        return true
      }
    }
    return false
  }

  function getRowScopeFieldOptions(formData) {
    const fields = Array.isArray(formData?.fields) ? formData.fields : []
    const datasetFieldOptions = fields
      .map(field => ({
        label: field.fieldLabel && field.fieldLabel !== field.fieldName
          ? `${field.fieldName}（${field.fieldLabel}）`
          : field.fieldName,
        value: field.sourceColumn || field.fieldName,
      }))
      .filter(item => item.value)
    if (datasetFieldOptions.length > 0) {
      return datasetFieldOptions
    }
    if (formData?.datasetType === 'TABLE') {
      return rowScopeTableFieldOptions.value
    }
    return []
  }

  function getRowScopeFieldSourceLabel(formData) {
    const fields = Array.isArray(formData?.fields) ? formData.fields : []
    if (fields.length > 0) {
      return `数据集字段 ${fields.length} 个`
    }
    if (formData?.datasetType === 'TABLE' && rowScopeTableFieldOptions.value.length > 0) {
      return `来源表字段 ${rowScopeTableFieldOptions.value.length} 个`
    }
    return '暂无字段'
  }

  function clearRowScopeColumns(formData) {
    if (!formData?.rowScope) {
      return
    }
    formData.rowScope.tenantColumn = null
    formData.rowScope.orgColumn = null
    formData.rowScope.userColumn = null
    formData.rowScope.regionColumn = null
    formData.rowScope.ruleItems = []
  }

  function ensureRowScope(formData) {
    if (!formData.rowScope) {
      formData.rowScope = createDefaultRowScope()
    }
    if (!Array.isArray(formData.rowScope.ruleItems)) {
      formData.rowScope.ruleItems = buildRowScopeRuleItems(formData.rowScope)
    }
    return formData.rowScope
  }

  function getRowScopeRules(formData) {
    return ensureRowScope(formData).ruleItems
  }

  function isRowScopeEnabled(formData) {
    return formData?.rowScope?.enabled === 1
  }

  function getRowScopeRemark(formData) {
    return formData?.rowScope?.remark || null
  }

  function handleRowScopeEnabledChange(checked, formData, updateValue) {
    const rowScope = ensureRowScope(formData)
    rowScope.enabled = checked ? 1 : 0
    if (rowScope.enabled === 1 && rowScope.ruleItems.length === 0) {
      addRowScopeRule(formData, updateValue)
    }
    syncSlotForm(updateValue)
  }

  function addRowScopeRule(formData, updateValue) {
    const rowScope = ensureRowScope(formData)
    const usedAttributes = new Set(rowScope.ruleItems.map(rule => rule.attribute).filter(Boolean))
    const nextAttribute = rowScopeAttributeOptions.value.find(option => !usedAttributes.has(option.value))?.value || null
    if (!nextAttribute && rowScope.ruleItems.length >= rowScopeAttributeOptions.value.length) {
      window.$message?.warning('可配置的用户属性已全部添加')
      return
    }
    if (rowScope.enabled !== 1) {
      rowScope.enabled = 1
    }
    rowScope.ruleItems.push(createRowScopeRule(nextAttribute))
    syncRowScopeColumnsFromRules(rowScope)
    syncSlotForm(updateValue)
  }

  function removeRowScopeRule(formData, index, updateValue) {
    const rowScope = ensureRowScope(formData)
    rowScope.ruleItems.splice(index, 1)
    syncRowScopeColumnsFromRules(rowScope)
    syncSlotForm(updateValue)
  }

  function handleRowScopeRuleAttributeChange(formData, rule, value, updateValue) {
    rule.attribute = value
    rule.field = null
    syncRowScopeColumnsFromRules(ensureRowScope(formData))
    syncSlotForm(updateValue)
  }

  function handleRowScopeRuleFieldChange(formData, rule, value, updateValue) {
    rule.field = value
    syncRowScopeColumnsFromRules(ensureRowScope(formData))
    syncSlotForm(updateValue)
  }

  function handleRowScopeRuleLogicChange(rule, value, updateValue) {
    rule.logic = normalizeRowScopeLogic(value)
    syncSlotForm(updateValue)
  }

  function handleRowScopeRemarkChange(formData, value, updateValue) {
    ensureRowScope(formData).remark = value
    syncSlotForm(updateValue)
  }

  function handleCacheStrategyChange(value, formData, updateValue) {
    formData.cacheEnabled = value === 1 ? 1 : 0
    if (formData.cacheEnabled === 1 && !formData.cacheTtlSeconds) {
      formData.cacheTtlSeconds = 300
    }
    syncSlotForm(updateValue)
  }

  function getRowScopeAttributeOptions(formData, currentRule) {
    const usedAttributes = new Set(
      getRowScopeRules(formData)
        .filter(rule => rule !== currentRule)
        .map(rule => rule.attribute)
        .filter(Boolean),
    )
    return rowScopeAttributeOptions.value.map(option => ({
      ...option,
      disabled: usedAttributes.has(option.value),
    }))
  }

  function getRowScopeConfiguredCount(formData) {
    return getRowScopeRules(formData).filter(rule => rule.attribute && trimToNull(rule.field)).length
  }

  function getRowScopeConditionPreview(formData) {
    const rules = getRowScopeRules(formData).filter(rule => rule.attribute && trimToNull(rule.field))
    if (rules.length === 0) {
      return '保存后将按角色数据范围动态拼接过滤条件。'
    }
    return rules.map((rule, index) => {
      const attributeLabel = rowScopeAttributeOptions.value.find(option => option.value === rule.attribute)?.label || '用户属性'
      const expression = `${attributeLabel} = ${rule.field}`
      if (index === rules.length - 1) {
        return expression
      }
      return `${expression} ${normalizeRowScopeLogic(rule.logic)}`
    }).join(' ')
  }

  function getRowScopeRuleLabel(rule) {
    const attributeLabel = rowScopeAttributeOptions.value.find(option => option.value === rule.attribute)?.label || '用户属性'
    return `${attributeLabel} = ${rule.field}`
  }

  function syncRowScopeColumnsFromRules(rowScope) {
    rowScope.tenantColumn = null
    rowScope.orgColumn = null
    rowScope.userColumn = null
    rowScope.regionColumn = null
    for (const rule of rowScope.ruleItems || []) {
      if (rowScopeAttributeOptions.value.some(option => option.value === rule.attribute) && trimToNull(rule.field)) {
        rowScope[rule.attribute] = trimToNull(rule.field)
      }
    }
  }

  function normalizeSubmitAclItems(formData) {
    formData.accessMode = formData.accessMode === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC'
    if (formData.accessMode !== 'PRIVATE') {
      return []
    }

    const normalizedItems = []
    const uniqueSubjects = new Set()
    for (const [index, item] of (formData.aclItems || []).entries()) {
      const subjectType = normalizeAclSubjectType(item?.subjectType)
      const subjectId = toIdString(item?.subjectId)
      const accessLevel = normalizeAccessLevel(item?.accessLevel)
      const isEmptyRow = !item?.subjectId && !item?.accessLevel
      if (isEmptyRow) {
        continue
      }
      if (!subjectId) {
        window.$message?.error(`第${index + 1}行授权主体不能为空`)
        return null
      }
      const uniqueKey = `${subjectType}:${subjectId}`
      if (uniqueSubjects.has(uniqueKey)) {
        window.$message?.error('同一个授权主体只能配置一条权限')
        return null
      }
      uniqueSubjects.add(uniqueKey)
      normalizedItems.push({ subjectType, subjectId, accessLevel })
    }
    return normalizedItems
  }

  function normalizeSubmitRowScope(formData) {
    const rowScope = normalizeRowScope(formData.rowScope)
    if (rowScope.enabled !== 1) {
      return {
        enabled: 0,
        scopeMode: rowScope.scopeMode || 'SYSTEM_DATA_SCOPE',
        tenantColumn: null,
        orgColumn: null,
        userColumn: null,
        regionColumn: null,
        regionStrategy: rowScope.regionStrategy || 'SELF_AND_DESCENDANTS',
        remark: trimToNull(rowScope.remark),
      }
    }

    if (rowScopeAttributeOptions.value.length === 0 || rowScopeLogicOptions.value.length === 0) {
      window.$message?.error('数据行权限字典未加载，无法安全保存当前配置')
      return null
    }

    const rules = rowScope.ruleItems || []
    if (rules.length === 0) {
      window.$message?.error('启用数据行权限后，至少需要添加一条权限规则')
      return null
    }

    const usedAttributes = new Set()
    const normalizedColumns = {
      tenantColumn: null,
      orgColumn: null,
      userColumn: null,
      regionColumn: null,
    }
    for (const [index, rule] of rules.entries()) {
      if (!rule.attribute) {
        window.$message?.error(`第${index + 1}行用户属性不能为空`)
        return null
      }
      if (!rowScopeAttributeOptions.value.some(option => option.value === rule.attribute)) {
        window.$message?.error(`第${index + 1}行用户属性无效`)
        return null
      }
      if (usedAttributes.has(rule.attribute)) {
        window.$message?.error('同一个用户属性只能配置一条映射规则')
        return null
      }
      usedAttributes.add(rule.attribute)
      const field = trimToNull(rule.field)
      if (!field) {
        window.$message?.error(`第${index + 1}行表字段不能为空`)
        return null
      }
      normalizedColumns[rule.attribute] = field
    }

    if (formData.datasetType === 'SQL' && !String(formData.sqlText || '').includes('/*DATA_SCOPE*/')) {
      window.$message?.error('SQL 数据集启用行权限时，SQL 中必须包含 /*DATA_SCOPE*/ 占位符')
      return null
    }

    return {
      enabled: 1,
      scopeMode: rowScope.scopeMode || 'SYSTEM_DATA_SCOPE',
      tenantColumn: normalizedColumns.tenantColumn,
      orgColumn: normalizedColumns.orgColumn,
      userColumn: normalizedColumns.userColumn,
      regionColumn: normalizedColumns.regionColumn,
      regionStrategy: rowScope.regionStrategy || 'SELF_AND_DESCENDANTS',
      remark: trimToNull(rowScope.remark),
    }
  }

  function trimToNull(value) {
    if (typeof value !== 'string') {
      return value ?? null
    }
    const trimmed = value.trim()
    return trimmed || null
  }

  function parseParamSchemaFormValue(value) {
    if (!value) {
      return []
    }
    if (Array.isArray(value)) {
      return value
    }

    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    }
    catch (error) {
      console.error('Failed to parse dataset param schema', error)
      window.$message?.error('查询参数定义格式异常，已按空配置处理')
      return []
    }
  }

  function normalizeParamSchema(rows, datasetType) {
    if (!rows) {
      return []
    }
    if (!Array.isArray(rows)) {
      window.$message?.error('查询条件配置格式不正确')
      return null
    }

    const normalizedRows = []
    const paramNames = new Set()

    for (const [index, row] of rows.entries()) {
      const paramName = typeof row?.paramName === 'string' ? row.paramName.trim() : ''
      const label = typeof row?.label === 'string' ? row.label.trim() : ''
      const dataType = typeof row?.dataType === 'string' && row.dataType
        ? row.dataType.trim().toUpperCase()
        : 'STRING'
      const operator = typeof row?.operator === 'string' && row.operator
        ? row.operator.trim().toUpperCase()
        : '='
      const fieldName = typeof row?.fieldName === 'string' ? row.fieldName.trim() : ''
      const defaultValue = row?.defaultValue === '' ? null : row?.defaultValue ?? null
      const required = row?.required === true
      const isEmptyRow = !paramName && !label && !fieldName && defaultValue === null && required === false

      if (isEmptyRow) {
        continue
      }
      if (!paramName) {
        window.$message?.error(`第${index + 1}行缺少条件参数名`)
        return null
      }
      if (paramNames.has(paramName)) {
        window.$message?.error(`条件参数名重复：${paramName}`)
        return null
      }
      if (!supportedParamOperators.includes(operator)) {
        window.$message?.error(`第${index + 1}行匹配方式不支持：${operator}`)
        return null
      }
      if (datasetType === 'TABLE' && !fieldName) {
        window.$message?.error(`第${index + 1}行还未选择数据表字段`)
        return null
      }
      if (datasetType === 'SQL' && fieldName) {
        window.$message?.error(`第${index + 1}行不需要配置数据表字段`)
        return null
      }

      paramNames.add(paramName)
      normalizedRows.push({
        paramName,
        label: label || null,
        dataType,
        required,
        defaultValue,
        operator,
        fieldName: fieldName || null,
      })
    }

    return normalizedRows
  }

  function renderFieldInput(row, key, placeholder) {
    if (fieldConfigReadonly.value) {
      return row[key] || '-'
    }
    return h(NInput, {
      value: row[key],
      size: 'small',
      placeholder,
      onUpdateValue: value => row[key] = value,
    })
  }

  function renderFieldSelect(row, key, options, extraProps = {}) {
    if (fieldConfigReadonly.value) {
      const option = options.find(item => item.value === row[key])
      return option?.label || row[key] || '-'
    }

    const { onChange, ...selectProps } = extraProps
    return h(NSelect, {
      value: row[key] ?? null,
      options,
      size: 'small',
      clearable: false,
      ...selectProps,
      onUpdateValue: (value) => {
        row[key] = value
        onChange?.(value)
      },
    })
  }

  function renderMaskRuleSelect(row) {
    if (fieldConfigReadonly.value) {
      if (!row.maskRule) {
        return '默认：保留前2后2'
      }
      const option = maskRuleOptions.value.find(item => item.value === row.maskRule)
      return option?.label || row.maskRule
    }

    return h(NSelect, {
      value: row.maskRule || '__DEFAULT__',
      options: maskRuleOptions.value,
      size: 'small',
      filterable: true,
      tag: true,
      placeholder: '选择脱敏规则',
      onUpdateValue: (value) => {
        row.maskRule = value === '__DEFAULT__' ? null : value
      },
    })
  }

  function normalizeSortInput(value) {
    const parsed = Number.parseInt(value, 10)
    if (Number.isNaN(parsed) || parsed < 0) {
      return 0
    }
    return parsed
  }

  async function handlePreviewSql(formData, openModal = true) {
    if (!formData.connectionId) {
      window.$message?.error('请选择数据连接')
      return
    }
    if (!formData.sqlText) {
      window.$message?.error('请输入查询SQL')
      return
    }

    sqlPreviewVisible.value = openModal
    sqlPreviewLoading.value = true
    sqlPreviewColumns.value = []
    sqlPreviewRows.value = []
    sqlPreviewScrollX.value = 0

    try {
      const res = await request.post('/data/dataset/preview-sql', {
        connectionId: formData.connectionId,
        sqlText: formData.sqlText,
        maxRows: 5,
      })
      if (res.code === 200) {
        const columns = res.data?.columns || []
        sqlPreviewColumns.value = columns.map(column => ({
          title: column,
          key: column,
          width: 160,
          ellipsis: { tooltip: true },
          render: row => row[column] ?? '',
        }))
        sqlPreviewRows.value = res.data?.rows || []
        sqlPreviewScrollX.value = Math.max(columns.length * 160, 800)
        window.$message?.success(`SQL校验通过，预览 ${sqlPreviewRows.value.length} 条数据`)
      }
      else {
        window.$message?.error(res.msg || 'SQL预览失败')
      }
    }
    catch (error) {
      window.$message?.error(error?.message || 'SQL预览失败')
    }
    finally {
      sqlPreviewLoading.value = false
    }
  }

  async function handleViewFields(row) {
    currentFieldDataset.value = row
    fieldModalTitle.value = `字段配置 - ${row.datasetName}`
    fieldModalVisible.value = true
    fieldLoading.value = true
    fieldRows.value = []

    try {
      await loadDimensionOptions()
      const res = await getDataDatasetById(row.id)
      if (res.code === 200) {
        fieldRows.value = normalizeFieldRows(res.data?.fields || [])
      }
      else {
        window.$message?.error(res.msg || '加载字段失败')
      }
    }
    catch (error) {
      console.error('Failed to load dataset fields', error)
      window.$message?.error('加载字段失败')
    }
    finally {
      fieldLoading.value = false
    }
  }

  function normalizeFieldRows(rows) {
    return (rows || []).map((row, index) => ({
      ...row,
      fieldLabel: row.fieldLabel || row.fieldName,
      dataType: row.dataType || 'STRING',
      fieldRole: row.fieldRole || 'DIMENSION',
      queryEnabled: row.queryEnabled ?? 1,
      displayEnabled: row.displayEnabled ?? 1,
      sensitiveLevel: row.sensitiveLevel || 'NONE',
      sort: row.sort ?? index,
    }))
  }

  __impl.loadTableOptions = loadTableOptions
  __impl.loadRowScopeTableFields = loadRowScopeTableFields
  __impl.loadPermissionOptions = loadPermissionOptions
  __impl.loadRoleOptions = loadRoleOptions
  __impl.loadUserOptions = loadUserOptions
  __impl.loadOrgOptions = loadOrgOptions
  __impl.transformOrgTreeOptions = transformOrgTreeOptions
  __impl.beforeSubmit = beforeSubmit
  __impl.createDefaultAclItem = createDefaultAclItem
  __impl.createDefaultRowScope = createDefaultRowScope
  __impl.normalizeAclItems = normalizeAclItems
  __impl.normalizeRowScope = normalizeRowScope
  __impl.createRowScopeRule = createRowScopeRule
  __impl.buildRowScopeRuleItems = buildRowScopeRuleItems
  __impl.normalizeRowScopeRule = normalizeRowScopeRule
  __impl.normalizeRowScopeLogic = normalizeRowScopeLogic
  __impl.normalizeAclSubjectType = normalizeAclSubjectType
  __impl.normalizeAccessLevel = normalizeAccessLevel
  __impl.syncSlotForm = syncSlotForm
  __impl.updateDatasetFormField = updateDatasetFormField
  __impl.handleAccessModeChange = handleAccessModeChange
  __impl.getAclCount = getAclCount
  __impl.addAclItem = addAclItem
  __impl.removeAclItem = removeAclItem
  __impl.handleAclSubjectTypeChange = handleAclSubjectTypeChange
  __impl.handleAclSubjectIdChange = handleAclSubjectIdChange
  __impl.handleAclAccessLevelChange = handleAclAccessLevelChange
  __impl.getAclItemLabel = getAclItemLabel
  __impl.findTreeOption = findTreeOption
  __impl.getAclSubjectOptions = getAclSubjectOptions
  __impl.getAclOrgOptions = getAclOrgOptions
  __impl.getAclSubjectFallbackLabel = getAclSubjectFallbackLabel
  __impl.appendMissingFlatOption = appendMissingFlatOption
  __impl.appendMissingTreeOption = appendMissingTreeOption
  __impl.containsTreeValue = containsTreeValue
  __impl.getRowScopeFieldOptions = getRowScopeFieldOptions
  __impl.getRowScopeFieldSourceLabel = getRowScopeFieldSourceLabel
  __impl.clearRowScopeColumns = clearRowScopeColumns
  __impl.ensureRowScope = ensureRowScope
  __impl.getRowScopeRules = getRowScopeRules
  __impl.isRowScopeEnabled = isRowScopeEnabled
  __impl.getRowScopeRemark = getRowScopeRemark
  __impl.handleRowScopeEnabledChange = handleRowScopeEnabledChange
  __impl.addRowScopeRule = addRowScopeRule
  __impl.removeRowScopeRule = removeRowScopeRule
  __impl.handleRowScopeRuleAttributeChange = handleRowScopeRuleAttributeChange
  __impl.handleRowScopeRuleFieldChange = handleRowScopeRuleFieldChange
  __impl.handleRowScopeRuleLogicChange = handleRowScopeRuleLogicChange
  __impl.handleRowScopeRemarkChange = handleRowScopeRemarkChange
  __impl.handleCacheStrategyChange = handleCacheStrategyChange
  __impl.getRowScopeAttributeOptions = getRowScopeAttributeOptions
  __impl.getRowScopeConfiguredCount = getRowScopeConfiguredCount
  __impl.getRowScopeConditionPreview = getRowScopeConditionPreview
  __impl.getRowScopeRuleLabel = getRowScopeRuleLabel
  __impl.syncRowScopeColumnsFromRules = syncRowScopeColumnsFromRules
  __impl.normalizeSubmitAclItems = normalizeSubmitAclItems
  __impl.normalizeSubmitRowScope = normalizeSubmitRowScope
  __impl.trimToNull = trimToNull
  __impl.parseParamSchemaFormValue = parseParamSchemaFormValue
  __impl.normalizeParamSchema = normalizeParamSchema
  __impl.renderFieldInput = renderFieldInput
  __impl.renderFieldSelect = renderFieldSelect
  __impl.renderMaskRuleSelect = renderMaskRuleSelect
  __impl.normalizeSortInput = normalizeSortInput
  __impl.handlePreviewSql = handlePreviewSql
  __impl.handleViewFields = handleViewFields
  __impl.normalizeFieldRows = normalizeFieldRows

  return {
    ...deps,
  }
}
