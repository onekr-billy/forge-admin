<template>
  <n-modal
    class="go-modal-box"
    v-model:show="showRef"
    :mask-closable="true"
    transform-origin="center"
    @afterLeave="closeHandle"
  >
    <div class="project-modal">
      <div class="modal-left">
        <div class="modal-image" @click="doEdit">
          <fg-auth-image
            :src="cardData?.indexImg"
            :alt="cardData?.title"
            :fallback="requireUrl('project/moke-20211219181327.png')"
            img-class="modal-cover"
            :img-style="{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }"
            :lazy="false"
          ></fg-auth-image>
          <div class="image-hover-panel">
            <n-icon size="28"><HammerIcon /></n-icon>
            <span>点击编辑项目</span>
          </div>
        </div>
      </div>

      <div class="modal-right">
        <div class="modal-header">
          <div class="modal-icon-wrap">
            <span class="modal-diamond">◆</span>
          </div>
          <div>
            <div class="modal-title">{{ cardData?.title || '' }}</div>
            <div class="modal-subtitle">项目详情</div>
          </div>
          <n-icon size="20" class="modal-close-btn" @click="closeHandle"><CloseIcon /></n-icon>
        </div>

        <div class="modal-divider"></div>

        <div class="modal-info-grid">
          <div class="info-item">
            <span class="info-label">状态</span>
            <span class="info-value">
              <n-badge
                class="go-animation-twinkle"
                dot
                :color="cardData?.release ? '#2ed573' : '#ffa502'"
                style="margin-right: 6px"
              />
              {{ cardData?.release ? $t('project.release') : $t('project.unreleased') }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ cardData?.createTime || '--' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">项目 ID</span>
            <span class="info-value id-value">{{ cardData?.id || '--' }}</span>
          </div>
        </div>

        <div class="modal-divider"></div>

        <div class="modal-actions">
          <n-button class="action-btn primary" @click="doEdit">
            <template #icon><n-icon size="16"><HammerIcon /></n-icon></template>
            编辑
          </n-button>
          <n-button class="action-btn" @click="doMoveDirectory">
            <template #icon><n-icon size="16"><StackedMoveIcon /></n-icon></template>
            调整目录
          </n-button>
          <n-button class="action-btn" @click="handlePublishTemplate">
            <template #icon><n-icon size="16"><CopyIcon /></n-icon></template>
            发布为模板
          </n-button>
          <n-button class="action-btn" @click="handlePreview">
            <template #icon><n-icon size="16"><BrowsersOutlineIcon /></n-icon></template>
            {{ $t('global.r_preview') }}
          </n-button>
          <n-button class="action-btn" @click="handlePublish">
            <template #icon><n-icon size="16"><SendIcon /></n-icon></template>
            {{ cardData?.release ? '重新发布' : $t('global.r_publish') }}
          </n-button>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchPathByName, routerTurnByPath } from '@/utils'
import { icon } from '@/plugins'
import { PreviewEnum } from '@/enums/pageEnum'
import FgAuthImage from '@/components/FgAuthImage/index.vue'
import { publishProjectApi, getProjectDetailApi } from '@/api/project'
import { createTemplateFromProjectApi } from '@/api/project/template'

const { HammerIcon, CloseIcon, BrowsersOutlineIcon, SendIcon, CopyIcon } = icon.ionicons5
const { StackedMoveIcon } = icon.carbon
const showRef = ref(false)
const emit = defineEmits(['close', 'edit', 'move-directory'])

const props = defineProps({
  modalShow: { required: true, type: Boolean },
  cardData: { required: true, type: Object }
})

watch(() => props.modalShow, v => { showRef.value = v }, { immediate: true })

const requireUrl = (n: string) => new URL(`../../../../../assets/images/${n}`, import.meta.url).href

const closeHandle = () => emit('close')

const handlePreview = () => {
  const p = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
  if (p) routerTurnByPath(p, [String(props.cardData?.id)], undefined, true)
}

const handlePublish = async () => {
  try {
    const p = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
    if (!p || !props.cardData?.id) return
    await publishProjectApi(props.cardData.id, `${window.location.origin}${p}/${props.cardData.id}`)
    window.$message.success(props.cardData.release ? '重新发布成功' : '发布成功')
  } catch (e: any) { window.$message.error(e?.message || '发布失败') }
}

