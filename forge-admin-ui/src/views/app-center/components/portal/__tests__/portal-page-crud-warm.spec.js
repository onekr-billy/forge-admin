import { describe, expect, it, vi } from 'vitest'
import { collectPortalPageCrudTargets, warmPortalPageCrudProps } from '../portal-page-crud-warm'

vi.mock('@/api/ai', () => ({
  crudConfigRender: vi.fn(async (configKey, designPreview) => ({
    data: {
      title: `${configKey}-${designPreview ? 'draft' : 'published'}`,
      fieldCatalog: [{ field: 'name', label: '名称' }],
      columns: [],
      searchSchema: [],
      editSchema: [],
      apiConfig: {},
    },
  })),
}))

vi.mock('@/components/lowcode-builder/shared/runtime-crud-props', () => ({
  buildRuntimeCrudProps: (config, options = {}) => ({
    title: config.title,
    fieldCatalog: config.fieldCatalog,
    designPreview: Boolean(options.designPreview),
  }),
}))

describe('portal-page-crud-warm', () => {
  it('collects unique data-block targets from object pages', () => {
    const targets = collectPortalPageCrudTargets({
      node: {
        id: 'page_1',
        type: 'page',
        pageType: 'object',
        objectRef: { objectId: 11, objectCode: 'order', configKey: 'order_cfg', objectName: '订单' },
      },
      page: {
        layout: {
          gridLayout: {
            items: [
              {
                id: 'b1',
                blockType: 'AiCrudPage',
                props: { objectRef: { objectId: 11, objectCode: 'order', configKey: 'order_cfg' } },
              },
              {
                id: 'b2',
                blockType: 'info-panel',
                props: { title: '提示信息', content: '在这里展示当前页面的说明、风险提醒或操作结果。' },
              },
            ],
          },
        },
      },
      objects: [{ id: 11, objectCode: 'order', configKey: 'order_cfg', objectName: '订单' }],
      entries: [{ id: 99, configKey: 'order_cfg' }],
    })
    expect(targets).toHaveLength(1)
    expect(targets[0]).toMatchObject({
      key: '11',
      configKey: 'order_cfg',
      entryId: 99,
    })
  })

  it('warms runtime crud props for collected targets', async () => {
    const seed = await warmPortalPageCrudProps([
      {
        key: '11',
        configKey: 'order_cfg',
        entryId: 99,
        objectRef: { objectName: '订单' },
      },
    ], {
      designPreview: true,
      applicationId: 'app-1',
      pageId: 'page_1',
      timeoutMs: 1000,
    })
    expect(seed['11']).toMatchObject({
      title: 'order_cfg-draft',
      designPreview: true,
    })
  })
})
