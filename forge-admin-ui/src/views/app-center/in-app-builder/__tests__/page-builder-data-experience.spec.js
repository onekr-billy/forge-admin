import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { inAppPageTemplateCatalog } from '../page-template-catalog'

describe('page builder data experience', () => {
  it('describes every data template as a page-canvas workflow', () => {
    const descriptions = inAppPageTemplateCatalog
      .filter(template => template.dataTemplate)
      .map(template => template.description)

    expect(descriptions).toHaveLength(3)
    expect(descriptions.every(description => description.includes('页面画布'))).toBe(true)
  })

  it('keeps a newly created data template on the page canvas', () => {
    const source = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const createFormStart = source.indexOf('function createFormAssetForPageCrud(pageId)')
    const createFormEnd = source.indexOf('\nfunction resolveNextNavigationTitle', createFormStart)
    const createFormSource = source.slice(createFormStart, createFormEnd)

    expect(createFormSource).toContain('configPanelVisible.value = true')
    expect(createFormSource).toContain('inspectorTab.value = \'data\'')
    expect(createFormSource).not.toContain('formDesignerMode.value = true')
  })

  it('opens the data inspector when a block asks to select its source', () => {
    const source = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')

    expect(source).toContain('@request-data-source="handlePageBlockDataSourceRequest"')
    expect(source).toContain('function handlePageBlockDataSourceRequest(blockId)')
    expect(source).toContain('@click="openSelectedBlockFormDesigner"')
  })

  it('clears object-specific field configuration only when the business object changes', () => {
    const source = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const switchStart = source.indexOf('function updateSelectedPageBlockRuntimeObject(objectId)')
    const switchEnd = source.indexOf('\nfunction createFormAssetForSelectedBlock', switchStart)
    const switchSource = source.slice(switchStart, switchEnd)

    expect(switchSource).toContain('const objectChanged = previousObjectKey !== nextObjectKey')
    expect(switchSource).toContain('fieldRefs: objectChanged ? [] : selectedPageBlock.value.fieldRefs')
    expect(switchSource).toContain('delete nextProps.fieldSettings')
    expect(switchSource).toContain('delete nextProps.searchFieldRefs')
    expect(switchSource).toContain('delete nextProps.searchFieldSettings')
  })

  it('preloads the full block tree and forwards object-scoped resolvers to nested blocks', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const rendererSource = readFileSync(resolve('src/components/lowcode-builder/page/GridBlockRenderer.vue'), 'utf8')

    expect(runtimeSource).toContain('visitPageBlocksInTree(pageBlocks.value, preloadPageBlockCrudRuntimeProps)')
    expect(runtimeSource).toContain('runtimeCrudLoadingObjectIds.has(cacheKey)')
    expect(runtimeSource).toContain(':block-fields-resolver="resolvePageBlockFields"')
    expect(runtimeSource).toContain(':runtime-crud-props-resolver="resolvePageBlockRuntimeCrudProps"')
    expect(runtimeSource).toContain(':data-source-configured-resolver="isPageBlockDataSourceConfigured"')
    expect(rendererSource).toContain(':fields="resolveNestedBlockFields(child)"')
    expect(rendererSource).toContain(':runtime-crud-props="resolveNestedBlockRuntimeCrudProps(child)"')
    expect(rendererSource).toContain(':runtime-crud-loading="resolveNestedBlockRuntimeCrudLoading(child)"')
  })

  it('allows standalone form persistence only in published runtime mode', () => {
    const runtimeSource = readFileSync(resolve('src/views/app-center/application-runtime.[applicationCode].vue'), 'utf8')
    const rendererSource = readFileSync(resolve('src/components/lowcode-builder/page/GridBlockRenderer.vue'), 'utf8')

    expect(runtimeSource).toContain(':runtime-interactive="!editing && !isDraftMode"')
    expect(rendererSource).toContain('@submit="handleAiFormSubmit"')
    expect(rendererSource).toContain('props.block.props?.createApi || props.runtimeCrudProps?.apiConfig?.create')
    expect(rendererSource).toContain('window.$message?.success(\'提交成功\')')
  })
})
