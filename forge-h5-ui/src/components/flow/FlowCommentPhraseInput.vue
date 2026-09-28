<template>
  <view class="flow-comment-input">
    <AiTextarea
      :model-value="modelValue"
      :maxlength="maxlength"
      :min-height="minHeight"
      :placeholder="placeholder"
      :disabled="disabled"
      @update:model-value="emit('update:modelValue', $event)"
    />

    <view v-if="visiblePhrases.length || showManage" class="phrase-toolbar">
      <view v-if="visiblePhrases.length" class="phrase-list" role="group" aria-label="常用审批意见">
        <button
          v-for="phrase in visiblePhrases"
          :key="phrase.id"
          class="phrase-chip"
          :class="{ active: String(modelValue || '') === phrase.content }"
          :disabled="disabled"
          @click="applyPhrase(phrase.content)"
        >
          {{ phrase.content }}
        </button>
      </view>
      <button v-if="showManage" class="phrase-manage-link" :disabled="disabled" @click="openManage">
        管理
      </button>
    </view>

    <AiPopupSheet v-model="manageVisible" title="常用审批意见" description="点选即可填入，也可以维护个人常用意见" max-height="82vh">
      <view class="phrase-editor">
        <AiTextarea v-model="draftContent" :maxlength="maxlength" min-height="76px" placeholder="输入个人常用审批意见" />
        <AiButton block size="sm" :loading="saving" :disabled="!draftContent.trim()" @click="saveDraft">
          保存为常用意见
        </AiButton>
      </view>
      <view v-if="personalPhrases.length" class="phrase-manage-list">
        <view v-for="phrase in personalPhrases" :key="phrase.id" class="phrase-manage-item">
          <button class="phrase-manage-content" @click="applyManagedPhrase(phrase.content)">
            <text>{{ phrase.content }}</text>
          </button>
          <button class="phrase-delete" :disabled="deletingId === String(phrase.id)" @click="removePhrase(phrase)">
            {{ deletingId === String(phrase.id) ? '删除中' : '删除' }}
          </button>
        </view>
      </view>
      <view v-else-if="!managingLoading" class="phrase-empty">暂无个人常用意见</view>
      <view v-else class="phrase-empty">正在加载...</view>
    </AiPopupSheet>
  </view>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/api'
import AiButton from '@/components/AiButton.vue'
import AiPopupSheet from '@/components/AiPopupSheet.vue'
import AiTextarea from '@/components/AiTextarea.vue'
import { showConfirmDialog } from '@/utils/dialog'
import { toast } from '@/utils/notify'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  scene: { type: String, default: '' },
  placeholder: { type: String, default: '请输入审批意见' },
  maxlength: { type: [String, Number], default: 200 },
  minHeight: { type: String, default: '96px' },
  disabled: { type: Boolean, default: false },
  showManage: { type: Boolean, default: true },
  maxVisible: { type: Number, default: 12 },
})

const emit = defineEmits(['update:modelValue'])
const usablePhrases = ref([])
const personalPhrases = ref([])
const manageVisible = ref(false)
const managingLoading = ref(false)
const saving = ref(false)
const deletingId = ref('')
const draftContent = ref('')

const visiblePhrases = computed(() => usablePhrases.value.slice(0, props.maxVisible))

watch(() => props.scene, loadUsable)
onMounted(loadUsable)

function unwrapList(response) {
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.data?.records)) return response.data.records
  if (Array.isArray(response)) return response
  return []
}

function normalizePhrases(response) {
  return unwrapList(response).filter(item => item?.id && String(item?.content || '').trim())
}

async function loadUsable() {
  try {
    usablePhrases.value = normalizePhrases(await api.listUsableCommentPhrases(
      props.scene ? { scene: props.scene } : {},
    ))
  }
  catch {
    usablePhrases.value = []
  }
}

async function loadMine() {
  managingLoading.value = true
  try {
    personalPhrases.value = normalizePhrases(await api.listMyCommentPhrases())
  }
  catch (error) {
    personalPhrases.value = []
    toast(error?.message || '常用意见加载失败', { type: 'error' })
  }
  finally {
    managingLoading.value = false
  }
}

function applyPhrase(content) {
  emit('update:modelValue', String(content || ''))
}

function applyManagedPhrase(content) {
  applyPhrase(content)
  manageVisible.value = false
}

async function openManage() {
  draftContent.value = String(props.modelValue || '').trim()
  manageVisible.value = true
  await loadMine()
}

async function saveDraft() {
  const content = draftContent.value.trim()
  if (!content) return
  saving.value = true
  try {
    await api.createCommentPhrase({ content, scene: props.scene || 'ALL', ownerType: 1 })
    toast('已保存为常用意见', { type: 'success' })
    draftContent.value = ''
    await Promise.all([loadUsable(), loadMine()])
  }
  catch (error) {
    toast(error?.message || '常用意见保存失败', { type: 'error' })
  }
  finally {
    saving.value = false
  }
}

async function removePhrase(phrase) {
  const confirmed = await showConfirmDialog({
    title: '删除常用意见',
    description: '删除后不能恢复，确认继续吗？',
    confirmText: '删除',
    isDestructive: true,
  })
  if (!confirmed) return
  deletingId.value = String(phrase.id)
  try {
    await api.deleteCommentPhrase(phrase.id)
    toast('已删除', { type: 'success' })
    await Promise.all([loadUsable(), loadMine()])
  }
  catch (error) {
    toast(error?.message || '常用意见删除失败', { type: 'error' })
  }
  finally {
    deletingId.value = ''
  }
}
</script>

<style lang="scss" scoped>
.flow-comment-input { min-width: 0; }
.phrase-toolbar { display: flex; align-items: flex-start; gap: 10px; margin-top: 10px; }
.phrase-list { display: flex; min-width: 0; flex: 1; flex-wrap: wrap; gap: 6px; }
.phrase-chip { max-width: 164px; min-height: 30px; margin: 0; padding: 4px 9px; border: 1px solid var(--border-light); border-radius: 6px; color: var(--text-secondary); font-size: 12px; line-height: 20px; background: #fff; box-sizing: border-box; }
.phrase-chip::after,
.phrase-manage-link::after,
.phrase-manage-content::after,
.phrase-delete::after { border: 0; }
.phrase-chip.active { border-color: var(--primary-color); color: var(--primary-color); background: var(--primary-soft); }
.phrase-manage-link { min-height: 30px; margin: 0; padding: 4px 0 4px 8px; border: 0; color: var(--primary-color); font-size: 12px; line-height: 20px; background: transparent; }
.phrase-editor { display: flex; flex-direction: column; gap: 12px; }
.phrase-manage-list { display: flex; flex-direction: column; margin-top: 16px; border-top: 1px solid var(--border-light); }
.phrase-manage-item { display: flex; min-height: 52px; align-items: center; gap: 10px; border-bottom: 1px solid var(--border-light); }
.phrase-manage-content { min-width: 0; flex: 1; margin: 0; padding: 12px 0; border: 0; color: var(--text-strong); font-size: 14px; line-height: 1.45; text-align: left; background: transparent; }
.phrase-manage-content text { display: block; overflow-wrap: anywhere; }
.phrase-delete { flex: 0 0 auto; min-height: 40px; margin: 0; padding: 0 4px; border: 0; color: var(--forge-color-danger); font-size: 12px; background: transparent; }
.phrase-empty { padding: 36px 0; color: var(--text-muted); font-size: 13px; text-align: center; }
</style>
