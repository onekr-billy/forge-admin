<template>
  <div v-if="cardData" class="go-items-list-card">
    <div class="card-inner">
      <div class="card-image" @click="resizeHandle">
        <fg-auth-image
          :src="cardData.indexImg"
          :alt="cardData.title"
          :fallback="requireErrorImg()"
          img-class="card-cover"
          :img-style="{ width: '100%', height: '170px', objectFit: 'contain', display: 'block' }"
        ></fg-auth-image>
        <div class="image-overlay">
          <div class="overlay-scan"></div>
          <div class="overlay-gradient"></div>
          <div class="overlay-corner top-left"></div>
          <div class="overlay-corner top-right"></div>
          <div class="overlay-corner bottom-left"></div>
          <div class="overlay-corner bottom-right"></div>
        </div>
        <div class="card-actions">
          <div class="action-group">
            <n-tooltip placement="top" trigger="hover">
              <template #trigger>
                <div class="action-dot" @click.stop="editHandle" title="编辑">
                  <n-icon size="12"><HammerIcon /></n-icon>
                </div>
              </template>
              <span>编辑</span>
            </n-tooltip>
            <n-dropdown trigger="click" placement="bottom-end" :options="selectOptions" @select="handleSelect">
              <div class="action-dot" @click.stop>
                <n-icon size="12"><EllipsisHorizontalCircleSharpIcon /></n-icon>
              </div>
            </n-dropdown>
          </div>
        </div>
      </div>

      <div class="card-body">
        <div class="card-title-row">
          <span class="card-indicator" :class="{ released: cardData.release }"></span>
          <n-text class="go-ellipsis-1 card-title" :title="cardData.title">
            {{ cardData.title || '' }}
          </n-text>
        </div>
        <div class="card-meta">
          <span class="meta-badge" :class="{ released: cardData.release }">
            {{ cardData.release ? $t('project.release') : $t('project.unreleased') }}
          </span>
          <span class="meta-time">{{ cardData.createTime || '' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { renderIcon, renderLang, requireErrorImg, fetchPathByName, routerTurnByPath } from '@/utils'
import { icon } from '@/plugins'
import { Chartype } from '../../index.d'
import { PreviewEnum } from '@/enums/pageEnum'
import FgAuthImage from '@/components/FgAuthImage/index.vue'
import { StorageEnum } from '@/enums/storageEnum'
import { getProjectDetailApi, publishProjectApi } from '@/api/project'
import { createTemplateFromProjectApi } from '@/api/project/template'

const { EllipsisHorizontalCircleSharpIcon, TrashIcon, HammerIcon, BrowsersOutlineIcon, SendIcon, CopyIcon } = icon.ionicons5
const { StackedMoveIcon } = icon.carbon

const emit = defineEmits(['delete', 'resize', 'edit', 'refresh', 'move-directory'])

const props = defineProps({ cardData: Object as () => Chartype })

const selectOptions = ref([
  { label: renderLang('global.r_preview'), key: 'preview', icon: renderIcon(BrowsersOutlineIcon) },
  { label: '调整目录', key: 'move-directory', icon: renderIcon(StackedMoveIcon) },
  { label: '发布为模板', key: 'publish-template', icon: renderIcon(CopyIcon) },
  { label: props.cardData?.release ? '重新发布' : renderLang('global.r_publish'), key: 'send', icon: renderIcon(SendIcon) },
  { label: renderLang('global.r_delete'), key: 'delete', icon: renderIcon(TrashIcon) }
])

const publishAsTemplate = async () => {
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
    emit('refresh')
  } catch (e: any) {
    window.$message.error(e?.message || '发布为模板失败')
  }
}

const handleSelect = async (key: string) => {
  if (key === 'delete') emit('delete', props.cardData)
  else if (key === 'move-directory') emit('move-directory', props.cardData)
  else if (key === 'preview') { const p = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href'); if (p) routerTurnByPath(p, [String(props.cardData?.id)], undefined, true) }
  else if (key === 'publish-template') {
    await publishAsTemplate()
  }
  else if (key === 'send') {
    try {
      const p = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
      if (!p || !props.cardData?.id) return
      await publishProjectApi(props.cardData.id, `${window.location.origin}${p}/${props.cardData.id}`)
      window.$message.success(props.cardData.release ? '重新发布成功' : '发布成功')
      emit('refresh')
    } catch (e: any) { window.$message.error(e?.message || '发布失败') }
  }
}

const editHandle = () => emit('edit', props.cardData)
const resizeHandle = () => emit('resize', props.cardData)
</script>

<style lang="scss" scoped>
@include go('items-list-card') {
  .card-inner {
    border-radius: $--border-radius-lg;
    overflow: hidden;
    @include fetch-bg-color('background-color1');
    border: 1px solid rgba(var(--app-theme-rgb), 0.04);
    transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  }

  &:hover .card-inner {
    border-color: rgba(var(--app-theme-rgb), 0.2);
    box-shadow:
      0 12px 40px rgba(0, 0, 0, 0.5),
      0 0 20px rgba(var(--app-theme-rgb), 0.06),
      inset 0 0 0 1px rgba(var(--app-theme-rgb), 0.06);
    transform: translateY(-3px);
  }

  .card-image {
    text-align: center;
    position: relative;
    overflow: hidden;
    cursor: pointer;
    height: 170px;
    @include fetch-bg-color('background-color2');

    @include deep() {
      img {
        display: block;
        width: 100%;
        transition: transform 0.5s ease;
      }
    }

    .image-overlay {
      position: absolute;
      inset: 0;
      pointer-events: none;

      .overlay-gradient {
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        height: 80px;
        background: linear-gradient(transparent, rgba(10, 14, 23, 0.95));
      }

      .overlay-scan {
        position: absolute;
        top: 0;
        left: -100%;
        width: 200%;
        height: 1px;
        background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.15), transparent);
        animation: scanDown 3s ease-in-out infinite;
      }

      .overlay-corner {
        position: absolute;
        width: 12px;
        height: 12px;
        border-color: rgba(var(--app-theme-rgb), 0.15);
        border-style: solid;
        transition: all 0.35s ease;
        opacity: 0;
      }
      .top-left { top: 4px; left: 4px; border-width: 1px 0 0 1px; }
      .top-right { top: 4px; right: 4px; border-width: 1px 1px 0 0; }
      .bottom-left { bottom: 4px; left: 4px; border-width: 0 0 1px 1px; }
      .bottom-right { bottom: 4px; right: 4px; border-width: 0 1px 1px 0; }
    }
  }

  &:hover .card-image {
    @include deep() { img { transform: scale(1.05); } }
    .overlay-corner { opacity: 1; border-color: rgba(var(--app-theme-rgb), 0.4); }
  }

  @keyframes scanDown {
    0%, 100% { top: -1px; }
    50% { top: 100%; }
  }

  .card-actions {
    position: absolute;
    top: 6px;
    right: 6px;
    opacity: 0;
    transform: translateX(4px);
    transition: all 0.25s ease;

    .action-group {
      display: flex;
      gap: 4px;
    }

    .action-dot {
      width: 26px;
      height: 26px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      @include fetch-bg-color('background-color');
      border: 1px solid rgba(255, 255, 255, 0.06);
      @include fetch-color(2);
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        background: rgba(var(--app-theme-rgb), 0.15);
        border-color: rgba(var(--app-theme-rgb), 0.3);
        color: $--color-primary;
        box-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.2);
      }
    }
  }

  &:hover .card-actions { opacity: 1; transform: translateX(0); }

  .card-body {
    padding: 14px 16px;
  }

  .card-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;

    .card-indicator {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      flex-shrink: 0;
      background: $--color-warn;
      box-shadow: 0 0 6px rgba(255, 165, 2, 0.5);

      &.released {
        background: $--color-success;
        box-shadow: 0 0 6px rgba(46, 213, 115, 0.5);
      }
    }
  }

  .card-title {
    font-size: 13px;
    font-weight: 600;
    @include fetch-color();
  }

  .card-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .meta-badge {
      font-size: 10px;
      padding: 2px 8px;
      border-radius: 10px;
      background: rgba(255, 165, 2, 0.1);
      color: $--color-warn;
      border: 1px solid rgba(255, 165, 2, 0.15);

      &.released {
        background: rgba(46, 213, 115, 0.08);
        color: $--color-success;
        border-color: rgba(46, 213, 115, 0.15);
      }
    }

    .meta-time {
      font-size: 10px;
      @include fetch-color(4);
      font-family: 'Courier New', monospace;
    }
  }
}
</style>
