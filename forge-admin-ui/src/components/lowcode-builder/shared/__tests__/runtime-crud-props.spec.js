import { describe, expect, it } from 'vitest'
import {
  appendDesignPreviewToApiValue,
  applyTableColumnLayout,
  buildCrudSearchTypeRequestParams,
  buildRuntimeCrudProps,
  filterCrudItemsByFieldRefs,
  includeManagedRuntimeFieldRefs,
  isDesignPreviewCrudProps,
  resolveCrudPreviewReloadKey,
  resolveCrudSearchFieldCatalog,
  resolveRuntimeBlockApi,
} from '../runtime-crud-props'

describe('runtime CRUD design preview props', () => {
  it('adds the design preview marker to every draft CRUD endpoint', () => {
    const props = buildRuntimeCrudProps({
      configKey: 'crm_customer',
      apiConfig: {
        list: 'get@/ai/crud/当前配置/page',
        detail: 'get@/ai/crud/当前配置/:id',
        create: 'post@/ai/crud/当前配置',
        update: 'put@/ai/crud/当前配置',
        delete: 'delete@/ai/crud/当前配置/:id?force=false',
      },
    }, { designPreview: true })

    expect(props.designPreview).toBe(true)
    expect(props.apiConfig).toEqual({
      list: 'get@/ai/crud/crm_customer/page?designPreview=1',
      detail: 'get@/ai/crud/crm_customer/:id?designPreview=1',
      create: 'post@/ai/crud/crm_customer?designPreview=1',
      update: 'put@/ai/crud/crm_customer?designPreview=1',
      delete: 'delete@/ai/crud/crm_customer/:id?force=false&designPreview=1',
    })
  })

  it('does not change published runtime endpoints or duplicate the marker', () => {
    const published = buildRuntimeCrudProps({
      configKey: 'crm_customer',
      apiConfig: { list: 'get@/ai/crud/当前配置/page' },
    })

    expect(published.designPreview).toBe(false)
    expect(published.apiConfig.list).toBe('get@/ai/crud/crm_customer/page')
    expect(appendDesignPreviewToApiValue('get@/ai/crud/crm_customer/page?designPreview=1'))
      .toBe('get@/ai/crud/crm_customer/page?designPreview=1')
  })

  it('adds the design preview marker to a block-level base API', () => {
    expect(resolveRuntimeBlockApi(
      'post@/ai/crud/当前配置',
      'crm_customer',
      true,
    )).toBe('post@/ai/crud/crm_customer?designPreview=1')
    expect(resolveRuntimeBlockApi(
      'post@/ai/crud/当前配置?designPreview=1',
      'crm_customer',
      true,
    )).toBe('post@/ai/crud/crm_customer?designPreview=1')
  })

  it('treats both compiled draft config and designer fallback as design preview', () => {
    expect(isDesignPreviewCrudProps({ designPreview: true })).toBe(true)
    expect(isDesignPreviewCrudProps({ draftOnly: true })).toBe(true)
    expect(isDesignPreviewCrudProps({ designPreview: false })).toBe(false)
  })

  it('uses explicit search field refs independently from table columns', () => {
    const fields = [
      { field: 'id', label: 'ID' },
      { field: 'customerName', label: '客户名称' },
      { field: 'orderNo', label: '订单号' },
    ]

    expect(resolveCrudSearchFieldCatalog(fields, {
      fieldRefs: ['id'],
      props: { searchFieldRefs: ['customerName', 'orderNo'] },
    }).map(field => field.field)).toEqual(['customerName', 'orderNo'])
  })

  it('applies page-level query type, component and field mapping to the runtime schema', () => {
    const fields = [
      { field: 'customerName', label: '客户名称', componentType: 'input', queryType: 'like' },
      { field: 'orderNo', label: '订单号', componentType: 'input', queryType: 'eq' },
    ]

    expect(resolveCrudSearchFieldCatalog(fields, {
      fieldRefs: ['customerName'],
      props: {
        searchFieldRefs: ['customerName'],
        searchFieldSettings: {
          customerName: {
            queryType: 'like',
            componentType: 'input',
            queryField: 'orderNo',
          },
        },
      },
    })).toEqual([expect.objectContaining({
      field: 'orderNo',
      fieldCode: 'orderNo',
      sourceField: 'customerName',
      label: '客户名称',
      queryType: 'like',
      componentType: 'input',
    })])
  })

  it('serializes only supported page query operators as dynamic CRUD control metadata', () => {
    expect(buildCrudSearchTypeRequestParams([
      { field: 'customerName', queryType: 'like' },
      { field: 'createdAt', queryType: 'between' },
      { field: 'unsafeField', queryType: 'drop table' },
      { field: '', queryType: 'eq' },
    ])).toEqual({
      _searchTypes: JSON.stringify({
        customerName: 'like',
        createdAt: 'between',
      }),
    })
  })

  it('keeps an explicitly empty search field selection empty', () => {
    const fields = [
      { field: 'id', label: 'ID' },
      { field: 'customerName', label: '客户名称' },
    ]

    expect(resolveCrudSearchFieldCatalog(fields, {
      fieldRefs: ['id', 'customerName'],
      props: { searchFieldRefs: [] },
    })).toEqual([])
  })

  it('falls back to list field refs only for legacy blocks without search field refs', () => {
    const fields = [
      { field: 'id', label: 'ID' },
      { field: 'customerName', label: '客户名称' },
    ]

    expect(resolveCrudSearchFieldCatalog(fields, {
      fieldRefs: ['customerName'],
      props: {},
    }).map(field => field.field)).toEqual(['customerName'])
  })

  it('keeps the preview reload key stable when only preview result state changes', () => {
    const source = {
      blockType: 'AiCrudPage',
      props: {
        previewLiveData: true,
        previewMode: 'realList',
        previewRecordId: '1001',
        listApi: 'get@/ai/crud/order/page',
        lastPreviewStatus: 'loading',
        lastPreviewMessage: '正在请求真实接口预览',
      },
    }
    const completed = {
      ...source,
      props: {
        ...source.props,
        lastPreviewStatus: 'success',
        lastPreviewMessage: '接口预览成功，读取 10 条数据',
      },
    }

    expect(resolveCrudPreviewReloadKey(completed)).toBe(resolveCrudPreviewReloadKey(source))
  })

  it('handles missing runtime CRUD props in static designer preview', () => {
    expect(resolveCrudPreviewReloadKey({ props: {} }, null)).toBe(JSON.stringify({
      enabled: false,
      mode: 'mock',
      recordId: '',
      listApi: '',
    }))
  })

  it('keeps action columns while dropping deleted form fields from runtime schemas', () => {
    expect(filterCrudItemsByFieldRefs([
      { field: 'fieldSlider', title: '滑块' },
      { field: 'fieldNumber', title: '数字' },
      { key: 'action', type: 'action', title: '操作' },
    ], ['fieldSlider']).map(item => item.field || item.key)).toEqual(['fieldSlider', 'action'])
  })

  it('keeps a managed flow status column when the application block has an older field snapshot', () => {
    expect(includeManagedRuntimeFieldRefs(
      ['fieldRate'],
      [
        { field: 'fieldRate', listVisible: true },
        {
          field: 'flowStatus',
          listVisible: true,
          fieldStatus: 'ENABLED',
          advancedProps: { managedBy: 'BUSINESS_FLOW' },
        },
      ],
    )).toEqual(['fieldRate', 'flowStatus'])

    expect(includeManagedRuntimeFieldRefs(
      ['fieldRate'],
      [{
        field: 'flowStatus',
        listVisible: true,
        advancedProps: { managedBy: 'BUSINESS_FLOW' },
      }],
      { flowStatus: { visible: false } },
    )).toEqual(['fieldRate'])
  })

  it('changes the preview reload key only when a real request condition changes', () => {
    const source = {
      props: {
        previewLiveData: true,
        previewMode: 'realList',
        previewRecordId: '1001',
        listApi: 'get@/ai/crud/order/page',
      },
    }
    const sourceKey = resolveCrudPreviewReloadKey(source)

    expect(resolveCrudPreviewReloadKey({
      props: { ...source.props, previewRecordId: '1002' },
    })).not.toBe(sourceKey)
    expect(resolveCrudPreviewReloadKey({
      props: { ...source.props, listApi: 'get@/ai/crud/order-v2/page' },
    })).not.toBe(sourceKey)
    expect(resolveCrudPreviewReloadKey({
      props: { ...source.props, previewLiveData: false },
    })).not.toBe(sourceKey)
  })

  it('keeps table row spacing and applies block alignment over compiled columns', () => {
    const props = buildRuntimeCrudProps({
      options: { tableRowGap: 18 },
      columnsSchema: [
        { prop: 'name', label: '名称', align: 'left', titleAlign: 'left' },
        { prop: 'status', label: '状态' },
      ],
    })
    const columns = applyTableColumnLayout(props.columns, {
      globalAlign: 'center',
      fieldSettings: { status: { align: 'right' } },
    })

    expect(props.tableRowGap).toBe(18)
    expect(columns).toEqual([
      expect.objectContaining({ prop: 'name', align: 'center', titleAlign: 'center' }),
      expect.objectContaining({ prop: 'status', align: 'right', titleAlign: 'right' }),
    ])
  })

  it('passes compiled process runtime actions through to AiCrudPage', () => {
    const props = buildRuntimeCrudProps({
      objectCode: 'order',
      options: {
        runtimeActions: [{ key: 'startProcess:submit_approval', actionType: 'START_PROCESS' }],
        detailActions: [{ key: 'startProcess:submit_approval:detail', position: 'detail' }],
        formActions: [{ key: 'startProcess:submit_approval:form', position: 'form' }],
      },
    })
    expect(props.businessObjectCode).toBe('order')
    expect(props.runtimeActions).toEqual([
      { key: 'startProcess:submit_approval', actionType: 'START_PROCESS' },
    ])
    expect(props.detailActions).toEqual([
      { key: 'startProcess:submit_approval:detail', position: 'detail' },
    ])
    expect(props.formActions).toEqual([
      { key: 'startProcess:submit_approval:form', position: 'form' },
    ])
  })
})
