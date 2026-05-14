<template>
  <n-modal v-model:show="visible" preset="card" title="新建大屏" class="project-create-modal" style="width: 560px">
    <n-spin :show="loadingDirectories">
      <n-form ref="formRef" :model="formModel" :rules="rules" label-placement="top">
        <n-form-item label="大屏名称" path="projectName">
          <n-input v-model:value="formModel.projectName" maxlength="100" placeholder="请输入大屏名称" />
        </n-form-item>
        <n-form-item label="所属目录" path="directoryId">
          <n-tree-select
            v-model:value="formModel.directoryId"
            :options="directoryOptions"
            :disabled="!directoryOptions.length"
            clearable
            filterable
            placeholder="请选择所属目录"
          />
        </n-form-item>
      </n-form>

      <n-alert v-if="!loadingDirectories && !directoryOptions.length" type="warning" :show-icon="false">
        当前还没有可用目录，请先在项目列表页创建目录后再新建大屏。
      </n-alert>
    </n-spin>

    <template #action>
      <div class="modal-actions">
        <n-button @click="visible = false">取消</n-button>
        <n-button type="primary" :loading="submitting" :disabled="!directoryOptions.length" @click="handleCreate">
          创建并进入编辑
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInst, FormRules } from 'naive-ui'
import { ChartEnum } from '@/enums/pageEnum'
import { createProjectApi, getProjectDirectoryTreeApi, type ReportDirectory } from '@/api/project'
import { fetchPathByName, getUUID, routerTurnByPath } from '@/utils'

const props = defineProps({
  show: {
    type: Boolean,
    default: false
  },
  defaultDirectoryId: {
    type: [String, Number],
    default: undefined
  }
})

const emit = defineEmits(['update:show', 'success'])

const formRef = ref<FormInst | null>(null)
const loadingDirectories = ref(false)
const submitting = ref(false)
const directoryOptions = ref<any[]>([])
const availableDirectoryKeys = ref<string[]>([])

const formModel = reactive({
  projectName: '新项目',
  directoryId: undefined as string | number | undefined
})

const rules: FormRules = {
  projectName: [
    { required: true, message: '请输入大屏名称', trigger: ['input', 'blur'] }
  ],
  directoryId: [
    { required: true, message: '请选择所属目录', trigger: ['change', 'blur'] }
  ]
}

const visible = computed({
  get: () => props.show,
  set: (value: boolean) => emit('update:show', value)
})

const mapTreeOptions = (directories: ReportDirectory[]) => {
  return (directories || []).map(directory => ({
    label: directory.directoryName,
    key: String(directory.id),
    children: mapTreeOptions(directory.children || [])
  }))
}

const flattenOptionKeys = (options: any[]) => {
  const keys: string[] = []
  const loop = (nodes: any[]) => {
    nodes.forEach(node => {
      keys.push(String(node.key))
      if (Array.isArray(node.children) && node.children.length) {
        loop(node.children)
      }
    })
  }
  loop(options)
  return keys
}

const resetForm = () => {
  formModel.projectName = '新项目'
  const preferredKey = props.defaultDirectoryId == null ? '' : String(props.defaultDirectoryId)
  if (preferredKey && availableDirectoryKeys.value.includes(preferredKey)) {
    formModel.directoryId = preferredKey
    return
  }
  formModel.directoryId = availableDirectoryKeys.value[0]
}

const loadDirectoryTree = async () => {
  try {
    loadingDirectories.value = true
    const res = await getProjectDirectoryTreeApi()
    directoryOptions.value = mapTreeOptions(res?.data || [])
    availableDirectoryKeys.value = flattenOptionKeys(directoryOptions.value)
    resetForm()
  } catch (error: any) {
    directoryOptions.value = []
    availableDirectoryKeys.value = []
    formModel.directoryId = undefined
    window.$message.error(error?.message || '获取目录列表失败')
  } finally {
    loadingDirectories.value = false
  }
}

const handleCreate = async () => {
  if (!directoryOptions.value.length) {
    window.$message.warning('请先创建目录')
    return
  }
  await formRef.value?.validate()
  submitting.value = true
  try {
    const res = await createProjectApi({
      projectName: formModel.projectName.trim(),
      directoryId: formModel.directoryId,
      canvasWidth: 1920,
      canvasHeight: 1080,
      backgroundColor: '',
      componentData: JSON.stringify({
        editCanvasConfig: { projectName: formModel.projectName.trim(), width: 1920, height: 1080 },
        requestGlobalConfig: {},
        componentList: []
      }),
      status: '0'
    })
    const id = res?.data?.id || getUUID()
    emit('success', res?.data)
    visible.value = false
    const path = fetchPathByName(ChartEnum.CHART_HOME_NAME, 'href')
    routerTurnByPath(path, [String(id)], undefined, true)
  } catch (error: any) {
    window.$message.error(error?.message || '创建项目失败')
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.show,
  async show => {
    if (!show) {
      return
    }
    await loadDirectoryTree()
  }
)
</script>

<style scoped lang="scss">
.project-create-modal {
  @include deep() {
    .n-card-header {
      padding-bottom: 12px;
    }
  }
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
