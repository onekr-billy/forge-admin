<template>
  <div class="workspace-title">
    <div class="title-mark">
      <n-icon size="18">
        <construct-icon></construct-icon>
      </n-icon>
    </div>
    <div v-show="!focus" class="title-display" @click="handleFocus">
      <span class="title-kicker">WORKSPACE</span>
      <div class="title-line">
        <strong class="title">{{ comTitle }}</strong>
        <span class="edit-cue">
          <n-icon size="12">
            <create-icon></create-icon>
          </n-icon>
          <span>编辑</span>
        </span>
      </div>
    </div>

    <n-input
      v-show="focus"
      ref="inputInstRef"
      size="small"
      type="text"
      maxlength="16"
      show-count
      placeholder="请输入项目名称"
      v-model:value.trim="title"
      @keyup.enter="handleBlur"
      @blur="handleBlur"
    ></n-input>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, computed, watch } from 'vue'
import { setTitle } from '@/utils'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasConfigEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { icon } from '@/plugins'

const { ConstructIcon, CreateIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()

const focus = ref<boolean>(false)
const inputInstRef = ref(null)

const title = ref<string>(chartEditStore.getEditCanvasConfig.projectName || '')

watch(
  () => chartEditStore.getEditCanvasConfig.projectName,
  newValue => {
    title.value = newValue || ''
  },
  { immediate: true }
)

const comTitle = computed(() => {
  // eslint-disable-next-line vue/no-side-effects-in-computed-properties
  title.value = title.value.replace(/\s/g, '')
  const newTitle = title.value.length ? title.value : '新项目'
  setTitle(`工作空间-${newTitle}`)
  chartEditStore.setEditCanvasConfig(EditCanvasConfigEnum.PROJECT_NAME, newTitle)
  return newTitle
})

const handleFocus = () => {
  focus.value = true
  nextTick(() => {
    inputInstRef.value && (inputInstRef.value as any).focus()
  })
}

const handleBlur = () => {
  focus.value = false
}
</script>
<style lang="scss" scoped>
.workspace-title {
  min-width: 260px;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 12px 3px 4px;
  border-radius: 999px;
  border: 1px solid rgba(var(--app-theme-rgb), 0.14);
  background:
    linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.12), transparent 70%),
    rgba(2, 6, 23, 0.34);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.035),
    0 0 20px rgba(var(--app-theme-rgb), 0.08);

  .title-mark {
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: var(--app-theme, $--color-primary);
    background: rgba(var(--app-theme-rgb), 0.12);
    box-shadow: inset 0 0 0 1px rgba(var(--app-theme-rgb), 0.16);
  }

  .title-display {
    min-width: 0;
    cursor: text;
    line-height: 1.1;

    &:hover {
      .edit-cue {
        opacity: 1;
        transform: translateX(0);
      }
    }
  }

  .title-kicker {
    display: block;
    margin-bottom: 2px;
    font-size: 8px;
    letter-spacing: 1.2px;
    @include fetch-color(4);
  }

.title {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-theme, $--color-primary);
  text-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.32);
}

  .title-line {
    display: flex;
    align-items: center;
    gap: 7px;
    min-width: 0;
  }

  .edit-cue {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    height: 16px;
    padding: 0 6px;
    border-radius: 999px;
    color: rgba(226, 232, 240, 0.72);
    background: rgba(var(--app-theme-rgb), 0.1);
    border: 1px solid rgba(var(--app-theme-rgb), 0.12);
    font-size: 10px;
    opacity: 0.72;
    transform: translateX(-2px);
    transition: all 0.18s ease;
    white-space: nowrap;
  }

:deep(.n-input) {
  min-width: 180px;
    background: transparent;
  }
}
</style>
