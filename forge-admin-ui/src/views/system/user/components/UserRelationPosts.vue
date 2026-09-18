<template>
  <!-- 岗位绑定 -->
  <div v-show="relationActiveTab === 'post'" class="relation-section post-modal-content">
    <n-spin :show="postLoading">
      <div class="relation-primary-setting">
        <div class="relation-primary-copy">
          <strong>主汇报岗位</strong>
          <span>展示在通讯录和用户名片中的主头衔。</span>
        </div>
        <div class="relation-primary-control">
          <n-select
            v-model:value="mainPostId"
            :options="selectedPostOptions"
            placeholder="请选择主岗位"
            clearable
          />
        </div>
      </div>

      <div class="relation-option-section">
        <div class="relation-option-heading">
          <div>
            <strong>绑定更多岗位</strong>
            <span>可为用户同时分配多个岗位。</span>
          </div>
          <span class="relation-option-count">
            已选 {{ checkedPostKeys.length }} 个
          </span>
        </div>

        <div class="relation-option-list">
          <n-checkbox-group v-model:value="checkedPostKeys">
            <div class="relation-card-grid">
              <label
                v-for="post in postList"
                :key="post.id"
                class="relation-option-card"
                :class="{ 'is-selected': checkedPostKeys.includes(post.id) }"
              >
                <n-checkbox :value="post.id" />
                <span class="relation-option-main">
                  <strong>{{ post.postName }}</strong>
                  <small>{{ post.postCode || '未设置岗位编码' }}</small>
                </span>
              </label>
            </div>
          </n-checkbox-group>
          <n-empty v-if="!postLoading && postList.length === 0" description="暂无岗位数据" size="small" />
        </div>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useUserManagementStore } from '@/stores/system/userManagementStore'

const store = useUserManagementStore()
const { relationActiveTab, postLoading, postList, checkedPostKeys, mainPostId, selectedPostOptions } = storeToRefs(store)
</script>
