<template>
  <div class="ai-provider-model-page">
    <main class="provider-workspace">
      <aside class="provider-list-panel" aria-label="供应商列表">
        <div class="provider-list-panel__header">
          <h2>供应商列表</h2>
          <div class="provider-list-panel__header-actions">
            <NTag size="small" :bordered="false">
              {{ providerPagination.itemCount }} 个
            </NTag>
            <NButton
              type="primary"
              circle
              size="small"
              aria-label="新增供应商"
              title="新增供应商"
              @click="handleAddProvider"
            >
              <template #icon>
                <i class="ai-icon:plus" aria-hidden="true" />
              </template>
            </NButton>
          </div>
        </div>
        <div class="provider-list-filters">
          <n-input
            v-model:value="providerSearch.name"
            placeholder="搜索供应商名称"
            clearable
            @keyup.enter="handleProviderSearch"
          >
            <template #prefix>
              <i class="ai-icon:search" aria-hidden="true" />
            </template>
          </n-input>
          <div class="provider-list-filters__row">
            <n-select v-model:value="providerSearch.type" placeholder="全部类型" clearable :options="providerTypeOptions" />
            <n-select v-model:value="providerSearch.status" placeholder="全部状态" clearable :options="statusOptions" />
          </div>
          <div class="provider-list-filters__actions">
            <NButton type="primary" size="small" @click="handleProviderSearch">
              查询
            </NButton>
            <NButton size="small" @click="handleResetProviderSearch">
              重置
            </NButton>
          </div>
        </div>

        <n-spin :show="providerLoading">
          <div class="provider-list">
            <button
              v-for="provider in providerList"
              :key="provider.id"
              type="button"
              class="provider-list-item"
              :class="{ 'provider-list-item--selected': selectedProvider?.id === provider.id }"
              :aria-pressed="selectedProvider?.id === provider.id"
              @click="handleSelectProvider(provider)"
            >
              <span class="provider-list-item__avatar">
                <AuthImage
                  v-if="provider.logo"
                  :src="provider.logo"
                  :img-style="providerListAvatarStyle"
                />
                <span v-else>{{ providerInitial(provider) }}</span>
              </span>
              <span class="provider-list-item__content">
                <span class="provider-list-item__title">
                  <strong>{{ provider.providerName }}</strong>
                  <NTag v-if="provider.isDefault === '1'" type="success" size="small" :bordered="false">
                    默认
                  </NTag>
                  <DictTag dict-type="ai_status" :value="provider.status" size="small" />
                </span>
                <span class="provider-list-item__meta">
                  <span>{{ providerModelCount(provider) }} 个模型</span>
                  <span>{{ formatProviderTime(provider.updateTime || provider.createTime) }}</span>
                </span>
              </span>
              <i class="provider-list-item__arrow ai-icon:chevron-right" aria-hidden="true" />
            </button>
            <n-empty v-if="!providerLoading && providerList.length === 0" description="暂无供应商" size="small" />
          </div>
        </n-spin>

        <div class="provider-list-pagination">
          <span>共 {{ providerPagination.itemCount }} 条</span>
          <n-pagination
            :page="providerPagination.pageNum"
            :page-size="providerPagination.pageSize"
            :item-count="providerPagination.itemCount"
            :page-sizes="providerPageSizes"
            show-size-picker
            size="small"
            @update:page="handleProviderPageChange"
            @update:page-size="handleProviderPageSizeChange"
          />
        </div>
      </aside>

      <section class="provider-detail-panel" aria-label="供应商配置">
        <template v-if="selectedProvider">
          <div class="provider-detail-header">
            <div class="provider-detail-header__identity">
              <div class="provider-detail-header__avatar">
                <AuthImage
                  v-if="selectedProvider.logo"
                  :src="selectedProvider.logo"
                  :img-style="providerAvatarStyle"
                />
                <span v-else>{{ providerInitial(selectedProvider) }}</span>
              </div>
              <div>
                <div class="provider-detail-header__title">
                  <h2>{{ selectedProvider.providerName }}</h2>
                  <DictTag dict-type="ai_status" :value="selectedProvider.status" size="small" />
                  <NTag v-if="selectedProvider.isDefault === '1'" type="success" size="small" :bordered="false">
                    默认供应商
                  </NTag>
                </div>
                <div class="provider-detail-header__time">
                  <span>创建时间：{{ formatProviderTime(selectedProvider.createTime) }}</span>
                  <span>更新时间：{{ formatProviderTime(selectedProvider.updateTime) }}</span>
                </div>
              </div>
            </div>
            <div class="provider-detail-header__actions">
              <NButton type="primary" ghost :loading="providerEditing" @click="handleEditProvider(selectedProvider)">
                编辑配置
              </NButton>
              <NButton
                v-if="selectedProvider.isDefault !== '1'"
                text
                class="text-success"
                :loading="providerDefaultUpdatingId === selectedProvider.id"
                @click="handleSetDefault(selectedProvider)"
              >
                设为默认
              </NButton>
              <NPopconfirm @positive-click="handleDeleteProvider(selectedProvider.id)">
                <template #trigger>
                  <NButton text class="text-error">
                    删除
                  </NButton>
                </template>
                确定删除供应商“{{ selectedProvider.providerName }}”吗？
              </NPopconfirm>
              <NButton quaternary circle aria-label="关闭供应商详情" title="关闭" @click="clearSelectedProvider">
                <i class="ai-icon:x" />
              </NButton>
            </div>
          </div>

          <div class="provider-config-content">
            <section class="config-section">
              <div class="config-section__title">
                <i class="ai-icon:settings" aria-hidden="true" />
                <strong>基础信息</strong>
              </div>
              <div class="config-form-grid">
                <div class="config-field">
                  <span>供应商名称</span>
                  <n-input :value="selectedProvider.providerName" readonly />
                </div>
                <div class="config-field">
                  <span>供应商类型</span>
                  <div class="config-field__value">
                    <DictTag dict-type="ai_provider_type" :value="selectedProvider.providerType" size="small" />
                  </div>
                </div>
                <div class="config-field">
                  <span>连接协议</span>
                  <div class="config-field__value">
                    <DictTag dict-type="ai_provider_adapter_type" :value="selectedProvider.adapterCode" size="small" />
                  </div>
                </div>
                <div class="config-field">
                  <span>默认供应商</span>
                  <div class="config-field__value">
                    {{ selectedProvider.isDefault === '1' ? '是' : '否' }}
                  </div>
                </div>
                <div class="config-field config-field--full">
                  <span>Base URL</span>
                  <n-input :value="selectedProvider.baseUrl" readonly />
                </div>
                <div class="config-field config-field--full">
                  <span>备注</span>
                  <n-input :value="selectedProvider.remark || '暂无备注'" type="textarea" :rows="2" readonly />
                </div>
              </div>
            </section>

            <section class="config-section">
              <div class="config-section__title">
                <i class="ai-icon:lock" aria-hidden="true" />
                <strong>API Key / 密钥配置</strong>
              </div>
              <div class="secret-config-row">
                <div class="config-field">
                  <span>API Key</span>
                  <div class="secret-config-input">
                    <n-input
                      :value="selectedProvider.apiKey || '未配置'"
                      type="password"
                      readonly
                      show-password-on="click"
                    />
                    <NButton secondary @click="handleFetchModels">
                      验证连接
                    </NButton>
                  </div>
                  <small>密钥仅展示脱敏值，编辑时留空表示不修改。</small>
                </div>
              </div>
            </section>
          </div>

          <section class="model-section">
            <div class="model-section__heading">
              <i class="ai-icon:apps" aria-hidden="true" />
              <strong>模型管理</strong>
              <NTag size="small" :bordered="false">
                {{ modelPagination.itemCount }} 个
              </NTag>
            </div>
            <div class="model-tab-toolbar">
              <div class="model-tab-toolbar__left">
                <span>默认模型用于连接测试，以及未显式指定模型时的调用。</span>
                <n-select
                  v-model:value="modelSearch.modelType"
                  placeholder="全部类型"
                  clearable
                  :options="modelTypeOptions"
                  size="small"
                  style="width: 130px"
                  @update:value="handleModelSearch"
                />
              </div>
              <div class="model-tab-toolbar__actions">
                <NButton secondary @click="handleFetchModels">
                  <template #icon>
                    <i class="ai-icon:download" />
                  </template>
                  获取模型
                </NButton>
                <NButton type="primary" @click="handleAddModel()">
                  <template #icon>
                    <i class="ai-icon:plus" />
                  </template>
                  新增模型
                </NButton>
              </div>
            </div>
            <n-data-table
              :columns="modelColumns"
              :data="modelList"
              :loading="modelLoading"
              :row-key="row => row.id"
              :scroll-x="1030"
              size="small"
              class="model-table"
            />
            <div class="model-pagination">
              <n-pagination
                :page="modelPagination.pageNum"
                :page-size="modelPagination.pageSize"
                :item-count="modelPagination.itemCount"
                :page-sizes="modelPageSizes"
                :prefix="paginationPrefix"
                show-size-picker
                show-quick-jumper
                size="small"
                @update:page="handleModelPageChange"
                @update:page-size="handleModelPageSizeChange"
              />
            </div>
          </section>
        </template>

        <div v-else class="provider-detail-empty">
          <i class="ai-icon:settings" aria-hidden="true" />
          <h2>请选择供应商</h2>
          <p>从左侧列表选择一个供应商，查看基础配置和模型信息。</p>
        </div>
      </section>
    </main>

    <n-modal
      v-model:show="providerModal.show"
      preset="card"
      :title="providerModal.isEdit ? '编辑供应商' : '新增供应商'"
      :style="modalCardStyle"
    >
      <n-form ref="providerFormRef" :model="providerModal.form" :rules="providerRules" label-placement="left" label-width="100">
        <div class="modal-form-grid">
          <n-form-item label="供应商名称" path="providerName">
            <n-input v-model:value="providerModal.form.providerName" placeholder="请输入供应商名称" />
          </n-form-item>
          <n-form-item label="供应商类型" path="providerType">
            <n-select v-model:value="providerModal.form.providerType" placeholder="请选择类型" :options="providerTypeOptions" />
          </n-form-item>
          <n-form-item label="连接协议" path="adapterCode">
            <n-select v-model:value="providerModal.form.adapterCode" placeholder="请选择连接协议" :options="providerAdapterOptions" />
          </n-form-item>
          <n-form-item label="状态" path="status">
            <n-radio-group v-model:value="providerModal.form.status">
              <n-radio v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </n-radio>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="Logo" path="logo">
            <n-upload
              :action="`${uploadPrefix}/system/file/upload`"
              :headers="uploadHeaders"
              :max="1"
              accept=".png,.jpg,.jpeg,.svg,.webp"
              :default-upload="true"
              @finish="handleLogoUploadFinish"
            >
              <div v-if="providerModal.form.logo" class="logo-preview">
                <AuthImage :src="providerModal.form.logo" :img-style="{ width: '64px', height: '64px', borderRadius: '12px', objectFit: 'cover' }" />
                <NButton text size="tiny" type="error" class="logo-remove" aria-label="移除供应商 Logo" @click.stop="providerModal.form.logo = ''">
                  <i class="ai-icon:x" />
                </NButton>
              </div>
              <NButton v-else size="small">
                <i class="ai-icon:upload" /> 上传Logo
              </NButton>
            </n-upload>
          </n-form-item>
          <n-form-item class="form-item--full" label="Base URL" path="baseUrl">
            <n-input v-model:value="providerModal.form.baseUrl" placeholder="留空自动使用默认地址，如 https://dashscope.aliyuncs.com/compatible-mode" />
          </n-form-item>
          <n-form-item class="form-item--full" label="API Key" path="apiKey">
            <div class="form-control-stack">
              <n-input
                v-model:value="providerModal.form.apiKey"
                type="password"
                show-password-on="click"
                :placeholder="providerApiKeyPlaceholder"
              />
              <span v-if="providerModal.isEdit" class="field-tip">已保存密钥不会回显，留空表示保持不变。</span>
            </div>
          </n-form-item>
          <n-form-item class="form-item--full" label="备注" path="remark">
            <n-input v-model:value="providerModal.form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
          </n-form-item>
        </div>
      </n-form>
      <template #action>
        <div class="modal-footer-actions">
          <NButton @click="providerModal.show = false">
            取消
          </NButton>
          <NButton type="primary" :loading="providerModal.saving" @click="handleSaveProvider">
            确定
          </NButton>
        </div>
      </template>
    </n-modal>

    <n-modal v-model:show="modelModal.show" preset="card" :title="modelModal.isEdit ? '编辑模型' : '新增模型'" :style="modalCardStyle">
      <n-form ref="modelFormRef" :model="modelModal.form" :rules="modelRules" label-placement="left" label-width="100">
        <div class="modal-form-grid">
          <n-form-item label="供应商" path="providerId">
            <n-select v-model:value="modelModal.form.providerId" placeholder="请选择供应商" :options="providerSelectOptions" :disabled="modelModal.isEdit" />
          </n-form-item>
          <n-form-item label="模型类型" path="modelType">
            <n-select v-model:value="modelModal.form.modelType" placeholder="请选择模型类型" :options="modelTypeOptions" />
          </n-form-item>
          <n-form-item label="模型标识" path="modelId">
            <n-input v-model:value="modelModal.form.modelId" placeholder="如 gpt-4o" />
          </n-form-item>
          <n-form-item label="模型名称" path="modelName">
            <n-input v-model:value="modelModal.form.modelName" placeholder="如 GPT-4o" />
          </n-form-item>
          <n-form-item label="最大Token" path="maxTokens">
            <n-input-number v-model:value="modelModal.form.maxTokens" placeholder="128000" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="上下文窗口" path="contextWindow">
            <n-input-number v-model:value="modelModal.form.contextWindow" placeholder="128000" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item class="form-item--full" label="模型能力" path="capabilityCodes">
            <n-select v-model:value="modelModal.form.capabilityCodes" multiple clearable :options="modelCapabilityOptions" placeholder="请选择模型实际支持的能力" />
          </n-form-item>
          <n-form-item label="输入价格" path="inputPricePerMillionCent">
            <n-input-number v-model:value="modelModal.form.inputPricePerMillionCent" placeholder="分/百万 Token" :min="0" :precision="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="输出价格" path="outputPricePerMillionCent">
            <n-input-number v-model:value="modelModal.form.outputPricePerMillionCent" placeholder="分/百万 Token" :min="0" :precision="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="排序号" path="sortOrder">
            <n-input-number v-model:value="modelModal.form.sortOrder" placeholder="排序值" :min="0" style="width: 100%" />
          </n-form-item>
          <n-form-item label="默认模型" path="isDefault">
            <n-radio-group v-model:value="modelModal.form.isDefault">
              <n-radio v-for="opt in isDefaultOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </n-radio>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="状态" path="status">
            <n-radio-group v-model:value="modelModal.form.status">
              <n-radio v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </n-radio>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="图标" path="icon">
            <n-upload
              :action="`${uploadPrefix}/system/file/upload`"
              :headers="uploadHeaders"
              :max="1"
              accept=".png,.jpg,.jpeg,.svg,.webp"
              :default-upload="true"
              @finish="handleIconUploadFinish"
            >
              <div v-if="modelModal.form.icon" class="logo-preview">
                <AuthImage :src="modelModal.form.icon" :img-style="{ width: '64px', height: '64px', borderRadius: '12px', objectFit: 'cover' }" />
                <NButton text size="tiny" type="error" class="logo-remove" aria-label="移除模型图标" @click.stop="modelModal.form.icon = ''">
                  <i class="ai-icon:x" />
                </NButton>
              </div>
              <NButton v-else size="small">
                <i class="ai-icon:upload" /> 上传图标
              </NButton>
            </n-upload>
          </n-form-item>
          <n-form-item class="form-item--full" label="描述" path="description">
            <n-input v-model:value="modelModal.form.description" type="textarea" :rows="3" placeholder="请输入模型描述" />
          </n-form-item>
        </div>
      </n-form>
      <template #action>
        <div class="modal-footer-actions">
          <NButton @click="modelModal.show = false">
            取消
          </NButton>
          <NButton type="primary" :loading="modelModal.saving" @click="handleSaveModel">
            确定
          </NButton>
        </div>
      </template>
    </n-modal>

    <n-modal
      v-model:show="fetchModelsModal.show"
      preset="card"
      title="获取可用模型"
      :style="fetchModelsModalStyle"
      :content-style="fetchModelsModalContentStyle"
      :mask-closable="false"
    >
      <template v-if="fetchModelsModal.loading">
        <div class="fetch-models-loading">
          <i class="ai-icon:loader animate-spin" aria-hidden="true" />
          <p>正在从供应商拉取可用模型…</p>
        </div>
      </template>

      <template v-else-if="fetchModelsModal.error">
        <n-result
          status="error"
          title="获取模型失败"
          :description="fetchModelsModal.error"
          size="small"
          class="fetch-models-result"
        >
          <template #footer>
            <NButton size="small" @click="handleFetchModels">
              重试
            </NButton>
          </template>
        </n-result>
      </template>

      <template v-else>
        <div class="fetch-models-categories">
          <n-radio-group v-model:value="fetchModelsModalCategory" size="small">
            <n-radio-button v-for="cat in fetchModelsModalCategories" :key="cat.value" :value="cat.value">
              {{ cat.label }}
            </n-radio-button>
          </n-radio-group>
        </div>
        <div class="fetch-models-toolbar">
          <span class="fetch-models-count">发现 {{ fetchModelsModal.models.length }} 个可用模型</span>
          <n-input
            v-model:value="fetchModelsModalSearch"
            size="small"
            clearable
            placeholder="搜索模型 ID"
            class="fetch-models-search"
          />
        </div>
        <div class="fetch-models-list">
          <template v-for="group in fetchModelsModalGroups" :key="group.type">
            <div class="fetch-models-group-title">
              <n-checkbox
                size="small"
                :checked="isGroupChecked(group.type)"
                :indeterminate="isGroupIndeterminate(group.type)"
                @update:checked="checked => handleToggleGroup(group.type, checked)"
              />
              <span class="fetch-models-group-name">{{ group.label }}</span>
              <span class="fetch-models-group-count">{{ group.models.length }}</span>
            </div>
            <div v-for="model in group.models" :key="model.id" class="fetch-models-item">
              <n-checkbox :checked="fetchModelsModal.checked[model.id]" @update:checked="checked => handleToggleModel(model.id, checked)">
                <code class="fetch-models-model-id" :title="model.id">{{ model.id }}</code>
              </n-checkbox>
              <n-popselect
                v-model:value="fetchModelsModal.modelTypes[model.id]"
                :options="modelTypeOverrideOptions"
                trigger="click"
                size="small"
                class="fetch-models-type-select"
              >
                <n-tag size="small" :type="modelTypeTagColor(fetchModelsModal.modelTypes[model.id] || inferModelType(model.id))">
                  {{ modelTypeLabel(fetchModelsModal.modelTypes[model.id] || inferModelType(model.id)) }}
                </n-tag>
              </n-popselect>
            </div>
          </template>
          <n-empty
            v-if="fetchModelsModalGroups.length === 0"
            size="small"
            description="没有匹配的模型"
            style="padding: 24px 0"
          />
        </div>
      </template>
      <template #footer>
        <div class="modal-footer-actions">
          <NButton
            v-if="!fetchModelsModal.loading && !fetchModelsModal.error"
            @click="fetchModelsModal.show = false"
          >
            取消
          </NButton>
          <NButton
            v-if="!fetchModelsModal.loading && !fetchModelsModal.error"
            type="primary"
            :loading="fetchModelsModal.importing"
            :disabled="fetchModelsModalVisibleCheckedCount === 0"
            @click="handleImportModels"
          >
            导入 {{ fetchModelsModalVisibleCheckedCount }} 个模型
          </NButton>
          <NButton v-if="fetchModelsModal.error" @click="fetchModelsModal.show = false">
            关闭
          </NButton>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { NButton, NPopconfirm, NSwitch, NTag } from 'naive-ui'
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  modelPage as fetchModelPage,
  providerPage as fetchProviderPage,
  modelAdd,
  modelDelete,
  modelGetById,
  modelTest,
  modelUpdate,
  providerAdd,
  providerBatchImportModels,
  providerDelete,
  providerFetchModels,
  providerGetById,
  providerSetDefault,
  providerUpdate,
} from '@/api/ai'
import AuthImage from '@/components/common/AuthImage.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'AiProviderModel' })

