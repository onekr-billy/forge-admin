<template>
<n-form label-placement="top" :show-feedback="false" class="property-form">
            <section class="panel-item">
              <div class="panel-item-title">
                显示与编辑状态
              </div>
              <div class="switch-list">
                <label>
                  <span>隐藏组件</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.visibility?.hidden"
                    @update:value="updateComponentHidden"
                  />
                </label>
                <label v-if="isField">
                  <span>只读</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.visibility?.readonly"
                    @update:value="updateComponent({ visibility: { readonly: $event } })"
                  />
                </label>
                <label v-if="isField || isButtonComponent">
                  <span>禁用</span>
                  <n-switch
                    size="small"
                    :value="!!selectedComponent.props?.disabled"
                    @update:value="updateComponent({ props: { disabled: $event } })"
                  />
                </label>
              </div>
              <RuntimeRulesEditor
                title="条件规则"
                :rules="selectedComponent.props?.runtimeRules || []"
                :field-options="runtimeRuleFieldOptions"
                @update:rules="updateComponent({ props: { runtimeRules: $event } })"
              />
            </section>

            <section v-if="isField && selectedDrivenRuntimeRules.length" class="panel-item driven-runtime-rules-panel">
              <div class="panel-item-title">
                其他字段引用了它
              </div>
              <div class="driven-runtime-rule-list">
                <article v-for="item in selectedDrivenRuntimeRules" :key="item.key" class="driven-runtime-rule-card">
                  <div>
                    <strong>{{ item.targetLabel }}</strong>
                    <span>{{ item.summary }}</span>
                  </div>
                  <n-button size="tiny" text type="primary" @click="emit('update:selectedId', item.targetId)">
                    查看目标字段
                  </n-button>
                </article>
              </div>
            </section>

            <section class="panel-item">
              <div class="panel-item-title">
                联动动作
              </div>
              <div class="interaction-presets">
                <button
                  v-for="preset in interactionPresets"
                  :key="preset.key"
                  type="button"
                  class="interaction-preset-card"
                  @click="addInteractionPreset(preset.key)"
                >
                  <strong>{{ preset.title }}</strong>
                  <span>{{ preset.description }}</span>
                </button>
              </div>
              <div v-if="interactionRules.length" class="interaction-rule-list">
                <details v-for="(rule, ruleIndex) in interactionRules" :key="rule.id || ruleIndex" class="interaction-rule-card" open>
                  <summary class="interaction-rule-head">
                    <div>
                      <strong>{{ resolveTriggerLabel(rule.trigger) }}</strong>
                      <span>{{ resolveActionLabel(rule.action) }}</span>
                    </div>
                    <n-button size="tiny" quaternary type="error" @click.stop.prevent="removeInteractionRule(ruleIndex)">
                      删除
                    </n-button>
                  </summary>
                  <div class="interaction-grid">
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          触发事件
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            什么时候执行这条规则。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.trigger || defaultTrigger"
                        size="small"
                        :consistent-menu-width="false"
                        :options="triggerOptions"
                        @update:value="updateInteractionRule(ruleIndex, { trigger: $event || defaultTrigger })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          执行动作
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            规则触发后对目标组件做什么。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.action || 'setValue'"
                        size="small"
                        :consistent-menu-width="false"
                        :options="actionOptions"
                        @update:value="updateInteractionRule(ruleIndex, { action: $event || 'setValue' })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          目标组件
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            被这条规则影响的字段、按钮或区块。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.targetId || ''"
                        size="small"
                        filterable
                        clearable
                        :consistent-menu-width="false"
                        :options="componentTargetOptions"
                        @update:value="updateInteractionRule(ruleIndex, { targetId: $event || '' })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          触发值等于
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            用于下拉联动，例如选择“省份A”时才更新城市。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-input
                        :value="rule.whenValue ?? ''"
                        size="small"
                        clearable
                        placeholder="为空表示任何值都触发"
                        @update:value="updateInteractionRule(ruleIndex, { whenValue: $event || undefined })"
                      />
                    </n-form-item>
                  </div>
                  <template v-if="rule.action === 'setOptions'">
                    <n-form-item label="选项来源 API">
                      <n-input
                        :value="rule.api || ''"
                        size="small"
                        clearable
                        placeholder="get@/api/options?parent=:value"
                        @update:value="updateInteractionRule(ruleIndex, { api: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item label="静态选项 JSON">
                      <n-input
                        :value="rule.optionsJson || stringifyJsonProp(rule.options)"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 5 }"
                        placeholder="[{&quot;label&quot;:&quot;选项&quot;,&quot;value&quot;:&quot;A&quot;}]"
                        @update:value="updateInteractionRuleJson(ruleIndex, 'options', $event)"
                      />
                    </n-form-item>
                  </template>
                  <n-form-item v-else-if="['setValue', 'showHide', 'enableDisable'].includes(rule.action)">
                    <template #label>
                      <span class="field-label-with-help">
                        {{ resolveActionValueMeta(rule.action).label }}
                        <n-tooltip trigger="hover">
                          <template #trigger>
                            <span class="help-icon">?</span>
                          </template>
                          {{ resolveActionValueMeta(rule.action).help }}
                        </n-tooltip>
                      </span>
                    </template>
                    <n-input
                      :value="rule.value ?? ''"
                      size="small"
                      clearable
                      :placeholder="resolveActionValueMeta(rule.action).placeholder"
                      @update:value="updateInteractionRule(ruleIndex, { value: $event || undefined })"
                    />
                  </n-form-item>
                  <template v-else-if="rule.action === 'openModal'">
                    <n-form-item label="弹窗标题">
                      <n-input
                        :value="rule.modalTitle || ''"
                        size="small"
                        clearable
                        placeholder="请输入弹窗标题"
                        @update:value="updateInteractionRule(ruleIndex, { modalTitle: $event || undefined })"
                      />
                    </n-form-item>
                    <n-form-item>
                      <template #label>
                        <span class="field-label-with-help">
                          弹窗内容
                          <n-tooltip trigger="hover">
                            <template #trigger>
                              <span class="help-icon">?</span>
                            </template>
                            不需要手写 JSON。可以复用当前表单，或选择画布里的某个组件作为弹窗内容。
                          </n-tooltip>
                        </span>
                      </template>
                      <n-select
                        :value="rule.modalContentMode || 'currentForm'"
                        :options="modalContentModeOptions"
                        @update:value="updateInteractionRule(ruleIndex, { modalContentMode: $event || 'currentForm' })"
                      />
                    </n-form-item>
                    <n-form-item v-if="rule.modalContentMode === 'component'" label="弹窗组件">
                      <n-select
                        :value="rule.modalComponentId || ''"
                        filterable
                        clearable
                        :consistent-menu-width="false"
                        :options="componentTargetOptions"
                        @update:value="updateInteractionRule(ruleIndex, { modalComponentId: $event || '' })"
                      />
                    </n-form-item>
                    <n-form-item v-if="rule.modalContentMode === 'formAsset'" label="引用表单">
                      <n-select
                        :value="rule.modalFormKey || 'current'"
                        filterable
                        :consistent-menu-width="false"
                        :options="formAssetOptions"
                        @update:value="updateInteractionRule(ruleIndex, { modalFormKey: $event || 'current' })"
                      />
                    </n-form-item>
                  </template>
                  <n-form-item v-else-if="rule.action === 'apiRequest'" label="请求接口">
                    <n-input
                      :value="rule.api || ''"
                      size="small"
                      clearable
                      placeholder="post@/api/action"
                      @update:value="updateInteractionRule(ruleIndex, { api: $event || undefined })"
                    />
                  </n-form-item>
                </details>
              </div>
              <div v-else class="empty-config-box">
                暂无联动动作
              </div>
              <n-button size="small" dashed block @click="addInteractionRule">
                + 新增规则
              </n-button>
            </section>
          </n-form>
        
</template>

<script>
import { useForgePropertyPanelApi } from '../forgePropertyPanelContext'
import { forgePropertyPanelLocalComponents } from '../forgePropertyPanelLocalComponents'

export default {
  name: 'SelectedInteractionTab',
  components: { ...forgePropertyPanelLocalComponents },
  setup() {
    return useForgePropertyPanelApi()
  },
}
</script>

<style scoped src="../forge-property-panel-shell.css"></style>
<style scoped src="../forge-property-panel-fields.css"></style>
