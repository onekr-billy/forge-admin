/** dataset.vue setup part 3. */
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

export function applyDatasetPagePart3(deps = {}) {
  const {
    __impl, mut, addAclItem, addRowScopeRule, appendMissingFlatOption, appendMissingTreeOption, applySearch, beforeRenderDetail,
    beforeRenderForm, beforeSubmit, buildCategorySelectOptions, buildCategoryTreeNodes, buildRowScopeRuleItems, buildSearchParams, clearRowScopeColumns, containsTreeValue,
    createDefaultAclItem, createDefaultRowScope, createRowScopeRule, ensureRowScope, ensureRowScopeDictOptions, extractSqlParamNames, filterCategoryTree, findCategoryById,
    findTreeOption, formatDatasetDate, getAccessModeLabel, getAclCount, getAclItemLabel, getAclOrgOptions, getAclSubjectFallbackLabel, getAclSubjectOptions,
    getCategoryName, getConnectionName, getDatasetCreatorLabel, getDatasetParamConstraint, getDatasetParamGuide, getDatasetParamReadiness, getDatasetSourceGuide, getDatasetSourceSubject,
    getDatasetTagLabels, getDatasetTypeLabel, getDatasetUpdaterLabel, getDatasetVersionLabel, getEnableStatusLabel, getParamPreviewDescription, getParamPreviewRows, getPublishStatusLabel,
    getRowScopeAttributeOptions, getRowScopeConditionPreview, getRowScopeConfiguredCount, getRowScopeFieldOptions, getRowScopeFieldSourceLabel, getRowScopeRemark, getRowScopeRuleLabel, getRowScopeRules,
    getSqlParamCount, getStepNodeInlineStyle, handleAccessModeChange, handleAclAccessLevelChange, handleAclSubjectIdChange, handleAclSubjectTypeChange, handleAddDataset, handleCacheStrategyChange,
    handleCategoryTreeSelect, handleConnectionChange, handleDatasetModalClose, handleDatasetTypeChange, handleEdit, handlePreviewSql, handleResetFilters, handleRowScopeEnabledChange,
    handleRowScopeRemarkChange, handleRowScopeRuleAttributeChange, handleRowScopeRuleFieldChange, handleRowScopeRuleLogicChange, handleTableNameChange, handleViewDataset, handleViewFields, isRowScopeEnabled,
    loadCategoryTree, loadConnectionOptions, loadDimensionOptions, loadOrgOptions, loadPermissionOptions, loadRoleOptions, loadRowScopeTableFields, loadTableOptions,
    loadUserOptions, normalizeAccessLevel, normalizeAclItems, normalizeAclSubjectType, normalizeFieldRows, normalizeParamSchema, normalizeRowScope, normalizeRowScopeLogic,
    normalizeRowScopeRule, normalizeSortInput, normalizeSubmitAclItems, normalizeSubmitRowScope, parseParamSchemaFormValue, prepareDatasetFormData, removeAclItem, removeRowScopeRule,
    renderFieldInput, renderFieldSelect, renderMaskRuleSelect, resetRowScopeTableFields, resetTableOptions, scrollToStepSection, selectAllCategories, selectUncategorized,
    setEditorStep, syncRowScopeColumnsFromRules, syncSlotForm, toIdString, transformOrgTreeOptions, trimToNull, updateDatasetFormField, router,
    crudRef, connectionOptions, categoryTree, categoryKeyword, tableOptions, dimensionOptions, tableLoading, rowScopeTableFieldLoading,
    loadedTableConnectionId, loadingTableConnectionId, rowScopeTableFieldKey, rowScopeTableFieldOptions, fieldModalVisible, fieldLoading, fieldSaving, fieldModalTitle,
    fieldRows, currentFieldDataset, sqlPreviewVisible, sqlPreviewLoading, sqlPreviewColumns, sqlPreviewRows, sqlPreviewScrollX, roleOptions,
    userOptions, orgTreeOptions, permissionOptionsLoaded, permissionOptionsLoading, activeCategoryScope, selectedCategoryId, currentFormMode, currentEditingDataset,
    currentStep, stepDefinitions, totalSteps, queryForm, datasetTypeOptions, statusOptions, resultEncodingOptions, publishStatusOptions,
    datasetImpactLimit, datasetImpactVisibleLimit, accessModeOptions, aclSubjectTypeOptions, accessLevelOptions, rowScopeAttributeOptions, rowScopeLogicOptions, dataTypeOptions,
    fieldRoleOptions, sensitiveLevelOptions, maskRuleOptions, dateFormatOptions, dataUnitOptions, supportedParamOperators, isFormReadOnly, fieldConfigReadonly,
    selectedCategoryNode, selectedTreeKeys, currentStepMeta, formModeLabel, stepNavigationNote, stepProgressPercent, stepShellStyle, stepProgressWrapStyle,
    stepProgressBaseLineStyle, stepProgressActiveLineStyle, stepNavigationWrapperStyle, stepNavigationActionsStyle, stepNavigationMetaStyle, activeCategoryScopeLabel, categoryTreeNodes, categoryTreeSelectOptions,
    fieldConfigStats, tableColumns, fieldColumns, fieldTableScrollX, editSchema,
  } = deps
  function validateFieldRows(rows) {
    const fieldNames = new Set()
    for (const [index, row] of rows.entries()) {
      const fieldName = typeof row.fieldName === 'string' ? row.fieldName.trim() : ''
      const fieldLabel = typeof row.fieldLabel === 'string' ? row.fieldLabel.trim() : ''
      if (!fieldName) {
        window.$message?.error(`第${index + 1}行缺少字段名`)
        return null
      }
      if (fieldNames.has(fieldName)) {
        window.$message?.error(`字段名重复：${fieldName}`)
        return null
      }
      if (!fieldLabel) {
        window.$message?.error(`字段 ${fieldName} 缺少显示名称`)
        return null
      }
      fieldNames.add(fieldName)
    }

    return rows.map((row, index) => {
      const dataType = row.dataType || 'STRING'
      const fieldRole = row.fieldRole || 'DIMENSION'
      const sensitiveLevel = row.sensitiveLevel || 'NONE'
      const maskRule = row.maskRule === '__DEFAULT__' ? null : row.maskRule
      return {
        ...row,
        fieldName: row.fieldName.trim(),
        fieldLabel: row.fieldLabel.trim(),
        dataType,
        fieldRole,
        queryEnabled: row.queryEnabled ?? 1,
        displayEnabled: row.displayEnabled ?? 1,
        sensitiveLevel,
        dateFormat: ['DATE', 'DATETIME'].includes(dataType) ? row.dateFormat || null : null,
        dataUnit: row.dataUnit || null,
        dimensionId: fieldRole === 'DIMENSION' ? row.dimensionId || null : null,
        maskRule: sensitiveLevel === 'MASK' ? maskRule || null : null,
        sort: row.sort ?? index,
        description: row.description || null,
      }
    })
  }

  async function handleSaveFieldConfig() {
    if (!currentFieldDataset.value?.id) {
      return
    }
    if (fieldConfigReadonly.value) {
      window.$message?.warning('已发布数据集不可修改字段配置')
      return
    }

    const normalizedRows = validateFieldRows(fieldRows.value)
    if (!normalizedRows) {
      return
    }

    fieldSaving.value = true
    try {
      const res = await saveDataDatasetFields(currentFieldDataset.value.id, normalizedRows)
      if (res.code === 200) {
        window.$message?.success('字段配置已保存')
        fieldRows.value = normalizeFieldRows(normalizedRows)
      }
      else {
        window.$message?.error(res.msg || '保存字段配置失败')
      }
    }
    catch (error) {
      window.$message?.error(error?.message || '保存字段配置失败')
    }
    finally {
      fieldSaving.value = false
    }
  }

  async function handleSyncCurrentFields() {
    if (currentFieldDataset.value) {
      confirmSyncDatasetFields(currentFieldDataset.value, true)
    }
  }

  function handleDelete(row) {
    window.$dialog.warning({
      title: '确认删除',
      content: `确定要删除数据集“${row.datasetName}”吗？`,
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await deleteDataDataset(row.id)
          if (res.code === 200) {
            window.$message?.success('删除成功')
            crudRef.value?.refresh()
          }
        }
        catch (error) {
          window.$message?.error(error?.message || '删除失败')
        }
      },
    })
  }

  async function handleSyncFields(row) {
    confirmSyncDatasetFields(row, true)
  }

  function confirmSyncDatasetFields(row, openModal = false) {
    window.$dialog.warning({
      title: '确认同步字段',
      content: `同步会重新读取数据源字段，并覆盖数据集“${row.datasetName}”当前字段配置，包括显示名称、字段角色、维度绑定、脱敏规则和排序。确认继续吗？`,
      positiveText: '确认同步',
      negativeText: '取消',
      onPositiveClick: () => syncDatasetFields(row, openModal),
    })
  }

  async function syncDatasetFields(row, openModal = false) {
    if (openModal) {
      currentFieldDataset.value = row
      fieldModalTitle.value = `字段配置 - ${row.datasetName}`
      fieldModalVisible.value = true
      fieldLoading.value = true
    }
    try {
      window.$message?.loading('正在同步字段...', { duration: 0, key: 'syncFields' })
      await loadDimensionOptions()
      const res = await syncDataDatasetFields(row.id)
      if (res.code === 200) {
        window.$message?.success(`同步成功，共 ${res.data?.length || 0} 个字段`, { key: 'syncFields' })
        fieldRows.value = normalizeFieldRows(res.data || [])
      }
      else {
        window.$message?.error(res.msg || '同步失败', { key: 'syncFields' })
      }
    }
    catch (error) {
      window.$message?.error(error?.message || '同步字段失败', { key: 'syncFields' })
    }
    finally {
      fieldLoading.value = false
    }
  }

  function handlePublishDataset(row) {
    window.$dialog.warning({
      title: '确认发布',
      content: `发布后数据集“${row.datasetName}”将进入只读状态，仅可查看和下架，确认继续吗？`,
      positiveText: '发布',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await publishDataDataset(row.id)
          if (res.code === 200) {
            window.$message?.success('发布成功')
            crudRef.value?.refresh()
          }
        }
        catch (error) {
          window.$message?.error(error?.message || '发布失败')
        }
      },
    })
  }

  async function loadDatasetImpact(row) {
    try {
      const res = await getDashboardDatasetImpact(row.id, datasetImpactLimit)
      if (res.code === 200) {
        return res.data || []
      }
      window.$message?.warning(res.msg || '数据集影响分析查询失败')
    }
    catch (error) {
      window.$message?.warning(error?.message || '数据集影响分析查询失败')
    }
    return null
  }

  function renderDatasetImpactContent(row, impacts) {
    if (impacts === null) {
      return `下架后数据集“${row.datasetName}”将暂停供下游使用。当前影响分析查询失败，请确认是否继续下架。`
    }
    if (!impacts.length) {
      return `下架后数据集“${row.datasetName}”将暂停供下游使用。当前未发现 AI 大屏组件血缘影响，确认继续吗？`
    }

    const visibleItems = impacts.slice(0, datasetImpactVisibleLimit)
    return h('div', { class: 'dataset-impact-dialog' }, [
      h('p', null, `下架后数据集“${row.datasetName}”将暂停供下游使用。`),
      h('div', { class: 'dataset-impact-dialog__summary' }, `检测到最近 ${impacts.length} 个 AI 大屏组件使用了该数据集：`),
      h('ul', { class: 'dataset-impact-dialog__list' }, visibleItems.map(item => h('li', { key: item.lineageId || `${item.recordId}-${item.componentIndex}` }, [
        h('strong', null, item.projectName || item.generatedTitle || '未命名大屏'),
        h('span', null, ` / ${item.componentTitle || item.componentKey || `组件${item.componentIndex ?? ''}`}`),
        item.businessName ? h('small', null, `业务：${item.businessName}`) : null,
        item.fieldNames ? h('small', null, `字段：${item.fieldNames}`) : null,
      ]))),
      impacts.length > visibleItems.length
        ? h('div', { class: 'dataset-impact-dialog__more' }, `仅展示前 ${visibleItems.length} 个，请到影响分析接口查看完整结果。`)
        : null,
    ])
  }

  async function handleOfflineDataset(row) {
    const impacts = await loadDatasetImpact(row)
    window.$dialog.warning({
      title: '确认下架',
      content: () => renderDatasetImpactContent(row, impacts),
      positiveText: '下架',
      negativeText: '取消',
      onPositiveClick: async () => {
        try {
          const res = await offlineDataDataset(row.id)
          if (res.code === 200) {
            window.$message?.success('下架成功')
            crudRef.value?.refresh()
          }
        }
        catch (error) {
          window.$message?.error(error?.message || '下架失败')
        }
      },
    })
  }

  function goToCategoryManage() {
    router.push('/data/dataset-category')
  }

  function handleStepReset() {
    currentStep.value = 1
  }

  function canGoToNextStep(formData) {
    if (isFormReadOnly.value) {
      return true
    }

    if (currentStep.value === 1) {
      if (!formData.datasetCode) {
        window.$message?.warning('请输入数据集编码')
        return false
      }
      if (!formData.datasetName) {
        window.$message?.warning('请输入数据集名称')
        return false
      }
      if (!formData.connectionId) {
        window.$message?.warning('请选择数据连接')
        return false
      }
      if (formData.datasetType === 'TABLE' && !formData.tableName) {
        window.$message?.warning('请选择数据表')
        return false
      }
      if (formData.datasetType === 'SQL' && !formData.sqlText) {
        window.$message?.warning('请输入查询SQL')
        return false
      }
    }
    return true
  }

  function goToNextStep(formData) {
    if (!canGoToNextStep(formData)) {
      return
    }
    if (currentStep.value < totalSteps) {
      currentStep.value++
      if (currentStep.value === totalSteps) {
        loadPermissionOptions()
        loadRowScopeTableFields(formData)
      }
    }
  }

  function goToPrevStep() {
    if (currentStep.value > 1) {
      currentStep.value--
    }
  }
  __impl.validateFieldRows = validateFieldRows
  __impl.handleSaveFieldConfig = handleSaveFieldConfig
  __impl.handleSyncCurrentFields = handleSyncCurrentFields
  __impl.handleDelete = handleDelete
  __impl.handleSyncFields = handleSyncFields
  __impl.confirmSyncDatasetFields = confirmSyncDatasetFields
  __impl.syncDatasetFields = syncDatasetFields
  __impl.handlePublishDataset = handlePublishDataset
  __impl.loadDatasetImpact = loadDatasetImpact
  __impl.renderDatasetImpactContent = renderDatasetImpactContent
  __impl.handleOfflineDataset = handleOfflineDataset
  __impl.goToCategoryManage = goToCategoryManage
  __impl.handleStepReset = handleStepReset
  __impl.canGoToNextStep = canGoToNextStep
  __impl.goToNextStep = goToNextStep
  __impl.goToPrevStep = goToPrevStep

  return {
    ...deps,
  }
}
