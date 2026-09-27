/** monitor.vue setup part 2. */
import * as echarts from 'echarts'
import { NButton, NDropdown, NSpace, NTag } from 'naive-ui'
import { computed, h, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import flowApi from '@/api/flow'
import UserAvatar from '@/components/common/UserAvatar.vue'
import UserSelectModal from '@/components/common/UserSelectModal.vue'
import DictTag from '@/components/DictTag.vue'
import DingFlowViewer from '@/components/flow-designer/viewer/DingFlowViewer.vue'
import FlowReadonlyFormPanel from '@/components/flow/FlowReadonlyFormPanel.vue'
import FlowTaskDetailShell from '@/components/flow/FlowTaskDetailShell.vue'
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store'
import { request } from '@/utils'
import {
  buildCurrentTaskOptions,
  buildMonitorFormQuery,
  canInterveneInstance,
  canMutateRunningInstance,
  hasMonitorPermission,
  isSuspendedInstance,
} from '../utils/monitorAdmin'
export function applyFlowMonitorPart2(deps = {}) {
  const {
    __impl, mut, beginAdminMutation, buildSearchParams, closeDeleteLoading, confirmRetry, endAdminMutation, formatVariableValue,
    getErrorMessage, getErrorStageText, getErrorTypeText, getInstanceStatusClass, getInstanceStatusIcon, getInstanceStatusText, getRequestId, handleActivate,
    handleAdminTaskPageChange, handleAdminTaskPageSizeChange, handleCleanupCurrentFilter, handleDeleteInstance, handleInstanceErrorPageChange, handleInstanceErrorPageSizeChange, handlePageChange, handlePageSizeChange,
    handleReset, handleResolveErrorLog, handleRetryError, handleSearch, handleSuspend, handleUserSelect, hasCleanupFilter, isAdminMutating,
    loadActivities, loadAdminActionContext, loadAdminInstanceTasks, loadApprovalHistory, loadCurrentTasks, loadData, loadInstanceErrorLogs, loadProcessDistribution,
    loadStatistics, loadTaskTrend, openReassignUserSelect, openRoutedInstance, refreshMonitorData, resetAdminActionForm, showAdminActions, showDeleteLoading,
    showDetail, showDiagram, showErrorDetail, showInstanceErrorLogs, showMutationError, showMutationResponseError, showRetryDialog, showVariables,
    toNumberOptions, route, userStore, message, canManage, canCleanup, statistics, searchForm,
    statusOptions, tableData, loading, dataError, cleanupLoading, deletingProcessId, isDeletingProcess, cleanupLoadingMessageKey,
    deleteLoadingMessageKey, pagination, columns, detailDrawerVisible, currentInstance, approvalHistory, monitorFormRow, canMutateCurrent,
    isSuspendedCurrent, diagramModalVisible, currentDiagramInstanceId, variablesModalVisible, variablesLoading, processVariables, adminActionsModalVisible, adminMutation,
    isAdminMutationBusy, terminateReason, rollbackTargetActivity, rollbackReason, reassignUserId, reassignUserName, reassignReason, activityOptions,
    activitiesLoading, currentTaskId, currentTaskOptions, userSelectModalVisible, adminTaskContext, adminTaskTree, adminTaskTreeTruncated, adminTaskContextLoading,
    adminTaskContextError, adminTaskPagination, adminTaskColumns, taskChartRef, processChartRef, chartPeriod, instanceErrorDrawerVisible, currentErrorInstanceId,
    instanceErrorLogData, instanceErrorLogLoading, instanceErrorStatusFilter, instanceErrorPagination, errorStatusOptions, errorDetailVisible, currentErrorLog, retryModalVisible,
    retryReason, retrying, errorColumns,
  } = deps
  async function confirmTerminate() {
    if (!terminateReason.value.trim()) {
      message.warning('请输入终止原因')
      return
    }
    if (!currentInstance.value.id) {
      message.warning('无法获取流程实例ID')
      return
    }
    if (!canInterveneInstance(currentInstance.value.status)) {
      message.warning('只能终止运行中或已挂起的流程')
      return
    }

    if (!beginAdminMutation('terminate'))
      return
    try {
      const res = await request.post(`/api/flow/monitor/terminate/${currentInstance.value.id}`, {
        reason: terminateReason.value,
      })
      if (res.code === 200) {
        message.success('流程已终止')
        adminActionsModalVisible.value = false
        detailDrawerVisible.value = false
        await Promise.all([loadData(), loadStatistics()])
      }
      else {
        showMutationResponseError(res, '终止流程失败')
      }
    }
    catch (error) {
      console.error('终止流程失败:', error)
      showMutationError(error, '终止流程失败')
    }
    finally {
      endAdminMutation('terminate')
    }
  }

  async function confirmRollback() {
    if (!canMutateRunningInstance(currentInstance.value.status)) {
      message.warning('请先激活流程后再回退')
      return
    }
    if (!rollbackTargetActivity.value) {
      message.warning('请选择目标节点')
      return
    }
    if (!rollbackReason.value.trim()) {
      message.warning('请输入回退原因')
      return
    }

    if (!beginAdminMutation('rollback'))
      return
    try {
      const res = await request.post(`/api/flow/monitor/rollback/${currentInstance.value.id}`, {
        targetActivityId: rollbackTargetActivity.value,
        reason: rollbackReason.value,
      })
      if (res.code === 200) {
        message.success('流程已回退')
        adminActionsModalVisible.value = false
        await Promise.all([loadData(), loadStatistics(), loadAdminActionContext(currentInstance.value.id)])
      }
      else {
        showMutationResponseError(res, '回退流程失败')
      }
    }
    catch (error) {
      console.error('回退流程失败', error)
      showMutationError(error, '回退流程失败')
    }
    finally {
      endAdminMutation('rollback')
    }
  }

  async function confirmReassign() {
    if (!canMutateRunningInstance(currentInstance.value.status)) {
      message.warning('请先激活流程后再转派')
      return
    }
    if (!reassignUserId.value.trim()) {
      message.warning('请选择新处理人')
      return
    }
    if (!reassignReason.value.trim()) {
      message.warning('请输入转派原因')
      return
    }
    if (!currentTaskId.value) {
      message.error('请选择要转派的任务')
      return
    }

    if (!beginAdminMutation('reassign'))
      return
    try {
      const res = await request.post(`/api/flow/monitor/reassign/${currentTaskId.value}`, {
        newAssignee: String(reassignUserId.value),
        reason: reassignReason.value,
      })
      if (res.code === 200) {
        message.success('任务已转派')
        adminActionsModalVisible.value = false
        await Promise.all([loadData(), loadAdminActionContext(currentInstance.value.id)])
      }
      else {
        showMutationResponseError(res, '转派任务失败')
      }
    }
    catch (error) {
      console.error('转派任务失败', error)
      showMutationError(error, '转派任务失败')
    }
    finally {
      endAdminMutation('reassign')
    }
  }

  // 超时任务快捷筛选：点击卡片直接过滤列表
  function filterByTimeout() {
    if (searchForm.overdue) {
      searchForm.overdue = false
    }
    else {
      searchForm.overdue = true
    }
    pagination.page = 1
    loadData()
  }

  // 初始化图表（首次挂载）
  async function initCharts() {
    await nextTick()
    if (taskChartRef.value) {
      mut.taskChart = echarts.init(taskChartRef.value)
    }
    if (processChartRef.value) {
      mut.processChart = echarts.init(processChartRef.value)
    }
    await refreshCharts()
  }

  // 刷新图表数据（切换周期时调用）
  async function refreshCharts() {
    const [trendData, distributionData] = await Promise.all([
      loadTaskTrend(chartPeriod.value),
      loadProcessDistribution(),
    ])

    // 计算通过率（逐日 completed/created，避免除零）
    const passRateData = trendData.dates.map((_, i) => {
      const created = trendData.created[i] || 0
      const completed = trendData.completed[i] || 0
      return created > 0 ? Math.round((completed / created) * 100) : 0
    })

    if (mut.taskChart) {
      mut.taskChart.setOption({
        color: ['#2080f0', '#18a058', '#f0a020'],
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross',
            crossStyle: { color: '#999' },
          },
          backgroundColor: 'rgba(255,255,255,0.95)',
          borderColor: '#e8e8e8',
          borderWidth: 1,
          textStyle: { color: '#333' },
          formatter(params) {
            let html = `<b>${params[0].axisValue}</b><br/>`
            params.forEach((p) => {
              const suffix = p.seriesName === '通过率' ? '%' : ' 件'
              html += `${p.marker} ${p.seriesName}：<b>${p.value}${suffix}</b><br/>`
            })
            return html
          },
        },
        legend: {
          data: ['新增流程', '完成流程', '通过率'],
          bottom: 0,
          textStyle: { fontSize: 12 },
        },
        grid: {
          left: 50,
          right: 60,
          top: 20,
          bottom: 40,
        },
        xAxis: {
          type: 'category',
          boundaryGap: true,
          axisTick: { alignWithLabel: true },
          data: trendData.dates.length > 0 ? trendData.dates : Array.from({ length: chartPeriod.value }, (_, i) => `第${i + 1}天`),
        },
        yAxis: [
          {
            type: 'value',
            name: '数量（件）',
            minInterval: 1,
            nameTextStyle: { fontSize: 12, color: '#666' },
            axisLabel: { fontSize: 11 },
            splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
          },
          {
            type: 'value',
            name: '通过率',
            min: 0,
            max: 100,
            nameTextStyle: { fontSize: 12, color: '#666' },
            axisLabel: { formatter: '{value}%', fontSize: 11 },
            splitLine: { show: false },
          },
        ],
        series: [
          {
            name: '新增流程',
            type: 'bar',
            barWidth: '30%',
            barGap: '20%',
            data: trendData.created.length > 0 ? trendData.created : Array.from({ length: chartPeriod.value }).fill(0),
            itemStyle: { borderRadius: [4, 4, 0, 0] },
            emphasis: { itemStyle: { borderRadius: [4, 4, 0, 0] } },
          },
          {
            name: '完成流程',
            type: 'bar',
            barWidth: '30%',
            data: trendData.completed.length > 0 ? trendData.completed : Array.from({ length: chartPeriod.value }).fill(0),
            itemStyle: { borderRadius: [4, 4, 0, 0] },
            emphasis: { itemStyle: { borderRadius: [4, 4, 0, 0] } },
          },
          {
            name: '通过率',
            type: 'line',
            yAxisIndex: 1,
            data: passRateData,
            smooth: true,
            symbol: 'circle',
            symbolSize: 6,
            lineStyle: { width: 2.5 },
            itemStyle: { borderWidth: 2, borderColor: '#fff' },
            areaStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: 'rgba(240,160,32,0.25)' },
                  { offset: 1, color: 'rgba(240,160,32,0.02)' },
                ],
              },
            },
          },
        ],
      }, true)
    }

    if (mut.processChart) {
      const pieData = distributionData.length > 0 ? distributionData : [{ name: '暂无数据', value: 1 }]
      mut.processChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c}件 ({d}%)' },
        legend: { orient: 'vertical', left: 'left', top: 'center' },
        series: [
          {
            name: '流程分布',
            type: 'pie',
            radius: ['40%', '68%'],
            center: ['60%', '50%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
            label: { show: true, formatter: '{b}\n{d}%' },
            labelLine: { show: true, length: 10, length2: 8 },
            data: pieData,
          },
        ],
      }, true)
    }
  }

  // 窗口 resize 时自适应图表尺寸
  function handleResize() {
    mut.taskChart?.resize()
    mut.processChart?.resize()
  }

  onMounted(() => {
    if (route.query.modelKey) {
      searchForm.modelKey = route.query.modelKey
    }
    const routedProcessInstanceId = Array.isArray(route.query.processInstanceId)
      ? route.query.processInstanceId[0]
      : route.query.processInstanceId
    if (routedProcessInstanceId)
      openRoutedInstance(String(routedProcessInstanceId))
    loadStatistics()
    loadData()
    initCharts()
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    mut.taskChart?.dispose()
    mut.processChart?.dispose()
  })
  __impl.confirmTerminate = confirmTerminate
  __impl.confirmRollback = confirmRollback
  __impl.confirmReassign = confirmReassign
  __impl.filterByTimeout = filterByTimeout
  __impl.initCharts = initCharts
  __impl.refreshCharts = refreshCharts
  __impl.handleResize = handleResize

  return {
    ...deps,
  }
}
