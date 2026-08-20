export const FIELD_COMPONENT_PALETTE_GROUPS = [
  {
    title: '输入',
    items: [
      { componentKey: 'input', label: '输入框' },
      { componentKey: 'barcodeScanner', label: '扫码输入' },
      { componentKey: 'textarea', label: '多行文本' },
      { componentKey: 'number', label: '数字' },
      { componentKey: 'money', label: '金额' },
      { componentKey: 'slider', label: '滑块' },
      { componentKey: 'rate', label: '评分' },
      { componentKey: 'color', label: '颜色选择' },
    ],
  },
  {
    title: '选择',
    items: [
      { componentKey: 'select', label: '静态下拉' },
      { componentKey: 'dictSelect', label: '字典下拉' },
      { componentKey: 'radio', label: '单选' },
      { componentKey: 'radioButton', label: '按钮单选' },
      { componentKey: 'checkbox', label: '多选' },
      { componentKey: 'transfer', label: '穿梭框' },
      { componentKey: 'cascader', label: '级联选择' },
      { componentKey: 'treeSelect', label: '树形选择' },
      { componentKey: 'customSelect', label: '远程选择' },
      { componentKey: 'date', label: '日期' },
      { componentKey: 'datetime', label: '日期时间' },
      { componentKey: 'daterange', label: '日期范围' },
      { componentKey: 'datetimerange', label: '日期时间范围' },
      { componentKey: 'month', label: '月份' },
      { componentKey: 'year', label: '年份' },
      { componentKey: 'timerange', label: '时间范围' },
      { componentKey: 'switch', label: '开关' },
    ],
  },
  {
    title: '业务',
    items: [
      { componentKey: 'userSelect', label: '人员选择' },
      { componentKey: 'orgTreeSelect', label: '部门选择' },
      { componentKey: 'regionTreeSelect', label: '行政区划' },
      { componentKey: 'objectReference', label: '对象引用' },
      { componentKey: 'recordSelector', label: '记录选择器' },
      { componentKey: 'fileUpload', label: '文件上传' },
      { componentKey: 'imageUpload', label: '图片上传' },
      { componentKey: 'text', label: '文本展示' },
    ],
  },
]

const inputDefaults = { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'input', length: 128, precision: 2, queryType: 'like' }
const numberDefaults = { fieldType: 'NUMBER', businessFieldType: 'NUMBER', dataType: 'int', componentType: 'number', length: 11, precision: 0, queryType: 'eq' }
const orgDefaults = { fieldType: 'DEPT', businessFieldType: 'DEPT', dataType: 'bigint', componentType: 'orgTreeSelect', length: null, precision: null, queryType: 'eq' }
const userDefaults = { fieldType: 'USER', businessFieldType: 'USER', dataType: 'bigint', componentType: 'userSelect', length: null, precision: null, queryType: 'eq' }
const fileDefaults = { fieldType: 'FILE', businessFieldType: 'FILE', dataType: 'varchar', componentType: 'fileUpload', length: 512, precision: 2, queryType: 'eq' }