const { dict } = useDict('ai_provider_type', 'ai_provider_adapter_type', 'ai_model_type', 'ai_status', 'ai_is_default', 'ai_model_capability_type')

const providerTypeOptions = computed(() => dict.value.ai_provider_type || [])
const providerAdapterOptions = computed(() => dict.value.ai_provider_adapter_type || [])
const modelTypeOptions = computed(() => dict.value.ai_model_type || [])
const statusOptions = computed(() => dict.value.ai_status || [])
const isDefaultOptions = computed(() => dict.value.ai_is_default || [])
const modelCapabilityOptions = computed(() => dict.value.ai_model_capability_type || [])

// 模型类型能力分类（对齐阿里百炼模型广场，与后端 AiModelType 枚举一致）
const MODEL_TYPE_LABEL = {
  chat: '对话',
  vision: '视觉理解',
  video_understanding: '视频理解',
  audio_understanding: '音频理解',
  asr: '语音识别',
  tts: '语音合成',
  image_generation: '图像生成',
  video_generation: '视频生成',
  embedding: '向量化',
  rerank: '重排',
}
const MODEL_TYPE_ORDER = ['chat', 'vision', 'video_understanding', 'audio_understanding', 'asr', 'tts', 'image_generation', 'video_generation', 'embedding', 'rerank']
const MODEL_TYPE_TAG_COLOR = {
  chat: 'success',
  vision: 'primary',
  video_understanding: 'error',
  audio_understanding: 'info',
  asr: 'default',
  tts: 'info',
  image_generation: 'warning',
  video_generation: 'error',
  embedding: 'info',
  rerank: 'warning',
}

