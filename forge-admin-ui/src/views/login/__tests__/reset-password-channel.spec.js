import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

function readSource(relativeUrl) {
  return readFileSync(new URL(relativeUrl, import.meta.url), 'utf8')
}

describe('login reset password', () => {
  it('only shows forgot password when recovery channels are enabled', () => {
    const page = readSource('../index.vue')
    const api = readSource('../api.js')
    expect(page).toContain('resetPasswordChannels')
    expect(page).toContain('v-if="canResetPassword"')
    expect(page).toContain('api.sendResetPasswordCode')
    expect(page).toContain('encryptPassword(resetForm.value.newPassword')
    expect(api).toContain('/auth/resetPassword/code')
    expect(api).toContain('/auth/resetPassword')
  })
})
