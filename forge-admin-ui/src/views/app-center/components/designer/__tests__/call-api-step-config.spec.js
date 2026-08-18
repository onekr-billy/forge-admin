import { describe, expect, it } from 'vitest'
import {
  createCallApiBusinessActionStep,
  filterExternalApiSources,
  normalizeCallApiStepConfig,
  syncCallApiParamMappings,
} from '../call-api-step-config'

describe('cALL_API step config', () => {
  it('creates an orchestration step with a governed source protocol', () => {
    expect(createCallApiBusinessActionStep(2, 100)).toEqual({
      stepCode: 'call_api_100_2',
      stepName: '调用外部接口',
      stepType: 'CALL_API',
      rollbackOnFailure: true,
      stepConfig: {
        sourceType: 'EXTERNAL_API',
        sourceKey: '',
        paramMappings: [],
        resultMode: 'ROOT',
        resultMappings: [],
        failureStrategy: 'THROW',
      },
    })
  })

  it('only exposes registered external API sources', () => {
    expect(filterExternalApiSources([
      { sourceType: 'EXTERNAL_API', sourceKey: 'crm/member', sourceName: '会员查询' },
      { sourceType: 'DATASET', sourceKey: 'member_dataset', sourceName: '会员数据集' },
      { sourceType: 'EXTERNAL_API', sourceKey: '', sourceName: '无稳定编码' },
    ])).toEqual([
      { sourceType: 'EXTERNAL_API', sourceKey: 'crm/member', sourceName: '会员查询' },
    ])
  })

  it('builds parameter rows from query source metadata without losing existing mappings', () => {
    const result = syncCallApiParamMappings({
      sourceKey: 'inventory/deduct',
      paramMappings: [{ param: 'quantity', sourceType: 'form', sourceField: 'pickupQuantity' }],
    }, {
      inputSchemaJson: JSON.stringify([
        { name: 'sku', label: '商品编码' },
        { name: 'quantity', label: '数量' },
      ]),
    }, [{ label: '商品编码', value: 'sku' }])

    expect(result.paramMappings).toEqual([
      { param: 'sku', sourceType: 'record', sourceField: 'sku' },
      { param: 'quantity', sourceType: 'form', sourceField: 'pickupQuantity' },
    ])
  })

  it('normalizes failure and result targets while preserving compatible extension fields', () => {
    expect(normalizeCallApiStepConfig({
      sourceType: 'DATASET',
      querySourceKey: 'inventory/deduct',
      failureStrategy: 'log_and_continue',
      extensionCode: 'preserved',
      resultMappings: [{ from: 'code', to: 'resultCode', target: 'form_data', whenMissing: 'clear' }],
    })).toMatchObject({
      sourceType: 'EXTERNAL_API',
      sourceKey: 'inventory/deduct',
      failureStrategy: 'LOG_AND_CONTINUE',
      extensionCode: 'preserved',
      resultMappings: [{ from: 'code', to: 'resultCode', target: 'FORM_DATA', whenMissing: 'CLEAR' }],
    })
  })
})
