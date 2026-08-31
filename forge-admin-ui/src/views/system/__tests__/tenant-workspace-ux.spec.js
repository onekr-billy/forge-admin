import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

function readSource(relativeUrl) {
  return readFileSync(new URL(relativeUrl, import.meta.url), 'utf8')
}

describe('tenant workspace UX', () => {
  it('opens a workspace picker dialog after password login when the account has more than one workspace', () => {
    const source = readSource('../../login/index.vue')
    expect(source).toContain('showWorkspaceModal')
    expect(source).toContain('LOGIN_TENANT_SELECTION_REQUIRED = 4091')
    expect(source).toContain('applyWorkspaceChallenge')
    expect(source).toContain('选择工作区')
    expect(source).toContain('confirmWorkspace')
    expect(source).not.toContain('await loadLoginTenantOptions()')
    expect(source).not.toContain('请选择登录租户')
    expect(source).not.toContain('$message.warning(\'该账号可进入多个工作区，请选择后再登录\')')
  })

  it('hides header tenant switcher when the user has only one tenant', () => {
    const source = readSource('../../../layouts/components/TenantSwitcher.vue')
    expect(source).toContain('v-if="switchableTenantCount > 1"')
  })

  it.each([
    '../user.vue',
    '../role.vue',
    '../org.vue',
    '../post.vue',
  ])('does not expose a tenant search or create field in %s', (relativeUrl) => {
    const source = readSource(relativeUrl)
    expect(source).not.toMatch(/label:\s*'所属租户'/)
    expect(source).not.toMatch(/label:\s*'默认租户'/)
    expect(source).not.toMatch(/placeholder:\s*'全部租户'/)
  })
})
