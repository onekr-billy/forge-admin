import { describe, expect, it } from 'vitest'
import { resolveRuntimeSearchSchema } from '../runtime-crud-block-props'

describe('resolveRuntimeSearchSchema', () => {
  it('prefers runtime searchSchema ordered by explicit searchFieldRefs', () => {
    const schema = resolveRuntimeSearchSchema({
      hasExplicitSearchFieldRefs: true,
      blockProps: { searchFieldRefs: ['fieldB', 'fieldA'] },
      runtimeSearchSchema: [
        { field: 'fieldA', label: 'A', queryType: 'like' },
        { field: 'fieldB', label: 'B', queryType: 'eq' },
        { field: 'fieldC', label: 'C', queryType: 'eq' },
      ],
      aiSearchSchema: [{ field: 'fieldA', label: 'local-A' }],
    })
    expect(schema.map(item => item.field)).toEqual(['fieldB', 'fieldA'])
    expect(schema[0].queryType).toBe('eq')
    expect(schema[1].queryType).toBe('like')
  })

  it('keeps explicitly empty searchFieldRefs empty', () => {
    expect(resolveRuntimeSearchSchema({
      hasExplicitSearchFieldRefs: true,
      blockProps: { searchFieldRefs: [] },
      runtimeSearchSchema: [{ field: 'fieldA', label: 'A' }],
      aiSearchSchema: [{ field: 'fieldA', label: 'A' }],
    })).toEqual([])
  })

  it('falls back to aiSearchSchema when runtime searchSchema is empty', () => {
    const schema = resolveRuntimeSearchSchema({
      hasExplicitSearchFieldRefs: true,
      blockProps: { searchFieldRefs: ['fieldA'] },
      runtimeSearchSchema: [],
      aiSearchSchema: [{ field: 'fieldA', label: 'local-A' }],
    })
    expect(schema).toEqual([{ field: 'fieldA', label: 'local-A' }])
  })
})
