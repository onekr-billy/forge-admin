import assert from 'node:assert/strict'
import test from 'node:test'
import { sanitizeMessageHtml } from '../message-html.js'

test('mini-program fallback renders message text without executable markup', () => {
  const result = sanitizeMessageHtml('<p>通知</p><script>alert(1)</script><img src=x onerror=alert(2)>')
  assert.match(result, /通知/)
  assert.doesNotMatch(result, /script|img|onerror|alert/)
})
