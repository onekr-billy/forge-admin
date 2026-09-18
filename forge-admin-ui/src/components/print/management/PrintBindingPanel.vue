<script setup>
import { NAlert, NButton, NSelect, NSpace } from 'naive-ui'
import { computed, ref, watch } from 'vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { usePrintTemplateStore } from '@/stores/print/printTemplateStore'

const props = defineProps({ canManage: Boolean })
const store = usePrintTemplateStore()
const { dict } = useDict('sys_print_scene')
const scenes = computed(() => dict.value.sys_print_scene || [])
const binding = computed(() => store.bindings.find(item => String(item.templateId) === String(store.row?.id)))
const busy = ref(false)
const error = ref('')
async function run(action) {
  if (busy.value)
    return
  busy.value = true
  error.value = ''
  try {
    await action()
  }
  catch (reason) {
    error.value = reason.message || '绑定操作失败'
  }
  finally {
    busy.value = false
  }
}
watch(() => store.scene, () => run(() => store.loadBindings()), { immediate: true })
</script>

<template>
  <NSelect v-model:value="store.scene" aria-label="打印场景" :options="scenes" :disabled="busy" />
  <p>配置当前表单在此场景的模板；随应用发布后生效。默认模板会替换同场景的原默认项。</p>
  <NAlert v-if="error" type="error">
    {{ error }}
  </NAlert>
  <p v-if="binding">
    <DictTag dict-type="sys_normal_disable" :value="binding.status" />
  </p>
  <p>{{ binding ? (binding.isDefault ? '当前为默认模板' : '当前已有绑定') : '当前场景尚未绑定此模板' }}</p>
  <NSpace v-if="props.canManage">
    <NButton :disabled="busy" type="primary" @click="run(() => store.bind(true))">
      设为默认模板
    </NButton>
    <NButton :disabled="busy" @click="run(() => store.bind(false))">
      {{ binding ? '取消默认并启用' : '添加绑定' }}
    </NButton>
    <NButton v-if="binding" :disabled="busy" @click="run(() => store.bind(false, 0))">
      停用绑定
    </NButton>
    <NButton v-if="binding" :disabled="busy" type="error" secondary @click="run(() => store.unbind(binding))">
      解除绑定
    </NButton>
  </NSpace>
</template>
