import { describe, expect, it } from 'vitest'
import { escapeHtml, sanitizeHtml } from '../sanitize-html'

describe('sanitizeHtml', () => {
  it('strips script tags and event handlers', () => {
    const dirty = '<p onclick="alert(1)">hello<script>alert(2)</script></p>'
    const clean = sanitizeHtml(dirty)
    expect(clean).toContain('hello')
    expect(clean.toLowerCase()).not.toContain('<script')
    expect(clean.toLowerCase()).not.toContain('onclick')
  })

  it('blocks javascript urls', () => {
    const clean = sanitizeHtml('<a href="javascript:alert(1)">x</a>')
    expect(clean.toLowerCase()).not.toContain('javascript:')
  })

  it('escapes plain text for highlight', () => {
    expect(escapeHtml('<img src=x onerror=alert(1)>')).toBe(
      '&lt;img src=x onerror=alert(1)&gt;',
    )
  })
})
