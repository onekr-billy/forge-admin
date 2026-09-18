import { describe, expect, it } from 'vitest'
import { resolveControlProps } from '../control-props'

describe('resolveControlProps', () => {
  it('keeps placeholder while dropping value bindings that would override controlled inputs', () => {
    expect(resolveControlProps({
      'placeholder': '请输入输入框',
      'value': '',
      'defaultValue': '',
      'modelValue': '',
      'onUpdate:value': () => {},
      'maxlength': 64,
    })).toEqual({
      placeholder: '请输入输入框',
      maxlength: 64,
    })
  })

  it('drops kebab-case update listeners from schema props as well', () => {
    expect(resolveControlProps({
      'on-update:value': () => {},
      'on-update:model-value': () => {},
      'on-update:label-value': () => {},
      'clearable': true,
    })).toEqual({ clearable: true })
  })
})