function modelTypeLabel(code) {
  return MODEL_TYPE_LABEL[code] || code
}
function modelTypeTagColor(code) {
  return MODEL_TYPE_TAG_COLOR[code] || 'default'
}

// 类型覆盖下拉：只展示规范化的能力分类（过滤字典中存量的 image/audio 宽泛值）
const modelTypeOverrideOptions = computed(() =>
  modelTypeOptions.value.filter(o => MODEL_TYPE_ORDER.includes(o.value)))

/**
 * 根据模型标识启发式推断模型类型（与后端 AiModelType.inferFromModelId 逻辑一致）。
 * @param {string} modelId 模型标识
 * @returns {string} 推断的模型类型 code
 */
function inferModelType(modelId) {
  if (!modelId)
    return 'chat'
  const lower = modelId.toLowerCase()
  if (lower.includes('embedding') || lower.includes('embed')) return 'embedding'
  if (lower.includes('rerank') || lower.includes('re-rank') || lower.includes('cross-encoder')) return 'rerank'
  if (lower.includes('t2v') || lower.includes('i2v')
    || lower.includes('video-gen') || lower.includes('videogen')
    || lower.includes('maku')) return 'video_generation'
  if (lower.includes('t2i') || lower.includes('i2i')
    || lower.includes('dall-e') || lower.includes('dalle')
    || lower.includes('imagen') || lower.includes('flux')
    || lower.includes('midjourney') || lower.includes('stable-diffusion')
    || lower.includes('sdxl') || lower.includes('cogview')
    || lower.includes('wanx')) return 'image_generation'
  if (lower.includes('video')) return 'video_understanding'
  if (lower.includes('whisper') || lower.includes('asr')
    || lower.includes('speech-to-text') || lower.includes('paraformer')
    || lower.includes('sensevoice')) return 'asr'
  if (lower.includes('tts') || lower.includes('speech-to-speech')
    || lower.includes('speech-synthesis') || lower.includes('cosyvoice')
    || lower.includes('sambert')) return 'tts'
  if (lower.includes('audio')) return 'audio_understanding'
  if (lower.includes('vl') || lower.includes('vision')) return 'vision'
  return 'chat'
}

