import { describe, expect, it } from 'vitest'
import {
  appendSelectionLabelContextDefaults,
  buildBindingParams,
  buildContextDefaultsPatch,
  findMissingRequiredParams,
  mapBindingResult,
  normalizeFormInitConfig,
  resolveFormInitRecordId,
  snapshotRuntimeContext,
} from '../data-source-binding-runtime'

describe('data source binding runtime', () => {
  describe('generic binding mappings', () => {
    it('builds params from form field, context path and route query', () => {
      const params = buildBindingParams({
        paramMappings: [
          { param: 'mobile', source: 'FORM_FIELD', field: 'mobile' },
          { param: 'userId', source: 'CONTEXT_PATH', path: 'currentUser.userId' },
          { param: 'orderNo', source: 'ROUTE_QUERY', path: 'orderNo' },
        ],
      }, {
        formData: { mobile: '13800000000' },
        context: { currentUser: { userId: 'u001' } },
        routeQuery: { orderNo: 'A-001' },
      })
      expect(params).toEqual({ mobile: '13800000000', userId: 'u001', orderNo: 'A-001' })
    })

    it('maps results with CLEAR fallback and keeps targets on missing when KEEP', () => {
      const { found, patch } = mapBindingResult({
        resultMode: 'ROOT',
        resultMappings: [
          { from: 'name', to: 'contactName', whenMissing: 'CLEAR' },
          { from: 'dept', to: 'contactDept', whenMissing: 'KEEP' },
        ],
      }, { name: '张三' })
      expect(found).toBe(true)
      expect(patch).toEqual({ contactName: '张三' })
    })

    it('selects first row for FIRST_ROW mode and clears missing targets when not found', () => {
      const { found, patch } = mapBindingResult({
        resultMode: 'FIRST_ROW',
        resultMappings: [{ from: 'name', to: 'contactName', whenMissing: 'CLEAR' }],
      }, { records: [] })
      expect(found).toBe(false)
      expect(patch).toEqual({ contactName: undefined })
    })
  })

  describe('required params guard', () => {
    it('reports only required params whose values are blank', () => {
      const binding = {
        paramMappings: [
          { param: 'mobile', source: 'FORM_FIELD', field: 'mobile', required: true },
          { param: 'channel', source: 'ROUTE_QUERY', path: 'channel', required: true },
          { param: 'userId', source: 'CONTEXT_PATH', path: 'currentUser.userId', required: false },
        ],
      }
      const missing = findMissingRequiredParams(binding, {
        formData: { mobile: '' },
        context: { currentUser: { userId: 'u001' } },
        routeQuery: {},
      })
      expect(missing).toEqual(['mobile', 'channel'])
    })

    it('treats params without the required flag as optional', () => {
      const missing = findMissingRequiredParams({
        paramMappings: [{ param: 'mobile', source: 'FORM_FIELD', field: 'mobile' }],
      }, { formData: {} })
      expect(missing).toEqual([])
    })

    it('keeps zero values as filled', () => {
      const missing = findMissingRequiredParams({
        paramMappings: [{ param: 'amount', source: 'FORM_FIELD', field: 'amount', required: true }],
      }, { formData: { amount: 0 } })
      expect(missing).toEqual([])
    })
  })

  describe('runtime context snapshot', () => {
    it('deep-copies plain values, arrays and strips function references', () => {
      const context = {
        currentUser: { userId: 'u001', roles: ['admin', 'user'] },
        tenantId: 1,
        scanField: () => {},
        formData: { mobile: '138' },
      }
      const snapshot = snapshotRuntimeContext(context)
      expect(snapshot).toEqual({
        currentUser: { userId: 'u001', roles: ['admin', 'user'] },
        tenantId: 1,
        formData: { mobile: '138' },
      })
      // 快照与原对象解耦：原值变化不影响快照
      context.currentUser.userId = 'u002'
      context.currentUser.roles.push('guest')
      expect(snapshot.currentUser.userId).toBe('u001')
      expect(snapshot.currentUser.roles).toEqual(['admin', 'user'])
    })

    it('stops deep-copying beyond the depth limit but still isolates one level', () => {
      const context = { a: { b: { c: { d: { e: 1 } } } } }
      const snapshot = snapshotRuntimeContext(context, 1)
      expect(snapshot.a.b).toEqual({ c: { d: { e: 1 } } })
      // depth=1：a 为深拷贝，b 之后浅拷贝一层（c 与原对象同引用下的 d 仍隔离不了）
      context.a.b.c = null
      expect(snapshot.a.b.c).toEqual({ d: { e: 1 } })
    })

    it('returns empty object for non-plain inputs', () => {
      expect(snapshotRuntimeContext(null)).toEqual({})
      expect(snapshotRuntimeContext('text')).toEqual({})
      expect(snapshotRuntimeContext([1, 2])).toEqual([1, 2])
    })
  })

  describe('form init protocol', () => {
    it('normalizes context defaults and strips unsafe or duplicated entries', () => {
      const config = normalizeFormInitConfig({
        contextDefaults: [
          { id: 'a', field: 'creator', source: 'CONTEXT_PATH', path: 'currentUser.userId' },
          { id: 'b', field: 'creator', source: 'ROUTE_QUERY', path: 'orderNo' },
          { id: 'c', field: 'evil', source: 'CONTEXT_PATH', path: '__proto__.polluted' },
          { id: 'd', field: 'ok', source: 'ROUTE_QUERY', path: 'orderNo', enabled: false },
        ],
      })
      expect(config.contextDefaults).toHaveLength(2)
      expect(config.contextDefaults[0]).toMatchObject({ field: 'creator', source: 'CONTEXT_PATH', enabled: true })
      expect(config.contextDefaults[1]).toMatchObject({ field: 'ok', enabled: false })
    })

    it('normalizes recordLoad keyNames and keeps only safe names', () => {
      const config = normalizeFormInitConfig({
        recordLoad: {
          enabled: true,
          keyNames: ['recordId', 'id', 'recordId', 'bad key', 'currentUser.staffId'],
        },
      })
      expect(config.recordLoad.enabled).toBe(true)
      expect(config.recordLoad.keyNames).toEqual(['recordId', 'id', 'currentUser.staffId'])
    })

    it('fills blank fields with context defaults and skips disabled entries', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'creator', source: 'CONTEXT_PATH', path: 'currentUser.userId', enabled: true },
          { id: '2', field: 'orderNo', source: 'ROUTE_QUERY', path: 'orderNo', enabled: true },
          { id: '3', field: 'dept', source: 'CONTEXT_PATH', path: 'activeOrgId', enabled: false },
        ],
      }
      const patch = buildContextDefaultsPatch(config, {
        formData: { creator: '' },
        context: { currentUser: { userId: 'u001' }, activeOrgId: 'org9' },
        routeQuery: { orderNo: 'A-001' },
      })
      expect(patch).toEqual({ creator: 'u001', orderNo: 'A-001' })
    })

    it('keeps existing values unless they still equal the static field default', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'assignee', source: 'CONTEXT_PATH', path: 'currentUser.realName', enabled: true },
        ],
      }
      const runtime = {
        context: { currentUser: { realName: '张三' } },
        routeQuery: {},
      }
      // 仍是静态默认值 → 允许覆盖为初始化默认值
      expect(buildContextDefaultsPatch(config, {
        ...runtime,
        formData: { assignee: '默认处理人' },
        fieldStaticDefaults: { assignee: '默认处理人' },
      })).toEqual({ assignee: '张三' })
      // 已有真实值 → 保留不动
      expect(buildContextDefaultsPatch(config, {
        ...runtime,
        formData: { assignee: '李四' },
        fieldStaticDefaults: { assignee: '默认处理人' },
      })).toEqual({})
    })

    it('takes the first value when a route query param repeats', () => {
      const config = {
        contextDefaults: [{ id: '1', field: 'orderNo', source: 'ROUTE_QUERY', path: 'orderNo', enabled: true }],
      }
      const patch = buildContextDefaultsPatch(config, {
        formData: {},
        context: {},
        routeQuery: { orderNo: ['A-1', 'A-2'] },
      })
      expect(patch).toEqual({ orderNo: 'A-1' })
    })

    it('resolves record id from route query first, then context paths', () => {
      const config = { recordLoad: { enabled: true, keyNames: ['recordId', 'currentUser.staffId'] } }
      expect(resolveFormInitRecordId(config, {
        routeQuery: { recordId: 'R1' },
        context: { currentUser: { staffId: 'S1' } },
      })).toBe('R1')
      expect(resolveFormInitRecordId(config, {
        routeQuery: {},
        context: { currentUser: { staffId: 'S1' } },
      })).toBe('S1')
      expect(resolveFormInitRecordId(config, { routeQuery: {}, context: {} })).toBe('')
      expect(resolveFormInitRecordId({ recordLoad: { enabled: false, keyNames: ['recordId'] } }, {
        routeQuery: { recordId: 'R1' },
        context: {},
      })).toBe('')
    })

    it('appends the login user realName to the user picker label companion field', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'ownerId', source: 'CONTEXT_PATH', path: 'currentUser.userId', enabled: true },
        ],
      }
      const runtime = {
        formData: {},
        context: { currentUser: { userId: 1, realName: '张三' } },
        fields: [{ field: 'ownerId', type: 'userSelect' }],
      }
      const patch = buildContextDefaultsPatch(config, runtime)
      appendSelectionLabelContextDefaults(patch, config, runtime)
      // ID 写入主字段，姓名写入 ownerIdName 伴随字段（候选规则与手动选择一致）
      expect(patch).toEqual({ ownerId: 1, ownerIdName: '张三' })
    })

    it('prefers the explicit labelValueField for the user picker label companion', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'handler', source: 'CONTEXT_PATH', path: 'currentUser.staffId', enabled: true },
        ],
      }
      const runtime = {
        formData: { handlerName: '遗留值' },
        context: { currentUser: { staffId: 'S9', staffName: '王五' } },
        fields: [{ field: 'handler', type: 'userSelect', props: { labelValueField: 'handlerDisplayName' } }],
      }
      const patch = buildContextDefaultsPatch(config, runtime)
      appendSelectionLabelContextDefaults(patch, config, runtime)
      // 显式 labelValueField 提到首位写入；handlerName 虽在候选但 formData 键存在才写
      expect(patch).toEqual({ handler: 'S9', handlerDisplayName: '王五', handlerName: '王五' })
    })

    it('appends the org name for the org picker activeOrgId default', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'deptId', source: 'CONTEXT_PATH', path: 'currentUser.activeOrgId', enabled: true },
        ],
      }
      const runtime = {
        formData: {},
        context: { currentUser: { activeOrgId: 9, activeOrgName: '总部' } },
        fields: [{ field: 'deptId', type: 'orgTreeSelect' }],
      }
      const patch = buildContextDefaultsPatch(config, runtime)
      appendSelectionLabelContextDefaults(patch, config, runtime)
      // deptId 的首个候选伴随字段是 deptIdName（与手动选择时 patchSelectionLabelValue 一致）
      expect(patch).toEqual({ deptId: 9, deptIdName: '总部' })
    })

    it('skips selector label companions for non-selector fields, inactive defaults and blank labels', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'plainInput', source: 'CONTEXT_PATH', path: 'currentUser.userId', enabled: true },
          { id: '2', field: 'ownerId', source: 'CONTEXT_PATH', path: 'currentUser.userId', enabled: true },
          { id: '3', field: 'handler', source: 'CONTEXT_PATH', path: 'currentUser.staffId', enabled: false },
        ],
      }
      const runtime = {
        formData: {},
        context: { currentUser: { userId: 1, realName: '' } },
        fields: [
          { field: 'plainInput', type: 'input' },
          { field: 'ownerId', type: 'userSelect' },
          { field: 'handler', type: 'userSelect' },
        ],
      }
      const patch = buildContextDefaultsPatch(config, runtime)
      appendSelectionLabelContextDefaults(patch, config, runtime)
      // 非选择器字段不补；realName 为空不补；被禁用默认值未生效不补
      expect(patch).toEqual({ plainInput: 1, ownerId: 1 })
    })

    it('keeps the patch untouched when no default is applied to the selector field', () => {
      const config = {
        contextDefaults: [
          { id: '1', field: 'ownerId', source: 'CONTEXT_PATH', path: 'currentUser.userId', enabled: true },
        ],
      }
      const runtime = {
        formData: { ownerId: 'existing-user' },
        context: { currentUser: { userId: 1, realName: '张三' } },
        fieldStaticDefaults: {},
        fields: [{ field: 'ownerId', type: 'userSelect' }],
      }
      const patch = buildContextDefaultsPatch(config, runtime)
      appendSelectionLabelContextDefaults(patch, config, runtime)
      // 存量值保护生效（patch 无 ownerId）→ 不补姓名，避免 ID 与姓名不一致
      expect(patch).toEqual({})
    })
  })
})
