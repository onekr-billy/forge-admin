<template>
  <n-modal
    v-model:show="visible"
    :title="`代码预览 - ${tableName}`"
    preset="card"
    style="width: min(1400px, calc(100vw - 48px))"
    :mask-closable="false"
  >
    <div class="code-preview-modal">
      <n-spin :show="loading">
        <div class="preview-container">
          <div class="file-list">
            <div class="file-list-header">
              <span>目录</span>
              <span>{{ fileCount }} 个文件</span>
            </div>
            <n-tree
              block-line
              expand-on-click
              :data="fileTree"
              :expanded-keys="expandedKeys"
              :selected-keys="selectedKeys"
              :render-label="renderTreeLabel"
              @update:expanded-keys="handleExpandedKeys"
              @update:selected-keys="handleTreeSelect"
            />
          </div>

          <div class="code-viewer">
            <div class="code-header">
              <span class="file-name">{{ selectedFile || '请选择文件' }}</span>
              <n-button size="small" @click="handleCopy">
                <template #icon>
                  <i class="i-material-symbols:content-copy" />
                </template>
                复制
              </n-button>
            </div>
            <div v-if="selectedFile" ref="editorContainer" class="editor-container" />
            <div v-else class="preview-empty">
              暂无可预览文件
            </div>
          </div>
        </div>
      </n-spin>
    </div>

    <template #footer>
      <n-space justify="end">
        <n-button @click="handleClose">
          关闭
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import { java } from '@codemirror/lang-java'
import { javascript } from '@codemirror/lang-javascript'
import { sql } from '@codemirror/lang-sql'
import { xml } from '@codemirror/lang-xml'
import { oneDark } from '@codemirror/theme-one-dark'
import { basicSetup, EditorView } from 'codemirror'
import { computed, h, nextTick, onUnmounted, ref, watch } from 'vue'
import { request } from '@/utils'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  tableName: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:show'])

const visible = ref(false)
const loading = ref(false)
const selectedFile = ref('')
const fileMap = ref({})
const fileTree = ref([])
const expandedKeys = ref([])
const editorContainer = ref(null)
let editorView = null

const selectedKeys = computed(() => selectedFile.value ? [selectedFile.value] : [])
const fileCount = computed(() => Object.keys(fileMap.value).length)

// 监听 show 变化
watch(() => props.show, (val) => {
  visible.value = val
  if (val && props.tableName) {
    loadPreview()
  }
})

watch(visible, (val) => {
  emit('update:show', val)
  if (!val) {
    destroyEditor()
  }
})

// 加载预览代码
async function loadPreview() {
  try {
    loading.value = true
    const res = await request.get(`/generator/preview/${props.tableName}`)
    if (res.code === 200) {
      fileMap.value = res.data || {}
      const keys = Object.keys(fileMap.value).sort((a, b) => a.localeCompare(b))
      const treeResult = buildFileTree(keys)
      fileTree.value = treeResult.tree
      expandedKeys.value = treeResult.expandedKeys

      // 自动选中第一个文件
      if (keys.length > 0) {
        selectedFile.value = keys[0]
        await nextTick()
        initEditor(fileMap.value[keys[0]], keys[0])
      }
      else {
        selectedFile.value = ''
        destroyEditor()
      }
    }
  }
  catch (error) {
    console.error('加载预览失败:', error)
    window.$message.error('加载预览失败')
  }
  finally {
    loading.value = false
  }
}

function buildFileTree(paths) {
  const root = []
  const nodeMap = new Map()
  const directoryKeys = []

  paths.forEach((path) => {
    const parts = path.split('/').filter(Boolean)
    let currentChildren = root
    let currentPath = ''

    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1
      currentPath = currentPath ? `${currentPath}/${part}` : part
      const key = isFile ? path : `dir:${currentPath}`

      let node = nodeMap.get(key)
      if (!node) {
        node = {
          key,
          label: part,
          type: isFile ? 'file' : 'directory',
          children: isFile ? undefined : [],
        }
        nodeMap.set(key, node)
        currentChildren.push(node)

        if (!isFile) {
          directoryKeys.push(key)
        }
      }

      if (!isFile) {
        currentChildren = node.children
      }
    })
  })

  sortTree(root)
  return {
    tree: root,
    expandedKeys: directoryKeys,
  }
}

function sortTree(nodes) {
  nodes.sort((a, b) => {
    if (a.type !== b.type) {
      return a.type === 'directory' ? -1 : 1
    }
    return a.label.localeCompare(b.label)
  })
  nodes.forEach((node) => {
    if (node.children) {
      sortTree(node.children)
    }
  })
}

