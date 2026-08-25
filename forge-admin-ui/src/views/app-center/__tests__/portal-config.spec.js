import { describe, expect, it } from 'vitest'
import { buildPortalAccessUrls, buildPortalWatermarkText, RESERVED_PORTAL_SLUGS } from '../components/portal/portal-config'

describe('published portal globalization settings', () => {
  it('uses the configured timezone and date format for watermark time', () => {
    const text = buildPortalWatermarkText({
      watermark: { enabled: true, showUsername: false, showTime: true },
      globalization: { enabled: true, timezone: 'UTC', dateFormat: 'DD/MM/YYYY' },
    }, '', new Date('2026-08-17T23:30:00.000Z'))

    expect(text).toBe('17/08/2026 23:30')
  })

  it('builds computer portal urls and reuses the old H5 entry runtime address', () => {
    expect(buildPortalAccessUrls({
      origin: 'http://localhost:3000',
      slug: 'presale_registration_apply',
      configKey: 'presale_registration_business_object',
    })).toEqual({
      path: '/app/presale_registration_apply',
      pcUrl: 'http://localhost:3000/app/presale_registration_apply',
      h5Url: 'http://localhost:3009/#/pages/lowcode-runtime?configKey=presale_registration_business_object',
    })
    expect(buildPortalAccessUrls({
      origin: 'http://localhost:3000',
      slug: 'presale_registration_apply',
      pageId: 'page_page',
      configKey: 'presale_registration_business_object',
      appId: '2089974506884993026',
    })).toEqual({
      path: '/app/presale_registration_apply',
      pcUrl: 'http://localhost:3000/ai/crud-page/presale_registration_business_object?pageKey=list&appId=2089974506884993026',
      h5Url: 'http://localhost:3009/#/pages/lowcode-runtime?appId=2089974506884993026&configKey=presale_registration_business_object',
    })
  })

  it('keeps the reserved portal slug list aligned with the backend contract', () => {
    expect(RESERVED_PORTAL_SLUGS).toContain('admin')
    expect(RESERVED_PORTAL_SLUGS).toContain('app-center')
    expect(RESERVED_PORTAL_SLUGS).toContain('favicon.ico')
  })

  it('builds a standalone form runtime url for an object page', () => {
    const urls = buildPortalAccessUrls({
      origin: 'http://localhost:3000',
      slug: 'presale_registration_apply',
      pageId: 'page_form',
      appId: '2089974506884993026',
      application: {
        options: JSON.stringify({
          inAppBuilder: {
            nodes: [{
              id: 'page_form',
              type: 'page',
              objectRef: {
                configKey: 'presale_registration_business_object',
                pageKey: 'form',
                pageMode: 'form',
                formKey: 'presale_registration_business_object_form',
              },
            }],
          },
        }),
      },
    })

    expect(urls.pcUrl).toBe('http://localhost:3000/ai/crud-page/presale_registration_business_object?pageKey=form&appId=2089974506884993026&formKey=presale_registration_business_object_form&runtimeOpenMode=CREATE_FORM&mode=create')
  })
})
