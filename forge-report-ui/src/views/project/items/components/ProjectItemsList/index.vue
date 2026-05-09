<template>
  <div class="go-items-list">
    <n-grid
      v-if="!loading"
      :x-gap="20"
      :y-gap="20"
      cols="2 s:2 m:3 l:4 xl:4 xxl:4"
      responsive="screen"
    >
      <n-grid-item v-for="(item, index) in list" :key="item.id">
        <div class="go-float-up" :style="{ animationDelay: `${index * 0.06}s` }">
          <project-items-card
            :cardData="item"
            @resize="resizeHandle"
            @delete="deleteHandle($event, index)"
            @edit="editHandle"
            @refresh="loadList"
          ></project-items-card>
        </div>
      </n-grid-item>
    </n-grid>
    <div v-else class="go-flex-center list-loading">
      <n-spin size="large">
        <template #description>
          <span style="color: #64748b; margin-top: 12px; display: block;">加载项目数据...</span>
        </template>
      </n-spin>
    </div>
    <div class="list-pagination">
      <n-pagination
        :item-count="10"
        :page-sizes="[10, 20, 30, 40]"
        show-size-picker
      />
    </div>
  </div>
  <project-items-modal-card
    v-if="modalData"
    :modalShow="modalShow"
    :cardData="modalData"
    @close="closeModal"
    @edit="editHandle"
  ></project-items-modal-card>
</template>

<script setup lang="ts">
import { ProjectItemsCard } from '../ProjectItemsCard/index'
import { ProjectItemsModalCard } from '../ProjectItemsModalCard/index'
import { icon } from '@/plugins'
import { useModalDataInit } from './hooks/useModal.hook'
import { useDataListInit } from './hooks/useData.hook'

const { CopyIcon, EllipsisHorizontalCircleSharpIcon } = icon.ionicons5
const { list, loading, deleteHandle, loadList } = useDataListInit()
const { modalData, modalShow, closeModal, resizeHandle, editHandle } = useModalDataInit(loadList)
</script>

<style lang="scss" scoped>
@include go('items-list') {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 120px);

  .list-loading {
    min-height: 400px;
    flex-direction: column;
  }

  .list-pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 24px;
    padding-top: 8px;
  }
}
</style>
