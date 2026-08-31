import { describe, expect, it } from 'vitest'
import { isRuntimeAutoHeightBlock, shouldUseContentSizedFlow } from '../portal-page-runtime-layout'

describe('portal-page-runtime-layout', () => {
  it('treats standalone form pages as auto-height runtime blocks', () => {
    expect(isRuntimeAutoHeightBlock({ blockType: 'AiForm' })).toBe(true)
    expect(isRuntimeAutoHeightBlock({
      blockType: 'AiCrudPage',
      props: { formOnly: true },
    })).toBe(true)
    expect(isRuntimeAutoHeightBlock({
      blockType: 'AiCrudPage',
      props: { objectRef: { pageMode: 'form' } },
    })).toBe(true)
    expect(isRuntimeAutoHeightBlock({
      blockType: 'AiCrudPage',
      props: { objectRef: { pageKey: 'form' } },
    })).toBe(true)
  })

  it('keeps list CRUD pages on the designer canvas height', () => {
    expect(isRuntimeAutoHeightBlock({ blockType: 'AiCrudPage' })).toBe(false)
    expect(isRuntimeAutoHeightBlock({
      blockType: 'AiCrudPage',
      props: { objectRef: { pageMode: 'list' } },
    })).toBe(false)
  })

  it('uses content-sized flow for published form pages, not design preview fill', () => {
    const formBlocks = [{
      blockType: 'AiCrudPage',
      props: { formOnly: true },
    }]
    expect(shouldUseContentSizedFlow(formBlocks)).toBe(true)
    expect(shouldUseContentSizedFlow(formBlocks, { fillHost: true })).toBe(false)
    expect(shouldUseContentSizedFlow([
      { blockType: 'AiCrudPage', props: { formOnly: true } },
      { blockType: 'AiCrudPage' },
    ])).toBe(false)
  })
})
