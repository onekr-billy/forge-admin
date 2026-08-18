import { describe, expect, it } from 'vitest'
import {
  BUSINESS_ACTION_EXECUTION_MODE,
  canUseBusinessActionStep,
  createDefaultBusinessActionConfig,
  createLocalBusinessActionStep,
  resolveBusinessActionExecutionMode,
} from '../business-action-designer-protocol'

describe('business action designer protocol', () => {
  it('creates a local transaction action by default', () => {
    expect(createDefaultBusinessActionConfig()).toEqual({
      executionMode: 'LOCAL_TRANSACTION',
      inputSchema: [],
      steps: [],
    })
  })

  it('blocks non-local step types in local transaction mode', () => {
    expect(canUseBusinessActionStep(BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION, 'CREATE_RECORD')).toBe(true)
    expect(canUseBusinessActionStep(BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION, 'START_FLOW')).toBe(false)
    expect(canUseBusinessActionStep(BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION, 'SEND_MESSAGE')).toBe(false)
    expect(canUseBusinessActionStep(BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION, 'DOMAIN_ACTION')).toBe(false)
    expect(canUseBusinessActionStep(BUSINESS_ACTION_EXECUTION_MODE.LOCAL_TRANSACTION, 'CALL_API')).toBe(false)
  })

  it('forces actions containing CALL_API into orchestration mode', () => {
    expect(resolveBusinessActionExecutionMode({
      executionMode: 'LOCAL_TRANSACTION',
      steps: [{ stepType: 'CALL_API', stepConfig: {} }],
    })).toBe('ORCHESTRATION')
  })

  it('keeps legacy actions with external steps in orchestration mode', () => {
    expect(resolveBusinessActionExecutionMode({
      steps: [{ stepType: 'FOREACH', stepConfig: { steps: [{ stepType: 'DOMAIN_ACTION' }] } }],
    })).toBe('ORCHESTRATION')
  })

  it('emits structured local data steps', () => {
    expect(createLocalBusinessActionStep('CREATE_RECORD', 1, 100)).toMatchObject({
      stepCode: 'local_create_record_100_1',
      stepType: 'CREATE_RECORD',
      rollbackOnFailure: true,
      stepConfig: { targetConfigKey: '', fieldMappings: [] },
    })
    expect(createLocalBusinessActionStep('ADJUST_NUMBER', 2, 100)).toMatchObject({
      stepCode: 'local_adjust_number_100_2',
      stepType: 'ADJUST_NUMBER',
      stepConfig: { targetConfigKey: '', targetRecordIdField: 'record.id', adjustments: [] },
    })
    expect(createLocalBusinessActionStep('TRANSITION_STATUS', 3, 100)).toMatchObject({
      stepCode: 'local_transition_status_100_3',
      stepType: 'TRANSITION_STATUS',
      stepConfig: {
        targetConfigKey: '',
        targetRecordIdField: 'record.id',
        statusField: '',
        fromValue: '',
        toValue: '',
      },
    })
  })
})