const handlePublishTemplate = async () => {
  try {
    if (!props.cardData?.id) return
    const res = await getProjectDetailApi(props.cardData.id)
    const project = res?.data
    if (!project?.componentData) {
      window.$message.warning('该项目暂无可发布内容')
      return
    }
    await createTemplateFromProjectApi({
      sourceProjectId: project.id,
      templateName: project.projectName,
      remark: project.remark,
      indexImg: project.indexImg,
      componentData: project.componentData,
      status: '0'
    })
    window.$message.success('已发布为模板')
  } catch (e: any) {
    window.$message.error(e?.message || '发布为模板失败')
  }
}

const doEdit = () => { emit('edit', props.cardData); closeHandle() }
const doMoveDirectory = () => { emit('move-directory', props.cardData); closeHandle() }
</script>

<style lang="scss" scoped>
.project-modal {
  display: flex;
  width: 85vw;
  max-width: 960px;
  border-radius: $--border-radius-lg;
  overflow: hidden;
  @include fetch-bg-color('background-color1');
  border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  backdrop-filter: blur(20px);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.6), 0 0 40px rgba(var(--app-theme-rgb), 0.03);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background-image: radial-gradient(circle at 1px 1px, rgba(var(--app-theme-rgb), 0.025) 1px, transparent 0);
    background-size: 24px 24px;
    z-index: 0;
  }
}

.modal-left {
  flex: 1;
  position: relative;
  z-index: 1;
  min-width: 0;
}

.modal-image {
  width: 100%;
  height: 100%;
  cursor: pointer;
  position: relative;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    display: block;
    transition: transform 0.4s ease;
  }

  &:hover img { transform: scale(1.02); }

  .image-hover-panel {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    background: rgba(var(--app-theme-rgb), 0.06);
    backdrop-filter: blur(3px);
    opacity: 0;
    transition: opacity 0.3s;
    color: $--color-primary;
    font-size: 14px;
  }

  &:hover .image-hover-panel { opacity: 1; }
}

.modal-right {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
  border-left: 1px solid rgba(255, 255, 255, 0.04);
  @include fetch-bg-color('background-color2');
}

.modal-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 20px;

  .modal-icon-wrap {
    flex-shrink: 0;
  }

  .modal-diamond {
    font-size: 18px;
    color: $--color-primary;
    text-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.5);
  }

  .modal-title {
    font-size: 16px;
    font-weight: 700;
    @include fetch-color();
    letter-spacing: 0.5px;
  }

  .modal-subtitle {
    font-size: 10px;
    @include fetch-color(4);
    letter-spacing: 2px;
    text-transform: uppercase;
    margin-top: 2px;
  }

  .modal-close-btn {
    margin-left: auto;
    flex-shrink: 0;
    @include fetch-color(3);
    cursor: pointer;
    transition: all 0.2s;
    &:hover { color: $--color-red; }
  }
}

.modal-divider {
  height: 1px;
  margin: 0 20px;
  background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.1), transparent);
}

.modal-info-grid {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;

  .info-item {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .info-label {
      font-size: 10px;
      text-transform: uppercase;
      letter-spacing: 1.5px;
      @include fetch-color(4);
      font-weight: 600;
    }

    .info-value {
      font-size: 13px;
      @include fetch-color(1);
      display: flex;
      align-items: center;

      &.id-value {
        font-family: 'Courier New', monospace;
        font-size: 11px;
        @include fetch-color(3);
      }
    }
  }
}

.modal-actions {
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .action-btn {
    justify-content: flex-start;
    width: 100%;
    border-radius: $--border-radius-sm;
    background: rgba(255, 255, 255, 0.02);
    border: 1px solid rgba(255, 255, 255, 0.05);
    @include fetch-color(2);
    transition: all 0.2s;

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.2);
      color: $--color-primary;
      background: rgba(var(--app-theme-rgb), 0.04);
    }

    &.primary {
      background: rgba(var(--app-theme-rgb), 0.06);
      border-color: rgba(var(--app-theme-rgb), 0.2);
      color: $--color-primary;
      &:hover {
        background: rgba(var(--app-theme-rgb), 0.12);
        box-shadow: 0 0 16px rgba(var(--app-theme-rgb), 0.15);
      }
    }

    &.danger {
      &:hover {
        color: $--color-red;
        border-color: rgba(255, 71, 87, 0.3);
        background: rgba(255, 71, 87, 0.06);
      }
    }
  }
}
</style>
