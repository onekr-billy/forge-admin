import { describe, expect, it } from 'vitest'
import {
  applyFieldLinkagesToFormSchema,
  buildLegacyLinkageSchema,
  resolveFormFieldLinkages,
} from '../field-linkage-config'

describe('field linkage form governance bridge', () => {
  it('loads historical linkageSchema into the application event configuration', () => {
    const legacy = {
      schemaVersion: 'linkage-schema-v1',
      rules: [{ ruleId: 'province_city', type: 'linkedDict', sourceField: 'province', targetField: 'city' }],
    }

    expect(resolveFormFieldLinkages({}, legacy)).toEqual(legacy.rules)
  })

  it('prefers application form linkages and writes them back without mutating the source', () => {
    const schema = {
      formKey: 'order',
      settings: { governance: { fieldEvents: [{ id: 'lookup' }] } },
    }
    const rules = [{ ruleId: 'clear_store', type: 'clear', sourceField: 'region', targetField: 'store' }]
    const next = applyFieldLinkagesToFormSchema(schema, rules)

    expect(next.settings.governance).toEqual({
      fieldEvents: [{ id: 'lookup' }],
      fieldLinkages: rules,
    })
    expect(schema.settings.governance).not.toHaveProperty('fieldLinkages')
    expect(resolveFormFieldLinkages(next, { rules: [{ ruleId: 'legacy' }] })).toEqual(rules)
  })

  it('builds the legacy publish schema from the unified event rules', () => {
    const formSchema = applyFieldLinkagesToFormSchema({}, [
      { ruleId: 'member_store', type: 'objectReference', sourceField: 'memberId', targetField: 'storeId' },
    ])
    const legacy = buildLegacyLinkageSchema(formSchema, { settings: { strict: true }, rules: [{ ruleId: 'old' }] })

    expect(legacy).toEqual({
      schemaVersion: 'linkage-schema-v1',
      settings: { strict: true },
      rules: [{ ruleId: 'member_store', type: 'objectReference', sourceField: 'memberId', targetField: 'storeId' }],
    })
  })
})
