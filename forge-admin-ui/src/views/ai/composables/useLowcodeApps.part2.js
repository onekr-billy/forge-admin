/** src/views/ai/lowcode-apps.vue setup part 2. */
import { EllipsisVertical, SaveOutline, SparklesOutline } from '@vicons/ionicons5'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { modelListByProvider, providerPage } from '@/api/ai'
import {
  lowcodeAiStreamGenerateApp,
  lowcodeAppDetail,
  lowcodeAppPage,
  lowcodeCreateDomain,
  lowcodeCreateModel,
  lowcodeDeleteApp,
  lowcodeDeleteDomain,
  lowcodeDomainDetail,
  lowcodeDomainTree,
  lowcodeDownloadAppCode,
  lowcodeModelList,
  lowcodeSaveDraft,
} from '@/api/lowcode-crud'
import LowcodeCodePreviewModal from '@/components/lowcode-builder/code/LowcodeCodePreviewModal.vue'
import DomainEditorDrawer from '@/components/lowcode-builder/domain/DomainEditorDrawer.vue'
import DomainTreePanel from '@/components/lowcode-builder/domain/DomainTreePanel.vue'
import MoveDomainModal from '@/components/lowcode-builder/domain/MoveDomainModal.vue'
import { cloneSchema, normalizeObjectCode } from '@/components/lowcode-builder/model/model-schema'
import { useDict } from '@/composables/useDict'
export function applyLowcodeAppsPart2(deps = {}) {
  const {
    __impl, mut, abortAiGeneration, applyAiResultSteps, applyDomainToModelSchema, buildApiRowsForTarget, buildDraftContext, buildExistingModelContext,
    buildImportedConfigKey, clearPendingAppSearch, closeAiCreateModal, confirmAiAppDraft, createApp, createInitialAiSteps, deleteApp, deleteDomain,
    downloadAppCode, editDomain, ensureAiDomains, exportApp, findDomainById, flattenDomains, generateAiAppDraft, getAppActionOptions,
    handleAiCreateVisibleUpdate, handleAiProviderChange, handleAiStreamChunk, handleAiStreamEvent, handleAiTargetDomainChange, handleAppActionSelect, handleAppImportFile, handleDomainSaved,
    handleKeywordSearch, handleMoved, handlePageChange, handlePageSizeChange, handleSearch, layoutName, loadAiModelOptions, loadAiProviderOptions,
    loadApps, loadDomainModelContext, loadDomains, markAiStepError, markPendingAiStepsCompleted, openAiCreateApp, openAiPreviewDetail, openBuilder,
    openChildDomainEditor, openCodePreview, openDomainEditor, openMoveDomain, openRuntime, refreshAll, refreshDomains, resetAiStreamState,
    resolveAiDomainInfo, resolveAppModel, saveAiApps, saveAiModels, selectDomain, statusLabel, stepStatusText, triggerAppImport,
    updateAiStep, router, domainLoading, loading, domains, selectedDomainId, selectedDomain, apps,
    total, pageNum, pageSize, keyword, domainKeyword, publishStatus, domainEditorVisible, editingDomain,
    moveVisible, movingApp, appImportInputRef, codePreviewVisible, codePreviewApp, downloadingCodeId, aiCreateVisible, aiCreateLoading,
    aiConfirmSaving, aiCreateDescription, aiCreateResult, aiStreamController, aiSteps, aiTargetDomainId, aiRawContent, aiReasoningContent,
    aiIsReasoningPhase, aiReasoningStartTime, aiReasoningEndTime, aiProviderId, aiModelId, aiProviderOptions, aiModelOptions, aiProviderLoading,
    aiModelLoading, aiIncludeDomainModels, domainModelContext, domainModelLoading, activePreviewModelCode, aiPreviewDetailVisible, aiPreviewDetailType, aiAppendInstruction,
    statusOptions, flatDomains, domainSelectOptions, aiTargetDomain, aiReasoningTime, aiGeneratedDomains, aiGeneratedModels, aiGeneratedApps,
    aiDecisions, aiNotes, previewModelOptions, activePreviewModel, activePreviewFields, aiPrimaryDomainLabel, aiPrimaryModelName, aiRelationCount,
    aiModelRelationRows, aiApiDetailRows, aiApiCount, aiCompletionPercent, aiValidationStatus, aiPageStructureRows, aiPreviewFields, aiApiPreviewRows,
    aiPageDetailRows, aiPreviewDetailTitle, modelPreviewColumns, appPreviewColumns, apiPreviewColumns, pageTitle, pageSubtitle, appScopeText,
  } = deps
  function appInitial(app) {
    const text = app.appName || app.tableComment || app.configKey || '低'
    return text.slice(0, 1).toUpperCase()
  }

  function formatTime(value) {
    if (!value)
      return ''
    const date = new Date(value)
    return `${date.getMonth() + 1}-${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  }

  function downloadJson(payload, filename) {
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
    downloadBlob(blob, filename)
  }

  function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  }
  __impl.appInitial = appInitial
  __impl.formatTime = formatTime
  __impl.downloadJson = downloadJson
  __impl.downloadBlob = downloadBlob

  return {
    ...deps,
  }
}
