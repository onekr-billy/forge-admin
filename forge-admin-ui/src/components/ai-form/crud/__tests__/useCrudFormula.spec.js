import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { previewFormula } from '@/api/formula'
import { flattenRuntimeFormFields, useCrudFormula } from '../composables/useCrudFormula'

vi.mock('@/api/formula', () => ({ previewFormula: vi.fn() }))
const instances = []
function setup(schema, values = {}, flags = {}) {
  const deps = {
    props: reactive({ editSchema: schema, formOnly: false }),
    formData: ref(values),
    isDetailMode: ref(false),
    formOnlySubmitted: ref(false),
    modalVisible: ref(true),
    inlineWorkspaceVisible: ref(false),
    ...flags,
  }
  const runtime = useCrudFormula(deps)
  instances.push(runtime)
  return { ...deps, ...runtime }
}
const calc = (expression = 'qty * price') => ({ field: 'total', formulaConfig: { expression } })
async function refresh(runtime) {
  runtime.refreshRuntimeFormulas()
  await vi.advanceTimersByTimeAsync(0)
}

beforeEach(() => {
  vi.useFakeTimers()
  previewFormula.mockReset().mockResolvedValue({ data: { success: true, result: 12 } })
})
afterEach(() => {
  instances.splice(0).forEach(runtime => runtime.clearRuntimeFormulaTimers())
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('公式运行态', () => {
  it('展平布局字段，过滤无效配置和聚合公式', () => {
    const field = calc()
    const schema = [{ nodeType: 'row', children: [null, field] }, { field: 'bad', formulaConfig: '{' }, { field: 'sum', formulaConfig: { type: 'AGGREGATE', expression: 'qty' } }]
    expect(flattenRuntimeFormFields(schema)).toEqual([field, schema[1], schema[2]])
    expect(flattenRuntimeFormFields(null)).toEqual([])
    expect(setup(schema).runtimeFormulaFields.value).toHaveLength(1)
  })

  it('普通公式转换数字但保留无关字段，在同一个表单 ref 上回填', async () => {
    const runtime = setup([{ field: 'qty', type: 'number' }, calc()], { qty: '3', price: '4.0', name: '001', total: null })
    const originalRef = runtime.formData
    await refresh(runtime)
    expect(previewFormula).toHaveBeenCalledWith({
      expression: 'qty * price',
      type: 'CALC',
      dependsOn: ['qty', 'price'],
      sampleValues: { qty: 3, price: 4, name: '001', total: null },
    })
    expect(runtime.formData).toBe(originalRef)
    expect(runtime.formData.value).toEqual({ qty: '3', price: '4.0', name: '001', total: 12 })
  })

  it('保留条件公式、字符串配置和依赖去重规则', async () => {
    const runtime = setup([{ field: 'result', formulaConfig: JSON.stringify({
      type: 'CONDITIONAL',
      dependsOn: ['qty', 'result'],
      condition: { expression: 'qty > 2 and name == "qty"', trueValue: '是', falseValue: '否' },
    }) }], { qty: '3', name: '001' })
    await refresh(runtime)
    expect(previewFormula).toHaveBeenCalledWith({
      expression: 'qty > 2 and name == "qty"',
      type: 'CONDITIONAL',
      dependsOn: ['qty', 'name'],
      sampleValues: { qty: 3, name: '001' },
      condition: { expression: 'qty > 2 and name == "qty"', trueValue: '是', falseValue: '否' },
    })
  })

  it.each([null, undefined, '', []])('空依赖 %s 清空目标且不请求', async (value) => {
    const runtime = setup([calc()], { qty: value, price: 4, total: 99 })
    await refresh(runtime)
    expect(previewFormula).not.toHaveBeenCalled()
    expect(runtime.formData.value.total).toBeNull()
  })

  it('零值与无依赖常量继续计算', async () => {
    const runtime = setup([calc('qty * 4')], { qty: 0 })
    await refresh(runtime)
    expect(previewFormula).toHaveBeenCalledTimes(1)
    runtime.props.editSchema = [calc('2 * 6')]
    await refresh(runtime)
    expect(previewFormula.mock.lastCall[0].dependsOn).toEqual([])
  })

  it('props schema 和依赖值改变后签名仍响应式更新', () => {
    const runtime = setup([calc()], { qty: 3, price: 4 })
    const initial = runtime.runtimeFormulaSignature.value
    runtime.formData.value.qty = 5
    expect(runtime.runtimeFormulaSignature.value).not.toBe(initial)
    runtime.props.editSchema = [calc('qty * 2')]
    expect(runtime.runtimeFormulaSignature.value).toContain('qty * 2')
  })

  it('保留默认防抖和主组件 forEach 的现有 delay 实参', async () => {
    const runtime = setup([calc('qty * 4'), { field: 'other', formulaConfig: { expression: 'qty * 5' } }], { qty: 3 })
    runtime.scheduleRuntimeFormulaCalculation(runtime.runtimeFormulaFields.value[0])
    await vi.advanceTimersByTimeAsync(179)
    expect(previewFormula).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(previewFormula).toHaveBeenCalledTimes(1)
    previewFormula.mockClear()
    runtime.runtimeFormulaFields.value.forEach(runtime.scheduleRuntimeFormulaCalculation)
    await vi.advanceTimersByTimeAsync(0)
    expect(previewFormula).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(previewFormula).toHaveBeenCalledTimes(2)
  })

  it('较新的请求序号防止旧响应覆盖', async () => {
    const resolvers = []
    previewFormula.mockImplementation(() => new Promise(resolve => resolvers.push(resolve)))
    const runtime = setup([calc('qty * 4')], { qty: 3 })
    await refresh(runtime)
    runtime.formData.value.qty = 5
    await refresh(runtime)
    resolvers[1]({ data: { success: true, result: 20 } })
    await Promise.resolve()
    resolvers[0]({ data: { success: true, result: 12 } })
    await Promise.resolve()
    expect(runtime.formData.value.total).toBe(20)
  })

  it.each(['isDetailMode', 'formOnlySubmitted'])('%s 模式禁算，也不接受在途结果', async (flag) => {
    let resolve
    previewFormula.mockImplementation(() => new Promise((done) => {
      resolve = done
    }))
    const runtime = setup([calc('qty * 4')], { qty: 3 })
    await refresh(runtime)
    runtime[flag].value = true
    resolve({ data: { success: true, result: 12 } })
    await Promise.resolve()
    expect(runtime.formData.value.total).toBeUndefined()
    await refresh(runtime)
    expect(previewFormula).toHaveBeenCalledTimes(1)
  })

  it('关闭容器禁算，formOnly 与内联工作区独立启用', async () => {
    const runtime = setup([calc('qty * 4')], { qty: 3 }, { modalVisible: ref(false) })
    await refresh(runtime)
    expect(previewFormula).not.toHaveBeenCalled()
    runtime.props.formOnly = true
    await refresh(runtime)
    runtime.props.formOnly = false
    runtime.inlineWorkspaceVisible.value = true
    await refresh(runtime)
    expect(previewFormula).toHaveBeenCalledTimes(2)
  })

  it('实例的定时器、请求序号及清理互不影响', async () => {
    const first = setup([calc('qty * 4')], { qty: 1 })
    const second = setup([calc('qty * 4')], { qty: 3 })
    first.refreshRuntimeFormulas(180)
    second.refreshRuntimeFormulas(180)
    first.clearRuntimeFormulaTimers()
    await vi.advanceTimersByTimeAsync(180)
    expect(previewFormula).toHaveBeenCalledTimes(1)
    expect(first.formData.value.total).toBeUndefined()
    expect(second.formData.value.total).toBe(12)
  })
})
