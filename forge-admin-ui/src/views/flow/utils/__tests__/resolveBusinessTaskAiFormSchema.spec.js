import { describe, expect, it } from 'vitest'
import {
  resolveBusinessTaskAiFormSchema,
} from '../resolveBusinessTaskAiFormSchema'

describe('resolveBusinessTaskAiFormSchema re-export', () => {
  it('保持审批页旧 import 可用', () => {
    const fields = [{ field: 'a' }, { field: 'b' }]
    expect(resolveBusinessTaskAiFormSchema({
      protocolVersion: '1',
      uiDocument: { version: '1', sections: [{ fields: ['b', 'a'] }], components: [] },
      fields,
    }).map(f => f.field)).toEqual(['b', 'a'])
  })
})
