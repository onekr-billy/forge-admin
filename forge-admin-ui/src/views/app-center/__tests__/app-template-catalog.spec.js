import { describe, expect, it } from 'vitest'
import {
  APPLICATION_TEMPLATE_CATALOG,
  buildTemplateInitializePayload,
  filterApplicationTemplates,
  findApplicationTemplate,
} from '../components/create/app-template-catalog'

describe('application template catalog', () => {
  it('filters official templates by name, category, and description', () => {
    expect(filterApplicationTemplates('客户', 'official').map(item => item.key))
      .toContain('customer-management')
    expect(filterApplicationTemplates('进销存', 'official').length).toBeGreaterThanOrEqual(2)
    expect(filterApplicationTemplates('不存在的场景', 'official')).toEqual([])
  })

  it('resolves a template and builds a detached initialization payload', () => {
    const template = findApplicationTemplate('order-management')
    const payload = buildTemplateInitializePayload(template)

    expect(template.templateCode).toBe('MASTER_DETAIL')
    expect(payload.templateCode).toBe('MASTER_DETAIL')
    expect(payload.details).toHaveLength(1)
    expect(payload).not.toBe(template.initialization)
  })

  it('keeps stable unique public template keys', () => {
    const keys = APPLICATION_TEMPLATE_CATALOG.map(item => item.key)
    expect(new Set(keys).size).toBe(keys.length)
  })
})
