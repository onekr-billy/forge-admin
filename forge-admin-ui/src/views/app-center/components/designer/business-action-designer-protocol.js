export const BUSINESS_ACTION_EXECUTION_MODE = Object.freeze({
  LOCAL_TRANSACTION: 'LOCAL_TRANSACTION',
  ORCHESTRATION: 'ORCHESTRATION',
})

export const LOCAL_TRANSACTION_STEP_TYPES = Object.freeze([
  'CREATE_RECORD',
  'UPDATE_FIELD',
  'ADJUST_NUMBER',
  'TRANSITION_STATUS',
  'ASSERT_RECORD',
])

export { createCallApiBusinessActionStep } from './call-api-step-config'

const LOCAL_STEP_LABELS = Object.freeze({
  CREATE_RECORD: '创建记录',
  UPDATE_FIELD: '更新字段',
  ADJUST_NUMBER: '调整数值',
  TRANSITION_STATUS: '变更状态',
  ASSERT_RECORD: '状态门禁',
})

export function canUseBusinessActionStep(executionMode, stepType) {
  if (String(executionMode || '').toUpperCase() !== BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION)
    return true
  const type = String(stepType || '').toUpperCase()
  return type === 'FOREACH' || LOCAL_TRANSACTION_STEP_TYPES.includes(type)
}

export function resolveBusinessActionExecutionMode(actionConfig = {}) {
  const configured = String(actionConfig.executionMode || '').trim().toUpperCase()
  const hasNonLocalStep = collectBusinessActionSteps(actionConfig).some(step => !canUseBusinessActionStep(
    BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION,
    step?.stepType,
  ))
  if (hasNonLocalStep)
    return BUSINESS_ACTION_EXECUTION_MODE.ORCHESTRATION
  if (Object.values(BUSINESS_ACTION_EXECUTION_MODE).includes(configured))
    return configured
  return BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION
}

export function createDefaultBusinessActionConfig(overrides = {}) {
  return {
    executionMode: BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION,
    inputSchema: [],
    steps: [],
    ...overrides,
  }
}

export function createLocalBusinessActionStep(stepType, index = 1, timestamp = Date.now()) {
  const type = LOCAL_TRANSACTION_STEP_TYPES.includes(String(stepType || '').toUpperCase())
    ? String(stepType).toUpperCase()
    : 'CREATE_RECORD'
  const stepConfig = { targetConfigKey: '' }
  if (type !== 'CREATE_RECORD')
    stepConfig.targetRecordIdField = 'record.id'
  if (['CREATE_RECORD', 'UPDATE_FIELD'].includes(type))
    stepConfig.fieldMappings = []
  if (type === 'ADJUST_NUMBER')
    stepConfig.adjustments = []
  if (type === 'TRANSITION_STATUS') {
    stepConfig.statusField = ''
    stepConfig.fromValue = ''
    stepConfig.toValue = ''
  }
  if (type === 'ASSERT_RECORD') {
    stepConfig.expectedFieldMappings = []
  }
  return {
    stepCode: `local_${type.toLowerCase()}_${timestamp}_${index}`,
    stepName: LOCAL_STEP_LABELS[type],
    stepType: type,
    rollbackOnFailure: true,
    stepConfig,
  }
}

function collectBusinessActionSteps(actionConfig = {}) {
  const result = []
  const visit = (steps) => {
    if (!Array.isArray(steps))
      return
    steps.forEach((step) => {
      if (!step || typeof step !== 'object')
        return
      result.push(step)
      visit(step.stepConfig?.steps || step.steps)
    })
  }
  visit(actionConfig.steps || actionConfig.stepList)
  return result
}
