import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearDictCache, getDictData } from '../useDict'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils', () => ({ request: requestMocks }))

describe('useDict managed cache endpoint', () => {
  beforeEach(() => {
    clearDictCache()
    requestMocks.get.mockReset()
  })

  it('loads business dictionaries through the managed type endpoint', async () => {
    requestMocks.get.mockResolvedValue({
      code: 200,
      data: [
        { dictCode: 2, dictLabel: '停用', dictValue: '0', dictSort: 2 },
        { dictCode: 1, dictLabel: '启用', dictValue: '1', dictSort: 1 },
      ],
    })

    const result = await getDictData('sys/org type', true)

    expect(requestMocks.get).toHaveBeenCalledWith(
      '/system/dict/data/type/sys%2Forg%20type',
      { needTip: false },
    )
    expect(result.map(item => item.value)).toEqual(['1', '0'])
  })
})
