<template>
  <section class="automation-designer">
    <header class="designer-head">
      <div>
        <h2>自动化动作</h2>
        <p>配置审批结果、按钮或触发器之后自动执行的业务处理。发起审批和审批办理不在这里配置。</p>
      </div>
      <NButton size="small" type="primary" secondary @click="addAutomationAction">
        新增自动化
      </NButton>
    </header>

    <div class="boundary-strip">
      <div class="boundary-item">
        <strong>发起审批</strong>
        <span>在“单据流程”和列表按钮里配置</span>
      </div>
      <div class="boundary-item">
        <strong>同意 / 驳回</strong>
        <span>在流程设计器节点中配置</span>
      </div>
      <div class="boundary-item active">
        <strong>审批后业务处理</strong>
        <span>在本页配置字段映射和执行动作</span>
      </div>
    </div>

    <div v-if="approvalEntryActions.length || pageInteractionActions.length" class="context-notices">
      <n-alert v-if="approvalEntryActions.length" type="info" :bordered="false" class="approval-entry-note">
        已识别到 {{ approvalEntryActions.length }} 个发起审批入口。这类入口由“单据流程”和列表按钮维护，本页不展示底层流程启动参数。
      </n-alert>

      <n-alert v-if="pageInteractionActions.length" type="info" :bordered="false" class="approval-entry-note">
        已隐藏 {{ pageInteractionActions.length }} 个页面操作（{{ pageInteractionActionNames }}）。它们只负责打开新增/编辑页面或执行前端交互，
        不是服务端自动化步骤，也不能直接作为开放能力执行；页面上的原有按钮不会受影响。
      </n-alert>
    </div>

    <n-empty v-if="!automationActions.length" description="当前还没有业务自动化动作" class="empty-state" />

    <div v-else class="automation-workbench">
      <aside class="automation-list">
        <div class="pane-title">
          <strong>业务自动化</strong>
          <span>{{ automationActions.length }}</span>
        </div>
        <div
          v-for="item in automationActions"
          :key="item.originalIndex"
          class="automation-list-item"
          :class="{ active: item.originalIndex === selectedActionIndex }"
          @click="selectedActionIndex = item.originalIndex"
        >
          <div class="automation-list-item__info">
            <strong>{{ item.action.actionName || '未命名自动化' }}</strong>
            <span>{{ actionSceneLabel(item.action) }}</span>
          </div>
          <NButton
            size="tiny"
            quaternary
            type="error"
            class="automation-list-item__delete"
            @click.stop="removeAction(item.originalIndex)"
          >
            删除
          </NButton>
        </div>
      </aside>

      <main v-if="selectedAction" class="automation-main">
        <section class="panel-section action-summary">
          <div class="section-title">
            <h3>自动化信息</h3>
            <n-switch
              :value="selectedAction.status !== 0"
              @update:value="patchSelectedAction({ status: $event ? 1 : 0 })"
            />
          </div>
          <NGrid :cols="3" :x-gap="12" :y-gap="8" responsive="screen">
            <NFormItemGi label="自动化名称">
              <NInput
                :value="selectedAction.actionName || ''"
                placeholder="例如：审批通过后更新库存"
                @update:value="patchSelectedAction({ actionName: $event })"
              />
            </NFormItemGi>
            <NFormItemGi label="执行场景">
              <NSelect
                :value="resolveActionScene(selectedAction)"
                :options="sceneOptions"
                @update:value="updateActionScene($event)"
              />
            </NFormItemGi>
            <NFormItemGi label="成功后">
              <NSelect
                :value="selectedAction.actionConfig?.successBehavior || 'refreshList'"
                :options="successBehaviorOptions"
                @update:value="patchActionConfig({ successBehavior: $event })"
              />
            </NFormItemGi>
          </NGrid>
          <NGrid v-if="resolveActionScene(selectedAction) === 'MANUAL'" :cols="2" :x-gap="12" :y-gap="8" responsive="screen">
            <NFormItemGi label="按钮位置">
              <NSelect
                :value="selectedManualActionPosition"
                :options="manualActionPositionOptions"
                @update:value="updateManualActionPosition"
              />
            </NFormItemGi>
            <NFormItemGi v-if="selectedManualActionPosition === 'CHILD_ROW'" label="目标明细关系">
              <NSelect
                filterable
                :options="childRelationOptions"
                :value="selectedAction.actionConfig?.relationKey || ''"
                placeholder="选择关系与级联中的明细"
                @update:value="updateChildActionRelation"
              />
            </NFormItemGi>
            <NFormItemGi label="按钮权限标识">
              <NInput
                :value="selectedAction.permissionKey || selectedAction.permissionCode || selectedAction.permission || ''"
                placeholder="例如：order:submit"
                @update:value="patchSelectedAction({ permissionKey: $event, permission: $event })"
              />
            </NFormItemGi>
            <NFormItemGi label="无权限时">
              <NSelect
                :value="selectedAction.permissionStrategy || 'hide'"
                :options="permissionStrategyOptions"
                @update:value="patchSelectedAction({ permissionStrategy: $event })"
              />
            </NFormItemGi>
          </NGrid>
          <n-alert
            v-if="resolveActionScene(selectedAction) === 'MANUAL' && selectedManualActionPosition === 'CHILD_ROW' && !childRelationOptions.length"
            type="warning"
            :bordered="false"
            class="relation-warning"
          >
            还没有可绑定的明细关系，请先到“关系与级联”配置一对多明细。
          </n-alert>
        </section>

        <section class="panel-section command-protocol-section">
          <div class="section-title">
            <div>
              <h3>执行协议</h3>
              <p class="section-hint">
                本地事务只覆盖 Forge 主数据源；流程、消息和领域动作需要编排模式。
              </p>
            </div>
            <NTag size="small" :type="isLocalTransaction ? 'success' : 'warning'">
              {{ isLocalTransaction ? '单事务提交' : '逐步编排' }}
            </NTag>
          </div>
          <NGrid :cols="2" :x-gap="12" :y-gap="8" responsive="screen">
            <NFormItemGi label="执行模式">
              <NSelect
                :value="resolvedExecutionMode"
                :options="executionModeOptions"
                @update:value="updateExecutionMode"
              />
            </NFormItemGi>
            <NFormItemGi label="事务范围">
              <n-alert type="info" :bordered="false" class="scope-note">
                {{ isLocalTransaction
                  ? '所有本地数据步骤在同一事务中执行，任一步失败会整体回滚。'
                  : '步骤可能触发外部副作用，只保证顺序和幂等，不承诺跨系统自动回滚。' }}
              </n-alert>
            </NFormItemGi>
          </NGrid>
          <n-alert v-if="isLocalTransaction && nonLocalStepCount" type="error" :bordered="false" class="protocol-warning">
            当前动作含 {{ nonLocalStepCount }} 个非本地步骤。请先在高级 JSON 中移除，或切换到编排模式后再保存/发布。
          </n-alert>

          <div class="schema-editor">
            <div class="subsection-head">
              <div>
                <strong>动作输入字段</strong>
                <span>支持文本、数字、金额、布尔、日期和下拉选项，运行时会自动生成表单。</span>
              </div>
              <NButton size="tiny" secondary @click="addInputSchemaField">
                添加输入字段
              </NButton>
            </div>
            <n-empty v-if="!inputSchemaRows.length" description="无输入字段（动作直接使用当前记录）" size="small" />
            <div v-for="(field, index) in inputSchemaRows" :key="`${field.name || 'field'}-${index}`" class="schema-row">
              <NInput
                :value="field.name || ''"
                placeholder="字段名，如 quantity"
                @update:value="patchInputSchemaField(index, { name: $event })"
              />
              <NInput
                :value="field.label || ''"
                placeholder="显示名称"
                @update:value="patchInputSchemaField(index, { label: $event })"
              />
              <NSelect
                :value="field.type || 'text'"
                :options="inputTypeOptions"
                @update:value="patchInputSchemaField(index, { type: $event })"
              />
              <n-switch
                :value="field.required === true"
                size="small"
                @update:value="patchInputSchemaField(index, { required: $event })"
              />
              <NInputNumber
                v-if="['number', 'integer', 'money'].includes(field.type)"
                :value="field.min ?? null"
                :show-button="false"
                placeholder="最小"
                @update:value="patchInputSchemaField(index, { min: $event })"
              />
              <NInputNumber
                v-if="['number', 'integer', 'money'].includes(field.type)"
                :value="field.max ?? null"
                :show-button="false"
                placeholder="最大"
                @update:value="patchInputSchemaField(index, { max: $event })"
              />
              <NInputNumber
                v-if="field.type === 'money'"
                :value="field.scale ?? 2"
                :min="0"
                :max="6"
                :precision="0"
                :show-button="false"
                placeholder="小数位"
                @update:value="patchInputSchemaField(index, { scale: $event })"
              />
              <NInputNumber
                v-if="field.type === 'text'"
                :value="field.maxLength ?? null"
                :min="1"
                :precision="0"
                :show-button="false"
                placeholder="最大长度"
                @update:value="patchInputSchemaField(index, { maxLength: $event })"
              />
              <NInput
                v-if="field.type === 'select'"
                :value="inputOptionsText(field)"
                placeholder="选项：名称=值，逗号分隔"
                @update:value="updateInputOptions(index, $event)"
              />
              <NButton size="tiny" quaternary type="error" @click="removeInputSchemaField(index)">
                删除
              </NButton>
            </div>
          </div>
        </section>

        <section v-if="isLocalTransaction" class="panel-section local-step-section">
          <div class="section-title">
            <div>
              <h3>本地事务步骤</h3>
              <p class="section-hint">
                步骤按顺序执行并共享同一事务，目标对象和字段只能从已发布模型中选择。
              </p>
            </div>
            <NDropdown :options="localStepMenuOptions" @select="addLocalStep">
              <NButton size="small" secondary class="add-step-select">
                添加本地步骤
              </NButton>
            </NDropdown>
          </div>
          <n-empty v-if="!localStepViews.length" description="还没有本地事务步骤" size="small" />
          <div v-else class="local-step-list">
            <article v-for="localStep in localStepViews" :key="localStep.key" class="local-step-card">
              <div class="local-step-card__head">
                <NSelect
                  :value="localStep.raw.stepType || ''"
                  :options="localStepTypeOptions"
                  class="step-type-select"
                  @update:value="updateLocalStepType(localStep, $event)"
                />
                <NInput
                  :value="localStep.raw.stepName || ''"
                  placeholder="步骤名称"
                  @update:value="patchStep(localStep, { stepName: $event })"
                />
                <NButton size="tiny" quaternary type="error" @click="removeStep(localStep)">
                  删除
                </NButton>
              </div>
              <NGrid :cols="2" :x-gap="12" :y-gap="8" responsive="screen">
                <NFormItemGi label="目标对象">
                  <NSelect
                    filterable
                    :options="targetConfigOptions"
                    :value="localStep.config.targetConfigKey || ''"
                    placeholder="选择业务对象"
                    @update:value="patchStepConfig(localStep, { targetConfigKey: $event }); loadTargetFields($event)"
                  />
                </NFormItemGi>
                <NFormItemGi v-if="stepTypeNeedsRecordId(localStep.raw.stepType)" label="目标记录 ID">
                  <NInput
                    :value="localStep.config.targetRecordIdField || ''"
                    placeholder="如 record.id 或 formData.targetId"
                    @update:value="patchStepConfig(localStep, { targetRecordIdField: $event })"
                  />
                </NFormItemGi>
              </NGrid>

              <div v-if="['CREATE_RECORD', 'UPDATE_FIELD'].includes(localStep.raw.stepType)" class="mapping-editor">
                <div class="subsection-head">
                  <strong>{{ localStep.raw.stepType === 'CREATE_RECORD' ? '创建字段' : '更新字段' }}</strong>
                  <NButton size="tiny" secondary @click="addFieldMapping(localStep)">
                    添加字段
                  </NButton>
                </div>
                <div v-for="(mapping, mappingIndex) in stepFieldMappings(localStep)" :key="`${localStep.key}-mapping-${mappingIndex}`" class="mapping-row">
                  <NSelect
                    filterable
                    :options="targetFieldOptions(localStep)"
                    :value="mapping.targetField || ''"
                    placeholder="目标字段"
                    @update:value="patchFieldMapping(localStep, mappingIndex, { targetField: $event })"
                  />
                  <NSelect
                    :options="sourceTypeOptions"
                    :value="mapping.sourceType || 'record'"
                    @update:value="patchFieldMapping(localStep, mappingIndex, { sourceType: $event })"
                  />
                  <NInput
                    v-if="mapping.sourceType !== 'static'"
                    :value="mapping.sourceField || ''"
                    :placeholder="mappingSourcePlaceholder(mapping.sourceType)"
                    @update:value="patchFieldMapping(localStep, mappingIndex, { sourceField: $event })"
                  />
                  <NInput
                    v-else
                    :value="mapping.value ?? ''"
                    placeholder="固定值"
                    @update:value="patchFieldMapping(localStep, mappingIndex, { value: $event })"
                  />
                  <NButton size="tiny" quaternary type="error" @click="removeFieldMapping(localStep, mappingIndex)">
                    删除
                  </NButton>
                </div>
                <n-empty v-if="!stepFieldMappings(localStep).length" description="尚未配置字段" size="small" />
              </div>

              <div v-else-if="localStep.raw.stepType === 'ADJUST_NUMBER'" class="mapping-editor">
                <div class="subsection-head">
                  <strong>数值调整字段</strong>
                  <NButton size="tiny" secondary @click="addNumberAdjustment(localStep)">
                    添加数值字段
                  </NButton>
                </div>
                <div v-for="(mapping, mappingIndex) in stepAdjustments(localStep)" :key="`${localStep.key}-adjustment-${mappingIndex}`" class="mapping-row adjustment-row">
                  <NSelect
                    filterable
                    :options="targetFieldOptions(localStep)"
                    :value="mapping.targetField || ''"
                    placeholder="数值目标字段"
                    @update:value="patchAdjustment(localStep, mappingIndex, { targetField: $event })"
                  />
                  <NSelect
                    :options="adjustmentOperatorOptions"
                    :value="mapping.operator || 'ADD'"
                    @update:value="patchAdjustment(localStep, mappingIndex, { operator: $event })"
                  />
                  <NInput
                    :value="mapping.sourceField || ''"
                    placeholder="动作输入字段名，如 quantity"
                    @update:value="patchAdjustment(localStep, mappingIndex, { sourceType: 'form', sourceField: $event })"
                  />
                  <NInputNumber
                    :value="mapping.min ?? null"
                    :show-button="false"
                    placeholder="下界"
                    @update:value="patchAdjustment(localStep, mappingIndex, { min: $event })"
                  />
                  <NInputNumber
                    :value="mapping.max ?? null"
                    :show-button="false"
                    placeholder="上界"
                    @update:value="patchAdjustment(localStep, mappingIndex, { max: $event })"
                  />
                  <NButton size="tiny" quaternary type="error" @click="removeNumberAdjustment(localStep, mappingIndex)">
                    删除
                  </NButton>
                </div>
                <n-empty v-if="!stepAdjustments(localStep).length" description="尚未配置调整字段" size="small" />
              </div>
              <div v-else-if="localStep.raw.stepType === 'TRANSITION_STATUS'" class="mapping-editor status-transition-editor">
                <div class="subsection-head">
                  <strong>状态迁移</strong>
                  <span class="section-hint">“从状态”会作为同一条更新语句的并发条件。</span>
                </div>
                <NGrid :cols="3" :x-gap="12" :y-gap="8" responsive="screen">
                  <NFormItemGi label="状态字段">
                    <NSelect
                      filterable
                      :options="targetFieldOptions(localStep)"
                      :value="localStep.config.statusField || ''"
                      placeholder="选择状态字段"
                      @update:value="patchStepConfig(localStep, { statusField: $event })"
                    />
                  </NFormItemGi>
                  <NFormItemGi label="从状态">
                    <NInput
                      :value="localStep.config.fromValue ?? ''"
                      placeholder="如 DRAFT"
                      @update:value="patchStepConfig(localStep, { fromValue: $event })"
                    />
                  </NFormItemGi>
                  <NFormItemGi label="到状态">
                    <NInput
                      :value="localStep.config.toValue ?? ''"
                      placeholder="如 SUBMITTED"
                      @update:value="patchStepConfig(localStep, { toValue: $event })"
                    />
                  </NFormItemGi>
                </NGrid>
              </div>
              <div v-else-if="localStep.raw.stepType === 'ASSERT_RECORD'" class="mapping-editor">
                <div class="subsection-head">
                  <strong>状态门禁条件</strong>
                  <NButton size="tiny" secondary @click="addExpectedFieldMapping(localStep)">
                    添加条件
                  </NButton>
                </div>
                <div v-for="(mapping, mappingIndex) in stepExpectedFieldMappings(localStep)" :key="`${localStep.key}-expected-${mappingIndex}`" class="mapping-row">
                  <NSelect
                    filterable
                    :options="targetFieldOptions(localStep)"
                    :value="mapping.targetField || ''"
                    placeholder="目标字段"
                    @update:value="patchExpectedFieldMapping(localStep, mappingIndex, { targetField: $event })"
                  />
                  <NInput
                    :value="mapping.value ?? ''"
                    placeholder="必须等于的状态/标记值"
                    @update:value="patchExpectedFieldMapping(localStep, mappingIndex, { value: $event })"
                  />
                  <NButton size="tiny" quaternary type="error" @click="removeExpectedFieldMapping(localStep, mappingIndex)">
                    删除
                  </NButton>
                </div>
                <n-empty v-if="!stepExpectedFieldMappings(localStep).length" description="未配置条件，仅校验记录存在和权限" size="small" />
              </div>
            </article>
          </div>
        </section>

        <section v-else class="panel-section">
          <div class="section-title">
            <h3>业务处理流程</h3>
            <div class="orchestration-step-actions">
              <NButton size="tiny" secondary @click="addDetailQuantityFlow">
                添加明细数量处理
              </NButton>
              <NButton size="tiny" type="primary" secondary @click="addCallApiStep">
                调用外部接口
              </NButton>
            </div>
          </div>

          <n-empty v-if="!rootSteps.length" description="还没有业务处理步骤" size="small" />

          <div v-else class="flow-stack">
            <article v-for="rootStep in rootSteps" :key="rootStep.key" class="flow-card">
              <template v-if="isInternalStepType(rootStep.raw, INTERNAL_STEP.FOREACH)">
                <div class="flow-card-head">
                  <span class="step-index">{{ rootStep.index + 1 }}</span>
                  <div>
                    <strong>逐行处理明细</strong>
                    <em>对选中的子表明细逐行执行业务动作</em>
                  </div>
                  <NButton size="tiny" quaternary type="error" @click="removeStep(rootStep)">
                    删除
                  </NButton>
                </div>
                <NGrid :cols="1" :x-gap="12" :y-gap="8" responsive="screen">
                  <NFormItemGi label="处理明细">
                    <NSelect
                      filterable
                      :options="collectionOptionsForStep(rootStep)"
                      :value="rootStep.config.collectionPath || ''"
                      placeholder="选择关系与级联中配置的明细"
                      @update:value="updateStepCollection(rootStep, $event)"
                    />
                  </NFormItemGi>
                </NGrid>
                <n-alert
                  v-if="!collectionPathOptions.length"
                  type="warning"
                  :bordered="false"
                  class="relation-warning"
                >
                  还没有可用于自动化的明细关系。请先到“关系与级联”配置主表和明细表的关系，自动化动作会直接复用那里的关系和字段。
                </n-alert>

                <div class="nested-actions">
                  <div class="nested-title">
                    <strong>每行执行</strong>
                    <NButton size="tiny" secondary @click="addQuantityStep(rootStep)">
                      添加数量处理
                    </NButton>
                  </div>
                  <BusinessQuantityStepCard
                    v-for="child in childBusinessSteps(rootStep)"
                    :key="child.key"
                    :step="child"
                    :field-options="fieldPathOptions(child)"
                    @patch-step="patchStep(child, $event)"
                    @patch-config="patchStepConfig(child, $event)"
                    @patch-param="updateStepParam(child, $event.key, $event.value)"
                    @patch-fallback="updateFallbackFields(child, $event.key, $event.value)"
                    @remove="removeStep(child)"
                  />
                </div>
              </template>

              <BusinessQuantityStepCard
                v-else-if="isQuantityStep(rootStep.raw)"
                :step="rootStep"
                :field-options="fieldPathOptions(rootStep)"
                @patch-step="patchStep(rootStep, $event)"
                @patch-config="patchStepConfig(rootStep, $event)"
                @patch-param="updateStepParam(rootStep, $event.key, $event.value)"
                @patch-fallback="updateFallbackFields(rootStep, $event.key, $event.value)"
                @remove="removeStep(rootStep)"
              />

              <section v-else-if="isInternalStepType(rootStep.raw, INTERNAL_STEP.CALL_API)" class="call-api-step-card">
                <div class="flow-card-head">
                  <span class="step-index">{{ rootStep.index + 1 }}</span>
                  <div>
                    <NInput
                      :value="rootStep.raw.stepName || '调用外部接口'"
                      placeholder="步骤名称"
                      @update:value="patchStep(rootStep, { stepName: $event })"
                    />
                    <em>调用已登记的 EXTERNAL_API，不在动作中填写 URL 或凭据</em>
                  </div>
                  <NButton size="tiny" quaternary type="error" @click="removeStep(rootStep)">
                    删除
                  </NButton>
                </div>
                <CallApiStepConfigPanel
                  :model-value="rootStep.config"
                  :record-field-options="callApiRecordFieldOptions"
                  :form-field-options="callApiFormFieldOptions"
                  @update:model-value="updateCallApiStepConfig(rootStep, $event)"
                />
              </section>

              <div v-else class="unsupported-step">
                <div>
                  <strong>{{ rootStep.raw.stepName || '高级步骤' }}</strong>
                  <span>该步骤暂未提供可视化表单，可在高级 JSON 中维护。</span>
                </div>
                <NButton size="tiny" quaternary type="error" @click="removeStep(rootStep)">
                  删除
                </NButton>
              </div>
            </article>
          </div>
        </section>

        <n-collapse class="advanced-json">
          <n-collapse-item title="高级 JSON（开发者兜底）" name="json">
            <NInput
              v-model:value="actionConfigText"
              type="textarea"
              :autosize="{ minRows: 8, maxRows: 18 }"
              placeholder="动作配置 JSON"
              @blur="applyActionConfigText"
            />
            <n-alert v-if="jsonError" type="error" :bordered="false" class="json-error">
              {{ jsonError }}
            </n-alert>
          </n-collapse-item>
        </n-collapse>
      </main>
    </div>
  </section>