const providerPageSizes = [10, 20, 50]
const modelPageSizes = [10, 20, 50]
const modalCardStyle = { maxWidth: '860px', width: 'calc(100vw - 32px)' }

// 获取可用模型弹窗：固定高度，避免随列表数量伸缩导致窗口跳动
const fetchModelsModalStyle = { maxWidth: '860px', width: 'calc(100vw - 32px)', height: 'min(640px, calc(100vh - 80px))' }
// 内容区约束为 flex 列，列表在固定高度内滚动，避免卡片内容溢出（穿模）
const fetchModelsModalContentStyle = { display: 'flex', flexDirection: 'column', minHeight: '0', overflow: 'hidden' }
const providerAvatarStyle = { width: '48px', height: '48px', borderRadius: '14px', objectFit: 'cover' }
const providerListAvatarStyle = { width: '40px', height: '40px', borderRadius: '8px', objectFit: 'cover' }

const providerSearch = reactive({ name: '', type: null, status: null })
const providerList = ref([])
const providerLoading = ref(false)
const providerPagination = reactive({ pageNum: 1, pageSize: 10, itemCount: 0 })
const selectedProvider = ref(null)
const modelList = ref([])
const modelLoading = ref(false)
const defaultModelUpdatingId = ref(null)
const providerDefaultUpdatingId = ref(null)
const modelPagination = reactive({ pageNum: 1, pageSize: 10, itemCount: 0 })
const modelSearch = reactive({ modelType: null })
const providerFormRef = ref(null)
const modelFormRef = ref(null)

const providerSelectOptions = computed(() => providerList.value.map(p => ({ label: p.providerName, value: p.id })))

// 模型行操作 loading：{ id: 'test' | 'edit' | 'delete' }
const modelRowActionLoading = reactive({})
const isModelRowActionLoading = (id, type) => modelRowActionLoading[id] === type

const uploadPrefix = import.meta.env.VITE_API_BASEURL || '/api'
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token') || ''
  return { Authorization: `Bearer ${token}` }
})

const providerEditing = ref(false)

const providerModal = reactive({
  show: false,
  isEdit: false,
  saving: false,
  form: createProviderForm(),
})
const providerApiKeyPlaceholder = computed(() => providerModal.isEdit ? '留空表示不修改' : '请输入 API Key')

const DASHSCOPE_NATIVE_BASE_URL = 'https://dashscope.aliyuncs.com'
// 已知供应商类型（字典 ai_provider_type）的默认 OpenAI Compatible 端点，与后端 AiProviderBaseUrlPolicy 对齐
const PROVIDER_DEFAULT_BASE_URL = {
  alibaba: 'https://dashscope.aliyuncs.com/compatible-mode',
  openai: 'https://api.openai.com/v1',
  zhipu: 'https://open.bigmodel.cn/api/paas/v4',
  moonshot: 'https://api.moonshot.cn/v1',
  deepseek: 'https://api.deepseek.com/v1',
  ollama: 'http://localhost:11434/v1',
}
// 已知默认地址集合（含尾斜杠变体）：当前地址是"别的协议/类型的默认"时切换后覆盖，自定义地址不覆盖
const KNOWN_PROVIDER_DEFAULT_URLS = new Set(
  [DASHSCOPE_NATIVE_BASE_URL, ...Object.values(PROVIDER_DEFAULT_BASE_URL)]
    .flatMap(url => [url, `${url}/`]),
)

function resolveDefaultBaseUrl(adapterCode, providerType) {
  if (adapterCode === 'dashscope_native')
    return DASHSCOPE_NATIVE_BASE_URL
  return PROVIDER_DEFAULT_BASE_URL[providerType] || ''
}

watch(
  () => [providerModal.form.adapterCode, providerModal.form.providerType],
  ([adapterCode, providerType]) => {
    const resolved = resolveDefaultBaseUrl(adapterCode, providerType)
    if (!resolved)
      return

    const currentBaseUrl = providerModal.form.baseUrl?.trim() || ''
    if (!currentBaseUrl || (KNOWN_PROVIDER_DEFAULT_URLS.has(currentBaseUrl) && currentBaseUrl !== resolved))
      providerModal.form.baseUrl = resolved
  },
)

const modelModal = reactive({
  show: false,
  isEdit: false,
  saving: false,
  form: createModelForm(),
})

const fetchModelsModal = reactive({
  show: false,
  loading: false,
  importing: false,
  error: '',
  models: [],
  checked: {},
  modelTypes: {}, // { modelId: inferredType } — 每个模型的推断/用户修改后的类型
})

const fetchModelsModalSearch = ref('')
const fetchModelsModalCategory = ref('all')

// 顶部分类筛选：只显示当前供应商列表里真实存在的分类（全部始终保留）
const fetchModelsModalCategories = computed(() => {
  const present = new Set(fetchModelsModal.models.map(m => fetchModelsModal.modelTypes[m.id] || inferModelType(m.id)))
  return [
    { label: '全部', value: 'all' },
    ...MODEL_TYPE_ORDER.filter(t => present.has(t)).map(t => ({ label: MODEL_TYPE_LABEL[t], value: t })),
  ]
})

