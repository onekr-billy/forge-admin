import { describe, expect, it } from 'vitest'
import { adaptBusinessTaskFields } from '../business-task-form-adapter'

describe('business task form adapter', () => {
  it('preserves range controls and writable metadata for H5', () => {
    const fields = adaptBusinessTaskFields([{
      field: 'period',
      label: '有效期',
      componentType: 'daterange',
      writable: true,
      readable: true,
      props: { startPlaceholder: '开始日期', endPlaceholder: '结束日期' },
    }])

    expect(fields[0]).toMatchObject({
      field: 'period',
      type: 'daterange',
      readonly: false,
      props: { disabled: false, startPlaceholder: '开始日期', endPlaceholder: '结束日期' },
    })
  })
})
