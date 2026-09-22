<template>
  <n-modal
    :show="show"
    preset="card"
    :title="modalTitle"
    class="capability-register-modal"
    :mask-closable="false"
    :close-on-esc="!submitting"
    :closable="!submitting"
    @update:show="emit('update:show', $event)"
  >
    <div class="registration-progress" aria-label="注册进度">
      <span :class="{ current: step === 1 }">1 选择场景</span>
      <i class="i-lucide:chevron-right" />
      <span :class="{ current: step === 2 }">2 配置来源</span>
      <i class="i-lucide:chevron-right" />
      <span :class="{ current: step === 3 }">3 确认发布</span>
    </div>
    <div v-if="step === 1" class="scenario-picker">
      <div class="scenario-intro">
        <h3>你想开放什么功能？</h3><p>选择来源后，平台会生成接口契约、授权入口和调用文档。</p>
      </div>
      <button v-if="allowedTypes.includes('FLOW_ACTION') || allowedTypes.includes('SYSTEM_SERVICE')" type="button" class="scenario-option" @click="chooseScenario('flow')">
        <i class="i-lucide:workflow" /><span><strong>流程操作</strong><small>发起流程、提交申请、审批与撤回</small></span><i class="i-lucide:chevron-right" />
      </button>
      <button v-if="allowedTypes.includes('BUSINESS_ACTION') || allowedTypes.includes('SYSTEM_SERVICE')" type="button" class="scenario-option" @click="chooseScenario('application')">
        <i class="i-lucide:panels-top-left" /><span><strong>低代码应用</strong><small>选择应用页面，开放表单填报或业务动作</small></span><i class="i-lucide:chevron-right" />
      </button>
      <button v-if="allowedTypes.includes('SYSTEM_SERVICE')" type="button" class="scenario-option" @click="chooseScenario('rest')">
        <i class="i-lucide:braces" /><span><strong>系统 REST 接口</strong><small>将已注册的系统接口转为可授权的开放能力</small></span><i class="i-lucide:chevron-right" />
      </button>
    </div>
    <n-form
      v-if="step > 1"
      v-show="step === 2"
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="top"
      label-width="112px"
    >
      <n-alert v-if="isUpgrade" type="info" class="form-alert">
        当前版本为 {{ capability.currentVersion }}。新版本会重新读取当前绑定生成快照，旧版本保持不变。
      </n-alert>

      <div v-if="sourceLoading" class="source-loading" role="status" aria-live="polite">
        <n-spin size="small" /><span>{{ isUpgrade ? '正在读取当前能力和发布来源…' : scenario === 'rest' ? '正在加载可开放的系统接口…' : '正在加载已发布的页面和能力来源…' }}</span>
      </div>

      <n-form-item v-if="scenario !== 'rest'" label="操作方式">
        <n-radio-group v-model:value="form.sourceType" :disabled="isUpgrade" @update:value="handleSourceTypeChange">
          <n-radio-button v-if="scenario === 'application' && allowedTypes.includes('BUSINESS_ACTION')" value="BUSINESS_ACTION">
            执行业务动作
          </n-radio-button>
          <n-radio-button v-if="scenario === 'flow' && allowedTypes.includes('SYSTEM_SERVICE')" value="APPLICATION_PROCESS">
            应用业务流程
          </n-radio-button>
          <n-radio-button v-if="scenario === 'flow' && allowedTypes.includes('FLOW_ACTION') && (!initialContext.lockApplication || form.sourceType === 'FLOW_ACTION' && isUpgrade)" value="FLOW_ACTION">
            旧版对象审批
          </n-radio-button>
          <n-radio-button v-if="allowedTypes.includes('SYSTEM_SERVICE') && !(initialContext.lockApplication && scenario === 'flow')" value="SYSTEM_SERVICE">
            {{ scenario === 'application' ? '表单填报' : '独立流程' }}
          </n-radio-button>
        </n-radio-group>
      </n-form-item>

      <n-alert v-if="sourceError" type="error" class="form-alert">
        {{ sourceError }}
        <n-button text type="error" @click="form.sourceType === 'SYSTEM_SERVICE' ? loadSystemServices() : loadObjects()">
          重新加载来源
        </n-button>
      </n-alert>

      <CapabilityApplicationSource v-if="!isUpgrade && !['SYSTEM_SERVICE', 'APPLICATION_PROCESS'].includes(form.sourceType) && !advancedSource" @select="selectApplicationPage" />
      <n-button v-if="!isUpgrade && !initialContext.lockApplication && !['SYSTEM_SERVICE', 'APPLICATION_PROCESS'].includes(form.sourceType)" text size="small" class="source-toggle" @click="advancedSource = !advancedSource">
        {{ advancedSource ? '从应用页面选择' : '高级：直接选择业务对象' }}
      </n-button>
      <n-form-item v-if="!['SYSTEM_SERVICE', 'APPLICATION_PROCESS'].includes(form.sourceType) && (advancedSource || isUpgrade)" label="业务对象" path="objectId">
        <n-select
          v-model:value="form.objectId"
          :options="objectOptions"
          :loading="objectLoading"
          :disabled="isUpgrade"
          placeholder="请选择已发布业务对象"
          filterable
          @update:value="handleObjectChange"
        >
          <template #empty>
            <n-empty size="small" description="暂无已发布业务对象" />
          </template>
        </n-select>
      </n-form-item>

      <n-alert v-if="form.sourceType === 'FLOW_ACTION' && flowSourceError" type="error" class="form-alert">
        {{ flowSourceError }}
      </n-alert>
      <n-alert
        v-else-if="form.sourceType === 'FLOW_ACTION' && flowSource"
        type="success"
        class="form-alert"
      >
        已匹配主流程 {{ flowSource.flowModelKey }}，发布对象版本 v{{ flowSource.publishedObjectVersion }}。
      </n-alert>
      <n-alert
        v-if="form.sourceType === 'FLOW_ACTION' && flowSource && !flowSource.submissionSupported"
        type="warning"
        class="form-alert"
      >
        “提交业务申请”不可用：{{ flowSource.submissionUnavailableReason || '当前对象暂不支持由平台自动创建申请记录' }}
      </n-alert>
      <n-alert
        v-if="form.sourceType === 'FLOW_ACTION' && flowSubmitOptionMissing"
        type="warning"
        class="form-alert"
      >
        <div class="dict-refresh-notice">
          <span>
            当前没有加载到“提交业务申请”流程动作。{{ flowOperationDictError || '可能仍在使用页面打开时缓存的旧字典。' }}
          </span>
          <n-button
            text
            type="warning"
            size="small"
            :loading="dictLoading"
            @click="reloadFlowOperationOptions(true)"
          >
            重新加载流程动作
          </n-button>
        </div>
      </n-alert>

      <CapabilityProcessSource v-if="form.sourceType === 'APPLICATION_PROCESS'" :upgrade="isUpgrade" @page="applicationProcess.selectPage" @retry="applicationProcess.load()" />
      <template v-else-if="form.sourceType === 'BUSINESS_ACTION'">
        <n-form-item label="业务动作" path="actionCode">
          <n-select
            v-model:value="form.actionCode"
            :options="actionOptions"
            :loading="detailLoading"
            :disabled="isUpgrade || !form.objectId"
            placeholder="请选择可开放的业务动作"
            filterable
            @update:value="handleActionChange"
          >
            <template #empty>
              <n-empty size="small" description="该对象暂无可发布动作" />
            </template>
          </n-select>
        </n-form-item>
        <n-alert
          v-if="businessActionNotice"
          :type="businessActionNotice.type"
          class="form-alert"
        >
          <div class="action-diagnostic">
            <strong>{{ businessActionNotice.title }}</strong>
            <span>{{ businessActionNotice.summary }}</span>
            <ul v-if="businessActionNotice.items.length">
              <li v-for="item in businessActionNotice.items" :key="item.actionCode">
                {{ item.unavailableReason }}
              </li>
            </ul>
            <span v-if="businessActionNotice.remaining > 0">
              还有 {{ businessActionNotice.remaining }} 个不可发布动作，可在业务对象设计器中查看并修正。
            </span>
            <div class="action-diagnostic-actions">
              <n-button
                v-if="recommendFlowSubmission"
                text
                type="primary"
                size="small"
                @click="switchToFlowSubmission"
              >
                改为“提交业务申请”
              </n-button>
              <n-button text type="warning" size="small" @click="openBusinessActionDesigner">
                打开业务对象设计器
              </n-button>
            </div>
          </div>
        </n-alert>
        <n-alert
          v-else-if="businessActionSource"
          type="success"
          class="form-alert"
        >
          已按业务对象发布版本 v{{ businessActionSource.publishedObjectVersion }} 校验执行步骤，当前动作均可发布。
        </n-alert>
        <n-form-item label="允许字段" path="allowedFields">
          <n-select
            v-model:value="form.allowedFields"
            :options="fieldOptions"
            :loading="detailLoading"
            :disabled="!form.objectId"
            placeholder="选择外部调用可以写入的字段"
            multiple
            filterable
            clearable
          >
            <template #empty>
              <n-empty size="small" description="该对象暂无可写业务字段" />
            </template>
          </n-select>
        </n-form-item>
        <n-form-item label="必填字段">
          <n-select
            v-model:value="form.requiredFields"
            :options="requiredFieldOptions"
            :disabled="form.allowedFields.length === 0"
            placeholder="可选，必须属于允许字段"
            multiple
            filterable
            clearable
          />
        </n-form-item>
      </template>

      <template v-else-if="form.sourceType === 'FLOW_ACTION'">
        <n-form-item label="流程动作" path="operation">
          <n-select
            v-model:value="form.operation"
            :options="flowOperationOptions"
            :loading="dictLoading"
            :disabled="isUpgrade || !form.objectId || detailLoading || !flowSource"
            placeholder="请选择流程动作"
            @update:value="handleOperationChange"
          >
            <template #empty>
              <n-empty size="small" description="流程动作字典尚未初始化" />
            </template>
          </n-select>
        </n-form-item>
        <template v-if="form.operation === 'SUBMIT'">
          <n-alert type="info" class="form-alert">
            外围系统只提交申请数据，Forge 会使用 Token 对应的真实用户创建业务记录并立即发起主流程，不需要先准备 recordId。
          </n-alert>
          <n-form-item label="允许输入字段" path="allowedFields">
            <n-select
              v-model:value="form.allowedFields"
              :options="flowSubmissionFieldOptions"
              :disabled="!flowSource?.submissionSupported"
              placeholder="选择外围系统可以填写的申请字段"
              multiple
              filterable
              clearable
            >
              <template #empty>
                <n-empty size="small" description="当前发布模型没有可开放的申请字段" />
              </template>
            </n-select>
            <template #feedback>
              字段类型、长度、字典和业务说明会自动写入接口文档；系统字段、用户、租户、单据状态和流程字段不会开放。
            </template>
          </n-form-item>
          <n-form-item label="必填字段">
            <n-select
              v-model:value="form.requiredFields"
              :options="flowRequiredFieldOptions"
              :disabled="form.allowedFields.length === 0"
              placeholder="业务模型必填项已自动锁定，可增加接口级必填项"
              multiple
              filterable
              clearable
            />
          </n-form-item>
        </template>
        <n-alert v-else type="info" class="form-alert">
          {{ form.operation === 'START'
            ? 'START 只适用于 Forge 中已经保存的业务记录，调用时必须传真实 recordId。'
            : '流程办理只能通过用户委托 Token 调用，办理人和组织从可信登录身份解析。' }}
        </n-alert>
      </template>

      <template v-else>
        <CapabilitySystemSource :services="systemServices" :options="systemServiceOptions" :loading="systemSourceLoading || draftLoading" :upgrade="isUpgrade" @service-change="handleSystemServiceChange" @form-change="handleSystemFormChange" @parameters-change="updateGeneratedCode" />
        <template v-if="systemKind === 'FLOW'">
          <n-form-item label="流程模型" path="systemModelId">
            <n-select
              v-model:value="form.systemModelId"
              :options="systemModelOptions"
              :disabled="isUpgrade || !selectedSystemService"
              placeholder="请选择已发布且启用的流程模型"
              filterable
              @update:value="updateGeneratedCode"
            >
              <template #empty>
                <n-empty size="small" description="暂无可开放的已发布流程模型" />
              </template>
            </n-select>
          </n-form-item>

          <n-form-item label="开放流程变量">
            <div class="variable-editor">
              <n-alert type="warning" :show-icon="true">
                流程变量会影响审批人和分支路由，默认不开放。只有外围系统确实需要传入的变量才应逐项添加。
              </n-alert>
              <div v-if="form.systemVariables.length" class="variable-list">
                <div
                  v-for="(variable, index) in form.systemVariables"
                  :key="variable.key"
                  class="variable-row"
                >
                  <n-input
                    v-model:value="variable.name"
                    placeholder="变量名"
                    maxlength="64"
                  />
                  <n-select
                    v-model:value="variable.type"
                    :options="systemVariableTypeOptions"
                    placeholder="类型"
                  />
                  <n-input
                    v-model:value="variable.description"
                    placeholder="业务含义和取值说明"
                    maxlength="200"
                  />
                  <n-checkbox v-model:checked="variable.required">
                    必填
                  </n-checkbox>
                  <n-button quaternary circle type="error" aria-label="删除变量" @click="removeSystemVariable(index)">
                    <template #icon>
                      <i class="i-material-symbols:delete-outline-rounded" />
                    </template>
                  </n-button>
                </div>
              </div>
              <n-button dashed block :disabled="form.systemVariables.length >= 50" @click="addSystemVariable">
                <template #icon>
                  <i class="i-material-symbols:add-rounded" />
                </template>
                添加允许外围传入的变量
              </n-button>
            </div>
          </n-form-item>
          <n-alert type="info" class="form-alert">
            外围请求不能传入模型、租户、用户、组织或发起人；这些信息由发布快照和用户委托身份固定。
          </n-alert>
        </template>
      </template>

      <n-collapse class="technical-options">
        <n-collapse-item title="接口标识与版本" name="technical">
          <n-form-item label="能力编码（自动生成）" path="capabilityCode">
            <n-input
              v-model:value="form.capabilityCode"
              placeholder="如 business.order.create"
              maxlength="128"
              show-count
              :disabled="isUpgrade"
            />
          </n-form-item>
          <n-form-item label="能力版本" path="version">
            <n-input v-model:value="form.version" placeholder="如 1.0.0" />
            <template v-if="isUpgrade" #feedback>
              必须高于当前版本 {{ capability.currentVersion }}，已为你建议下一补丁版本。
            </template>
          </n-form-item>
        </n-collapse-item>
      </n-collapse>
      <n-form-item label="能力描述">
        <n-input
          v-model:value="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-count
          placeholder="可选"
        />
      </n-form-item>
    </n-form>

    <section v-if="step === 3" class="publish-review">
      <h3>确认开放范围</h3>
      <p>发布后会生成能力版本。外部系统仍需要单独获得授权才能调用。</p>
      <dl>
        <dt>能力编码</dt><dd><code>{{ form.capabilityCode }}</code></dd>
        <dt>发布版本</dt><dd>{{ form.version }}</dd>
        <dt>业务来源</dt><dd>{{ reviewSource }}</dd>
        <dt>操作</dt><dd>{{ form.sourceType === 'APPLICATION_PROCESS' ? '发起应用业务流程' : form.sourceType === 'SYSTEM_SERVICE' ? selectedSystemService?.serviceName : form.actionCode || flowOperationOptions.find(item => item.value === form.operation)?.label }}</dd>
        <dt>输入范围</dt><dd>{{ form.sourceType === 'APPLICATION_PROCESS' ? '已保存的表单记录 ID（recordId）' : form.allowedFields.length ? `${form.allowedFields.length} 个业务字段` : '按来源契约校验' }}</dd>
      </dl>
      <n-space v-if="reviewFields.length" class="review-fields">
        <n-tag v-for="field in reviewFields" :key="field.field" size="small" :bordered="false">
          {{ field.label || field.field }}{{ form.requiredFields.includes(field.field) ? ' · 必填' : '' }}
        </n-tag>
      </n-space>
      <n-alert type="info">
        已发布版本保持不变。后续调整来源或字段，请发布新版本并检查接入系统的授权版本。
      </n-alert>
    </section>

    <template #footer>
      <n-space justify="end">
        <n-button :disabled="submitting" @click="emit('update:show', false)">
          取消
        </n-button>
        <n-button v-if="step > (isUpgrade ? 2 : 1)" :disabled="submitting" @click="step--">
          上一步
        </n-button>
        <n-button v-if="step === 2" type="primary" :loading="sourceLoading" :disabled="submitDisabled" @click="nextStep">
          检查并继续
        </n-button>
        <n-button
          v-if="step === 3"
          type="primary"
          :loading="submitting"
          :disabled="submitDisabled"
          @click="handleSubmit"
        >
          {{ submitText }}
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import CapabilityApplicationSource from './CapabilityApplicationSource.vue'
import CapabilityProcessSource from './CapabilityProcessSource.vue'
import CapabilitySystemSource from './CapabilitySystemSource.vue'
import { useCapabilityRegistration } from './useCapabilityRegistration'

