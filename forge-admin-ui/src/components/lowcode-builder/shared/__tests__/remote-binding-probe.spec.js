import { describe, expect, it } from 'vitest'
import {
  buildRemoteFieldSelectOptions,
  extractJsonFieldPaths,
  getNestedJsonValue,
  resolveProbeSlotSource,
} from '../remote-binding-probe'

describe('remote-binding-probe', () => {
  const sample = {
    code: 0,
    data: {
      title: '订单',
      list: [
        { name: '创建', time: '09:00', content: '发起' },
        { name: '审批', time: '10:00', content: '通过' },
      ],
    },
  }

  it('extracts nested field paths', () => {
    const paths = extractJsonFieldPaths(sample)
    expect(paths.some(item => item.path === 'data')).toBe(true)
    expect(paths.some(item => item.path === 'data.list' && item.type === 'array')).toBe(true)
    expect(paths.some(item => item.path === 'data.list.name')).toBe(true)
  })

  it('resolves slot source from array dataPath', () => {
    const source = resolveProbeSlotSource(sample, 'data.list')
    expect(source).toEqual({ name: '创建', time: '09:00', content: '发起' })
    const options = buildRemoteFieldSelectOptions(
      extractJsonFieldPaths(source, { includeContainers: false }),
      { onlyLeaves: true },
    )
    expect(options.map(item => item.value)).toEqual(expect.arrayContaining(['name', 'time', 'content']))
  })

  it('reads nested values', () => {
    expect(getNestedJsonValue(sample, 'data.title')).toBe('订单')
  })
})
