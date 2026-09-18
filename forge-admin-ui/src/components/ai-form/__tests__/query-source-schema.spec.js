import { describe, expect, it } from 'vitest'
import { parseQuerySourceInputSchema } from '../query-source-schema'

describe('parseQuerySourceInputSchema', () => {
  it('parses dataset paramSchemaJson array with paramName fields', () => {
    const json = JSON.stringify([
      { paramName: 'mobile', label: '手机号', dataType: 'string', required: true },
      { paramName: 'status', label: '状态', dataType: 'number', required: false },
    ])
    expect(parseQuerySourceInputSchema(json)).toEqual([
      { name: 'mobile', label: '手机号', type: 'string', required: true },
      { name: 'status', label: '状态', type: 'number', required: false },
    ])
  })

  it('parses plain array with name fields', () => {
    const json = JSON.stringify([{ name: 'keyword', label: '关键字' }])
    expect(parseQuerySourceInputSchema(json)).toEqual([
      { name: 'keyword', label: '关键字', type: 'string', required: false },
    ])
  })

  it('parses nested { inputSchema: [...] } shape', () => {
    const json = JSON.stringify({
      inputSchema: [{ name: 'orderNo', type: 'string', required: true }],
    })
    expect(parseQuerySourceInputSchema(json)).toEqual([
      { name: 'orderNo', label: 'orderNo', type: 'string', required: true },
    ])
  })

  it('parses external api JSON Schema properties shape', () => {
    const json = JSON.stringify({
      properties: {
        mobile: { type: 'string', title: '手机号' },
        level: { type: 'number', label: '等级' },
      },
      required: ['mobile'],
    })
    expect(parseQuerySourceInputSchema(json)).toEqual([
      { name: 'mobile', label: '手机号', type: 'string', required: true },
      { name: 'level', label: '等级', type: 'number', required: false },
    ])
  })

  it('accepts already-parsed object input', () => {
    expect(parseQuerySourceInputSchema([{ paramName: 'code' }])).toEqual([
      { name: 'code', label: 'code', type: 'string', required: false },
    ])
  })

  it('drops entries without name or paramName', () => {
    const json = JSON.stringify([{ label: '缺名字' }, null, { paramName: 'ok' }])
    expect(parseQuerySourceInputSchema(json)).toEqual([
      { name: 'ok', label: 'ok', type: 'string', required: false },
    ])
  })

  it('returns empty array for blank or malformed input', () => {
    expect(parseQuerySourceInputSchema('')).toEqual([])
    expect(parseQuerySourceInputSchema(null)).toEqual([])
    expect(parseQuerySourceInputSchema('{not json')).toEqual([])
    expect(parseQuerySourceInputSchema('{}')).toEqual([])
  })
})
