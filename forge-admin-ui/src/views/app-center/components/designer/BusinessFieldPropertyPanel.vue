<template>
  <aside class="property-panel" :class="{ 'formula-only-panel': formulaOnly }">
    <template v-if="field">
      <div v-if="!formulaOnly" class="property-head">
        <div>
          <h3>{{ form.fieldName || '字段属性' }}</h3>
          <p>维护字段业务定义、数据库映射和全局约束</p>
          <code v-if="developerMode">{{ form.fieldCode || '保存后自动生成字段编码和列名' }}</code>
        </div>
        <div class="property-head-actions">
          <n-tag v-if="field.systemField" size="small" :bordered="false">
            系统字段
          </n-tag>
          <div v-else class="property-head-buttons">
            <n-button
              secondary
              circle
              size="small"
              title="还原字段修改"
              :disabled="!changed || saving"
              @click="resetForm"
            >
              <template #icon>
                <n-icon><ArrowUndoOutline /></n-icon>
              </template>
            </n-button>
            <n-button
              type="primary"
              size="small"
              :loading="saving"
              :disabled="!changed || saving"
              @click="$emit('save', payload)"
            >
              <template #icon>
                <n-icon><SaveOutline /></n-icon>
              </template>
              保存字段
            </n-button>
          </div>
        </div>
      </div>

      <div class="property-body" :class="{ 'formula-only-body': formulaOnly }">
        <n-form v-if="formulaOnly" label-placement="top" size="small" :show-feedback="false" class="formula-only-form">
          <FormulaConfigPanel
            :form="form"
            :field="field"
            :all-fields="allFields"
            :relations="relations"
            :saving="saving"
            :formula-validating="formulaValidating"
            :formula-previewing="formulaPreviewing"
            :formula-validate-result="formulaValidateResult"
            :formula-feedback-lines="formulaFeedbackLines"
            :can-open-formula-debugger="canOpenFormulaDebugger"
            :has-formula-tool-fields="hasFormulaToolFields"
            :preview-disabled="!canOpenFormulaPreview"
            @toggle-enabled="updateFormulaEnabled"
            @type-change="onFormulaTypeChange"
            @insert-token="insertFormulaToken"
            @insert-string-token="insertStringToken"
            @condition-expression-change="onConditionExpressionChange"
            @condition-mode-change="onConditionModeChange"
            @condition-rule-compiled="onConditionRuleCompiled"
            @condition-rule-validation="onConditionRuleValidation"
            @trigger-mode="setFormulaTriggerMode"
            @save="$emit('save', payload)"
            @validate="handleValidateFormula"
            @preview="openFormulaPreview"
            @open-debugger="formulaDebuggerVisible = true"
            @open-log="formulaLogVisible = true"
            @open-graph="formulaGraphVisible = true"
          />
        </n-form>

        <n-tabs v-else v-model:value="activeTab" type="line" animated class="property-tabs">
          <n-tab-pane v-if="isTabVisible('basic')" name="basic" tab="业务定义">
            <n-form label-placement="top" size="small" :show-feedback="false" class="business-definition-form">
              <section class="property-section">
                <header class="property-section-head">
                  <div>
                    <strong>字段身份</strong>
                    <span>业务人员看到的名称和字段语义。</span>
                  </div>
                </header>
                <n-grid :cols="2" :x-gap="12">
                  <n-form-item-gi label="字段名称">
                    <n-input v-model:value="form.fieldName" :disabled="field.systemField" placeholder="例如：客户等级" />
                  </n-form-item-gi>
                  <n-form-item-gi label="字段类型">
                    <n-select
                      v-model:value="form.fieldType"
                      :options="fieldTypeOptions"
                      :disabled="field.systemField"
                      filterable
                    />
                  </n-form-item-gi>
                </n-grid>
              </section>

              <section class="property-section">
                <header class="property-section-head">
                  <div>
                    <strong>默认值与提示</strong>
                    <span>作为新页面和新记录的初始建议，可在页面设计器覆盖。</span>
                  </div>
                  <label class="property-section-switch">
                    <span>必填</span>
                    <n-switch
                      :value="form.required"
                      :disabled="field.systemField"
                      size="small"
                      @update:value="updateRequired"
                    />
                  </label>
                </header>
                <n-form-item label="输入提示">
                  <n-input v-model:value="form.placeholder" :disabled="field.systemField" placeholder="例如：请输入客户等级" />
                </n-form-item>

                <n-form-item v-if="form.fieldType === 'REGION'" label="默认地区">
                  <RegionTreeSelect v-model="form.defaultValue" size="small" :disabled="field.systemField" />
                </n-form-item>
                <n-form-item v-else-if="['NUMBER', 'MONEY'].includes(form.fieldType)" label="默认值">
                  <n-input-number
                    v-model:value="form.defaultValue"
                    :show-button="false"
                    :disabled="field.systemField"
                    class="full-input"
                    placeholder="请输入数字默认值"
                  />
                </n-form-item>
                <n-form-item v-else-if="form.fieldType === 'SWITCH'" label="默认值">
                  <n-select
                    v-model:value="form.defaultValue"
                    :options="switchDefaultOptions"
                    :disabled="field.systemField"
                  />
                </n-form-item>
                <n-form-item v-else-if="['DATE', 'DATETIME'].includes(form.fieldType)" label="默认值">
                  <FieldDefaultValueEditor
                    :component-key="form.fieldType === 'DATETIME' ? 'datetime' : 'date'"
                    :component="{ props: { defaultValue: form.defaultValue } }"
                    :field-asset="form"
                    :model-value="form.defaultValue"
                    :disabled="field.systemField"
                    @update:model-value="form.defaultValue = $event === undefined ? '' : $event"
                  />
                </n-form-item>
                <n-form-item v-else label="默认值">
                  <n-input v-model:value="form.defaultValue" :disabled="field.systemField" placeholder="可为空" />
                </n-form-item>
                <div v-if="requiredDefaultHint" class="required-default-hint" :class="{ warning: !hasSafeRequiredDefault }">
                  {{ requiredDefaultHint }}
                </div>

                <n-form-item v-if="needsDict" label="系统字典" class="dict-property-item">
                  <DictTypeSelect
                    v-model:value="form.dictType"
                    compact
                    :fields="allFields"
                    :disabled="field.systemField"
                  />
                </n-form-item>
              </section>

              <section class="property-section field-constraint-config">
                <header class="property-section-head">
                  <div>
                    <strong>数据约束</strong>
                    <span>这里限制数据库可保存的值，会自动应用到所有表单和保存接口。</span>
                  </div>
                </header>
                <n-grid v-if="supportsTextLength || isDecimalType" :cols="2" :x-gap="12">
                  <n-form-item-gi v-if="supportsTextLength" label="最大字符数">
                    <n-input-number
                      v-model:value="form.length"
                      :min="1"
                      :max="lengthMax"
                      :show-button="false"
                      :disabled="field.systemField"
                      class="full-input"
                    />
                  </n-form-item-gi>
                  <n-form-item-gi v-if="isDecimalType" label="总位数">
                    <n-input-number
                      :value="form.length"
                      :min="1"
                      :max="65"
                      :show-button="false"
                      :disabled="field.systemField"
                      class="full-input"
                      @update:value="onDecimalTotalDigitsChange"
                    />
                  </n-form-item-gi>
                  <n-form-item-gi v-if="isDecimalType" label="小数位">
                    <n-input-number
                      v-model:value="form.precision"
                      :min="0"
                      :max="decimalPrecisionMax"
                      :show-button="false"
                      :disabled="field.systemField"
                      class="full-input"
                    />
                  </n-form-item-gi>
                </n-grid>
                <n-grid v-if="isNumericDataType" :cols="2" :x-gap="12">
                  <n-form-item-gi label="最小值">
                    <n-input-number
                      v-model:value="form.minValue"
                      :show-button="false"
                      :disabled="field.systemField"
                      :placeholder="numericMinimumPlaceholder"
                      clearable
                      class="full-input"
                    />
                  </n-form-item-gi>
                  <n-form-item-gi label="最大值">
                    <n-input-number
                      v-model:value="form.maxValue"
                      :show-button="false"
                      :disabled="field.systemField"
                      :placeholder="numericMaximumPlaceholder"
                      clearable
                      class="full-input"
                    />
                  </n-form-item-gi>
                </n-grid>
                <div v-if="isNumericDataType" class="storage-constraint-hint">
                  <strong>数据库可保存范围</strong>
                  <span>{{ numericStorageHint }}</span>
                </div>
                <n-grid v-if="!isNumericDataType" :cols="1" :x-gap="12">
                  <n-form-item-gi label="常用校验">
                    <n-select
                      v-model:value="form.validationPreset"
                      :options="commonValidationOptions"
                      :disabled="field.systemField"
                      clearable
                      placeholder="选择手机号、邮箱等规则"
                      @update:value="onValidationPresetChange"
                    />
                  </n-form-item-gi>
                </n-grid>
                <n-grid v-if="!isNumericDataType" :cols="2" :x-gap="12">
                  <n-form-item-gi label="校验提示">
                    <n-input
                      v-model:value="form.validationMessage"
                      :disabled="field.systemField"
                      clearable
                      placeholder="为空时使用规则默认提示"
                    />
                  </n-form-item-gi>
                  <n-form-item-gi label="正则表达式">
                    <n-input
                      v-model:value="form.validationPattern"
                      :disabled="field.systemField"
                      clearable
                      placeholder="选择常用校验后自动填充"
                    />
                  </n-form-item-gi>
                </n-grid>
              </section>

              <section v-if="isRelationField" class="property-section relation-config-unified">
                <header class="property-section-head record-selector-config-head">
                  <div>
                    <strong>关联对象</strong>
                    <span>配置字段从哪个对象选择和如何回显。</span>
                  </div>
                  <n-tag size="small" :bordered="false" :type="relationSelectionMode === 'dropdown' ? 'info' : 'success'">
                    {{ relationSelectionMode === 'dropdown' ? '下拉选择' : '弹窗选择器' }}
                  </n-tag>
                </header>

                <n-form-item label="选择方式">
                  <n-radio-group :value="relationSelectionMode" @update:value="switchRelationSelectionMode">
                    <n-radio-button value="dropdown">
                      下拉选择
                    </n-radio-button>
                    <n-radio-button value="popup">
                      弹窗选择器
                    </n-radio-button>
                  </n-radio-group>
                </n-form-item>

                <n-form-item label="目标对象">
                  <n-select
                    :value="unifiedRelationObjectCode"
                    :options="businessObjectOptions"
                    :loading="businessObjectLoading"
                    :disabled="field.systemField"
                    filterable
                    clearable
                    placeholder="选择关联的业务对象"
                    @update:value="handleUnifiedObjectCodeChange"
                  />
                </n-form-item>

                <n-grid v-if="unifiedRelationObjectCode" :cols="2" :x-gap="12">
                  <n-form-item-gi label="显示字段">
                    <n-select
                      :value="unifiedDisplayField"
                      :options="unifiedTargetFieldOptions"
                      :disabled="field.systemField || !unifiedRelationObjectCode"
                      filterable
                      clearable
                      placeholder="自动推断"
                      @update:value="handleUnifiedDisplayFieldChange"
                    />
                  </n-form-item-gi>
                  <n-form-item-gi label="值字段">
                    <n-select
                      :value="unifiedValueField"
                      :options="unifiedTargetFieldOptions"
                      :disabled="field.systemField || !unifiedRelationObjectCode"
                      filterable
                      clearable
                      placeholder="默认 id"
                      @update:value="handleUnifiedValueFieldChange"
                    />
                  </n-form-item-gi>
                </n-grid>

                <!-- 关联选择高级配置：下拉/弹窗共用，搜索字段与过滤参数对两种模式都生效，弹窗专属项仅在弹窗模式展示 -->
                <n-collapse v-if="unifiedRelationObjectCode" :default-expanded-names="[]" class="relation-advanced-collapse">
                  <n-collapse-item title="高级配置" name="advanced">
                    <n-grid v-if="relationSelectionMode === 'popup'" :cols="2" :x-gap="12">
                      <n-form-item-gi label="套件编码">
                        <n-input
                          v-model:value="form.recordSelectorSuiteCode"
                          :disabled="field.systemField"
                          clearable
                          placeholder="可为空"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="回显目标字段">
                        <n-input
                          v-model:value="form.recordSelectorTargetLabelField"
                          :disabled="field.systemField"
                          clearable
                          placeholder="可为空"
                        />
                      </n-form-item-gi>
                    </n-grid>
                    <n-form-item v-if="relationSelectionMode === 'popup'" label="弹窗展示字段">
                      <n-select
                        v-model:value="form.recordSelectorDisplayFields"
                        :options="unifiedTargetFieldOptions"
                        :disabled="field.systemField || !unifiedRelationObjectCode"
                        multiple
                        filterable
                        clearable
                        placeholder="选择弹窗列表中展示的字段"
                      />
                    </n-form-item>
                    <n-form-item label="搜索字段">
                      <n-select
                        v-model:value="form.recordSelectorKeywordFields"
                        :options="unifiedTargetFieldOptions"
                        :disabled="field.systemField || !unifiedRelationObjectCode"
                        multiple
                        filterable
                        clearable
                        placeholder="选择用于关键字搜索的字段"
                      />
                    </n-form-item>
                    <n-form-item v-if="relationSelectionMode === 'popup'" label="字段映射">
                      <n-input
                        v-model:value="form.recordSelectorMappingsText"
                        :disabled="field.systemField"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 6 }"
                        placeholder="source=target，每行一个"
                      />
                    </n-form-item>
                    <n-form-item label="过滤参数">
                      <n-input
                        v-model:value="form.recordSelectorSearchParamsText"
                        :disabled="field.systemField"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 6 }"
                        placeholder="warehouseId=${formData.warehouseId}，每行一个"
                      />
                    </n-form-item>
                  </n-collapse-item>
                </n-collapse>
              </section>

              <section class="property-section property-note-section">
                <header class="property-section-head">
                  <div>
                    <strong>业务说明</strong>
                    <span>补充字段口径和使用说明。</span>
                  </div>
                </header>
                <n-form-item label="备注">
                  <n-input v-model:value="form.remark" type="textarea" :rows="3" placeholder="字段说明，业务用户可见" />
                </n-form-item>
              </section>
            </n-form>
          </n-tab-pane>

          <n-tab-pane v-if="isTabVisible('display')" name="display" tab="规则与安全">
            <n-form label-placement="top" size="small" :show-feedback="false">
              <section class="page-config-boundary">
                <strong>页面配置已独立</strong>
                <span>表单显示、列表字段、字段顺序和查询条件，请在表单设计或列表设计中配置；这里维护字段默认值和数据约束。</span>
              </section>

              <section v-if="showCascadeConfig" class="cascade-config">
                <div class="cascade-config-head">
                  <div>
                    <strong>级联选项</strong>
                    <span>{{ isUserSelectCascadeField ? '选了组织后，人员只能从该组织范围内选择，组织重选时人员同步刷新。' : '本字段选项跟随上级字段的值变化，如：先选部门，再按部门加载人员。' }}</span>
                  </div>
                  <n-switch
                    :value="form.basicProps.cascade.enabled"
                    :disabled="field.systemField"
                    size="small"
                    @update:value="updateCascadeEnabled"
                  />
                </div>
                <div v-if="form.basicProps.cascade.enabled" class="cascade-grid">
                  <n-form-item :label="isUserSelectCascadeField ? '① 组织字段' : '① 上级字段'">
                    <n-select
                      v-model:value="form.basicProps.cascade.sourceField"
                      :options="cascadeSourceFieldOptions"
                      :disabled="field.systemField"
                      filterable
                      clearable
                      :placeholder="isUserSelectCascadeField && !hasOrgFieldForCascade ? '未找到组织组件字段，可手动选择其他字段' : '选谁变化时刷新本字段，如：部门'"
                    />
                  </n-form-item>
                  <n-form-item v-if="!isUserSelectCascadeField" label="② 联动方式">
                    <n-select
                      v-model:value="form.basicProps.cascade.mode"
                      :options="cascadeModeOptions"
                      :disabled="field.systemField"
                    />
                  </n-form-item>
                  <n-form-item v-if="isUserSelectCascadeField" label="② 组织范围">
                    <n-switch
                      :value="form.basicProps.cascade.includeChildren !== false"
                      :disabled="field.systemField"
                      size="small"
                      @update:value="updateCascadeIncludeChildren"
                    >
                      <template #checked>
                        含子组织
                      </template>
                      <template #unchecked>
                        仅本组织
                      </template>
                    </n-switch>
                  </n-form-item>
                  <n-form-item v-if="form.basicProps.cascade.mode === 'remoteParam'" label="③ 参数名">
                    <n-input
                      v-model:value="form.basicProps.cascade.paramName"
                      :disabled="field.systemField"
                      placeholder="接口接收上级值的参数，如 deptId"
                    />
                  </n-form-item>
                  <div v-if="form.basicProps.cascade.mode === 'remoteParam'" class="cascade-remote-hint">
                    选了上级后，会以该参数名携带上级值请求选项接口并刷新选项；接口地址在表单设计器 → 该字段 → 级联选项中填写。
                  </div>
                  <div v-else-if="form.basicProps.cascade.mode === 'parentDictCode'" class="cascade-remote-hint">
                    从本字段已有选项中按父级编码筛选，需选项数据（字典/静态选项）包含父级编码。
                  </div>
                  <div v-else-if="isUserSelectCascadeField" class="cascade-remote-hint">
                    选了组织后人员选择范围限定在该组织（{{ form.basicProps.cascade.includeChildren === false ? '仅本组织直属人员' : '含全部子组织人员' }}）；组织重选时已选人员会自动清空。
                  </div>
                </div>
              </section>

              <div class="switch-grid">
                <label>
                  <span>唯一校验</span>
                  <n-switch v-model:value="form.advancedProps.unique" :disabled="field.systemField" size="small" />
                </label>
                <label>
                  <span>允许导入</span>
                  <n-switch v-model:value="form.importable" :disabled="field.systemField" size="small" />
                </label>
                <label>
                  <span>允许导出</span>
                  <n-switch v-model:value="form.exportable" size="small" />
                </label>
              </div>
            </n-form>
          </n-tab-pane>

          <n-tab-pane v-if="developerMode && isTabVisible('advanced')" name="advanced" tab="数据库与开发">
            <n-form label-placement="top" size="small" :show-feedback="false">
              <section class="default-inheritance-tip">
                <strong>页面默认值</strong>
                <span>默认控件和默认查询方式仅供新建页面继承，已有页面的最终配置不会被这里覆盖。</span>
              </section>
              <n-grid :cols="2" :x-gap="12">
                <n-form-item-gi label="字段英文名">
                  <n-input v-model:value="form.fieldCode" :disabled="field.systemField" placeholder="自动生成" />
                </n-form-item-gi>
                <n-form-item-gi label="数据库列名">
                  <n-input v-model:value="form.columnName" :disabled="field.systemField" placeholder="自动生成" />
                </n-form-item-gi>
                <n-form-item-gi label="数据类型">
                  <FieldTypeSelect v-model:value="form.dataType" :disabled="field.systemField" />
                </n-form-item-gi>
                <n-form-item-gi v-if="normalizedDataType === 'decimal'" label="小数位">
                  <n-input-number
                    v-model:value="form.precision"
                    :min="0"
                    :max="8"
                    :show-button="false"
                    :disabled="field.systemField"
                    class="full-input"
                  />
                </n-form-item-gi>
                <n-form-item-gi label="默认控件">
                  <n-select v-model:value="form.componentType" :options="componentOptions" filterable clearable />
                </n-form-item-gi>
                <n-form-item-gi label="默认查询方式">
                  <n-select v-model:value="form.queryType" :options="queryTypeOptions" clearable />
                </n-form-item-gi>
                <n-form-item-gi label="字段状态">
                  <n-select v-model:value="form.fieldStatus" :options="statusOptions" />
                </n-form-item-gi>
              </n-grid>

              <n-grid :cols="2" :x-gap="12">
                <n-form-item-gi label="脱敏类型">
                  <n-select v-model:value="form.sensitiveType" :options="sensitiveOptions" clearable />
                </n-form-item-gi>
                <n-form-item-gi label="加密算法">
                  <n-select v-model:value="form.encryptAlgorithm" :options="encryptOptions" clearable />
                </n-form-item-gi>
              </n-grid>
            </n-form>
          </n-tab-pane>
        </n-tabs>
      </div>
    </template>

    <n-empty v-else description="选择左侧字段后维护业务定义和数据库属性" />

    <n-modal
      v-model:show="formulaPreviewDialogVisible"
      preset="card"
      class="formula-preview-modal"
      :title="`预览计算：${form.fieldName || form.fieldCode || '目标字段'}`"
      :bordered="false"
      :mask-closable="!formulaPreviewing"
    >
      <div class="formula-preview-dialog">
        <div class="formula-preview-summary">
          <div>
            <span>目标字段</span>
            <strong>{{ previewTargetLabel }}</strong>
          </div>
          <n-tag size="small" :bordered="false" type="info">
            {{ form.formulaTriggerMode === 'REALTIME' ? '实时计算' : '保存时计算' }}
          </n-tag>
        </div>

        <div class="formula-preview-expression">
          {{ form.formulaExpression || '未配置表达式' }}
        </div>

        <n-empty
          v-if="!previewVariableFields.length"
          size="small"
          description="当前公式没有识别到变量，将直接计算表达式。"
        />
        <n-form v-else label-placement="top" size="small" :show-feedback="false" class="formula-preview-form">
          <n-form-item
            v-for="item in previewVariableFields"
            :key="item.field"
            :label="item.label"
          >
            <n-input-number
              v-if="item.inputType === 'number'"
              v-model:value="formulaPreviewForm[item.field]"
              :show-button="false"
              class="full-input"
              :placeholder="`请输入${item.label}`"
            />
            <n-switch
              v-else-if="item.inputType === 'switch'"
              v-model:value="formulaPreviewForm[item.field]"
            />
            <n-input
              v-else
              v-model:value="formulaPreviewForm[item.field]"
              :placeholder="`请输入${item.label}`"
            />
          </n-form-item>
        </n-form>

        <div v-if="formulaPreviewResult" class="formula-preview-result" :class="formulaPreviewResult.success ? 'success' : 'error'">
          <template v-if="formulaPreviewResult.success">
            <span>计算结果</span>
            <strong>{{ formatPreviewValue(formulaPreviewResult.result) }}</strong>
            <em v-if="formulaPreviewResult.elapsedMs !== null && formulaPreviewResult.elapsedMs !== undefined">
              {{ formulaPreviewResult.elapsedMs }}ms
            </em>
          </template>
          <template v-else>
            <span>计算失败</span>
            <strong>{{ formulaPreviewResult.errorMessage || '请检查变量值和表达式' }}</strong>
          </template>
        </div>
      </div>

      <template #footer>
        <div class="formula-preview-footer">
          <n-button secondary :disabled="formulaPreviewing" @click="fillPreviewSampleValues">
            填入样例值
          </n-button>
          <div>
            <n-button :disabled="formulaPreviewing" @click="formulaPreviewDialogVisible = false">
              关闭
            </n-button>
            <n-button
              type="primary"
              :loading="formulaPreviewing"
              :disabled="!canPreviewCalculate"
              @click="handlePreviewFormula"
            >
              计算
            </n-button>
          </div>
        </div>
      </template>
    </n-modal>

    <FormulaDebuggerPanel
      v-model:show="formulaDebuggerVisible"
      :field="selectedFormulaField"
      :fields="formulaToolFields"
      :object-code="objectCode"
    />
    <FormulaExecutionLogDrawer
      v-model:show="formulaLogVisible"
      :object-code="objectCode"
      :field-code="form.fieldCode"
    />
    <FormulaDependencyGraph
      v-model:show="formulaGraphVisible"
      :fields="formulaToolFields"
      :object-code="objectCode"
      :current-field-code="form.fieldCode"
    />
  </aside>
</template>

<script>
import { businessFieldPropertyPanelLocalComponents } from './businessFieldPropertyPanelLocalComponents'
import { useBusinessFieldPropertyPanel } from './composables/useBusinessFieldPropertyPanel'

export default {
  name: 'BusinessFieldPropertyPanel',
  components: {
    ...businessFieldPropertyPanelLocalComponents,
  },
  props: {
  field: {
    type: Object,
    default: null,
  },
  allFields: {
    type: Array,
    default: () => [],
  },
  relations: {
    type: Array,
    default: () => [],
  },
  objectCode: {
    type: String,
    default: '',
  },
  developerMode: {
    type: Boolean,
    default: false,
  },
  saving: {
    type: Boolean,
    default: false,
  },
  defaultActiveTab: {
    type: String,
    default: 'basic',
  },
  visibleTabs: {
    type: Array,
    default: () => [],
  },
  mode: {
    type: String,
    default: 'property',
  },
},
  emits: ['save', 'dirtyChange'],
  setup(props, { emit, expose }) {
    return useBusinessFieldPropertyPanel(props, emit, expose)
  },
}
</script>

<style scoped src="./businessFieldPropertyPanel.css"></style>
