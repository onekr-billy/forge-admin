<template>
  <section class="settings-section-card">
    <header>
      <h2>应用权限</h2>
      <p>这里配置应用级可见范围；页面与数据权限仍在应用权限工作台中维护。</p>
    </header>
    <n-alert type="info" :bordered="false" class="settings-info-alert">
      应用管理员自动拥有所有页面权限。角色页面授权和数据范围继续复用 Forge RBAC 与 DataScope。
    </n-alert>
    <n-form label-placement="top">
      <n-form-item label="可见范围">
        <n-radio-group :value="permission.visibility" @update:value="patch({ visibility: $event })">
          <n-radio-button value="all">
            组织全员
          </n-radio-button>
          <n-radio-button value="roles">
            指定角色
          </n-radio-button>
          <n-radio-button value="departments">
            指定部门
          </n-radio-button>
          <n-radio-button value="users">
            指定用户
          </n-radio-button>
        </n-radio-group>
      </n-form-item>
      <n-form-item label="应用管理员 ID">
        <n-dynamic-tags :value="permission.administrators" @update:value="patch({ administrators: normalizeIds($event) })" />
      </n-form-item>
      <n-form-item v-if="permission.visibility === 'roles'" label="可见角色 ID">
        <n-dynamic-tags :value="permission.roleIds" @update:value="patch({ roleIds: normalizeIds($event) })" />
      </n-form-item>
      <n-form-item v-if="permission.visibility === 'departments'" label="可见部门 ID">
        <n-dynamic-tags :value="permission.departmentIds" @update:value="patch({ departmentIds: normalizeIds($event) })" />
      </n-form-item>
      <n-form-item v-if="permission.visibility === 'users'" label="可见用户 ID">
        <n-dynamic-tags :value="permission.userIds" @update:value="patch({ userIds: normalizeIds($event) })" />
      </n-form-item>
    </n-form>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
const permission = computed(() => props.modelValue.permission || {})

function patch(value) {
  emit('update:modelValue', {
    ...props.modelValue,
    permission: { ...permission.value, ...value },
  })
}

function normalizeIds(values) {
  return [...new Set((values || []).map(value => String(value || '').trim()).filter(Boolean))]
}
</script>
