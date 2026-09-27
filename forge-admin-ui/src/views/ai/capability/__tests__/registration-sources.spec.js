import { describe, expect, it } from 'vitest'
import { parseOptions, publishedPageSources, registrationScenario } from '../registrationSources'

const object = { objectId: '9007199254740999', suiteCode: 'law', objectCode: 'matter', configKey: 'matter_config' }
function runtime(blocks, objectRef) {
  return {
    objects: [object],
    application: { options: JSON.stringify({ inAppBuilder: {
      nodes: [{ id: 'page', type: 'page', title: '案件登记', objectRef }],
      pages: { page: { layout: { gridLayout: { items: blocks } } } },
    } }) },
  }
}

describe('published application sources', () => {
  it.each(['null', 'invalid', '[]', 'true'])('tolerates invalid options %s', (value) => {
    expect(parseOptions(value)).toEqual({})
  })
  it('keeps long IDs as strings and resolves bindings in nested tabs', () => {
    const source = runtime([{ id: 'tabs', props: { tabs: [{ children: [{
      id: 'form',
      blockType: 'AiCrudPage',
      props: { businessObjectRef: { objectId: object.objectId } },
    }] }] } }])
    expect(publishedPageSources(source)).toEqual([expect.objectContaining({ objectId: object.objectId, value: 'page/form' })])
  })
  it('does not return duplicate page-level fallback for a bound block', () => {
    expect(publishedPageSources(runtime([{ id: 'form', blockType: 'AiForm' }], { objectId: object.objectId }))).toHaveLength(1)
  })
  it('does not turn an invalid/deleted binding into another object with the same code', () => {
    expect(publishedPageSources(runtime([], { objectId: '9', objectCode: 'matter' }))).toEqual([])
    expect(publishedPageSources(runtime([], { objectId: object.objectId, valid: false }))).toEqual([])
  })
  it('keeps pure presentation pages out of executable sources', () => {
    expect(publishedPageSources(runtime([{ id: 'text', blockType: 'Text' }]))).toEqual([])
    expect(publishedPageSources(null)).toEqual([])
  })
  it.each([
    ['SYSTEM_SERVICE', 'system.rest.invoke', 'rest'],
    ['SYSTEM_SERVICE', 'lowcode.form.create', 'application'],
    ['SYSTEM_SERVICE', 'flow.process.start', 'flow'],
    ['BUSINESS_ACTION', 'law/matter/create', 'application'],
    ['FLOW_ACTION', 'law/matter/WITHDRAW', 'flow'],
  ])('restores scenario %s/%s', (type, key, expected) => {
    expect(registrationScenario(type, key)).toBe(expected)
  })
})
