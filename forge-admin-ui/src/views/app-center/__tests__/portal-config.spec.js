import { describe, expect, it } from 'vitest'
import { buildPortalWatermarkText } from '../components/portal/portal-config'

describe('published portal globalization settings', () => {
  it('uses the configured timezone and date format for watermark time', () => {
    const text = buildPortalWatermarkText({
      watermark: { enabled: true, showUsername: false, showTime: true },
      globalization: { enabled: true, timezone: 'UTC', dateFormat: 'DD/MM/YYYY' },
    }, '', new Date('2026-08-17T23:30:00.000Z'))

    expect(text).toBe('17/08/2026 23:30')
  })
})
