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
                  <!-- setup 内 defineComponent：Options API 需 :is 才能解析 -->
                  <component
                    :is="BusinessQuantityStepCard"
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

              <component
                :is="BusinessQuantityStepCard"
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

<script>
import { businessActionDesignerLocalComponents } from './businessActionDesignerLocalComponents'
import { useBusinessActionDesigner } from './composables/useBusinessActionDesigner'

export default {
  name: 'BusinessActionDesigner',
  components: {
    ...businessActionDesignerLocalComponents,
  },
  props: {
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
},
  emits: ['update:actions', 'dirtyChange'],
  setup(props, { emit }) {
    return useBusinessActionDesigner(props, emit)
  },
}
</script>

<style scoped src="./businessActionDesigner.css"></style>