</template>

<script setup>
import { NButton, NDropdown, NFormItemGi, NGrid, NInput, NInputNumber, NSelect, NTag } from 'naive-ui'
import { computed, defineComponent, h, ref, watch } from 'vue'
import { businessObjectDesigner, businessObjectList } from '@/api/business-app'
import {
  BUSINESS_ACTION_EXECUTION_MODE,
  canUseBusinessActionStep,
  createCallApiBusinessActionStep,
  createDefaultBusinessActionConfig,
  createLocalBusinessActionStep,
  LOCAL_TRANSACTION_STEP_TYPES,
  resolveBusinessActionExecutionMode,
} from './business-action-designer-protocol'
import CallApiStepConfigPanel from './CallApiStepConfigPanel.vue'

const props = defineProps({
  actions: {
    type: Array,
    default: () => [],
  },
  fields: {
    type: Array,
    default: () => [],
  },
  modelSchema: {
    type: Object,
    default: () => ({}),
  },
  relations: {
    type: Array,
    default: () => [],
  },
  suiteCode: {
    type: String,
    default: '',
  },
  objectCode: {
    type: String,
    default: '',
  },
  configKey: {
    type: String,
    default: '',
  },
  documentConfig: {
    type: Object,
    default: () => ({}),
  },
})
const emit = defineEmits(['update:actions', 'dirtyChange'])
const INTERNAL_STEP = {
  FOREACH: 'FOREACH',
  DOMAIN_ACTION: 'DOMAIN_ACTION',
  START_FLOW: 'START_FLOW',
  CALL_API: 'CALL_API',
}
const INTERNAL_ACTION = {
  QUANTITY: 'QUANTITY',
}
const EXECUTION_MODE = BUSINESS_ACTION_EXECUTION_MODE
const LOCAL_STEP_TYPES = LOCAL_TRANSACTION_STEP_TYPES

