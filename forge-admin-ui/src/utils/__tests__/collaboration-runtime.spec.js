import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  detectCollaborationPlatform,
  normalizeScanContext,
  normalizeScanResult,
  scan,
} from '../collaboration-runtime'

describe('collaboration runtime platform and result protocol', () => {
  it('identifies collaboration containers without trusting globals', () => {
    expect(detectCollaborationPlatform('Mozilla/5.0 wxwork', { userId: 'spoofed' })).toBe('WECHAT_ENTERPRISE')
    expect(detectCollaborationPlatform('DingTalk/7.0')).toBe('DINGTALK')
    expect(detectCollaborationPlatform('Lark/1.0')).toBe('FEISHU')
    expect(detectCollaborationPlatform('Mozilla/5.0 iPhone Mobile')).toBe('H5')
    expect(detectCollaborationPlatform('Mozilla/5.0 Macintosh')).toBe('BROWSER')
  })

  it('normalizes supported scan callback shapes and rejects unsafe values', () => {
    expect(normalizeScanResult('  ABC-001  ', 'H5')).toEqual({
      value: 'ABC-001',
      type: 'UNKNOWN',
      platform: 'H5',
    })
    expect(normalizeScanResult({ resultStr: 'A', scanType: 'qrCode' }, 'WECHAT_ENTERPRISE')).toMatchObject({
      value: 'A',
      type: 'qrCode',
      platform: 'WECHAT_ENTERPRISE',
    })
    expect(normalizeScanResult({ codeString: 'B' }, 'DINGTALK').value).toBe('B')
    expect(normalizeScanResult({ value: 'C', type: 'barCode' }, 'FEISHU').type).toBe('barCode')
    expect(normalizeScanContext({ value: 'D', type: 'qrCode', platform: 'H5' })).toEqual({
      value: 'D',
      type: 'qrCode',
      platform: 'H5',
    })
    expect(() => normalizeScanResult({ value: '   ' })).toThrowError()
    expect(() => normalizeScanResult({ value: 'x'.repeat(2049) })).toThrowError()
  })
})

describe('collaboration runtime scan adapters', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('prefers an injected scanner and resolves only once', async () => {
    const scanner = vi.fn(() => Promise.resolve({ value: ' 123 ', type: 'barCode' }))
    await expect(scan({ platform: 'H5', scanner })).resolves.toEqual({
      value: '123',
      type: 'barCode',
      platform: 'H5',
    })
    expect(scanner).toHaveBeenCalledWith(expect.objectContaining({ platform: 'H5', timeoutMs: 30000 }))
  })

  it('adapts the enterprise WeCom callback API', async () => {
    const wx = {
      scanQRCode: vi.fn(({ success }) => success({ resultStr: 'PAY-001', scanType: 'qrCode' })),
    }
    await expect(scan({ userAgent: 'wxwork', globals: { wx } })).resolves.toMatchObject({
      value: 'PAY-001',
      type: 'qrCode',
      platform: 'WECHAT_ENTERPRISE',
    })
    expect(wx.scanQRCode).toHaveBeenCalledWith(expect.objectContaining({ needResult: 1 }))
  })

  it('returns stable unsupported, timeout and cancelled errors', async () => {
    await expect(scan({ platform: 'BROWSER' })).rejects.toMatchObject({ code: 'SCAN_UNSUPPORTED' })

    const timeoutPromise = scan({ platform: 'H5', timeoutMs: 1000, scanner: () => new Promise(() => {}) })
    const timeoutExpectation = expect(timeoutPromise).rejects.toMatchObject({ code: 'SCAN_TIMEOUT' })
    await vi.advanceTimersByTimeAsync(1000)
    await timeoutExpectation

    const controller = new AbortController()
    const cancelledPromise = scan({ platform: 'H5', scanner: () => new Promise(() => {}), signal: controller.signal })
    const cancelledExpectation = expect(cancelledPromise).rejects.toMatchObject({ code: 'SCAN_CANCELLED' })
    controller.abort()
    await cancelledExpectation
  })

  it('maps host cancellation and ignores a late callback', async () => {
    let resolveScanner
    const scanner = vi.fn(() => new Promise((resolve) => {
      resolveScanner = resolve
    }))
    const promise = scan({ platform: 'H5', scanner })
    resolveScanner({ value: 'FIRST' })
    await expect(promise).resolves.toMatchObject({ value: 'FIRST' })
    resolveScanner({ value: 'LATE' })
    expect(scanner).toHaveBeenCalledTimes(1)
  })
})
