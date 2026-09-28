<template>
  <n-collapse-item title="基础事件配置" name="2">
    <template #header-extra>
      <n-button type="primary" tertiary size="small" @click.stop="showModal = true">
        <template #icon>
          <n-icon>
            <pencil-icon />
          </n-icon>
        </template>
        编辑
      </n-button>
    </template>
    <n-card class="collapse-show-box">
      <!-- 函数体 -->
      <div v-for="eventName in BaseEvent" :key="eventName">
        <p>
          <span class="func-annotate">// {{ EventTypeName[eventName] }}</span>
          <br />
          <span class="func-keyword">async {{ eventName }}</span> (mouseEvent,components) {
        </p>
        <p class="go-ml-4">
          <n-code :code="(targetData.events.baseEvent || {})[eventName] || ''" language="typescript"></n-code>
        </p>
        <p>}<span>,</span></p>
      </div>
    </n-card>
  </n-collapse-item>

  <!-- 弹窗 -->
  <n-modal class="go-chart-data-monaco-editor" v-model:show="showModal" :mask-closable="false" style="width: 1100px">
    <n-card class="event-modal-card" :bordered="false" role="dialog" size="small" aria-modal="true">
      <template #header>
        <div class="modal-header">
          <n-icon size="20" color="var(--app-theme)"><pencil-icon /></n-icon>
          <n-text strong>基础事件编辑器</n-text>
        </div>
      </template>

      <template #header-extra>
        <n-tag :bordered="false" type="warning" size="small">ECharts 组件会拦截鼠标事件</n-tag>
      </template>

      <n-layout has-sider sider-placement="right" class="modal-layout">
        <n-layout class="editor-pane">
          <n-tabs v-model:value="editTab" type="segment" size="small">
            <n-tab-pane
              v-for="(eventName, index) in BaseEvent"
              :key="index"
              :tab="EventTypeName[eventName]"
              :name="eventName"
            >
              <div class="func-signature">
                <span class="func-kw">async function</span>
                <span class="func-name">{{ eventName }}</span>
                <span class="func-params">(mouseEvent, components) {</span>
              </div>
              <div class="monaco-wrap">
                <monaco-editor v-model:modelValue="baseEvent[eventName]" height="440px" language="javascript" />
              </div>
              <p class="func-close">}</p>
            </n-tab-pane>
          </n-tabs>
        </n-layout>

        <n-layout-sider
          class="modal-sider"
          :collapsed-width="14"
          :width="320"
          show-trigger="bar"
          collapse-mode="transform"
        >
          <n-tabs default-value="1" justify-content="space-evenly" type="segment" size="small">
            <n-tab-pane tab="验证结果" name="1">
              <n-scrollbar trigger="none" style="max-height: 460px">
                <n-collapse arrow-placement="right" :default-expanded-names="[1, 2, 3]">
                  <template v-for="error in [validEvents()]" :key="error">
                    <n-collapse-item title="错误函数" :name="1" style="padding:10px">
                      <n-text depth="3">{{ error.errorFn || '暂无' }}</n-text>
                    </n-collapse-item>
                    <n-collapse-item title="错误信息" :name="2" style="padding:10px">
                      <n-text depth="3">{{ error.name || '暂无' }}</n-text>
                    </n-collapse-item>
                    <n-collapse-item title="堆栈信息" :name="3" style="padding:10px">
                      <n-text depth="3">{{ error.message || '暂无' }}</n-text>
                    </n-collapse-item>
                  </template>
                </n-collapse>
              </n-scrollbar>
            </n-tab-pane>
            <n-tab-pane tab="变量说明" name="2">
              <n-scrollbar trigger="none" style="max-height: 460px">
                <n-collapse arrow-placement="right" :default-expanded-names="[1]">
                  <n-collapse-item title="mouseEvent" :name="1" style="padding:10px">
                    <n-text depth="3">鼠标事件对象</n-text>
                  </n-collapse-item>
                </n-collapse>
              </n-scrollbar>
            </n-tab-pane>
          </n-tabs>
        </n-layout-sider>
      </n-layout>

      <template #action>
        <n-space justify="space-between" class="modal-action">
          <div class="action-hint">
            <n-icon size="16" :depth="3"><document-text-icon /></n-icon>
            <n-text depth="3">编写方式同正常 JavaScript 写法</n-text>
          </div>
          <n-space>
            <n-button size="small" ghost @click="closeEvents">取消</n-button>
            <n-button size="small" type="primary" @click="saveEvents">保存</n-button>
          </n-space>
        </n-space>
      </template>
    </n-card>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch, toRefs, toRaw } from 'vue'
import { MonacoEditor } from '@/components/Pages/MonacoEditor'
import { useTargetData } from '../../../hooks/useTargetData.hook'
import { BaseEvent } from '@/enums/eventEnum'
import { icon } from '@/plugins'

const { targetData, chartEditStore } = useTargetData()
const { DocumentTextIcon, ChevronDownIcon, PencilIcon } = icon.ionicons5

const EventTypeName = {
  [BaseEvent.ON_CLICK]: '单击',
  [BaseEvent.ON_DBL_CLICK]: '双击',
  [BaseEvent.ON_MOUSE_ENTER]: '鼠标进入',
  [BaseEvent.ON_MOUSE_LEAVE]: '鼠标移出'
}

// 受控弹窗
const showModal = ref(false)
// 编辑区域控制
const editTab = ref(BaseEvent.ON_CLICK)
// events 函数模板
let baseEvent = ref({ ...targetData.value.events.baseEvent })
// 事件错误标识
const errorFlag = ref(false)

// 验证语法
const validEvents = () => {
  let errorFn = ''
  let message = ''
  let name = ''

  errorFlag.value = Object.entries(baseEvent.value).every(([eventName, str]) => {
    try {
      // 支持await，验证语法
      const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor
      new AsyncFunction(str)
      return true
    } catch (error: any) {
      message = error.message
      name = error.name
      errorFn = eventName
      return false
    }
  })
  return {
    errorFn,
    message,
    name
  }
}

// 关闭事件
const closeEvents = () => {
  showModal.value = false
}

// 新增事件
const saveEvents = () => {
  if (validEvents().errorFn) {
    window['$message'].error('事件函数错误，无法进行保存')
    return
  }
  if (Object.values(baseEvent.value).join('').trim() === '') {
    // 清空事件
    targetData.value.events.baseEvent = {
      [BaseEvent.ON_CLICK]: undefined,
      [BaseEvent.ON_DBL_CLICK]: undefined,
      [BaseEvent.ON_MOUSE_ENTER]: undefined,
      [BaseEvent.ON_MOUSE_LEAVE]: undefined
    }
  } else {
    targetData.value.events.baseEvent = { ...baseEvent.value }
  }
  closeEvents()
}

watch(
  () => showModal.value,
  (newData: boolean) => {
    if (newData) {
      baseEvent.value = { ...targetData.value.events.baseEvent }
    }
  }
)
</script>

<style lang="scss" scoped>
@import '../index.scss';
</style>