const selectedActionIndex = ref(0)
const actionConfigText = ref('')
const jsonError = ref('')
const businessObjects = ref([])
const targetFieldsMap = ref({})
const targetFieldLoadingMap = ref({})

const sceneOptions = [
  { label: '审批通过后', value: 'FLOW_APPROVED' },
  { label: '审批驳回后', value: 'FLOW_REJECTED' },
  { label: '手动点击按钮', value: 'MANUAL' },
  { label: '触发器调用', value: 'TRIGGER' },
]
const manualActionPositionOptions = [
  { label: '主记录详情按钮', value: 'DETAIL' },
  { label: '子表行按钮', value: 'CHILD_ROW' },
]
const successBehaviorOptions = [
  { label: '刷新列表', value: 'refreshList' },
  { label: '无操作', value: 'none' },
]
const permissionStrategyOptions = [
  { label: '隐藏按钮', value: 'hide' },
  { label: '禁用按钮', value: 'disable' },
]
const executionModeOptions = [
  { label: '本地事务（可整体回滚）', value: EXECUTION_MODE.LOCAL_TRANSACTION },
  { label: '编排模式（幂等 + 补偿）', value: EXECUTION_MODE.ORCHESTRATION },
]
const inputTypeOptions = [
  { label: '文本', value: 'text' },
  { label: '数字', value: 'number' },
  { label: '整数', value: 'integer' },
  { label: '金额（元输入，服务端存分）', value: 'money' },
  { label: '布尔', value: 'boolean' },
  { label: '日期', value: 'date' },
  { label: '日期时间', value: 'datetime' },
  { label: '下拉选项', value: 'select' },
]
const localStepTypeOptions = [
  { label: '创建记录', value: 'CREATE_RECORD' },
  { label: '更新字段', value: 'UPDATE_FIELD' },
  { label: '调整数值', value: 'ADJUST_NUMBER' },
  { label: '变更状态', value: 'TRANSITION_STATUS' },
  { label: '状态门禁', value: 'ASSERT_RECORD' },
]
const localStepMenuOptions = localStepTypeOptions.map(item => ({ label: item.label, key: item.value }))
const sourceTypeOptions = [
  { label: '当前记录', value: 'record' },
  { label: '父记录', value: 'parent' },
  { label: '动作输入', value: 'form' },
  { label: '页面路由参数', value: 'context' },
  { label: '系统身份', value: 'system' },
  { label: '固定值', value: 'static' },
]
const adjustmentOperatorOptions = [
  { label: '增加（ADD）', value: 'ADD' },
  { label: '扣减（SUBTRACT）', value: 'SUBTRACT' },
]
const quantityOperationOptions = [
  { label: '增加数量', value: 'INBOUND' },
  { label: '扣减数量', value: 'OUTBOUND' },
  { label: '锁定数量', value: 'LOCK' },
  { label: '释放锁定', value: 'RELEASE' },
  { label: '转移数量', value: 'TRANSFER' },
]
const paramLabels = {
  accountCode: '归属字段',
  itemCode: '对象字段',
  dimensionKey: '维度',
  quantity: '数量字段',
  sourceDetailId: '明细记录',
  remark: '备注',
  targetAccountCode: '目标归属字段',
  targetItemCode: '目标对象字段',
  targetDimensionKey: '目标维度',
}

