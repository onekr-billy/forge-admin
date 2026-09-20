import { describe, expect, it, vi } from 'vitest'
import { buildRuntimeCrudBlockProps } from '../../page/runtime-crud-block-props'
import { resolveCrudFormOnly, resolveCrudPagePresentation } from '../runtime-crud-page-mode'
import { buildRuntimeCrudProps } from '../runtime-crud-props'

describe('runtime CRUD page shape', () => {
  it.each([
    [{ formOnly: true }, {}, true],
    [{ formOnly: false, objectRef: { pageMode: 'form' } }, {}, false],
    [{ objectRef: { pageMode: 'form' } }, {}, true],
    [{ objectRef: { pageMode: 'list', pageKey: 'form' } }, { formOnly: true }, false],
    [{ objectRef: { pageMode: 'crud' } }, { formOnly: true }, false],
    [{ objectRef: { pageKey: 'form' } }, {}, true],
    [{}, { formOnly: true }, true],
    [{}, {}, false],
    [null, null, false],
    [{ businessObjectRef: { pageMode: 'form' } }, {}, true],
    [{ runtimeObjectRef: { pageMode: 'list-form' } }, { formOnly: true }, false],
  ])('resolves block presentation without leaking object defaults: %o', (block, runtime, expected) => {
    expect(resolveCrudFormOnly(block, runtime)).toBe(expected)
  })

  it('preserves optional form-only configuration from runtime and block overrides', () => {
    const runtime = buildRuntimeCrudProps({ options: { formOnly: true, formOnlySubmitText: '提交申请' } })
    expect(resolveCrudPagePresentation({}, runtime)).toMatchObject({ formOnly: true, formOnlySubmitText: '提交申请' })
    expect(resolveCrudPagePresentation({ formOnlySubmitText: '保存登记' }, runtime).formOnlySubmitText).toBe('保存登记')
    expect(resolveCrudPagePresentation({})).not.toHaveProperty('formOnlyTitle')
    expect(resolveCrudPagePresentation(null, null)).toEqual({ formOnly: false })
  })

  it('blocks static form submission without blocking interactive draft runtime hooks', () => {
    const runtimeProps = buildRuntimeCrudProps({ configKey: 'test_order' }, { designPreview: true })
    const preventStaticCrudSubmit = vi.fn(() => false)
    const beforeSubmit = vi.fn(data => ({ ...data, prepared: true }))
    const afterSubmit = vi.fn()
    const context = { source: 'runtime' }
    const args = {
      runtimeProps,
      blockProps: { formOnly: true },
      extensionHooks: { beforeSubmit, afterSubmit },
      extensionRuntimeApi: () => context,
      preventStaticCrudSubmit,
    }
    const preview = buildRuntimeCrudBlockProps(args)
    expect(preview.lazy).toBe(true)
    expect(preview.beforeSubmit({ name: '测试' })).toBe(false)
    expect(beforeSubmit).not.toHaveBeenCalled()

    const runtime = buildRuntimeCrudBlockProps({ ...args, runtimeInteractive: true })
    expect(runtime.lazy).toBe(false)
    expect(runtime.beforeSubmit({ name: '测试' })).toEqual({ name: '测试', prepared: true })
    expect(beforeSubmit).toHaveBeenCalledWith({ name: '测试' }, context)
    runtime.afterSubmit({ id: 'record-1' })
    expect(afterSubmit).toHaveBeenCalledWith({ id: 'record-1' }, context)
  })

  it('keeps form/list/list-form blocks independent when they share a compiled object', () => {
    const runtimeProps = buildRuntimeCrudProps({
      configKey: 'test_order',
      apiConfig: { list: 'get@/ai/crud/test_order/page', create: 'post@/ai/crud/test_order' },
      editSchema: [{ field: 'name', label: '名称', type: 'input' }],
      columnsSchema: [{ prop: 'name', label: '名称' }],
      options: { formOpenMode: 'flat' },
    }, { designPreview: true })
    const snapshot = JSON.stringify(runtimeProps)
    const results = ['form', 'list', 'crud'].map(pageMode => buildRuntimeCrudBlockProps({
      runtimeProps,
      blockProps: { objectRef: { pageMode } },
      runtimeInteractive: true,
    }))
    expect(results.map(item => item.formOnly)).toEqual([true, false, false])
    for (const item of results) {
      expect(item.editSchema).toHaveLength(1)
      expect(item.columns).toHaveLength(1)
      expect(item.apiConfig.create).toBe('post@/ai/crud/test_order?designPreview=1')
      expect(item.formOpenMode).toBe('flat')
      expect(item.lazy).toBe(false)
    }
    expect(JSON.stringify(runtimeProps)).toBe(snapshot)
  })
})
