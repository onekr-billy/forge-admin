import { describe, expect, it } from 'vitest'
import {
  resolveDefaultSearchComponentType,
  resolveSearchComponentLabel,
  resolveSearchFieldRefsForWrite,
  resolveSelectedFieldRefs,
} from '../fieldDrawerConfig'

describe('fieldDrawerConfig search refs', () => {
  const fields = [
    { field: 'id', label: 'ID', searchable: false },
    { field: 'fieldInput', label: '输入框', searchable: true },
    { field: 'fieldNumber', label: '数字', searchable: true },
    { field: 'fieldTreeSelect', label: '树形选择', searchable: false },
  ]

  it('does not light up query switch from table fieldRefs when searchFieldRefs is missing', () => {
    const block = {
      blockType: 'AiCrudPage',
      fieldRefs: ['id', 'fieldInput', 'fieldTreeSelect'],
      props: {},
    }
    expect(resolveSelectedFieldRefs(block, 'search', fields)).toEqual(['fieldInput', 'fieldNumber'])
    expect(resolveSelectedFieldRefs(block, 'table', fields)).toEqual(['id', 'fieldInput', 'fieldTreeSelect'])
  })

  it('uses explicit searchFieldRefs when present', () => {
    const block = {
      blockType: 'AiCrudPage',
      fieldRefs: ['id', 'fieldInput', 'fieldTreeSelect'],
      props: { searchFieldRefs: ['id', 'fieldTreeSelect'] },
    }
    expect(resolveSelectedFieldRefs(block, 'search', fields)).toEqual(['id', 'fieldTreeSelect'])
  })

  it('writes search refs from searchable baseline when property is missing', () => {
    const block = {
      blockType: 'AiCrudPage',
      fieldRefs: ['id', 'fieldTreeSelect'],
      props: {},
    }
    expect(resolveSearchFieldRefsForWrite(block, fields)).toEqual(['fieldInput', 'fieldNumber'])
  })
})

describe('resolveDefaultSearchComponentType', () => {
  it('keeps treeSelect even when storage type is bigint', () => {
    expect(resolveDefaultSearchComponentType({
      field: 'parentId',
      componentType: 'treeSelect',
      dataType: 'bigint',
    })).toBe('treeSelect')
  })

  it('falls back to number only for plain numeric fields without UI component type', () => {
    expect(resolveDefaultSearchComponentType({
      field: 'amount',
      dataType: 'decimal',
    })).toBe('number')
  })

  it('labels search component from form field type', () => {
    expect(resolveSearchComponentLabel({
      componentType: 'treeSelect',
      dataType: 'bigint',
    })).toBe('树形选择')
  })
})