const actionList = computed(() => Array.isArray(props.actions) ? props.actions : [])
const approvalEntryActions = computed(() => actionList.value.filter(action => containsInternalStartFlow(action)))
const automationActions = computed(() => actionList.value
  .map((action, originalIndex) => ({ action, originalIndex }))
  .filter(item => isAutomationAction(item.action)))
const pageInteractionActions = computed(() => actionList.value
  .filter(action => !containsInternalStartFlow(action) && !isAutomationAction(action)))
const pageInteractionActionNames = computed(() => pageInteractionActions.value
  .slice(0, 4)
  .map(action => action.actionName || action.actionCode || '未命名操作')
  .join('、') + (pageInteractionActions.value.length > 4 ? '等' : ''))
const selectedAction = computed(() => actionList.value[selectedActionIndex.value] || automationActions.value[0]?.action || null)
const rootSteps = computed(() => flattenRootSteps(selectedAction.value?.actionConfig || {}))
const resolvedExecutionMode = computed(() => resolveExecutionMode(selectedAction.value))
const isLocalTransaction = computed(() => resolvedExecutionMode.value === EXECUTION_MODE.LOCAL_TRANSACTION)
const localStepViews = computed(() => rootSteps.value.filter(step => LOCAL_STEP_TYPES.includes(String(step.raw.stepType || '').toUpperCase())))
const nonLocalStepCount = computed(() => flattenAllSteps(selectedAction.value?.actionConfig || {})
  .filter((step) => {
    const type = String(step.raw.stepType || '').toUpperCase()
    return type && !canUseBusinessActionStep(EXECUTION_MODE.LOCAL_TRANSACTION, type)
  })
  .length)
const inputSchemaRows = computed(() => Array.isArray(selectedAction.value?.actionConfig?.inputSchema)
  ? selectedAction.value.actionConfig.inputSchema
  : [])
const callApiRecordFieldOptions = computed(() => collectMainFields(props.fields, props.modelSchema)
  .map(toPageField)
  .filter(field => !isInactiveField(field))
  .map((field) => {
    const code = field.sourceField || field.field || field.fieldCode
    return code
      ? { label: `${businessFieldLabel(field)}（${code}）`, value: `record.main.${code}` }
      : null
  })
  .filter(Boolean))
const callApiFormFieldOptions = computed(() => [
  ...inputSchemaRows.value
    .filter(field => field?.name)
    .map(field => ({ label: `${field.label || field.name}（${field.name}）`, value: field.name })),
  ...callApiRecordFieldOptions.value,
])
const targetConfigOptions = computed(() => buildTargetConfigOptions())
const actionRelations = computed(() => buildActionRelations(props.modelSchema, props.relations))
const collectionPathOptions = computed(() => buildCollectionPathOptions(actionRelations.value))
const childRelationOptions = computed(() => {
  const detailRelations = actionRelations.value.filter(relation => isDetailRelation(relation))
  const options = detailRelations.map((relation) => {
    const isKeyName = relation.relationName && relation.relationName === relation.collectionKey
    return {
      label: (!isKeyName && relation.relationName)
        || relation.detailTabTitle
        || relation.targetObjectName
        || relation.modelName
        || relation.relationName
        || relation.collectionKey,
      value: relation.collectionKey,
    }
  })
  const currentValue = String(selectedAction.value?.actionConfig?.relationKey || '').trim()
  if (currentValue && !options.some(opt => opt.value === currentValue)) {
    const matched = detailRelations[0]
    const fallbackLabel = matched
      ? (matched.relationName || matched.targetObjectName || matched.modelName || currentValue)
      : currentValue
    options.push({ label: fallbackLabel, value: currentValue })
  }
  return options
})
const selectedManualActionPosition = computed(() => {
  const position = String(selectedAction.value?.actionPosition || 'DETAIL')
    .replace('-', '_')
    .toUpperCase()
  return position === 'CHILD_ROW' ? 'CHILD_ROW' : 'DETAIL'
})

watch(() => props.suiteCode, () => {
  loadBusinessObjects()
}, { immediate: true })

watch([() => props.relations, () => props.modelSchema, businessObjects], () => {
  preloadRelationFields()
}, { immediate: true, deep: true })

watch(automationActions, (items) => {
  if (!items.length) {
    selectedActionIndex.value = 0
    return
  }
  if (!items.some(item => item.originalIndex === selectedActionIndex.value))
    selectedActionIndex.value = items[0].originalIndex
}, { immediate: true })

watch(selectedAction, (action) => {
  actionConfigText.value = stringifyJson(action?.actionConfig || {})
  jsonError.value = ''
}, { immediate: true })

const BusinessQuantityStepCard = defineComponent({
  name: 'BusinessQuantityStepCard',
  props: {
    step: {
      type: Object,
      required: true,
    },
    fieldOptions: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['patchStep', 'patchConfig', 'patchParam', 'patchFallback', 'remove'],
  setup(cardProps, { emit: cardEmit }) {
    const paramValue = key => cardProps.step.config?.params?.[key] ?? ''
    const fieldSelect = (key, placeholder = '选择字段') => h(NSelect, {
      'filterable': true,
      'options': mergeSelectedFieldOptions(cardProps.fieldOptions, [unwrapExpression(paramValue(key))]),
      'value': unwrapExpression(paramValue(key)),
      placeholder,
      'onUpdate:value': value => cardEmit('patchParam', { key, value: wrapExpression(value) }),
    })
    const staticInput = (key, placeholder = '固定值') => h(NInput, {
      'value': stringValue(paramValue(key)),
      placeholder,
      'onUpdate:value': value => cardEmit('patchParam', { key, value }),
    })
    const fallbackSelect = key => h(NSelect, {
      'multiple': true,
      'filterable': true,
      'clearable': true,
      'options': mergeSelectedFieldOptions(cardProps.fieldOptions, normalizeStringList(cardProps.step.config?.[`${key}FallbackFields`])),
      'value': normalizeStringList(cardProps.step.config?.[`${key}FallbackFields`]),
      'placeholder': '主字段为空时按顺序尝试其他字段',
      'onUpdate:value': value => cardEmit('patchFallback', { key, value }),
    })
    return () => h('div', { class: 'quantity-card' }, [
      h('div', { class: 'quantity-card-head' }, [
        h('div', null, [
          h('strong', null, cardProps.step.raw.stepName || '数量处理'),
          h('span', null, '更新数量台账或库存余额'),
        ]),
        h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => cardEmit('remove') }, { default: () => '删除' }),
      ]),
      h(NGrid, { cols: 3, xGap: 12, yGap: 8, responsive: 'screen' }, {
        default: () => [
          h(NFormItemGi, { label: '处理方式' }, {
            default: () => h(NSelect, {
              'options': quantityOperationOptions,
              'value': cardProps.step.config.operationType || cardProps.step.config.operation || 'INBOUND',
              'onUpdate:value': value => cardEmit('patchConfig', { operationType: value }),
            }),
          }),
          h(NFormItemGi, { label: paramLabels.accountCode }, { default: () => fieldSelect('accountCode') }),
          h(NFormItemGi, { label: paramLabels.quantity }, { default: () => fieldSelect('quantity') }),
          h(NFormItemGi, { label: paramLabels.itemCode }, { default: () => fieldSelect('itemCode') }),
          h(NFormItemGi, { label: '备用识别字段' }, { default: () => fallbackSelect('itemCode') }),
          h(NFormItemGi, { label: paramLabels.sourceDetailId }, { default: () => fieldSelect('sourceDetailId') }),
          h(NFormItemGi, { label: paramLabels.dimensionKey }, { default: () => staticInput('dimensionKey', '留空表示默认维度') }),
          h(NFormItemGi, { label: paramLabels.remark, span: 2 }, { default: () => staticInput('remark', '备注') }),
        ],
      }),
    ])
  },
})

function addAutomationAction() {
  const actions = cloneValue(actionList.value)
  const index = actions.length + 1
  actions.push({
    actionCode: `automation_${Date.now()}`,
    actionName: `自动化 ${index}`,
    actionPosition: 'DETAIL',
    actionType: 'COMMAND',
    status: 1,
    sortOrder: index * 10,
    actionConfig: createDefaultBusinessActionConfig({
      triggerScene: 'FLOW_APPROVED',
      successBehavior: 'refreshList',
    }),
  })
  emitActions(actions)
  selectedActionIndex.value = actions.length - 1
}

function resolveExecutionMode(action = {}) {
  return resolveBusinessActionExecutionMode(action.actionConfig || {})
}

function updateExecutionMode(mode) {
  const normalized = String(mode || '').toUpperCase()
  if (normalized === EXECUTION_MODE.LOCAL_TRANSACTION && nonLocalStepCount.value) {
    window.$message?.warning('当前动作包含流程、消息或领域步骤，请先移除这些步骤后再切换本地事务')
    return
  }
  patchActionConfig({ executionMode: normalized === EXECUTION_MODE.ORCHESTRATION
    ? EXECUTION_MODE.ORCHESTRATION
    : EXECUTION_MODE.LOCAL_TRANSACTION })
}