function renderTreeLabel({ option }) {
  return h('span', { class: ['tree-node-label', option.type === 'directory' ? 'is-directory' : 'is-file'] }, [
    h('i', {
      class: option.type === 'directory'
        ? 'i-material-symbols:folder-outline-rounded'
        : 'i-material-symbols:description-outline-rounded',
    }),
    h('span', option.label),
  ])
}

// 获取语言支持
function getLanguageSupport(filename) {
  if (filename.endsWith('.java'))
    return java()
  if (filename.endsWith('.xml'))
    return xml()
  if (filename.endsWith('.vue') || filename.endsWith('.js') || filename.endsWith('.ts'))
    return javascript()
  if (filename.endsWith('.sql'))
    return sql()
  return []
}

// 初始化编辑器
function initEditor(code, filename) {
  destroyEditor()

  if (!editorContainer.value)
    return

  const languageSupport = getLanguageSupport(filename)
  const extensions = [
    basicSetup,
    oneDark,
    EditorView.editable.of(false),
    // 移除 lineWrapping 以显示水平滚动条
  ]

  if (languageSupport) {
    extensions.splice(1, 0, languageSupport)
  }

  editorView = new EditorView({
    doc: code || '',
    extensions,
    parent: editorContainer.value,
  })
}

// 销毁编辑器
function destroyEditor() {
  if (editorView) {
    editorView.destroy()
    editorView = null
  }
}

function handleExpandedKeys(keys) {
  expandedKeys.value = keys
}

// 文件选择变化
async function handleTreeSelect(keys) {
  const key = keys?.[0]
  if (!key || !fileMap.value[key]) {
    return
  }
  selectedFile.value = key
  await nextTick()
  initEditor(fileMap.value[key], key)
}

// 复制代码
function handleCopy() {
  const code = fileMap.value[selectedFile.value]
  if (code) {
    navigator.clipboard.writeText(code).then(() => {
      window.$message.success('复制成功')
    }).catch(() => {
      window.$message.error('复制失败')
    })
  }
}

// 关闭弹窗
function handleClose() {
  visible.value = false
}

// 组件卸载时销毁编辑器
onUnmounted(() => {
  destroyEditor()
})
</script>

<style scoped>
.code-preview-modal {
  height: min(72vh, 720px);
  min-height: 460px;
  overflow: hidden;
}

.code-preview-modal :deep(.n-spin-container),
.code-preview-modal :deep(.n-spin-content) {
  height: 100%;
}

.preview-container {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  height: 100%;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
  overflow: hidden;
  min-height: 0;
}

.file-list {
  border-right: 1px solid var(--n-border-color);
  background-color: var(--n-color-modal);
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.file-list-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 12px;
  font-size: 12px;
  color: var(--n-text-color-3);
  background-color: var(--n-color-modal);
  border-bottom: 1px solid var(--n-border-color);
}

.file-list :deep(.n-tree) {
  padding: 8px 6px 12px;
}

.file-list :deep(.n-tree-node-content) {
  min-width: 0;
}

.file-list :deep(.n-tree-node-content__text) {
  min-width: 0;
}

.file-list :deep(.tree-node-label) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  font-size: 13px;
}

.file-list :deep(.tree-node-label i) {
  flex: 0 0 auto;
  color: var(--n-text-color-3);
}

.file-list :deep(.tree-node-label span) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code-viewer {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: #282c34;
  border-bottom: 1px solid #3e4451;
  flex-shrink: 0;
  gap: 12px;
}

.file-name {
  font-size: 13px;
  color: #abb2bf;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.editor-container {
  flex: 1;
  overflow: hidden;
  position: relative;
  min-height: 0;
  background-color: #282c34;
}

.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: #8b949e;
  background-color: #282c34;
}

.editor-container :deep(.cm-editor) {
  height: 100%;
  width: 100%;
}

.editor-container :deep(.cm-scroller) {
  overflow: auto !important;
  height: 100%;
  max-height: 100%;
}

.editor-container :deep(.cm-content) {
  min-height: 100%;
}

/* 确保滚动条样式可见 */
.editor-container :deep(.cm-scroller)::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

.editor-container :deep(.cm-scroller)::-webkit-scrollbar-track {
  background: #282c34;
}

.editor-container :deep(.cm-scroller)::-webkit-scrollbar-thumb {
  background: #4e5561;
  border-radius: 5px;
}

.editor-container :deep(.cm-scroller)::-webkit-scrollbar-thumb:hover {
  background: #5c6370;
}
</style>