// 分类筛选 + 搜索过滤（ID 模糊匹配）
const fetchModelsModalFilteredModels = computed(() => {
  const keyword = fetchModelsModalSearch.value.trim().toLowerCase()
  const category = fetchModelsModalCategory.value
  return fetchModelsModal.models.filter(m => {
    if (category !== 'all' && (fetchModelsModal.modelTypes[m.id] || inferModelType(m.id)) !== category)
      return false
    if (keyword && !(m.id || '').toLowerCase().includes(keyword))
      return false
    return true
  })
})

// 按推断能力类型分组（顺序对齐阿里百炼模型广场分类）
const fetchModelsModalGroups = computed(() => {
  const groupsMap = new Map()
  for (const model of fetchModelsModalFilteredModels.value) {
    const type = fetchModelsModal.modelTypes[model.id] || inferModelType(model.id)
    if (!groupsMap.has(type))
      groupsMap.set(type, [])
    groupsMap.get(type).push(model)
  }
  return MODEL_TYPE_ORDER
    .filter(t => groupsMap.has(t))
    .map(t => ({ type: t, label: modelTypeLabel(t), models: groupsMap.get(t) }))
})

function groupModels(type) {
  return fetchModelsModalGroups.value.find(g => g.type === type)?.models || []
}
function isGroupChecked(type) {
  const models = groupModels(type)
  return models.length > 0 && models.every(m => fetchModelsModal.checked[m.id])
}
function isGroupIndeterminate(type) {
  const models = groupModels(type)
  const n = models.filter(m => fetchModelsModal.checked[m.id]).length
  return n > 0 && n < models.length
}
function handleToggleGroup(type, checked) {
  for (const m of groupModels(type))
    fetchModelsModal.checked[m.id] = checked
}

// 导入数量 = 当前筛选视图内已勾选的模型数（切分类/搜索后只统计可见部分）
const fetchModelsModalVisibleCheckedCount = computed(() => fetchModelsModalFilteredModels.value.filter(m => fetchModelsModal.checked[m.id]).length)

function createProviderForm() {
  return {
    providerName: '',
    providerType: null,
    adapterCode: 'openai_compatible',
    logo: '',
    baseUrl: '',
    apiKey: '',
    status: '0',
    remark: '',
  }
}

function createModelForm(providerId = null) {
  return {
    providerId,
    modelType: null,
    modelId: '',
    modelName: '',
    icon: '',
    maxTokens: null,
    contextWindow: null,
    inputPricePerMillionCent: null,
    outputPricePerMillionCent: null,
    capabilityCodes: [],
    sortOrder: 0,
    isDefault: '0',
    status: '0',
    description: '',
  }
}

function handleLogoUploadFinish({ event }) {
  try {
    const res = JSON.parse(event.target.response)
    if (res.code === 200 && res.data) {
      providerModal.form.logo = res.data.fileId || res.data.id || res.data
    }
    else {
      window.$message.error(res.msg || '上传失败')
    }
  }
  catch {
    window.$message.error('上传失败')
  }
  return false
}

function handleIconUploadFinish({ event }) {
  try {
    const res = JSON.parse(event.target.response)
    if (res.code === 200 && res.data) {
      modelModal.form.icon = res.data.fileId || res.data.id || res.data
    }
    else {
      window.$message.error(res.msg || '上传失败')
    }
  }
  catch {
    window.$message.error('上传失败')
  }
  return false
}

const providerRules = {
  providerName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  adapterCode: [{ required: true, message: '请选择连接协议', trigger: 'change' }],
  // baseUrl 选填：已知类型/协议选择后自动填充默认地址；azure/custom 留空时后端会明确提示
  baseUrl: [],
  apiKey: [{
    trigger: 'blur',
    validator(_rule, value) {
      if (!providerModal.isEdit && !value?.trim())
        return new Error('请输入 API Key')
      return true
    },
  }],
}

const modelRules = {
  providerId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }],
  modelId: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
}