const props = defineProps({
  initialContext: { type: Object, default: () => ({}) },
  show: {
    type: Boolean,
    default: false,
  },
  allowedTypes: {
    type: Array,
    default: () => [],
  },
  capability: {
    type: Object,
    default: null,
  },
})
const emit = defineEmits(['update:show', 'success'])

const {
  applicationProcess,
  step,
  scenario,
  advancedSource,
  chooseScenario,
  nextStep,
  selectApplicationPage,
  handleSystemFormChange,
  systemKind,
  reviewSource,
  reviewFields,
  dictLoading,
  formRef,
  objectLoading,
  detailLoading,
  systemSourceLoading,
  sourceLoading,
  draftLoading,
  submitting,
  sourceError,
  loadSystemServices,
  loadObjects,
  flowSourceError,
  flowSource,
  businessActionSource,
  systemServices,
  form,
  flowOperationOptions,
  flowOperationDictError,
  flowSubmitOptionMissing,
  flowSubmissionFieldOptions,
  flowRequiredFieldOptions,
  selectedSystemService,
  systemServiceOptions,
  systemModelOptions,
  systemVariableTypeOptions,
  isUpgrade,
  modalTitle,
  submitText,
  submitDisabled,
  rules,
  objectOptions,
  actionOptions,
  recommendFlowSubmission,
  businessActionNotice,
  fieldOptions,
  requiredFieldOptions,
  handleSourceTypeChange,
  handleObjectChange,
  handleActionChange,
  openBusinessActionDesigner,
  switchToFlowSubmission,
  reloadFlowOperationOptions,
  handleOperationChange,
  handleSystemServiceChange,
  addSystemVariable,
  removeSystemVariable,
  updateGeneratedCode,
  handleSubmit,
} = useCapabilityRegistration(props, emit)
</script>