export const FIELD_COMPONENT_DEFAULTS = Object.freeze({
  'input': inputDefaults,
  'barcodeScanner': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'barcodeScanner', length: 2048, precision: 2, queryType: 'eq' },
  'textarea': { fieldType: 'MULTILINE', businessFieldType: 'MULTILINE', dataType: 'text', componentType: 'textarea', length: null, precision: 2, queryType: 'like' },
  'number': numberDefaults,
  'inputNumber': numberDefaults,
  'input-number': numberDefaults,
  'inputnumber': numberDefaults,
  'integer': numberDefaults,
  'money': { fieldType: 'MONEY', businessFieldType: 'MONEY', dataType: 'decimal', componentType: 'number', length: 18, precision: 2, queryType: 'eq' },
  'slider': { fieldType: 'NUMBER', businessFieldType: 'NUMBER', dataType: 'int', componentType: 'slider', length: 11, precision: 0, queryType: 'eq' },
  'rate': { fieldType: 'NUMBER', businessFieldType: 'NUMBER', dataType: 'decimal', componentType: 'rate', length: 4, precision: 1, queryType: 'eq' },
  'color': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'color', length: 32, precision: 2, queryType: 'eq' },
  'date': { fieldType: 'DATE', businessFieldType: 'DATE', dataType: 'date', componentType: 'date', length: null, precision: null, queryType: 'eq' },
  'datetime': { fieldType: 'DATETIME', businessFieldType: 'DATETIME', dataType: 'datetime', componentType: 'datetime', length: null, precision: null, queryType: 'eq' },
  'daterange': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'text', componentType: 'daterange', length: null, precision: null, queryType: 'eq' },
  'datetimerange': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'text', componentType: 'datetimerange', length: null, precision: null, queryType: 'eq' },
  'month': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'month', length: 7, precision: null, queryType: 'eq' },
  'year': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'year', length: 4, precision: null, queryType: 'eq' },
  'time': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'time', length: 32, precision: null, queryType: 'eq' },
  'timerange': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'text', componentType: 'timerange', length: null, precision: null, queryType: 'eq' },
  'switch': { fieldType: 'SWITCH', businessFieldType: 'SWITCH', dataType: 'tinyint', componentType: 'switch', length: 1, precision: 0, queryType: 'eq' },
  'select': { fieldType: 'DICT', businessFieldType: 'DICT', dataType: 'varchar', componentType: 'select', length: 64, precision: 2, queryType: 'eq' },
  'dictSelect': { fieldType: 'DICT', businessFieldType: 'DICT', dataType: 'varchar', componentType: 'dictSelect', length: 64, precision: 2, queryType: 'eq' },
  'radio': { fieldType: 'RADIO', businessFieldType: 'RADIO', dataType: 'varchar', componentType: 'radio', length: 64, precision: 2, queryType: 'eq' },
  'radioButton': { fieldType: 'RADIO', businessFieldType: 'RADIO', dataType: 'varchar', componentType: 'radioButton', length: 64, precision: 2, queryType: 'eq' },
  'checkbox': { fieldType: 'CHECKBOX', businessFieldType: 'CHECKBOX', dataType: 'varchar', componentType: 'checkbox', length: 255, precision: 2, queryType: 'in' },
  'transfer': { fieldType: 'MULTI_SELECT', businessFieldType: 'MULTI_SELECT', dataType: 'text', componentType: 'transfer', length: null, precision: null, queryType: 'in' },
  'cascader': { fieldType: 'DICT', businessFieldType: 'DICT', dataType: 'varchar', componentType: 'cascader', length: 128, precision: 2, queryType: 'eq' },
  'treeSelect': { fieldType: 'SELECT', businessFieldType: 'SELECT', dataType: 'varchar', componentType: 'treeSelect', length: 128, precision: 2, queryType: 'eq' },
  'customSelect': { fieldType: 'SELECT', businessFieldType: 'SELECT', dataType: 'varchar', componentType: 'customSelect', length: 128, precision: 2, queryType: 'eq' },
  'regionTreeSelect': { fieldType: 'REGION', businessFieldType: 'REGION', dataType: 'varchar', componentType: 'regionTreeSelect', length: 32, precision: 2, queryType: 'eq' },
  'orgTreeSelect': orgDefaults,
  'orgSelect': orgDefaults,
  'departmentSelect': orgDefaults,
  'departmentTreeSelect': orgDefaults,
  'deptSelect': orgDefaults,
  'deptTreeSelect': orgDefaults,
  'elTreeSelect': orgDefaults,
  'orgName': orgDefaults,
  'deptName': orgDefaults,
  'userSelect': userDefaults,
  'userPicker': userDefaults,
  'userName': userDefaults,
  'fileUpload': fileDefaults,
  'upload': fileDefaults,
  'imageUpload': { fieldType: 'IMAGE', businessFieldType: 'IMAGE', dataType: 'varchar', componentType: 'imageUpload', length: 512, precision: 2, queryType: 'eq' },
  'objectReference': { fieldType: 'REFERENCE', businessFieldType: 'REFERENCE', dataType: 'bigint', componentType: 'objectReference', length: null, precision: null, queryType: 'eq' },
  'recordSelector': { fieldType: 'RECORD_SELECTOR', businessFieldType: 'RECORD_SELECTOR', dataType: 'bigint', componentType: 'recordSelector', length: null, precision: null, queryType: 'eq' },
  'text': { fieldType: 'TEXT', businessFieldType: 'TEXT', dataType: 'varchar', componentType: 'text', length: 255, precision: 2, queryType: 'like' },
})

export const FORM_FIELD_COMPONENT_KEYS = new Set(Object.keys(FIELD_COMPONENT_DEFAULTS))

export const STRUCTURED_VALUE_COMPONENT_KEYS = new Set([
  'checkbox',
  'transfer',
  'daterange',
  'datetimerange',
  'timerange',
])

export function resolveFieldComponentDefaults(componentKey = '') {
  return FIELD_COMPONENT_DEFAULTS[componentKey] || FIELD_COMPONENT_DEFAULTS.input
}