const modelColumns = [
  {
    title: '模型名称',
    key: 'modelName',
    width: 150,
    ellipsis: { tooltip: true },
    render(row) { return h('span', { class: 'model-name' }, row.modelName || '未命名模型') },
  },
  {
    title: '模型编码',
    key: 'modelId',
    width: 190,
    ellipsis: { tooltip: true },
    render(row) { return h('code', { class: 'model-code', title: row.modelId }, row.modelId || '—') },
  },
  {
    title: '类型',
    key: 'modelType',
    width: 80,
    render(row) { return h(DictTag, { dictType: 'ai_model_type', value: row.modelType, size: 'small' }) },
  },
  {
    title: '最大 Token',
    key: 'maxTokens',
    width: 90,
    align: 'right',
    render(row) { return row.maxTokens ? row.maxTokens.toLocaleString() : '-' },
  },
  {
    title: '是否默认',
    key: 'isDefault',
    width: 90,
    align: 'center',
    render(row) {
      const isDefault = row.isDefault === '1'
      return h(NSwitch, {
        value: isDefault,
        size: 'small',
        loading: defaultModelUpdatingId.value === row.id,
        disabled: defaultModelUpdatingId.value !== null,
        ariaLabel: isDefault ? '当前默认模型' : '设为默认模型',
        onUpdateValue: value => handleDefaultModelChange(row, value),
      })
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 55,
    align: 'center',
    render(row) { return h(DictTag, { dictType: 'ai_status', value: row.status, size: 'small' }) },
  },
  { title: '描述', key: 'description', width: 170, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    fixed: 'right',
    render(row) {
      const rowLoading = type => isModelRowActionLoading(row.id, type)
      const anyLoading = rowLoading('test') || rowLoading('edit') || rowLoading('delete')
      const actions = [
        h(NButton, { text: true, size: 'small', class: 'text-info', loading: rowLoading('test'), disabled: anyLoading, onClick: () => handleTestModel(row) }, { default: () => '测试' }),
        h(NButton, { text: true, size: 'small', class: 'text-primary', loading: rowLoading('edit'), disabled: anyLoading, onClick: () => handleEditModel(row) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDeleteModel(row) }, {
          trigger: () => h(NButton, { text: true, size: 'small', class: 'text-error', loading: rowLoading('delete'), disabled: anyLoading }, { default: () => '删除' }),
          default: () => '确定删除该模型吗？',
        }),
      ]
      return h('div', { class: 'table-actions' }, actions)
    },
  },
]

function providerInitial(provider) {
  return (provider?.providerName || '?').trim().charAt(0).toUpperCase()
}

async function handleTestModel(row) {
  if (modelRowActionLoading[row.id])
    return
  modelRowActionLoading[row.id] = 'test'
  try {
    const res = await modelTest(row.id)
    if (res.code === 200)
      window.$message.success(res.data || '连接成功')
    else
      window.$message.error(res.msg || '模型连接失败')
  }
  catch (error) {
    window.$message.error(error.message || '模型连接失败')
  }
  finally {
    delete modelRowActionLoading[row.id]
  }
}

function providerModelCount(provider) {
  if (Array.isArray(provider?.models))
    return provider.models.length
  if (!provider?.models)
    return 0
  try {
    const models = JSON.parse(provider.models)
    return Array.isArray(models) ? models.length : 0
  }
  catch {
    return 0
  }
}

function formatProviderTime(value) {
  if (!value)
    return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function paginationPrefix({ itemCount }) {
  return `共 ${itemCount} 条`
}

function handleProviderSearch() {
  providerPagination.pageNum = 1
  clearSelectedProvider()
  loadProviders()
}

function handleResetProviderSearch() {
  providerSearch.name = ''
  providerSearch.type = null
  providerSearch.status = null
  providerPagination.pageNum = 1
  clearSelectedProvider()
  loadProviders()
}

function handleProviderPageChange(page) {
  providerPagination.pageNum = page
  clearSelectedProvider()
  loadProviders()
}

function handleProviderPageSizeChange(pageSize) {
  providerPagination.pageSize = pageSize
  providerPagination.pageNum = 1
  clearSelectedProvider()
  loadProviders()
}

async function loadProviders() {
  providerLoading.value = true
  try {
    const selectedId = selectedProvider.value?.id
    const params = { pageNum: providerPagination.pageNum, pageSize: providerPagination.pageSize }
    if (providerSearch.name)
      params.providerName = providerSearch.name
    if (providerSearch.type)
      params.providerType = providerSearch.type
    if (providerSearch.status)
      params.status = providerSearch.status
    const res = await fetchProviderPage(params)
    if (res.code === 200 && res.data) {
      providerList.value = res.data.records || []
      providerPagination.itemCount = Number(res.data.total || 0)
      if (selectedId) {
        const current = providerList.value.find(provider => provider.id === selectedId)
        if (current)
          selectedProvider.value = current
      }
    }
  }
  catch {}
  finally {
    providerLoading.value = false
  }
}

async function loadModels() {
  if (!selectedProvider.value) {
    modelList.value = []
    return
  }
  modelLoading.value = true
  try {
    const res = await fetchModelPage({
      pageNum: modelPagination.pageNum,
      pageSize: modelPagination.pageSize,
      providerId: selectedProvider.value.id,
      ...(modelSearch.modelType ? { modelType: modelSearch.modelType } : {}),
    })
    if (res.code === 200 && res.data) {
      modelList.value = res.data.records || []
      modelPagination.itemCount = Number(res.data.total || 0)
    }
  }
  catch {}
  finally {
    modelLoading.value = false
  }
}

function handleSelectProvider(row) {
  if (selectedProvider.value?.id === row.id)
    return

  selectedProvider.value = row
  modelPagination.pageNum = 1
  loadModels()
}

function clearSelectedProvider() {
  selectedProvider.value = null
  modelList.value = []
  modelPagination.pageNum = 1
  modelPagination.itemCount = 0
}

function handleModelPageChange(page) {
  modelPagination.pageNum = page
  loadModels()
}

function handleModelPageSizeChange(pageSize) {
  modelPagination.pageSize = pageSize
  modelPagination.pageNum = 1
  loadModels()
}

function handleModelSearch() {
  modelPagination.pageNum = 1
  loadModels()
}

function handleAddProvider() {
  providerModal.isEdit = false
  providerModal.form = createProviderForm()
  providerModal.show = true
}

async function handleEditProvider(row) {
  if (providerEditing.value)
    return
  providerEditing.value = true
  try {
    const res = await providerGetById(row.id)
    if (res.code === 200 && res.data) {
      providerModal.isEdit = true
      providerModal.form = { ...createProviderForm(), ...res.data, apiKey: '' }
      providerModal.show = true
    }
    else {
      window.$message.error(res.msg || '读取供应商失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '读取供应商失败')
  }
  finally {
    providerEditing.value = false
  }
}

async function handleSaveProvider() {
  try {
    await providerFormRef.value?.validate()
  }
  catch { return }
  providerModal.saving = true
  try {
    const payload = { ...providerModal.form }
    if (providerModal.isEdit && !payload.apiKey?.trim())
      delete payload.apiKey
    const res = providerModal.isEdit
      ? await providerUpdate(payload)
      : await providerAdd(payload)
    if (res.code === 200) {
      window.$message.success(providerModal.isEdit ? '更新成功' : '新增成功')
      providerModal.show = false
      await loadProviders()
    }
    else {
      window.$message.error(res.msg || '操作失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '操作失败')
  }
  finally {
    providerModal.saving = false
  }
}

async function handleDeleteProvider(id) {
  try {
    const res = await providerDelete(id)
    if (res.code === 200) {
      window.$message.success('删除成功')
      if (selectedProvider.value?.id === id) {
        clearSelectedProvider()
      }
      await loadProviders()
    }
    else {
      window.$message.error(res.msg || '删除失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '删除失败')
  }
}

async function handleSetDefault(row) {
  if (providerDefaultUpdatingId.value !== null)
    return
  providerDefaultUpdatingId.value = row.id
  try {
    const res = await providerSetDefault(row.id)
    if (res.code === 200) {
      window.$message.success('设置成功')
      await loadProviders()
    }
    else {
      window.$message.error(res.msg || '设置失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '设置失败')
  }
  finally {
    providerDefaultUpdatingId.value = null
  }
}

async function handleFetchModels() {
  if (!selectedProvider.value) {
    window.$message.warning('请先选择供应商')
    return
  }
  const provider = selectedProvider.value
  fetchModelsModal.show = true
  fetchModelsModal.loading = true
  fetchModelsModal.error = ''
  fetchModelsModal.models = []
  fetchModelsModal.checked = {}
  fetchModelsModal.modelTypes = {}
  fetchModelsModalSearch.value = ''
  fetchModelsModalCategory.value = 'all'
  try {
    const res = await providerFetchModels(provider.id)
    if (res.code === 200 && Array.isArray(res.data)) {
      fetchModelsModal.models = res.data
      const checked = {}
      const types = {}
      for (const model of res.data) {
        checked[model.id] = true
        types[model.id] = inferModelType(model.id)
      }
      fetchModelsModal.checked = checked
      fetchModelsModal.modelTypes = types
    }
    else {
      fetchModelsModal.error = res.msg || '获取模型失败'
    }
  }
  catch (e) {
    fetchModelsModal.error = e.message || '获取模型失败'
  }
  finally {
    fetchModelsModal.loading = false
  }
}

function handleToggleModel(id, checked) {
  fetchModelsModal.checked[id] = checked
}

async function handleImportModels() {
  const providerId = selectedProvider.value?.id
  // 只导入当前筛选视图内勾选的模型，与按钮计数保持一致
  const items = fetchModelsModalFilteredModels.value
    .filter(m => fetchModelsModal.checked[m.id])
    .map(m => ({
      modelId: m.id,
      modelType: fetchModelsModal.modelTypes[m.id] || inferModelType(m.id),
    }))
  if (!providerId || items.length === 0)
    return

  fetchModelsModal.importing = true
  try {
    const res = await providerBatchImportModels(providerId, items)
    if (res.code === 200) {
      window.$message.success(`已导入 ${res.data ?? items.length} 个模型`)
      fetchModelsModal.show = false
      modelPagination.pageNum = 1
      await Promise.all([loadModels(), loadProviders()])
    }
    else {
      window.$message.error(res.msg || '导入失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '导入失败')
  }
  finally {
    fetchModelsModal.importing = false
  }
}

function handleAddModel(provider = selectedProvider.value) {
  if (!provider) {
    window.$message.warning('请先选择供应商')
    return
  }
  const providerChanged = selectedProvider.value?.id !== provider.id
  selectedProvider.value = provider
  if (providerChanged) {
    modelPagination.pageNum = 1
    loadModels()
  }
  modelModal.isEdit = false
  modelModal.form = createModelForm(provider.id || null)
  modelModal.show = true
}

async function handleEditModel(row) {
  if (modelRowActionLoading[row.id])
    return
  modelRowActionLoading[row.id] = 'edit'
  try {
    const res = await modelGetById(row.id)
    if (res.code === 200 && res.data) {
      modelModal.isEdit = true
      modelModal.form = { ...res.data }
      modelModal.show = true
    }
    else {
      window.$message.error(res.msg || '读取模型失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '读取模型失败')
  }
  finally {
    delete modelRowActionLoading[row.id]
  }
}

async function handleSaveModel() {
  try {
    await modelFormRef.value?.validate()
  }
  catch { return }
  modelModal.saving = true
  try {
    const res = modelModal.isEdit
      ? await modelUpdate(modelModal.form)
      : await modelAdd(modelModal.form)
    if (res.code === 200) {
      window.$message.success(modelModal.isEdit ? '更新成功' : '新增成功')
      modelModal.show = false
      await Promise.all([loadModels(), loadProviders()])
    }
    else {
      window.$message.error(res.msg || '操作失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '操作失败')
  }
  finally {
    modelModal.saving = false
  }
}

async function handleDeleteModel(row) {
  if (modelRowActionLoading[row.id])
    return
  modelRowActionLoading[row.id] = 'delete'
  try {
    const res = await modelDelete(row.id)
    if (res.code === 200) {
      window.$message.success('删除成功')
      if (modelList.value.length === 1 && modelPagination.pageNum > 1)
        modelPagination.pageNum -= 1
      await Promise.all([loadModels(), loadProviders()])
    }
    else {
      window.$message.error(res.msg || '删除失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '删除失败')
  }
  finally {
    delete modelRowActionLoading[row.id]
  }
}

async function handleDefaultModelChange(row, value) {
  if (!value) {
    window.$message.warning('请直接将其他模型设为默认，供应商必须保留一个默认模型')
    return
  }
  if (row.isDefault === '1' || defaultModelUpdatingId.value !== null)
    return

  defaultModelUpdatingId.value = row.id
  try {
    const detail = await modelGetById(row.id)
    if (detail.code !== 200 || !detail.data) {
      window.$message.error(detail.msg || '读取模型失败')
      return
    }
    const res = await modelUpdate({ ...detail.data, isDefault: '1' })
    if (res.code === 200) {
      window.$message.success('默认模型已更新')
      await Promise.all([loadModels(), loadProviders()])
    }
    else {
      window.$message.error(res.msg || '设置默认模型失败')
    }
  }
  catch (e) {
    window.$message.error(e.message || '设置默认模型失败')
  }
  finally {
    defaultModelUpdatingId.value = null
  }
}

onMounted(() => {
  loadProviders()
})
</script>

<style scoped>
.ai-provider-model-page {
  --page-bg: #f3f6fa;
  --panel-bg: #ffffff;
  --panel-subtle: #f8fafc;
  --panel-border: #dfe6ee;
  --text-strong: #111827;
  --text-body: #475569;
  --text-muted: #64748b;
  --accent: #0369a1;
  --accent-soft: #eaf4fb;
  --accent-border: #b9d9ec;
  --success-soft: #ecfdf3;
  --danger-soft: #fef2f2;
  --shadow: 0 12px 32px rgba(15, 23, 42, 0.06);
  min-height: 100%;
  padding: 20px;
  overflow-x: hidden;
  color: var(--text-body);
  background: radial-gradient(circle at 100% 0, rgba(14, 116, 144, 0.07), transparent 340px), var(--page-bg);
}

:global(.dark) .ai-provider-model-page {
  --page-bg: #0d1420;
  --panel-bg: #151f2d;
  --panel-subtle: #111a27;
  --panel-border: #2c3a4d;
  --text-strong: #f1f5f9;
  --text-body: #cbd5e1;
  --text-muted: #94a3b8;
  --accent: #38bdf8;
  --accent-soft: rgba(14, 165, 233, 0.12);
  --accent-border: rgba(56, 189, 248, 0.3);
  --success-soft: rgba(34, 197, 94, 0.1);
  --danger-soft: rgba(239, 68, 68, 0.1);
  --shadow: 0 14px 40px rgba(0, 0, 0, 0.18);
  background: radial-gradient(circle at 100% 0, rgba(14, 165, 233, 0.08), transparent 360px), var(--page-bg);
}

.provider-workspace {
  display: grid;
  grid-template-columns: minmax(500px, 0.88fr) minmax(0, 1.12fr);
  gap: 16px;
  align-items: start;
}

.provider-workspace > * {
  min-width: 0;
}

:deep(.n-data-table .n-data-table-th),
:deep(.n-data-table .n-data-table-td) {
  padding: 10px 12px;
}

:deep(.n-data-table .n-data-table-th) {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  background: var(--panel-bg);
}

:deep(.provider-row) {
  cursor: pointer;
  transition: background-color 180ms ease;
}

:deep(.provider-row:hover),
:deep(.provider-row:focus-visible) {
  background: var(--panel-subtle) !important;
}

:deep(.provider-row:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

:deep(.provider-row--selected),
:deep(.provider-row--selected:hover) {
  background: var(--accent-soft) !important;
}

:deep(.provider-row--selected td:first-child) {
  box-shadow: inset 3px 0 0 var(--accent);
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-height: 32px;
  white-space: nowrap;
}

.modal-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
}

.form-item--full {
  grid-column: 1 / -1;
}

.form-control-stack {
  width: 100%;
}

.field-tip {
  display: block;
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.logo-preview {
  position: relative;
  display: inline-block;
}

.logo-remove {
  position: absolute;
  top: -6px;
  right: -6px;
}

@media (max-width: 1260px) {
  .provider-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .ai-provider-model-page {
    padding: 12px;
  }

  .modal-form-grid {
    grid-template-columns: 1fr;
  }

  .form-item--full {
    grid-column: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.provider-row) {
    transition: none;
  }
}

/* 参考主流供应商控制台：简洁的左侧列表 + 右侧配置工作区。 */
.ai-provider-model-page {
  padding: 0 12px 12px;
  background: transparent;
}

.model-name {
  color: var(--text-strong);
  font-weight: 600;
}

.model-code {
  color: var(--text-muted);
  font-family: inherit;
  font-size: 12px;
}

.inline-default-editor {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.inline-default-editor__label {
  color: #16a34a;
  font-size: 12px;
  font-weight: 500;
}

:global(.dark) .inline-default-editor__label {
  color: #4ade80;
}

.provider-workspace {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
}

.provider-list-panel,
.provider-detail-panel {
  min-width: 0;
  min-height: calc(100vh - 150px);
  overflow: hidden;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 9px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}

.provider-list-panel {
  display: flex;
  flex-direction: column;
}

.provider-list-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid var(--panel-border);
}

.provider-list-panel__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-list-panel__header h2,
.provider-detail-header h2,
.provider-detail-empty h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
}

.provider-list-filters {
  padding: 12px;
  background: var(--panel-subtle);
  border-bottom: 1px solid var(--panel-border);
}

.provider-list-filters__row,
.provider-list-filters__actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.provider-list-filters__row > * {
  min-width: 0;
  flex: 1;
}

.provider-list-filters__actions {
  justify-content: flex-end;
}

.provider-list-panel > .n-spin-container {
  min-height: 0;
  flex: 1;
}

.provider-list-panel :deep(.n-spin-content) {
  height: 100%;
}

.provider-list {
  min-height: 260px;
  max-height: calc(100vh - 355px);
  overflow-y: auto;
}

.provider-list-item {
  display: flex;
  width: 100%;
  padding: 14px 13px;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--panel-border);
  transition: background-color 160ms ease;
}

.provider-list-item:hover,
.provider-list-item:focus-visible {
  background: var(--panel-subtle);
  outline: none;
}

.provider-list-item:focus-visible {
  box-shadow: inset 0 0 0 2px var(--accent);
}

.provider-list-item--selected,
.provider-list-item--selected:hover {
  background: var(--accent-soft);
  box-shadow: inset 3px 0 0 var(--accent);
}

.provider-list-item__avatar,
.provider-detail-header__avatar {
  display: grid;
  flex: 0 0 auto;
  overflow: hidden;
  color: #fff;
  font-weight: 600;
  place-items: center;
  background: #475569;
}

.provider-list-item__avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

.provider-list-item__content {
  display: block;
  min-width: 0;
  flex: 1;
}

.provider-list-item__title,
.provider-list-item__meta,
.provider-detail-header__identity,
.provider-detail-header__title,
.provider-detail-header__actions,
.provider-detail-header__time,
.secret-config-row,
.model-tab-toolbar {
  display: flex;
  align-items: center;
}

.provider-list-item__title {
  min-width: 0;
  justify-content: flex-start;
  gap: 6px;
}

.provider-list-item__title strong {
  flex: 0 0 auto;
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.provider-list-item__meta {
  gap: 7px;
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 11px;
}

.provider-list-item__meta span + span::before {
  margin-right: 7px;
  content: '|';
}

.provider-list-item__arrow {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: 14px;
}

.provider-list-item--selected .provider-list-item__arrow {
  color: var(--accent);
}

.provider-list :deep(.n-empty) {
  padding: 48px 16px;
}

.provider-list-pagination {
  display: flex;
  padding: 11px 12px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: auto;
  overflow-x: auto;
  color: var(--text-muted);
  border-top: 1px solid var(--panel-border);
  font-size: 11px;
}

.provider-detail-header {
  display: flex;
  padding: 18px 20px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--panel-border);
}

.provider-detail-header__identity {
  min-width: 0;
  gap: 12px;
}

.provider-detail-header__avatar {
  width: 48px;
  height: 48px;
  border-radius: 10px;
}

.provider-detail-header__title {
  flex-wrap: wrap;
  gap: 8px;
}

.provider-detail-header__title h2 {
  font-size: 17px;
}

.provider-detail-header__time {
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 7px;
  color: var(--text-muted);
  font-size: 11px;
}

.provider-detail-header__actions {
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.provider-config-content {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.config-section {
  overflow: hidden;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
}

.config-section__title {
  display: flex;
  padding: 11px 14px;
  align-items: center;
  gap: 8px;
  color: var(--text-strong);
  background: var(--panel-subtle);
  border-bottom: 1px solid var(--panel-border);
  font-size: 13px;
}

.config-section__title i {
  color: var(--accent);
}

.config-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
  padding: 16px;
}

.config-field {
  min-width: 0;
}

.config-field > span {
  display: block;
  margin-bottom: 7px;
  color: var(--text-body);
  font-size: 12px;
}

.config-field--full {
  grid-column: 1 / -1;
}

.config-field__value {
  display: flex;
  min-height: 34px;
  padding: 0 10px;
  align-items: center;
  background: var(--panel-subtle);
  border: 1px solid var(--panel-border);
  border-radius: 6px;
  font-size: 13px;
}

.secret-config-row {
  padding: 16px;
  align-items: flex-end;
  gap: 12px;
}

.secret-config-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.secret-config-input .n-input {
  flex: 1;
  min-width: 0;
}

.secret-config-row .config-field {
  flex: 1;
}

.secret-config-row small {
  display: block;
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 11px;
}

.model-section {
  border-top: 1px solid var(--panel-border);
}

.model-section__heading {
  display: flex;
  padding: 13px 16px 0;
  align-items: center;
  gap: 8px;
  color: var(--text-strong);
  font-size: 13px;
}

.model-section__heading i {
  color: var(--accent);
}

.model-tab-toolbar {
  padding: 13px 16px;
  justify-content: space-between;
  gap: 14px;
  color: var(--text-muted);
  font-size: 12px;
}

.model-tab-toolbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.model-tab-toolbar__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.modal-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.model-pagination {
  display: flex;
  padding: 12px 16px;
  justify-content: flex-end;
  overflow-x: auto;
  border-top: 1px solid var(--panel-border);
}

.provider-detail-empty {
  display: flex;
  min-height: calc(100vh - 152px);
  padding: 32px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--text-muted);
  text-align: center;
}

.provider-detail-empty > i {
  margin-bottom: 12px;
  color: var(--text-muted);
  font-size: 32px;
}

.provider-detail-empty p {
  margin: 7px 0 0;
  font-size: 12px;
}

@media (max-width: 1120px) {
  .provider-workspace {
    grid-template-columns: 1fr;
  }

  .provider-list-panel,
  .provider-detail-panel {
    min-height: auto;
  }

  .provider-list {
    max-height: 360px;
  }

  .provider-detail-empty {
    min-height: 260px;
  }
}

@media (max-width: 720px) {
  .ai-provider-model-page {
    padding: 0 8px 8px;
  }

  .provider-detail-header,
  .provider-detail-header__actions,
  .secret-config-row,
  .model-tab-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .provider-detail-header__actions {
    justify-content: flex-start;
  }

  .config-form-grid {
    grid-template-columns: 1fr;
  }

  .config-field--full {
    grid-column: auto;
  }

  .provider-list-pagination,
  .model-pagination {
    align-items: flex-start;
    flex-direction: column;
  }
}

.fetch-models-loading {
  display: flex;
  flex: 1 1 auto;
  min-height: 200px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  color: var(--text-muted);
}

.fetch-models-loading i {
  font-size: 32px;
}

.fetch-models-result {
  flex: 1 1 auto;
  justify-content: center;
}

.fetch-models-categories {
  padding: 2px 2px 10px;
  border-bottom: 1px solid var(--panel-border);
  margin-bottom: 10px;
}

.fetch-models-categories :deep(.n-radio-group) {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
}

.fetch-models-toolbar {
  display: flex;
  padding: 0 2px 12px;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 13px;
}

.fetch-models-count {
  flex: 0 0 auto;
}

.fetch-models-search {
  flex: 1 1 auto;
  min-width: 160px;
  max-width: 260px;
  margin-left: auto;
}

.fetch-models-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 12px;
  align-content: start;
  flex: 1 1 auto;
  min-height: 0;
  padding: 4px 2px;
  overflow-y: auto;
  border-top: 1px solid var(--panel-border);
}

.fetch-models-group-title {
  display: flex;
  grid-column: 1 / -1;
  padding: 10px 6px 6px;
  align-items: center;
  gap: 8px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 600;
}

.fetch-models-group-name {
  flex: 0 0 auto;
}

.fetch-models-group-count {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 400;
  background: var(--bg-secondary);
  border-radius: 10px;
  padding: 0 8px;
  line-height: 18px;
}

.fetch-models-item {
  display: flex;
  min-width: 0;
  padding: 5px 8px;
  align-items: center;
  gap: 8px;
  border-radius: 6px;
}

.fetch-models-item:hover {
  background: var(--bg-hover);
}

.fetch-models-item :deep(.n-checkbox) {
  flex: 1 1 auto;
  min-width: 0;
}

.fetch-models-item :deep(.n-checkbox__label) {
  flex: 1 1 auto;
  min-width: 0;
}

.fetch-models-list :deep(.n-empty) {
  grid-column: 1 / -1;
}

.fetch-models-model-id {
  display: block;
  color: var(--text-strong);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fetch-models-type-select {
  flex: 0 0 auto;
}
</style>
