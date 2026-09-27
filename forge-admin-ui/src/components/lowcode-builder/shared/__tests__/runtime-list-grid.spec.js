import { describe, expect, it } from 'vitest'
import {
  isRichListGridLayout,
  normalizeRuntimeListGridLayout,
  buildRuntimeListFlowBlocks,
  resolveListGridLayoutFromPageSchema,
  shouldRenderRuntimeListGrid,
} from '../runtime-list-grid'

describe('runtime-list-grid', () => {
  it('detects companion blocks as rich layout', () => {
    expect(isRichListGridLayout({
      items: [
        { id: 'a', blockType: 'AiCrudPage' },
        { id: 'b', blockType: 'info-panel' },
      ],
    })).toBe(true)
    expect(isRichListGridLayout({
      items: [{ id: 'a', blockType: 'AiCrudPage' }],
    })).toBe(false)
    expect(isRichListGridLayout({
      items: [
        { id: 'a', blockType: 'AiCrudPage' },
        { id: 'b', blockType: 'data-table' },
      ],
    })).toBe(false)
  })

  it('normalizes design heights into flow-friendly auto blocks', () => {
    const next = normalizeRuntimeListGridLayout({
      items: [{
        id: 'crud',
        blockType: 'AiCrudPage',
        props: { style: { heightMode: 'full', height: 632, width: 1360 }, previewLiveData: false },
      }],
    })
    expect(next.items[0].props.style.heightMode).toBe('auto')
    expect(next.items[0].props.style.widthMode).toBe('full')
    expect(next.items[0].props.style.height).toBeUndefined()
    expect(next.items[0].props.previewLiveData).toBe(true)
  })

  it('sorts flow blocks by pageFlowY', () => {
    const blocks = buildRuntimeListFlowBlocks({
      items: [
        { id: 'b', blockType: 'calendar', props: { style: { pageFlowY: 200 } } },
        { id: 'a', blockType: 'AiCrudPage', props: { style: { pageFlowY: 0 } } },
      ],
    })
    expect(blocks.map(item => item.id)).toEqual(['a', 'b'])
  })

  it('renders grid for list only when rich', () => {
    const pageSchema = {
      listGridLayout: {
        items: [
          { id: 't', blockType: 'tree-panel' },
          { id: 'c', blockType: 'AiCrudPage' },
        ],
      },
    }
    expect(shouldRenderRuntimeListGrid({ pageSchema, pageKey: 'list' })).toBe(true)
    expect(shouldRenderRuntimeListGrid({
      pageSchema: { listGridLayout: { items: [{ id: 'c', blockType: 'AiCrudPage' }] } },
      pageKey: 'list',
    })).toBe(false)
  })

  it('resolves list page grid from pages[].gridLayout', () => {
    const layout = resolveListGridLayoutFromPageSchema({
      pages: [{ pageKey: 'list', gridLayout: { items: [{ id: 'x', blockType: 'page-title' }] } }],
      listGridLayout: { items: [{ id: 'y', blockType: 'AiCrudPage' }] },
    }, { pageKey: 'list' })
    expect(layout.items[0].id).toBe('x')
  })
})
