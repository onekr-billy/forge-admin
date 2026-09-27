/**
 * 受控表单控件不能把 schema 里的 value / modelValue / update 监听再绑一遍。
 * Vue 后写的 v-bind 会盖掉 :value，输入框表现为能聚焦、handleUpdate 有日志，但内容写不进去。
 *
 * readonly / disabled 由 AiFormItem 的 disabledHandler 统一处理（只读 → disabled 控件），
 * 不能再经 v-bind 渗入 Naive 组件：Select / DatePicker 等收到原生 readonly 时表现不稳定。
 * 不要用「纯文本替换」冒充只读——空值时看起来像组件消失。
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
    'on-update:value': _onUpdateValueKebab,
    'on-update:model-value': _onUpdateModelValueKebab,
    'onUpdate:label-value': _onUpdateLabelValue,
    'on-update:label-value': _onUpdateLabelValueKebab,
    readonly: _readonly,
    disabled: _disabled,
    // 设计器/运行态元数据，不是 Naive 控件 props
    optionSource: _optionSource,
    fieldMappings: _fieldMappings,
    mappings: _mappings,
    recordSelector: _recordSelector,
    runtimeRules: _runtimeRules,
    fieldEvents: _fieldEvents,
    // options 由 AiFormItem.currentOptions 统一接管；设计器常残留 options:[]，
    // 若经 v-bind 盖掉 :options，远程下拉会变成空列表、表现为「选不中」
    options: _options,
    labelValueField: _labelValueField,
    labelValue: _labelValue,
    displayFields: _displayFields,
    referenceObjectCode: _referenceObjectCode,
    referenceDisplayField: _referenceDisplayField,
    referenceValueField: _referenceValueField,
    dataBinding: _dataBinding,
    cascade: _cascade,
    cascadeConfig: _cascadeConfig,
    visibility: _visibility,
    validation: _validation,
    ...rest
  } = source
  return rest
}
