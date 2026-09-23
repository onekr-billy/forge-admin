import { describe, expect, it } from 'vitest'
import {
  DEFAULT_EMPTY_STATE_PLACEHOLDER,
  DEFAULT_INFO_PANEL_PLACEHOLDER,
  isDefaultEmptyStatePlaceholder,
  isDefaultInfoPanelPlaceholder,
  isRuntimeAutoHeightBlock,
  resolvePortalPageBlocks,
  shouldUseContentSizedFlow,
} from '../portal-page-runtime-layout'

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
    expect(isRuntimeAutoHeightBlock({
      blockType: 'AiCrudPage',
      props: { formOnly: false, objectRef: { pageKey: 'form', pageMode: 'form' } },
    })).toBe(false)
    expect(isRuntimeAutoHeightBlock({
      blockType: 'AiCrudPage',
      props: { objectRef: { pageKey: 'form', pageMode: 'list' } },
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

  it('hides untouched info-panel and empty-state placeholders on object pages', () => {
    const objectRef = { objectId: '1', objectCode: 'demo', configKey: 'demo' }
    const blocks = resolvePortalPageBlocks({
      node: { id: 'page_1', pageType: 'object', objectRef },
      page: {
        layout: {
          gridLayout: {
            items: [
              {
                id: 'tip_1',
                blockType: 'info-panel',
                props: { ...DEFAULT_INFO_PANEL_PLACEHOLDER },
              },
              {
                id: 'empty_1',
                blockType: 'empty-state',
                props: { ...DEFAULT_EMPTY_STATE_PLACEHOLDER, actionText: '新建数据' },
              },
            ],
          },
        },
      },
      resolveObjectRef: source => source?.objectRef || objectRef,
    })
    expect(blocks).toHaveLength(1)
    expect(blocks[0]).toMatchObject({
      blockType: 'AiCrudPage',
      props: { objectRef },
    })
    expect(isDefaultInfoPanelPlaceholder({
      blockType: 'info-panel',
      props: { ...DEFAULT_INFO_PANEL_PLACEHOLDER },
    })).toBe(true)
    expect(isDefaultEmptyStatePlaceholder({
      blockType: 'empty-state',
      props: { ...DEFAULT_EMPTY_STATE_PLACEHOLDER },
    })).toBe(true)
  })

  it('keeps customized info-panel copy on object pages that already have CRUD', () => {
    const objectRef = { objectId: '1', configKey: 'demo' }
    const blocks = resolvePortalPageBlocks({
      node: { id: 'page_1', pageType: 'object', objectRef },
      page: {
        layout: {
          gridLayout: {
            items: [
              {
                id: 'tip_1',
                blockType: 'info-panel',
                props: { title: '报名须知', content: '请先核对身份信息。' },
              },
              {
                id: 'crud_1',
                blockType: 'AiCrudPage',
                props: { objectRef },
              },
            ],
          },
        },
      },
      resolveObjectRef: () => objectRef,
    })
    expect(blocks.map(item => item.blockType)).toEqual(['info-panel', 'AiCrudPage'])
  })

  it('keeps intro pages that only use the default info-panel', () => {
    const blocks = resolvePortalPageBlocks({
      node: { id: 'page_intro', pageType: 'content' },
      page: {
        layout: {
          gridLayout: {
            items: [{
              id: 'tip_1',
              blockType: 'info-panel',
              props: { ...DEFAULT_INFO_PANEL_PLACEHOLDER },
            }],
          },
        },
      },
      resolveObjectRef: () => null,
    })
    expect(blocks).toHaveLength(1)
    expect(blocks[0].blockType).toBe('info-panel')
  })
})
