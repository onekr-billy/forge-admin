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
})
