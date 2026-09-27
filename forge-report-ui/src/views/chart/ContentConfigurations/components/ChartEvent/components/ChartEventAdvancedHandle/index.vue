<template>
  <n-collapse-item title="高级事件配置" name="3">
    <n-alert type="warning" :bordered="false">
      生命周期 JavaScript 已停用，旧脚本仅供迁移查看，不会在主线程执行。
    </n-alert>
    <n-card v-if="legacyScripts.length" class="collapse-show-box go-mt-2">
      <n-space vertical>
        <n-text depth="3">检测到 {{ legacyScripts.length }} 个旧生命周期脚本。</n-text>
        <n-code :code="legacyScripts.join('\n\n')" language="text" />
        <n-button size="small" type="warning" tertiary @click="clearLegacyScripts">清除旧脚本</n-button>
      </n-space>
    </n-card>
  </n-collapse-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { EventLife } from '@/enums/eventEnum'
import { goDialog } from '@/utils'
import { useTargetData } from '../../../hooks/useTargetData.hook'

const { targetData } = useTargetData()
const legacyScripts = computed(() => Object.entries(targetData.value.events.advancedEvents || {})
  .filter(([, script]) => typeof script === 'string' && script.trim())
  .map(([eventName, script]) => `${eventName}:\n${script}`))

const clearLegacyScripts = () => {
  goDialog({
    message: '确认清除当前组件的旧生命周期脚本吗？此操作仅修改当前设计稿。',
    onPositiveCallback: () => {
      targetData.value.events.advancedEvents = {
        [EventLife.VNODE_BEFORE_MOUNT]: undefined,
        [EventLife.VNODE_MOUNTED]: undefined
      }
    }
  })
}
</script>