function addInputSchemaField() {
  const rows = cloneValue(inputSchemaRows.value)
  rows.push({ name: `input_${rows.length + 1}`, label: `输入字段 ${rows.length + 1}`, type: 'text', required: false })
  patchActionConfig({ inputSchema: rows })
}

function patchInputSchemaField(index, patch = {}) {
  const rows = cloneValue(inputSchemaRows.value)
  if (!rows[index])
    return
  rows[index] = { ...rows[index], ...patch }
  if (rows[index].type === 'select' && !Array.isArray(rows[index].options))
    rows[index].options = []
  patchActionConfig({ inputSchema: rows })
}

function removeInputSchemaField(index) {
  const rows = cloneValue(inputSchemaRows.value)
  rows.splice(index, 1)
  patchActionConfig({ inputSchema: rows })
}

function inputOptionsText(field = {}) {
  return (Array.isArray(field.options) ? field.options : [])
    .map(option => `${option?.label ?? option?.value ?? ''}=${option?.value ?? option?.label ?? ''}`)
    .join(', ')
}

function updateInputOptions(index, value) {
  const options = String(value || '').split(',').map(item => item.trim()).filter(Boolean).map((item) => {
    const [label, ...valueParts] = item.split('=')
    const optionValue = valueParts.length ? valueParts.join('=').trim() : label
    return { label: label.trim(), value: optionValue }
  })
  patchInputSchemaField(index, { options })
}

function addLocalStep(stepType = 'CREATE_RECORD') {
  const type = LOCAL_STEP_TYPES.includes(String(stepType).toUpperCase()) ? String(stepType).toUpperCase() : 'CREATE_RECORD'
  const actions = cloneValue(actionList.value)
  const action = actions[selectedActionIndex.value]
  if (!action)
    return
  const config = ensureActionConfig(action)
  if (!Array.isArray(config.steps))
    config.steps = []
  const step = createLocalStep(type, config.steps.length + 1)
  step.stepConfig.targetConfigKey = props.configKey || props.modelSchema?.configKey || props.objectCode || ''
  config.steps.push(step)
  config.executionMode = EXECUTION_MODE.LOCAL_TRANSACTION
  emitActions(actions)
}

function createLocalStep(stepType, index) {
  return createLocalBusinessActionStep(stepType, index)
}

function updateLocalStepType(step, stepType) {
  const nextType = LOCAL_STEP_TYPES.includes(String(stepType || '').toUpperCase()) ? String(stepType).toUpperCase() : 'CREATE_RECORD'
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, step)
  if (!cloned)
    return
  const previousConfig = ensureStepConfig(cloned)
  const nextConfig = {
    targetConfigKey: previousConfig.targetConfigKey || '',
    rollbackOnFailure: true,
  }
  if (nextType !== 'CREATE_RECORD')
    nextConfig.targetRecordIdField = previousConfig.targetRecordIdField || 'record.id'
  if (nextType === 'CREATE_RECORD' || nextType === 'UPDATE_FIELD')
    nextConfig.fieldMappings = Array.isArray(previousConfig.fieldMappings) ? previousConfig.fieldMappings : []
  if (nextType === 'ADJUST_NUMBER')
    nextConfig.adjustments = Array.isArray(previousConfig.adjustments) ? previousConfig.adjustments : []
  if (nextType === 'TRANSITION_STATUS') {
    nextConfig.statusField = previousConfig.statusField || ''
    nextConfig.fromValue = previousConfig.fromValue ?? ''
    nextConfig.toValue = previousConfig.toValue ?? ''
  }
  if (nextType === 'ASSERT_RECORD') {
    nextConfig.expectedFieldMappings = Array.isArray(previousConfig.expectedFieldMappings)
      ? previousConfig.expectedFieldMappings
      : []
  }
  cloned.stepType = nextType
  cloned.stepName = localStepTypeOptions.find(item => item.value === nextType)?.label || nextType
  cloned.stepConfig = nextConfig
  emitActions(actions)
}

function stepTypeNeedsRecordId(stepType) {
  return String(stepType || '').toUpperCase() !== 'CREATE_RECORD'
}

function mappingSourcePlaceholder(sourceType) {
  const placeholders = {
    form: '动作输入字段名，如 quantity',
    context: '路由参数，如 routeQuery.scene',
    system: '系统字段，如 userId',
    record: '当前记录字段，如 amount',
    parent: '父记录字段，如 status',
  }
  return placeholders[sourceType] || placeholders.record
}

function stepFieldMappings(step) {
  return Array.isArray(step.config?.fieldMappings) ? step.config.fieldMappings : []
}

function addFieldMapping(step) {
  const mappings = [...stepFieldMappings(step), { targetField: '', sourceType: 'form', sourceField: '' }]
  patchStepConfig(step, { fieldMappings: mappings })
}

function patchFieldMapping(step, index, patch = {}) {
  const mappings = cloneValue(stepFieldMappings(step))
  if (!mappings[index])
    return
  mappings[index] = { ...mappings[index], ...patch }
  if (patch.sourceType === 'static') {
    delete mappings[index].sourceField
  }
  else if (patch.sourceType) {
    delete mappings[index].value
    delete mappings[index].staticValue
  }
  patchStepConfig(step, { fieldMappings: mappings })
}

function removeFieldMapping(step, index) {
  const mappings = cloneValue(stepFieldMappings(step))
  mappings.splice(index, 1)
  patchStepConfig(step, { fieldMappings: mappings })
}

function stepExpectedFieldMappings(step) {
  return Array.isArray(step.config?.expectedFieldMappings) ? step.config.expectedFieldMappings : []
}

function addExpectedFieldMapping(step) {
  patchStepConfig(step, {
    expectedFieldMappings: [
      ...stepExpectedFieldMappings(step),
      { targetField: '', value: '' },
    ],
  })
}

function patchExpectedFieldMapping(step, index, patch = {}) {
  const mappings = cloneValue(stepExpectedFieldMappings(step))
  if (!mappings[index])
    return
  mappings[index] = { ...mappings[index], ...patch }
  patchStepConfig(step, { expectedFieldMappings: mappings })
}

function removeExpectedFieldMapping(step, index) {
  patchStepConfig(step, {
    expectedFieldMappings: stepExpectedFieldMappings(step)
      .filter((_item, itemIndex) => itemIndex !== index),
  })
}

function stepAdjustments(step) {
  return Array.isArray(step.config?.adjustments) ? step.config.adjustments : []
}

function addNumberAdjustment(step) {
  patchStepConfig(step, {
    adjustments: [...stepAdjustments(step), { targetField: '', sourceType: 'form', sourceField: '', operator: 'ADD' }],
  })
}

function patchAdjustment(step, index, patch = {}) {
  const adjustments = cloneValue(stepAdjustments(step))
  if (!adjustments[index])
    return
  adjustments[index] = { ...adjustments[index], ...patch }
  patchStepConfig(step, { adjustments })
}

function removeNumberAdjustment(step, index) {
  const adjustments = cloneValue(stepAdjustments(step))
  adjustments.splice(index, 1)
  patchStepConfig(step, { adjustments })
}

function buildTargetConfigOptions() {
  const options = []
  const seen = new Set()
  const add = (value, label) => {
    const code = String(value || '').trim()
    if (!code || seen.has(code))
      return
    seen.add(code)
    options.push({ label: `${label || code}（${code}）`, value: code })
  }
  add(props.configKey || props.modelSchema?.configKey || props.objectCode, props.modelSchema?.objectName || props.modelSchema?.object?.name || '当前对象')
  actionRelations.value.forEach(relation => add(
    relation.targetConfigKey || relation.targetObjectCode || relation.objectCode,
    relation.relationName || relation.modelName || '关联对象',
  ))
  businessObjects.value.forEach(item => add(
    item.configKey || item.objectCode,
    item.objectName || item.name || item.objectCode,
  ))
  return options
}

function targetFieldOptions(step = {}) {
  const targetCode = String(step.config?.targetConfigKey || '').trim()
  const currentCode = String(props.configKey || props.modelSchema?.configKey || props.objectCode || props.modelSchema?.objectCode || '').trim()
  const fields = targetCode && targetCode !== currentCode
    ? (targetFieldsMap.value[targetCode] || [])
    : collectMainFields(props.fields, props.modelSchema).map(toPageField)
  const options = fields.filter(field => !isInactiveField(field)).map(field => ({
    label: businessFieldLabel(field),
    value: field.sourceField || field.field || field.fieldCode,
  })).filter(item => item.value)
  const selected = [
    ...stepFieldMappings(step).map(item => item.targetField),
    ...stepAdjustments(step).map(item => item.targetField),
  ]
  return mergeSelectedFieldOptions(options, selected)
}

function patchSelectedAction(patch = {}) {
  const actions = cloneValue(actionList.value)
  if (!actions[selectedActionIndex.value])
    return
  actions[selectedActionIndex.value] = {
    ...actions[selectedActionIndex.value],
    ...patch,
  }
  emitActions(actions)
}

function patchActionConfig(patch = {}) {
  const actions = cloneValue(actionList.value)
  const action = actions[selectedActionIndex.value]
  if (!action)
    return
  action.actionConfig = {
    ...(action.actionConfig || {}),
    ...patch,
  }
  emitActions(actions)
}

