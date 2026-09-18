<script setup>
/**
 * FormPermissionConfig — 表单字段权限
 *
 * 由当前流程字段目录生成，不再手工输入字段名。
 * config.formFieldPermissions: [{ field, label, readable, writable, required }]
 */
import { computed } from 'vue'
import {
  normalizeFlowArrayPermission,
  normalizeFlowChildPermission,
  normalizeFlowFieldCatalog,
  normalizeFlowFieldPermission,
  normalizeFlowFormPermissions,
  serializeFlowFormPermissions,
} from '@/utils/flow-field-permissions'

const props = defineProps({
  config: { type: Object, required: true },
  formFieldCatalog: { type: Array, default: () => [] },
  readonly: Boolean,
})

const emit = defineEmits(['update:config'])

const savedPermissionBundle = computed(() => {
  const embedded = normalizeFlowFormPermissions(props.config?.formFieldPermissions)
  const children = Array.isArray(props.config?.formChildPermissions) && props.config.formChildPermissions.length
    ? props.config.formChildPermissions
    : embedded.children
  const arrays = Array.isArray(props.config?.formArrayPermissions) && props.config.formArrayPermissions.length
    ? props.config.formArrayPermissions
    : embedded.arrays
  return normalizeFlowFormPermissions({
    fields: embedded.fields,
    children,
    arrays,
  })
})

const savedPermissions = computed(() => {
  const map = new Map()
  for (const item of savedPermissionBundle.value.fields) {
    if (item.field)
      map.set(item.permissionKey, item)
  }
  return map
})

const formFields = computed(() => {
  return normalizeFlowFieldCatalog(props.formFieldCatalog || []).map(item => ({
    ...item,
    componentType: item?.componentType || item?.type || '',
    dataType: item?.dataType || '',
    sourceRequired: item?.required === true || item?.sourceRequired === true,
  }))
})

const rows = computed(() => {
  const output = formFields.value.map((field) => {
    const saved = savedPermissions.value.get(field.permissionKey)
    return {
      ...field,
      configured: Boolean(saved),
      ...normalizeFlowFieldPermission(saved || {
        ...field,
        readable: true,
        writable: field.scope !== 'child',
      }),
    }
  })

  for (const saved of savedPermissions.value.values()) {
    if (!output.some(row => row.permissionKey === saved.permissionKey)) {
      output.push({
        ...saved,
        configured: true,
        sourceRequired: false,
        componentType: '',
        dataType: '',
        stale: true,
      })
    }
  }
  return output
})

const childPermissionRows = computed(() => {
  const source = new Map()
  formFields.value.filter(field => field.scope === 'child').forEach((field) => {
    if (!source.has(field.childKey)) {
      source.set(field.childKey, {
        childKey: field.childKey,
        label: field.childLabel || field.relationName || field.childKey,
      })
    }
  })
  savedPermissionBundle.value.children.forEach((child) => {
    if (!child.childKey)
      return
    source.set(child.childKey, {
      ...(source.get(child.childKey) || {}),
      ...child,
    })
  })
  return Array.from(source.values()).map(child => ({
    ...normalizeFlowChildPermission(child),
    fieldCount: formFields.value.filter(field => field.scope === 'child' && field.childKey === child.childKey).length,
  }))
})

const arrayPermissionRows = computed(() => {
  const source = new Map()
  formFields.value.filter(field => field.scope === 'array').forEach((field) => {
    if (!source.has(field.arrayKey)) {
      source.set(field.arrayKey, {
        arrayKey: field.arrayKey,
        label: field.arrayLabel || field.arrayKey,
      })
    }
  })
  savedPermissionBundle.value.arrays.forEach((array) => {
    if (!array.arrayKey)
      return
    source.set(array.arrayKey, {
      ...(source.get(array.arrayKey) || {}),
      ...array,
    })
  })
  return Array.from(source.values()).map(array => ({
    ...normalizeFlowArrayPermission(array),
    fieldCount: formFields.value.filter(field => field.scope === 'array' && field.arrayKey === array.arrayKey).length,
  }))
})

const configuredCount = computed(() => {
  const fieldCount = rows.value.filter(row => row.configured && (row.readable === false || row.writable === false || row.required === true)).length
  const childCount = childPermissionRows.value.filter(row => row.configured && (row.allowCreate || row.allowUpdate || row.allowDelete || row.readable === false)).length
  const arrayCount = arrayPermissionRows.value.filter(row => row.configured && (row.allowCreate || row.allowDelete || row.readable === false || row.allowUpdate === false)).length
  return fieldCount + childCount + arrayCount
})

