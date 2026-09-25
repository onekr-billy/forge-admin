import { describe, expect, it } from 'vitest'
import { readDataFieldValue, readSelectionLabelFromData, resolveSelectionLabelFields } from '../selection-label-fields'

describe('selection label field isolation', () => {
  it.each([
    ['user', 'applicantId', 'applicantName'],
    ['org', 'departmentId', 'departmentName'],
  ])('never writes a %s label back to its primary ID field', (selectionType, fieldName, expectedLabelField) => {
    const fields = resolveSelectionLabelFields({
      field: fieldName,
      props: {
        labelValueField: fieldName,
        targetField: fieldName,
      },
    }, selectionType)

    expect(fields).not.toContain(fieldName)
    expect(fields).toContain(expectedLabelField)
  })

  it('reads companion labels via snake_case aliases on child rows', () => {
    expect(readDataFieldValue({ field_user_name: '张三' }, 'fieldUserName')).toBe('张三')
    expect(readSelectionLabelFromData(
      { field_user: '1', field_user_name: '李四' },
      { field: 'fieldUser', type: 'userSelect' },
      'user',
    )).toBe('李四')
  })
})