function updateActionScene(scene) {
  const actions = cloneValue(actionList.value)
  const action = actions[selectedActionIndex.value]
  if (!action)
    return
  const config = ensureActionConfig(action)
  config.triggerScene = scene
  if (scene !== 'MANUAL' && String(action.actionPosition || '').toUpperCase() === 'CHILD_ROW') {
    action.actionPosition = 'DETAIL'
    delete config.relationKey
  }
  emitActions(actions)
}

function updateManualActionPosition(position) {
  const actions = cloneValue(actionList.value)
  const action = actions[selectedActionIndex.value]
  if (!action)
    return
  const config = ensureActionConfig(action)
  action.actionPosition = position === 'CHILD_ROW' ? 'CHILD_ROW' : 'DETAIL'
  config.triggerScene = 'MANUAL'
  if (action.actionPosition === 'CHILD_ROW')
    config.relationKey = config.relationKey || childRelationOptions.value[0]?.value || ''
  else
    delete config.relationKey
  emitActions(actions)
}

function updateChildActionRelation(relationKey) {
  patchActionConfig({ relationKey: relationKey || '' })
}

function addDetailQuantityFlow() {
  const actions = cloneValue(actionList.value)
  const action = actions[selectedActionIndex.value]
  if (!action)
    return
  const config = ensureActionConfig(action)
  if (!Array.isArray(config.steps))
    config.steps = []
  const collectionPath = collectionPathOptions.value[0]?.value || ''
  config.steps.push({
    stepCode: `detail_loop_${Date.now()}`,
    stepName: '逐行处理明细',
    stepType: INTERNAL_STEP.FOREACH,
    rollbackOnFailure: true,
    stepConfig: {
      collectionPath,
      itemAlias: 'item',
      indexAlias: 'index',
      steps: [createQuantityStep()],
    },
  })
  emitActions(actions)
}

function addCallApiStep() {
  const actions = cloneValue(actionList.value)
  const action = actions[selectedActionIndex.value]
  if (!action)
    return
  const config = ensureActionConfig(action)
  if (!Array.isArray(config.steps))
    config.steps = []
  config.steps.push(createCallApiBusinessActionStep(config.steps.length + 1))
  config.executionMode = EXECUTION_MODE.ORCHESTRATION
  emitActions(actions)
}

function updateCallApiStepConfig(step, value = {}) {
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, step)
  if (!cloned)
    return
  const nextConfig = { ...(value || {}) }
  const failureStrategy = String(nextConfig.failureStrategy || 'THROW').toUpperCase()
  cloned.stepConfig = nextConfig
  cloned.rollbackOnFailure = failureStrategy !== 'LOG_AND_CONTINUE'
  ensureActionConfig(actions[selectedActionIndex.value]).executionMode = EXECUTION_MODE.ORCHESTRATION
  emitActions(actions)
}

function addQuantityStep(parentStep) {
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, parentStep)
  if (!cloned)
    return
  const config = ensureStepConfig(cloned)
  if (!Array.isArray(config.steps))
    config.steps = []
  config.steps.push(createQuantityStep())
  emitActions(actions)
}

function patchStep(step, patch = {}) {
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, step)
  if (!cloned)
    return
  Object.assign(cloned, patch)
  emitActions(actions)
}

function patchStepConfig(step, patch = {}) {
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, step)
  if (!cloned)
    return
  Object.assign(ensureStepConfig(cloned), patch)
  emitActions(actions)
}

function updateStepCollection(step, collectionPath) {
  const relation = relationByCollectionPath(collectionPath)
  patchStepConfig(step, {
    collectionPath,
    itemAlias: step.config?.itemAlias || 'item',
    indexAlias: step.config?.indexAlias || 'index',
    relationKey: relation?.collectionKey || '',
    relationName: relation?.relationName || relation?.modelName || '',
    targetObjectCode: relation?.targetObjectCode || '',
  })
}

function updateStepParam(step, key, value) {
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, step)
  if (!cloned)
    return
  const params = ensureParams(ensureStepConfig(cloned))
  params[key] = value
  emitActions(actions)
}

function updateFallbackFields(step, key, value) {
  const actions = cloneValue(actionList.value)
  const cloned = resolveStep(actions, step)
  if (!cloned)
    return
  ensureStepConfig(cloned)[`${key}FallbackFields`] = normalizeStringList(value)
  emitActions(actions)
}

function removeStep(step) {
  const actions = cloneValue(actionList.value)
  const parentSteps = getPathValue(actions[selectedActionIndex.value]?.actionConfig, step.parentPath)
  if (!Array.isArray(parentSteps))
    return
  parentSteps.splice(step.index, 1)
  emitActions(actions)
}

function removeAction(originalIndex) {
  const actions = cloneValue(actionList.value)
  if (originalIndex < 0 || originalIndex >= actions.length)
    return
  actions.splice(originalIndex, 1)
  emitActions(actions)
  if (selectedActionIndex.value >= actions.length)
    selectedActionIndex.value = Math.max(0, actions.length - 1)
  else if (selectedActionIndex.value > originalIndex)
    selectedActionIndex.value--
}

function applyActionConfigText() {
  jsonError.value = ''
  let parsed
  try {
    parsed = actionConfigText.value?.trim() ? JSON.parse(actionConfigText.value) : {}
  }
  catch (error) {
    jsonError.value = error?.message || 'JSON 格式不正确'
    return
  }
  patchSelectedAction({
    actionConfig: parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {},
  })
}

function emitActions(actions) {
  emit('update:actions', actions)
  emit('dirtyChange', true)
}

function containsInternalStartFlow(action = {}) {
  return normalizeActionType(action) === INTERNAL_STEP.START_FLOW
    || flattenAllSteps(action.actionConfig || {}).some(step => isInternalStepType(step.raw, INTERNAL_STEP.START_FLOW))
}

function isAutomationAction(action = {}) {
  if (containsInternalStartFlow(action))
    return false
  const type = normalizeActionType(action)
  return type === 'COMMAND'
    || type === 'TRIGGER'
    || hasConfiguredSteps(action.actionConfig)
}

function hasConfiguredSteps(actionConfig = {}) {
  return (Array.isArray(actionConfig?.steps) && actionConfig.steps.length > 0)
    || (Array.isArray(actionConfig?.stepList) && actionConfig.stepList.length > 0)
}

function normalizeActionType(action = {}) {
  return String(action.actionType || '')
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/-/g, '_')
    .trim()
    .toUpperCase()
}

function flattenRootSteps(actionConfig = {}) {
  const steps = Array.isArray(actionConfig.steps) ? actionConfig.steps : []
  return steps.map((step, index) => buildStepVM(step, ['steps', index], ['steps'], index, []))
}

function childBusinessSteps(step) {
  const children = Array.isArray(step.config.steps) ? step.config.steps : []
  const childParentPath = [...step.configPath, 'steps']
  const aliases = [
    ...step.aliases,
    {
      alias: step.config.itemAlias || 'item',
      collectionPath: step.config.collectionPath || '',
    },
  ]
  return children.map((child, index) => buildStepVM(child, [...childParentPath, index], childParentPath, index, aliases))
}

function flattenAllSteps(actionConfig = {}) {
  const result = []
  function visit(steps, path, parentPath, aliases) {
    if (!Array.isArray(steps))
      return
    steps.forEach((step, index) => {
      const vm = buildStepVM(step, [...path, index], parentPath.length ? parentPath : path, index, aliases)
      result.push(vm)
      visit(vm.config.steps, [...vm.configPath, 'steps'], [...vm.configPath, 'steps'], vm.aliases)
    })
  }
  visit(actionConfig.steps, ['steps'], ['steps'], [])
  return result
}

function buildStepVM(raw, path, parentPath, index, aliases) {
  const config = raw?.stepConfig && typeof raw.stepConfig === 'object' ? raw.stepConfig : raw || {}
  const configPath = raw?.stepConfig && typeof raw.stepConfig === 'object' ? [...path, 'stepConfig'] : path
  return {
    raw: raw || {},
    index,
    path,
    parentPath,
    config,
    configPath,
    key: path.join('.'),
    aliases,
  }
}

function createQuantityStep() {
  return {
    stepCode: `quantity_${Date.now()}`,
    stepName: '数量处理',
    stepType: INTERNAL_STEP.DOMAIN_ACTION,
    rollbackOnFailure: true,
    stepConfig: {
      actionType: INTERNAL_ACTION.QUANTITY,
      operationType: 'INBOUND',
      params: {
        accountCode: '',
        itemCode: '',
        quantity: '',
        sourceDetailId: '',
        dimensionKey: '',
        remark: '',
      },
    },
  }
}

function isQuantityStep(step) {
  const config = step?.config || step?.stepConfig || {}
  return String(config.actionType || '').toUpperCase() === INTERNAL_ACTION.QUANTITY
}

function isInternalStepType(step, type) {
  return String(step?.stepType || '').toUpperCase() === type
}

function resolveActionScene(action = {}) {
  if (action.actionConfig?.triggerScene)
    return action.actionConfig.triggerScene
  const code = action.actionCode || action.key
  const callbackMap = collectCallbackActionMap(props.documentConfig)
  return callbackMap.get(code) || 'MANUAL'
}

function actionSceneLabel(action = {}) {
  const value = resolveActionScene(action)
  return sceneOptions.find(item => item.value === value)?.label || '业务自动化'
}

