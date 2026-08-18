import { describe, expect, it } from 'vitest'
import { applyRuntimeControl, resolveRuntimeControl } from '../runtime-rules'

describe('lowcode runtime visibility rules', () => {
  const field = {
    field: 'pickupMethod',
    hidden: true,
    required: true,
    runtimeRules: [{
      conditions: [{ field: 'deliveryMode', operator: 'eq', value: 'PICKUP' }],
      effect: { visible: true },
    }],
  }

  it('shows a statically hidden field when its visibility condition matches', () => {
    const control = resolveRuntimeControl(field, { formData: { deliveryMode: 'PICKUP' } })

    expect(control.visible).toBe(true)
    expect(applyRuntimeControl(field, { formData: { deliveryMode: 'PICKUP' } })).not.toBeNull()
  })

  it('hides the field and clears required validation when the condition does not match', () => {
    const control = resolveRuntimeControl(field, { formData: { deliveryMode: 'DELIVERY' } })

    expect(control.visible).toBe(false)
    expect(applyRuntimeControl(field, { formData: { deliveryMode: 'DELIVERY' } })).toBeNull()
  })

  it('keeps legacy hidden effects compatible', () => {
    const control = resolveRuntimeControl({
      field: 'pickupMethod',
      runtimeRules: [{
        conditions: [{ field: 'deliveryMode', operator: 'eq', value: 'DELIVERY' }],
        effect: { hidden: true },
      }],
    }, { formData: { deliveryMode: 'DELIVERY' } })

    expect(control.visible).toBe(false)
  })

  it('keeps a visible-by-condition rule hidden until its condition matches', () => {
    const target = {
      field: 'pickupAddress',
      runtimeRules: [{
        conditions: [{ field: 'deliveryMode', operator: 'eq', value: 'PICKUP' }],
        effect: { visible: true, whenUnmatched: 'hidden' },
      }],
    }

    expect(resolveRuntimeControl(target, { formData: { deliveryMode: 'DELIVERY' } }).visible).toBe(false)
    expect(resolveRuntimeControl(target, { formData: { deliveryMode: 'PICKUP' } }).visible).toBe(true)
  })
})
