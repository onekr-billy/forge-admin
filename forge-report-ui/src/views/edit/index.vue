<template>
  <div class="go-edit">
    <div class="edit-bg-orb"></div>
    <n-layout class="edit-layout">
      <n-layout-header class="go-edit-header">
        <div class="header-left">
          <n-text class="go-edit-title">页面在线编辑器</n-text>
          <n-button v-if="showOpenFilePicker" size="small" ghost @click="importJSON">
            <template #icon>
              <n-icon><download-icon /></n-icon>
            </template>
            导入
          </n-button>
        </div>
        <n-space align="center">
          <n-tag :bordered="false" size="small"> Ctrl + S 更新视图 </n-tag>
          <n-button v-if="showOpenFilePicker" size="small" ghost type="primary" @click="updateSync">
            <template #icon>
              <n-icon><analytics-icon /></n-icon>
            </template>
            保存
          </n-button>
        </n-space>
      </n-layout-header>
      <n-layout-content class="edit-content">
        <div class="editor-wrapper">
          <monaco-editor
            v-model:modelValue="content"
            language="json"
            :editorOptions="{
              lineNumbers: 'on',
              minimap: { enabled: true }
            }"
          />
        </div>
      </n-layout-content>
    </n-layout>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { MonacoEditor } from '@/components/Pages/MonacoEditor'
import { SavePageEnum } from '@/enums/editPageEnum'
import { getSessionStorageInfo } from '../preview/utils'
import { setSessionStorage, JSONStringify, JSONParse, setTitle, goDialog } from '@/utils'
import { StorageEnum } from '@/enums/storageEnum'
import { icon } from '@/plugins'
import type { ChartEditStorageType } from '../preview/index.d'

const { ChevronBackOutlineIcon, DownloadIcon, AnalyticsIcon } = icon.ionicons5
const showOpenFilePicker: Function = (window as any).showOpenFilePicker
const content = ref('')

window['$message'].warning('请不要刷新此窗口！')

// 从sessionStorage 获取数据
async function getDataBySession() {
  const localStorageInfo: ChartEditStorageType = (await getSessionStorageInfo()) as unknown as ChartEditStorageType
  setTitle(`编辑-${localStorageInfo.editCanvasConfig.projectName}`)
  content.value = JSONStringify(localStorageInfo)
}
setTimeout(getDataBySession)

// 返回父窗口
function back() {
  window.opener.name = Date.now()
  window.open(window.opener.location.href, window.opener.name)
}

// 导入json文本
function importJSON() {
  goDialog({
    message: '导入数据将覆盖内容，此操作不可撤回，是否继续？',
    isMaskClosable: true,
    transformOrigin: 'center',
    onPositiveCallback: async () => {
      try {
        const files = await showOpenFilePicker()
        const file = await files[0].getFile()
        const fr = new FileReader()
        fr.readAsText(file)
        fr.onloadend = () => {
          content.value = (fr.result || '').toString()
        }
        window['$message'].success('导入成功！')
      } catch (error) {
        window['$message'].error('导入失败，请检查文件是否损坏！')
        console.log(error)
      }
    }
  })
}

// 同步数据编辑页
window.opener.addEventListener(SavePageEnum.CHART, (e: any) => {
  window['$message'].success('正在进行更新...')
  setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, [e.detail])
  content.value = JSONStringify(e.detail)
})

// 保存按钮同步数据
document.addEventListener('keydown', function (e) {
  if (e.keyCode == 83 && (navigator.platform.match('Mac') ? e.metaKey : e.ctrlKey)) {
    e.preventDefault()
    updateSync()
  }
})

// 失焦保存（暂时关闭）
// addEventListener('blur', updateSync)

// 同步更新
async function updateSync() {
  if (!window.opener) {
    return window['$message'].error('源窗口已关闭，视图同步失败！')
  }
  goDialog({
    message: '是否覆盖源视图内容? 此操作不可撤！',
    isMaskClosable: true,
    transformOrigin: 'center',
    onPositiveCallback: () => {
      try {
        const detail = JSONParse(content.value)
        delete detail.id
        // 保持id不变
        window.opener.dispatchEvent(new CustomEvent(SavePageEnum.JSON, { detail }))
        window['$message'].success('正在同步内容...')
      } catch (e) {
        window['$message'].error('内容格式有误')
        console.log(e)
      }
    }
  })
}

// 关闭页面发送关闭事件
window.onbeforeunload = () => {
  if (window.opener) {
    window.opener.dispatchEvent(new CustomEvent(SavePageEnum.CLOSE))
  }
}
</script>

<style lang="scss" scoped>
.go-edit {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #06090f;

  .edit-bg-orb {
    position: fixed;
    top: -20%;
    right: -10%;
    width: 500px;
    height: 500px;
    border-radius: 50%;
    background: rgba(var(--app-theme-rgb), 0.1);
    filter: blur(120px);
    pointer-events: none;
    z-index: 0;
    animation: orbBreath 10s ease-in-out infinite alternate;
  }

  @keyframes orbBreath {
    0% { transform: scale(1); opacity: 0.6; }
    100% { transform: scale(1.3); opacity: 1; }
  }

  .edit-layout {
    position: relative;
    z-index: 1;
    background: transparent;
  }

  .go-edit-header {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 56px;
    padding: 0 22px;
    background: rgba(10, 14, 23, 0.86);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.1);

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 0;
      width: 100%;
      height: 1px;
      background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.35), transparent);
      pointer-events: none;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .go-edit-title {
      font-size: 16px;
      font-weight: 700;
      letter-spacing: 1px;
      color: rgba(226, 232, 240, 0.92);
      text-shadow: 0 0 12px rgba(var(--app-theme-rgb), 0.2);
    }

    :deep(.n-button) {
      height: 32px;
      border-radius: 8px;
      border-color: rgba(var(--app-theme-rgb), 0.14);
      background: rgba(15, 23, 42, 0.36);
      transition: all 0.22s ease;

      &:hover {
        border-color: rgba(var(--app-theme-rgb), 0.28);
        background: rgba(var(--app-theme-rgb), 0.1);
        box-shadow: 0 0 16px rgba(var(--app-theme-rgb), 0.16);
        transform: translateY(-1px);
      }
    }

    :deep(.n-tag) {
      height: 28px;
      font-size: 11px;
      background: rgba(var(--app-theme-rgb), 0.08);
      color: rgba(var(--app-theme-rgb), 0.78);
      border: 1px solid rgba(var(--app-theme-rgb), 0.1);
      border-radius: 8px;
      letter-spacing: 0.5px;
    }
  }

  .edit-content {
    position: relative;

    .editor-wrapper {
      margin: 12px;
      height: calc(100vh - 56px - 24px);
      border-radius: 12px;
      overflow: hidden;
      border: 1px solid rgba(var(--app-theme-rgb), 0.08);
      box-shadow:
        0 0 60px rgba(0, 0, 0, 0.4),
        inset 0 1px 0 rgba(255, 255, 255, 0.02);
      background: #0a0e17;
    }
  }

  @include deep() {
    .go-editor-area {
      height: calc(100vh - 56px - 24px) !important;
    }
  }
}
</style>