function collectCallbackActionMap(documentConfig = {}) {
  const result = new Map()
  const callbackActions = documentConfig.callbackActions
    || documentConfig.mainFlowSummary?.callbackActions
    || documentConfig.mainFlow?.callbackActions
    || documentConfig.options?.callbackActions
    || {}
  Object.entries(callbackActions).forEach(([key, value]) => {
    if (!value)
      return
    const normalized = String(key).toUpperCase()
    if (normalized.includes('APPROVED') || normalized === 'APPROVED')
      result.set(value, 'FLOW_APPROVED')
    if (normalized.includes('REJECTED') || normalized === 'REJECTED')
      result.set(value, 'FLOW_REJECTED')
  })
  if (callbackActions.approvedActionCode)
    result.set(callbackActions.approvedActionCode, 'FLOW_APPROVED')
  if (callbackActions.rejectedActionCode)
    result.set(callbackActions.rejectedActionCode, 'FLOW_REJECTED')
  return result
}

async function loadBusinessObjects() {
  try {
    const res = await businessObjectList({
      suiteCode: props.suiteCode || undefined,
    })
    businessObjects.value = Array.isArray(res.data) ? res.data : []
    await preloadRelationFields()
  }
  catch {
    businessObjects.value = []
  }
}

async function preloadRelationFields() {
  const objectCodes = Array.from(new Set(actionRelations.value
    .map(relation => relation.targetObjectCode)
    .filter(Boolean)))
  await Promise.all(objectCodes.map(objectCode => loadTargetFields(objectCode)))
}

async function loadTargetFields(objectCode) {
  const code = String(objectCode || '').trim()
  if (!code || targetFieldsMap.value[code] || targetFieldLoadingMap.value[code])
    return
  targetFieldLoadingMap.value = {
    ...targetFieldLoadingMap.value,
    [code]: true,
  }
  try {
    let targetObject = businessObjects.value.find(item => item.configKey === code || item.objectCode === code)
    if (!targetObject?.id) {
      const byConfig = await businessObjectList({ configKey: code })
      targetObject = (byConfig.data || [])[0]
    }
    if (!targetObject?.id) {
      const byObject = await businessObjectList({ objectCode: code })
      targetObject = (byObject.data || [])[0]
    }
    if (!targetObject?.id) {
      targetFieldsMap.value = {
        ...targetFieldsMap.value,
        [code]: [],
      }
      return
    }
    const res = await businessObjectDesigner(targetObject.id)
    const fields = res.data?.fields || res.data?.modelSchema?.fields || []
    targetFieldsMap.value = {
      ...targetFieldsMap.value,
      [code]: fields.map(toPageField),
    }
  }
  catch {
    targetFieldsMap.value = {
      ...targetFieldsMap.value,
      [code]: [],
    }
  }
  finally {
    targetFieldLoadingMap.value = {
      ...targetFieldLoadingMap.value,
      [code]: false,
    }
  }
}

function buildCollectionPathOptions(relations = []) {
  return relations
    .filter(relation => isDetailRelation(relation))
    .map((child) => {
      const key = child.collectionKey || child.key || child.modelCode || child.tableName || child.relationName
      const value = `record.children.${key}`
      const isKeyName = child.relationName && child.relationName === key
      return {
        label: (!isKeyName && child.relationName)
          || child.detailTabTitle
          || child.targetObjectName
          || child.modelName
          || child.label
          || '明细关系',
        value,
      }
    })
}

function collectionOptionsForStep(step = {}) {
  const options = [...collectionPathOptions.value]
  const current = String(step.config?.collectionPath || '').trim()
  if (current && !options.some(item => item.value === current)) {
    options.unshift({
      label: resolveCollectionPathLabel(current),
      value: current,
    })
  }
  return options
}

function resolveCollectionPathLabel(collectionPath = '') {
  const relation = relationByCollectionPath(collectionPath)
  if (relation)
    return relation.relationName || relation.detailTabTitle || relation.targetObjectName || relation.modelName || '明细关系'
  return '未识别明细关系（请在关系与级联中维护）'
}

function relationByCollectionPath(collectionPath = '') {
  const path = String(collectionPath || '')
  return actionRelations.value.find((child) => {
    const keys = collectionKeyCandidates(child)
    return keys.some(key => path.endsWith(String(key)))
  }) || null
}

function buildActionRelations(modelSchema = {}, relations = []) {
  const schemaChildren = collectSchemaChildren(modelSchema)
  const result = []
  const usedSchema = new Set()
  ;(Array.isArray(relations) ? relations : []).forEach((relation) => {
    const matchedIndex = schemaChildren.findIndex(child => isSameRelation(child, relation))
    const schemaChild = matchedIndex >= 0 ? schemaChildren[matchedIndex] : {}
    if (matchedIndex >= 0)
      usedSchema.add(matchedIndex)
    result.push(normalizeActionRelation({
      ...schemaChild,
      ...relation,
      fields: mergeRelationFields(schemaChild, relation),
    }))
  })
  schemaChildren.forEach((child, index) => {
    if (!usedSchema.has(index))
      result.push(normalizeActionRelation(child))
  })
  return result
}

function normalizeActionRelation(relation = {}) {
  const targetObjectCode = relation.targetObjectCode || relation.objectCode || relation.modelCode || ''
  const parsedConfig = parseRelationConfig(relation.relationConfig)
  const collectionKey = parsedConfig.relationKey
    || relation.key
    || relation.modelCode
    || relation.tableName
    || lowerSnake(targetObjectCode)
    || relation.relationName
  const isKeyName = relation.relationName && relation.relationName === collectionKey
  return {
    ...relation,
    targetObjectCode,
    collectionKey,
    relationType: relation.relationType || relation.type || 'DETAIL',
    relationName: (!isKeyName && relation.relationName)
      || relation.detailTabTitle
      || relation.targetObjectName
      || parsedConfig.detailTabTitle
      || relation.modelName
      || relation.label
      || relation.relationName
      || '',
    fields: relationFields({
      ...relation,
      targetObjectCode,
    }),
  }
}

function mergeRelationFields(schemaChild = {}, relation = {}) {
  return [
    ...normalizeFields(schemaChild.fields),
    ...normalizeFields(relation.fields),
  ]
}

function relationFields(relation = {}) {
  const fields = [
    ...normalizeFields(relation.fields),
    ...normalizeFields(targetFieldsMap.value[relation.targetObjectCode]),
  ]
  const seen = new Set()
  return fields.filter((field) => {
    const code = field.sourceField || field.field || field.fieldCode
    if (!code || seen.has(code) || isInactiveField(field))
      return false
    seen.add(code)
    return true
  })
}

function normalizeFields(fields = []) {
  return Array.isArray(fields) ? fields.map(toPageField) : []
}

function isSameRelation(left = {}, right = {}) {
  const leftCodes = collectionKeyCandidates(left)
  const rightCodes = collectionKeyCandidates(right)
  return leftCodes.some(code => rightCodes.includes(code))
}

function collectionKeyCandidates(relation = {}) {
  return [
    relation.collectionKey,
    relation.key,
    relation.modelCode,
    relation.tableName,
    relation.targetObjectCode,
    lowerSnake(relation.targetObjectCode),
    relation.relationName,
  ].filter(Boolean).map(String)
}

function isDetailRelation(relation = {}) {
  const type = String(relation.relationType || relation.type || '').toUpperCase()
  return !type || ['DETAIL', 'CHILD_LIST', 'ONE_TO_MANY'].includes(type)
}

function collectSchemaChildren(modelSchema = {}) {
  if (Array.isArray(modelSchema.children))
    return modelSchema.children
  if (Array.isArray(modelSchema.childrenConfig))
    return modelSchema.childrenConfig
  if (Array.isArray(modelSchema.relations))
    return modelSchema.relations
  return []
}

function lowerSnake(value = '') {
  return String(value || '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/\W+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .toLowerCase()
}

function parseRelationConfig(value) {
  if (!value)
    return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  }
  catch {
    return {}
  }
}

function toPageField(field = {}) {
  return {
    ...field,
    field: field.field || field.fieldCode || field.sourceField,
    label: field.label || field.fieldName || field.name || field.fieldCode || field.sourceField,
    fieldStatus: field.fieldStatus,
    basicProps: { ...(field.basicProps || {}) },
    advancedProps: { ...(field.advancedProps || {}) },
  }
}

function isInactiveField(field = {}) {
  const status = String(field.fieldStatus || '').toUpperCase()
  return status === 'DISABLED' || status === 'HIDDEN'
}

function businessFieldLabel(field = {}) {
  const fieldName = field.field || field.fieldCode || field.sourceField || ''
  const systemLabels = {
    id: '记录ID',
    createBy: '创建人',
    createTime: '创建时间',
    updateBy: '修改人',
    updateTime: '修改时间',
    createDept: '创建部门',
    tenantId: '租户',
  }
  return systemLabels[fieldName] || field.label || field.fieldName || field.name || '未命名字段'
}

