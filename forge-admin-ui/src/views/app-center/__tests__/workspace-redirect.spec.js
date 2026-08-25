import { describe, expect, it } from 'vitest'
import { resolveDeprecatedWorkspaceLocation } from '../workspace-redirect'

describe('deprecated workspace redirect', () => {
  it('sends the default workspace path to page management', () => {
    expect(resolveDeprecatedWorkspaceLocation({
      params: { applicationCode: 'crm' },
      query: {},
    })).toEqual({
      name: 'BusinessApplicationRuntime',
      params: { applicationCode: 'crm' },
      query: {},
    })
  })

  it('maps publish and permission sections to dedicated pages', () => {
    expect(resolveDeprecatedWorkspaceLocation({
      params: { applicationCode: 'crm' },
      query: { section: 'releases', publish: '1' },
    })).toMatchObject({
      name: 'BusinessApplicationPublish',
      params: { applicationCode: 'crm' },
    })
    expect(resolveDeprecatedWorkspaceLocation({
      params: { applicationCode: 'crm' },
      query: { section: 'permissions' },
    })).toEqual({
      name: 'BusinessApplicationSettings',
      params: { applicationCode: 'crm' },
      query: { section: 'permission' },
    })
  })
})
