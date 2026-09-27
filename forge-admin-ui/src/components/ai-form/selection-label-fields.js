/** 人员选择器组件类型（与 AiFormItem 渲染分支保持同步，勿单侧修改） */
export const USER_SELECT_FIELD_TYPES = new Set([
  'userSelect',
  'userPicker',
  'user',
  'userName',
  'sysUserSelect',
  'forgeUserSelect',
])

/** 组织/部门选择器组件类型（与 AiFormItem 渲染分支保持同步，勿单侧修改） */
export const ORG_SELECT_FIELD_TYPES = new Set([
  'orgTreeSelect',
  'orgSelect',
  'organizationSelect',
  'departmentSelect',
  'departmentTreeSelect',
  'deptSelect',
  'deptTreeSelect',
  'elTreeSelect',
  'orgName',
  'deptName',
  'forgeOrgTreeSelect',
])

export function isUserSelectLikeField(field = {}) {
  const candidates = [field.type, field.componentType, field.componentKey]
  return candidates.some(value => USER_SELECT_FIELD_TYPES.has(String(value || '').trim()))
}

export function isOrgSelectLikeField(field = {}) {
  const candidates = [field.type, field.componentType, field.componentKey]
  return candidates.some(value => ORG_SELECT_FIELD_TYPES.has(String(value || '').trim()))
}

export function resolveSelectionLabelFields(field = {}, selectionType = '') {
  const fieldName = String(field.field || '').trim()
  const candidates = [
    field.props?.referenceDisplayField,
    field.referenceDisplayField,
    field.props?.displayField,
    field.displayField,
    field.props?.labelField,
    field.labelField,
    field.props?.targetLabelField,
    field.targetLabelField,
    field.props?.labelValueField,
    field.labelValueField,
    field.props?.targetField,
    field.targetField,
  ]
  if (fieldName) {
    candidates.push(`${fieldName}Name`)
    // snake_case 字段（field_select）冗余列常为 field_select_name；camel 读模型为 fieldSelectName
    if (fieldName.includes('_')) {
      candidates.push(`${fieldName}_name`)
      const camel = fieldName.replace(/_([a-zA-Z0-9])/g, (_, ch) => String(ch).toUpperCase())
      if (camel && camel !== fieldName)
        candidates.push(`${camel}Name`)
    }
    else {
      const snake = fieldName.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
      if (snake && snake !== fieldName)
        candidates.push(`${snake}_name`)
    }
    if (fieldName.endsWith('UserId')) {
      candidates.push(fieldName.replace(/UserId$/, 'UserName'))
      candidates.push(fieldName.replace(/UserId$/, 'Name'))
    }
    if (fieldName.endsWith('DeptId')) {
      candidates.push(fieldName.replace(/DeptId$/, 'DeptName'))
      candidates.push(fieldName.replace(/DeptId$/, 'Name'))
    }
    if (fieldName.endsWith('OrgId')) {
      candidates.push(fieldName.replace(/OrgId$/, 'OrgName'))
      candidates.push(fieldName.replace(/OrgId$/, 'Name'))
    }
    if (fieldName.endsWith('Id'))
      candidates.push(fieldName.replace(/Id$/, 'Name'))
    candidates.push(`${fieldName}Label`, `${fieldName}Text`)
  }
  if (selectionType === 'user')
    candidates.push('userName', 'realName', 'nickname')
  if (selectionType === 'org')
    candidates.push('orgName', 'deptName', 'departmentName')
  return candidates
    .map(value => String(value || '').trim())
    .filter((value, index, all) => value && value !== fieldName && all.indexOf(value) === index)
}

/** 从表单/子表行里读伴随显示名：精确键 → camel/snake 别名 */
export function readSelectionLabelFromData(formData = {}, field = {}, selectionType = '') {
  if (!formData || typeof formData !== 'object')
    return ''
  for (const candidate of resolveSelectionLabelFields(field, selectionType)) {
    const value = readDataFieldValue(formData, candidate)
    if (value !== null && value !== undefined && String(value).trim() !== '')
      return value
  }
  return ''
}

export function readDataFieldValue(data = {}, fieldName = '') {
  const key = String(fieldName || '').trim()
  if (!key || !data || typeof data !== 'object')
    return undefined
  if (Object.prototype.hasOwnProperty.call(data, key) && data[key] !== undefined)
    return data[key]
  const aliases = []
  if (key.includes('_')) {
    const camel = key.replace(/_([a-zA-Z0-9])/g, (_, ch) => String(ch).toUpperCase())
    if (camel && camel !== key)
      aliases.push(camel)
  }
  else {
    const snake = key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
    if (snake && snake !== key)
      aliases.push(snake)
  }
  for (const alias of aliases) {
    if (Object.prototype.hasOwnProperty.call(data, alias) && data[alias] !== undefined)
      return data[alias]
  }
  return undefined
}