function fieldPathOptions(step = {}) {
  const options = []
  const seen = new Set()
  const add = (value, label) => {
    const text = String(value || '').trim()
    if (!text || seen.has(text))
      return
    seen.add(text)
    options.push({ label: label || '未命名字段', value: text })
  }
  collectMainFields(props.fields, props.modelSchema).forEach((sourceField) => {
    const field = toPageField(sourceField)
    const fieldCode = field.sourceField || field.field || field.fieldCode
    if (!fieldCode)
      return
    add(`record.main.${fieldCode}`, fieldDisplayLabel(field, '单据字段'))
  })
  const aliases = step.aliases?.length ? step.aliases : [{ alias: 'item', collectionPath: '' }]
  aliases.forEach((aliasInfo) => {
    const relation = relationByCollectionPath(aliasInfo.collectionPath) || actionRelations.value[0]
    const detailLabel = detailDisplayLabel(aliasInfo.collectionPath)
    const fields = relation?.fields || []
    fields.forEach((field) => {
      const fieldCode = field.sourceField || field.field || field.fieldCode
      if (!fieldCode)
        return
      add(`${aliasInfo.alias}.${fieldCode}`, fieldDisplayLabel(field, detailLabel))
    })
    add(`${aliasInfo.alias}.id`, `${detailLabel} · ID`)
  })
  return options
}

function fieldDisplayLabel(field = {}, scopeLabel = '') {
  const label = businessFieldLabel(field)
  return scopeLabel ? `${scopeLabel} · ${label}` : label
}

function collectMainFields(fields = [], modelSchema = {}) {
  if (Array.isArray(fields) && fields.length)
    return fields
  return Array.isArray(modelSchema.fields) ? modelSchema.fields : []
}

function detailDisplayLabel(collectionPath = '') {
  const relation = relationByCollectionPath(collectionPath) || actionRelations.value[0]
  return relation?.relationName || relation?.detailTabTitle || relation?.modelName || relation?.label || '明细字段'
}

function mergeSelectedFieldOptions(options = [], values = []) {
  const result = Array.isArray(options) ? [...options] : []
  const seen = new Set(result.map(item => item.value))
  normalizeStringList(values).forEach((value) => {
    if (seen.has(value))
      return
    result.push({
      label: resolvePathDisplayLabel(value, result),
      value,
    })
    seen.add(value)
  })
  return result
}

function resolvePathDisplayLabel(value, options = []) {
  const matched = options.find(item => item.value === value)
  if (matched?.label)
    return matched.label
  const fieldCode = String(value || '').split('.').pop()
  const field = findFieldByCode(fieldCode)
  if (field)
    return fieldDisplayLabel(field, String(value || '').startsWith('record.') ? '单据字段' : '明细字段')
  return '未识别字段（请在关系与级联中维护）'
}

function findFieldByCode(fieldCode) {
  if (!fieldCode)
    return null
  const allFields = [
    ...collectMainFields(props.fields, props.modelSchema).map(toPageField),
    ...actionRelations.value.flatMap(relation => relation.fields || []),
  ].map(toPageField)
  return allFields.find((field) => {
    const codes = [field.sourceField, field.field, field.fieldCode, field.columnName].filter(Boolean)
    return codes.some(code => String(code) === String(fieldCode))
  }) || null
}

function ensureActionConfig(action) {
  if (!action.actionConfig || typeof action.actionConfig !== 'object' || Array.isArray(action.actionConfig))
    action.actionConfig = {}
  return action.actionConfig
}

function ensureStepConfig(step) {
  if (!step.stepConfig || typeof step.stepConfig !== 'object' || Array.isArray(step.stepConfig))
    step.stepConfig = {}
  return step.stepConfig
}

function ensureParams(config) {
  if (!config.params || typeof config.params !== 'object' || Array.isArray(config.params))
    config.params = {}
  return config.params
}

function resolveStep(actions, step) {
  const action = actions[selectedActionIndex.value]
  if (!action?.actionConfig)
    return null
  return getPathValue(action.actionConfig, step.path)
}

function getPathValue(root, path = []) {
  let cursor = root
  for (const key of path) {
    if (cursor == null)
      return null
    cursor = cursor[key]
  }
  return cursor
}

function wrapExpression(path) {
  const text = String(path || '').trim()
  return text ? `\${${text}}` : ''
}

function unwrapExpression(value) {
  const text = String(value || '').trim()
  const match = text.match(/^\$\{([^}]+)\}$/)
  return match ? match[1] : text
}

function stringValue(value) {
  if (value == null)
    return ''
  if (typeof value === 'object')
    return JSON.stringify(value)
  return String(value)
}

function normalizeStringList(value) {
  const list = Array.isArray(value) ? value : value ? [value] : []
  return Array.from(new Set(list.map(item => String(item || '').trim()).filter(Boolean)))
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value ?? []))
}

function stringifyJson(value) {
  try {
    return JSON.stringify(value || {}, null, 2)
  }
  catch {
    return '{}'
  }
}
</script>

<style scoped>
.automation-designer {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: 12px;
  min-height: 100%;
  padding: 14px;
  background: #f7f8fa;
}

.designer-head,
.boundary-strip,
.panel-section,
.automation-list,
.advanced-json {
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  background: #fff;
}

.designer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
}

.designer-head h2,
.section-title h3 {
  margin: 0;
  color: #18181b;
  font-size: 15px;
  font-weight: 700;
}

.designer-head p {
  margin: 3px 0 0;
  color: #71717a;
  font-size: 12px;
}

.boundary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
}

.boundary-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  background: #fafafa;
}

.boundary-item.active {
  background: #eef3ff;
}

.boundary-item strong {
  color: #27272a;
  font-size: 13px;
}

.boundary-item span,
.pane-title span,
.automation-list-item span,
.flow-card-head em,
.quantity-card-head span,
.unsupported-step span {
  color: #71717a;
  font-size: 12px;
}

.approval-entry-note {
  font-size: 12px;
}

.context-notices {
  display: grid;
  gap: 8px;
}

.relation-warning {
  margin-top: 10px;
  font-size: 12px;
}

.empty-state {
  align-self: center;
}

.automation-workbench {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.automation-list {
  min-height: 0;
  overflow: auto;
  padding: 8px;
}

.pane-title,
.section-title,
.flow-card-head,
.nested-title,
.quantity-card-head,
.unsupported-step {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.pane-title {
  padding: 4px 4px 8px;
  color: #52525b;
  font-size: 12px;
}

.automation-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  width: 100%;
  padding: 10px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  text-align: left;
}

.automation-list-item__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.automation-list-item__delete {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.automation-list-item:hover .automation-list-item__delete,
.automation-list-item.active .automation-list-item__delete {
  opacity: 1;
}

.automation-list-item:hover,
.automation-list-item.active {
  border-color: #bfd0ff;
  background: #eef3ff;
}

.automation-list-item strong,
.quantity-card-head strong,
.unsupported-step strong {
  color: #27272a;
  font-size: 13px;
}

.automation-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.panel-section {
  padding: 12px;
}

.action-summary {
  padding-bottom: 4px;
}

.section-title > div,
.subsection-head > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.section-hint,
.subsection-head span {
  margin: 0;
  color: #71717a;
  font-size: 12px;
}

.orchestration-step-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.scope-note {
  width: 100%;
  font-size: 12px;
}

.protocol-warning {
  margin-bottom: 12px;
}

.schema-editor,
.mapping-editor {
  padding-top: 12px;
  border-top: 1px solid #e4e4e7;
}

.subsection-head,
.local-step-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.schema-row {
  display: grid;
  grid-template-columns: minmax(130px, 1fr) minmax(130px, 1fr) 120px 44px repeat(2, minmax(90px, 0.7fr)) auto;
  gap: 8px;
  align-items: center;
}

.schema-row + .schema-row {
  margin-top: 8px;
}

.add-step-select {
  width: 150px;
}

.local-step-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.local-step-card {
  padding: 12px;
  border: 1px solid #dbe4ff;
  border-radius: 8px;
  background: #f8faff;
}

.local-step-card__head {
  display: grid;
  grid-template-columns: 180px minmax(180px, 1fr) auto;
}

.mapping-editor {
  margin-top: 4px;
}

.mapping-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) 120px minmax(180px, 1.3fr) auto;
  gap: 8px;
  align-items: center;
}

.mapping-row + .mapping-row {
  margin-top: 8px;
}

.adjustment-row {
  grid-template-columns: minmax(140px, 1fr) 130px minmax(180px, 1.2fr) 100px 100px auto;
}

.flow-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.flow-card,
.quantity-card,
.call-api-step-card,
.unsupported-step {
  padding: 12px;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  background: #fafafa;
}

.call-api-step-card {
  border-color: #c7d2fe;
  background: #f8faff;
}

.flow-card-head {
  margin-bottom: 12px;
}

.flow-card-head > div,
.quantity-card-head > div,
.unsupported-step > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.step-index {
  display: grid;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 50%;
  background: #2944cc;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.nested-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e4e7;
}

.nested-title {
  margin-bottom: 10px;
}

.quantity-card + .quantity-card {
  margin-top: 10px;
}

.quantity-card {
  background: #fff;
}

.quantity-card-head {
  margin-bottom: 10px;
}

.advanced-json {
  overflow: hidden;
}

.json-error {
  margin-top: 8px;
}

@media (max-width: 1180px) {
  .boundary-strip,
  .automation-workbench {
    grid-template-columns: 1fr;
  }

  .schema-row,
  .mapping-row,
  .adjustment-row {
    grid-template-columns: minmax(140px, 1fr) minmax(140px, 1fr);
  }
}
</style>
