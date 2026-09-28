<template>
  <n-collapse-item title="基础事件配置" name="2">
    <n-alert type="warning" :bordered="false">
      自定义 JavaScript 事件已停用。页面跳转、打开弹窗和关闭弹窗请使用结构化组件动作。
    </n-alert>
    <n-card v-if="legacyScripts.length" class="collapse-show-box go-mt-2">
      <n-space vertical>
        <n-text depth="3">检测到 {{ legacyScripts.length }} 个旧脚本；这些脚本不会在预览或发布页面执行。</n-text>
        <n-code :code="legacyScripts.join('\n\n')" language="text" />
        <n-button size="small" type="warning" tertiary @click="clearLegacyScripts">清除旧脚本</n-button>
      </n-space>
    </n-card>
  </n-collapse-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BaseEvent } from '@/enums/eventEnum'
import { goDialog } from '@/utils'
import { useTargetData } from '../../../hooks/useTargetData.hook'

const { targetData } = useTargetData()
const legacyScripts = computed(() => Object.entries(targetData.value.events.baseEvent || {})
  .filter(([, script]) => typeof script === 'string' && script.trim())
  .map(([eventName, script]) => `${eventName}:\n${script}`))

const clearLegacyScripts = () => {
  goDialog({
    message: '确认清除当前组件的旧 JavaScript 事件吗？此操作仅修改当前设计稿。',
    onPositiveCallback: () => {
      targetData.value.events.baseEvent = {
        [BaseEvent.ON_CLICK]: undefined,
        [BaseEvent.ON_DBL_CLICK]: undefined,
        [BaseEvent.ON_MOUSE_ENTER]: undefined,
        [BaseEvent.ON_MOUSE_LEAVE]: undefined
      }
    }
  })
}
</script>