function update(permissionKey, patch) {
  if (props.readonly)
    return
  const nextRows = rows.value.map((row) => {
    if (row.permissionKey !== permissionKey)
      return row
    const next = { ...row, ...patch }

    if (Object.prototype.hasOwnProperty.call(patch, 'readable')) {
      next.readable = patch.readable === true
      next.visible = next.readable
      if (!next.readable) {
        next.writable = false
        next.editable = false
        next.required = false
      }
    }

    if (Object.prototype.hasOwnProperty.call(patch, 'writable')) {
      next.writable = patch.writable === true
      next.editable = next.writable
      if (next.writable) {
        next.readable = true
        next.visible = true
      }
      else {
        next.required = false
      }
    }

    if (Object.prototype.hasOwnProperty.call(patch, 'required')) {
      next.required = patch.required === true
      if (next.required) {
        next.readable = true
        next.visible = true
        next.writable = true
        next.editable = true
      }
    }

    if (next.readable === false) {
      next.writable = false
      next.editable = false
      next.required = false
    }
    if (next.writable === true || next.required === true) {
      next.readable = true
      next.visible = true
    }
    if (next.required === true) {
      next.writable = true
      next.editable = true
    }
    next.visible = next.readable
    next.editable = next.writable
    return next
  })
  emitPermissions(nextRows, childPermissionRows.value, arrayPermissionRows.value)
}

function updateChild(childKey, patch) {
  if (props.readonly)
    return
  const nextChildren = childPermissionRows.value.map((child) => {
    if (child.childKey !== childKey)
      return child
    const next = { ...child, ...patch }
    if (next.readable === false) {
      next.allowCreate = false
      next.allowUpdate = false
      next.allowDelete = false
    }
    if (next.allowCreate || next.allowUpdate || next.allowDelete)
      next.readable = true
    return normalizeFlowChildPermission(next)
  })
  emitPermissions(rows.value, nextChildren, arrayPermissionRows.value)
}

function updateArray(arrayKey, patch) {
  if (props.readonly)
    return
  const nextArrays = arrayPermissionRows.value.map((array) => {
    if (array.arrayKey !== arrayKey)
      return array
    const next = { ...array, ...patch, configured: true }
    if (next.readable === false) {
      next.allowCreate = false
      next.allowUpdate = false
      next.allowDelete = false
    }
    if (next.allowCreate || next.allowUpdate || next.allowDelete)
      next.readable = true
    return normalizeFlowArrayPermission(next)
  })
  emitPermissions(rows.value, childPermissionRows.value, nextArrays)
}

function emitPermissions(fields, children, arrays) {
  const normalizedChildren = children.map(normalizeFlowChildPermission)
  const normalizedArrays = arrays.map(normalizeFlowArrayPermission)
  emit('update:config', {
    formFieldPermissions: serializeFlowFormPermissions(fields, normalizedChildren, normalizedArrays),
    formChildPermissions: normalizedChildren,
    formArrayPermissions: normalizedArrays,
  })
}
</script>

