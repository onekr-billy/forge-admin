<template>
  <div class="business-relation-designer">
    <div class="relation-designer-head">
      <div>
        <h3>{{ modelOnly ? '对象关系' : '关系与级联' }}</h3>
        <p>{{ modelOnly ? '维护对象之间的关系和字段端点；表单交互与字段联动在应用设计器中配置。' : '优先在关系图拖线建立对象关系，再按需配置字段联动和高级行为。' }}</p>
      </div>
      <n-space size="small">
        <n-button size="small" secondary @click="loadRelations">
          刷新
        </n-button>
        <n-button size="small" secondary @click="addRelation">
          新增关系
        </n-button>
        <n-button size="small" type="primary" :loading="saving" @click="saveRelations">
          保存配置
        </n-button>
      </n-space>
    </div>

    <div class="relation-workbench">
      <aside class="relation-step-rail">
        <div class="rail-block">
          <button
            type="button"
            class="relation-choice er-choice"
            :class="{ active: activePanel === 'er' }"
            @click="selectErDiagram"
          >
            <strong>可视化关系图</strong>
            <span>{{ erDiagramSummary }}，拖动字段连接点创建关系</span>
          </button>
        </div>

        <div class="rail-divider" />

        <div class="rail-block">
          <div class="rail-title">
            <span>对象关系</span>
            <n-button size="tiny" secondary @click="addRelation">
              新增
            </n-button>
          </div>
          <div v-if="localRelations.length" class="relation-choice-list">
            <button
              v-for="relation in localRelations"
              :key="relation.clientKey"
              type="button"
              class="relation-choice"
              :class="{ active: activePanel === 'relation' && activeRelation?.clientKey === relation.clientKey }"
              @click="selectRelation(relation.clientKey)"
            >
              <strong>{{ relation.relationName || relationLabel(relation) }}</strong>
              <span>{{ relationSentence(relation) }}</span>
            </button>
          </div>
          <n-empty v-else size="small" description="暂无关系" />
        </div>

        <template v-if="!modelOnly">
          <div class="rail-divider" />

          <div class="rail-block">
            <div class="rail-title">
              <span>字段联动</span>
              <n-button size="tiny" secondary @click="addLinkageRule">
                新增
              </n-button>
            </div>
            <div v-if="localLinkage.rules.length" class="relation-choice-list">
              <button
                v-for="rule in localLinkage.rules"
                :key="rule.ruleId"
                type="button"
                class="relation-choice linkage-choice"
                :class="{ active: activePanel === 'linkage' && activeLinkageRuleId === rule.ruleId }"
                @click="selectLinkageRule(rule.ruleId)"
              >
                <strong>{{ linkageRuleLabel(rule) }}</strong>
                <span>{{ linkageRuleSentence(rule) }}</span>
              </button>
            </div>
            <n-empty v-else size="small" description="暂无联动" />
          </div>
        </template>
      </aside>

      <main class="relation-main-pane">
        <n-spin :show="loading">
          <template v-if="activePanel === 'relation'">
            <template v-if="activeRelation">
              <section id="relation-section-basic" class="relation-config-card">
                <header class="section-title-row">
                  <div>
                    <strong>关系属性</strong>
                    <span>{{ relationSentence(activeRelation) }}</span>
                  </div>
                  <n-space size="small">
                    <n-tag size="small" :type="relationMatchTagType(activeRelation)" :bordered="false">
                      {{ relationMatchSummary(activeRelation) }}
                    </n-tag>
                    <n-tag size="small" :type="activeRelation.status === 0 ? 'default' : 'success'" :bordered="false">
                      {{ activeRelation.status === 0 ? '停用' : '启用' }}
                    </n-tag>
                    <n-popconfirm @positive-click="removeActiveRelation">
                      <template #trigger>
                        <n-button quaternary circle size="small">
                          <template #icon>
                            <n-icon><TrashOutline /></n-icon>
                          </template>
                        </n-button>
                      </template>
                      确认移除该关系？
                    </n-popconfirm>
                  </n-space>
                </header>
                <div class="relation-identity-grid">
                  <n-form label-placement="top" :show-feedback="false" size="small">
                    <n-form-item label="关系名称">
                      <n-input v-model:value="activeRelation.relationName" placeholder="例如：采购单包含采购明细" @update:value="markDirty" />
                    </n-form-item>
                  </n-form>
                  <n-form label-placement="top" :show-feedback="false" size="small">
                    <n-form-item label="目标对象">
                      <n-select
                        v-model:value="activeRelation.targetObjectCode"
                        :options="targetObjectOptions"
                        filterable
                        placeholder="选择目标对象"
                        @update:value="value => updateTargetObject(activeRelation, value)"
                      />
                    </n-form-item>
                  </n-form>
                  <div class="relation-identity-state">
                    <span>关系类型</span>
                    <strong>一对多明细</strong>
                  </div>
                  <div class="relation-identity-state">
                    <span>启用关系</span>
                    <n-switch
                      :value="activeRelation.status !== 0"
                      @update:value="value => updateStatus(activeRelation, value)"
                    />
                  </div>
                </div>

                <div class="relation-match-box">
                  <div class="relation-match-box-head">
                    <div>
                      <strong>字段端点</strong>
                      <span>这两个字段决定主记录与明细记录如何匹配。</span>
                    </div>
                    <n-tag size="small" :type="relationMatchTagType(activeRelation)" :bordered="false">
                      {{ relationMatchSummary(activeRelation) }}
                    </n-tag>
                  </div>
                  <div class="relation-endpoint-flow">
                    <section class="relation-endpoint-card is-source">
                      <header>
                        <span>当前对象</span>
                        <strong>{{ objectName || objectCode }}</strong>
                      </header>
                      <n-form label-placement="top" :show-feedback="false" size="small">
                        <n-form-item :label="sourceFieldLabel(activeRelation)">
                          <n-select
                            v-model:value="activeRelation.sourceFieldCode"
                            :options="sourceFieldOptions"
                            clearable
                            filterable
                            :placeholder="sourceFieldPlaceholder(activeRelation)"
                            @update:value="markDirty"
                          />
                        </n-form-item>
                      </n-form>
                    </section>
                    <div class="endpoint-connector" aria-hidden="true">
                      <span>1</span>
                      <i />
                      <span>N</span>
                    </div>
                    <section class="relation-endpoint-card is-target">
                      <header>
                        <span>目标对象</span>
                        <strong>{{ objectNameMap.get(activeRelation.targetObjectCode) || activeRelation.targetObjectCode || '未选择' }}</strong>
                      </header>
                      <n-form label-placement="top" :show-feedback="false" size="small">
                        <n-form-item :label="targetFieldLabel(activeRelation)">
                          <n-select
                            v-model:value="activeRelation.targetFieldCode"
                            :options="targetFieldOptions(activeRelation)"
                            :loading="targetFieldLoadingMap[activeRelation.targetObjectCode]"
                            clearable
                            filterable
                            :placeholder="targetFieldPlaceholder(activeRelation)"
                            @update:value="markDirty"
                          />
                        </n-form-item>
                      </n-form>
                    </section>
                  </div>
                  <n-form label-placement="top" :show-feedback="false" size="small" class="relation-display-field">
                    <n-form-item :label="displayFieldLabel(activeRelation)">
                      <n-select
                        v-model:value="activeRelation.displayField"
                        :options="targetDisplayFieldOptions(activeRelation)"
                        :loading="targetFieldLoadingMap[activeRelation.targetObjectCode]"
                        clearable
                        filterable
                        placeholder="选择运行态回显字段"
                        @update:value="markDirty"
                      />
                    </n-form-item>
                  </n-form>
                </div>
              </section>

              <n-collapse v-if="!modelOnly" class="relation-advanced-collapse" arrow-placement="right">
                <n-collapse-item name="inline">
                  <template #header>
                    <span class="advanced-relation-title">
                      显示与内嵌
                      <n-tag size="small" :type="inlineConfigTagType(activeRelation)" :bordered="false">
                        {{ inlineConfigSummary(activeRelation) }}
                      </n-tag>
                    </span>
                  </template>
                  <p class="advanced-relation-hint">
                    控制这条关系是否出现在新增、编辑和详情区域。
                  </p>
                  <n-form label-placement="top" :show-feedback="false" size="small">
                    <n-grid :cols="4" :x-gap="16" :y-gap="6" responsive="screen">
                      <n-form-item-gi label="新增表单维护">
                        <n-switch
                          :value="activeRelation.inlineCreateEnabled === true"
                          :disabled="!canInlineEdit(activeRelation)"
                          @update:value="value => updateInlineCreate(activeRelation, value)"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="编辑表单维护">
                        <n-switch
                          :value="activeRelation.inlineEditEnabled === true"
                          :disabled="!canInlineEdit(activeRelation)"
                          @update:value="value => updateInlineEdit(activeRelation, value)"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="详情页展示">
                        <n-switch
                          :value="activeRelation.showInDetail !== false"
                          @update:value="value => updateShowInDetail(activeRelation, value)"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="子表保存模式">
                        <n-select
                          v-model:value="activeRelation.saveMode"
                          :options="childSaveModeOptions"
                          :disabled="!canInlineEdit(activeRelation)"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="详情页签">
                        <n-input v-model:value="activeRelation.detailTabTitle" placeholder="例如：采购明细" @update:value="markDirty" />
                      </n-form-item-gi>
                      <n-form-item-gi label="排序">
                        <n-input-number v-model:value="activeRelation.sortOrder" :min="0" style="width: 100%" @update:value="markDirty" />
                      </n-form-item-gi>
                    </n-grid>
                  </n-form>
                </n-collapse-item>

                <n-collapse-item name="selector">
                  <template #header>
                    <span class="advanced-relation-title">
                      子表选择器
                      <n-tag size="small" :type="selectorConfigTagType(activeRelation)" :bordered="false">
                        {{ selectorConfigSummary(activeRelation) }}
                      </n-tag>
                    </span>
                  </template>
                  <div class="selector-toggle-row">
                    <span>{{ activeRelation.selectorEnabled ? '已启用选择器按钮' : '开启后可从候选对象批量选择记录' }}</span>
                    <n-space size="small">
                      <n-button v-if="activeRelation.selectorEnabled" size="tiny" secondary @click="applySelectorDefaults(activeRelation)">
                        智能补齐
                      </n-button>
                      <n-switch
                        :value="activeRelation.selectorEnabled === true"
                        :disabled="!canInlineEdit(activeRelation)"
                        @update:value="value => updateRelationSelectorEnabled(activeRelation, value)"
                      />
                    </n-space>
                  </div>
                  <n-form v-if="activeRelation.selectorEnabled" label-placement="top" :show-feedback="false" size="small">
                    <n-grid :cols="3" :x-gap="16" :y-gap="6" responsive="screen">
                      <n-form-item-gi label="候选对象">
                        <n-select
                          v-model:value="activeRelation.selectorObjectCode"
                          :options="targetObjectOptions"
                          clearable
                          filterable
                          placeholder="选择弹窗里查询的对象"
                          @update:value="value => updateSelectorObject(activeRelation, value)"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="按钮文案">
                        <n-input v-model:value="activeRelation.selectorButtonText" placeholder="选择记录" @update:value="markDirty" />
                      </n-form-item-gi>
                      <n-form-item-gi label="选择器标题">
                        <n-input v-model:value="activeRelation.selectorTitle" placeholder="可为空" @update:value="markDirty" />
                      </n-form-item-gi>
                    </n-grid>
                  </n-form>
                  <n-empty v-else size="small" description="开启后可配置候选对象、弹窗字段和回填映射" />
                </n-collapse-item>

                <n-collapse-item name="mapping">
                  <template #header>
                    <span class="advanced-relation-title">
                      字段映射
                      <n-tag size="small" :type="mappingConfigTagType(activeRelation)" :bordered="false">
                        {{ mappingConfigSummary(activeRelation) }}
                      </n-tag>
                    </span>
                  </template>
                  <div class="selector-toggle-row">
                    <span>选择器弹窗展示、搜索和选中后的写入规则。</span>
                    <n-button v-if="activeRelation.selectorEnabled" size="tiny" secondary @click="addSelectorMapping(activeRelation)">
                      <template #icon>
                        <n-icon><AddOutline /></n-icon>
                      </template>
                      添加映射
                    </n-button>
                  </div>
                  <template v-if="activeRelation.selectorEnabled">
                    <n-form label-placement="top" :show-feedback="false" size="small">
                      <n-grid :cols="2" :x-gap="16" :y-gap="6" responsive="screen">
                        <n-form-item-gi label="弹窗展示字段">
                          <n-select
                            v-model:value="activeRelation.selectorDisplayFields"
                            :options="selectorCandidateFieldOptions(activeRelation)"
                            :loading="targetFieldLoadingMap[selectorCandidateObjectCode(activeRelation)]"
                            multiple
                            clearable
                            filterable
                            placeholder="选择用户在弹窗里看到的字段"
                            @update:value="markDirty"
                          />
                        </n-form-item-gi>
                        <n-form-item-gi label="关键词搜索字段">
                          <n-select
                            v-model:value="activeRelation.selectorKeywordFields"
                            :options="selectorCandidateFieldOptions(activeRelation)"
                            :loading="targetFieldLoadingMap[selectorCandidateObjectCode(activeRelation)]"
                            multiple
                            clearable
                            filterable
                            placeholder="选择编号、名称等可搜索字段"
                            @update:value="markDirty"
                          />
                        </n-form-item-gi>
                      </n-grid>
                    </n-form>
                    <div v-if="activeRelation.selectorMappings.length" class="selector-mapping-list">
                      <div class="selector-row-head">
                        <span>候选对象字段</span>
                        <span />
                        <span>子表字段</span>
                        <span />
                      </div>
                      <div v-for="(mapping, mappingIndex) in activeRelation.selectorMappings" :key="mapping.clientKey" class="selector-mapping-row">
                        <n-select
                          v-model:value="mapping.sourceField"
                          :options="selectorCandidateFieldOptions(activeRelation, mapping.sourceField)"
                          :loading="targetFieldLoadingMap[selectorCandidateObjectCode(activeRelation)]"
                          clearable
                          filterable
                          placeholder="候选字段"
                          @update:value="markDirty"
                        />
                        <span class="selector-mapping-arrow">写入</span>
                        <n-select
                          v-model:value="mapping.targetField"
                          :options="selectorTargetFieldOptions(activeRelation, mapping.targetField)"
                          :loading="targetFieldLoadingMap[activeRelation.targetObjectCode]"
                          clearable
                          filterable
                          placeholder="子表字段"
                          @update:value="markDirty"
                        />
                        <n-button quaternary circle size="small" @click="removeSelectorMapping(activeRelation, mappingIndex)">
                          <template #icon>
                            <n-icon><TrashOutline /></n-icon>
                          </template>
                        </n-button>
                      </div>
                    </div>
                    <n-empty v-else size="small" description="还没有字段映射，点击添加映射" />

                    <div class="selector-filter-block">
                      <div class="selector-filter-title">
                        <strong>筛选候选记录</strong>
                        <n-button size="tiny" secondary @click="addSelectorSearchParam(activeRelation)">
                          <template #icon>
                            <n-icon><AddOutline /></n-icon>
                          </template>
                          添加筛选
                        </n-button>
                      </div>
                      <div v-if="activeRelation.selectorSearchParams.length" class="selector-filter-list">
                        <div v-for="(param, paramIndex) in activeRelation.selectorSearchParams" :key="param.clientKey" class="selector-filter-row">
                          <n-select
                            v-model:value="param.paramKey"
                            :options="selectorCandidateFieldOptions(activeRelation, param.paramKey)"
                            :loading="targetFieldLoadingMap[selectorCandidateObjectCode(activeRelation)]"
                            clearable
                            filterable
                            placeholder="候选对象筛选字段"
                            @update:value="markDirty"
                          />
                          <n-select
                            v-model:value="param.sourceType"
                            :options="selectorFilterSourceOptions"
                            @update:value="value => updateSelectorSearchParamSource(param, value)"
                          />
                          <n-select
                            v-if="param.sourceType !== 'static'"
                            v-model:value="param.sourceField"
                            :options="selectorContextFieldOptions(param)"
                            clearable
                            filterable
                            placeholder="选择来源字段"
                            @update:value="markDirty"
                          />
                          <n-input
                            v-else
                            v-model:value="param.staticValue"
                            clearable
                            placeholder="固定值"
                            @update:value="markDirty"
                          />
                          <n-button quaternary circle size="small" @click="removeSelectorSearchParam(activeRelation, paramIndex)">
                            <template #icon>
                              <n-icon><TrashOutline /></n-icon>
                            </template>
                          </n-button>
                        </div>
                      </div>
                      <n-empty v-else size="small" description="未设置筛选条件，弹窗展示全部候选记录" />
                    </div>
                  </template>
                  <n-empty v-else size="small" description="开启子表选择器后可维护字段映射" />
                </n-collapse-item>

                <n-collapse-item v-if="canInlineEdit(activeRelation)" name="approval">
                  <template #header>
                    <span class="advanced-relation-title">
                      审批后处理
                      <n-tag size="small" :type="approvalConfigTagType(activeRelation)" :bordered="false">
                        {{ approvalConfigSummary(activeRelation) }}
                      </n-tag>
                    </span>
                  </template>
                  <div class="selector-toggle-row">
                    <span>{{ activeRelation.approvalQuantityEnabled ? '审批通过后按当前关系逐行同步数量' : '未启用审批后数量同步' }}</span>
                    <n-switch
                      :value="activeRelation.approvalQuantityEnabled === true"
                      @update:value="value => updateRelationApprovalQuantityEnabled(activeRelation, value)"
                    />
                  </div>
                  <n-form v-if="activeRelation.approvalQuantityEnabled" label-placement="top" :show-feedback="false" size="small">
                    <n-grid :cols="2" :x-gap="16" :y-gap="6" responsive="screen">
                      <n-form-item-gi label="处理方式">
                        <n-select
                          v-model:value="activeRelation.approvalQuantityOperation"
                          :options="approvalQuantityOperationOptions"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="归属字段（主表）">
                        <n-select
                          v-model:value="activeRelation.approvalQuantityAccountField"
                          :options="sourceFieldOptions"
                          clearable
                          filterable
                          placeholder="选择主表字段"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="对象字段（明细）">
                        <n-select
                          v-model:value="activeRelation.approvalQuantityItemField"
                          :options="targetFieldOptions(activeRelation)"
                          :loading="targetFieldLoadingMap[activeRelation.targetObjectCode]"
                          clearable
                          filterable
                          placeholder="选择明细字段"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="备用对象字段（明细）">
                        <n-select
                          v-model:value="activeRelation.approvalQuantityItemFallbackFields"
                          :options="targetFieldOptions(activeRelation)"
                          :loading="targetFieldLoadingMap[activeRelation.targetObjectCode]"
                          multiple
                          clearable
                          filterable
                          placeholder="主对象字段为空时按顺序尝试"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="数量字段（明细）">
                        <n-select
                          v-model:value="activeRelation.approvalQuantityField"
                          :options="targetFieldOptions(activeRelation)"
                          :loading="targetFieldLoadingMap[activeRelation.targetObjectCode]"
                          clearable
                          filterable
                          placeholder="选择明细里的数量字段"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                      <n-form-item-gi label="备注">
                        <n-input
                          v-model:value="activeRelation.approvalQuantityRemark"
                          placeholder="例如：审批通过后自动同步"
                          @update:value="markDirty"
                        />
                      </n-form-item-gi>
                    </n-grid>
                  </n-form>
                </n-collapse-item>
              </n-collapse>
            </template>
            <n-empty v-else-if="!loading" class="relation-empty-state" description="暂无关系配置">
              <template #extra>
                <n-button size="small" type="primary" @click="addRelation">
                  新增关系
                </n-button>
              </template>
            </n-empty>
          </template>

          <template v-else-if="activePanel === 'er'">
            <section id="relation-section-er" class="relation-er-panel">
              <div class="er-object-picker">
                <div>
                  <strong>画布对象</strong>
                  <span>选择要参与连线的目标对象，字段会按需加载。</span>
                </div>
                <n-select
                  :value="erCandidateObjectCodes"
                  :options="targetObjectOptions"
                  multiple
                  filterable
                  clearable
                  max-tag-count="responsive"
                  placeholder="选择目标对象加入画布"
                  @update:value="updateErCandidateObjects"
                />
              </div>
              <LowcodeErDiagram
                title="拖线配置对象关系"
                :subtitle="erDiagramSubtitle"
                :models="erDiagramModels"
                :primary-model-code="objectCode"
                :selected-relation-key="activeRelationKey"
                editable
                :download-file-name="`${objectCode || 'business-object'}-er.svg`"
                empty-text="暂无可绘制的业务对象关系"
                @connect="handleErConnect"
                @relation-select="selectRelation"
              />
            </section>
          </template>

          <template v-else>
            <section id="relation-section-linkage" class="relation-config-card">
              <header class="section-title-row">
                <div>
                  <strong>字段联动</strong>
                  <span>维护表单字段之间的过滤和清空规则。</span>
                </div>
                <n-button size="tiny" secondary @click="addLinkageRule">
                  <template #icon>
                    <n-icon><AddOutline /></n-icon>
                  </template>
                  新增联动
                </n-button>
              </header>
              <div v-if="localLinkage.rules.length" class="relation-card-list">
                <section v-for="(rule, index) in localLinkage.rules" :key="rule.ruleId" class="relation-card linkage-card">
                  <header class="relation-card-head">
                    <div>
                      <strong>{{ linkageRuleLabel(rule) }}</strong>
                      <p>{{ linkageRuleSentence(rule) }}</p>
                    </div>
                    <n-space size="small">
                      <n-tag size="small" :type="rule.enabled === false ? 'default' : 'success'" :bordered="false">
                        {{ rule.enabled === false ? '停用' : '启用' }}
                      </n-tag>
                      <n-popconfirm @positive-click="removeLinkageRule(index)">
                        <template #trigger>
                          <n-button quaternary circle size="small">
                            <template #icon>
                              <n-icon><TrashOutline /></n-icon>
                            </template>
                          </n-button>
                        </template>
                        确认移除该级联规则？
                      </n-popconfirm>
                    </n-space>
                  </header>

                  <n-form label-placement="top" :show-feedback="false" size="small" class="relation-form">
                    <div class="linkage-flow-editor">
                      <n-form-item label="控制字段">
                        <n-select
                          v-model:value="rule.sourceField"
                          :options="sourceFieldOptions"
                          clearable
                          filterable
                          placeholder="选择控制字段"
                          @update:value="value => updateLinkageSource(rule, value)"
                        />
                      </n-form-item>
                      <div class="linkage-flow-arrow" aria-hidden="true">
                        →
                      </div>
                      <n-form-item label="联动方式">
                        <n-select
                          v-model:value="rule.type"
                          :options="linkageTypeOptions"
                          @update:value="value => updateLinkageType(rule, value)"
                        />
                      </n-form-item>
                      <div class="linkage-flow-arrow" aria-hidden="true">
                        →
                      </div>
                      <n-form-item label="目标字段">
                        <n-select
                          v-model:value="rule.targetField"
                          :options="linkageTargetFieldOptions(rule)"
                          clearable
                          filterable
                          placeholder="选择被过滤字段"
                          @update:value="value => updateLinkageTarget(rule, value)"
                        />
                      </n-form-item>
                    </div>

                    <section class="linkage-detail-section">
                      <strong>数据来源</strong>
                      <n-grid :cols="3" :x-gap="12" :y-gap="4" responsive="screen">
                        <template v-if="rule.dataSourceType === 'dict'">
                          <n-form-item-gi label="上级字典类型">
                            <n-input v-model:value="rule.dictConfig.sourceDictType" placeholder="选择或填写字典类型" @update:value="markLinkageDirty" />
                          </n-form-item-gi>
                          <n-form-item-gi label="目标字典类型">
                            <n-input v-model:value="rule.dictConfig.targetDictType" placeholder="选择或填写字典类型" @update:value="markLinkageDirty" />
                          </n-form-item-gi>
                          <n-form-item-gi v-if="rule.type === 'linkedDict'" label="关联字典类型">
                            <n-input v-model:value="rule.dictConfig.linkedDictType" placeholder="选择或填写关联字典类型" @update:value="markLinkageDirty" />
                          </n-form-item-gi>
                        </template>

                        <template v-else>
                          <n-form-item-gi label="请求参数名">
                            <n-input v-model:value="rule.remoteConfig.paramName" placeholder="例如：上级字段参数名" @update:value="markLinkageDirty" />
                          </n-form-item-gi>
                          <n-form-item-gi label="远程接口">
                            <n-input v-model:value="rule.remoteConfig.url" placeholder="选择项接口地址" @update:value="markLinkageDirty" />
                          </n-form-item-gi>
                          <n-form-item-gi label="请求方式">
                            <n-select v-model:value="rule.remoteConfig.method" :options="methodOptions" @update:value="markLinkageDirty" />
                          </n-form-item-gi>
                          <n-form-item-gi v-if="rule.type === 'objectReference'" label="目标对象">
                            <n-select
                              v-model:value="rule.objectConfig.targetObjectCode"
                              :options="targetObjectOptions"
                              clearable
                              filterable
                              placeholder="选择目标对象"
                              @update:value="markLinkageDirty"
                            />
                          </n-form-item-gi>
                        </template>
                      </n-grid>
                    </section>

                    <section class="linkage-state-grid">
                      <n-form-item label="控制字段为空时">
                        <n-select
                          v-model:value="rule.emptyStrategy"
                          :options="emptyStrategyOptions"
                          @update:value="markLinkageDirty"
                        />
                      </n-form-item>
                      <n-form-item label="控制值变化后清空目标">
                        <n-switch
                          :value="rule.clearOnSourceChange !== false"
                          @update:value="value => updateRuleClearOnChange(rule, value)"
                        />
                      </n-form-item>
                      <n-form-item label="启用规则">
                        <n-switch
                          :value="rule.enabled !== false"
                          @update:value="value => updateRuleEnabled(rule, value)"
                        />
                      </n-form-item>
                    </section>
                  </n-form>
                </section>
              </div>
              <n-empty v-else description="暂无级联规则" />
            </section>
          </template>
        </n-spin>
      </main>
    </div>

    <n-modal
      v-model:show="relationWizardVisible"
      preset="card"
      title="新增关系"
      :bordered="false"
      :style="{ width: 'min(760px, calc(100vw - 32px))' }"
    >
      <n-spin :show="relationWizardLoading">
        <n-form label-placement="top" size="small" :show-feedback="false" class="relation-wizard-form">
          <section class="wizard-section">
            <header>
              <span>1</span>
              <div>
                <strong>选择目标对象</strong>
                <small>当前版本统一创建一对多明细关系。</small>
              </div>
            </header>
            <n-form-item label="目标对象">
              <n-select
                v-model:value="relationWizardForm.targetObjectCode"
                :options="targetObjectOptions"
                filterable
                placeholder="选择目标业务对象"
                @update:value="applyRelationWizardDefaults"
              />
            </n-form-item>
          </section>

          <section class="wizard-section">
            <header>
              <span>2</span>
              <div>
                <strong>确认字段端点</strong>
                <small>系统会智能推断，你可以在这里调整。</small>
              </div>
            </header>
            <div class="wizard-endpoint-flow">
              <n-form-item :label="sourceFieldLabel({ relationType: relationWizardForm.relationType })">
                <n-select
                  v-model:value="relationWizardForm.sourceFieldCode"
                  :options="sourceFieldOptions"
                  filterable
                  clearable
                  :placeholder="sourceFieldPlaceholder({ relationType: relationWizardForm.relationType })"
                />
              </n-form-item>
              <div class="wizard-flow-arrow">
                1 → N
              </div>
              <n-form-item :label="targetFieldLabel({ relationType: relationWizardForm.relationType })">
                <n-select
                  v-model:value="relationWizardForm.targetFieldCode"
                  :options="targetFieldOptions({ targetObjectCode: relationWizardForm.targetObjectCode, targetFieldCode: relationWizardForm.targetFieldCode })"
                  :loading="targetFieldLoadingMap[relationWizardForm.targetObjectCode]"
                  filterable
                  clearable
                  :placeholder="targetFieldPlaceholder({ relationType: relationWizardForm.relationType })"
                />
              </n-form-item>
            </div>
          </section>

          <section class="wizard-section">
            <header>
              <span>3</span>
              <div>
                <strong>页面展示</strong>
                <small>这些文案会出现在表单和详情区域。</small>
              </div>
            </header>
            <n-grid :cols="2" :x-gap="12">
              <n-form-item-gi label="关系名称">
                <n-input v-model:value="relationWizardForm.relationName" placeholder="例如：采购单包含采购明细" />
              </n-form-item-gi>
              <n-form-item-gi label="详情页签标题">
                <n-input v-model:value="relationWizardForm.detailTabTitle" placeholder="例如：采购明细" />
              </n-form-item-gi>
            </n-grid>
            <n-form-item v-if="canInlineEdit({ relationType: relationWizardForm.relationType })" label="子表保存模式">
              <n-select v-model:value="relationWizardForm.saveMode" :options="childSaveModeOptions" />
              <div class="wizard-field-hint">
                推荐“行级合并”：按目标字段更新已有明细，未匹配行新增；全量替换会先删除旧明细。
              </div>
            </n-form-item>
          </section>
        </n-form>
      </n-spin>
      <template #footer>
        <n-space justify="end">
          <n-button size="small" @click="relationWizardVisible = false">
            取消
          </n-button>
          <n-button size="small" type="primary" :loading="relationWizardLoading" @click="confirmRelationWizard">
            确认
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script>
import { businessRelationDesignerLocalComponents } from './businessRelationDesignerLocalComponents'
import { useBusinessRelationDesigner } from './composables/useBusinessRelationDesigner'

export default {
  name: 'BusinessRelationDesigner',
  components: {
    ...businessRelationDesignerLocalComponents,
  },
  props: {
  objectId: {
    type: [Number, String],
    default: null,
  },
  suiteCode: {
    type: String,
    default: '',
  },
  objectCode: {
    type: String,
    default: '',
  },
  objectName: {
    type: String,
    default: '',
  },
  fields: {
    type: Array,
    default: () => [],
  },
  linkageSchema: {
    type: Object,
    default: null,
  },
  designerActions: {
    type: Array,
    default: () => [],
  },
  designerOptions: {
    type: Object,
    default: () => ({}),
  },
  modelOnly: {
    type: Boolean,
    default: false,
  },
},
  emits: ['updated', 'dirtyChange', 'fieldsUpdated', 'update:linkageSchema', 'update:designerActions'],
  setup(props, { emit, expose }) {
    return useBusinessRelationDesigner(props, emit, expose)
  },
}
</script>

<style scoped src="./businessRelationDesigner.css"></style>