<style scoped>
.source-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid var(--border-light);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 13px;
}
.review-fields {
  margin: 0 0 20px;
}
.registration-progress {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 20px;
  color: var(--text-tertiary);
  font-size: 13px;
}
.registration-progress .current {
  color: var(--primary-color);
  font-weight: 600;
}
.scenario-picker {
  display: grid;
  gap: 12px;
  padding: 8px 0 20px;
}
.scenario-intro h3,
.publish-review h3 {
  margin: 0 0 6px;
  font-size: 16px;
}
.scenario-intro p,
.publish-review p {
  margin: 0 0 12px;
  color: var(--text-tertiary);
  font-size: 13px;
}
.scenario-option {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg-primary);
  text-align: left;
  cursor: pointer;
  color: var(--text-primary);
  transition: border-color 150ms;
}
.scenario-option:hover,
.scenario-option:focus-visible {
  border-color: var(--primary-color);
  outline-color: var(--primary-color);
}
.scenario-option > i {
  font-size: 22px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}
.scenario-option span {
  display: grid;
  gap: 5px;
  flex: 1;
}
.scenario-option strong {
  font-size: 14px;
  font-weight: 600;
}
.scenario-option small {
  font-size: 12px;
  color: var(--text-tertiary);
}
.source-toggle {
  margin-bottom: 18px;
}
.technical-options {
  margin: 8px 0 20px;
}
.publish-review dl {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 14px;
  padding: 20px 0;
  margin: 0;
}
.publish-review dt {
  color: var(--text-tertiary);
}
.publish-review dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.form-alert {
  margin-bottom: 18px;
}

.service-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  line-height: 1.55;
}

.action-diagnostic {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  line-height: 1.55;
}

.action-diagnostic ul {
  display: grid;
  gap: 4px;
  margin: 0;
  padding-left: 18px;
}

.action-diagnostic-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.dict-refresh-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.variable-editor {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.variable-row {
  display: grid;
  grid-template-columns: minmax(120px, 0.9fr) 150px minmax(180px, 1.4fr) auto 34px;
  align-items: center;
  gap: 8px;
}

@media (max-width: 760px) {
  .variable-row {
    grid-template-columns: 1fr;
    padding: 12px;
    border: 1px solid var(--border-light);
  }
}

:global(.capability-register-modal) {
  width: min(960px, calc(100vw - 32px));
  max-height: calc(100dvh - 40px);
}
:global(.capability-register-modal > .n-card-content) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}
:global(.capability-register-modal > .n-card-header),
:global(.capability-register-modal > .n-card__footer) {
  flex-shrink: 0;
}
</style>
