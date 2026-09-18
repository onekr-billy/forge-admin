/**
 * 受控表单控件不能把 schema 里的 value / modelValue / update 监听再绑一遍。
 * Vue 后写的 v-bind 会盖掉 :value，输入框表现为能聚焦、handleUpdate 有日志，但内容写不进去。
 */
export function resolveControlProps(props = {}) {
  const source = props && typeof props === 'object' ? props : {}
  const {
    value: _value,
    defaultValue: _defaultValue,
    modelValue: _modelValue,
    'onUpdate:value': _onUpdateValue,
    'onUpdate:modelValue': _onUpdateModelValue,
    onUpdateValue: _onUpdateValueCamel,
    onUpdateModelValue: _onUpdateModelValueCamel,
    ...rest
  } = source
  return rest
}