<template>
  <div class="form-permission-config">
    <div class="form-permission-head">
      <div>
        <div class="form-permission-title">
          表单字段权限
        </div>
        <div class="form-permission-desc">
          按当前流程字段目录控制该审批节点的可见、可编辑和必填。
        </div>
      </div>
      <n-tag size="small" :type="configuredCount > 0 ? 'info' : 'default'" :bordered="false">
        {{ configuredCount > 0 ? `${configuredCount} 项已调整` : '默认全量可写' }}
      </n-tag>
    </div>

    <div v-if="rows.length === 0" class="form-permission-empty">
      <i class="i-material-symbols:dynamic-form" />
      <div>
        <div class="form-permission-empty-title">
          暂无可配置字段
        </div>
        <div class="form-permission-empty-desc">
          请先为流程选择动态表单，或从应用中心带业务对象进入流程设计器。
        </div>
      </div>
    </div>

    <div v-else class="form-permission-table">
      <div class="form-permission-row is-head">
        <span>字段</span>
        <span>可见</span>
        <span>可编辑</span>
        <span>必填</span>
      </div>
      <div
        v-for="row in rows"
        :key="row.permissionKey"
        class="form-permission-row"
        :class="{ stale: row.stale }"
      >
        <div class="form-field-cell">
          <div class="form-field-label">
            {{ row.label || row.field }}
            <n-tag v-if="row.sourceRequired" size="tiny" type="warning" :bordered="false">
              表单必填
            </n-tag>
            <n-tag v-if="row.stale" size="tiny" type="default" :bordered="false">
              历史字段
            </n-tag>
          </div>
          <div class="form-field-code">
            {{ row.scope === 'child' ? `${row.childKey}.${row.field}` : (row.scope === 'array' ? `${row.arrayKey}[].${row.field}` : row.field) }}
          </div>
        </div>
        <n-checkbox
          data-test="permission-readable"
          :checked="row.readable"
          :disabled="readonly"
          @update:checked="update(row.permissionKey, { readable: $event })"
        />
        <n-checkbox
          data-test="permission-writable"
          :checked="row.writable"
          :disabled="readonly || !row.readable"
          @update:checked="update(row.permissionKey, { writable: $event })"
        />
        <n-checkbox
          data-test="permission-required"
          :checked="row.required"
          :disabled="readonly || !row.readable"
          @update:checked="update(row.permissionKey, { required: $event })"
        />
      </div>
      <div v-if="childPermissionRows.length" class="child-permission-section">
        <div class="child-permission-title">
          子表行操作
        </div>
        <div v-for="child in childPermissionRows" :key="child.childKey" class="child-permission-row">
          <div>
            <div class="form-field-label">
              {{ child.label || child.childKey }}
            </div>
            <div class="form-field-code">
              {{ child.childKey }} · {{ child.fieldCount }} 个字段
            </div>
          </div>
          <label><span>可见</span><n-checkbox :checked="child.readable" :disabled="readonly" @update:checked="updateChild(child.childKey, { readable: $event })" /></label>
          <label><span>新增</span><n-checkbox data-test="child-allow-create" :checked="child.allowCreate" :disabled="readonly || !child.readable" @update:checked="updateChild(child.childKey, { allowCreate: $event })" /></label>
          <label><span>修改</span><n-checkbox data-test="child-allow-update" :checked="child.allowUpdate" :disabled="readonly || !child.readable" @update:checked="updateChild(child.childKey, { allowUpdate: $event })" /></label>
          <label><span>删除</span><n-checkbox data-test="child-allow-delete" :checked="child.allowDelete" :disabled="readonly || !child.readable" @update:checked="updateChild(child.childKey, { allowDelete: $event })" /></label>
        </div>
      </div>
      <div v-if="arrayPermissionRows.length" class="child-permission-section">
        <div class="child-permission-title">
          数组明细行操作
        </div>
        <div v-for="array in arrayPermissionRows" :key="array.arrayKey" class="child-permission-row">
          <div>
            <div class="form-field-label">
              {{ array.label || array.arrayKey }}
            </div>
            <div class="form-field-code">
              {{ array.arrayKey }} · {{ array.fieldCount }} 个行字段
            </div>
          </div>
          <label><span>可见</span><n-checkbox :checked="array.readable" :disabled="readonly" @update:checked="updateArray(array.arrayKey, { readable: $event })" /></label>
          <label><span>新增</span><n-checkbox data-test="array-allow-create" :checked="array.allowCreate" :disabled="readonly || !array.readable" @update:checked="updateArray(array.arrayKey, { allowCreate: $event })" /></label>
          <label><span>修改</span><n-checkbox data-test="array-allow-update" :checked="array.allowUpdate" :disabled="readonly || !array.readable" @update:checked="updateArray(array.arrayKey, { allowUpdate: $event })" /></label>
          <label><span>删除</span><n-checkbox data-test="array-allow-delete" :checked="array.allowDelete" :disabled="readonly || !array.readable" @update:checked="updateArray(array.arrayKey, { allowDelete: $event })" /></label>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-permission-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-permission-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.form-permission-title {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
}

.form-permission-desc {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.form-permission-empty {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  padding: 14px;
  color: #64748b;
}

.form-permission-empty i {
  margin-top: 2px;
  color: #2563eb;
  font-size: 20px;
}

.form-permission-empty-title {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.form-permission-empty-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
}

.form-permission-table {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.form-permission-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 64px 64px 64px;
  align-items: center;
  gap: 8px;
  min-height: 54px;
  padding: 8px 12px;
  background: #fff;
  border-top: 1px solid #eef2f7;
}

.child-permission-section {
  margin-top: 12px;
  border-top: 1px solid #e2e8f0;
  padding-top: 12px;
}

.child-permission-title {
  margin-bottom: 8px;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.child-permission-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) repeat(4, 64px);
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #f1f5f9;
  padding: 8px 0;
  color: #475569;
  font-size: 12px;
}

.child-permission-row label {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.form-permission-row:first-child {
  border-top: none;
}

.form-permission-row.is-head {
  min-height: 36px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.form-permission-row:not(.is-head):hover {
  background: #f8fafc;
}

.form-permission-row.stale {
  background: #fffbeb;
}

.form-field-cell {
  min-width: 0;
}

.form-field-label {
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.form-field-code {
  margin-top: 3px;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  word-break: break-all;
}
</style>
