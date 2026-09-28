<template>
  <div class="external-manage-page">
    <n-card class="panel-card">
      <template #header>
        <div class="panel-header">
          <div>
            <span>{{ apiPageTitle }}</span>
            <div class="panel-subtitle">
              {{ panelSubtitle }}
            </div>
          </div>
          <NSpace align="center">
            <NTag v-if="!isApiStandalone && !apiViewMode" size="small" type="info">
              {{ systemCountText }}
            </NTag>
            <NButton v-if="!isApiStandalone" size="small" secondary @click="backToSystems">
              返回系统列表
            </NButton>
            <NButton v-if="!isApiStandalone && !apiViewMode" size="small" secondary @click="showAllApis">
              查看全部接口
            </NButton>
          </NSpace>
        </div>
      </template>

      <AiCrudPage
        v-if="!isApiStandalone && !apiViewMode"
        ref="systemCrudRef"
        api="/external/system"
        :api-config="systemApiConfig"
        :load-detail-on-edit="true"
        :search-schema="systemSearchSchema"
        :columns="systemTableColumns"
        :edit-schema="systemEditSchema"
        :before-render-list="beforeRenderSystemList"
        :before-render-detail="beforeRenderSystemDetail"
        :before-render-form="beforeRenderSystemForm"
        :before-submit="beforeSubmitSystem"
        row-key="id"
        add-button-text="新增外部系统"
        :edit-grid-cols="2"
        form-open-mode="modal"
        modal-width="min(920px, 96vw)"
        edit-label-width="120px"
        :hide-form-section-nav="true"
        :hide-selection="true"
        :scroll-x="1180"
        max-height="var(--external-table-max-height)"
        :search-y-gap="8"
        @load-list-success="handleSystemListLoaded"
        @submit-success="handleSystemSaved"
        @delete="handleSystemDeleted"
      >
        <template #form-customAuthConfig="{ value, updateValue, formData }">
          <div class="auth-config-panel">
            <div class="auth-config-toolbar">
              <span>按适配器要求填写键值，密钥不会出现在列表中。</span>
              <NButton size="small" secondary @click="useQichachaPreset(formData, updateValue)">
                填充企查查认证
              </NButton>
            </div>
            <ExternalConfigEditor
              :model-value="value"
              mode="key-value"
              hint="企查查常用键：appKey、secret"
              @update:model-value="updateValue"
            />
          </div>
        </template>

        <template #form-systemAdvanced="{ formData }">
          <n-collapse class="system-advanced">
            <n-collapse-item title="网络与稳定性（超时 / 重试 / 日志）" name="network">
              <div class="adv-row">
                <label class="adv-item">
                  <span class="adv-label">连接超时(ms)</span>
                  <n-input-number v-model:value="formData.connectTimeout" size="small" :min="100" :max="120000" :step="1000" />
                </label>
                <label class="adv-item">
                  <span class="adv-label">读取超时(ms)</span>
                  <n-input-number v-model:value="formData.readTimeout" size="small" :min="100" :max="120000" :step="1000" />
                </label>
                <label class="adv-item">
                  <span class="adv-label">写入超时(ms)</span>
                  <n-input-number v-model:value="formData.writeTimeout" size="small" :min="100" :max="120000" :step="1000" />
                </label>
              </div>
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.retryEnabled" size="small" />
                  <span>失败自动重试</span>
                </div>
                <template v-if="formData.retryEnabled">
                  <label class="adv-item">
                    <span class="adv-label">最大重试次数</span>
                    <n-input-number v-model:value="formData.retryMaxAttempts" size="small" :min="1" :max="5" :step="1" />
                  </label>
                  <label class="adv-item">
                    <span class="adv-label">重试间隔(ms)</span>
                    <n-input-number v-model:value="formData.retryBackoffInterval" size="small" :min="0" :max="5000" :step="500" />
                  </label>
                </template>
              </div>
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.requestLoggingEnabled" size="small" />
                  <span>记录调用日志</span>
                </div>
              </div>
            </n-collapse-item>
          </n-collapse>
        </template>
      </AiCrudPage>

      <AiCrudPage
        v-else
        ref="apiCrudRef"
        api="/external/api"
        :api-config="apiApiConfig"
        :load-detail-on-edit="true"
        :search-schema="apiSearchSchema"
        :columns="apiTableColumns"
        :edit-schema="apiEditSchema"
        :before-load-list="beforeLoadApiList"
        :before-render-form="beforeRenderApiForm"
        :before-render-detail="beforeRenderApiDetail"
        :before-submit="beforeSubmitApi"
        row-key="id"
        add-button-text="新增外部接口"
        :edit-grid-cols="2"
        form-open-mode="modal"
        modal-width="min(960px, 96vw)"
        edit-label-width="128px"
        :hide-form-section-nav="true"
        :hide-selection="true"
        :scroll-x="1780"
        max-height="var(--external-table-max-height)"
        :search-y-gap="8"
        @submit-success="handleApiSaved"
        @delete="handleApiDeleted"
      >
        <template v-for="slotName in apiFormSlotNames" :key="slotName" #[`form-${slotName}`]="slotProps">
          <ExternalApiFormSlot
            :name="slotName"
            :base-url-map="systemBaseUrlMap"
            v-bind="slotProps"
            @fill-preset="useQichachaApiPreset"
          />
        </template>

        <template #form-responsePaths="{ formData }">
          <ApiConfigSection :step="3" title="数据与错误定位" desc="候选项来自上方「返回字段解析」的结果，也可手动输入路径">
            <div class="resp-paths">
              <PathFieldPicker v-model:value="formData.responseDataPath" label="数据路径" placeholder="如：data.records" :options="pathOptionsFor(formData, 'array')" />
              <PathFieldPicker v-model:value="formData.responseTotalPath" label="总数路径" placeholder="如：data.total" :options="pathOptionsFor(formData, 'integer')" />
              <PathFieldPicker v-model:value="formData.errorCodePath" label="错误码路径" placeholder="如：code" :options="pathOptionsFor(formData)" />
              <PathFieldPicker v-model:value="formData.errorMsgPath" label="错误消息路径" placeholder="如：message" :options="pathOptionsFor(formData)" />
            </div>
          </ApiConfigSection>
        </template>

        <template #form-apiAdvanced="{ formData }">
          <n-collapse class="api-advanced">
            <n-collapse-item title="高级设置（响应转换 / 限流 / 缓存 / 权限 / 低代码开放）" name="advanced">
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.responseTransformEnabled" size="small" />
                  <span>启用响应字段映射</span>
                </div>
              </div>
              <n-input
                v-if="formData.responseTransformEnabled"
                v-model:value="formData.responseTransformScript"
                type="textarea"
                size="small"
                :rows="7"
                placeholder="仅支持字段映射 JSON，例如：{&quot;version&quot;:&quot;FIELD_MAP_V1&quot;,&quot;sourcePath&quot;:&quot;data.records&quot;,&quot;fieldMapping&quot;:{&quot;id&quot;:&quot;id&quot;,&quot;name&quot;:&quot;profile.name&quot;},&quot;targetPath&quot;:&quot;records&quot;}"
              />
              <div v-if="formData.responseTransformEnabled" class="secondary-text">
                仅支持 FIELD_MAP_V1 白名单字段映射；不执行 JavaScript。fieldMapping 使用“目标字段: 源字段路径”。
              </div>
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.rateLimitEnabled" size="small" />
                  <span>启用限流</span>
                </div>
                <label v-if="formData.rateLimitEnabled" class="adv-item">
                  <span class="adv-label">限流QPS</span>
                  <n-input-number v-model:value="formData.rateLimitQps" size="small" :min="1" :step="1" />
                </label>
              </div>
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.cacheEnabled" size="small" />
                  <span>启用缓存</span>
                </div>
                <template v-if="formData.cacheEnabled">
                  <label class="adv-item">
                    <span class="adv-label">缓存时长(秒)</span>
                    <n-input-number v-model:value="formData.cacheTtl" size="small" :min="1" :step="60" />
                  </label>
                  <n-input
                    v-model:value="formData.cacheKeyTemplate"
                    size="small"
                    class="adv-input--wide"
                    placeholder="缓存Key模板，如 external:user:{userId}"
                  />
                </template>
              </div>
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.permissionCheckEnabled" size="small" />
                  <span>启用权限校验</span>
                </div>
                <n-input
                  v-if="formData.permissionCheckEnabled"
                  v-model:value="formData.requiredPermission"
                  size="small"
                  class="adv-input--wide"
                  placeholder="所需权限标识，如 external:user:list"
                />
              </div>
              <div class="adv-row">
                <div class="adv-switch">
                  <n-switch v-model:value="formData.lowcodeQueryEnabled" size="small" />
                  <span>开放为低代码查询源</span>
                </div>
                <span v-if="formData.lowcodeQueryEnabled" class="adv-tip">需同时启用权限校验并配置权限码</span>
              </div>
              <div class="adv-row">
                <label class="adv-item">
                  <span class="adv-label">排序</span>
                  <n-input-number v-model:value="formData.sortOrder" size="small" :min="0" :step="1" />
                </label>
              </div>
            </n-collapse-item>
          </n-collapse>
        </template>
      </AiCrudPage>
    </n-card>

    <n-modal v-model:show="debugModalVisible" title="接口调试" preset="card" style="width: min(880px, 92vw)" :mask-closable="false">
      <div class="debug-panel">
        <div class="debug-title">
          {{ debugApi?.apiName || '-' }}
          <DictTag :options="methodOptions" :value="debugApi?.apiMethod" size="small" force-tag />
        </div>
        <div v-if="debugFieldSchema.length" class="debug-fields">
          <div v-for="field in debugFieldSchema" :key="field.name" class="debug-field">
            <span>{{ field.label || field.name }}<em v-if="field.required">必填</em></span>
            <n-switch
              v-if="field.type === 'boolean'"
              v-model:value="debugFieldValues[field.name]"
              class="debug-field-control"
            />
            <n-input-number
              v-else-if="field.type === 'integer' || field.type === 'number'"
              v-model:value="debugFieldValues[field.name]"
              class="debug-field-control"
              :placeholder="field.path ? `参数名：${field.name}` : '请输入数值'"
            />
            <n-input
              v-else
              v-model:value="debugFieldValues[field.name]"
              :placeholder="field.path ? `参数名：${field.name}` : '请输入参数值'"
            />
          </div>
        </div>
        <n-input v-else v-model:value="debugParamsText" type="textarea" :rows="8" placeholder="请输入 JSON 调试参数，例如：{&quot;userId&quot;:&quot;1001&quot;}" />
        <div v-if="debugResult" class="debug-result">
          <NSpace align="center">
            <DictTag :options="callStatusOptions" :value="debugResult.success ? 1 : 0" force-tag />
            <span>HTTP: {{ debugResult.httpStatusCode ?? '-' }}</span>
            <span>耗时: {{ debugResult.durationMs ?? '-' }}ms</span>
          </NSpace>
          <pre>{{ formatJson(debugResult.responseData || debugResult.responseBody || debugResult.errorMessage) }}</pre>
        </div>
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="debugModalVisible = false">
            关闭
          </NButton>
          <NButton type="primary" :loading="debugLoading" @click="handleRunDebug">
            发送调试
          </NButton>
        </NSpace>
      </template>
    </n-modal>

    <n-modal v-model:show="usageModalVisible" title="前端调用方式" preset="card" style="width: min(880px, 92vw)" :mask-closable="false">
      <div class="usage-panel">
        <div class="usage-meta">
          <NTag size="small" type="success">
            {{ usageApi?.apiName || '-' }}
          </NTag>
          <span>{{ usageApi?.apiMethod || 'GET' }} /external/proxy/{{ usageApi?.id || '-' }}</span>
        </div>
        <n-input :value="usageCode" type="textarea" :rows="12" readonly />
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="usageModalVisible = false">
            关闭
          </NButton>
          <NButton type="primary" @click="copyUsageCode">
            复制示例代码
          </NButton>
        </NSpace>
      </template>
    </n-modal>

    <n-modal v-model:show="docModalVisible" title="接口文档" preset="card" style="width: min(880px, 92vw)" :mask-closable="false">
      <div class="doc-panel">
        <div class="doc-current">
          <span>当前文档：</span>
          <a v-if="docApi?.docFileId" class="table-link" @click="downloadDoc">
            {{ docApi.docFileName || docApi.docFileId }}
          </a>
          <span v-else class="secondary-text">暂无文档</span>
        </div>
        <FileUpload
          v-model="docFiles"
          business-type="external_api_doc"
          :business-id="docApi?.id ? String(docApi.id) : ''"
          :limit="1"
          :multiple="false"
          :file-size="20"
          :file-type="['doc', 'docx', 'md', 'txt']"
          value-type="object"
          upload-button-text="上传接口文档"
          @remove="handleDocRemove"
          @success="handleDocUploadSuccess"
        />
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="docModalVisible = false">
            关闭
          </NButton>
          <NButton v-if="docApi?.docFileId" secondary @click="downloadDoc">
            下载文档
          </NButton>
          <NButton type="primary" :loading="docSaving" @click="handleSaveDoc">
            保存
          </NButton>
        </NSpace>
      </template>
    </n-modal>

    <n-drawer v-model:show="logDrawerVisible" :width="logDrawerWidth" placement="right">
      <n-drawer-content :title="logDrawerTitle" closable>
        <div class="log-summary-strip">
          <div class="log-summary-card">
            <span>调用总数</span>
            <strong>{{ logSummary.totalCount || 0 }}</strong>
          </div>
          <div class="log-summary-card success">
            <span>成功率</span>
            <strong>{{ formatPercent(logSummary.successRate) }}</strong>
          </div>
          <div class="log-summary-card danger">
            <span>失败数</span>
            <strong>{{ logSummary.failureCount || 0 }}</strong>
          </div>
          <div class="log-summary-card">
            <span>平均耗时</span>
            <strong>{{ formatDuration(logSummary.avgDurationMs) }}</strong>
          </div>
          <div class="log-summary-card">
            <span>最大耗时</span>
            <strong>{{ formatDuration(logSummary.maxDurationMs) }}</strong>
          </div>
        </div>
        <AiCrudPage
          v-if="logDrawerVisible"
          ref="logCrudRef"
          api="/external/api/log"
          :api-config="logApiConfig"
          :load-detail-on-edit="true"
          :search-schema="logSearchSchema"
          :columns="logTableColumns"
          :edit-schema="logDetailSchema"
          :before-load-list="beforeLoadLogList"
          row-key="id"
          :hide-add="true"
          :hide-selection="true"
          :hide-modal-footer="true"
          :scroll-x="1500"
          modal-width="920px"
        >
          <template #toolbar-start>
            <NButton size="small" type="error" secondary @click="handleClearLogs">
              清空日志
            </NButton>
            <NButton size="small" secondary @click="handleRefreshLogs">
              刷新统计
            </NButton>
          </template>
        </AiCrudPage>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script>
import { useExternalManage } from './composables/useExternalManage'
import { externalManageLocalComponents } from './externalManageLocalComponents'

export default {
  name: 'ExternalManage',
  components: {
    ...externalManageLocalComponents,
  },
  props: {
    initialView: {
      type: String,
      default: 'system',
    },
  },
  setup(props, { emit }) {
    return useExternalManage(props, emit)
  },
}
</script>

<style scoped lang="scss" src="./externalManage.scss"></style>
