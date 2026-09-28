<template>
  <image
    class="ai-auth-image"
    :class="imgClass"
    :src="currentSrc"
    :mode="mode"
    @load="$emit('load', $event)"
    @error="handleError"
  />
</template>

<script setup>
import { ref, watch } from 'vue'
import { resolveRenderableFileUrl } from '@/utils/file'

const props = defineProps({
  src: {
    type: [String, Object],
    default: '',
  },
  fallback: {
    type: String,
    default: '',
  },
  mode: {
    type: String,
    default: 'aspectFill',
  },
  imgClass: {
    type: [String, Array, Object],
    default: '',
  },
})

const emit = defineEmits(['load', 'error'])
const currentSrc = ref('')
let loadSeq = 0
let retriedInputKey = ''

watch([() => sourceKey(props.src), () => props.fallback], () => {
  retriedInputKey = ''
  loadImage()
}, { immediate: true })

async function loadImage(forceRefresh = false) {
  const seq = ++loadSeq
  // 文件 ID 需要异步换取访问地址，先显示品牌兜底，避免请求期间出现空白头像。
  currentSrc.value = props.fallback
  if (!props.src) {
    return
  }

  try {
    const url = await resolveRenderableFileUrl(props.src, { forceRefresh })
    if (seq !== loadSeq) {
      return
    }
    currentSrc.value = url || props.fallback
  }
  catch (error) {
    if (seq !== loadSeq) {
      return
    }
    currentSrc.value = props.fallback
    emit('error', error)
  }
}

function handleError(error) {
  const failedSource = String(currentSrc.value || '')
  const fallbackSource = String(props.fallback || '')
  const inputKey = sourceKey(props.src)
  // 重试次数必须按原始文件 ID 计数，不能按每次都会变化的临时签名 URL 计数。
  // 兜底图自身失败也不能触发源文件刷新，否则隐藏页面仍会持续请求文件地址接口。
  if (inputKey
    && failedSource
    && failedSource !== fallbackSource
    && !failedSource.startsWith('blob:')
    && retriedInputKey !== inputKey) {
    retriedInputKey = inputKey
    loadImage(true)
    return
  }
  if (props.fallback && currentSrc.value !== props.fallback) {
    currentSrc.value = props.fallback
    return
  }
  emit('error', error)
}

function sourceKey(source) {
  if (!source || typeof source !== 'object') return String(source || '').trim()
  return String(source.accessUrl || source.fileId || source.filePath || source.url || '').trim()
}
</script>

<style lang="scss" scoped>
.ai-auth-image {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
