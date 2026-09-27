import { describe, expect, it } from 'vitest'
import { consumeWeComEntry, readWeComConnection, rememberWeComEntry } from '@/utils/wecom-entry'
import { resolveApplicationSettingsSection } from '../components/application-print-entry'

function storage() {
  const values = new Map()
  return { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}
describe('application integration navigation', () => {
  it('recognizes refreshable integration settings deep link', () => {
    expect(resolveApplicationSettingsSection(['integrations'])).toBe('integrations')
    expect(resolveApplicationSettingsSection('printing')).toBe('basic')
    expect(resolveApplicationSettingsSection('unknown')).toBe('basic')
  })
  it('uses the selected connection in history and hash routes', () => {
    expect(readWeComConnection({ search: '?collaborationConnection=legal%20company' }, storage())).toBe('legal company')
    expect(readWeComConnection({ hash: '#/app/legal?collaborationConnection=legal' }, storage())).toBe('legal')
  })
  it('restores the exact application after OAuth callback without replaying code and state', () => {
    const local = storage()
    rememberWeComEntry({ pathname: '/forge/', hash: '#/app/legal?collaborationConnection=wecom' }, local, 'wecom')
    expect(readWeComConnection({ search: '?code=once&state=verified' }, local)).toBe('wecom')
    expect(consumeWeComEntry(local)).toBe('/forge/#/app/legal?collaborationConnection=wecom')
    expect(consumeWeComEntry(local)).toBe('')
  })
  it('never restores external or malformed targets', () => {
    for (const path of ['//evil.test', '/\\evil.test', 'https://evil.test']) {
      const local = storage()
      rememberWeComEntry({ pathname: path }, local, 'a')
      expect(consumeWeComEntry(local)).toBe('')
    }
  })
  it('does not reuse a cached connection on an unrelated visit', () => {
    const local = storage()
    rememberWeComEntry({ pathname: '/app/legal' }, local, 'old')
    expect(readWeComConnection({ search: '' }, local)).toBe('')
  })
})
