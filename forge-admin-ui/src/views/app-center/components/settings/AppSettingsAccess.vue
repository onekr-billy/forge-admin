<template>
  <section class="settings-section-card">
    <header>
      <h2>访问地址</h2>
      <p>门户地址在当前租户内唯一，修改后需重新发布才会影响正式门户。</p>
    </header>
    <n-form label-placement="top">
      <n-form-item label="门户 slug" :validation-status="slugStatus" :feedback="slugFeedback">
        <n-input-group>
          <n-input-group-label>/app/</n-input-group-label>
          <n-input
            :value="modelValue.portalSlug"
            maxlength="50"
            placeholder="例如 crm-portal"
            @update:value="handleSlugInput"
            @blur="checkSlug"
          />
        </n-input-group>
      </n-form-item>
      <div class="access-link-card">
        <div>
          <span>组织内访问链接</span>
          <strong>{{ portalUrl }}</strong>
        </div>
        <n-space>
          <n-button secondary @click="copyLink">
            复制链接
          </n-button>
          <n-button type="primary" :disabled="!modelValue.portalSlug" @click="openPortal">
            新窗口打开
          </n-button>
        </n-space>
      </div>
      <div v-if="modelValue.portalSlug" class="access-qrcode">
        <n-qr-code :value="portalUrl" :size="132" :color="modelValue.themeColor || '#3370ff'" />
        <div>
          <strong>移动端访问</strong>
          <p>门户采用响应式布局，扫描二维码可直接打开当前访问地址。</p>
        </div>
      </div>
    </n-form>
  </section>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed, ref } from 'vue'
import { checkBusinessApplicationSlugAvailable } from '@/api/business-application'

const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
const message = useMessage()
const slugStatus = ref(undefined)
const slugFeedback = ref('只允许 2-50 位字母、数字、中划线或下划线。')
let validationSequence = 0

const portalUrl = computed(() => {
  const path = `/app/${encodeURIComponent(props.modelValue.portalSlug || props.modelValue.applicationCode || '')}`
  return `${window.location.origin}${import.meta.env.BASE_URL.replace(/\/$/, '')}${path}`
})

function handleSlugInput(value) {
  slugStatus.value = undefined
  slugFeedback.value = '只允许 2-50 位字母、数字、中划线或下划线。'
  emit('update:modelValue', { ...props.modelValue, portalSlug: String(value || '').trim() })
}

async function checkSlug() {
  const slug = String(props.modelValue.portalSlug || '').trim()
  if (!/^[\w-]{2,50}$/.test(slug)) {
    slugStatus.value = 'error'
    slugFeedback.value = '门户地址格式不正确。'
    return false
  }
  const sequence = ++validationSequence
  try {
    const response = await checkBusinessApplicationSlugAvailable(slug, props.modelValue.id)
    if (sequence !== validationSequence)
      return false
    const available = response.data === true
    slugStatus.value = available ? 'success' : 'error'
    slugFeedback.value = available ? '该访问地址可用。' : '该访问地址已被占用。'
    return available
  }
  catch (error) {
    if (sequence !== validationSequence)
      return false
    slugStatus.value = 'error'
    slugFeedback.value = error?.message || '地址校验失败。'
    return false
  }
}

async function copyLink() {
  await navigator.clipboard.writeText(portalUrl.value)
  message.success('访问链接已复制')
}

function openPortal() {
  window.open(portalUrl.value, '_blank', 'noopener,noreferrer')
}

defineExpose({ checkSlug })
</script>

<style scoped>
.access-link-card,
.access-qrcode {
  display: flex;
  align-items: center;
  gap: 18px;
  border: 1px solid var(--border-color, #e5e6eb);
  border-radius: 8px;
  background: var(--card-color, #fff);
  padding: 16px;
}

.access-link-card {
  justify-content: space-between;
}

.access-link-card div:first-child,
.access-qrcode div {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.access-link-card span,
.access-qrcode p {
  margin: 0;
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.access-link-card strong {
  overflow: hidden;
  color: var(--text-color-1, #1f2329);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.access-qrcode {
  width: fit-content;
  max-width: 100%;
  margin-top: 18px;
}
</style>
