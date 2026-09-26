import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it, vi } from 'vitest'
import {
  createApplicationRuntimeLoadCoordinator,
  prefetchApplicationRuntimeChunk,
  resolveApplicationRuntimeLoadKey,
  shouldUseApplicationWorkspaceLoad,
} from '../application-runtime-load'

describe('application runtime route loading', () => {
  it('exposes a stable runtime chunk prefetch helper', async () => {
    expect(typeof prefetchApplicationRuntimeChunk).toBe('function')
    const first = prefetchApplicationRuntimeChunk()
    const second = prefetchApplicationRuntimeChunk()
    expect(first).toBe(second)
    await first
  })
  it('coalesces repeated notifications for the same route state', async () => {
    let release
    const load = vi.fn(() => new Promise((resolve) => {
      release = resolve
    }))
    const coordinator = createApplicationRuntimeLoadCoordinator(load)

    const first = coordinator.run('hr_apply|draft')
    const second = coordinator.run('hr_apply|draft')
    release()
    await Promise.all([first, second])
    await coordinator.run('hr_apply|draft')

    expect(load).toHaveBeenCalledTimes(1)
  })

  it('runs only the latest different route queued during a load', async () => {
    const releases = []
    const load = vi.fn(() => new Promise(resolve => releases.push(resolve)))
    const coordinator = createApplicationRuntimeLoadCoordinator(load)

    const task = coordinator.run('app-a|draft')
    coordinator.run('app-b|draft')
    coordinator.run('app-b|runtime')
    releases.shift()()
    await Promise.resolve()
    releases.shift()()
    await task

    expect(load.mock.calls.map(call => call[0])).toEqual([
      'app-a|draft',
      'app-b|runtime',
    ])
  })

  it('keeps page selection out of the workspace load key', () => {
    const first = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_1', draft: '1' },
    })
    const second = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_2', draft: '1' },
    })

    expect(second).toBe(first)
  })

  it('does not reload workspace when editors toggle edit or draft query', () => {
    const pageManagement = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_1' },
    }, true)
    const pageDesign = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_1', edit: '1' },
    }, true)
    const draftPreview = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_1', draft: '1' },
    }, true)

    expect(pageDesign).toBe(pageManagement)
    expect(draftPreview).toBe(pageManagement)
  })

  it('uses workspace for edit, draft preview, or editors in page management', () => {
    expect(shouldUseApplicationWorkspaceLoad({ query: {} })).toBe(false)
    expect(shouldUseApplicationWorkspaceLoad({ query: { pageId: 'page_1' } })).toBe(false)
    expect(shouldUseApplicationWorkspaceLoad({ query: { pageId: 'page_1' } }, true)).toBe(true)
    expect(shouldUseApplicationWorkspaceLoad({ query: { edit: '1' } })).toBe(true)
    expect(shouldUseApplicationWorkspaceLoad({ query: { draft: '1' } })).toBe(true)
  })

  it('loads workspace for editors in page management; published runtime only for viewers', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    expect(runtimeSource).toContain('businessApplicationRuntimeByCode')
    expect(runtimeSource).toContain('shouldUseApplicationWorkspaceLoad(route, canEditApplication.value)')
    // 正式门户只有编辑/草稿模式才 designPreview；有编辑权限也看已发布配置，避免与普通用户不一致。
    // configurable 必须为 false，否则 GridBlockRenderer 会按设计态画虚线边框。
    expect(runtimeSource).toContain(':design-preview="editing || isDraftMode"')
    expect(runtimeSource).not.toContain(':design-preview="editing || isDraftMode || canEditApplication"')
    expect(runtimeSource).toContain(':configurable="false"')
    expect(runtimeSource).toContain(':crud-config-revision="portalCrudConfigRevision"')
    expect(runtimeSource).toContain("import.meta.glob('/src/assets/images/form/*.png', { import: 'default' })")
    expect(runtimeSource).not.toContain('eager: true')
  })

  it('includes editor workspace access in the load key', () => {
    const withoutEdit = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_1' },
    }, false)
    const withEdit = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { pageId: 'page_1' },
    }, true)
    expect(withEdit).not.toBe(withoutEdit)
  })

  it('does not reload when an editor enters page design', () => {
    const pageManagement = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: {},
    }, true)
    const editing = resolveApplicationRuntimeLoadKey({
      params: { applicationCode: 'hr_apply' },
      query: { edit: '1', pageId: 'page_new', designTab: 'page' },
    }, true)
    expect(editing).toBe(pageManagement)
  })

  it('invalidates portal crud cache after form save refresh', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const portalSource = readFileSync(resolve('src/views/app-center/components/portal/PortalPageRenderer.vue'), 'utf8')
    expect(runtimeSource).toContain('portalCrudConfigRevision.value += 1')
    expect(runtimeSource).toContain('normalizeInAppBuilder(application.value.options, application.value, objects.value)')
    expect(runtimeSource).toContain('if (application.value && syncBuilder)')
    expect(runtimeSource).toContain('if (markClean)')
    expect(runtimeSource).toContain('savedSignature.value = JSON.stringify(builder.value || {})')
    // 换页不强制 remount Portal，靠 revision 与内部缓存失效；同对象跨页复用 render
    expect(runtimeSource).toContain(':key="`portal:${portalCrudConfigRevision}`"')
    expect(portalSource).toContain('crudConfigRevision')
    expect(portalSource).toContain('configRev')
    expect(portalSource).toContain('invalidateAll')
  })

  it('marks draft clean after save refresh mutations to avoid double success toasts', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    expect(runtimeSource).toContain('refreshWorkspaceMetadata({ syncBuilder: true, markClean: true })')
    expect(runtimeSource).toContain('return await saveDraft({ quiet: true })')
    expect(runtimeSource).toContain('bind/hydrate 可能继续改 builder')
    expect(runtimeSource).toContain('async function markBuilderClean')
    expect(runtimeSource).toContain('await markBuilderClean()')
  })

  it('prefetches workspace tab panels after application load for editors', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    expect(runtimeSource).toContain('prefetchRuntimeWorkspacePanels')
    expect(runtimeSource).toContain('requestIdleCallback')
    expect(runtimeSource).toContain('<keep-alive>')
  })

  it('waits for object runtime config before mounting the CRUD page', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const rendererSource = readFileSync(resolve('src/components/lowcode-builder/page/GridBlockRenderer.vue'), 'utf8')
    const portalSource = readFileSync(resolve('src/views/app-center/components/portal/PortalPageRenderer.vue'), 'utf8')

    expect(runtimeSource).toContain(':runtime-crud-loading="isPageBlockRuntimeCrudLoading(block)"')
    expect(runtimeSource).toContain('warmCurrentPortalPageCrud')
    expect(runtimeSource).toContain('seed-runtime-crud-props')
    expect(runtimeSource).toContain('import(\'@/components/ai-form/AiCrudPage.vue\')')
    expect(runtimeSource).toContain('editCanvasBootstrapping')
    expect(runtimeSource).toContain('resolveEntryDesignTab(pageId) === \'page\'')
    expect(portalSource).toContain('seedRuntimeCrudProps')
    expect(portalSource).toContain('portal-content-skeleton')
    expect(rendererSource).toContain('<div v-if="runtimeCrudLoading" class="runtime-crud-loading">')
    expect(rendererSource).toContain('<n-skeleton height="32px" :sharp="false" />')
    expect(rendererSource).not.toContain('<n-spin size="small" />')
    expect(rendererSource).toContain('v-else-if="effectiveRuntimeCrudProps"')
    expect(rendererSource).toContain("defineAsyncComponent(() => import('@/components/ai-form/AiCrudPage.vue'))")
  })

  it('keeps nested tab blocks selectable and configurable from the runtime canvas', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const rendererSource = readFileSync(resolve('src/components/lowcode-builder/page/GridBlockRenderer.vue'), 'utf8')
    const designerSource = readFileSync(resolve('src/components/lowcode-builder/page/ListPageGridDesigner.vue'), 'utf8')

    expect(runtimeSource).toContain('@child-block-select="handleNestedPageBlockSelect"')
    expect(runtimeSource).toContain('@child-block-menu-select="handleNestedPageBlockMenuSelect"')
    expect(runtimeSource).toContain('@child-block-resize-start="handleNestedPageBlockResizeStart"')
    expect(runtimeSource).toContain('findPageBlockInTree(pageBlocks.value, selectedPageBlockId.value)')
    expect(rendererSource).toContain('class="tab-pane-child"')
    expect(rendererSource).toContain('emit(\'childBlockSelect\', child.id)')
    expect(rendererSource).toContain('class="nested-block-menu-trigger"')
    expect(rendererSource).toContain('v-for="anchor in resizeAnchors"')
    expect(designerSource).toContain('if (blockId && findBlockInTree(blocks.value, blockId))')
    expect(designerSource).not.toContain('if (blockId && blocks.value.some(block => block.id === blockId))')
  })
})
