<script setup>
/**
 * PermissionConfig — 8 个操作权限布尔开关
 *
 * 字段名对齐 user-task-parser DEFAULT_PERMISSIONS。
 * 指定节点驳回由流程级 allowMultiReturn 控制，不再在节点上展示 allowReturn。
 */
import { computed } from 'vue'

const props = defineProps({
  config: { type: Object, required: true },
  readonly: Boolean,
})

const emit = defineEmits(['update:config'])

const FIELDS = [
  { key: 'allowApprove', label: '允许通过', desc: '审批人可点击“通过”' },
  { key: 'allowReject', label: '允许驳回', desc: '审批人可点击“驳回”。若流程开启了指定节点驳回，点驳回后再选择回到哪个已审节点' },
  { key: 'allowRejectToStart', label: '驳回至发起人', desc: '按流程设计的发起人修改路径退回' },
  { key: 'allowDelegate', label: '允许委派', desc: '审批人可委托他人代审' },
  { key: 'allowTerminate', label: '允许终止', desc: '可直接终止流程' },
  { key: 'requireSignature', label: '强制签名', desc: '审批时必须电子签名' },
  { key: 'requireComment', label: '审批意见', desc: '同意或驳回流程时必须填写审批意见' },
]

function bool(key, fallback = false) {
  return computed({
    get: () => Boolean(props.config?.[key] ?? fallback),
    set: v => emit('update:config', { [key]: !!v }),
  })
}

// 创建一组 v-model 引用
const fields = FIELDS.map(({ key, label, desc, fallback }) => ({
  key,
  label,
  desc,
  model: bool(key, fallback),
}))
</script>

<template>
  <div class="space-y-2">
    <div
      v-for="f in fields"
      :key="f.key"
      class="flex items-start justify-between gap-3 border border-gray-100 rounded p-3"
    >
      <div class="flex-1">
        <div class="text-sm text-gray-700 font-medium">
          {{ f.label }}
        </div>
        <div class="text-xs text-gray-400">
          {{ f.desc }}
        </div>
      </div>
      <n-switch v-model:value="f.model.value" :disabled="readonly" />
    </div>
  </div>
</template>
